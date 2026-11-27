import java.io.DataInputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import l1j.server.Config;
import l1j.server.TextMapReader;
import l1j.server.server.Account;
import l1j.server.server.Opcodes;
import l1j.server.server.datatables.MapsTable;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.Instance.L1ClanJoinInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1V1Map;
import l1j.server.server.model.map.L1WorldMap;
import l1j.server.server.model.network.core.broadcast.CSharedBroadcast;
import l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter;
import l1j.server.server.model.network.core.config.CIOConfig;
import l1j.server.server.model.network.core.connection.CIOBufferPool;
import l1j.server.server.model.network.core.connection.CIOServer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.session.CSessionLifecycle;
import l1j.server.server.model.network.packet.CBytesPacket;
import l1j.server.server.model.network.packet.UserPacketCallbackMap;
import l1j.server.server.model.network.safe.CPacketFilter;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.network.utill.CConnectionState;
import sun.misc.Unsafe;

/** Compile with the reference codec in SelectorTransportTest, as for BroadcastDeliveryRegression. */
public final class MapBroadcastLoopbackRegression {
    private static final int ALIVE = Opcodes.C_ALIVE & 255;
    private static final int ACK = 0x45;
    private static final int TAG = 0x43;
    private static final long MAGIC = 0x4e59_2c7a_8bc1_4d30L;
    private static final int MAX_PEERS = 12;
    private static final int RADIUS = 4;

    private final ConcurrentHashMap<Integer, CUserSocket> accepted = new ConcurrentHashMap<>();
    private final ArrayList<Peer> peers = new ArrayList<>();
    private final ArrayList<L1PcInstance> actors = new ArrayList<>();
    private final CWorld world;
    private int broadcasts, deliveries, assertions;

    private MapBroadcastLoopbackRegression() {
        world = CWorld.getInstance();
    }

    public static void main(String[] args) throws Exception {
        installMapDefinitions();
        Config.MAX_ONLINE_USERS = MAX_PEERS;
        CIOConfig.NETWORK_CLIENT_PERMISSION = MAX_PEERS;
        CPacketFilter.getInstance().load();
        final var owners = CIOThreadCallback.getInstance();
        final var address = new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0);
        final var config = new CIOConfig(address);
        config.packetExecutorShardCount = 2;
        config.usePreLoginTimeout = false;
        final int shards = owners.bindNetworkIngress(config);
        final var pool = new CIOBufferPool(MAX_PEERS, MAX_PEERS * 6, MAX_PEERS, MAX_PEERS, shards);
        final var fixture = new MapBroadcastLoopbackRegression();
        final var previousAlive = UserPacketCallbackMap.gPacketTableFromClient[ALIVE];
        UserPacketCallbackMap.gPacketTableFromClient[ALIVE] = (request, client) -> {
            final int seat = request.readInt();
            final int marker = request.readInt();
            final CUserSocket previous = fixture.accepted.putIfAbsent(seat, client);
            check(previous == null || previous == client, "probe arrived on another fixture session");
            return client.tryWritePacket(client, new CBytesPacket(body(ACK, marker)));
        };
        try (var server = new CIOServer(address, MAX_PEERS, shards, MAX_PEERS, pool, owners, config)) {
            try {
                fixture.scenarios(server);
            } finally {
                fixture.close(server);
            }
            check(pool.optionalBroadcastRefusals() == 0 && pool.sharedSourceRefusals() == 0,
                    "unexpected socket or shared-source refusal");
        } finally {
            UserPacketCallbackMap.gPacketTableFromClient[ALIVE] = previousAlive;
            owners.shutdown();
            check(owners.awaitTermination(5, TimeUnit.SECONDS), "CIO owner shutdown did not drain");
            CSessionLifecycle.stopAccepting();
            check(CSessionLifecycle.awaitDrained(5, TimeUnit.SECONDS), "session releaser did not drain");
        }
        check(ManagementFactory.getThreadMXBean().findDeadlockedThreads() == null, "JVM deadlock");
        System.out.printf("PASS map broadcast: %d broadcasts, %d encrypted deliveries, %d recipient/exclusion checks; registry and leases drained%n",
                fixture.broadcasts, fixture.deliveries, fixture.assertions);
    }

    private void scenarios(CIOServer server) throws Exception {
        final L1WorldMap maps = L1WorldMap.getInstance();
        final L1Map field = fieldMap(441);
        final L1Map template = fieldMap(442);
        maps.addMap(field);
        maps.addMap(template);
        final L1Map firstRoom = maps.tryCloneMap(template.getId(), 443);
        check(firstRoom != null, "first clone");

        final Peer origin = join(server, firstRoom, 101, true);
        final Peer near = join(server, firstRoom, 103, true);
        final Peer edge = join(server, firstRoom, 105, true);
        final Peer far = join(server, firstRoom, 106, true);
        final Peer otherMap = join(server, field, 103, true);
        final Peer registeredOnly = join(server, firstRoom, 103, false);

        recruitmentSign(origin, near, edge, far, otherMap, registeredOnly);
        nearby("finite radius includes its edge, excludes source/other/far/unentered", origin.pc, RADIUS, near, edge);
        nearby("fast screen selection", origin.pc, -1, near, edge, far);
        final int sound = ++broadcasts;
        final int effectId = 9063;
        SC_SkillSound.broadcastFast(origin.pc, origin.pc.getId(), effectId);
        final byte[] effect = ByteBuffer.allocate(13).order(ByteOrder.LITTLE_ENDIAN)
                .put((byte) Opcodes.S_EFFECT).putInt(origin.pc.getId()).putShort((short) effectId)
                .putShort((short) 0).putInt(0).array();
        verify("gameplay SC_SkillSound on the real encrypted stream", sound, List.of(near, edge, far), effect);

        move(near.pc, 103, field);
        nearby("same coordinates across distinct maps", origin.pc, RADIUS, edge);
        move(far.pc, 104, firstRoom);
        nearby("cross-cell relocation", origin.pc, RADIUS, edge, far);
        final int finiteRace = ++broadcasts;
        final var finiteEncodes = new AtomicInteger();
        CSharedBroadcast.sendWrittenBestEffort(origin.pc, writer(finiteRace, finiteEncodes,
                () -> move(edge.pc, 105, field)), false, RADIUS, null);
        verify("recipient leaves while the finite frame is frozen", finiteRace, List.of(far));
        check(finiteEncodes.get() == 1, "finite relocation frame was not encoded exactly once");
        move(edge.pc, 105, firstRoom);

        world.removeVisibleObject(edge.pc);
        check(world.findObject(edge.pc.getId()) == edge.pc, "visible leave keeps canonical registration");
        nearby("left visibility while TCP still open", origin.pc, RADIUS, far);

        final Peer former = far;
        final L1PcInstance resumed = far.pc;
        final Peer replacement = connect(server);
        final int rebindRace = ++broadcasts;
        final var rebindEncodes = new AtomicInteger();
        CSharedBroadcast.sendWrittenBestEffort(origin.pc, writer(rebindRace, rebindEncodes, () -> {
            detach(former);
            attach(replacement, resumed);
        }), false, RADIUS, null);
        verify("neither session receives a frame already being encoded at rebind", rebindRace, List.of());
        check(rebindEncodes.get() == 1, "rebind test frame was not encoded exactly once");
        nearby("only the newly bound eligible session", origin.pc, RADIUS, replacement);

        // Empty the old instance before retiring it. Keep an ordinary live socket on
        // the same coordinates in another map to detect accidental map-ID fanout.
        move(origin.pc, 101, field);
        move(resumed, 104, field);
        check(maps.removeMap(firstRoom), "first clone removed");
        final L1Map secondRoom = maps.tryCloneMap(template.getId(), firstRoom.getId());
        check(secondRoom != null && secondRoom != firstRoom, "map ID reused with distinct terrain");
        check(!world.relocateVisibleObject(near.pc, 103, 101, firstRoom), "stale terrain accepted a move");
        move(origin.pc, 101, secondRoom);
        move(resumed, 104, secondRoom);
        nearby("recycled map does not see old registered-only occupants or other maps",
                origin.pc, RADIUS, replacement);

        origin.pc.set_current_combat_team_id(7);
        resumed.set_current_combat_team_id(7);
        final int teamRace = ++broadcasts;
        final var teamEncodes = new AtomicInteger();
        CSharedBroadcast.sendWrittenMapTeamBestEffort(secondRoom, 7, writer(teamRace, teamEncodes,
                () -> move(origin.pc, 101, field)));
        verify("selected team member left during frame encoding", teamRace, List.of(replacement));
        check(teamEncodes.get() == 1, "map team frame was not encoded exactly once");
        move(origin.pc, 101, secondRoom);

        final int stale = ++broadcasts;
        final var staleEncodes = new AtomicInteger();
        CSharedBroadcast.sendWrittenMapBestEffort(firstRoom, writer(stale, staleEncodes, null));
        verify("retired map reference cannot broadcast into its replacement", stale, List.of());
        check(staleEncodes.get() == 0, "old terrain unexpectedly encoded a frame");

        detach(replacement);
        replacement.socket.close();
        await("physical replacement close", replacement.connection::isCloseComplete);
        nearby("closed client cannot take a frame or stall a healthy peer", origin.pc, RADIUS);
        move(otherMap.pc, 103, secondRoom);
        nearby("healthy delivery after disconnect", origin.pc, RADIUS, otherMap);
        clanBroadcast(server, origin, otherMap);

    }

    private void recruitmentSign(Peer owner, Peer near, Peer edge, Peer far, Peer elsewhere, Peer unentered) throws Exception {
        int oldRange = Config.PC_RECOGNIZE_RANGE;
        Config.PC_RECOGNIZE_RANGE = 8;
        try {
            owner.pc.setClanid(710);
            owner.pc.setTitle("Recruit");
            var factory = L1ClanJoinInstance.class.getDeclaredMethod("newInstance", L1PcInstance.class, int.class, int.class);
            factory.setAccessible(true);
            var sign = (L1ClanJoinInstance) factory.invoke(null, owner.pc, 19001, 19002);
            for (var peer : List.of(owner, near, edge, far, elsewhere, unentered)) peer.pc.inSight(sign);
            byte[] data = sign.serialize();
            byte[] appearance = ByteBuffer.allocate(3 + data.length).order(ByteOrder.LITTLE_ENDIAN)
                    .put((byte) Opcodes.S_EXTENDED_PROTOBUF).putShort((short) 0x77).put(data).array();
            verify("sign appears once to owner and nearby nonmembers", ++broadcasts, List.of(owner, near, edge, far), appearance);
            byte[] remove = ByteBuffer.allocate(7).order(ByteOrder.LITTLE_ENDIAN)
                    .put((byte) Opcodes.S_REMOVE_OBJECT).putInt(sign.getId()).putShort((short) 0).array();
            near.pc.outOfSight(sign);
            verify("departed viewer forgets sign", ++broadcasts, List.of(near), remove);
            sign.dispose();
            sign.dispose();
            far.pc.inSight(sign);
            verify("retirement retracts only remaining viewers", ++broadcasts, List.of(owner, edge, far), remove);
            check(!owner.pc.knownsObject(sign) && !far.pc.knownsObject(sign) && world.findObject(sign.getId()) == null,
                    "retired recruitment sign remained visible or registered");
        } finally { Config.PC_RECOGNIZE_RANGE = oldRange; }
    }

    private void clanBroadcast(CIOServer server, Peer old, Peer teammate) throws Exception {
        var clan = new L1Clan();
        clan.setClanId(731);
        for (Peer peer : List.of(old, teammate)) {
            peer.pc.setClanid(731);
            peer.pc.setOnlineStatus(1);
            clan.addLoadedClanMember(peer.pc.getName(), 1, 1, "", peer.pc.getId(), 0);
        }
        check(clan.GetMemberCounts() == (2L << 32 | 2), "online roster counts");
        Peer next = connect(server);
        L1PcInstance current = new L1PcInstance();
        current.setId(old.pc.getId());
        current.setName(old.pc.getName());
        current.getLocation().set(old.pc.getX(), old.pc.getY(), old.pc.getMap());
        current.setClanid(731);
        current.setOnlineStatus(1);
        actors.add(current);
        attach(next, current);
        world.storeObject(current);
        clan.releaseClanMemberReference(old.pc);
        int mark = ++broadcasts;
        CSharedBroadcast.sendWrittenClan(clan, writer(mark, new AtomicInteger(), null));
        verify("clan broadcast selects the new socket before roster cache refresh", mark, List.of(next, teammate));
        mark = ++broadcasts;
        CSharedBroadcast.sendWrittenClanBestEffort(clan, writer(mark, new AtomicInteger(), () -> current.setClanid(0)));
        verify("member leaves during encoding", mark, List.of(teammate));
    }

    private void move(L1PcInstance pc, int x, L1Map map) {
        check(world.relocateVisibleObject(pc, x, 101, map), "world relocation was not committed");
    }

    private void nearby(String label, L1PcInstance origin, int radius, Peer... recipients) throws IOException {
        final int mark = ++broadcasts;
        final AtomicInteger encodes = new AtomicInteger();
        CSharedBroadcast.sendWrittenBestEffort(origin, writer(mark, encodes, null), radius < 0, radius, null);
        verify(label, mark, List.of(recipients));
        check(encodes.get() == (recipients.length == 0 ? 0 : 1), label + " encoded once when eligible");
    }

    private static NativeWireWriter writer(int mark, AtomicInteger encodes, Runnable beforeWrite) {
        return out -> {
            encodes.incrementAndGet();
            if (beforeWrite != null) beforeWrite.run();
            out.put((byte) TAG).putLong(MAGIC).putInt(mark);
        };
    }

    private void verify(String label, int mark, List<Peer> recipients) throws IOException {
        verify(label, mark, recipients, null);
    }

    private void verify(String label, int mark, List<Peer> recipients, byte[] gameplayFrame) throws IOException {
        // Probe replies share each socket's ordered encrypted send stream with
        // broadcasts. They bound both positive and negative assertions without sleeps.
        for (Peer peer : peers) if (!peer.socket.isClosed()) peer.sendProbe(mark);
        for (Peer peer : peers) {
            if (peer.socket.isClosed()) continue;
            final int count = peer.readThroughProbe(mark, gameplayFrame);
            final int expected = recipients.contains(peer) ? 1 : 0;
            check(count == expected, label + " seat=" + peer.id + " expected " + expected + ", got " + count);
            deliveries += count;
            assertions++;
        }
    }

    private Peer join(CIOServer server, L1Map map, int x, boolean entered) throws IOException {
        final Peer peer = connect(server);
        final L1PcInstance pc = new L1PcInstance();
        pc.setId(18_000 + peer.id);
        pc.setName("MapProbe" + peer.id);
        pc.getLocation().set(x, 101, map);
        actors.add(pc);
        attach(peer, pc);
        if (entered) world.storeAndEnterWorld(pc);
        else world.storeObject(pc);
        return peer;
    }

    private Peer connect(CIOServer server) throws IOException {
        final Peer peer = new Peer(server.localAddress(), peers.size() + 1);
        peers.add(peer);
        peer.sendProbe(0);
        check(peer.readThroughProbe(0, null) == 0, "unexpected frame during initial heartbeat");
        peer.connection = accepted.get(peer.id);
        check(peer.connection != null, "encrypted probe did not establish server socket identity");
        return peer;
    }

    private static void attach(Peer peer, L1PcInstance pc) {
        final String login = "map_probe_" + peer.id;
        final Account account = new Account();
        account.setName(login);
        account.setAccountId(27_000 + peer.id);
        pc.setAccountName(login);
        peer.connection.setAccount(account);
        peer.connection.setActiveChar(pc);
        pc.setNetConnection(peer.connection);
        peer.connection.setConnectionState(CConnectionState.IN_GAME);
        peer.connection.publishGameSessionReady(pc);
        check(peer.connection.ownsReadyGameSession(), "bidirectional game session was not published");
        peer.pc = pc;
    }

    private static void detach(Peer peer) {
        if (peer.connection == null || peer.pc == null) return;
        if (peer.pc.getNetConnection() == peer.connection) peer.pc.setNetConnection(null);
        peer.connection.setActiveChar(null);
        peer.connection.setConnectionState(CConnectionState.AUTHENTICATED);
        peer.pc = null;
    }

    private void close(CIOServer server) throws Exception {
        for (Peer peer : peers) detach(peer);
        for (L1PcInstance pc : actors) world.leaveAndRemoveObject(pc);
        for (Peer peer : peers) peer.socket.close();
        await("accepted socket registry not drained", () -> server.accountRegistryDiagnostics()[0] == 0);
        await("SEND/RECV/source leases not drained", () -> Arrays.stream(server.poolSnapshot()).allMatch(row -> row[2] == 0));
        check(world.getAllPlayers().isEmpty() && world.objectCount() == 0 && world.ExecutableBacklog() == 0,
                "world objects or jobs retained");
    }

    private static L1Map fieldMap(int id) {
        return new L1V1Map(id, new byte[64][64], 80, 80,
                false, true, true, true, true, true, true, true, true, true, true, false);
    }

    private static void installMapDefinitions() throws Exception {
        final Unsafe unsafe = (Unsafe) field(Unsafe.class, "theUnsafe").get(null);
        final MapsTable metadata = (MapsTable) unsafe.allocateInstance(MapsTable.class);
        field(MapsTable.class, "_maps").set(metadata, Map.of());
        field(MapsTable.class, "_instance").set(null, metadata);
        Arrays.fill((int[][]) field(TextMapReader.class, "MAP_INFO").get(null), null);
        Config.CACHE_MAP_FILES = false;
    }

    private static Field field(Class<?> type, String name) throws ReflectiveOperationException {
        final Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static byte[] body(int opcode, int value) {
        return ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN).put((byte) opcode).putInt(value).array();
    }

    private static void await(String label, BooleanSupplier condition) throws InterruptedException {
        final long limit = System.nanoTime() + TimeUnit.SECONDS.toNanos(6);
        while (!condition.getAsBoolean() && System.nanoTime() < limit) Thread.sleep(1);
        check(condition.getAsBoolean(), label);
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static final class Peer {
        final int id;
        final Socket socket = new Socket();
        final DataInputStream input;
        final SelectorTransportTest.ScalarClient cipher = new SelectorTransportTest.ScalarClient(Opcodes.getSeed());
        CUserSocket connection;
        L1PcInstance pc;

        Peer(InetSocketAddress address, int id) throws IOException {
            this.id = id;
            try {
                socket.setTcpNoDelay(true);
                socket.setSoTimeout(4000);
                socket.connect(address, 3000);
                input = new DataInputStream(socket.getInputStream());
                check(Arrays.equals(read(), Opcodes.S_KEYBYTES), "TCP handshake mismatch");
            } catch (IOException | Error failure) {
                socket.close();
                throw failure;
            }
        }

        void sendProbe(int mark) throws IOException {
            final byte[] encrypted = cipher.toServer(ByteBuffer.allocate(9).order(ByteOrder.LITTLE_ENDIAN)
                    .put((byte) ALIVE).putInt(id).putInt(probeMarker(mark)).array());
            socket.getOutputStream().write(ByteBuffer.allocate(encrypted.length + 2).order(ByteOrder.LITTLE_ENDIAN)
                    .putShort((short) (encrypted.length + 2)).put(encrypted).array());
        }

        int readThroughProbe(int mark, byte[] gameplayFrame) throws IOException {
            final byte[] ack = body(ACK, probeMarker(mark));
            int count = 0;
            for (int frame = 0; frame < 256; frame++) {
                final byte[] plaintext = cipher.fromServer(read());
                if (Arrays.equals(ack, plaintext)) return count;
                if (Arrays.equals(gameplayFrame, plaintext)) {
                    count++;
                    continue;
                }
                if (plaintext.length != 13 || (plaintext[0] & 255) != TAG) continue;
                final var message = ByteBuffer.wrap(plaintext).order(ByteOrder.LITTLE_ENDIAN);
                message.get();
                if (message.getLong() != MAGIC) continue;
                check(message.getInt() == mark, "frozen frame crossed a heartbeat boundary or changed wire data");
                count++;
            }
            throw new AssertionError("probe reply not found among normal sight traffic");
        }

        private byte[] read() throws IOException {
            final int size = (input.readUnsignedByte() | input.readUnsignedByte() << 8) - 2;
            check(size > 0, "invalid framed payload");
            final byte[] payload = new byte[size];
            input.readFully(payload);
            return payload;
        }

        private int probeMarker(int mark) { return 0x3ca0_0000 | id << 8 | mark; }
    }
}
