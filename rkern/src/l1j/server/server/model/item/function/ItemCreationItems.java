package l1j.server.server.model.item.function;

import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

/** Legacy immediate grants, with world-object ownership cleaned on failed/merged inserts. */
final class ItemCreationItems {
    private ItemCreationItems() {}

    public static boolean createNewItem(L1PcInstance pc, int itemId, int count) {
        return create(pc, itemId, count, 0, false);
    }

    static boolean createGMItem(L1PcInstance pc, int itemId, int count) {
        return create(pc, itemId, count, 0, true);
    }

    static boolean createNewItem2(L1PcInstance pc, int itemId, int count, int enchantLevel) {
        return create(pc, itemId, count, enchantLevel, true);
    }

    private static boolean create(L1PcInstance pc, int itemId, int count,
            int enchantLevel, boolean explicitProperties) {
        // No object is created for invalid quantities; none can be inserted.
        if (pc == null || count <= 0) return false;
        final L1ItemInstance item = ItemTable.getInstance().createItem(itemId);
        if (item == null) return false;
        try {
            item.setCount(count);
            if (explicitProperties) {
                item.setEnchantLevel(enchantLevel);
                item.setIdentified(true);
            }
            if (pc.getInventory().checkAddItem(item, count) != L1Inventory.OK) {
                SC_ServerMessage.send(pc, 82);
                return false;
            }
            // storeItem may merge a stackable grant into an existing instance.
            // In that case ItemTable's pre-registered temporary object is orphaned
            // unless it is removed from L1World.
            if (pc.getInventory().storeItem(item) == null) return false;
            SC_ServerMessage.send(pc, 403, item.getLogName());
            return true;
        } finally {
            // Keep the world object only when THIS new instance really entered
            // inventory. On rejection, merge, or a failed insert, release it.
            if (pc.getInventory().getItem(item.getId()) != item) {
                L1World.getInstance().removeObject(item);
            }
        }
    }
}
