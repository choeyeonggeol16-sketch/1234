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

/** Consumable active skill for item ID 70706. */
final class BerserkSkillConsumable {
    private BerserkSkillConsumable() {}

    static void use(L1PcInstance pc) {
            if (pc.getType() == 7 && pc.getLevel() >= 1) {
                if (pc.getCurrentHp() <= 100) {
                    pc.sendPackets("HP가 부족합니다.");
                    return;
                }
                if (!pc.getInventory().checkItem(41246, 100)) {
                    pc.sendPackets("결정체가 부족합니다.");
                    return;
                }
                if (MJCommons.isNonAction(pc)) {
                    return;
                }
                if (pc.hasSkillEffect(L1SkillId.버서크대기)) {
                    int n = pc.getSkillEffectTimeSec(L1SkillId.버서크대기);
                    SC_SystemMessage.send(pc, "\\fY버서크 대기시간 : " + n + "초 후에 사용가능합니다.");
                    return;
                }
                DurableSkillReagentTransaction.useDurableSkillReagent(pc, 41246, 100, ()->{
                    if (pc.hasSkillEffect(L1SkillId.버서크)) {
                        pc.removeSkillEffect(L1SkillId.버서크);
                    }
                    pc.setSkillEffect(L1SkillId.버서크대기, Config.버서크대기시간 * 1000);
                    pc.setSkillEffect(L1SkillId.버서크, Config.버서크시간 * 1000);
                    SC_InventoryIcon.icoNew(pc, L1SkillId.버서크, 3155, Config.버서크시간, true);
                    SC_SkillSound.send(pc, pc.getId(), 8531);
                    SC_SkillSound.broadcast(pc, pc.getId(), 8531);
                    L1SkillUse.cast(pc, L1SkillId.보조버프, pc.getId(), pc.getX(), pc.getY(), null, 0, L1SkillUse.TYPE_SPELLSC);
                    pc.applySkillModifierFromOwner(L1SkillId.버서크, 0);
                    pc.setCurrentHp(pc.getCurrentHp() - 100);
                    pc.sendPackets("버서크 효과: 모든 내성 +20, 근거리대미지 +20 증가");
                });
            } else {
                pc.sendPackets("현재 버서크를 사용할 수 없는 조건입니다.");
                return;
            }
    }
}
