package l1j.server.server.model.item.function;

import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.L1PcInventory;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

/** Sealed-equipment legacy grant, including its original bless update order. */
public final class SealedEquipmentRewards {
    private SealedEquipmentRewards() {}

    static boolean UseSealedItem(L1PcInstance pc, int itemId, int count, int enchantLevel,
            int bless, int attr, boolean identified) {
        final L1ItemInstance item = ItemTable.getInstance().createItem(itemId);
        if (item == null) return false;
        try {
            item.setCount(count);
            item.setIdentified(identified);
            item.setEnchantLevel(enchantLevel);
            item.setAttrEnchantLevel(attr);
            item.setIdentified(true); // Original operation sets identified twice.
            if (pc.getInventory().checkAddItem(item, count) != L1Inventory.OK) {
                SC_ServerMessage.send(pc, 82);
                return false;
            }
            pc.getInventory().storeItem(item);
            item.setBless(bless);
            pc.getInventory().updateItem(item, L1PcInventory.COL_BLESS);
            pc.getInventory().saveItem(item, L1PcInventory.COL_BLESS);
            SC_ServerMessage.send(pc, 403, item.getLogName());
            return true;
        } finally {
            if (pc.getInventory().getItem(item.getId()) != item) {
                L1World.getInstance().removeObject(item);
            }
        }
    }
}
