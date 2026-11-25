package l1j.server.server.model.rank.buff;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.*;
import l1j.server.server.model.Instance.*;
import l1j.server.server.model.rank.loader.RankLoadManager;
public final class RankBuffLegacy {
public static void run(L1PcInstance pc) { giveProtectionItem(pc); }
    private static void giveProtectionItem(L1PcInstance pc){
        L1ItemInstance item = ItemTable.getInstance().createItem(RankLoadManager.MRK_TOPPROTECTION_ID);
        if(pc.getInventory().checkAddItem(item, 1) == CInventory.OK
                && !pc.getInventory().checkItem(RankLoadManager.MRK_TOPPROTECTION_ID))
            pc.getInventory().storeItem(item);
    }
}
