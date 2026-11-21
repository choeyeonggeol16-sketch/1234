package l1j.server.server.model.character.effect;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_HpUpdate;
import l1j.server.server.model.network.core.sc.packet.SC_MpUpdate;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_SPELL_BUFF_NOTI;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_SPELL_BUFF_NOTI.eDurationShowType;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_SPELL_BUFF_NOTI.eNotiType;
import l1j.server.server.utils.eKind;

/**
 * Potential mutations and their shared wire notification; no per-player state. 
 */
public final class PcPotentialEffects {
    
    private PcPotentialEffects() {
    }
    private static final int FIELDS = 1 | 2 | 8 | 16 | 32 | 128 | 256 | 512 | 1024;
    
    public enum Kind {
        hp300 (L1SkillId.hp300, 5027),
        mp300 (L1SkillId.mp300, 5028),
        대미지감소5 (L1SkillId.대미지감소5, 5029),
        ac5 (L1SkillId.ac5, 5030),
        mr15 (L1SkillId.mr15, 5031),
        경험치10 (L1SkillId.경험치10, 5032),
        모든속성저항20 (L1SkillId.모든속성저항20, 5033),
        힘3 (L1SkillId.힘3, 5034),
        덱3 (L1SkillId.덱3, 5035),
        콘3 (L1SkillId.콘3, 5036),
        위즈3 (L1SkillId.위즈3, 5037),
        인트3 (L1SkillId.인트3, 5038),
        pvp대미지7 (L1SkillId.pvp대미지7, 5039),
        pvp감소7 (L1SkillId.pvp감소7, 5040),
        근거리대미지5 (L1SkillId.근거리대미지5, 5041),
        원거리대미지5 (L1SkillId.원거리대미지5, 5042),
        근거리명중5 (L1SkillId.근거리명중5, 5043),
        원거리명중5 (L1SkillId.원거리명중5, 5044),
        sp5 (L1SkillId.sp5, 5045),
        마법적중5 (L1SkillId.마법적중5, 5051),
        기술내성8 (L1SkillId.기술내성8, 5052),
        정령내성8 (L1SkillId.정령내성8, 5053),
        용언내성3 (L1SkillId.용언내성3, 5054),
        공포내성3 (L1SkillId.공포내성3, 5055),
        모든내성3 (L1SkillId.모든내성3, 5056),
        정령적중5 (L1SkillId.정령적중5, 5057),
        용언적중5 (L1SkillId.용언적중5, 5058),
        공포적중5 (L1SkillId.공포적중5, 5059),
        기술적중5 (L1SkillId.기술적중5, 5060),
        모든적중3 (L1SkillId.모든적중3, 5061),
        경험치보너스20 (L1SkillId.경험치보너스20, 5062);
        private final int skillId;
        private final int tooltip;
        
        Kind(int skillId, int tooltip) {
            this.skillId = skillId;
            this.tooltip = tooltip;
        }
    }
    
    public static void apply(L1PcInstance pc, Kind kind) {
        switch (kind) {
        case hp300 -> {
            pc.applySkillModifierFromOwner(kind.skillId, 0L);
            SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
        }
        case mp300 -> {
            pc.applySkillModifierFromOwner(kind.skillId, 0L);
            SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
        }
        case 콘3 -> {
            pc.applySkillModifierFromOwner(kind.skillId, 0L);
            SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
        }
        case 위즈3 -> {
            pc.applySkillModifierFromOwner(kind.skillId, 0L);
            SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
        }
        // These four legacy effects still need distinct pump lanes. Preserve
        // their original formulas until the corresponding getter is migrated.
        case pvp감소7 -> pc.addPVPMagicDamageReduction(7);
        case 기술적중5 -> pc.getResistance().addSpecialPierce(eKind.ABILITY, 5);
        case 용언적중5 -> pc.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, 5);
        case 공포적중5 -> pc.getResistance().addSpecialPierce(eKind.FEAR, 5);
        case 경험치10, 경험치보너스20 -> { }
        default -> pc.applySkillModifierFromOwner(kind.skillId, 0L);
        }
        SC_SPELL_BUFF_NOTI.sendFields(pc, eNotiType.RESTAT, kind.skillId, 0, eDurationShowType.TYPE_EFF_AUTO_DAY_HOUR_MIN_SEC, 4710, 4710, 0, kind.tooltip, kind.tooltip, kind.tooltip, true, 0, 0, 0, FIELDS);
        pc.setSkillEffect(kind.skillId, -1);
    }
}
