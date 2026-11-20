import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import l1j.server.server.model.network.buffer.CPacketReadBuffer;
import l1j.server.server.model.network.core.transport.selector.CSelectorBufferPool;
import l1j.server.server.model.network.core.transport.selector.CSelectorFrameReader;

public final class SelectorFrameReaderTest {
    @FunctionalInterface
    private interface CheckedAction { void run() throws IOException; }

    public static void main(String[] args) throws IOException {
        CSelectorBufferPool pool = CSelectorBufferPool.receive(2, 1);
        try (ServerSocketChannel server = ServerSocketChannel.open()) {
            server.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            checkFraming(server, pool);
            checkCloseDuringDispatch(server, pool);
            checkReadFailures(server, pool);
            checkExhaustion(server, pool);
            long warmBytes = pool.stats().allocatedDirectBytes();
            for (int i = 0; i < 256; i++) {
                int seed = i;
                try (Connection conn = new Connection(server, pool)) {
                    conn.send(wireFrame(4095, seed));
                    conn.receive(4097);
                    check(conn.reader.drain(1, frame -> checkPayload(frame, 4095, seed)) == 1, "churn frame");
                }
                check(pool.stats().inUse() == 0, "churn returned every receive lease");
                check(pool.stats().allocatedDirectBytes() == warmBytes, "direct memory grew after warmup");
            }
        }
        CSelectorBufferPool.Stats stats = pool.stats();
        check(stats.inUse() == 0, "outstanding or duplicate lease");
        check(pool.stats().allocatedDirectBytes() <= pool.stats().maxDirectBytes(), "pool direct cap exceeded");
        System.out.printf("PASS Selector receive tests, 256 reconnects, direct allocated=%d, pool cap=%d%n",
                pool.stats().allocatedDirectBytes(), pool.stats().maxDirectBytes());
    }

    private static void checkFraming(ServerSocketChannel server, CSelectorBufferPool pool) throws IOException {
        try (Connection conn = new Connection(server, pool)) {
            ByteBuffer first = wireFrame(3, 7);
            conn.send(first.slice(0, 1));
            conn.receive(1);
            check(!conn.reader.hasBufferedFrame(), "partial header");
            conn.send(first.slice(1, 2));
            conn.receive(2);
            check(conn.reader.drain(8, frame -> { throw new AssertionError("partial body"); }) == 0,
                    "partial body must be buffered");
            ByteBuffer remainder = ByteBuffer.allocate(10);
            remainder.put(first.slice(3, 2)).put(wireFrame(2, 17)).put(wireFrame(2, 27)).flip();
            conn.send(remainder);
            conn.receive(10);
            CPacketReadBuffer[] borrowed = new CPacketReadBuffer[1];
            check(conn.reader.drain(8, frame -> {
                borrowed[0] = frame;
                return !checkPayload(frame, 3, 7);
            }) == 1, "handler pause must consume only one frame");
            check(!borrowed[0].isValid() && conn.reader.hasBufferedFrame(), "borrow revoked, tail retained");
            check(conn.reader.read(conn.input) == 0, "buffered work takes priority over another read");
            check(conn.reader.drain(1, frame -> {
                check(frame == borrowed[0], "reader view reused");
                return checkPayload(frame, 2, 17);
            }) == 1 && conn.reader.hasBufferedFrame(), "frame budget consumed exactly one frame");
            check(conn.reader.drain(1, frame -> checkPayload(frame, 2, 27)) == 1, "tail frame");
            check(!conn.reader.hasBufferedFrame(), "all coalesced frames consumed");
            conn.send(wireFrame(0, 0));
            conn.receive(2);
            check(conn.reader.drain(1, frame -> checkPayload(frame, 0, 0)) == 1, "empty-frame policy delegated");
            conn.send(wireFrame(4094, 39));
            conn.receive(4096);
            check(conn.reader.drain(1, frame -> checkPayload(frame, 4094, 39)) == 1, "exact compact capacity");
            conn.send(wireFrame(65_533, 49));
            conn.receive(65_535);
            check(pool.stats().inUse() == 1, "promotion must release the compact lease");
            check(conn.reader.drain(1, frame -> checkPayload(frame, 65_533, 49)) == 1, "max wire frame");
            check(pool.stats().inUse() == 0, "empty large lease released while connection remains open");
        }
        check(pool.stats().inUse() == 0, "framing test drained");
    }

    private static void checkCloseDuringDispatch(ServerSocketChannel server, CSelectorBufferPool pool) throws IOException {
        try (Connection conn = new Connection(server, pool)) {
            conn.send(wireFrame(1, 7));
            conn.receive(3);
            conn.reader.drain(1, frame -> {
                conn.reader.close();
                check(pool.stats().inUse() == 1, "close must keep callback's active lease");
                return checkPayload(frame, 1, 7);
            });
            check(pool.stats().inUse() == 0, "close returns borrow after handler exits");
        }
        try (Connection conn = new Connection(server, pool)) {
            conn.send(wireFrame(1, 7));
            conn.receive(3);
            expect(IOException.class, () -> conn.reader.drain(1, frame -> { throw new IOException("test"); }));
            check(pool.stats().inUse() == 0, "handler failure returns borrow");
        }
    }

    private static void checkReadFailures(ServerSocketChannel server, CSelectorBufferPool pool) throws IOException {
        try (Connection conn = new Connection(server, pool)) {
            conn.send(ByteBuffer.wrap(new byte[] {1, 0}));
            conn.receive(2);
            check(conn.reader.hasBufferedFrame(), "malformed header schedules rejection");
            expect(IOException.class, () -> conn.reader.drain(1, frame -> true));
            check(pool.stats().inUse() == 0, "invalid frame returns borrow");
        }
        try (Connection conn = new Connection(server, pool)) {
            conn.send(ByteBuffer.wrap(new byte[] {4, 0, 1}));
            conn.receive(3);
            conn.output.shutdownOutput();
            conn.awaitRead();
            expect(EOFException.class, () -> conn.reader.read(conn.input));
            check(pool.stats().inUse() == 0, "truncated EOF returns borrow");
        }
        try (Connection conn = new Connection(server, pool)) {
            conn.output.shutdownOutput();
            conn.awaitRead();
            check(conn.reader.read(conn.input) == -1 && pool.stats().inUse() == 0, "clean EOF returns borrow");
        }
    }

    private static void checkExhaustion(ServerSocketChannel server, CSelectorBufferPool pool) throws IOException {
        try (Connection holder = new Connection(server, pool); Connection conn = new Connection(server, pool)) {
            holder.send(wireFrame(4_998, 1));
            holder.receive(5_000);
            check(pool.stats().inUse() == 1, "reserve only large slot");
            conn.send(wireFrame(4095, 1));
            conn.receive(4096);
            expect(IOException.class, () -> conn.reader.read(conn.input));
            check(pool.stats().inUse() == 1, "exhaustion only retains held large lease");
        }
    }

    private static boolean checkPayload(CPacketReadBuffer frame, int length, int seed) {
        check(frame.remaining() == length, "payload length");
        for (int i = 0; i < length; i++) {
            check(frame.readByte() == payloadByte(seed, i), "payload byte");
        }
        return true;
    }

    private static ByteBuffer wireFrame(int bodyLength, int seed) {
        ByteBuffer frame = ByteBuffer.allocate(bodyLength + 2).order(ByteOrder.LITTLE_ENDIAN);
        frame.putShort((short) (bodyLength + 2));
        for (int i = 0; i < bodyLength; i++) frame.put(payloadByte(seed, i));
        return frame.flip();
    }

    private static byte payloadByte(int seed, int index) {
        return (byte) (seed + index * 13);
    }

    private static void expect(Class<? extends IOException> type, CheckedAction action) throws IOException {
        try {
            action.run();
            throw new AssertionError("expected " + type.getSimpleName());
        } catch (IOException error) {
            if (!type.isInstance(error)) throw error;
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class Connection implements AutoCloseable {
        final SocketChannel output;
        final SocketChannel input;
        final Selector selector;
        final CSelectorFrameReader reader;

        Connection(ServerSocketChannel server, CSelectorBufferPool pool) throws IOException {
            output = SocketChannel.open(server.getLocalAddress());
            input = server.accept();
            input.configureBlocking(false);
            selector = Selector.open();
            input.register(selector, SelectionKey.OP_READ);
            reader = new CSelectorFrameReader(pool);
        }

        void send(ByteBuffer bytes) throws IOException {
            while (bytes.hasRemaining()) output.write(bytes);
        }

        void receive(int expected) throws IOException {
            int received = 0;
            while (received < expected) {
                awaitRead();
                int bytes = reader.read(input);
                check(bytes >= 0, "unexpected EOF");
                check(bytes > 0, "no progress on readable loopback");
                received += bytes;
            }
            check(received == expected, "unexpected byte count");
        }

        void awaitRead() throws IOException {
            check(selector.select(1000) > 0, "Selector read readiness timeout");
            selector.selectedKeys().clear();
        }

        @Override public void close() throws IOException {
            try (output; input; selector; reader) { }
        }
    }
}
