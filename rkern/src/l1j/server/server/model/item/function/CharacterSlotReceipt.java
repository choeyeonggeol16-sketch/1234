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
final class CharacterSlotReceipt {
    private static final Logger _log = Logger.getLogger(SpecialConsumableRouter.class.getName());

    private CharacterSlotReceipt() {}

    static void useCharacterSlotExpansionAtomic(CUserSocket client,
            L1PcInstance pc, L1ItemInstance input) {
        if (client == null || client.getAccount() == null || pc == null || input == null
                || input.getItemId() != 210083
                || pc.getInventory().getItem(input.getId()) != input) return;
        final int before = client.getAccount().getCharSlot();
        if (before >= 8) {
            SC_SystemMessage.send(pc, "캐릭터 슬롯이 이미 가득찼습니다.");
            return;
        }
        if (before < 0) return;
        final int after = before + 1;
        final String accountName = client.getAccount().getName();
        try (l1j.server.server.model.L1InventoryTransaction tx =
                User.CheckAndSetPendingUseETCItem(pc,
                        l1j.server.server.storage.CacheD.accountOrderKey(accountName))) {
            if (!tx.debit(input, 1)) return;
            tx.requireTables("accounts");
            tx.sql(con -> {
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE accounts SET charslot=? WHERE login=? AND charslot=? AND charslot<8")) {
                    ps.setInt(1, after);
                    ps.setString(2, accountName);
                    ps.setInt(3, before);
                    if (ps.executeUpdate() != 1)
                        throw new SQLException("Character slot preimage changed: " + accountName);
                }
            });
            tx.onFailure(() -> SC_SystemMessage.send(pc, "캐릭터 슬롯 확장을 저장하지 못했습니다. 아이템을 확인해 주세요."));
            CDB.RequestUseItem(pc, tx, () -> {
                client.getAccount().setCharSlot(after);
                pc.getAccount().is_changed_slot(true);
                SC_SystemMessage.send(pc, "캐릭터 슬롯 확장 완료(완전히 접속종료후 적용됨)");
            });
        } catch (RuntimeException failure) {
            _log.log(Level.WARNING, "Character slot transaction failed: " + pc.getId(), failure);
            SC_SystemMessage.send(pc, "캐릭터 슬롯 확장을 처리하지 못했습니다.");
        }
    }
}
