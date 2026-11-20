import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import l1j.server.Config;
import l1j.server.Server;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.packet.BytesWritablePacket;
import l1j.server.server.model.network.packet.CPacketHandler;
import l1j.server.server.model.network.packet.WritablePacket;
import l1j.server.server.model.network.safe.NetworkSafeLoadManager;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.packets.Protocol.MainServer.PlaySupport.window.PlaySupportWindow;

/** Lockstep benchmark client and actual DLL-free game listener in separate JVMs. */
public final class GameSendBenchmark {
    private static final int REQUEST = Opcodes.C_ALIVE & 255;
    private static final int RESPONSE = 0x45;

    public static void main(String[] args) throws Exception {
        if (args[0].equals("server")) serve(Integer.parseInt(args[1]), Integer.parseInt(args[2]),
                Integer.parseInt(args[3]), args[4]);
        else drive(Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]),
                Integer.parseInt(args[4]), Integer.parseInt(args[5]), Integer.parseInt(args[6]));
    }

    private static int port() throws Exception {
        try (var socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress("127.0.0.1", 0));
            return socket.getLocalPort();
        }
    }

    private static void serve(int clients, int owners, int payload, String packetKind) throws Exception {
        PrintStream protocol = System.out;
        System.setOut(System.err);
        Files.createDirectory(Path.of("config"));
        Files.writeString(Path.of("config/Network.ini"), "DisruptorShardCount=" + owners
                + "\nNetworkClientPermission=" + (clients + 2)
                + "\nMaxPacketsPerCompletion=32\nPacketLimitPerSec=1000000\nDropPackets=false\nUsePreLoginTimeout=false\n");
        Config.MAX_ONLINE_USERS = (short) (clients + 2);
        Config.GAME_SERVER_PORT = port();
        int pssPort = port();
        while (pssPort == Config.GAME_SERVER_PORT) pssPort = port();
        System.setProperty("pss.uiBridge.port", Integer.toString(pssPort));
        NetworkSafeLoadManager.getInstance().load();
        var sessions = ConcurrentHashMap.<CUserSocket>newKeySet();
        var rejected = new AtomicInteger();
        var locals = ThreadLocal.withInitial(() -> new Reply(payload));
        var old = CPacketHandler.HANDLERS[REQUEST];
        CPacketHandler.HANDLERS[REQUEST] = (request, client) -> {
            CUserSocket socket = client.getConnection();
            if (socket.affinityShard() != CIOThreadCallback.currentShardIndex()) throw new AssertionError("shard");
            Reply packet = locals.get();
            int sequence = request.readInt();
            if (sequence == 0) sessions.add(socket);
            body(packet.body, RESPONSE, sequence);
            boolean bytes = packetKind.equals("bytes") || (packetKind.equals("mixed") && (sequence & 1) == 0);
            if (!socket.tryWritePacket(client, bytes ? new BytesWritablePacket(packet.body) : packet)) {
                rejected.incrementAndGet();
                return false;
            }
            return true;
        };
        try {
            if (!Server.startLoginServer()) throw new AssertionError("listener failed");
            protocol.println("READY " + Config.GAME_SERVER_PORT);
            var cpu = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            var threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
            if (threads.isThreadAllocatedMemorySupported() && !threads.isThreadAllocatedMemoryEnabled())
                threads.setThreadAllocatedMemoryEnabled(true);
            var input = new BufferedReader(new InputStreamReader(System.in));
            for (String command; (command = input.readLine()) != null; ) {
                if (command.equals("STOP")) break;
                if (!command.equals("SAMPLE")) throw new IllegalArgumentException(command);
                protocol.printf(Locale.ROOT, "SAMPLE %d %d %d %d %d%n", cpu.getProcessCpuTime(),
                        threads.getTotalThreadAllocatedBytes(), sessions.stream().mapToLong(CUserSocket::javaDirectPacketCommits).sum(),
                        sessions.stream().mapToLong(CUserSocket::javaDirectByteAppends).sum(), rejected.get());
            }
            if (rejected.get() != 0) throw new AssertionError("SEND rejected: " + rejected.get());
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (Server.networkAccountRegistryDiagnostics()[0] != 0 && System.nanoTime() < deadline) Thread.sleep(5);
            String pool = Server.networkPoolReport();
            if (!pool.contains("receive slots=0 send slots=0")) throw new AssertionError(pool);
            protocol.println("DRAINED " + pool);
        } finally {
            try { Server.stopLoginServer(); }
            finally {
                CPacketHandler.HANDLERS[REQUEST] = old;
                PlaySupportWindow.stopIndependent();
                CIOThreadCallback scheduler = CIOThreadCallback.getInstance();
                scheduler.shutdown();
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) throw new AssertionError("CIO shutdown");
                l1j.server.server.ThreadPool.getInstance().shutdown();
            }
        }
    }

    private static void drive(int port, int clients, int window, int payload, int warmupMillis, int measureMillis)
            throws Exception {
        var ready = new CountDownLatch(clients);
        var go = new CountDownLatch(1);
        var begin = new AtomicLong();
        long measure = TimeUnit.MILLISECONDS.toNanos(measureMillis);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var samples = new ArrayList<Future<long[]>>();
            for (int connection = 0; connection < clients; connection++) samples.add(executor.submit(() -> {
                try (var socket = new Socket()) {
                    socket.setTcpNoDelay(true);
                    socket.setSoTimeout(5000);
                    socket.connect(new InetSocketAddress("127.0.0.1", port), 5000);
                    var in = new DataInputStream(socket.getInputStream());
                    var out = socket.getOutputStream();
                    if (!Arrays.equals(read(in), Opcodes.S_KEYBYTES)) throw new AssertionError("initial key");
                    var codec = new SelectorTransportTest.ScalarClient(Opcodes.getSeed());
                    var request = new byte[5];
                    var expected = new byte[payload];
                    var batch = new byte[window * 11]; // 2 byte framing + 4 byte cipher prefix + 5 byte request
                    ready.countDown();
                    go.await();
                    long from = begin.get(), to = from + measure;
                    long[] measured = new long[512];
                    int count = 0, sequence = 0;
                    while (System.nanoTime() < to) {
                        for (int i = 0; i < window; i++) {
                            body(request, REQUEST, sequence + i);
                            byte[] encoded = codec.toServer(request);
                            int offset = i * 11;
                            batch[offset] = 11;
                            System.arraycopy(encoded, 0, batch, offset + 2, encoded.length);
                        }
                        long sent = System.nanoTime();
                        out.write(batch);
                        for (int i = 0; i < window; i++) {
                            body(expected, RESPONSE, sequence++);
                            if (!Arrays.equals(read(in), codec.fromServer(expected))) throw new AssertionError("wire mismatch");
                            long received = System.nanoTime();
                            if (sent >= from && received <= to) {
                                if (count == measured.length) measured = Arrays.copyOf(measured, count * 2);
                                measured[count++] = received - sent;
                            }
                        }
                    }
                    return Arrays.copyOf(measured, count);
                }
            }));
            try {
                if (!ready.await(6, TimeUnit.SECONDS)) throw new AssertionError("client connect timeout");
                System.out.println("READY");
                if (System.in.read() != '\n') throw new AssertionError("expected start");
                begin.set(System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(warmupMillis));
                go.countDown();
                waitUntil(begin.get());
                System.out.println("MEASURE");
                waitUntil(begin.get() + measure);
                System.out.println("END");
                var all = new ArrayList<long[]>();
                int count = 0;
                for (var sample : samples) {
                    long[] result = sample.get(8, TimeUnit.SECONDS);
                    count += result.length;
                    all.add(result);
                }
                if (count == 0) throw new AssertionError("no samples");
                long[] sorted = new long[count];
                int offset = 0;
                for (long[] result : all) {
                    System.arraycopy(result, 0, sorted, offset, result.length);
                    offset += result.length;
                }
                Arrays.sort(sorted);
                System.out.printf(Locale.ROOT, "RESULT %d %.2f %.3f %.3f %.3f%n", count, count * 1e9 / measure,
                        percentile(sorted, 500), percentile(sorted, 990), percentile(sorted, 999));
            } finally {
                go.countDown();
                executor.shutdownNow();
            }
        }
    }

    private static void waitUntil(long end) throws InterruptedException {
        long wait;
        while ((wait = end - System.nanoTime()) > 0) TimeUnit.NANOSECONDS.sleep(wait);
    }

    private static double percentile(long[] sorted, int rank) {
        return sorted[(int) ((sorted.length * (long) rank + 999) / 1000) - 1] / 1000.0;
    }

    private static byte[] read(DataInputStream in) throws Exception {
        int length = in.readUnsignedByte() | (in.readUnsignedByte() << 8);
        if (length < 3) throw new AssertionError("frame length " + length);
        byte[] body = new byte[length - 2];
        in.readFully(body);
        return body;
    }

    private static void body(byte[] data, int opcode, int sequence) {
        data[0] = (byte) opcode;
        for (int i = 0; i < 4; i++) data[i + 1] = (byte) (sequence >>> (i * 8));
        Arrays.fill(data, 5, data.length, (byte) sequence);
    }

    private static final class Reply extends WritablePacket<CIOClient> {
        final byte[] body;
        Reply(int payload) { body = new byte[payload]; }
        @Override protected boolean write(CIOClient client, WriteBuffer writer) {
            writer.writeBytes(body);
            return true;
        }
    }
}
