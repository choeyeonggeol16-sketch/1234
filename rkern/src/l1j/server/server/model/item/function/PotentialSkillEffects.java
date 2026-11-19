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
final class PotentialSkillEffects {
    private PotentialSkillEffects() {}

    static void applyPotentialSkill(L1PcInstance pc, int skill) {
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
