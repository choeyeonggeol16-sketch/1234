package l1j.server.server.model;

import java.lang.reflect.Field;
import java.util.Map;
import l1j.server.Config;
import l1j.server.server.datatables.MapsTable;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.npc.ai.CDesirePQ;
import l1j.server.server.model.ai.core.WorldCollisionHash;
import l1j.server.server.model.character.TileOccupancy.TileOccupancy;
import l1j.server.server.model.character.TileOccupancy.TileOccupancyNpc;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1V1Map;
import l1j.server.server.model.map.L1WorldMap;
import sun.misc.Unsafe;

/** Run from an isolated working directory without live map files. */
public final class WorldOccupancyGenerationRegression {
    public static void main(String[] args) throws Exception {
        Field allocator = field(Unsafe.class, "theUnsafe");
        MapsTable empty = (MapsTable) ((Unsafe) allocator.get(null)).allocateInstance(MapsTable.class);
        field(MapsTable.class, "_maps").set(empty, Map.of());
        field(MapsTable.class, "_instance").set(null, empty);
        Config.CACHE_MAP_FILES = true;
        L1WorldMap maps = L1WorldMap.getInstance();
        short source = 30081, id = 30082;
        L1Map template = new L1V1Map(source, new byte[16], 4, 4, 0, 0, false, true, true,
                true, true, true, true, true, true, true, true, false);
        maps.addMap(template);
        L1PcInstance actor = new L1PcInstance();
        actor.setId(994001);
        actor.setName("collision-fixture");
        CWorld world = CWorld.getInstance();
        world.storeObject(actor);
        var pc = TileOccupancy.getInstance();
        var npc = TileOccupancyNpc.getInstance();
        try {
            L1Map old = maps.tryCloneMap(source, id);
            claims(old, actor.getId());
            check(maps.removeMap(old), "owned clone removal");
            expectDetached(pc, id);
            expectDetached(npc, id);
            pc.releaseNow(id, 0, 0, actor.getId());
            npc.cancelMove(id, 1, 1, actor.getId());
            check(!pc.tryOccupyNow(id, 0, 0, actor.getId()) && !npc.tryOccupyNow(id, 1, 0, actor.getId()),
                    "unpublished numeric map accepts no new claim");
            expectDetached(pc, id);
            expectDetached(npc, id);
            L1Map fresh = maps.tryCloneMap(source, id);
            check(pc.getReservedBy(id, 0, 1) == 0 && npc.getReservedBy(id, 1, 1) == 0,
                    "reservations from the previous generation cleared");
            claims(fresh, actor.getId());
            // A melee resume slot survives AI ticks until retarget/death. Retire the old
            // actor after a replacement with the same numeric ID reserved the same tile.
            L1MonsterInstance former = (L1MonsterInstance) ((Unsafe) allocator.get(null)).allocateInstance(L1MonsterInstance.class);
            former.setId(actor.getId());
            former._desireResumeAttackTerrain = old;
            former._desireResumeAttackPos = (1 << 16) | 1;
            var clearSlot = CDesirePQ.class.getDeclaredMethod("clearResumeReservation", L1MonsterInstance.class);
            clearSlot.setAccessible(true);
            clearSlot.invoke(null, former);
            check(former._desireResumeAttackTerrain == null && former._desireResumeAttackPos == 0
                    && npc.getReservedBy(id, 1, 1) == actor.getId(),
                    "old AI resume cancellation cleared a replacement reservation or retained terrain");
            WorldCollisionHash.getInstance().retireMap(old);
            pc.releaseNow(old, 0, 0, actor.getId());
            npc.releaseNow(old, 1, 0, actor.getId());
            pc.cancelMove(old, 0, 1, actor.getId());
            npc.cancelMove(old, 1, 1, actor.getId());
            check(pc.getOccupier(id, 0, 0) == actor.getId() && npc.getOccupier(id, 1, 0) == actor.getId()
                    && pc.getReservedBy(id, 0, 1) == actor.getId() && npc.getReservedBy(id, 1, 1) == actor.getId(),
                    "delayed release of retired terrain preserves same-tile, same-ID replacement");
            check(maps.removeMap(fresh), "replacement cleanup");
            // Legacy addMap can replace terrain without a clone allocation.
            claims(template, actor.getId());
            maps.addMap(template.copyMap(source));
            expectDetached(pc, source);
            expectDetached(npc, source);
        } finally {
            maps.removeMap(source);
            world.removeObject(actor);
        }
        System.out.println("PASS PC/NPC occupancy and reservations retire with terrain, stale cleanup and map-ID reuse");
    }

    private static void claims(L1Map terrain, int actor) {
        short id = (short) terrain.getId();
        check(TileOccupancy.getInstance().tryOccupyNow(terrain, 0, 0, actor)
                && TileOccupancyNpc.getInstance().tryOccupyNow(terrain, 1, 0, actor), "body claims");
        check(TileOccupancy.getInstance().canMoveTo(id, 0, 1, actor, 60_000)
                && TileOccupancyNpc.getInstance().canMoveTo(id, 1, 1, actor, 60_000), "reservation claims");
    }

    private static void expectDetached(Object table, int id) throws Exception {
        check(((Object[]) field(table.getClass(), "contexts").get(table))[id] == null, "terrain buffers retained");
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field f = type.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
