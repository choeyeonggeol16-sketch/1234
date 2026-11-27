import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

import l1j.server.Config;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.core.config.CIOConfig;
import l1j.server.server.model.network.core.connection.CExecutionSerializerFast;
import l1j.server.server.model.network.core.connection.CIOBufferEx.CIOFrame;
import l1j.server.server.model.network.core.connection.CIOBufferPool;
import l1j.server.server.model.network.core.connection.CIOServer;
import l1j.server.server.model.network.core.connection.CIOSocket;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.session.CSessionLifecycle;
import l1j.server.server.model.network.core.transport.selector.CSelectorTransport;
import l1j.server.server.model.network.packet.UserPacketCallbackMap;
import l1j.server.server.model.network.safe.CPacketFilter;
import l1j.server.server.model.network.thread.CIOThreadCallback;

/** Real hosted TCP SEND FIFO. Compile with SelectorTransportTest for the independent cipher.
 * Run twice in fresh JVMs, with rkern.network.sharedSendWorkers=false and true. */
public final class SharedSendSerializerRegression {
    private static final int TIMEOUT = 6;
    private static final int BURST = 70;
    private static final int BYTES = 2112; // Distinct real SEND leases above the small-frame packing threshold.
    private static final boolean SHARE = Boolean.getBoolean("rkern.network.sharedSendWorkers");
    // Open the deterministic observation seam only in the correctness mode.
    private static final class Barrier {
        static final Class<?> SERIALIZER = CExecutionSerializerFast.class;
        static final VarHandle TOKEN = (VarHandle) get(field(SERIALIZER, "SOCKET_DRAINING"), null);
        static final Field OWNER = field(SERIALIZER, "_socketOwner"), HEAD = field(SERIALIZER, "_socketHead");
        static final Method DRAIN = drainMethod();
    }

    public static void main(String[] args) throws Exception {
        String mode = args.length == 0 ? "" : args[0];
        boolean skew = mode.equals("skew-load"), load = skew || mode.equals("backlog-load");
        if (args.length > 1 || args.length == 1 && !load)
            throw new IllegalArgumentException("Optional mode: backlog-load or skew-load");
        int capacity = skew ? 8 : 4;
        Config.MAX_ONLINE_USERS = (short) capacity;
        CPacketFilter.getInstance().load();
        var cio = CIOThreadCallback.getInstance();
        var address = new InetSocketAddress("127.0.0.1", 0);
        var config = new CIOConfig(address);
        config.packetExecutorShardCount = 2;
        config.usePreLoginTimeout = false;
        CIOConfig.NETWORK_CLIENT_PERMISSION = capacity;
        int shards = cio.bindNetworkIngress(config);
        check(shards == 2, "two existing Selector owners required");
        var pool = new CIOBufferPool(capacity, skew ? 512 : 128, capacity, capacity, shards);
        int alive = Opcodes.C_ALIVE & 255;
        var ready = new ArrayBlockingQueue<CUserSocket>(capacity);
        var old = UserPacketCallbackMap.gPacketTableFromClient[alive];
        UserPacketCallbackMap.gPacketTableFromClient[alive] = (request, socket) -> {
            return ready.offer(socket);
        };
        try (var server = new CIOServer(address, capacity, shards, capacity, pool, cio, config)) {
            var peers = new ArrayList<Peer>();
            try {
                for (int i = 0; i < (skew ? 8 : 1); i++) peers.add(connect(server, ready, alive));
                Peer first = peers.getFirst();
                if (load) {
                    int home = first.socket.affinityShard();
                    var hot = peers.stream().filter(peer -> peer.socket.affinityShard() == home).toList();
                    check(hot.size() == (skew ? 4 : 1), "hot sockets share one Selector owner");
                    backlogLoad(server, hot);
                    for (Peer peer : peers) peer.socket.requestClientCloseAfter(0);
                } else {
                    verifyOwnerHandoff(server, first.socket, cio, pool, first.in, first.codec);
                }
                for (Peer peer : peers) check(peer.in.read() == -1, "graceful EOF after the exact last frame");
                await(() -> pool.usedRecvSlots() == 0 && pool.usedSendSlots() == 0 && pool.usedSharedSourceSlots() == 0,
                        "all receive, SEND and shared-source leases returned");
            } finally {
                for (Peer peer : peers) peer.wire.close();
            }
        } finally {
            UserPacketCallbackMap.gPacketTableFromClient[alive] = old;
            cio.shutdown();
            check(cio.awaitTermination(TIMEOUT, TimeUnit.SECONDS), "CIO stopped");
            CSessionLifecycle.stopAccepting();
            check(CSessionLifecycle.awaitDrained(TIMEOUT, TimeUnit.SECONDS), "session teardown drained");
        }
        System.out.println("PASS sharedSendWorkers=" + SHARE + " "
                + (load ? mode : "actual owner identity") + ", ordered ciphertext, graceful EOF and leases");
    }

    private record Peer(Socket wire, DataInputStream in, SelectorTransportTest.ScalarClient codec, CUserSocket socket) {}

    private static Peer connect(CIOServer server, ArrayBlockingQueue<CUserSocket> ready, int alive) throws Exception {
        var wire = new Socket();
        try {
            wire.setSoTimeout(TIMEOUT * 1000);
            wire.connect(server.localAddress(), TIMEOUT * 1000);
            var in = new DataInputStream(new BufferedInputStream(wire.getInputStream(), 64 * 1024));
            var codec = new SelectorTransportTest.ScalarClient(Opcodes.getSeed());
            check(Arrays.equals(read(in), Opcodes.S_KEYBYTES), "handshake key");
            byte[] encoded = codec.toServer(new byte[]{(byte) alive, 0, 0, 0, 0});
            wire.getOutputStream().write(ByteBuffer.allocate(encoded.length + 2).order(ByteOrder.LITTLE_ENDIAN)
                    .putShort((short) (encoded.length + 2)).put(encoded).array());
            var socket = ready.poll(TIMEOUT, TimeUnit.SECONDS);
            check(socket != null, "production socket registered");
            return new Peer(wire, in, codec, socket);
        } catch (Exception | Error failure) {
            wire.close();
            throw failure;
        }
    }

    private static void verifyOwnerHandoff(CIOServer server, CUserSocket socket, CIOThreadCallback cio,
            CIOBufferPool pool, DataInputStream in, SelectorTransportTest.ScalarClient codec) throws Exception {
        Thread home = threadOn(cio, socket.affinityShard());
        Thread foreign = threadOn(cio, 1 - socket.affinityShard());
        var slots = (Object[]) get(field(CSelectorTransport.class, "owners"),
                get(field(CIOServer.class, "transport"), server));
        Object peer = slots[1 - socket.affinityShard()];
        var peerSlot = (AtomicReference<?>) get(field(peer.getClass(), "sharedWork"), peer);

        var connection = (CSelectorTransport.Connection) get(field(CIOSocket.class, "peer"), socket);
        int sequence = 0;
        var small = exercise(connection, socket, peerSlot, home, foreign, 1, sequence, false);
        check(!small, "ordinary SEND used the home owner");
        expect(in, codec, sequence++);
        await(() -> pool.usedSendSlots() == 0, "ordinary write completed and returned its lease");

        boolean transferred = false;
        for (int attempt = 0; attempt < (SHARE ? 4 : 1); attempt++) {
            transferred = exercise(connection, socket, peerSlot, home, foreign, BURST, sequence, SHARE);
            boolean finalBurst = !SHARE || transferred;
            if (finalBurst) socket.requestClientCloseAfter(0);
            for (int i = 0; i < BURST; i++) expect(in, codec, sequence++);
            if (finalBurst) break;
            await(() -> pool.usedSendSlots() == 0, "retry started after busy-peer local fallback");
        }
        check(transferred == SHARE, "eligible FIFO was handled by the expected existing worker");
    }

    private static void backlogLoad(CIOServer server, List<Peer> hot) throws Exception {
        int sockets = hot.size(), frames = 64;
        int warmup = sockets == 1 ? 96 : 64, measured = sockets == 1 ? 256 : 160;
        int perSocket = (warmup + measured) * frames;
        byte[][] expected = new byte[perSocket * sockets][];
        for (int k = 0; k < sockets; k++) {
            for (int i = 0; i < perSocket; i++) {
                int sequence = k * perSocket + i;
                expected[sequence] = hot.get(k).codec.fromServer(body(sequence));
            }
        }

        CIOFrame[][] staged = new CIOFrame[sockets][frames];
        byte[] plain = new byte[BYTES], received = new byte[BYTES];
        var encoder = ByteBuffer.wrap(plain).order(ByteOrder.LITTLE_ENDIAN);
        long[] latency = new long[measured];
        long runsBefore = 0, start = 0;
        for (int window = 0; window < warmup + measured; window++) {
            if (window == warmup) {
                runsBefore = sharedRuns(server);
                start = System.nanoTime();
            }
            long begin = System.nanoTime();
            try {
                for (int k = 0; k < sockets; k++) {
                    int first = k * perSocket + window * frames;
                    CUserSocket socket = hot.get(k).socket;
                    for (int i = 0; i < frames; i++) {
                        var frame = socket.beginDirectFrame(BYTES + 2);
                        if (frame == null) throw new AssertionError("SEND refused at frame " + (first + i));
                        staged[k][i] = frame;
                        fillBody(encoder, first + i);
                        frame.buffer().writeBytes(plain);
                    }
                }
                // Every session's later reservations are ready before any session's head is released.
                for (int i = frames - 1; i >= 0; i--) {
                    for (int k = 0; k < sockets; k++) {
                        if (!hot.get(k).socket.commitDirectFrame(staged[k][i]))
                            throw new AssertionError("SEND commit refused");
                        staged[k][i] = null;
                    }
                }
            } finally {
                for (int k = 0; k < sockets; k++)
                    for (var frame : staged[k]) if (frame != null) hot.get(k).socket.abortDirectFrame(frame);
            }
            for (int k = 0; k < sockets; k++) {
                var in = hot.get(k).in;
                int first = k * perSocket + window * frames;
                for (int i = 0; i < frames; i++) {
                    int size = in.readUnsignedByte() | in.readUnsignedByte() << 8;
                    if (size != BYTES + 2) throw new AssertionError("wrong frame size " + size);
                    in.readFully(received);
                    if (!Arrays.equals(received, expected[first + i]))
                        throw new AssertionError("wire/cipher mismatch for frame " + (first + i));
                }
            }
            if (window >= warmup) latency[window - warmup] = System.nanoTime() - begin;
        }
        long elapsed = System.nanoTime() - start;
        long runs = sharedRuns(server) - runsBefore;
        check(SHARE ? runs > 0 : runs == 0, "backlog workload exercised selected policy, shared runs=" + runs);
        Arrays.sort(latency);
        int percentile = (int) Math.ceil(0.99 * measured) - 1;
        System.out.printf(Locale.ROOT, "%s shared=%s hotSockets=%d ownerIndex=%d framesPerWindow=%d bodyBytes=%d"
                        + " warmupWindows=%d measuredWindows=%d sharedWorkRuns=%d framesPerSec=%.2f p99WindowUs=%.3f%n",
                sockets == 1 ? "BACKLOG" : "SKEW", SHARE, sockets, hot.getFirst().socket.affinityShard(),
                frames * sockets, BYTES, warmup, measured, runs, (double) frames * sockets * measured * 1e9 / elapsed,
                latency[percentile] / 1e3);
    }

    private static long sharedRuns(CIOServer server) {
        return server.ownerDiagnostics().stream().mapToLong(CSelectorTransport.OwnerDiagnostics::sharedWorkRuns).sum();
    }

    private static boolean exercise(CSelectorTransport.Connection connection, CUserSocket socket, AtomicReference<?> peerSlot,
            Thread home, Thread foreign, int count, int sequence, boolean eligible) throws Exception {
        await(() -> (int) Barrier.TOKEN.getVolatile(socket) == 0, "idle serializer");
        check(Barrier.TOKEN.compareAndSet(socket, 0, 1), "test reserved the idle drain barrier");
        long start = (long) get(Barrier.HEAD, socket);
        boolean stagedAll = false;
        try {
            // Real reservation, writer and commit path. Only the last drain notification is postponed.
            CIOFrame[] staged = new CIOFrame[count];
            try {
                for (int i = 0; i < count; i++) {
                    staged[i] = socket.beginDirectFrame(BYTES + 2);
                    check(staged[i] != null, "accepted SEND " + i);
                    staged[i].buffer().writeBytes(body(sequence + i));
                }
                for (int i = count - 1; i >= 0; i--) {
                    check(socket.commitDirectFrame(staged[i]), "sealed SEND " + i);
                    staged[i] = null;
                }
            } finally {
                for (var frame : staged) if (frame != null) socket.abortDirectFrame(frame);
            }
            stagedAll = true;
        } finally {
            if (!stagedAll) Barrier.TOKEN.setVolatile(socket, 0);
        }

        var stateLock = (ReentrantLock) get(field(CIOSocket.class, "_stateLock"), socket);
        var letHomeRun = new CountDownLatch(1);
        var finished = new CompletableFuture<Void>();
        boolean transferred;
        try {
            stateLock.lock();
            try {
                // Timer/shard completions deliberately execute inside a pinned SEND burst.
                // A Selector owner command exercises the ordinary shareable notification.
                boolean scheduled = connection.submit(ignored -> {
                    Barrier.TOKEN.setVolatile(socket, 0);
                    try {
                        if (eligible) await(() -> peerSlot.get() instanceof Enum<?> e && e.name().equals("IDLE"), "foreign Selector advertises idle");
                        Barrier.DRAIN.invoke(socket);
                        waitFor(letHomeRun);
                        finished.complete(null);
                    } catch (Throwable error) { finished.completeExceptionally(error); }
                }, true);
                if (!scheduled) Barrier.TOKEN.setVolatile(socket, 0);
                check(scheduled, "home observation scheduled");
                // Lock ownership makes the first actual SEND callback observable, with no scheduler sampling.
                await(() -> get(Barrier.OWNER, socket) == home && stateLock.hasQueuedThread(home)
                                || get(Barrier.OWNER, socket) == foreign && stateLock.hasQueuedThread(foreign),
                        "existing CIO worker reached SEND critical section");
                Thread executing = (Thread) get(Barrier.OWNER, socket);
                check(executing == home || executing == foreign, "FIFO claimant is an existing CIO worker");
                transferred = executing == foreign;
                check(eligible || !transferred, "light/default path did not transfer");
            } finally { stateLock.unlock(); }
            if (transferred) {
                // Home remains in the test command. The peer must give the live token
                // back after one bounded slice rather than consuming the whole FIFO.
                await(() -> (long) get(Barrier.HEAD, socket) == start + 32 && get(Barrier.OWNER, socket) == null
                        && (int) Barrier.TOKEN.getVolatile(socket) == 1, "shared worker yielded its 32-ticket slice");
            }
        } finally { letHomeRun.countDown(); }
        finished.get(TIMEOUT, TimeUnit.SECONDS);
        return transferred;
    }

    private static Thread threadOn(CIOThreadCallback cio, int shard) throws Exception {
        var thread = new CompletableFuture<Thread>();
        check(cio.completeOnShard(shard, () -> thread.complete(Thread.currentThread()), failure -> {
            if (failure != null) thread.completeExceptionally(failure);
        }), "existing CIO worker identified");
        return thread.get(TIMEOUT, TimeUnit.SECONDS);
    }

    private static byte[] body(int sequence) {
        byte[] bytes = new byte[BYTES];
        fillBody(ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN), sequence);
        return bytes;
    }

    private static void fillBody(ByteBuffer out, int sequence) {
        Arrays.fill(out.array(), (byte) (sequence ^ 0x95));
        out.put(0, (byte) 0x45);
        out.putInt(1, sequence);
    }

    private static byte[] read(DataInputStream in) throws Exception {
        int length = (in.readUnsignedByte() | in.readUnsignedByte() << 8) - 2;
        check(length > 0 && length <= 65533, "valid on-wire length");
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return bytes;
    }

    private static void expect(DataInputStream in, SelectorTransportTest.ScalarClient codec, int sequence) throws Exception {
        check(Arrays.equals(read(in), codec.fromServer(body(sequence))), "exact FIFO/cipher sequence " + sequence);
    }

    private static void waitFor(CountDownLatch latch) {
        try { check(latch.await(TIMEOUT, TimeUnit.SECONDS), "home released"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
    }

    private static void await(BooleanSupplier condition, String text) {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT);
        do {
            if (condition.getAsBoolean()) return;
            LockSupport.parkNanos(100_000L);
        } while (System.nanoTime() < end);
        throw new AssertionError(text);
    }

    private static Method drainMethod() {
        try {
            var method = Barrier.SERIALIZER.getDeclaredMethod("drainSocketCallbacks");
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private static Field field(Class<?> type, String name) {
        try {
            var f = type.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private static Object get(Field field, Object target) {
        try { return field.get(target); }
        catch (IllegalAccessException e) { throw new AssertionError(e); }
    }

    private static void check(boolean ok, String text) { if (!ok) throw new AssertionError(text); }
}
