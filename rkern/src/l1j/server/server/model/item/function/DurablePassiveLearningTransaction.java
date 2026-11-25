package l1j.server.server.model.item.function;

import l1j.server.server.model.network.core.cs.packet.CDB;
import l1j.server.server.model.network.core.cs.packet.User;
import java.util.logging.Logger;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharAttrDef;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import l1j.server.Config;
import l1j.server.server.utils.CommonUtil;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

/** Domain-specific item handler; original transaction ordering preserved. */
final class DurablePassiveLearningTransaction {
    private DurablePassiveLearningTransaction() {}

    private static final Logger _log = Logger.getLogger(SpecialConsumableRouter.class.getName());

    static void useDurablePassive(L1PcInstance pc, L1ItemInstance input,
            int skillId, int replacedSkillId, Runnable afterCommitted) {
        if (pc == null || input == null || pc.getInventory().GetByServerID(input.getId()) != input
                || pc.hasSkillEffect(skillId)) return;
        try (l1j.server.server.model.L1InventoryTransaction tx =
                User.CheckAndSetPendingUseETCItem(pc,
                        l1j.server.server.storage.CacheD.accountOrderKey("item-use:", pc.getId()))) {
            if (!tx.debit(input, 1)) return;
            final int owner = pc.getId();
            tx.requireTables("characters", "character_buff");
            tx.sql(con -> {
                try (PreparedStatement lock = con.prepareStatement(
                        "SELECT objid FROM characters WHERE objid=? FOR UPDATE")) {
                    lock.setInt(1, owner);
                    try (ResultSet rs = lock.executeQuery()) {
                        if (!rs.next()) throw new SQLException("Passive character row missing");
                    }
                }
                try (PreparedStatement check = con.prepareStatement(
                        "SELECT skill_id FROM character_buff WHERE char_obj_id=? AND skill_id=? FOR UPDATE")) {
                    check.setInt(1, owner);
                    check.setInt(2, skillId);
                    try (ResultSet rs = check.executeQuery()) {
                        if (rs.next()) throw new SQLException("Passive already recorded: " + skillId);
                    }
                }
                if (replacedSkillId != 0) {
                    try (PreparedStatement delete = con.prepareStatement(
                            "DELETE FROM character_buff WHERE char_obj_id=? AND skill_id=?")) {
                        delete.setInt(1, owner);
                        delete.setInt(2, replacedSkillId);
                        delete.executeUpdate();
                    }
                }
                try (PreparedStatement insert = con.prepareStatement(
                        "INSERT INTO character_buff(char_obj_id,skill_id,remaining_time,poly_id) VALUES(?,?,0,0)")) {
                    insert.setInt(1, owner);
                    insert.setInt(2, skillId);
                    if (insert.executeUpdate() != 1) throw new SQLException("Passive insert failed");
                }
            });
            tx.onFailure(() -> SC_SystemMessage.send(pc, "패시브 습득을 저장하지 못했습니다. 아이템을 확인해 주세요."));
            CDB.RequestUseItem(pc, tx, afterCommitted);
        } catch (RuntimeException failure) {
            _log.log(Level.WARNING, "Passive item prepare failed: " + pc.getId() + "/" + skillId, failure);
            SC_SystemMessage.send(pc, "패시브 습득을 처리하지 못했습니다.");
        }
    }
}
