package l1j.server.server.model.character.effect;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus2;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharAttrDef;
import l1j.server.server.model.network.core.sc.packet.SC_SPMR;
import l1j.server.server.model.network.core.sc.packet.SC_CharTitle;
import l1j.server.Config;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_InventoryIcon;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

/**
 * Stateless wanted buff effects; existing callers keep their public PC methods. 
 */
public final class PcWantedEffects {
    
    private PcWantedEffects() {
    }
    
    public enum Kind {
        doWanted1 ,
        doWanted ;
    }
    
    public static void apply(L1PcInstance pc, Kind kind, boolean isOn) {
        final int skillId = kind == Kind.doWanted1
                ? L1SkillId.USER_WANTED1 : L1SkillId.USER_WANTED;
        final int otherSkill = kind == Kind.doWanted1
                ? L1SkillId.USER_WANTED : L1SkillId.USER_WANTED1;

        if (!isOn) {
            pc.removeSkillModifierFromOwner(skillId);
            if (pc.hasSkillEffect(skillId)) pc.killSkillEffectTimer(skillId);
            SC_InventoryIcon.icoEnd(pc, kind == Kind.doWanted1 ? L1SkillId.수배1 : L1SkillId.수배);
        } else {
            // Wanted tiers are mutually exclusive, and recasts must not
            // accumulate against base weapon, armor, or spell attributes.
            pc.removeSkillModifierFromOwner(otherSkill);
            if (pc.hasSkillEffect(otherSkill)) pc.killSkillEffectTimer(otherSkill);
            pc.applySkillModifierFromOwner(skillId, 0L);

            if (kind == Kind.doWanted1) {
                SC_InventoryIcon.icoEnd(pc, L1SkillId.수배);
                SC_InventoryIcon.iconNewUnLimit(pc, L1SkillId.수배1, 3945, true);
                SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "\\f3당신의 수배범 단계는 2단입니다 (수배버프 효과증가)");
                SC_SystemMessage.send(pc, "\\f3당신의 수배범 단계는 2단입니다 (수배버프 효과증가)");
            } else {
                SC_InventoryIcon.icoEnd(pc, L1SkillId.수배1);
                SC_InventoryIcon.iconNewUnLimit(pc, L1SkillId.수배, 3938, true);
            }
            if (Config.수배온오프) {
                final String title = kind == Kind.doWanted1
                        ? L1PcInstance.WANTED_TITLE1 : L1PcInstance.WANTED_TITLE;
                SC_CharTitle.send(pc, pc.getId(), title);
                SC_CharTitle.broadcast(pc, pc.getId(), title);
            }
        }
        SC_SPMR.send(pc);
        SC_OwnCharAttrDef.send(pc);
        SC_OwnCharStatus2.send(pc);
        SC_OwnCharStatus.send(pc);
    }
}
