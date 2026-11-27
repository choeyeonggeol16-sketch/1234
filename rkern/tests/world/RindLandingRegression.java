package l1j.server.server.model.world.instance.raid.boss.skill;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import l1j.server.Config;
import l1j.server.TextMapReader;
import l1j.server.server.datatables.MapsTable;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.character.TileOccupancy.TileOccupancy;
import l1j.server.server.model.character.TileOccupancy.TileOccupancyNpc;
import l1j.server.server.model.character.animation.NpcSpeedData;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1V1Map;
import l1j.server.server.model.map.L1WorldMap;
import l1j.server.server.templates.L1Npc;
import sun.misc.Unsafe;

/** Run in a fresh JVM without a game database. */
public final class RindLandingRegression {
    public static void main(String[] args) throws Exception {
        final Unsafe unsafe = (Unsafe) field(Unsafe.class, "theUnsafe").get(null);
        final MapsTable metadata = (MapsTable) unsafe.allocateInstance(MapsTable.class);
        field(MapsTable.class, "_maps").set(metadata, Map.of());
        field(MapsTable.class, "_instance").set(null, metadata);
        Arrays.fill((int[][]) field(TextMapReader.class, "MAP_INFO").get(null), null);
        field(NpcSpeedData.class, "m_npc_speed_data").set(null, new HashMap<>());
        Config.CACHE_MAP_FILES = false;
        final short id = 2700;
        final L1Map map = new L1V1Map(id, new byte[128][128], 80, 80,
                false, true, true, true, true, true, true, true, true, true, true, false);
        final CWorld world = CWorld.getInstance();
        L1WorldMap.getInstance().addMap(map);
        final L1Npc template = new L1Npc();
        template.set_npcId(812004);
        template.set_name("Rind fixture");
        template.set_nameid("Rind fixture");
        template.set_hp(100);
        final L1MonsterInstance boss = new L1MonsterInstance(template);
        final L1PcInstance player = new L1PcInstance();
        player.setName("RindTarget");
        enter(world, boss, map, 30000001, 100);
        enter(world, player, map, 30000002, 136);
        final TileOccupancyNpc npcs = TileOccupancyNpc.getInstance();
        final TileOccupancy pcs = TileOccupancy.getInstance();
        try {
            check(npcs.getOccupier(id, 100, 100) == boss.getId(), "original NPC claim");
            check(pcs.getOccupier(id, 136, 100) == player.getId(), "original PC claim");
            check(!npcs.tryOccupyNow(id, 136, 100, boss.getId()), "ordinary NPC cannot enter PC tile");
            boss.setHiddenStatus(L1MonsterInstance.HIDDEN_STATUS_FLY);
            check(npcs.getOccupier(id, 100, 100) == 0, "airborne releases floor tile");
            check(RindFly.land(boss, List.of()) && npcs.getOccupier(id, 100, 100) == boss.getId(),
                    "no target: recover original tile");
            boss.setHiddenStatus(L1MonsterInstance.HIDDEN_STATUS_FLY);
            check(RindFly.land(boss, List.of(player)), "landed");
            check(boss.getX() == 136 && boss.getMap() == map && boss.getHiddenStatus() == 0, "landing location");
            check(npcs.getOccupier(id, 100, 100) == 0 && npcs.getOccupier(id, 136, 100) == boss.getId(),
                    "NPC collision moved with boss");
            check(pcs.getOccupier(id, 136, 100) == player.getId(), "selected PC retains tile");
            check(world.getVisibleObjects(player, 0).contains(boss), "new cell includes boss");
            check(RindFly.land(boss, List.of(player)) && npcs.getOccupier(id, 136, 100) == boss.getId(),
                    "same tile landing keeps claim");
            boss.setHiddenStatus(L1MonsterInstance.HIDDEN_STATUS_FLY);
            check(npcs.getOccupier(id, 136, 100) == 0 && !RindFly.land(boss, List.of())
                    && boss.getHiddenStatus() == L1MonsterInstance.HIDDEN_STATUS_FLY
                    && boss.getX() == 136 && pcs.getOccupier(id, 136, 100) == player.getId(),
                    "blocked original tile: no partial landing");
            pcs.releaseNow(id, 136, 100, player.getId());
            check(!npcs.claimLandingTile(boss, player, map, 136, 100), "departed player refused");
            world.leaveAndRemoveObject(boss);
            check(!RindFly.land(boss, List.of(player)) && npcs.getOccupier(id, 136, 100) == 0,
                    "retired boss does not reappear");
        } finally {
            world.leaveAndRemoveObject(boss);
            world.leaveAndRemoveObject(player);
            L1WorldMap.getInstance().removeMap(id);
        }
        System.out.println("PASS Rind landing exact target, collision, cell publication and retirement");
    }

    private static void enter(CWorld world, L1Object actor, L1Map map, int id, int x) {
        actor.setId(id);
        actor.setMap(map);
        actor.setX(x);
        actor.setY(100);
        world.storeAndEnterWorld(actor);
    }

    private static Field field(Class<?> type, String name) throws ReflectiveOperationException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void check(boolean passed, String message) {
        if (!passed) throw new AssertionError(message);
    }
}
