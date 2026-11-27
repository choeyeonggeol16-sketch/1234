import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.HashSet;
import java.util.Locale;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import l1j.server.Config;
import l1j.server.server.Account;
import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.config.CIOConfig;
import l1j.server.server.model.network.core.connection.CIOBufferPool;
import l1j.server.server.model.network.core.connection.CIOServer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.session.CSessionLifecycle;
import l1j.server.server.model.network.packet.CBytesPacket;
import l1j.server.server.model.network.packet.UserPacketCallbackMap;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.network.safe.CPacketFilter;
import l1j.server.server.model.network.utill.CConnectionState;

/** Run uniform or hot in fresh JVMs with -XX:ActiveProcessorCount=2 and sharedPacketWorkers=false/true.
 * Six real loopback sessions; a fixture game callback replaces C_PLATE with fixed-cost integer work.
 * Each independent client maintains a 16-request window. Reference wire bytes are prepared before timing.
 * Set rkern.packetLoad.lanes=2 to compare both policies with two logical lanes, or rkern.packetLoad.smoke=true. */
public final class SharedPacketWorkerLoad {
    private static final int SESSIONS = 6, WINDOW = 16;
    private static final int ROUNDS = Integer.getInteger("rkern.packetLoad.rounds", 4096);
    private static final boolean SMOKE = Boolean.getBoolean("rkern.packetLoad.smoke");
    private static final int WARM = SMOKE ? 32 : 1024;
    private static final int ALIVE = Opcodes.C_ALIVE & 255, GAME = Opcodes.C_PLATE & 255, ACK = 0x45;
    private static final AtomicReference<Throwable> failure = new AtomicReference<>();

    private static final class Seat {
        final int id;
        final CompletableFuture<CUserSocket> ready = new CompletableFuture<>();
        final CompletableFuture<Void> finished = new CompletableFuture<>();
        final Thread[] workers = new Thread[2];
        final int[] calls = new int[2];
        int next;
        volatile CUserSocket socket;

        Seat(int id) { this.id = id; }

        void observe() {
            Thread current = Thread.currentThread();
            for (int i = 0; i < workers.length; i++) {
                if (workers[i] == null) workers[i] = current;
                if (workers[i] == current) { calls[i]++; return; }
            }
            throw new AssertionError("more than two physical handler threads");
        }
    }

    private record Peer(Seat seat, Socket wire, DataInputStream in, BufferedOutputStream out,
                        SelectorTransportTest.ScalarClient codec) {}

    public static void main(String[] args) throws Exception {
        require(ROUNDS > 0, "rounds must be positive");
        if (args.length != 1 || !(args[0].equals("uniform") || args[0].equals("hot")))
            throw new IllegalArgumentException("Expected uniform or hot");
        boolean hot = args[0].equals("hot");
        boolean shared = Boolean.parseBoolean(System.getProperty("rkern.network.sharedPacketWorkers", "true"));
        require(Runtime.getRuntime().availableProcessors() == 2, "run with -XX:ActiveProcessorCount=2");
        Config.MAX_ONLINE_USERS = 16;
        CPacketFilter.getInstance().load();
        var cio = CIOThreadCallback.getInstance();
        var address = new InetSocketAddress("127.0.0.1", 0);
        var config = new CIOConfig(address);
        config.packetExecutorShardCount = Integer.getInteger("rkern.packetLoad.lanes", 0);
        config.usePreLoginTimeout = false;
        CIOConfig.NETWORK_CLIENT_PERMISSION = 16;
        CIOConfig.PACKET_LIMIT_PER_SEC = 1_000_000;
        int lanes = cio.bindNetworkIngress(config);
        int expected = config.packetExecutorShardCount == 0 ? (shared ? 4 : 2) : config.packetExecutorShardCount;
        require(lanes == expected, "wrong logical topology " + lanes + " expected " + expected);
        var pool = new CIOBufferPool(16, 128, 16, 16, lanes);
        int count = (SMOKE ? 768 : 36_864) / (hot ? 3 : SESSIONS);
        Seat[] seats = new Seat[SESSIONS];
        for (int i = 0; i < seats.length; i++) seats[i] = new Seat(i);
        var oldAlive = UserPacketCallbackMap.gPacketTableFromClient[ALIVE];
        var oldGame = UserPacketCallbackMap.gPacketTableFromClient[GAME];
        UserPacketCallbackMap.gPacketTableFromClient[ALIVE] = (request, socket) -> {
            Seat seat = seats[request.readInt()];
            try {
                require(seat.socket == null, "duplicate fixture login");
                bind(socket, seat.id);
                seat.socket = socket;
                seat.ready.complete(socket);
                return true;
            } catch (Throwable e) { seat.ready.completeExceptionally(e); throw e; }
        };
        UserPacketCallbackMap.gPacketTableFromClient[GAME] = (request, socket) -> {
            try {
                Seat seat = seats[request.readInt()];
                int seq = request.readInt();
                require(socket == seat.socket && seat.next++ == seq, "handler identity or order");
                require(CIOThreadCallback.currentShardIndex() == socket.affinityShard(), "foreign logical owner");
                if (seq >= WARM) seat.observe();
                require(socket.tryWritePacket(socket, new CBytesPacket(ack(seat.id, seq))), "ACK admission");
                if (seq + 1 == WARM + count) seat.finished.complete(null);
                return true;
            } catch (Throwable e) { failure.compareAndSet(null, e); detach(socket); socket.close(); return false; }
        };

        try (var server = new CIOServer(address, 16, lanes, 16, pool, cio, config)) {
            var peers = new ArrayList<Peer>();
            try {
                for (Seat seat : seats) peers.add(connect(server, seat));
                var active = peers.stream().filter(p -> !hot || p.seat.id % 2 == 0).toList();
                var owners = new TreeSet<Integer>();
                for (Peer p : active) owners.add(p.seat.socket.affinityShard());
                require(owners.size() == (hot ? (lanes == 2 ? 1 : 2) : lanes), "unexpected hot-session routing " + owners);
                var clients = new ArrayList<Client>();
                for (var p : active) clients.add(new Client(p, count));
                try (var workers = Executors.newFixedThreadPool(clients.size())) {
                    phase(workers, clients, 0, WARM);
                    long elapsed = phase(workers, clients, WARM, count);
                    long[] latencies = new long[count * clients.size()];
                    var physical = new TreeMap<String, Integer>();
                    var identities = new HashSet<Thread>();
                    int n = 0;
                    for (Client client : clients) {
                        Seat seat = client.peer.seat;
                        seat.finished.get(5, TimeUnit.SECONDS);
                        require(seat.next == WARM + count, "request conservation");
                        for (int i = 0; i < seat.workers.length; i++) if (seat.workers[i] != null) {
                            identities.add(seat.workers[i]);
                            physical.merge(seat.workers[i].getName(), seat.calls[i], Integer::sum);
                        }
                        System.arraycopy(client.rtt, 0, latencies, n, count);
                        n += count;
                    }
                    require(physical.size() == identities.size() && identities.size() <= 2
                            && physical.values().stream().mapToInt(Integer::intValue).sum() == n,
                            "physical worker count and measured callbacks");
                    Arrays.sort(latencies);
                    System.out.printf(Locale.ROOT, "PACKET mode=%s shared=%s cpuWorkers=2 logical=%d active=%d"
                                    + " owners=%s pipeline=%d rounds=%d warmupPerClient=%d completed=%d"
                                    + " reqPerSec=%.2f p99RttUs=%.3f physicalCalls=%s%n",
                            args[0], shared, lanes, clients.size(), owners, WINDOW, ROUNDS, WARM, n,
                            n * 1e9 / elapsed, latencies[(int) Math.ceil(n * 0.99) - 1] / 1e3, physical);
                }
                for (Peer p : peers) {
                    detach(p.seat.socket);
                    p.seat.socket.requestClientCloseAfter(0);
                    require(p.in.read() == -1, "exact last ACK then EOF");
                }
                long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while ((pool.usedRecvSlots() != 0 || pool.usedSendSlots() != 0) && System.nanoTime() < until)
                    Thread.sleep(1);
                require(pool.usedRecvSlots() == 0 && pool.usedSendSlots() == 0 && pool.usedSharedSourceSlots() == 0,
                        "all socket leases returned");
            } finally {
                for (Peer p : peers) { detach(p.seat.socket); p.wire.close(); }
            }
        } finally {
            UserPacketCallbackMap.gPacketTableFromClient[ALIVE] = oldAlive;
            UserPacketCallbackMap.gPacketTableFromClient[GAME] = oldGame;
            cio.shutdown();
            require(cio.awaitTermination(8, TimeUnit.SECONDS), "CIO stopped");
            CSessionLifecycle.stopAccepting();
            require(CSessionLifecycle.awaitDrained(8, TimeUnit.SECONDS), "sessions drained");
        }
        System.out.println("PASS real inbound, logical owner, FIFO encrypted ACKs and drained leases");
    }

    private static void bind(CUserSocket socket, int id) {
        var account = new Account();
        account.setAccountId(900_000 + id);
        account.setName("packetbench" + id);
        account.setValid(true);
        require(socket.bindVerifiedMacIdentity(account.getAccountId()), "fixture identity");
        socket.setAccount(account);
        socket.setConnectionState(CConnectionState.AUTHENTICATED);
        var actor = new L1PcInstance();
        actor.setId(900_100 + id);
        actor.setName("PacketBench" + id);
        actor.setNetConnection(socket);
        socket.setActiveChar(actor);
        socket.setConnectionState(CConnectionState.IN_GAME);
        socket.publishGameSessionReady(actor);
        require(socket.ownsReadyGameSession(), "ready synthetic player");
    }

    private static void detach(CUserSocket socket) {
        if (socket == null || socket.getActiveChar() == null) return;
        var actor = socket.getActiveChar();
        socket.setActiveChar(null);
        actor.setNetConnection(null);
        socket.setAccount(null);
        socket.setConnectionState(CConnectionState.OUT_GAME);
    }

    private static Peer connect(CIOServer server, Seat seat) throws Exception {
        var wire = new Socket();
        try {
            wire.setTcpNoDelay(true);
            wire.setSoTimeout(6000);
            wire.connect(server.localAddress(), 5000);
            var in = new DataInputStream(new BufferedInputStream(wire.getInputStream()));
            var out = new BufferedOutputStream(wire.getOutputStream());
            require(Arrays.equals(read(in), Opcodes.S_KEYBYTES), "handshake");
            var codec = new SelectorTransportTest.ScalarClient(Opcodes.getSeed());
            out.write(framed(codec.toServer(body(ALIVE, seat.id, 0))));
            out.flush();
            seat.ready.get(6, TimeUnit.SECONDS);
            return new Peer(seat, wire, in, out, codec);
        } catch (Exception | Error e) { detach(seat.socket); wire.close(); throw e; }
    }

    private static final class Client {
        final Peer peer;
        final byte[][] request, expected;
        final long[] rtt;

        Client(Peer peer, int count) {
            this.peer = peer;
            rtt = new long[count];
            request = new byte[WARM + count][];
            expected = new byte[request.length][];
            for (int seq = 0; seq < request.length; seq++) {
                request[seq] = framed(peer.codec.toServer(body(GAME, peer.seat.id, seq)));
                expected[seq] = peer.codec.fromServer(ack(peer.seat.id, seq));
            }
        }

        void pump(int first, int count) throws IOException {
            long[] sentAt = new long[WINDOW];
            int sent = first;
            for (int completed = first; completed < first + count; completed++) {
                int limit = Math.min(first + count, completed + WINDOW);
                while (sent < limit) {
                    sentAt[sent % WINDOW] = System.nanoTime();
                    peer.out.write(request[sent++]);
                }
                peer.out.flush();
                byte[] response = read(peer.in);
                long now = System.nanoTime();
                require(Arrays.equals(response, expected[completed]), "incorrect ACK order or CPU result");
                if (first == WARM) rtt[completed - first] = now - sentAt[completed % WINDOW];
            }
        }
    }

    private static long phase(java.util.concurrent.ExecutorService workers, List<Client> clients,
                              int first, int count) throws Exception {
        var ready = new CountDownLatch(clients.size());
        var go = new CountDownLatch(1);
        var futures = new ArrayList<java.util.concurrent.Future<Void>>();
        for (Client client : clients) futures.add(workers.submit(() -> {
            ready.countDown();
            go.await();
            client.pump(first, count);
            return null;
        }));
        require(ready.await(6, TimeUnit.SECONDS), "clients ready");
        long begin = System.nanoTime();
        go.countDown();
        try {
            for (var future : futures) future.get(45, TimeUnit.SECONDS);
        } finally {
            if (failure.get() != null) throw new AssertionError("production packet callback failed", failure.get());
        }
        return System.nanoTime() - begin;
    }

    private static byte[] ack(int seat, int sequence) {
        int x = (seat + 1) * 0x9e3779b9 ^ sequence;
        for (int i = 0; i < ROUNDS; i++) x = Integer.rotateLeft(x ^ (x >>> 13), 9) * 0x85ebca6b + i;
        return ByteBuffer.allocate(13).order(ByteOrder.LITTLE_ENDIAN)
                .put((byte) ACK).putInt(seat).putInt(sequence).putInt(x).array();
    }

    private static byte[] body(int op, int seat, int seq) {
        return ByteBuffer.allocate(9).order(ByteOrder.LITTLE_ENDIAN).put((byte) op).putInt(seat).putInt(seq).array();
    }

    private static byte[] framed(byte[] payload) {
        return ByteBuffer.allocate(payload.length + 2).order(ByteOrder.LITTLE_ENDIAN)
                .putShort((short) (payload.length + 2)).put(payload).array();
    }

    private static byte[] read(DataInputStream in) throws IOException {
        int length = (in.readUnsignedByte() | in.readUnsignedByte() << 8) - 2;
        require(length > 0 && length <= 1024, "valid frame size");
        byte[] data = new byte[length];
        in.readFully(data);
        return data;
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
