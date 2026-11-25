import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import l1j.server.server.Opcodes;
import l1j.server.server.model.network.buffer.CPacketReadBuffer;
import l1j.server.server.model.network.core.transport.selector.CSelectorTransport;
import l1j.server.server.model.network.core.transport.selector.CSelectorTransport.CloseReason;
import l1j.server.server.model.network.core.transport.selector.CSelectorTransport.Connection;

public final class SelectorTransportTest {
    private static final InetSocketAddress LOOPBACK = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
    private static final int BURST_FRAMES = 16;
    private static final int BURST_BODY_BYTES = 60_000;

    public static void main(String[] args) throws Exception {
        checkMailboxAndShutdown();
        checkConcurrentMailbox();
        checkOwnerFailure();
        checkCallbackFailureIsolation();
        checkRawGracefulGather();
        checkRawGatherTail();
        checkHostedAttachFailure();
        checkHostedWaitBeforeAdvance();
        checkIndexedDeadlines();
        System.out.println("PASS raw TCP callbacks, graceful gather, mailbox concurrency, deadlines and hosted rollback");
    }

    private static CSelectorTransport.Config config(int owners, int connectionsPerOwner, int mailboxCapacity) {
        return new CSelectorTransport.Config(owners, connectionsPerOwner, 32, mailboxCapacity,
                2, 8, BURST_FRAMES, 1_000_000, 32, 4_096, Duration.ofSeconds(3));
    }

    private static void checkCallbackFailureIsolation() throws Exception {
        var failed = new AtomicReference<Connection>();
        var calls = new AtomicInteger();
        var handler = new CSelectorTransport.RawHandler() {
            @Override public void onOpen(Connection socket) {
                var scratch = ByteBuffer.allocate(8);
                if (failed.compareAndSet(null, socket)) {
                    check(socket.readRaw(scratch, bytes -> injectedCancellation(calls, bytes)), "raw READ accepted");
                    check(socket.writeRaw(ByteBuffer.wrap(new byte[] {42}), bytes -> injectedCancellation(calls, bytes)),
                            "raw WRITE accepted");
                    check(socket.submit(new CSelectorTransport.OwnerCommand() {
                        @Override public void run(Connection ignored) { throw new AssertionError("closed command ran"); }
                        @Override public void cancel() { injectedCancellation(calls, -1); }
                    }, true), "deferred command accepted");
                    socket.abort();
                } else socket.readRaw(scratch, bytes -> {
                    if (bytes > 0) {
                        scratch.flip();
                        socket.writeRaw(scratch, ignored -> {});
                    }
                });
            }

            @Override public void onClosed(Connection socket, CloseReason reason) {
                if (socket == failed.get()) injectedCancellation(calls, -1);
            }
        };
        try (var server = CSelectorTransport.bind(LOOPBACK, config(1, 1, 4), handler);
                var broken = connect(server, 0)) {
            check(broken.getInputStream().read() == -1, "only the failed connection closed");
            try (var healthy = connect(server, 0)) {
                healthy.getOutputStream().write(73);
                check(healthy.getInputStream().read() == 73, "same owner still serves peers and reused its admission");
            }
            server.close();
            check(calls.get() == 4,
                    "READ, WRITE, terminal, and queued command callbacks each ran once");
            balanced(server.shutdownStats());
        }
    }

    private static void injectedCancellation(AtomicInteger calls, long bytes) {
        check(bytes == -1, "an aborted operation reports explicit failure");
        calls.incrementAndGet();
        throw new IllegalStateException("injected callback failure");
    }

    private static void checkRawGracefulGather() throws Exception {
        var completed = new LinkedBlockingQueue<Long>();
        var payload = new byte[512 * 1024];
        for (int i = 0; i < payload.length; i++) payload[i] = sample(1, i);
        var handler = new CSelectorTransport.RawHandler() {
            @Override public void onOpen(Connection socket) {
                int half = payload.length / 2;
                ByteBuffer[] buffers = {ByteBuffer.wrap(payload, 0, half), ByteBuffer.wrap(payload, half, half)};
                check(socket.writeRaw(buffers, 0, 2, completed::add), "raw gather accepted");
                socket.closeAfterFlush();
                check(!socket.writeRaw(ByteBuffer.wrap(new byte[] {1}), ignored -> {}), "drain rejects new writes");
            }
        };
        try (var server = CSelectorTransport.bind(LOOPBACK, config(1, 1, 4), handler);
                var socket = connect(server, 16_384)) {
            Thread.sleep(150); // let the small socket send window fill before reading
            check(Arrays.equals(payload, socket.getInputStream().readNBytes(payload.length)), "raw gather residual precedes FIN");
            check(socket.getInputStream().read() == -1, "raw drain sends FIN");
            check(await(completed) == payload.length && completed.isEmpty(), "one completion for the entire pending gather");
            server.close();
            balanced(server.shutdownStats());
        }
    }

    private static void checkRawGatherTail() throws Exception {
        var completed = new LinkedBlockingQueue<Long>();
        var handler = new CSelectorTransport.RawHandler() {
            @Override public void onOpen(Connection socket) {
                ByteBuffer[] buffers = {ByteBuffer.wrap(new byte[] {9}), ByteBuffer.wrap(new byte[] {4, 5, 6})};
                check(socket.writeRaw(buffers, 1, 1, completed::add), "remaining gather segment accepted");
                socket.closeAfterFlush();
            }
        };
        try (var server = CSelectorTransport.bind(LOOPBACK, config(1, 1, 4), handler);
                var socket = connect(server, 0)) {
            check(Arrays.equals(socket.getInputStream().readAllBytes(), new byte[] {4, 5, 6}), "only the selected suffix reaches the peer");
            check(await(completed) == 3L && completed.isEmpty(), "gather suffix has one completion");
        }
    }

    private static void checkHostedAttachFailure() throws Exception {
        InetSocketAddress address;
        try (var probe = new ServerSocket(0, 1, LOOPBACK.getAddress())) {
            address = (InetSocketAddress) probe.getLocalSocketAddress();
        }
        var published = new AtomicReference<CSelectorTransport.OwnerDriver>();
        CSelectorTransport.RawHandler noPackets = new CSelectorTransport.RawHandler() {};
        try {
            CSelectorTransport.bindHosted(address, config(2, 2, 4), noPackets, drivers -> {
                published.set(drivers.getFirst());
                drivers.getFirst().advance();
                throw new IllegalStateException("injected partial attachment");
            });
            throw new AssertionError("partial attachment must fail");
        } catch (IllegalStateException expected) {
            check(expected.getMessage().equals("injected partial attachment"), "startup preserves the original exception");
        }
        check(!published.get().active(), "published driver retired after failed attachment");
        try {
            CSelectorTransport.bindHosted(address, config(2, 2, 4), noPackets, drivers -> drivers.getFirst().closeOnOwner());
            throw new AssertionError("concurrent stop during attachment must fail startup");
        } catch (IllegalStateException expected) {
            check(expected.getMessage().equals("Hosted Selector stopped during attachment"), "stop cancels startup");
        }
        try (var rebound = CSelectorTransport.bind(address, config(1, 1, 4), noPackets)) {
            check(rebound.localAddress().equals(address), "listener can rebind after both failures");
        }
    }

    private static void checkIndexedDeadlines() throws Exception {
        final int count = 128;
        var opened = new LinkedBlockingQueue<Connection>();
        var deadlines = new LinkedBlockingQueue<Integer>();
        var handler = new CSelectorTransport.RawHandler() {
            @Override public void onOpen(Connection socket) {
                socket.scheduleDeadline(30_000, () -> { throw new AssertionError("stale deadline fired"); });
                opened.add(socket);
            }
        };
        var peers = new ArrayList<Socket>();
        try (var server = CSelectorTransport.bind(LOOPBACK, config(1, count, 8), handler)) {
            var sockets = new ArrayList<Connection>();
            for (int i = 0; i < count; i++) peers.add(connect(server, 0));
            for (int i = 0; i < count; i++) sockets.add(await(opened));
            check(server.ownerDiagnostics().getFirst().deadlines() == count, "one deadline per connection");
            Connection first = sockets.get(0);
            check(first.tryExecute(owner -> {
                // Remove interior entries. Advance deadlines in reverse order.
                for (int i = 4; i < count; i += 2) sockets.get(i).abort();
                sockets.get(3).scheduleDeadline(750, () -> deadlines.add(3));
                sockets.get(1).scheduleDeadline(70, () -> {
                    deadlines.add(1);
                    sockets.get(1).scheduleDeadline(70, () -> deadlines.add(2));
                });
                sockets.get(1).scheduleDeadline(20_000, () -> { throw new AssertionError("later deadline replaced earlier"); });
                sockets.get(2).scheduleDeadline(1, () -> { throw new AssertionError("closed socket timer fired"); });
                sockets.get(2).abort();
            }), "deadline mutations admitted");
            for (int i = 1; i <= 3; i++) check(await(deadlines) == i, "advance, callback rearm, and deadline order");
            server.close();
            check(server.ownerDiagnostics().getFirst().deadlines() == 0, "all remaining timers detached at shutdown");
            balanced(server.shutdownStats());
        } finally {
            for (Socket peer : peers) peer.close();
        }
    }

    private static void checkHostedWaitBeforeAdvance() throws Exception {
        var owner = new AtomicReference<CSelectorTransport.OwnerDriver>();
        var failure = new AtomicReference<Throwable>();
        try (var server = CSelectorTransport.bindHosted(LOOPBACK, config(1, 1, 4), new CSelectorTransport.RawHandler() {}, drivers -> {
            owner.set(drivers.getFirst());
            drivers.getFirst().advance(); // Before the transport commits attachment, this must return without initializing.
        })) {
            Thread thread = Thread.ofPlatform().start(() -> {
                var driver = owner.get();
                try { driver.awaitWork(0); }
                catch (Throwable unexpected) { failure.set(unexpected); }
                finally { driver.closeOnOwner(); }
            });
            thread.join(5000);
            check(!thread.isAlive(), "hosted owner joined after wait-first startup");
            if (failure.get() != null) throw new AssertionError("hosted wait raced attachment", failure.get());
            server.close();
            balanced(server.shutdownStats());
        }
    }

    private static void checkMailboxAndShutdown() throws Exception {
        var opened = new LinkedBlockingQueue<Connection>();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger executed = new AtomicInteger();
        AtomicInteger canceled = new AtomicInteger();
        try (CSelectorTransport server = CSelectorTransport.bind(LOOPBACK, config(1, 2, 2), new CSelectorTransport.RawHandler() {
            @Override public void onOpen(Connection connection) { opened.add(connection); }
        }); Socket peer = connect(server, 0)) {
            Connection connection = await(opened);
            try {
                check(connection.tryExecute(ownerConnection -> {
                    started.countDown();
                    try { await(release); }
                    catch (InterruptedException e) { throw new IOException(e); }
                }), "owner command admitted");
                await(started);
                CSelectorTransport.OwnerCommand pending = new CSelectorTransport.OwnerCommand() {
                    @Override public void run(Connection ignored) { executed.incrementAndGet(); }
                    @Override public void cancel() { canceled.incrementAndGet(); }
                };
                check(connection.tryExecute(pending) && connection.tryExecute(pending), "mailbox holds exactly two");
                check(!connection.tryExecute(pending), "full mailbox refuses a third command");
                var owner = server.ownerDiagnostics().getFirst();
                check(owner.admitted() == 1 && owner.queuedCommands() == 2,
                        "saturated mailbox has two pending commands");
                server.requestStop();
                check(!connection.tryExecute(pending), "stop seals mailbox admission");
                check(!server.awaitTermination(Duration.ofMillis(20)), "termination reflects in-flight owner work");
            } finally {
                release.countDown();
            }
            server.close();
            check(executed.get() == 0 && canceled.get() == 2, "accepted commands canceled exactly once on shutdown");
            balanced(server.shutdownStats());
        }
    }

    private static void checkConcurrentMailbox() throws Exception {
        var opened = new LinkedBlockingQueue<Connection>();
        var finished = new CountDownLatch(20 * 100);
        var executed = new int[20];
        try (var server = CSelectorTransport.bind(LOOPBACK, config(1, 2, 33), new CSelectorTransport.RawHandler() {
            @Override public void onOpen(Connection connection) { opened.add(connection); }
        }); var peer = connect(server, 0); var producers = Executors.newVirtualThreadPerTaskExecutor()) {
            Connection connection = await(opened);
            var submitted = new ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < executed.length; i++) {
                int producer = i;
                submitted.add(producers.submit(() -> {
                    for (int sequence = 0; sequence < 100; sequence++) {
                        int expected = sequence;
                        CSelectorTransport.OwnerCommand command = ignored -> {
                            check(executed[producer]++ == expected, "producer FIFO and exactly-once execution");
                            finished.countDown();
                        };
                        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                        while (!connection.tryExecute(command)) {
                            check(System.nanoTime() < deadline, "owner kept making progress");
                            Thread.yield();
                        }
                    }
                }));
            }
            for (var submit : submitted) submit.get(6, TimeUnit.SECONDS);
            await(finished);
            check(Arrays.stream(executed).allMatch(count -> count == 100), "all admitted commands executed");
            server.close();
            balanced(server.shutdownStats());
        }
    }

    private static void checkOwnerFailure() throws Exception {
        var failure = new AssertionError("injected owner failure");
        var transport = CSelectorTransport.bind(LOOPBACK, config(2, 2, 8), new CSelectorTransport.RawHandler() {
            @Override public void onOpen(Connection connection) {
                throw failure;
            }
        });
        try (var peer = connect(transport, 0)) {
            check(transport.awaitTermination(Duration.ofSeconds(5)), "owner failure stopped and joined both owners");
            check(transport.shutdownFailure() == failure, "original fatal cause preserved");
            balanced(transport.shutdownStats());
        } finally {
            try {
                transport.close();
                throw new AssertionError("closing failed transport should report the cause");
            } catch (IOException expected) {
                check(expected.getCause() == failure, "close preserved the fatal cause");
            }
        }
    }

    /** Byte-at-a-time reference for the separate client-to-server and server-to-client streams. */
    static final class ScalarClient {
        private final int[] outboundTable = new int[256];
        private int clientWrite0;
        private int clientWrite1;

        ScalarClient(long seed) {
            clientWrite0 = Integer.rotateRight((int) seed ^ 0x9C30D539, 13);
            clientWrite1 = 0x930FD7E2 ^ clientWrite0 ^ 0x7C72E993;
            int index = 0;
            for (int i = 0; i < 256; i++) outboundTable[i] = i;
            for (int i = 0; i < 256; i++) {
                int key = (i & 7) < 4 ? clientWrite0 : clientWrite1;
                index = (index + outboundTable[i] + keyByte(key, i)) & 255;
                swap(i, index);
            }
        }

        byte[] toServer(byte[] body) {
            byte[] encoded = new byte[body.length + 4];
            encoded[3] = 0x17;
            int previous = encoded[3] ^ keyByte(clientWrite0, 2);
            for (int i = 0; i < body.length; i++) {
                int key = (i & 7) < 4 ? clientWrite1 : clientWrite0;
                encoded[i + 4] = (byte) (body[i] ^ previous ^ keyByte(key, i));
                previous = encoded[i + 4];
            }
            clientWrite0 ^= ByteBuffer.wrap(body).order(ByteOrder.LITTLE_ENDIAN).getInt();
            clientWrite1 += 679411651;
            return encoded;
        }

        byte[] fromServer(byte[] body) {
            byte[] encoded = body.clone();
            int j = 1;
            int k = 0;
            for (int i = 0; i < encoded.length; i++) {
                k = (k + outboundTable[j]) & 255;
                swap(j, k);
                encoded[i] ^= (byte) outboundTable[(outboundTable[j] + outboundTable[k]) & 255];
                j = (j + 1) & 255;
            }
            return encoded;
        }

        private static int keyByte(int key, int index) { return (key >>> (8 * (index & 3))) & 255; }

        private void swap(int left, int right) {
            int value = outboundTable[left];
            outboundTable[left] = outboundTable[right];
            outboundTable[right] = value;
        }
    }

    private static Socket connect(CSelectorTransport server, int receiveBytes) throws IOException {
        Socket socket = new Socket();
        try {
            if (receiveBytes > 0) socket.setReceiveBufferSize(receiveBytes);
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(5000);
            socket.connect(server.localAddress(), 3000);
            return socket;
        } catch (IOException e) {
            socket.close();
            throw e;
        }
    }

    private static byte sample(int sequence, int index) { return (byte) (sequence * 17 + index * 7); }

    private static byte[] frame(byte[] body) {
        return ByteBuffer.allocate(body.length + 2).order(ByteOrder.LITTLE_ENDIAN)
                .putShort((short) (body.length + 2)).put(body).array();
    }

    private static byte[] readFrame(Socket socket) throws IOException {
        InputStream in = socket.getInputStream();
        int low = in.read();
        int high = in.read();
        if (low < 0 || high < 0) throw new EOFException("expected framed response");
        int payloadLength = (low | (high << 8)) - 2;
        byte[] body = in.readNBytes(payloadLength);
        check(body.length == payloadLength, "complete framed response");
        return body;
    }

    private static void expectFrame(Socket socket, byte[] expected) throws IOException {
        byte[] actual = readFrame(socket);
        check(Arrays.equals(actual, expected), "expected " + Arrays.toString(expected) + ", received " + Arrays.toString(actual));
    }

    private static void balanced(List<CSelectorTransport.OwnerStats> stats) {
        for (CSelectorTransport.OwnerStats owner : stats) {
            check(owner.remainingConnections() == 0 && owner.remainingCommands() == 0, "no live connections or commands");
        }
    }

    private static <T> T await(LinkedBlockingQueue<T> queue) throws InterruptedException {
        T result = queue.poll(5, TimeUnit.SECONDS);
        if (result == null) throw new AssertionError("Timed out waiting for owner event");
        return result;
    }

    private static void await(CountDownLatch latch) throws InterruptedException {
        check(latch.await(5, TimeUnit.SECONDS), "Timed out waiting for test signal");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
