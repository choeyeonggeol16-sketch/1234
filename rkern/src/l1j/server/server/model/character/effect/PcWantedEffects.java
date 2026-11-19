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
        switch (kind) {
        case doWanted1 -> {
            int mul = isOn ? 1 : -1;
            pc.addDmgRate(5 * mul);
            pc.addHitup(5 * mul);
            pc.addBowDmgup(5 * mul);
            pc.addBowHitup(5 * mul);
            pc.getAbility().addSp(5 * mul);
            pc.addDamageReductionByArmor(5 * mul);
            pc.getAC().addAc(-5 * mul);
            SC_InventoryIcon.icoEnd(pc, L1SkillId.수배);
            SC_InventoryIcon.iconNewUnLimit(pc, L1SkillId.수배1, 3945, true);
            SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "\\f3당신의 수배범 단계는 2단입니다 (수배버프 효과증가)");
            SC_SystemMessage.send(pc, "\\f3당신의 수배범 단계는 2단입니다 (수배버프 효과증가)");
            SC_SPMR.send(pc);
            SC_OwnCharAttrDef.send(pc);
            SC_OwnCharStatus2.send(pc);
            SC_OwnCharStatus.send(pc);
            if (Config.수배온오프) {
                SC_CharTitle.send(pc, pc.getId(), L1PcInstance.WANTED_TITLE1);
                SC_CharTitle.broadcast(pc, pc.getId(), L1PcInstance.WANTED_TITLE1);
            }
        }
        case doWanted -> {
            int mul = isOn ? 1 : -1;
            pc.addDmgRate(3 * mul);
            pc.addHitup(3 * mul);
            pc.addBowDmgup(3 * mul);
            pc.addBowHitup(3 * mul);
            pc.getAbility().addSp(3 * mul);
            pc.addDamageReductionByArmor(3 * mul);
            pc.getAC().addAc(-3 * mul);
            SC_InventoryIcon.iconNewUnLimit(pc, L1SkillId.수배, 3938, true);
            SC_SPMR.send(pc);
            SC_OwnCharAttrDef.send(pc);
            SC_OwnCharStatus2.send(pc);
            SC_OwnCharStatus.send(pc);
            if (Config.수배온오프) {
                SC_CharTitle.send(pc, pc.getId(), L1PcInstance.WANTED_TITLE);
                SC_CharTitle.broadcast(pc, pc.getId(), L1PcInstance.WANTED_TITLE);
            }
        }
        }
    }
}
