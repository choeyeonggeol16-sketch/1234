package l1j.server.server.model.item.function;

import static l1j.server.server.model.character.skill.L1SkillId.STATUS_FREEZE;

import l1j.server.Config;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.network.core.sc.packet.SC_HpUpdate;
import l1j.server.server.model.network.core.sc.packet.SC_InventoryIcon;
import l1j.server.server.model.network.core.sc.packet.SC_MpUpdate;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharAttrDef;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_SPMR;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.utils.GameUtils;

/** Consumable active skill for item ID 70714. */
final class PotentialSkillConsumable {
    private PotentialSkillConsumable() {}

    static void use(L1PcInstance pc) {
            if (pc.getType() == 6 && pc.getLevel() >= 1) {
                if (pc.hasSkillEffect(L1SkillId.포텐셜대기시간)) {
                    int n = pc.getSkillEffectTimeSec(L1SkillId.포텐셜대기시간);
                    SC_SystemMessage.send(pc, "\\fY포텐셜 대기시간 : " + n + "초 후에 사용가능합니다.");
                    return;
                }
                if (GameUtils.isNonAction(pc)) {
                    return;
                }
                if (!pc.getInventory().HaveItemByClassId(210038, 2)) {
                    pc.sendPackets("속성석이 부족합니다.");
                    return;
                }
                if (pc.getCurrentMp() < 30) {
                    pc.sendPackets("MP가 부족합니다.");
                    return;
                }
                DurableSkillReagentTransaction.useDurableSkillReagent(pc, 210038, 2, ()->{
                    if (pc.hasSkillEffect(L1SkillId.포텐셜)) {
                        pc.removeSkillEffect(L1SkillId.포텐셜);
                    }
                    pc.setSkillEffect(L1SkillId.포텐셜, 128 * 1000);
                    pc.setSkillEffect(L1SkillId.포텐셜대기시간, Config.포텐셜대기시간 * 1000);
                    pc.setCurrentMp(pc.getCurrentMp() - 30);
                    int addHp = (int)Math.round(pc.getMaxHp() * 0.2);
                    int addMp = (int)Math.round(pc.getMaxMp() * 0.2);
                    int addMR = (int)Math.round(pc.GetMDefend() * 0.2);
                    int addSP = (int)Math.round(pc.GetStatSp() * 0.2);
                    int addDG = (int)Math.round(pc.getDg() * 0.2);
                    int addER = (int)Math.round(pc.getTotalER() * 0.2);
                    pc.applyPotentialModifierFromOwner(addHp, addMp, addMR, addSP, addER, addDG);
                    SC_SkillSound.send(pc, pc.getId(), 16647);
                    SC_SkillSound.broadcast(pc, pc.getId(), 16647);
                    SC_InventoryIcon.icoNew(pc, L1SkillId.포텐셜, 5386, 128, true);
                    L1SkillUse.cast(pc, L1SkillId.보조버프, pc.getId(), pc.getX(), pc.getY(), null, 0, L1SkillUse.TYPE_SPELLSC);
                    SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
                    SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
                    SC_SPMR.send(pc);
                    SC_OwnCharAttrDef.send(pc);
                    SC_PacketBox.send(pc, SC_PacketBox.ER_UpDate, pc.getTotalER());
                    pc.sendPackets("포텐셜의 기운이 느껴집니다.");
                });
            } else {
                pc.sendPackets("현재 [포텐셜]을 사용할 수 없는 조건입니다");
            }
    }
}
