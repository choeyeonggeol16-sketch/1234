package l1j.server.server.model.item.function;


import static l1j.server.server.model.item.function.CreatureItemActions.*;

import java.util.logging.Logger;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.cs.packet.CDB;
import l1j.server.server.model.network.core.cs.packet.User;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.storage.CacheD;

public final class DurabilityRepairItems {
    private static final Logger ITEM_USE2_LOG = CreatureItemActions.ITEM_USE2_LOG;
    private DurabilityRepairItems() {}

    public static void UseDurableRepair(L1PcInstance pc, L1ItemInstance whetstone,
            L1ItemInstance target, Runnable afterCommitted) {
        if (pc == null || whetstone == null || target == null || whetstone == target
                || pc.getInventory().GetByServerID(whetstone.getId()) != whetstone
                || pc.getInventory().GetByServerID(target.getId()) != target
                || target.getItem().getType2() == 0 || target.get_durability() <= 0) return;
        try (l1j.server.server.model.inventory.L1InventoryTransaction tx =
                User.CheckAndSetPendingUseETCItem(pc,
                        CacheD.accountOrderKey("item-use:", pc.getId()))) {
            if (!tx.debit(whetstone, 1)) return;
            tx.repairDurability(target);
            tx.onFailure(() -> SC_SystemMessage.send(pc, "숫돌 사용을 저장하지 못했습니다."));
            CDB.RequestUseItem(pc, tx, afterCommitted);
        } catch (RuntimeException failure) {
            ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
                    "Whetstone prepare failed: " + pc.getId(), failure);
            SC_SystemMessage.send(pc, "숫돌 사용을 처리하지 못했습니다.");
        }
    }
}
