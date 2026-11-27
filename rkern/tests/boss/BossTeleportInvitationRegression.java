package l1j.server.server.model.npc.spawn.boss;

import java.io.DataInputStream;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import l1j.server.Config;
import l1j.server.server.Account;
import l1j.server.server.datatables.MapsTable;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.animation.NpcSpeedData;
import l1j.server.server.model.map.L1V1Map;
import l1j.server.server.model.network.core.config.CIOConfig;
import l1j.server.server.model.network.core.connection.CIOBufferPool;
import l1j.server.server.model.network.core.connection.CIOServer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.cs.packet.CS_ActionUi;
import l1j.server.server.model.network.core.cs.packet.CS_Attr;
import l1j.server.server.model.network.safe.CPacketFilter;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.network.utill.CConnectionState;
import l1j.server.server.shared.EPcStatus;
import l1j.server.server.templates.L1Npc;
import sun.misc.Unsafe;

/** Production SEND admission and both typed answer routes against one ready TCP session. */
public final class BossTeleportInvitationRegression {
    private static final int NPC_TEMPLATE = 71000;
    private static final int DISPLAY_EFFECT = 2;

    private static final class Player extends L1PcInstance {
        private int teleports;
        private int targetX;
        private boolean autoHunt;

        @Override public void start_teleport(int x, int y, int map, int heading,
                int effect, boolean show, boolean hunt) {
            teleports++;
            targetX = x;
            autoHunt = hunt;
        }
    }

    public static void main(String[] args) throws Exception {
        final Unsafe unsafe = (Unsafe) field(Unsafe.class, "theUnsafe").get(null);
        final MapsTable maps = (MapsTable) unsafe.allocateInstance(MapsTable.class);
        field(MapsTable.class, "_maps").set(maps, Map.of());
        field(MapsTable.class, "_instance").set(null, maps);
        field(NpcSpeedData.class, "m_npc_speed_data").set(null, new HashMap<>());
        final var map = new L1V1Map(1, new byte[64][64], 96, 96, false, true, true, true,
                true, true, true, true, true, true, true, false);
        final var world = CWorld.getInstance();
        final var scheduler = CIOThreadCallback.getInstance();
        Config.STANDBY_SERVER = false;
        Config.MAX_ONLINE_USERS = 8;
        Config.YN_pclevel = 1;
        CPacketFilter.getInstance().load();
        final var address = new InetSocketAddress("127.0.0.1", 0);
        final var config = new CIOConfig(address);
        config.packetExecutorShardCount = 1;
        config.usePreLoginTimeout = false;
        CIOConfig.NETWORK_CLIENT_PERMISSION = 8;
        final int owners = scheduler.bindNetworkIngress(config);
        final var pool = new CIOBufferPool(8, 16, 4, 4, owners);

        try (var server = new CIOServer(address, 8, owners, 8, pool, scheduler, config);
                var peer = new Socket()) {
            peer.connect(server.localAddress());
            peer.setSoTimeout(3000);
            final var wire = new DataInputStream(peer.getInputStream());
            final int greetingLength = wire.readUnsignedByte() | wire.readUnsignedByte() << 8;
            require(greetingLength > 2 && wire.readNBytes(greetingLength - 2).length == greetingLength - 2,
                    "live socket greeting");
            final CUserSocket socket;
            synchronized (server) {
                final Set<?> sockets = (Set<?>) field(CIOServer.class, "sockets").get(server);
                socket = (CUserSocket) sockets.iterator().next();
            }
            final var pc = new Player();
            pc.setId(190006001);
            pc.setName("BossInviteFixture");
            pc.setMap(map);
            pc.setX(102);
            pc.setY(102);
            pc.setLevel(2);
            pc.setAutoHunt(true);
            pc.set_instance_status(EPcStatus.WORLD);
            final var account = new Account();
            account.setAccountId(190006001);
            account.setName("bossinvitefixture");
            socket.setAccount(account);
            socket.setActiveChar(pc);
            pc.setNetConnection(socket);
            socket.setConnectionState(CConnectionState.IN_GAME);
            socket.publishGameSessionReady(pc);
            require(socket.ownsReadyGameSession(), "ready boss invitation session");
            world.storeObject(pc);

            final CNpcSpawnDefine a = define(108);
            final CNpcSpawnDefine b = define(136);
            install(a, b);
            final var first = boss(190006002, map, 149);
            final var second = boss(190006003, map, 151);
            final var replacement = boss(first.getId(), map, 149);
            try {
                world.storeObject(first);
                world.storeObject(second);
                BossTeleportInvites.announce(a, first, DISPLAY_EFFECT);
                final var active = pc.bossTeleportInvitation();
                require(active != null, "first question admitted");
                BossTeleportInvites.announce(b, second, DISPLAY_EFFECT);
                require(pc.bossTeleportInvitation() == active, "overlap preserves first live invite");
                CS_ActionUi.handle(socket, 143, 0, 0, 0, 0, 0, null, null, null);
                require(pc.teleports == 1 && pc.autoHunt && Math.abs(pc.targetX - a.getX()) <= 4,
                        "ActionUI uses the offered row's configured anchor and hunt flag");
                yes(socket);
                require(pc.teleports == 1, "consumed invitation is shared by both handlers");

                BossTeleportInvites.announce(b, second, DISPLAY_EFFECT);
                yes(socket);
                require(pc.teleports == 2 && !pc.autoHunt && Math.abs(pc.targetX - b.getX()) <= 4,
                        "YN uses its exact row and legacy non-hunt behavior");

                BossTeleportInvites.announce(a, first, DISPLAY_EFFECT);
                world.removeObject(first);
                world.storeObject(replacement);
                yes(socket);
                require(pc.teleports == 2, "same numeric object ID cannot replace the actual spawn");
                no(socket);

                BossTeleportInvites.announce(b, second, DISPLAY_EFFECT);
                final CNpcSpawnDefine reloaded = define(b.getX());
                install(a, reloaded);
                yes(socket);
                require(pc.teleports == 2, "catalog reload retired the old invitation");
                no(socket);

                BossTeleportInvites.announce(reloaded, second, DISPLAY_EFFECT);
                pc.nextSessionStamp();
                yes(socket);
                require(pc.teleports == 2 && pc.bossTeleportInvitation() == null,
                        "session rotation clears only the retired slot");

                BossTeleportInvites.announce(reloaded, second, DISPLAY_EFFECT);
                require(pc.bossTeleportInvitation() != null, "invite admitted before expiry clock");
                Thread.sleep(10_050L);
                yes(socket);
                require(pc.teleports == 2 && pc.bossTeleportInvitation() == null, "expired question retired");
                BossTeleportInvites.announce(a, replacement, DISPLAY_EFFECT);
                yes(socket);
                require(pc.teleports == 3 && Math.abs(pc.targetX - a.getX()) <= 4,
                        "old expiry cannot clear the newly admitted invite");
            } finally {
                socket.setActiveChar(null);
                pc.setNetConnection(null);
                socket.setAccount(null);
                world.removeObject(pc);
                world.removeObject(replacement);
                world.removeObject(second);
                world.removeObject(first);
            }
        } finally {
            scheduler.shutdown();
            require(scheduler.awaitTermination(5, TimeUnit.SECONDS), "CIO stopped");
        }
        System.out.println("PASS Boss YN TCP admission, both handlers, exact instance/row, overlap, reload, session and expiry");
    }

    private static L1MonsterInstance boss(int id, L1V1Map map, int x) {
        final var template = new L1Npc();
        template.set_npcId(NPC_TEMPLATE);
        template.set_name("fixture boss");
        template.set_hp(100);
        final var npc = new L1MonsterInstance(template);
        npc.setId(id);
        npc.setMap(map);
        npc.setX(x);
        npc.setY(120);
        return npc;
    }

    private static CNpcSpawnDefine define(int x) {
        final var row = new CNpcSpawnDefine();
        row.setNpcId(NPC_TEMPLATE);
        row.setX(x);
        row.setY(120);
        row.setMap(1);
        row.setYnMessage("Boss invite fixture");
        return row;
    }

    private static void install(CNpcSpawnDefine a, CNpcSpawnDefine b) {
        CBossNpcMakerDB.install(new CBossNpcMakerDB.Catalog(List.of(a, b),
                Map.of(CBossSpawnKey.of(a), a, CBossSpawnKey.of(b), b), Set.of(NPC_TEMPLATE)));
    }

    private static void yes(CUserSocket socket) {
        CS_Attr.handle(socket, 622, CS_Attr.MSGCODE_622_BOSS, CS_Attr.MSGCODE_YES, null);
    }

    private static void no(CUserSocket socket) {
        CS_Attr.handle(socket, 622, CS_Attr.MSGCODE_622_BOSS, CS_Attr.MSGCODE_NO, null);
    }

    private static Field field(Class<?> owner, String name) throws ReflectiveOperationException {
        final Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
