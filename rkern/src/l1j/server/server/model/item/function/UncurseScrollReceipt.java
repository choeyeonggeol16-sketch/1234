package l1j.server.server.model.item.function;

import l1j.server.server.model.network.core.cs.packet.CDB;
import l1j.server.server.model.network.core.cs.packet.User;
import java.util.logging.Logger;
import l1j.server.server.model.network.core.sc.packet.SC_ItemName;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import l1j.server.server.Account;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.L1Quest;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;
import l1j.server.server.model.network.core.sc.packet.SC_NewCreateItem;
import l1j.server.server.model.network.core.sc.packet.SC_ReturnedStat;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1Item;

/** Item-specific effect extracted without changing its original execution body. */
final class UncurseScrollReceipt {
    private static final Logger _log = Logger.getLogger(SpecialConsumableRouter.class.getName());

    private UncurseScrollReceipt() {}

    static void useUncurseScrollAtomic(L1PcInstance pc, L1ItemInstance scroll, int scrollId) {
        if (pc == null || scroll == null) return;
        try (l1j.server.server.model.L1InventoryTransaction tx =
                User.CheckAndSetPendingUseETCItem(pc,
                        l1j.server.server.storage.CacheD.accountOrderKey("item-use:", pc.getId()))) {
            if (!tx.debit(scroll, 1)) return;
            for (L1ItemInstance item : pc.getInventory().getItems()) {
                if (item == scroll || item.getItem().getBless() != 2) continue;
                if (!item.isEquipped() && (scrollId == 40119 || scrollId == 40097)) continue;
                L1Item normal = ItemTable.getInstance().getTemplate(item.getItemId() - 200000);
                if (normal == null) continue;
                if (normal.getType2() != item.getItem().getType2()
                        || normal.getType() != item.getItem().getType()
                        || normal.getType1() != item.getItem().getType1()) continue;
                if (item.getBless() < 1) continue;
                if (normal.isStackable() && pc.getInventory().checkItem(normal.getItemId())
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
