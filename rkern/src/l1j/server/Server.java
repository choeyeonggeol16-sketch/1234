package l1j.server;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.sql.SQLException;
import java.util.logging.LogManager;
import l1j.server.server.GameServer;
import l1j.server.server.appcenter.AppCenterService;
import l1j.server.server.model.network.core.connection.CIOBufferPool;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.network.core.config.CIOConfig;
import l1j.server.server.monitor.LoggerInstance;
import l1j.server.server.utils.PerformanceTimer;

/**
 * l1j-jp의 서버를 기동한다.
 */
public class Server {

    /** 로그 설정 파일의 폴더. */
    private static final String LOG_PROP =
            "./config/log.properties";

    private static final int MAX_ACCEPT_BACKLOG = 512;
    private static final int SEND_LEASES_PER_CONNECTION = 4;

    private static volatile Server uniqueInstance;

    public static Server createServer() {
        if (uniqueInstance == null) {
            synchronized (Server.class) {
                if (uniqueInstance == null) {
                    uniqueInstance =
                            new Server();
                }
            }
        }

        return uniqueInstance;
    }

    private static volatile l1j.server.server.model.network.core.connection.CIOServer gameListener;

    public static boolean isJavaNetworkTransport() {
        final var listener = gameListener;
        return listener != null && listener.isJavaTransport();
    }

    /** Counts: all retained sockets, authenticated sockets, entering accounts, duplicate denials. */
    public static long[] networkAccountRegistryDiagnostics(){
        final var listener=gameListener;return listener==null?new long[4]:listener.accountRegistryDiagnostics();
    }
    public static long[][] networkSocketDiagnostics(){final var listener=gameListener;return listener==null?new long[0][]:listener.ReportSocket();}
    public static String networkPoolReport(){final var listener=gameListener;return listener==null?"게임 네트워크 시작 전입니다.":listener.poolReport();}
    public static long[][] networkPoolSnapshot() {
        final var listener = gameListener;
        return listener == null ? new long[0][] : listener.poolSnapshot();
    }
    public static long findEnteringAccount(int accountId){
        final var listener=gameListener;return listener==null?0:listener.findEnteringAccount(accountId);
    }


    public static synchronized boolean startLoginServer() {
        if (gameListener != null) {
            return false;
        }

        final InetSocketAddress address =
                new InetSocketAddress(Config.GAME_SERVER_PORT);
        try {
            final int maxOnline = Config.MAX_ONLINE_USERS;
            if (maxOnline < 1) {
                throw new IllegalStateException("MaximumOnlineUsers must be positive");
            }
            final int connectionCapacity = Math.min(65535,
                    maxOnline + Math.max(256, maxOnline >>> 3));
            // A socket can own an in-flight SEND and queued
            // SEND leases at the same time. One global lease per possible
            // connection rejects valid burst traffic. Bitmap slots are bounded;
            // direct buffers themselves still allocate only on first use.
            final int sendSlots = (int) Math.min(65535L,
                    (long) connectionCapacity * SEND_LEASES_PER_CONNECTION);
            final int largeSlots = Math.min(connectionCapacity, 256);
            final int largeRecvSlots = Math.min(connectionCapacity, 512);
            // Java accepts on one owner. Leave room for a login burst while that
            // owner is also servicing established sockets and game timers.
            final int backlog = Math.max(64, Math.min(connectionCapacity, MAX_ACCEPT_BACKLOG));
            final CIOConfig config = new CIOConfig(address);
            final CIOThreadCallback scheduler = CIOThreadCallback.getInstance();
            final int owners = scheduler.bindNetworkIngress(config);
            final CIOBufferPool pool = new CIOBufferPool(connectionCapacity,
                    sendSlots, largeRecvSlots, largeSlots, owners);
            gameListener = new l1j.server.server.model.network.core.connection.CIOServer(address, connectionCapacity,
                    owners, backlog, pool, scheduler, config);
            l1j.server.server.packets.Protocol.MainServer.PlaySupport.window.PlaySupportWindow.startIndependent();
            System.out.println("네트워크 서버 연결이 완료되었습니다.");
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static long networkShutdownDeadlineNs; // guarded by Server.class

    /** One deadline across accept stop, Selector owner drain, and Java await. */
    public static void stopNewConnections() {
        final l1j.server.server.model.network.core.connection.CIOServer listener;
        final long deadline;
        synchronized (Server.class) {
            listener = gameListener;
            if (listener == null) return;
            if (listener.isAdmissionThread())
                throw new IllegalStateException("shutdown from admission owner");
            deadline = System.nanoTime() + 30_000_000_000L;
            networkShutdownDeadlineNs = deadline;
        }
        listener.stopAccepting();
        try {
            final long remaining = Math.max(0L, deadline - System.nanoTime());
            if (!listener.awaitAcceptStopped(remaining, java.util.concurrent.TimeUnit.NANOSECONDS))
                throw new IllegalStateException("CIO accept shutdown exceeded 30 seconds");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("CIO accept shutdown interrupted", e);
        }
    }

    public static void stopLoginServer() {
        final l1j.server.server.model.network.core.connection.CIOServer listener;
        final long recordedDeadline;
        synchronized (Server.class) {
            listener = gameListener;
            if (listener == null) return;
            recordedDeadline = networkShutdownDeadlineNs;
        }
        final long deadline = recordedDeadline != 0L ? recordedDeadline : System.nanoTime() + 30_000_000_000L;
        try {
            listener.close(Math.max(0L, deadline - System.nanoTime()), java.util.concurrent.TimeUnit.NANOSECONDS);
            if (!listener.awaitTermination(Math.max(0L, deadline - System.nanoTime()),
                    java.util.concurrent.TimeUnit.NANOSECONDS))
                throw new IllegalStateException("CIO shutdown: transport owners still active");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("CIO shutdown interrupted", e);
        } finally {
            synchronized (Server.class) {
                if (gameListener == listener) {
                    if (listener.isTransportTerminated()) {
                        gameListener = null;
                        networkShutdownDeadlineNs = 0L;
                    } else if (networkShutdownDeadlineNs == recordedDeadline) {
                        // A retry gets a fresh budget once this attempt has returned.
                        networkShutdownDeadlineNs = 0L;
                    }
                }
            }
        }
    }

    public void changePort(int port) {
    }

    public void shutdown() {
        GameServer.getInstance().shutdown();
    }

    public static volatile PrintStream _dout;

    public class ConsoleScanner
            implements Runnable {

        @Override
        public void run() {
            try {
                ByteArrayOutputStream baos =
                        new ByteArrayOutputStream(
                                1024);

                _dout =
                        System.out;

                PrintStream ps =
                        new PrintStream(
                                baos);

                System.setOut(ps);
                System.setErr(ps);

                while (true) {
                    Thread.sleep(
                            1000L);

                    String s = null;

                    synchronized (baos) {
                        s =
                                baos.toString();

                        baos.reset();
                    }

                    if (s == null) {
                        continue;
                    }

                    s =
                            s.trim();

                    if (s.length() > 0) {
                        String msg =
                                String.format(
                                        "%s\r\n",
                                        s);

                        LoggerInstance
                                .getInstance()
                                .addCmd(
                                        msg);

                        _dout.print(
                                msg);
                    }
                }

            } catch (InterruptedException e) {
            } finally {
            }
        }
    }

    /**
     * 서버 메인.
     *
     * @param args 커멘드 라인 인수
     * @throws SQLException
     * @throws Exception
     */
    public Server() {
        new Thread(
                new ConsoleScanner())
                        .start();

        initLogManager();
        initDBFactory();

        try {
            PerformanceTimer timer =
                    new PerformanceTimer();

            timer.reset();
            timer = null;

        } catch (Exception e) {
        }
    }

    public static boolean startGameServer() {
        try {
            GameServer
                    .getInstance()
                    .initialize();
            if (!startLoginServer()) return false;
            AppCenterService.start();
            System.out.println("게임서버 연결이 완료되었습니다.");
            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private void initLogManager() {

        File logFolder =
                new File(
                        "log");

        logFolder.mkdir();

        try {
            InputStream is =
                    new BufferedInputStream(
                            new FileInputStream(
                                    LOG_PROP));

            LogManager
                    .getLogManager()
                    .readConfiguration(
                            is);

            is.close();

        } catch (IOException e) {
            System.exit(0);
        }

        try {
            // Config.load();
        } catch (Exception e) {
            System.exit(0);
        }
    }

    public static void printShutdownStatus(String message) {
        final PrintStream console = _dout;
        if (console != null) {
            console.println(message);
            console.flush();
        } else {
            System.out.println(message);
        }
    }

    public static void ClosingMent() {
        try {
            final String serverIp = InetAddress.getLocalHost().getHostAddress();
            System.out.println("────────────────────────────────────────────────────");
            System.out.println("  서버 주소 : " + serverIp);
            System.out.println("  서버 포트 : " + Config.GAME_SERVER_PORT);
            System.out.println("────────────────────────────────────────────────────");
            System.out.println("  >> LINEAGE SERVER INITIALIZATION COMPLETED. READY FOR CONNECTIONS.");
        } catch (Exception e) {
            System.out.println("  서버 포트 : " + Config.GAME_SERVER_PORT);
            System.out.println("  >> LINEAGE SERVER INITIALIZATION COMPLETED. READY FOR CONNECTIONS.");
        }
    }

    @SuppressWarnings("static-access")
    private void initDBFactory() {
        DatabaseFactory
                .getInstance()
                .init();
    }
}
