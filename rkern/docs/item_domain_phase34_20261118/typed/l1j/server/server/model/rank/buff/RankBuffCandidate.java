package l1j.server.server.model.rank.buff;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.*;
import l1j.server.server.model.Instance.*;
import l1j.server.server.model.rank.loader.RankLoadManager;
public final class RankBuffCandidate {
public static void run(L1PcInstance pc) { giveProtectionItem(pc); }
    private static void giveProtectionItem(L1PcInstance pc){
        final int itemId = RankLoadManager.MRK_TOPPROTECTION_ID;
        // Reapplying the rank buff must not allocate and register a throwaway item.
        if (pc.getInventory().checkItem(itemId)) return;
        final L1ItemInstance item = ItemTable.getInstance().createItem(itemId);
        if (item == null) return;
        try {
            if (pc.getInventory().checkAddItem(item, 1) == L1Inventory.OK
                    && !pc.getInventory().checkItem(itemId)) {
                pc.getInventory().storeItem(item);
            }
        } finally {
            if (pc.getInventory().getItem(item.getId()) != item) {
                L1World.getInstance().removeObject(item);
            }
        }
    }
}
