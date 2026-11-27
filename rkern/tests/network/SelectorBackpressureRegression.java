import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.time.Duration;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import l1j.server.server.model.network.core.config.CIOConfig;
import l1j.server.server.model.network.core.connection.CIOBufferEx;
import l1j.server.server.model.network.core.connection.CIOBufferPool;
import l1j.server.server.model.network.core.transport.selector.CSelectorTransport;
import l1j.server.server.model.network.core.transport.selector.CSelectorTransport.CloseReason;
import l1j.server.server.model.network.core.transport.selector.CSelectorTransport.Connection;
import l1j.server.server.model.network.thread.CIOThreadCallback;

/** Real loopback raw operations, driven by the production CIO owner scheduler. */
public final class SelectorBackpressureRegression {
    private static final int SEND_BUFFER = 4096, RECEIVE_BUFFER = 8192;
    private static final int CHUNK_BYTES = 32768, CHUNKS = 16;
    private static final byte[] PAYLOAD = new byte[CHUNK_BYTES * CHUNKS];
    private static final Field KEY = field("key"), CHANNEL = field("channel");

    static {
        for (int i = 0; i < PAYLOAD.length; i++) PAYLOAD[i] = (byte) (i * 37 + (i >>> 9));
    }

    public static void main(String[] args) throws Exception {
        var address = new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0);
        var config = new CIOConfig(address);
        config.packetExecutorShardCount = 1;
        var scheduler = CIOThreadCallback.getInstance();
        check(scheduler.bindNetworkIngress(config) == 1, "fixture binds one production CIO owner");
        var pool = new CIOBufferPool(2, 1, 1, CHUNKS);
        var fixture = new Fixture(pool);
        var transportConfig = new CSelectorTransport.Config(1, 2, 8, 16, 1, 1, CHUNKS,
                PAYLOAD.length, CHUNKS, SEND_BUFFER, Duration.ofSeconds(2));
        try {
            try (var server = CSelectorTransport.bindHosted(address, transportConfig, fixture, scheduler::attachSelectors)) {
                recover(server, fixture);
                drain(server, fixture);
                cancel(server, fixture, true);
                cancel(server, fixture, false);
                server.close();
                var stats = server.shutdownStats().getFirst();
                check(stats.remainingConnections() == 0 && stats.remainingCommands() == 0
                        && server.shutdownFailure() == null, "transport shutdown retired all owners' work");
            }
        } finally {
            scheduler.shutdown();
            check(scheduler.awaitTermination(5, TimeUnit.SECONDS), "CIO shutdown drained");
        }
        check(ManagementFactory.getThreadMXBean().findDeadlockedThreads() == null, "no JVM deadlock");
        System.out.println("PASS hosted loopback partial SEND, OP_WRITE enable/clear, READ pause/resume, drain, timeout and RST");
    }

    private static void recover(CSelectorTransport server, Fixture fixture) throws Exception {
        try (var client = connect(server)) {
            var peer = take(fixture.opened);
            onOwner(peer, () -> {
                peer.connection.pauseReads(true);
                peer.read();
                peer.write();
                return null;
            });
            waitForPartialWrite(server, peer);
            check((onOwner(peer, peer::interest) & SelectionKey.OP_READ) == 0, "paused READ has no interest");
            client.getOutputStream().write(92);
            try (var healthy = connect(server)) {
                var other = take(fixture.opened);
                onOwner(other, () -> { other.read(); return null; });
                healthy.getOutputStream().write(34);
                check(take(other.reads).equals(new Read(1, 34)), "same owner received an unrelated peer");
                other.connection.requestClose(true);
                check(take(other.closed) == CloseReason.APPLICATION, "unrelated peer drains");
            }
            check(peer.reads.isEmpty(), "read remains paused while the same CIO owner services another peer");
            expectPayload(client);
            check(take(peer.writes) == PAYLOAD.length, "partial completions account for every byte");
            check((onOwner(peer, peer::interest) & SelectionKey.OP_WRITE) == 0, "resumed writes clear OP_WRITE");
            check(server.ownerDiagnostics().getFirst().stalledWrites() == 0, "resumed writes remove stall deadline");
            check((onOwner(peer, () -> {
                peer.connection.pauseReads(false);
                return peer.interest();
            }) & SelectionKey.OP_READ) != 0, "resume re-arms READ interest");
            check(take(peer.reads).equals(new Read(1, 92)) && peer.reads.isEmpty(),
                    "paused inbound byte delivered exactly once after resume");
            peer.connection.requestClose(true);
            check(client.getInputStream().read() == -1, "idle completion then FIN");
            check(take(peer.closed) == CloseReason.APPLICATION, "expected normal close");
            fixture.checkRetired(server);
        }
    }

    private static void drain(CSelectorTransport server, Fixture fixture) throws Exception {
        try (var client = connect(server)) {
            var peer = take(fixture.opened);
            onOwner(peer, () -> { peer.write(); return null; });
            waitForPartialWrite(server, peer);
            check(!onOwner(peer, () -> {
                peer.connection.closeAfterFlush();
                return peer.connection.writeRaw(ByteBuffer.wrap(new byte[] {1}), n -> {});
            }),
                    "drain rejects a new raw write");
            expectPayload(client);
            check(take(peer.writes) == PAYLOAD.length && peer.writes.isEmpty(), "graceful drain completes remaining gather once");
            check(client.getInputStream().read() == -1, "drain sends FIN after the residual bytes");
            check(take(peer.closed) == CloseReason.APPLICATION, "expected graceful close");
            fixture.checkRetired(server);
        }
    }

    private static void cancel(CSelectorTransport server, Fixture fixture, boolean timeout) throws Exception {
        try (var client = connect(server)) {
            var peer = take(fixture.opened);
            onOwner(peer, () -> { peer.read(); peer.write(); return null; });
            waitForPartialWrite(server, peer);
            if (!timeout) {
                client.setSoLinger(true, 0);
                client.close();
            }
            CloseReason reason = take(peer.closed);
            if (timeout) check(reason == CloseReason.WRITE_TIMEOUT, "stalled receiver timed out");
            else check(reason == CloseReason.IO_ERROR || reason == CloseReason.REMOTE_EOF, "reset ended the stalled write");
            check(take(peer.writes) == -1 && peer.writes.isEmpty(), "pending write cancelled exactly once");
            check(take(peer.reads).equals(new Read(-1, -1)) && peer.reads.isEmpty(), "pending read cancelled exactly once");
            fixture.checkRetired(server);
        }
    }

    private static void waitForPartialWrite(CSelectorTransport server, Peer peer) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        do {
            if (server.ownerDiagnostics().getFirst().stalledWrites() != 0 && onOwner(peer, () ->
                    peer.progress > 0 && peer.progress < PAYLOAD.length
                    && (peer.interest() & SelectionKey.OP_WRITE) != 0)) {
                return;
            }
            Thread.sleep(1);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("partial SEND did not enable OP_WRITE with an indexed stall deadline");
    }

    private static void expectPayload(Socket client) throws Exception {
        check(Arrays.equals(PAYLOAD, client.getInputStream().readNBytes(PAYLOAD.length)),
                "loopback received exact gather bytes in order");
    }

    private static Socket connect(CSelectorTransport server) throws Exception {
        var socket = new Socket();
        socket.setReceiveBufferSize(RECEIVE_BUFFER);
        socket.setSoTimeout(5000);
        try {
            socket.connect(server.localAddress(), 5000);
            return socket;
        } catch (Exception failure) {
            socket.close();
            throw failure;
        }
    }

    private static <T> T onOwner(Peer peer, Callable<T> action) throws Exception {
        var task = new FutureTask<>(() -> {
            peer.checkOwner();
            return action.call();
        });
        check(peer.connection.tryExecute(ignored -> task.run()), "owner accepted inspection or setup");
        return task.get(5, TimeUnit.SECONDS);
    }

    private static <T> T take(BlockingQueue<T> queue) throws InterruptedException {
        T value = queue.poll(5, TimeUnit.SECONDS);
        if (value == null) throw new AssertionError("missing expected callback");
        return value;
    }

    private static Field field(String name) {
        try {
            Field value = Connection.class.getDeclaredField(name);
            value.setAccessible(true);
            return value;
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private record Read(int bytes, int value) { }

    private static final class Fixture implements CSelectorTransport.RawHandler {
        private final CIOBufferPool pool;
        private final BlockingQueue<Peer> opened = new LinkedBlockingQueue<>();
        private final Map<Connection, Peer> peers = new IdentityHashMap<>();

        Fixture(CIOBufferPool pool) { this.pool = pool; }

        @Override public void onOpen(Connection connection) {
            var peer = new Peer(connection, pool);
            peer.checkOwner();
            try {
                int actual = ((SocketChannel) CHANNEL.get(connection)).getOption(StandardSocketOptions.SO_SNDBUF);
                check(actual > 0 && actual <= SEND_BUFFER * 4, "kernel accepted a small server send buffer");
            } catch (Exception failure) {
                throw new AssertionError(failure);
            }
            peers.put(connection, peer);
            opened.add(peer);
        }

        @Override public void onClosed(Connection connection, CloseReason reason) {
            var peer = peers.remove(connection);
            peer.checkOwner();
            peer.closed.add(reason);
        }

        private void checkRetired(CSelectorTransport server) {
            var owner = server.ownerDiagnostics().getFirst();
            check(owner.admitted() == 0 && owner.queuedCommands() == 0 && owner.stalledWrites() == 0
                    && owner.deadlines() == 0 && pool.usedSendSlots() == 0 && pool.usedRecvSlots() == 0,
                    "closed peer left no admission, commands, deadlines or pooled leases");
        }
    }

    private static final class Peer {
        private final Connection connection;
        private final CIOBufferPool pool;
        private final Thread ownerThread = Thread.currentThread();
        private final BlockingQueue<CloseReason> closed = new LinkedBlockingQueue<>();
        private final BlockingQueue<Read> reads = new LinkedBlockingQueue<>();
        private final BlockingQueue<Long> writes = new LinkedBlockingQueue<>();
        private final CIOBufferEx[] leases = new CIOBufferEx[CHUNKS];
        private final ByteBuffer[] buffers = new ByteBuffer[CHUNKS];
        private long progress;
        private int offset;

        Peer(Connection connection, CIOBufferPool pool) {
            this.connection = connection;
            this.pool = pool;
        }

        private void checkOwner() {
            check(connection.isOwnerThread() && Thread.currentThread() == ownerThread, "callback changed CIO owner");
        }

        private int interest() throws IllegalAccessException {
            return ((SelectionKey) KEY.get(connection)).interestOps();
        }

        private void read() {
            CIOBufferEx lease = pool.acquireRecv();
            check(lease != null, "pooled receive slot available");
            var target = lease.buffer();
            target.clear().limit(1);
            check(connection.readRaw(target, bytes -> {
                checkOwner();
                int value = bytes > 0 ? target.get(0) & 0xff : -1;
                pool.release(lease);
                reads.add(new Read(bytes, value));
            }), "raw receive armed");
        }

        private void write() {
            for (int i = 0; i < CHUNKS; i++) {
                var lease = pool.acquireSend(CHUNK_BYTES);
                check(lease != null, "pooled gather slot available");
                leases[i] = lease;
                buffers[i] = lease.buffer().clear().put(PAYLOAD, i * CHUNK_BYTES, CHUNK_BYTES).flip();
            }
            armWrite();
        }

        private void armWrite() {
            check(connection.writeRaw(buffers, offset, buffers.length - offset, bytes -> {
                checkOwner();
                if (bytes >= 0) {
                    progress += bytes;
                    while (offset < CHUNKS && !buffers[offset].hasRemaining()) offset++;
                    if (offset < CHUNKS) {
                        armWrite();
                        return;
                    }
                }
                for (CIOBufferEx lease : leases) pool.release(lease);
                Arrays.fill(leases, null);
                Arrays.fill(buffers, null);
                writes.add(bytes < 0 ? -1L : progress);
            }), "raw gather armed");
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
