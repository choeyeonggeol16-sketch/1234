package l1j.server.server.model.item.function;

import l1j.server.server.model.network.core.sc.packet.SC_Paralysis;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharAttrDef;
import l1j.server.server.model.network.core.sc.packet.SC_SPMR;
import l1j.server.server.model.network.core.sc.packet.SC_MpUpdate;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_HpUpdate;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_FREEZE;
import l1j.server.Config;
import l1j.server.MJWarSystem.MJCastleWarBusiness;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1EffectSpawn;
import l1j.server.server.model.L1Party;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1EffectInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_SPELL_BUFF_NOTI;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_SPELL_BUFF_NOTI.eDurationShowType;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_SPELL_BUFF_NOTI.eNotiType;
import l1j.server.server.model.network.core.sc.packet.SC_InventoryIcon;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.utils.MJCommons;
import l1j.server.server.utils.eKind;

/** Consumable active skill for item ID 70710. */
final class HalphasSkillConsumable {
    private HalphasSkillConsumable() {}

    static void use(L1PcInstance pc) {
            if (pc.getType() == 5 && pc.getLevel() >= 1) {
                if (pc.hasSkillEffect(L1SkillId.할파스대기시간)) {
                    int n = pc.getSkillEffectTimeSec(L1SkillId.할파스대기시간);
                    SC_SystemMessage.send(pc, "\\fY할파스 대기시간 : " + n + "초 후에 사용가능합니다.");
                    return;
                }
                if (MJCommons.isNonAction(pc)) {
                    return;
                }
                if (pc.getInventory().getTypeEquipped(1, 18) < 1) {
                    SC_SystemMessage.send(pc, "체인소드를 착용 후 스킬시전이 가능합니다.");
                    return;
                }
                if (!pc.getInventory().checkItem(210035, 1)) {
                    pc.sendPackets("각인의 뼈조각이 부족합니다.");
                    return;
                }
                if (pc.getCurrentMp() < 18) {
                    pc.sendPackets("MP가 부족합니다.");
                    return;
                }
                DurableSkillReagentTransaction.useDurableSkillReagent(pc, 210035, 1, ()->{
                    if (pc.hasSkillEffect(L1SkillId.할파스)) {
                        pc.removeSkillEffect(L1SkillId.할파스);
                    }
                    pc.setSkillEffect(L1SkillId.할파스, 128 * 1000);
                    pc.setSkillEffect(L1SkillId.할파스대기시간, 20 * 1000);
                    pc.setCurrentMp(pc.getCurrentMp() - 18);
                    SC_SkillSound.send(pc, pc.getId(), 14455);
                    SC_SkillSound.broadcast(pc, pc.getId(), 14455);
                    SC_InventoryIcon.icoNew(pc, L1SkillId.할파스, 5384, 128, true);
                    L1SkillUse.cast(pc, L1SkillId.보조버프, pc.getId(), pc.getX(), pc.getY(), null, 0, L1SkillUse.TYPE_SPELLSC);
                    SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
                    pc.sendPackets("할파스: 일정 확률로 물리데미지를 피하며 반격합니다.");
                });
            } else {
                pc.sendPackets("현재 [할파스]를 사용할 수 없는 조건입니다");
            }
    }
}
