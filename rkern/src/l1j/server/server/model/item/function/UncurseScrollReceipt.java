package l1j.server.server.model.item.function;

import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.datatables.CObjectDB;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.inventory.CUserInventory;
import l1j.server.server.model.network.core.cs.packet.CDB;
import l1j.server.server.model.network.core.cs.packet.User;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1Item;

/** Item-specific effect extracted without changing its original execution body. */
final class UncurseScrollReceipt {
    private static final Logger _log = Logger.getLogger(SpecialConsumableRouter.class.getName());

    private UncurseScrollReceipt() {}

    static void useUncurseScrollAtomic(L1PcInstance pc, L1ItemInstance scroll, int scrollId) {
        if (pc == null || scroll == null) return;
        try (l1j.server.server.model.inventory.L1InventoryTransaction tx =
                User.CheckAndSetPendingUseETCItem(pc,
                        l1j.server.server.storage.CacheD.accountOrderKey("item-use:", pc.getId()))) {
            if (!tx.debit(scroll, 1)) return;
            final CUserInventory inventory = pc.getInventory();
            final boolean equippedOnly = scrollId == 40119 || scrollId == 40097;
            final L1ItemInstance[] candidates = equippedOnly
                    ? inventory.GetEquippedItems() : inventory.GetCursedTemplateItems();
            for (L1ItemInstance item : candidates) {
                if (item == scroll || item.getItem().getBless() != 2) continue;
                if (equippedOnly && !item.isEquipped()) continue;
                L1Item normal = CObjectDB.LoadItem().getTemplate(item.getItemId() - 200000);
                if (normal == null) continue;
                if (normal.getType2() != item.getItem().getType2()
                        || normal.getType() != item.getItem().getType()
                        || normal.getType1() != item.getItem().getType1()) continue;
                if (item.getBless() < 1) continue;
                if (normal.isStackable() && inventory.HaveItemByClassId(normal.getItemId())
                        && !item.isEquipped()) {
                    int amount = item.getCount();
                    if (!tx.debit(item, amount) || tx.grant(normal.getItemId(), amount) == null)
                        throw new IllegalStateException("Cursed stack conversion failed");
                } else {
                    tx.uncursedTemplate(item, normal);
                }
            }
            tx.onFailure(() -> SC_SystemMessage.send(pc, "저주 해제 아이템 저장에 실패했습니다."));
            CDB.RequestUseItem(pc, tx, () -> {
                try { SC_ServerMessage.send(pc, 155); }
                catch (RuntimeException noticeFailure) {
                    _log.log(Level.WARNING, "Committed uncurse notice failed", noticeFailure);
                }
            });
        } catch (RuntimeException failure) {
            _log.log(Level.WARNING, "Uncurse scroll prepare failed: " + pc.getId(), failure);
            SC_SystemMessage.send(pc, "저주 해제를 처리하지 못했습니다.");
        }
    }
}
