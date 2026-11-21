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

/** Consumable active skill for item ID 44001. */
final class BurningShotSkillConsumable {
    private BurningShotSkillConsumable() {}

    static void use(L1PcInstance pc) {
            if (pc.hasSkillEffect(L1SkillId.버닝샷대기)) {
                int n = pc.getSkillEffectTimeSec(L1SkillId.버닝샷대기);
                SC_SystemMessage.send(pc, "\\fY버닝샷 대기시간 : " + n + "초 후에 사용가능합니다.");
                return;
            }
            if (MJCommons.isNonAction(pc)) {
                return;
            }
            if (pc.isInvisble()) {
                SC_SystemMessage.send(pc, "버닝샷은 투명상태로 시전이 불가능합니다.");
                return;
            }
            if (!pc.getInventory().checkItem(40319, 2)) {
                SC_SystemMessage.send(pc, "마법재료가 부족합니다.");
                return;
            }
            if (pc.getType() == 2 && pc.getLevel() >= 85) {
                if (pc.getInventory().getTypeEquipped(1, 20) >= 1 || pc.getInventory().getTypeEquipped(1, 4) >= 1) {
                    DurableSkillReagentTransaction.useDurableSkillReagent(pc, 40319, 2, ()->{
                        if (pc.hasSkillEffect(L1SkillId.버닝샷)) {
                            pc.removeSkillEffect(L1SkillId.버닝샷);
                        }
                        int A = (int)((double)Config.버닝샷대미지 * 100);
                        pc.setSkillEffect(L1SkillId.버닝샷대기, Config.버닝샷대기 * 1000);
                        pc.setSkillEffect(L1SkillId.버닝샷, Config.버닝샷 * 1000);
                        SC_InventoryIcon.icoNew(pc, L1SkillId.버닝샷, 6914, Config.버닝샷, true);
                        pc.setSkillEffect(STATUS_FREEZE, Config.버닝샷 * 1000);
                        SC_Paralysis.send(pc, SC_Paralysis.TYPE_BIND, true);
                        SC_SkillSound.send(pc, pc.getId(), 10909);
                        SC_SkillSound.broadcast(pc, pc.getId(), 10909);
                        pc.applySkillModifierFromOwner(L1SkillId.버닝샷, 0);
                        L1EffectInstance eff1 = L1EffectSpawn.getInstance().spawnEffect(881162, Config.버닝샷 * 1000, pc.getX(), pc.getY(), pc.getMapId());
                        SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
                        pc.sendPackets("버닝샷: 기본 데미지 +" + A + "%,전체내성 +3,PVP대미지감소+8,   지속시간 동안 이동 및 귀환 불가, 활 사거리 감소");
                        L1SkillUse.cast(pc, L1SkillId.보조버프, pc.getId(), pc.getX(), pc.getY(), null, 0, L1SkillUse.TYPE_SPELLSC);
                    });
                } else {
                    pc.sendPackets("활을 착용 시에 사용 가능합니다.");
                }
            } else {
                pc.sendPackets("현재 [버닝샷]을 사용할 수 없는 조건입니다");
            }
    }
}
