package l1j.server.server.model.world.cleanup;

import l1j.server.server.task.TaskLane;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.inventory.CInventory;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.item.treasure.L1TreasureChest;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.world.instance.shared.L1LiftGateInstance;
import l1j.server.server.shared.L1Type;

public final class MapCleaner implements Runnable {
    public static void cleanup(L1Map map, MapCleanerFilter filter) {
        final MapCleaner cleaner = new MapCleaner(map, filter);
        if (TaskLane.WORLD.tryExecute(cleaner) || TaskLane.INSTANCE.tryExecute(cleaner)) return;
        if (!TaskLane.WORLD.isClosed()) TaskLane.WORLD.scheduleRequired(cleaner, 1L);
    }

    private final L1Map map;
    private final MapCleanerFilter filter;

    private MapCleaner(L1Map map, MapCleanerFilter filter) {
        this.map = map;
        this.filter = filter;
    }

    @Override public void run() {
        final var visible = L1World.getInstance().getVisibleObjects(map.getId());
        if (visible == null) return;
        for (L1Object object : visible.values()) {
            // A copied map ID can already belong to a different room.
            if (object == null || object.getMap() != map || (filter != null && filter.isFilter(object))) continue;
            if (object.instanceOf(L1Type.L1TYPE_NPC)) {
                ((L1NpcInstance) object).deleteMe();
            } else if (object.instanceOf(L1Type.L1TYPE_INVENTORY)) {
                ((CInventory) object).clearItems();
            } else if (filter != null && object.instanceOf(L1Type.L1TYPE_LIFT)) {
                ((L1LiftGateInstance) object).dispose();
            } else if (filter != null && object.instanceOf(L1Type.L1TYPE_TREASURE_CHEST)) {
                ((L1TreasureChest) object).dispose();
            }
        }
    }
}
