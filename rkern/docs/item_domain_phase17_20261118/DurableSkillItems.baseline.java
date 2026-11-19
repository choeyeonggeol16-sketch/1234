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

final class DurableSkillItems {
    private static final Logger _log = Logger.getLogger(SpecialConsumableRouter.class.getName());
    private DurableSkillItems() {}

    // A learned skill remains in inventory; only its material is consumed.
    // The material debit must commit before MP, buffs, cooldowns or party effects.
    static void useDurableSkillReagent(L1PcInstance pc, int materialId,
            int amount, Runnable afterCommitted) {
        if (pc == null || amount <= 0) return;
        try (l1j.server.server.model.L1InventoryTransaction tx =
                User.CheckAndSetPendingUseETCItem(pc,
                        l1j.server.server.storage.CacheD.accountOrderKey("item-use:", pc.getId()))) {
            if (!tx.debit(materialId, amount)) {
                SC_SystemMessage.send(pc, "마법재료가 부족합니다.");
                return;
            }
            tx.onFailure(() -> SC_SystemMessage.send(pc, "마법재료 소모를 저장하지 못했습니다."));
            CDB.RequestUseItem(pc, tx, afterCommitted);
        } catch (RuntimeException failure) {
            _log.log(Level.WARNING, "Skill material transaction prepare failed: "
                    + pc.getId() + "/" + materialId, failure);
            SC_SystemMessage.send(pc, "마법재료 사용을 처리하지 못했습니다.");
        }
    }

    /** A permanent learned passive and its source item are one durable decision.
     * The character row serializes persistent skill admission; an already
     * recorded passive must never charge the user twice. Effects and packets
     * happen on the CIO owner only after the receipt-backed COMMIT.
     */
    static void useDurablePassive(L1PcInstance pc, L1ItemInstance input,
            int skillId, int replacedSkillId, Runnable afterCommitted) {
        if (pc == null || input == null || pc.getInventory().getItem(input.getId()) != input
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


    // Tier probabilities are kept from the original scroll. All 11/7/13 results must be reachable.
    private static final int[] POTENTIAL_TIER1_SKILLS = {
        L1SkillId.hp300, L1SkillId.mp300, L1SkillId.ac5, L1SkillId.mr15,
        L1SkillId.경험치10, L1SkillId.모든속성저항20, L1SkillId.힘3, L1SkillId.덱3,
        L1SkillId.콘3, L1SkillId.위즈3, L1SkillId.인트3
    };

    private static final String[] POTENTIAL_TIER1_NOTICES = {
        "\\aH잠재력 [ HP +300 ]으로 진화에 성공 하였습니다.", "\\aH잠재력 [ MP +300 ]으로 진화에 성공 하였습니다.", "\\aH잠재력 [ AC -5 ]으로 진화에 성공 하였습니다.", "\\aH잠재력 [ MR +15 ]으로 진화에 성공 하였습니다.",
        "\\aH잠재력 [ 추가경험치 10% ]으로 진화에 성공 하였습니다.", "\\aH잠재력 [ 모든속성저항 +20 ]으로 진화에 성공 하였습니다.", "\\aH잠재력 [ STR +3 ]으로 진화에 성공 하였습니다.", "\\aH잠재력 [ DEX +3 ]으로 진화에 성공 하였습니다.",
        "\\aH잠재력 [ CON +3 ]으로 진화에 성공 하였습니다.", "\\aH잠재력 [ WIS +3 ]으로 진화에 성공 하였습니다.", "\\aH잠재력 [ INT +3 ]으로 진화에 성공 하였습니다."
    };

    private static final int[] POTENTIAL_TIER2_SKILLS = {
        L1SkillId.pvp대미지7, L1SkillId.pvp감소7, L1SkillId.근거리대미지5, L1SkillId.원거리대미지5,
        L1SkillId.근거리명중5, L1SkillId.원거리명중5, L1SkillId.sp5
    };

    private static final String[] POTENTIAL_TIER2_NOTICES = {
        "\\aH잠재력 [ PVP대미지 +7 ]으로 진화에 성공 하였습니다.", "\\aH잠재력 [ PVP감소 +7 ]으로 진화에 성공 하였습니다.", "\\aH잠재력 [ 근거리대미지 +5 ]으로 진화에 성공 하였습니다.", "\\aH잠재력 [ 원거리대미지 +5 ]으로 진화에 성공 하였습니다.",
        "\\aH잠재력 [ 근거리명중 +5 ]으로 진화에 성공 하였습니다.", "\\aH잠재력 [ 원거리명중 +5 ]으로 진화에 성공 하였습니다.", "\\aH잠재력 [ SP +5 ]으로 진화에 성공 하였습니다."
    };

    private static final int[] POTENTIAL_TIER3_SKILLS = {
        L1SkillId.마법적중5, L1SkillId.기술내성8, L1SkillId.정령내성8, L1SkillId.용언내성3,
        L1SkillId.공포내성3, L1SkillId.모든내성3, L1SkillId.정령적중5, L1SkillId.용언적중5,
        L1SkillId.공포적중5, L1SkillId.기술적중5, L1SkillId.모든적중3, L1SkillId.경험치보너스20,
        L1SkillId.대미지감소5
    };

    private static final String[] POTENTIAL_TIER3_NOTICES = {
        "\\aH잠재력 [ 마법적중 +5 ]으로 진화에 성공 하였습니다.", "\\aH잠재력 [ 기술내성 +8 ]으로 진화에 성공 하였습니다.", "\\aH잠재력 [ 정령내성 +8 ]으로 진화에 성공 하였습니다.", "\\aH잠재력 [ 용언내성 +3 ]으로 진화에 성공 하였습니다.",
        "\\aH잠재력 [ 공포내성 +3 ]으로 진화에 성공 하였습니다.", "\\aH잠재력 [ 모든내성 +3 ]으로 진화에 성공 하였습니다.", "\\aH잠재력 [ 정령적중 +3 ]으로 진화에 성공 하였습니다.", "\\aH잠재력 [ 용언적중 +3 ]으로 진화에 성공 하였습니다.",
        "\\aH잠재력 [ 공포적중 +3 ]으로 진화에 성공 하였습니다.", "\\aH잠재력 [ 기술적중 +3 ]으로 진화에 성공 하였습니다.", "\\aH잠재력 [ 모든적중 +3 ]으로 진화에 성공 하였습니다.", "\\aH잠재력 [ 경험치보너스 +20% ]으로 진화에 성공 하였습니다.",
        "\\aH잠재력 [ 대미지감소 +5 ]으로 진화에 성공 하였습니다."
    };

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


    static void usePotentialScroll(L1PcInstance pc, L1ItemInstance item) {
        final int roll = CommonUtil.random(1000);
        final int skill;
        final String notice;
        if (roll <= Config.부여주문서3단) {
            final int index = CommonUtil.random(POTENTIAL_TIER3_SKILLS.length);
            skill = POTENTIAL_TIER3_SKILLS[index];
            notice = POTENTIAL_TIER3_NOTICES[index];
        } else if (roll <= Config.부여주문서2단) {
            final int index = CommonUtil.random(POTENTIAL_TIER2_SKILLS.length);
            skill = POTENTIAL_TIER2_SKILLS[index];
            notice = POTENTIAL_TIER2_NOTICES[index];
        } else if (roll <= Config.부여주문서1단) {
            final int index = CommonUtil.random(POTENTIAL_TIER1_SKILLS.length);
            skill = POTENTIAL_TIER1_SKILLS[index];
            notice = POTENTIAL_TIER1_NOTICES[index];
        } else {
            skill = 0;
            notice = null;
        }
        useDurablePotentialScroll(pc, item, skill, notice);
    }

    static void useDurablePotentialScroll(L1PcInstance pc, L1ItemInstance input,
            int newSkill, String notice) {
        if (pc == null || input == null || pc.getInventory().getItem(input.getId()) != input) return;
        try (l1j.server.server.model.L1InventoryTransaction tx =
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
                    SC_SystemMessage.send(pc, "케릭터 잠재력 진화에 실패 하였습니다.");
                    SC_OwnCharAttrDef.send(pc);
                    return;
                }
                DollEffectItems.RemoveDollPotential(pc);
                applyPotentialSkill(pc, newSkill);
                pc.setSkillEffect(newSkill, -1);
                SC_SkillSound.send(pc, pc.getId(), 3241);
                SC_SkillSound.broadcast(pc, pc.getId(), 3241);
                SC_SystemMessage.send(pc, notice);
                SC_OwnCharAttrDef.send(pc);
            });
        } catch (RuntimeException failure) {
            _log.log(Level.WARNING, "Potential scroll prepare failed: " + pc.getId(), failure);
            SC_SystemMessage.send(pc, "잠재력 진화를 처리하지 못했습니다.");
        }
    }

    private static void applyPotentialSkill(L1PcInstance pc, int skill) {
        switch (skill) {

            case L1SkillId.hp300: pc.hp300(true); break;
            case L1SkillId.mp300: pc.mp300(true); break;
            case L1SkillId.ac5: pc.ac5(true); break;
            case L1SkillId.mr15: pc.mr15(true); break;
            case L1SkillId.경험치10: pc.경험치10(true); break;
            case L1SkillId.모든속성저항20: pc.모든속성저항20(true); break;
            case L1SkillId.힘3: pc.힘3(true); break;
            case L1SkillId.덱3: pc.덱3(true); break;
            case L1SkillId.콘3: pc.콘3(true); break;
            case L1SkillId.위즈3: pc.위즈3(true); break;
            case L1SkillId.인트3: pc.인트3(true); break;
            case L1SkillId.pvp대미지7: pc.pvp대미지7(true); break;
            case L1SkillId.pvp감소7: pc.pvp감소7(true); break;
            case L1SkillId.근거리대미지5: pc.근거리대미지5(true); break;
            case L1SkillId.근거리명중5: pc.근거리명중5(true); break;
            case L1SkillId.원거리명중5: pc.원거리명중5(true); break;
            case L1SkillId.sp5: pc.sp5(true); break;
            case L1SkillId.마법적중5: pc.마법적중5(true); break;
            case L1SkillId.기술내성8: pc.기술내성8(true); break;
            case L1SkillId.정령내성8: pc.정령내성8(true); break;
            case L1SkillId.용언내성3: pc.용언내성3(true); break;
            case L1SkillId.공포내성3: pc.공포내성3(true); break;
            case L1SkillId.모든내성3: pc.모든내성3(true); break;
            case L1SkillId.정령적중5: pc.정령적중5(true); break;
            case L1SkillId.용언적중5: pc.용언적중5(true); break;
            case L1SkillId.공포적중5: pc.공포적중5(true); break;
            case L1SkillId.기술적중5: pc.기술적중5(true); break;
            case L1SkillId.모든적중3: pc.모든적중3(true); break;
            case L1SkillId.경험치보너스20: pc.경험치보너스20(true); break;
            case L1SkillId.대미지감소5: pc.대미지감소5(true); break;

            default: throw new IllegalArgumentException("Unknown potential reward skill: " + skill);
        }
    }

}
