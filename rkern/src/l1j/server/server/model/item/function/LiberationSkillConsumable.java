package l1j.server.server.model.item.function;

import static l1j.server.server.model.character.skill.L1SkillId.STATUS_FREEZE;

import l1j.server.Config;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.network.core.sc.packet.SC_InventoryIcon;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.utils.GameUtils;

/** Consumable active skill for item ID 70735. */
final class LiberationSkillConsumable {
    private LiberationSkillConsumable() {}

    static void use(L1PcInstance pc) {
            if (pc.hasSkillEffect(L1SkillId.리버레이션대기)) {
                int n = pc.getSkillEffectTimeSec(L1SkillId.리버레이션대기);
                SC_SystemMessage.send(pc, "\\fY리버레이션 대기시간 : " + n + "초 후에 사용가능합니다.");
                return;
            }
            if (GameUtils.isNonAction(pc)) {
                return;
            }
            if (pc.isInvisble()) {
                SC_SystemMessage.send(pc, "리버레이션은 투명상태로 시전이 불가능합니다.");
                return;
            }
            if (!pc.getInventory().HaveItemByClassId(40319, 4)) {
                SC_SystemMessage.send(pc, "마법재료가 부족합니다.");
                return;
            }
            if (pc.getCurrentMp() < 20) {
                SC_ServerMessage.send(pc, 278);
                return;
            }
            if (pc.getType() == 2 && pc.getLevel() >= 85) {
                DurableSkillReagentTransaction.useDurableSkillReagent(pc, 40319, 4, ()->{
                    if (pc.hasSkillEffect(L1SkillId.리버레이션)) {
                        pc.removeSkillEffect(L1SkillId.리버레이션);
                    }
                    pc.setSkillEffect(L1SkillId.리버레이션대기, Config.리버레이션대기 * 1000);
                    pc.setSkillEffect(L1SkillId.리버레이션, Config.리버레이션지속 * 1000);
                    SC_InventoryIcon.icoNew(pc, L1SkillId.리버레이션, 5123, Config.리버레이션지속, true);
                    pc.setCurrentMp(pc.getCurrentMp() - 20);
                    SC_SkillSound.send(pc, pc.getId(), 12335);
                    SC_SkillSound.broadcast(pc, pc.getId(), 12335);
                    pc.sendPackets("리버레이션: 상태 이상 스킬의 시간이 감소됩니다.");
                    L1SkillUse.cast(pc, L1SkillId.보조버프, pc.getId(), pc.getX(), pc.getY(), null, 0, L1SkillUse.TYPE_SPELLSC);
                });
            } else {
                pc.sendPackets("현재 [리버레이션]을 사용할 수 없는 조건입니다");
            }
    }
}
