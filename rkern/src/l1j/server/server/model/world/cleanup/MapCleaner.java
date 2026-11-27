package l1j.server.server.model.world.cleanup;

import java.util.ArrayList;

import l1j.server.server.model.world.instance.CInstanceWork;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.inventory.CInventory;
import l1j.server.server.model.inventory.L1GroundInventory;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.item.treasure.L1TreasureChest;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.world.instance.shared.L1LiftGateInstance;
import l1j.server.server.shared.L1Type;

public final class MapCleaner implements Runnable {
    public static void cleanup(L1Map map, MapCleanerFilter filter) {
        final MapCleaner cleaner = new MapCleaner(map, filter);
        if (!CInstanceWork.tryExecute(cleaner) && !CInstanceWork.isClosed())
            CInstanceWork.scheduleRequired(cleaner, 1L);
    }

    /** The zone owner retains its map ID through completion of this cleanup. */
    public static void cleanupNow(L1Map map, MapCleanerFilter filter) {
        new MapCleaner(map, filter).run();
    }

    public static void removeGroundItems(L1Map map) {
        if (map == null || map.isNull()) return;
        final CWorld world = CWorld.getInstance();
        final var visible = world.getVisibleObjects(map.getId());
        if (visible == null) return;

        // Both registries traverse by object ID. Snapshot only the selected
        // room's items before deleting from its visible registry.
        ArrayList<L1ItemInstance> items = null;
        for (L1Object object : visible.values()) {
            if (object instanceof L1ItemInstance item && item.getMap() == map) {
                if (items == null) items = new ArrayList<>();
                items.add(item);
            }
        }
        if (items == null) return;
        for (L1ItemInstance item : items) {
            if (item.getMap() != map) continue;
            final L1GroundInventory ground = world.findGroundInventory(item.getX(), item.getY(), item.getMapId());
            if (ground != null && ground.getMap() == map) ground.DeleteItemAmount(item);
        }
    }

    private final L1Map map;
    private final MapCleanerFilter filter;

    private MapCleaner(L1Map map, MapCleanerFilter filter) {
        this.map = map;
        this.filter = filter;
    }

    @Override public void run() {
        final var visible = CWorld.getInstance().getVisibleObjects(map.getId());
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
