import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import l1j.server.server.Opcodes;
import l1j.server.server.model.network.buffer.ReadBuffer;
import l1j.server.server.model.network.core.transport.selector.SelectorBufferPool;
import l1j.server.server.model.network.core.transport.selector.SelectorTransport;
import l1j.server.server.model.network.core.transport.selector.SelectorTransport.CloseReason;
import l1j.server.server.model.network.core.transport.selector.SelectorTransport.Connection;

public final class SelectorTransportTest {
    private static final InetSocketAddress LOOPBACK = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
    private static final int BURST_FRAMES = 16;
    private static final int BURST_BODY_BYTES = 60_000;

    public static void main(String[] args) throws Exception {
        checkOwnersAndFraming();
        checkSendAndGracefulClose();
        checkWriteStall();
        checkMailboxAndShutdown();
        checkCipher();
        System.out.println("PASS multi-owner TCP, read/write, bounds, mailbox shutdown and Java cipher vectors");
    }

    private static SelectorTransport.Config config(int owners, int connectionsPerOwner, int mailboxCapacity) {
        return new SelectorTransport.Config(owners, connectionsPerOwner, 32, mailboxCapacity,
                2, 8, BURST_FRAMES, 1_000_000, 32, 4_096, Duration.ofSeconds(3));
    }

    private static void checkOwnersAndFraming() throws Exception {
        var opened = new LinkedBlockingQueue<Connection>();
        SelectorTransport.Handler handler = new SelectorTransport.Handler() {
            @Override public void onOpen(Connection connection) { opened.add(connection); }
            @Override public void onFrame(Connection connection, ReadBuffer payload) throws IOException {
                int size = payload.remaining();
                check(connection.trySend(size, writer -> {
                    while (payload.remaining() != 0) writer.writeByte(payload.readByte());
                }), "echo send");
            }
        };
        try (SelectorTransport server = SelectorTransport.bind(LOOPBACK, config(3, 2, 8), handler)) {
            Socket[] clients = new Socket[6];
            Set<Integer> ownerIds = new HashSet<>();
            try {
                for (int i = 0; i < clients.length; i++) {
                    clients[i] = connect(server, 0);
                    ownerIds.add(await(opened).ownerIndex());
                }
                check(ownerIds.equals(Set.of(0, 1, 2)), "round-robin uses all three owners");
                try (Socket overflow = connect(server, 0)) {
                    check(overflow.getInputStream().read() == -1, "unused admission is bounded");
                }
                for (int i = 0; i < clients.length; i++) {
                    Socket socket = clients[i];
                    byte[] p1 = {(byte) i, 11, 12};
                    byte[] p2 = {(byte) i, 21, 22, 23};
                    byte[] wire = frame(p1);
                    socket.getOutputStream().write(wire, 0, 1);
                    socket.getOutputStream().write(ByteBuffer.allocate(wire.length - 1 + p2.length + 2)
                            .put(wire, 1, wire.length - 1).put(frame(p2)).array());
                    expectFrame(socket, p1);
                    expectFrame(socket, p2);
                    socket.shutdownOutput();
                    check(socket.getInputStream().read() == -1, "remote half-close drains");
                }
            } finally {
                for (Socket client : clients) if (client != null) client.close();
            }
            server.close();
            balanced(server.shutdownStats());
        }
    }

    private static void checkSendAndGracefulClose() throws Exception {
        var closed = new LinkedBlockingQueue<CloseReason>();
        CountDownLatch queued = new CountDownLatch(1);
        try (SelectorTransport server = SelectorTransport.bind(LOOPBACK, config(1, 2, 8), burst(queued, closed, true));
                Socket socket = connect(server, 16_384)) {
            socket.getOutputStream().write(frame(new byte[] {1}));
            await(queued);
            Thread.sleep(150); // permit a real socket send window to fill before reading
            for (int f = 0; f < BURST_FRAMES; f++) {
                byte[] received = readFrame(socket);
                check(received.length == BURST_BODY_BYTES, "burst frame length");
                for (int i = 0; i < received.length; i++) check(received[i] == sample(f, i), "burst FIFO/partial bytes");
            }
            check(socket.getInputStream().read() == -1, "graceful close sends FIN after the whole FIFO");
            check(await(closed) == CloseReason.APPLICATION, "graceful close reason");
            server.close();
            balanced(server.shutdownStats());
            check(server.shutdownStats().getFirst().zeroWrites() > 0, "test actually hit socket backpressure");
        }
    }

    private static void checkWriteStall() throws Exception {
        var closed = new LinkedBlockingQueue<CloseReason>();
        CountDownLatch queued = new CountDownLatch(1);
        var config = new SelectorTransport.Config(1, 1, 32, 8, 1, 1, BURST_FRAMES,
                1_000_000, 32, 4_096, Duration.ofMillis(300));
        try (SelectorTransport server = SelectorTransport.bind(LOOPBACK, config, burst(queued, closed, false));
                Socket socket = connect(server, 8_192)) {
            socket.getOutputStream().write(frame(new byte[] {1}));
            await(queued);
            check(await(closed) == CloseReason.WRITE_TIMEOUT, "slow peer timed out without reading");
            server.close();
            balanced(server.shutdownStats());
        }
    }

    private static SelectorTransport.Handler burst(CountDownLatch queued, LinkedBlockingQueue<CloseReason> closed,
            boolean graceful) {
        return new SelectorTransport.Handler() {
            @Override public void onFrame(Connection connection, ReadBuffer payload) throws IOException {
                for (int f = 0; f < BURST_FRAMES; f++) {
                    int sequence = f;
                    check(connection.trySend(BURST_BODY_BYTES, writer -> {
                        for (int i = 0; i < BURST_BODY_BYTES; i++) writer.writeByte(sample(sequence, i));
                    }), "burst queued within pool capacity");
                }
                check(!connection.trySend(BURST_BODY_BYTES, writer -> { throw new AssertionError("pool is full"); }),
                        "send-pool exhaustion refuses before encoding");
                if (graceful) connection.closeAfterFlush();
                queued.countDown();
            }

            @Override public void onClosed(Connection connection, CloseReason reason) { closed.add(reason); }
        };
    }

    private static void checkMailboxAndShutdown() throws Exception {
        var opened = new LinkedBlockingQueue<Connection>();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger executed = new AtomicInteger();
        AtomicInteger canceled = new AtomicInteger();
        try (SelectorTransport server = SelectorTransport.bind(LOOPBACK, config(1, 2, 2), new SelectorTransport.Handler() {
            @Override public void onOpen(Connection connection) { opened.add(connection); }
            @Override public void onFrame(Connection connection, ReadBuffer payload) { }
        }); Socket peer = connect(server, 0)) {
            Connection connection = await(opened);
            try {
                check(connection.tryExecute(ownerConnection -> {
                    started.countDown();
                    try { await(release); }
                    catch (InterruptedException e) { throw new IOException(e); }
                }), "owner command admitted");
                await(started);
                SelectorTransport.OwnerCommand pending = new SelectorTransport.OwnerCommand() {
                    @Override public void run(Connection ignored) { executed.incrementAndGet(); }
                    @Override public void cancel() { canceled.incrementAndGet(); }
                };
                check(connection.tryExecute(pending) && connection.tryExecute(pending), "mailbox holds exactly two");
                check(!connection.tryExecute(pending), "full mailbox refuses a third command");
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

    private static void checkCipher() throws Exception {
        long seed = Opcodes.getSeed();
        try (SelectorTransport server = SelectorTransport.bind(LOOPBACK, config(2, 2, 8), new SelectorTransport.Handler() {
            @Override public void onOpen(Connection connection) throws IOException {
                connection.enableCipher(seed, true);
                try {
                    connection.trySend(3, writer -> { });
                    throw new AssertionError("empty encoder result should fail");
                } catch (IllegalArgumentException expected) { }
                check(connection.trySend(Opcodes.S_KEYBYTES.length, writer -> writer.writeBytes(Opcodes.S_KEYBYTES)),
                        "plaintext RKern handshake");
            }
            @Override public void onFrame(Connection connection, ReadBuffer payload) throws IOException {
                int size = payload.remaining();
                echoEncrypted(connection, payload, size);
                try {
                    connection.trySend(3, writer -> { throw new IOException("cancel appended frame"); });
                    throw new AssertionError("encoder failure should propagate");
                } catch (IOException expected) {
                    check(connection.pendingSendBytes() == size + 2, "failed append preserves prior frame");
                }
                payload.position(0);
                echoEncrypted(connection, payload, size);
            }
        }); Socket socket = connect(server, 0)) {
            expectFrame(socket, Opcodes.S_KEYBYTES);
            ScalarClient peer = new ScalarClient(seed);
            for (int round = 0; round < 28; round++) {
                byte[] request = new byte[4 + (round % 14)];
                for (int i = 0; i < request.length; i++) request[i] = sample(round, i);
                socket.getOutputStream().write(frame(peer.toServer(request)));
                expectFrame(socket, peer.fromServer(request));
                expectFrame(socket, peer.fromServer(request));
            }
            socket.getOutputStream().write(frame(new byte[] {1, 2, 3}));
            check(socket.getInputStream().read() == -1, "too-short encrypted packet is rejected");
            server.close();
            balanced(server.shutdownStats());
            check(server.shutdownStats().getFirst().send().borrows() == 30,
                    "two ordered encrypted responses share each outbound lease");
        }
    }

    private static void echoEncrypted(Connection connection, ReadBuffer payload, int size) throws IOException {
        check(connection.trySend(size, writer -> {
            while (payload.remaining() != 0) writer.writeByte(payload.readByte());
        }), "encrypted echo");
    }

    /** Byte-at-a-time reference for the separate client-to-server and server-to-client streams. */
    private static final class ScalarClient {
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

    private static Socket connect(SelectorTransport server, int receiveBytes) throws IOException {
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

    private static void balanced(List<SelectorTransport.OwnerStats> stats) {
        for (SelectorTransport.OwnerStats owner : stats) {
            check(owner.remainingConnections() == 0 && owner.remainingCommands() == 0, "no live connections or commands");
            balanced(owner.receive());
            balanced(owner.send());
        }
    }

    private static void balanced(SelectorBufferPool.Stats pool) {
        check(pool.inUse() == 0 && pool.borrows() == pool.returns(), "lease ownership reconciles");
        check(pool.allocatedDirectBytes() <= pool.maxDirectBytes(), "direct allocation respects cap");
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
