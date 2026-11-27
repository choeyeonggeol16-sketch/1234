import java.io.DataInputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import l1j.server.Config;
import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer;
import l1j.server.server.model.network.core.broadcast.CSharedBroadcast;
import l1j.server.server.model.network.core.config.CIOConfig;
import l1j.server.server.model.network.core.connection.CIOBufferEx;
import l1j.server.server.model.network.core.connection.CIOBufferPool;
import l1j.server.server.model.network.core.connection.CIOServer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.packet.CBytesPacket;
import l1j.server.server.model.network.packet.UserPacketCallbackMap;
import l1j.server.server.model.network.safe.CPacketFilter;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.network.core.session.CSessionLifecycle;

/**
 * Real loopback peers exercising production broadcast, SEND leases, and per-peer cipher state.
 * Compile alongside the current SelectorTransportTest for its reference client codec.
 * Run with no argument, then with pressure and one-slot in fresh JVMs.
 */
public final class BroadcastDeliveryRegression {
    private static final int ALIVE = Opcodes.C_ALIVE & 255;
    private static final int ACK = 0x45;
    private static final byte[] SHARED = new byte[64];
    private static final byte[] RECOVERY = new byte[32];

    public static void main(String[] args) throws Exception {
        Arrays.fill(SHARED, (byte) 0x42);
        Arrays.fill(RECOVERY, (byte) 0x23);
        boolean pressure = args.length > 0 && args[0].equals("pressure");
        boolean oneSlot = args.length > 0 && args[0].equals("one-slot");
        int maxPeers = pressure || oneSlot ? 4 : 504;
        Config.MAX_ONLINE_USERS = (short) maxPeers;
        CPacketFilter.getInstance().load();
        var owners = CIOThreadCallback.getInstance();
        var address = new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0);
        var config = new CIOConfig(address);
        config.packetExecutorShardCount = 2;
        config.usePreLoginTimeout = false;
        CIOConfig.NETWORK_CLIENT_PERMISSION = maxPeers;
        int shards = owners.bindNetworkIngress(config);
        var pool = new CIOBufferPool(maxPeers, oneSlot ? 1 : pressure ? 2 : maxPeers * 2, maxPeers, maxPeers, shards);
        var sockets = new ConcurrentHashMap<Integer, CUserSocket>();
        var previous = UserPacketCallbackMap.gPacketTableFromClient[ALIVE];
        UserPacketCallbackMap.gPacketTableFromClient[ALIVE] = (request, client) -> {
            int sequence = request.readInt();
            sockets.putIfAbsent(sequence, client);
            return client.tryWritePacket(client, new CBytesPacket(body(ACK, sequence)));
        };
        try (var server = new CIOServer(address, maxPeers, shards, maxPeers, pool, owners, config)) {
            var peers = new ArrayList<Peer>();
            try {
                if (pressure) verifyPressure(server, pool, sockets, peers);
                else if (oneSlot) verifyOneSlot(server, pool, sockets, peers);
                else if (args.length > 0 && args[0].equals("fanout-load"))
                    measureFanout(server, pool, sockets, peers);
                else verifyFanout(server, pool, sockets, peers);
            } finally {
                for (Peer peer : peers) peer.socket.close();
                await("all connections drained", () -> server.accountRegistryDiagnostics()[0] == 0);
                await("all send and receive leases returned", () -> Arrays.stream(server.poolSnapshot()).allMatch(row -> row[2] == 0));
            }
        } finally {
            UserPacketCallbackMap.gPacketTableFromClient[ALIVE] = previous;
            owners.shutdown();
            if (!owners.awaitTermination(5, TimeUnit.SECONDS)) throw new AssertionError("owner shutdown timed out");
            CSessionLifecycle.stopAccepting();
            if (!CSessionLifecycle.awaitDrained(5, TimeUnit.SECONDS))
                throw new AssertionError("session release shutdown timed out");
        }
        if (ManagementFactory.getThreadMXBean().findDeadlockedThreads() != null)
            throw new AssertionError("JVM deadlock found after loopback broadcast");
        System.out.println("PASS drained all sockets and leases, no JVM deadlock");
    }

    private static void verifyFanout(CIOServer server, CIOBufferPool pool, ConcurrentHashMap<Integer, CUserSocket> sockets,
            List<Peer> peers) throws Exception {
        var actors = new ArrayList<L1PcInstance>();
        var encodes = new AtomicInteger();
        for (int count : new int[] {20, 100, 500}) {
            while (peers.size() < count) actors.add(connect(server, pool, sockets, peers));
            int before = encodes.get();
            CSharedBroadcast.sendWrittenRecipientsBestEffort(actors, out -> {
                encodes.incrementAndGet();
                out.put(SHARED);
            });
            for (Peer peer : peers) peer.expect(SHARED);
            check(encodes.get() == before + 1, "encoded fan-out more than once");
            System.out.println("PASS " + count + " encrypted loopback recipients, one source encoding");
        }
        peers.get(250).socket.close();
        await("middle recipient closed", () -> !sockets.get(250).isConnected());
        CSharedBroadcast.sendWrittenRecipientsBestEffort(actors, out -> out.put(RECOVERY));
        for (int index = 0; index < peers.size(); index++) if (index != 250) peers.get(index).expect(RECOVERY);
        System.out.println("PASS closed middle recipient did not interrupt 499 remaining writes");
        verifyConcurrentFanout(actors.subList(0, 20), peers.subList(0, 20));
    }

    /** Closed-loop windows of eight events. Latency ends when the last of 300 clients decodes an event. */
    private static void measureFanout(CIOServer server, CIOBufferPool pool, ConcurrentHashMap<Integer, CUserSocket> sockets,
            List<Peer> peers) throws Exception {
        final int recipients = 300;
        final int window = 8;
        final int warmup = 128;
        final int measured = 512;
        final int events = warmup + measured;
        var actors = new ArrayList<L1PcInstance>(recipients);
        while (peers.size() < recipients) actors.add(connect(server, pool, sockets, peers));
        byte[][] messages = new byte[events][];
        long[] sent = new long[events];
        long[][] delivered = new long[recipients][events];
        CountDownLatch[] windows = new CountDownLatch[events / window];
        for (int index = 0; index < events; index++) messages[index] = body(0x6d, index);
        Arrays.setAll(windows, ignored -> new CountDownLatch(recipients));
        long sharedAtStart = 0;

        try (var readers = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new ArrayList<Future<?>>();
            for (int id = 0; id < recipients; id++) {
                Peer peer = peers.get(id);
                long[] arrival = delivered[id];
                futures.add(readers.submit(() -> {
                    for (int index = 0; index < events; index++) {
                        peer.expect(messages[index]);
                        arrival[index] = System.nanoTime();
                        if ((index + 1) % window == 0) windows[index / window].countDown();
                    }
                    return null;
                }));
            }
            for (int index = 0; index < events; index++) {
                if (index == warmup) sharedAtStart = sharedWorkRuns(server);
                final byte[] message = messages[index];
                sent[index] = System.nanoTime();
                CSharedBroadcast.sendWrittenRecipientsBestEffort(actors, out -> out.put(message));
                if ((index + 1) % window == 0)
                    check(windows[index / window].await(5, TimeUnit.SECONDS), "fanout window did not drain");
            }
            for (Future<?> future : futures) future.get(5, TimeUnit.SECONDS);
        }
        long[] latency = new long[measured];
        long last = 0;
        for (int index = warmup; index < events; index++) {
            for (long[] arrival : delivered) last = Math.max(last, arrival[index]);
            latency[index - warmup] = last - sent[index];
        }
        Arrays.sort(latency);
        System.out.printf(java.util.Locale.ROOT, "FANOUT recipients=%d events=%d deliveries/s=%.0f last-client-p99-ms=%.3f handoffs=%d%n",
                recipients, measured, recipients * (double) measured * 1e9 / (last - sent[warmup]),
                latency[(measured * 99 + 99) / 100 - 1] / 1e6, sharedWorkRuns(server) - sharedAtStart);
    }

    private static long sharedWorkRuns(CIOServer server) {
        long runs = 0;
        for (var owner : server.ownerDiagnostics()) runs += owner.sharedWorkRuns();
        return runs;
    }

    private static void verifyConcurrentFanout(List<L1PcInstance> actors, List<Peer> peers) throws Exception {
        final int producers = 4;
        final int rounds = 8;
        final int total = producers * rounds;
        final int tag = 0x6d;
        final var ready = new CountDownLatch(producers);
        final var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(producers, Thread.ofPlatform().daemon().factory());
        try {
            var tasks = new ArrayList<Future<?>>();
            for (int index = 0; index < producers; index++) {
                final int first = index * rounds;
                tasks.add(executor.submit(() -> {
                    ready.countDown();
                    check(start.await(5, TimeUnit.SECONDS), "producer start timeout");
                    for (int round = 0; round < rounds; round++) {
                        byte[] marker = body(tag, first + round);
                        CSharedBroadcast.sendWrittenRecipientsBestEffort(actors, out -> out.put(marker));
                    }
                    return null;
                }));
            }
            check(ready.await(5, TimeUnit.SECONDS), "broadcast producers not ready");
            start.countDown();
            for (Future<?> task : tasks) task.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
        for (Peer peer : peers) {
            var received = new BitSet(total);
            for (int i = 0; i < total; i++) {
                var frame = ByteBuffer.wrap(peer.codec.fromServer(peer.read())).order(ByteOrder.LITTLE_ENDIAN);
                check(frame.remaining() == 5 && (frame.get() & 255) == tag, "bad concurrent broadcast frame");
                int sequence = frame.getInt();
                check(sequence >= 0 && sequence < total && !received.get(sequence), "duplicate or invalid concurrent frame");
                received.set(sequence);
            }
            check(received.cardinality() == total, "concurrent recipient missed frames");
        }
        System.out.println("PASS 4 concurrent producers delivered 640 independently decrypted frames exactly once");
    }

    private static void verifyOneSlot(CIOServer server, CIOBufferPool pool,
            ConcurrentHashMap<Integer, CUserSocket> sockets, List<Peer> peers) throws Exception {
        L1PcInstance actor = connect(server, pool, sockets, peers);
        await("handshake SEND leases returned", () -> pool.usedSendSlots() == 0);
        CSharedBroadcast.sendWrittenRecipientsBestEffort(List.of(actor), out -> out.put(SHARED));
        check(sockets.get(0).isConnected(), "source allocation consumed the only socket SEND slot");
        peers.getFirst().expect(SHARED);
        await("frozen source released", () -> pool.usedSharedSourceSlots() == 0);
        System.out.println("PASS one SEND slot delivered with a separate frozen source slot");
        CUserSocket socket = sockets.get(0);
        var occupiedSmallSource = socket.createPooledWritableConstBuffer();
        check(occupiedSmallSource != null && pool.usedSharedSourceSlots() == 1, "small source slot was not reserved");
        try {
            long refusals = pool.sharedSourceRefusals();
            CSharedBroadcast.sendWrittenRecipientsBestEffort(List.of(actor), out -> out.put(RECOVERY));
            check(socket.isConnected() && pool.sharedSourceRefusals() == refusals + 1,
                    "small source pressure disconnected the recipient or was uncounted");
            byte[] large = new byte[CIOBufferPool.SMALL_SEND_BYTES + 123];
            Arrays.fill(large, (byte) 0x6f);
            byte[] expected = large.clone();
            try (var shared = CSharedBroadcast.openRaw(large)) {
                large[0] = 0;
                CSharedBroadcast.sendRecipientBestEffort(shared, actor);
                peers.getFirst().expect(expected);
            }
        } finally {
            occupiedSmallSource.release();
        }
        System.out.println("PASS large raw broadcast delivered while small source pool was full");
    }

    private static void verifyPressure(CIOServer server, CIOBufferPool pool,
            ConcurrentHashMap<Integer, CUserSocket> sockets, List<Peer> peers) throws Exception {
        L1PcInstance first = connect(server, pool, sockets, peers);
        L1PcInstance optional = connect(server, pool, sockets, peers);
        L1PcInstance strict = connect(server, pool, sockets, peers);
        await("handshake SEND leases returned", () -> pool.usedSendSlots() == 0);
        final long refusals = pool.optionalBroadcastRefusals();
        try (var shared = new CAsyncSendConstBuffer(out -> out.put(SHARED), -1, Integer.MAX_VALUE)) {
            CSharedBroadcast.sendRecipient(shared, first);
            peers.getFirst().expect(SHARED);
            await("initial broadcast SEND lease returned", () -> pool.usedSmallSendSlots() == 0);
            var blockers = new ArrayList<CIOBufferEx>();
            try {
                for (int index = 0; index < pool.smallSendSlots(); index++) {
                    CIOBufferEx lease = pool.acquireSend(3);
                    check(lease != null, "test could not saturate SEND pool");
                    blockers.add(lease);
                }
                CSharedBroadcast.sendRecipientBestEffort(shared, optional);
                check(sockets.get(1).isConnected(), "optional broadcast closed peer under SEND saturation");
                check(pool.optionalBroadcastRefusals() == refusals + 1, "optional refusal was not counted");
                CSharedBroadcast.sendRecipient(shared, strict);
                await("strict saturation closed affected peer", () -> !sockets.get(2).isConnected());
                check(sockets.get(0).isConnected() && sockets.get(1).isConnected(), "strict failure crossed to other peers");
            } finally {
                for (CIOBufferEx lease : blockers) pool.release(lease);
            }
        }
        CUserSocket survivor = sockets.get(1);
        var frame = survivor.beginSafeDirectFrame(16, 0);
        check(frame != null, "optional direct frame was not reserved after pressure");
        boolean committed = false;
        try {
            committed = survivor.commitDirectFrame(frame);
            check(!committed, "empty frame passed optional commit");
        } finally {
            if (!committed) survivor.abortDirectFrame(frame);
        }
        check(survivor.isConnected(), "invalid optional commit closed recipient");
        await("optional direct reservation returned", () -> pool.usedSendSlots() == 0);
        CSharedBroadcast.sendWrittenRecipientsBestEffort(List.of(first, optional, strict), out -> out.put(RECOVERY));
        peers.get(0).expect(RECOVERY);
        peers.get(1).expect(RECOVERY);
        System.out.println("PASS optional refusal and invalid commit kept socket and cipher usable, strict refusal closed only its target");
    }

    private static L1PcInstance connect(CIOServer server, CIOBufferPool pool, ConcurrentHashMap<Integer, CUserSocket> sockets,
            List<Peer> peers) throws Exception {
        int id = peers.size();
        Peer peer = new Peer(server.localAddress());
        peers.add(peer);
        for (int index = 0; index <= id % 3; index++) {
            if (pool.smallSendSlots() <= 2) await("previous SEND lease returned", () -> pool.usedSendSlots() == 0);
            peer.send(id);
            peer.expect(body(ACK, id));
        }
        L1PcInstance character = new L1PcInstance();
        character.setId(id + 1);
        character.setNetConnection(sockets.get(id));
        return character;
    }

    private static void await(String label, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(1);
        check(condition.getAsBoolean(), label);
    }

    private static void check(boolean result, String message) {
        if (!result) throw new AssertionError(message);
    }

    private static byte[] body(int opcode, int sequence) {
        return ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN).put((byte) opcode).putInt(sequence).array();
    }

    private static final class Peer {
        private final Socket socket;
        private final DataInputStream input;
        private final SelectorTransportTest.ScalarClient codec = new SelectorTransportTest.ScalarClient(Opcodes.getSeed());

        private Peer(InetSocketAddress address) throws IOException {
            socket = new Socket();
            try {
                socket.setTcpNoDelay(true);
                socket.setSoTimeout(5000);
                socket.connect(address, 3000);
                input = new DataInputStream(socket.getInputStream());
                check(Arrays.equals(read(), Opcodes.S_KEYBYTES), "handshake mismatch");
            } catch (IOException | Error failure) {
                socket.close();
                throw failure;
            }
        }

        private void send(int value) throws IOException {
            byte[] encoded = codec.toServer(body(ALIVE, value));
            socket.getOutputStream().write(ByteBuffer.allocate(encoded.length + 2).order(ByteOrder.LITTLE_ENDIAN)
                    .putShort((short) (encoded.length + 2)).put(encoded).array());
        }

        private byte[] read() throws IOException {
            int length = (input.readUnsignedByte() | input.readUnsignedByte() << 8) - 2;
            if (length < 1) throw new AssertionError("empty on-wire frame");
            byte[] data = new byte[length];
            input.readFully(data);
            return data;
        }

        private void expect(byte[] plain) throws IOException {
            check(Arrays.equals(read(), codec.fromServer(plain)), "recipient ciphertext or FIFO mismatch");
        }
    }
}
