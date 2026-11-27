import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.locks.LockSupport;

import l1j.server.Config;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.core.config.CIOConfig;
import l1j.server.server.model.network.core.connection.CIOBufferPool;
import l1j.server.server.model.network.core.connection.CIOServer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.session.CSessionLifecycle;
import l1j.server.server.model.network.packet.CBytesPacket;
import l1j.server.server.model.network.packet.UserPacketCallbackMap;
import l1j.server.server.model.network.safe.CPacketFilter;
import l1j.server.server.model.network.thread.CIOThreadCallback;

/**
 * Compile with the production JVM classpath and SelectorTransportTest.
 * Run in a fresh working directory with {@code -XX:ActiveProcessorCount=2
 * -Drkern.network.sharedPacketWorkers=true}. Use {@code blocked-neighbor}
 * with the shared workers flag enabled or disabled to compare automatic layouts.
 */
public final class SharedPacketWorkerRegression {
    private static final int SHARDS = 4;
    private static final int TIMEOUT = 8;
    private static final int BLOCK_MILLIS = 200;
    private static final int ALIVE = Opcodes.C_ALIVE & 255;
    private static final int REPLY = 0x45;

    private final AtomicReferenceArray<CUserSocket> sessions = new AtomicReferenceArray<>(SHARDS);
    private final AtomicReferenceArray<Thread> secondPacket = new AtomicReferenceArray<>(SHARDS);
    private final AtomicIntegerArray sequence = new AtomicIntegerArray(SHARDS);
    private final AtomicIntegerArray active = new AtomicIntegerArray(SHARDS);
    private final CountDownLatch held = new CountDownLatch(1);
    private final CountDownLatch resume = new CountDownLatch(1);
    private final Map<Thread, Set<Integer>> observed = new ConcurrentHashMap<>();
    private final AtomicInteger completions = new AtomicInteger();
    private final AtomicInteger timers = new AtomicInteger();
    private final CompletableFuture<Long> neighborHandledAt = new CompletableFuture<>();
    private long heldSinceNanos;

    public static void main(String[] args) throws Exception {
        require(Runtime.getRuntime().availableProcessors() == 2, "run the fixture with exactly two CPUs exposed");
        boolean blockedNeighbor = args.length == 1 && args[0].equals("blocked-neighbor");
        require(args.length == 0 || blockedNeighbor, "expected no arguments or blocked-neighbor");
        new SharedPacketWorkerRegression().run(blockedNeighbor);
        if (!blockedNeighbor)
            System.out.println("PASS four encrypted CIO shards on two shared workers; FIFO, timers, completions and shutdown");
    }

    private void run(boolean blockedNeighbor) throws Exception {
        final var address = new InetSocketAddress("127.0.0.1", 0);
        Config.TIME_ZONE = "UTC";
        Config.MAX_ONLINE_USERS = 16;
        CPacketFilter.getInstance().load();
        final var config = new CIOConfig(address);
        config.packetExecutorShardCount = blockedNeighbor ? 0 : SHARDS;
        config.packetExecutorMaxShards = SHARDS;
        config.usePreLoginTimeout = false;
        CIOConfig.NETWORK_CLIENT_PERMISSION = 16;
        CIOConfig.PACKET_LIMIT_PER_SEC = 10_000;
        CIOConfig.MAX_PACKETS_PER_COMPLETION = 8;
        final var scheduler = CIOThreadCallback.getInstance();
        var original = UserPacketCallbackMap.gPacketTableFromClient[ALIVE];
        try {
            final int shards = scheduler.bindNetworkIngress(config);
            final boolean shared = Boolean.getBoolean("rkern.network.sharedPacketWorkers");
            require(shards == (blockedNeighbor && !shared ? 2 : SHARDS),
                    "logical CIO shard count for the selected layout");
            final var pool = new CIOBufferPool(16, 24, 16, 16, shards);
            UserPacketCallbackMap.gPacketTableFromClient[ALIVE] = (input, socket) -> {
                handle(socket, input.readInt());
                return true;
            };
            final var peers = new Peer[SHARDS];
            try (var server = new CIOServer(address, 16, shards, 16, pool, scheduler, config)) {
                require(scheduler.sharedPacketWorkerCount() == (shared ? 2 : 0), "physical packet worker count");
                try {
                    for (int seat = 0; seat < peers.length; seat++) {
                        peers[seat] = new Peer(server.localAddress(), seat);
                        peers[seat].send(1, 1);
                        peers[seat].verify(1, 1);
                    }
                    if (blockedNeighbor) exerciseBlockedNeighbor(peers, shards, shared);
                    else exercise(scheduler, peers);
                } finally {
                    resume.countDown();
                    for (var peer : peers) if (peer != null) peer.close();
                }
                server.close(TIMEOUT, TimeUnit.SECONDS);
                require(server.isTerminated() && server.liveConnections() == 0 && server.pendingSessionCleanup() == 0,
                        "all TCP sessions and Selector drivers released");
                require(pool.usedRecvSlots() == 0 && pool.usedSendSlots() == 0 && pool.usedSharedSourceSlots() == 0,
                        "all packet buffer leases returned");
                for (int seat = 0; seat < SHARDS; seat++)
                    require(sessions.get(seat).isCloseComplete(), "socket session finalized for peer " + seat);
            }
        } finally {
            UserPacketCallbackMap.gPacketTableFromClient[ALIVE] = original;
            try {
                CSessionLifecycle.stopAccepting();
                require(CSessionLifecycle.awaitDrained(TIMEOUT, TimeUnit.SECONDS), "session cleanup workers drained");
            } finally {
                scheduler.shutdown();
                require(scheduler.awaitTermination(TIMEOUT, TimeUnit.SECONDS), "CIO workers and watchdog joined");
            }
        }
        require(blockedNeighbor ? observed.size() <= 2 : observed.size() == 2,
                "physical worker count stayed within the two available CPUs");
        require(observed.keySet().stream().noneMatch(Thread::isAlive), "both observed physical workers exited");
    }

    private void exerciseBlockedNeighbor(Peer[] peers, int shards, boolean shared) throws Exception {
        for (int seat = 0; seat < SHARDS; seat++)
            require(sessions.get(seat).affinityShard() == seat % shards, "round-robin affinity for peer " + seat);

        peers[0].send(2, 2);
        require(held.await(TIMEOUT, TimeUnit.SECONDS), "first callback entered before timing the neighbor");
        final var releasedAt = new AtomicLong();
        final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(BLOCK_MILLIS);
        Thread releaser = Thread.ofPlatform().daemon().name("fixture-blocked-neighbor-release").start(() -> {
            long remaining;
            while ((remaining = deadline - System.nanoTime()) > 0)
                LockSupport.parkNanos(remaining);
            releasedAt.set(System.nanoTime());
            resume.countDown();
        });

        final long sentAt = System.nanoTime();
        peers[2].send(2, 2);
        peers[2].verify(2, 2);
        final long receivedAt = System.nanoTime();
        releaser.join(TimeUnit.SECONDS.toMillis(TIMEOUT));
        require(!releaser.isAlive(), "bounded peer release finished");
        peers[0].verify(2, 2);

        final int origin = sessions.get(0).affinityShard();
        final int neighbor = sessions.get(2).affinityShard();
        final long release = releasedAt.get();
        final long handled = neighborHandledAt.get(TIMEOUT, TimeUnit.SECONDS);
        require(handled > 0L && release > 0L, "neighbor and release timestamps recorded");
        final boolean handlerEarly = handled < release;
        final boolean responseEarly = receivedAt < release;
        if (origin == neighbor)
            require(!handlerEarly && !responseEarly, "the pinned logical shard retained its serial order");
        System.out.printf(Locale.ROOT,
                "BLOCKED_NEIGHBOR shared=%s logical_shards=%d held_shard=%d neighbor_shard=%d "
                        + "hold_ms=%.3f rtt_ms=%.3f handler_before_release=%s response_before_release=%s%n",
                shared, shards, origin, neighbor, (release - heldSinceNanos) / 1e6, (receivedAt - sentAt) / 1e6,
                handlerEarly, responseEarly);
    }

    private void exercise(CIOThreadCallback scheduler, Peer[] peers) throws Exception {
        Set<Integer> packetShards = new HashSet<>();
        for (int seat = 0; seat < SHARDS; seat++) packetShards.add(sessions.get(seat).affinityShard());
        require(packetShards.equals(Set.of(0, 1, 2, 3)), "actual encrypted traffic reached every logical shard");
        final int waitingShard = sessions.get(0).affinityShard();
        final var dbDone = new CompletableFuture<Void>();
        final var timerDone = new CompletableFuture<Void>();

        peers[0].send(2, 2);
        require(held.await(TIMEOUT, TimeUnit.SECONDS), "first packet entered its handler");
        Thread waitingWorker = secondPacket.get(0);
        require(waitingWorker != null && active.get(waitingShard) == 1, "first packet owns its logical turn");
        // completeOnShard is the same entry point used to replay completed persistence work.
        require(scheduler.completeOnShard(waitingShard,
                () -> onShard(waitingShard, completions::incrementAndGet), failure -> {
                    if (failure != null) dbDone.completeExceptionally(failure);
                    else if (CIOThreadCallback.currentShardIndex() != waitingShard)
                        dbDone.completeExceptionally(new AssertionError("completion result on a different logical shard"));
                    else dbDone.complete(null);
                }), "completion admitted while its shard is occupied");
        require(scheduler.scheduleNetworkTimeout(waitingShard, new CIOThreadCallback.WheelTask() {
            @Override public boolean isCancelled() { return false; }
            @Override public void run() {
                try {
                    onShard(waitingShard, timers::incrementAndGet);
                    timerDone.complete(null);
                } catch (AssertionError failure) {
                    timerDone.completeExceptionally(failure);
                }
            }
        }, 1), "timer admitted while its shard is occupied");
        peers[0].send(3, 34);

        for (int seat = 1; seat < SHARDS; seat++) {
            peers[seat].send(2, 17);
            peers[seat].verify(2, 17);
        }
        Thread availableWorker = secondPacket.get(1);
        require(availableWorker != null && availableWorker != waitingWorker, "another physical worker processed live packets");
        for (int seat = 1; seat < SHARDS; seat++)
            require(secondPacket.get(seat) == availableWorker,
                    "available physical worker served logical shard for peer " + seat);
        require(active.get(waitingShard) == 1 && sequence.get(0) == 2
                && completions.get() == 0 && timers.get() == 0 && !dbDone.isDone() && !timerDone.isDone(),
                "same logical shard queued its later packets, timer and persistence completion");

        resume.countDown();
        peers[0].verify(2, 34);
        dbDone.get(TIMEOUT, TimeUnit.SECONDS);
        timerDone.get(TIMEOUT, TimeUnit.SECONDS);
        require(completions.get() == 1 && timers.get() == 1, "queued callbacks each completed once");
        for (int seat = 0; seat < SHARDS; seat++) {
            int next = seat == 0 ? 35 : 18;
            peers[seat].send(next, next);
            peers[seat].verify(next, next);
            require(sequence.get(seat) == next, "crypto stream and per-connection sequence continued");
        }
    }

    private void handle(CUserSocket socket, int value) {
        int seat = value >>> 16;
        int index = value & 0xffff;
        require(seat < SHARDS, "fixture packet seat");
        onShard(socket.affinityShard(), () -> {
            if (index == 1) require(sessions.compareAndSet(seat, null, socket), "one owner per live fixture session");
            require(sessions.get(seat) == socket, "packet remained on its accepted socket");
            require(sequence.compareAndSet(seat, index - 1, index), "packet order for seat " + seat);
            if (index == 2) {
                secondPacket.set(seat, Thread.currentThread());
                if (seat == 0) {
                    heldSinceNanos = System.nanoTime();
                    held.countDown();
                    awaitRelease();
                }
            }
            require(socket.tryWritePacket(socket, new CBytesPacket(body(REPLY, value))), "encrypted reply admitted");
        });
        if (seat == 2 && index == 2) neighborHandledAt.complete(System.nanoTime());
    }

    private void onShard(int expected, Runnable action) {
        int actual = CIOThreadCallback.currentShardIndex();
        require(actual == expected, "actual logical shard " + actual + " expected " + expected);
        require(active.compareAndSet(actual, 0, 1), "logical shard cannot overlap its callbacks");
        try {
            observed.computeIfAbsent(Thread.currentThread(), ignored -> ConcurrentHashMap.newKeySet()).add(actual);
            action.run();
        } finally {
            active.decrementAndGet(actual);
        }
    }

    private void awaitRelease() {
        try {
            require(resume.await(TIMEOUT, TimeUnit.SECONDS), "first handler released by the fixture");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }

    private static byte[] body(int opcode, int value) {
        return ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN).put((byte) opcode).putInt(value).array();
    }

    private static void require(boolean result, String message) {
        if (!result) throw new AssertionError(message);
    }

    private static final class Peer implements AutoCloseable {
        private final Socket wire = new Socket();
        private final DataInputStream input;
        private final int seat;
        private final SelectorTransportTest.ScalarClient cipher = new SelectorTransportTest.ScalarClient(Opcodes.getSeed());

        Peer(InetSocketAddress address, int seat) throws IOException {
            this.seat = seat;
            try {
                wire.setSoTimeout(TIMEOUT * 1000);
                wire.setTcpNoDelay(true);
                wire.connect(address, TIMEOUT * 1000);
                input = new DataInputStream(wire.getInputStream());
                require(Arrays.equals(receive(), Opcodes.S_KEYBYTES), "real CIO handshake key for peer " + seat);
            } catch (IOException | RuntimeException | AssertionError failure) {
                try { wire.close(); } catch (IOException closeFailure) { failure.addSuppressed(closeFailure); }
                throw failure;
            }
        }

        void send(int first, int last) throws IOException {
            var batch = new ByteArrayOutputStream();
            for (int index = first; index <= last; index++) {
                byte[] encrypted = cipher.toServer(body(ALIVE, seat << 16 | index));
                int size = encrypted.length + 2;
                batch.write(size & 255);
                batch.write(size >>> 8);
                batch.writeBytes(encrypted);
            }
            wire.getOutputStream().write(batch.toByteArray());
        }

        void verify(int first, int last) throws IOException {
            for (int index = first; index <= last; index++)
                require(Arrays.equals(receive(), cipher.fromServer(body(REPLY, seat << 16 | index))),
                        "ordered encrypted reply " + index + " for peer " + seat);
        }

        private byte[] receive() throws IOException {
            int size = (input.readUnsignedByte() | input.readUnsignedByte() << 8) - 2;
            require(size > 0 && size <= 4096, "complete game frame length");
            var data = new byte[size];
            input.readFully(data);
            return data;
        }

        @Override public void close() throws IOException { wire.close(); }
    }
}
