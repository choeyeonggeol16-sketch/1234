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
import l1j.server.server.model.network.core.session.CUserSession;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;
import l1j.server.server.model.network.core.sc.packet.SC_NewCreateItem;
import l1j.server.server.model.network.core.sc.packet.SC_ReturnedStat;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1Item;

/** Item-specific effect extracted without changing its original execution body. */
final class TamPointBoxReceipt {
    private static final Logger _log = Logger.getLogger(SpecialConsumableRouter.class.getName());

    private TamPointBoxReceipt() {}

    static void useTamBoxAtomic(L1PcInstance pc, L1ItemInstance box) {
        if (pc == null || box == null || pc.getNetConnection() == null) return;
        final Account account = pc.getNetConnection().getAccount();
        if (account == null || !account.tryBeginTamAward()) return;
        boolean submitted = false;
        try (l1j.server.server.model.L1InventoryTransaction tx =
                User.CheckAndSetPendingUseETCItem(pc,
                        l1j.server.server.storage.CacheD.accountOrderKey(account.getName()))) {
            if (!tx.debit(box, 1)) return;
            tx.requireTables("accounts");
            tx.sql(con -> {
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE accounts SET Tam_Point=Tam_Point+100000 "
                        + "WHERE login=? AND Tam_Point BETWEEN 0 AND 2147383647")) {
                    ps.setString(1, account.getName());
                    if (ps.executeUpdate() != 1) throw new SQLException("Tam award account missing or overflow");
                }
            });
            tx.onFailure(() -> {
                account.endTamAward();
                account.updateTam();
                SC_SystemMessage.send(pc, "탐 상자 사용을 저장하지 못했습니다.");
            });
            CDB.RequestUseItem(pc, tx, () -> {
                account.addTamPoint(100000);
                account.endTamAward();
                account.updateTam();
                SC_NewCreateItem.sendTamPoint(pc, pc.getNetConnection());
                SC_SystemMessage.send(pc, "탐 10만개를 얻었습니다.");
            });
            submitted = true;
        } catch (RuntimeException failure) {
            _log.log(Level.WARNING, "Tam box prepare failed: " + pc.getId(), failure);
            SC_SystemMessage.send(pc, "탐 상자를 처리하지 못했습니다.");
        } finally {
            if (!submitted) account.endTamAward();
        }
    }
}
