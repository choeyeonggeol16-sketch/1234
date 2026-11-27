package l1j.server.server.model.npc.spawn.boss;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import l1j.server.Config;
import l1j.server.server.datatables.MapsTable;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.character.animation.NpcSpeedData;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.map.L1V1Map;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.npc.ai.CNPCServer;
import l1j.server.server.templates.L1Npc;
import l1j.server.server.types.Point;
import sun.misc.Unsafe;

/** Run in a fresh JVM. Only the DB-backed map catalog and spawn definition are supplied locally. */
public final class BossSpawnPublicationRegression {
    private static final class TestMap extends L1V1Map {
        int allowProbe;
        int probes;

        TestMap() {
            super(1, new byte[16][16], 96, 96, false, false, false, false,
                    false, false, false, false, false, true, true, false);
        }

        @Override
        public boolean isPassable(Point point) { return ++probes == allowProbe; }
    }

    public static void main(String[] args) throws Exception {
        // Supply an empty DB-backed catalog; retain the real world and all registration indices.
        Field unsafe = Unsafe.class.getDeclaredField("theUnsafe");
        unsafe.setAccessible(true);
        MapsTable maps = (MapsTable) ((Unsafe) unsafe.get(null)).allocateInstance(MapsTable.class);
        Field catalog = MapsTable.class.getDeclaredField("_maps");
        catalog.setAccessible(true);
        catalog.set(maps, Map.of());
        Field singleton = MapsTable.class.getDeclaredField("_instance");
        singleton.setAccessible(true);
        singleton.set(null, maps);
        Config.STANDBY_SERVER = true;
        CWorld world = CWorld.getInstance();
        CNPCServer engine = CNPCServer.getInstance();
        try {
            TestMap room = new TestMap();
            // World registration installs the object ID before applying the speed table.
            // This injected failure exercises rollback of a partially registered actor.
            Field speeds = NpcSpeedData.class.getDeclaredField("m_npc_speed_data");
            speeds.setAccessible(true);
            speeds.set(null, null);
            L1MonsterInstance partial = monster(100000, room);
            check(CBossNpcMaker.enterBoss(partial, 100, 100, 0, 0) == null && partial.isDestroyed()
                    && world.findObject(100000) == null, "partial world entry rolled back");
            speeds.set(null, new HashMap<>());
            L1MonsterInstance npc = monster(100001, room);
            room.allowProbe = 50;
            check(CBossNpcMaker.placeBoss(npc, 100, 100, 1) && room.probes == 50
                    && npc.getX() == 100 && npc.getY() == 100, "valid last random candidate");
            room.probes = 0;
            room.allowProbe = 51;
            check(CBossNpcMaker.placeBoss(npc, 100, 100, 1) && room.probes == 51
                    && (npc.getX() != 100 || npc.getY() != 100), "legacy adjacent fallback");
            room.probes = 0;
            room.allowProbe = 0;
            check(!CBossNpcMaker.placeBoss(npc, 100, 100, 1), "invalid fallback rejected");
            check(CBossNpcMaker.enterBoss(npc, 100, 100, 0, 3_600_000) == npc, "spawn publication");
            check(world.findObject(npc.getId()) == npc && npc.npcDeleteDeadlineMs() > System.currentTimeMillis(),
                    "registered boss has mandatory lifetime");

            CNpcSpawnDefine brokenNotice = new CNpcSpawnDefine() {
                @Override public String getMentMessage() { throw new IllegalStateException("injected notice failure"); }
            };
            CBossNpcMaker.announceBoss(brokenNotice, npc, true, false, 0);
            check(world.findObject(npc.getId()) == npc && npc.npcDeleteDeadlineMs() != 0,
                    "optional announcement leaves committed identity and lifetime");
            npc.deleteMe();

            L1MonsterInstance invalid = monster(100002, room);
            check(CBossNpcMaker.enterBoss(invalid, 12, 12, 0, 1_000) == null && invalid.isDestroyed()
                    && world.findObject(100002) == null, "out-of-map placement cleans up unregistered actor");

            engine.shutdown();
            L1MonsterInstance rejected = monster(100003, room);
            check(CBossNpcMaker.enterBoss(rejected, 100, 100, 0, 1_000) == null && rejected.isDestroyed()
                    && world.findObject(100003) == null, "rejected lifetime rolls back world registration");
            System.out.println("PASS boss placement, publication, announcement failure and refused lifetime rollback");
        } finally {
            engine.shutdown();
            CIOThreadCallback.getInstance().shutdown();
        }
    }

    private static L1MonsterInstance monster(int id, TestMap room) {
        L1Npc definition = new L1Npc();
        definition.set_npcId(100);
        definition.set_name("fixture");
        definition.set_nameid("fixture");
        definition.set_hp(100);
        L1MonsterInstance monster = new L1MonsterInstance(definition);
        monster.setId(id);
        monster.setMap(room);
        return monster;
    }

    private static void check(boolean result, String message) {
        if (!result) throw new AssertionError(message);
    }
}
