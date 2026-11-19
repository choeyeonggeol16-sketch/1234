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
final class PotentialScrollSelection {
    private PotentialScrollSelection() {}
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
        PotentialScrollTransaction.useDurablePotentialScroll(pc, item, skill, notice);
    }
}
