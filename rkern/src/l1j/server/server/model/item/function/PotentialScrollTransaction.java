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
final class PotentialScrollTransaction {
    private PotentialScrollTransaction() {}

    private static final Logger _log = Logger.getLogger(SpecialConsumableRouter.class.getName());
    private static final int[] POTENTIAL_CLEAR_SKILLS = {
        L1SkillId.hp300, L1SkillId.mp300, L1SkillId.대미지감소5, L1SkillId.ac5,
        L1SkillId.mr15, L1SkillId.경험치10, L1SkillId.모든속성저항20, L1SkillId.힘3,
        L1SkillId.덱3, L1SkillId.콘3, L1SkillId.위즈3, L1SkillId.인트3,
        L1SkillId.pvp대미지7, L1SkillId.pvp감소7, L1SkillId.근거리대미지5, L1SkillId.원거리대미지5,
        L1SkillId.근거리명중5, L1SkillId.원거리명중5, L1SkillId.sp5, L1SkillId.마법적중5,
        L1SkillId.기술내성8, L1SkillId.정령내성8, L1SkillId.용언내성3, L1SkillId.공포내성3,
        L1SkillId.모든내성3, L1SkillId.정령적중5, L1SkillId.용언적중5, L1SkillId.공포적중5,
        L1SkillId.기술적중5, L1SkillId.모든적중3, L1SkillId.경험치보너스20
    };

    private static final String POTENTIAL_CLEAR_SQL =
            "DELETE FROM character_buff WHERE char_obj_id=? AND skill_id IN (?"
            + ",?".repeat(POTENTIAL_CLEAR_SKILLS.length - 1) + ")";



    static void useDurablePotentialScroll(L1PcInstance pc, L1ItemInstance input,
            int newSkill, String notice) {
        if (pc == null || input == null || pc.getInventory().GetByServerID(input.getId()) != input) return;
        try (l1j.server.server.model.inventory.L1InventoryTransaction tx =
                User.CheckAndSetPendingUseETCItem(pc,
                        l1j.server.server.storage.CacheD.accountOrderKey("item-use:", pc.getId()))) {
            if (!tx.debit(input, 1)) return;
            if (newSkill != 0) {
                final int owner = pc.getId();
                tx.requireTables("characters", "character_buff");
                tx.sql(con -> {
                    try (PreparedStatement lock = con.prepareStatement(
                            "SELECT objid FROM characters WHERE objid=? FOR UPDATE")) {
                        lock.setInt(1, owner);
                        try (ResultSet rs = lock.executeQuery()) {
                            if (!rs.next()) throw new SQLException("Potential character row missing");
                        }
                    }
                    try (PreparedStatement delete = con.prepareStatement(POTENTIAL_CLEAR_SQL)) {
                        delete.setInt(1, owner);
                        for (int i = 0; i < POTENTIAL_CLEAR_SKILLS.length; i++)
                            delete.setInt(i + 2, POTENTIAL_CLEAR_SKILLS[i]);
                        delete.executeUpdate();
                    }
                    try (PreparedStatement insert = con.prepareStatement(
                            "INSERT INTO character_buff(char_obj_id,skill_id,remaining_time,poly_id) VALUES(?,?,0,0)")) {
                        insert.setInt(1, owner);
                        insert.setInt(2, newSkill);
                        if (insert.executeUpdate() != 1) throw new SQLException("Potential insert failed");
                    }
                });
            }
            tx.onFailure(() -> SC_SystemMessage.send(pc, "잠재력 진화를 저장하지 못했습니다. 아이템을 확인해 주세요."));
            CDB.RequestUseItem(pc, tx, () -> {
                if (newSkill == 0) {
                    try { SC_SystemMessage.send(pc, "케릭터 잠재력 진화에 실패 하였습니다."); }
                    catch (RuntimeException noticeFailure) {
                        _log.log(Level.WARNING, "Committed potential-failure notice failed", noticeFailure);
                    }
                    try { SC_OwnCharAttrDef.send(pc); }
                    catch (RuntimeException noticeFailure) {
                        _log.log(Level.WARNING, "Committed potential-failure stat notice failed", noticeFailure);
                    }
                    return;
                }
                DollEffectItems.RemoveDollPotential(pc);
                PotentialSkillEffects.applyPotentialSkill(pc, newSkill);
                pc.setSkillEffect(newSkill, -1);
                try { SC_SkillSound.send(pc, pc.getId(), 3241); }
                catch (RuntimeException noticeFailure) {
                    _log.log(Level.WARNING, "Committed potential sound failed", noticeFailure);
                }
                try { SC_SkillSound.broadcast(pc, pc.getId(), 3241); }
                catch (RuntimeException noticeFailure) {
                    _log.log(Level.WARNING, "Committed potential broadcast failed", noticeFailure);
                }
                try { SC_SystemMessage.send(pc, notice); }
                catch (RuntimeException noticeFailure) {
                    _log.log(Level.WARNING, "Committed potential message failed", noticeFailure);
                }
                try { SC_OwnCharAttrDef.send(pc); }
                catch (RuntimeException noticeFailure) {
                    _log.log(Level.WARNING, "Committed potential stat notice failed", noticeFailure);
                }
            });
        } catch (RuntimeException failure) {
            _log.log(Level.WARNING, "Potential scroll prepare failed: " + pc.getId(), failure);
            SC_SystemMessage.send(pc, "잠재력 진화를 처리하지 못했습니다.");
        }
    }
}
