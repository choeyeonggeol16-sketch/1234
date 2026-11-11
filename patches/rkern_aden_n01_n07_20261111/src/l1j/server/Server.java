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
import l1j.server.server.model.network.core.CIOServer;
import l1j.server.server.model.network.core.CIOBufferPool;
import l1j.server.server.model.network.core.CIOSocketFactory;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.network.utill.ConnectionConfig;
import l1j.server.server.monitor.LoggerInstance;
import l1j.server.server.utils.PerformanceTimer;

/**
 * l1j-jp의 서버를 기동한다.
 */
public class Server {

    /** 로그 설정 파일의 폴더. */
    private static final String LOG_PROP =
            "./config/log.properties";

    // Native Windows IOCP transport settings.
    private static final int IOCP_COMPLETION_WORKERS = 8;
    private static final int IOCP_ACCEPT_BACKLOG = 1024;
    private static final int IOCP_SEND_LEASES_PER_CONNECTION = 4;

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

    private static volatile CIOServer gameListener;

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
            final int ioThreads = IOCP_COMPLETION_WORKERS;
            // A native socket can own an in-flight SEND and queued
            // SEND leases at the same time. One global lease per possible
            // connection rejects valid burst traffic. Bitmap slots are bounded;
            // direct buffers themselves still allocate only on first use.
            final int sendSlots = (int) Math.min(65535L,
                    (long) connectionCapacity * IOCP_SEND_LEASES_PER_CONNECTION);
            final int largeSlots = Math.min(connectionCapacity, 256);
            final int largeRecvSlots = Math.min(connectionCapacity, 512);
            final int backlog = IOCP_ACCEPT_BACKLOG;
            final ConnectionConfig config = new ConnectionConfig(address);
            final CIOBufferPool pool = new CIOBufferPool(connectionCapacity,
                    sendSlots, largeRecvSlots, largeSlots);
            final CIOSocketFactory factory = new CIOSocketFactory(
                    CIOThreadCallback.getInstance(), config);
            gameListener = new CIOServer(address, connectionCapacity,
                    ioThreads, backlog, pool, factory);
            System.out.println("네트워크 서버 연결이 완료되었습니다.");
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static volatile long networkShutdownDeadlineNs;

    /** One deadline across accept stop, native worker join, and Java await. */
    public static void stopNewConnections() {
        final CIOServer listener = gameListener;
        if (listener == null) return;
        final long deadline = System.nanoTime() + 30_000_000_000L;
        networkShutdownDeadlineNs = deadline;
        listener.stopAccepting();
        if (listener.isAdmissionThread())
            throw new IllegalStateException("shutdown from admission owner");
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
        final CIOServer listener = gameListener;
        if (listener == null) return;
        final long deadline = networkShutdownDeadlineNs != 0L
                ? networkShutdownDeadlineNs : System.nanoTime() + 30_000_000_000L;
        final long remaining = Math.max(0L, deadline - System.nanoTime());
        if (remaining == 0L)
            throw new IllegalStateException("CIO shutdown deadline expired before native Stop");
        listener.close(remaining, java.util.concurrent.TimeUnit.NANOSECONDS);
        try {
            if (!listener.awaitTermination(Math.max(0L, deadline - System.nanoTime()),
                    java.util.concurrent.TimeUnit.NANOSECONDS))
                throw new IllegalStateException("CIO shutdown: native workers still active");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("CIO shutdown interrupted", e);
        }
        synchronized (Server.class) {
            if (gameListener == listener) gameListener = null;
            networkShutdownDeadlineNs = 0L;
        }
    }

    public void changePort(int port) {
        // MJNetServerLoadManager.reload();
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
