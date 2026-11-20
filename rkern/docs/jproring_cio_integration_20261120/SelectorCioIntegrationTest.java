import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import l1j.server.Config;
import l1j.server.Server;
import l1j.server.server.Account;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer;
import l1j.server.server.model.network.core.broadcast.CSharedBroadcast;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.packet.CPacketHandler;
import l1j.server.server.model.network.packet.WritablePacket;
import l1j.server.server.model.network.safe.NetworkSafeLoadManager;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.network.utill.ConnectionState;
import l1j.server.server.packets.Protocol.MainServer.PlaySupport.window.PlaySupportWindow;

/** Real gameplay listener, scheduler, socket pumps and Java crypto; no database or live world. */
public final class SelectorCioIntegrationTest {
    private static final int ALIVE = Opcodes.C_ALIVE & 255;
    private static final int MOVE = Opcodes.C_MOVE & 255;
    private static final int BURST = 41;
    private static final Pattern POOL_REPORT = Pattern.compile(
            "JProRing direct bytes=(\\d+)/(\\d+) receive slots=(\\d+) send slots=(\\d+).*");

    public static void main(String[] arguments) throws Exception {
        check(!Files.exists(Path.of("CTransport.dll")) && !Files.exists(Path.of("CIOPacketCodec.dll")), "DLL-free working directory");
        try {
            Files.createDirectory(Path.of("config"));
            Files.writeString(Path.of("config/Network.ini"), "DisruptorShardCount=2\nNetworkClientPermission=2\nMaxPacketsPerCompletion=8\n");
            Config.MAX_ONLINE_USERS = 16;
            int gamePort = reservePort();
            Config.GAME_SERVER_PORT = gamePort;
            System.setProperty("ctransport.macLimit", "1");
            int companionPort = reservePort();
            while (companionPort == gamePort) companionPort = reservePort();
            System.setProperty("pss.uiBridge.port", Integer.toString(companionPort));
            NetworkSafeLoadManager.getInstance().load();
            AtomicInteger rejectedOpcodePackets = new AtomicInteger();
            Set<Integer> owners = ConcurrentHashMap.newKeySet();
            AtomicInteger responses = new AtomicInteger();
            var clients = new ConcurrentHashMap<Integer, CUserSocket>();
            CountDownLatch timer = new CountDownLatch(1);
            CountDownLatch ownerHeld = new CountDownLatch(1);
            CountDownLatch releaseOwner = new CountDownLatch(1);
            CountDownLatch competingOwnerSend = new CountDownLatch(1);
            CPacketHandler.PacketHandler alive = CPacketHandler.HANDLERS[ALIVE];
            CPacketHandler.PacketHandler move = CPacketHandler.HANDLERS[MOVE];
            CPacketHandler.HANDLERS[MOVE] = (payload, client) -> { rejectedOpcodePackets.incrementAndGet(); return true; };
            CPacketHandler.HANDLERS[ALIVE] = (payload, client) -> {
                CUserSocket socket = client.getConnection();
                int shard = CIOThreadCallback.currentShardIndex();
                check(shard >= 0 && socket.affinityShard() == shard, "packet executed on its existing CIO shard");
                owners.add(shard);
                int value = payload.readInt();
                if (value == 0 || value == 900) clients.put(value, socket);
                if (value == 903 || value == 904) {
                    check(socket.tryWritePacket(client, new Reply(value, () -> {})), "owner packet admitted");
                    if (value == 903) competingOwnerSend.countDown();
                    return true;
                }
                if (value == 902) {
                    clients.put(value, socket);
                    ownerHeld.countDown();
                    try { check(releaseOwner.await(5, TimeUnit.SECONDS), "stalled test owner released"); }
                    catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(interrupted);
                    }
                    return true;
                }
                int responseIndex = responses.incrementAndGet();
                if (responseIndex == 1) {
                    check(CIOThreadCallback.getInstance().scheduleNetworkTimeout(shard, new CIOThreadCallback.WheelTask() {
                        @Override public void run() {
                            check(CIOThreadCallback.currentShardIndex() == shard, "existing timer owner");
                            timer.countDown();
                        }
                        @Override public boolean isCancelled() { return false; }
                    }, 20), "game wheel accepts work from the Selector owner");
                }
                try (CAsyncSendConstBuffer reply = new CAsyncSendConstBuffer(
                        out -> out.put((byte) 0x45).putInt(value), Integer.MIN_VALUE, Integer.MAX_VALUE)) {
                    check(reply.send(socket, 1), "shared Java source admitted");
                }
                return true;
            };
            try {
                check(Server.startLoginServer(), "normal RKern login listener started");
                try (Socket first = connect(gamePort); Socket second = connect(gamePort)) {
                    check(Arrays.equals(readBody(first), Opcodes.S_KEYBYTES), "first initial key bytes");
                    check(Arrays.equals(readBody(second), Opcodes.S_KEYBYTES), "second initial key bytes");
                    try (Socket excess = connect(gamePort)) {
                        try { check(excess.getInputStream().read() == -1, "IP limit closes excess connection"); }
                        catch (SocketException expected) { check(!excess.isInputShutdown(), "peer reset"); }
                    }
                    var firstCodec = new SelectorTransportTest.ScalarClient(Opcodes.getSeed());
                    var secondCodec = new SelectorTransportTest.ScalarClient(Opcodes.getSeed());
                    var stream = new ByteArrayOutputStream();
                    // The existing session gate must ignore movement before authentication and still advance the key.
                    stream.write(frame(firstCodec.toServer(body(MOVE, 1))));
                    for (int i = 0; i < BURST; i++) stream.write(frame(firstCodec.toServer(body(ALIVE, i))));
                    byte[] batch = stream.toByteArray();
                    first.getOutputStream().write(batch, 0, 1);
                    first.getOutputStream().write(batch, 1, batch.length - 1);
                    second.getOutputStream().write(frame(secondCodec.toServer(body(ALIVE, 900))));
                    for (int i = 0; i < BURST; i++) expectEncrypted(first, firstCodec, i);
                    expectEncrypted(second, secondCodec, 900);
                    check(responses.get() == BURST + 1 && rejectedOpcodePackets.get() == 0, "packet and session gates reconcile");
                    check(owners.equals(Set.of(0, 1)), "both existing CIO shards performed packet work");
                    check(timer.await(5, TimeUnit.SECONDS), "game timer ran alongside I/O");
                    CUserSocket firstSession = clients.get(0);
                    CUserSocket secondSession = clients.get(900);
                    verifyConcurrentSendFifo(first, firstCodec, firstSession, competingOwnerSend);
                    check(firstSession.bindVerifiedMacIdentity(1L), "first verified MAC accepted");
                    check(!secondSession.bindVerifiedMacIdentity(1L), "per-MAC limit enforced");
                    check(secondSession.bindVerifiedMacIdentity(2L), "second verified MAC accepted");
                    Account account = new Account();
                    account.setAccountId(41);
                    firstSession.onAccountChanged(account);
                    secondSession.onAccountChanged(account);
                    firstSession.onSessionStateChanged(ConnectionState.IN_GAME);
                    check(Server.findEnteringAccount(41) != 0, "first account entry registered");
                    try {
                        secondSession.onSessionStateChanged(ConnectionState.IN_GAME);
                        throw new AssertionError("duplicate character entry was accepted");
                    } catch (IllegalStateException expected) {
                        check(Server.networkAccountRegistryDiagnostics()[3] == 1, "duplicate denial counted");
                    }
                    byte[] large = new byte[21_000];
                    Arrays.fill(large, (byte) 0x52);
                    try (var broadcast = new CAsyncSendConstBuffer(out -> out.put(large), -1, Integer.MAX_VALUE)) {
                        check(broadcast.send(firstSession, 1), "large shared source from a foreign producer admitted");
                    }
                    check(Arrays.equals(readBody(first), firstCodec.fromServer(large)), "large encrypted broadcast");
                    var areaSource = CAsyncSendConstBuffer.class.getDeclaredConstructor(
                            CSharedBroadcast.NativeWireWriter.class, int.class, int.class, int.class);
                    areaSource.setAccessible(true);
                    AtomicInteger areaEncodes = new AtomicInteger();
                    CSharedBroadcast.NativeWireWriter areaWriter = out -> {
                        areaEncodes.incrementAndGet();
                        out.put(large);
                    };
                    try (var area = areaSource.newInstance(areaWriter, -1, Integer.MAX_VALUE, large.length + 2)) {
                        check(area.send(firstSession, 1), "large area attack source admitted");
                    }
                    check(areaEncodes.get() == 1 && Arrays.equals(readBody(first), firstCodec.fromServer(large)),
                            "large area attack uses a single pooled plaintext");
                    try (var malformed = areaSource.newInstance(areaWriter, -1, Integer.MAX_VALUE, large.length + 3)) {
                        try {
                            malformed.send(firstSession, 1);
                            throw new AssertionError("invalid exact-size broadcast was accepted");
                        } catch (IllegalStateException expected) {
                            check(!malformed.hasFrozenSource(), "invalid frame was never published");
                        }
                    }
                    Server.stopNewConnections();
                    first.getOutputStream().write(frame(firstCodec.toServer(body(ALIVE, 901))));
                    expectEncrypted(first, firstCodec, 901);
                    check(Server.networkPoolReport().startsWith("JProRing"), "Java pool diagnostics available");
                    try (var draining = new CAsyncSendConstBuffer(out -> out.put(large), -1, Integer.MAX_VALUE)) {
                        for (int i = 0; i < 12; i++) check(draining.send(firstSession, 1), "SEND admitted before graceful close");
                        firstSession.requestClientClose(null);
                        check(!draining.send(firstSession, 1), "new SEND rejected during graceful close");
                    }
                    for (int i = 0; i < 12; i++) {
                        check(Arrays.equals(readBody(first), firstCodec.fromServer(large)), "game FIFO drains before FIN");
                    }
                    check(first.getInputStream().read() == -1, "game graceful close sends FIN after admitted frames");
                }
                awaitIdlePool();
                Server.stopLoginServer();
                check(Server.startLoginServer(), "same CIO scheduler can attach a fresh listener after shutdown");
                try (Socket restarted = connect(gamePort)) {
                    check(Arrays.equals(readBody(restarted), Opcodes.S_KEYBYTES), "restart handshake");
                }
                awaitIdlePool();
                try (Socket stalled = connect(gamePort)) {
                    check(Arrays.equals(readBody(stalled), Opcodes.S_KEYBYTES), "pending WRITE fixture handshake");
                    stalled.setSoLinger(true, 0);
                    var codec = new SelectorTransportTest.ScalarClient(Opcodes.getSeed());
                    stalled.getOutputStream().write(frame(codec.toServer(body(ALIVE, 902))));
                    check(ownerHeld.await(5, TimeUnit.SECONDS), "owner held before transport WRITE");
                    CUserSocket blocked = clients.get(902);
                    byte[] queueBody = new byte[21_000];
                    try (var queued = new CAsyncSendConstBuffer(out -> out.put(queueBody), -1, Integer.MAX_VALUE)) {
                        for (int i = 0; i < 12; i++) check(queued.send(blocked, 1), "WRITE pending before peer reset");
                        var pool = POOL_REPORT.matcher(Server.networkPoolReport());
                        check(pool.matches() && Integer.parseInt(pool.group(4)) > 0, "pending WRITE retains pooled buffers");
                    }
                } finally {
                    releaseOwner.countDown();
                }
                awaitIdlePool();
                for (int i = 0; i < 24; i++) {
                    try (Socket reset = connect(gamePort)) {
                        check(Arrays.equals(readBody(reset), Opcodes.S_KEYBYTES), "reconnect after prior lease cleanup");
                        reset.setSoLinger(true, 0);
                        reset.getOutputStream().write(new byte[] { 60, 0, 1 });
                    }
                    awaitIdlePool();
                }
            } finally {
                try { Server.stopLoginServer(); }
                finally {
                    CPacketHandler.HANDLERS[ALIVE] = alive;
                    CPacketHandler.HANDLERS[MOVE] = move;
                }
            }
        } finally {
            PlaySupportWindow.stopIndependent();
            CIOThreadCallback owners = CIOThreadCallback.getInstance();
            owners.shutdown();
            check(owners.awaitTermination(5, TimeUnit.SECONDS), "existing game owners joined");
            l1j.server.server.ThreadPool.getInstance().shutdown();
        }
        System.out.println("PASS RKern login entry, 2 CIO owners, timer, MAC/account admission, large broadcast, graceful FIFO drain, restart, reset with queued WRITEs, and 24 reset/reconnect cycles with zero outstanding leases");
    }

    private static void awaitIdlePool() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        String report;
        do {
            report = Server.networkPoolReport();
            var matcher = POOL_REPORT.matcher(report);
            check(matcher.matches(), "pool report: " + report);
            check(Long.parseLong(matcher.group(1)) <= Long.parseLong(matcher.group(2)), "direct memory cap: " + report);
            long[] accounts = Server.networkAccountRegistryDiagnostics();
            if (matcher.group(3).equals("0") && matcher.group(4).equals("0")
                    && accounts[0] == 0 && accounts[1] == 0 && accounts[2] == 0) return;
            Thread.sleep(5);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("leases or socket identities retained after disconnect: " + report);
    }

    private static int reservePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) { return socket.getLocalPort(); }
    }

    private static Socket connect(int port) throws IOException {
        Socket socket = new Socket("127.0.0.1", port);
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(5000);
        return socket;
    }

    private static byte[] body(int opcode, int value) {
        return ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN).put((byte) opcode).putInt(value).array();
    }

    private static byte[] frame(byte[] body) {
        return ByteBuffer.allocate(body.length + 2).order(ByteOrder.LITTLE_ENDIAN)
                .putShort((short) (body.length + 2)).put(body).array();
    }

    private static byte[] readBody(Socket socket) throws IOException {
        InputStream stream = socket.getInputStream();
        int lo = stream.read(), hi = stream.read();
        if (lo < 0 || hi < 0) throw new EOFException("game frame header");
        int length = (lo | hi << 8) - 2;
        byte[] body = stream.readNBytes(length);
        check(body.length == length, "game frame body delivered completely");
        return body;
    }

    private static void expectEncrypted(Socket socket, SelectorTransportTest.ScalarClient codec, int value) throws IOException {
        check(Arrays.equals(readBody(socket), codec.fromServer(body(0x45, value))), "ordered encrypted game response " + value);
    }

    private static void verifyConcurrentSendFifo(Socket peer, SelectorTransportTest.ScalarClient codec,
            CUserSocket session, CountDownLatch ownerSent) throws Exception {
        long previousCommits = session.javaDirectPacketCommits();
        var release = new CountDownLatch(1);
        try (var producers = Executors.newVirtualThreadPerTaskExecutor()) {
            var sends = new ArrayList<Future<Boolean>>();
            try {
                for (int i = 0; i < 20; i++) {
                    int sequence = 1000 + i;
                    var reserved = new CountDownLatch(1);
                    sends.add(producers.submit(() -> session.tryWritePacket(session.client(), new Reply(sequence, () -> {
                        reserved.countDown();
                        try { check(release.await(10, TimeUnit.SECONDS), "release concurrent encoder"); }
                        catch (InterruptedException interrupted) { throw new AssertionError(interrupted); }
                    }))));
                    check(reserved.await(5, TimeUnit.SECONDS), "each foreign FIFO ticket reserved before encoding");
                }
                peer.getOutputStream().write(frame(codec.toServer(body(ALIVE, 903))));
                check(ownerSent.await(5, TimeUnit.SECONDS), "owning shard published its send behind reserved tickets");
                check(session.javaDirectPacketCommits() == previousCommits, "no earlier unpublished ticket was bypassed");
            } finally { release.countDown(); }
            for (var send : sends) check(send.get(5, TimeUnit.SECONDS), "foreign packet admitted");
        }
        for (int i = 0; i < 20; i++) expectEncrypted(peer, codec, 1000 + i);
        expectEncrypted(peer, codec, 903);
        peer.getOutputStream().write(frame(codec.toServer(body(ALIVE, 904))));
        expectEncrypted(peer, codec, 904);
        long expected = Boolean.getBoolean("rkern.network.javaDirectPacketCommit") ? 1 : 0;
        check(session.javaDirectPacketCommits() - previousCommits == expected,
                "exactly the unblocked owner ticket can commit directly");
    }

    private static final class Reply extends WritablePacket<CIOClient> {
        private final int sequence;
        private final Runnable beforeEncoding;

        private Reply(int sequence, Runnable beforeEncoding) {
            this.sequence = sequence;
            this.beforeEncoding = beforeEncoding;
        }

        @Override protected boolean write(CIOClient client, WriteBuffer writer) {
            beforeEncoding.run();
            writer.writeByte(0x45);
            writer.writeInt(sequence);
            return true;
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
