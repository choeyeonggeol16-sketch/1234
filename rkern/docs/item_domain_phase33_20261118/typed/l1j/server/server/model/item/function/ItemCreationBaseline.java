package l1j.server.server.model.item.function;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.CInventory;
import l1j.server.server.model.CUserInventory;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
final class ItemCreationBaseline {
public static boolean createNewItem(L1PcInstance pc, int item_id, int count) {
    L1ItemInstance item = ItemTable.getInstance().createItem(item_id);
    if (item != null) {
        item.setCount(count);
        if (pc.getInventory().checkAddItem(item, count) == CInventory.OK) {
            pc.getInventory().storeItem(item);
        } else {
            SC_ServerMessage.send(pc, 82);
            return false;
        }
        SC_ServerMessage.send(pc, 403, item.getLogName());
        return true;
    } else {
        return false;
    }
}
static boolean createGMItem(L1PcInstance pc, int item_id, int count) {
    L1ItemInstance item = ItemTable.getInstance().createItem(item_id);
    if (item != null) {
        item.setCount(count);
        item.setIdentified(true);
        if (pc.getInventory().checkAddItem(item, count) == CInventory.OK) {
            pc.getInventory().storeItem(item);
        } else {
            SC_ServerMessage.send(pc, 82);
            return false;
        }
        SC_ServerMessage.send(pc, 403, item.getLogName());
        return true;
    } else {
        return false;
    }
}
static boolean createNewItem2(L1PcInstance pc, int item_id, int count, int EnchantLevel) {
    L1ItemInstance item = ItemTable.getInstance().createItem(item_id);
    if (item != null) {
        item.setCount(count);
        item.setEnchantLevel(EnchantLevel);
        item.setIdentified(true);
        if (pc.getInventory().checkAddItem(item, count) == CInventory.OK) {
            pc.getInventory().storeItem(item);
        } else {
            SC_ServerMessage.send(pc, 82);

            return false;
        }
        SC_ServerMessage.send(pc, 403, item.getLogName());

        return true;
    } else {
        return false;
    }
}
}