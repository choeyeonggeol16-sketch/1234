package l1j.server.server.model.item.function;

import static l1j.server.server.model.character.skill.L1SkillId.COUNTER_MAGIC;
import static l1j.server.server.model.item.function.CreatureItemActions.*;

import l1j.server.Config;
import l1j.server.server.ActionCodes;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Location;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.CParty;
import l1j.server.server.model.inventory.CUserInventory;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.item.teleport.L1Teleport;
import l1j.server.server.model.network.core.sc.packet.SC_AttackPacket;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.utils.GameUtils;

/** Item-specific effect extracted without changing its original execution body. */
final class CallAdvanceItemEffect {
    private CallAdvanceItemEffect() {}

    public static boolean 콜어드밴스(L1PcInstance pc, int targetId, L1ItemInstance item) {
        if (pc == null || item == null || item.getItemId() != 70721
                || pc.getInventory().GetByServerID(item.getId()) != item
                || pc.isDead() || pc.get_teleport()) return false;
        SC_AttackPacket.send(pc.getNetConnection(), pc, 0, ActionCodes.ACTION_Wand);
        SC_AttackPacket.broadcast(pc, 0, ActionCodes.ACTION_Wand);
        L1Object target = CWorld.getInstance().findObject(targetId);
        CParty party = pc.getParty();
        if ((pc.getType() == 0 && pc.getLevel() >= Config.어드밴스레벨) || pc.isGm()) {
            if (target != null) {
                if (party != null) {
                    if (target instanceof L1PcInstance) {
                        L1PcInstance member = (L1PcInstance)target;
                        if (member.getMapId() != pc.getMapId() || member.isDead() || member.get_teleport()) return false;
                        int diffLocX = Math.abs(pc.getX() - member.getX());
                        int diffLocY = Math.abs(pc.getY() - member.getY());
                        if (diffLocX > 8 || diffLocY > 8) {
                            SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "\\fY거리가 멀어서 사용할 수 없습니다.");
                            SC_SystemMessage.send(pc, "\\fY거리가 멀어서 사용할 수 없습니다.");
                            return false;
                        }
                        if (pc.hasSkillEffect(L1SkillId.어드밴스대기)) {
                            int n = pc.getSkillEffectTimeSec(L1SkillId.어드밴스대기);
                            SC_SystemMessage.send(pc, "\\fY콜 클랜 어드밴스 대기시간 : " + n + "초 후에 사용가능합니다.");
                            return false;
                        }
                        if (GameUtils.isNonAction(pc)) {
                            return false;
                        }
                        if (pc.isInvisble()) {
                            SC_SystemMessage.send(pc, "콜 클랜 어드밴스는 투명상태로 시전이 불가능합니다.");
                            return false;
                        }
                        if (!pc.getInventory().HaveItemByClassId(41246, 200)) {
                            SC_SystemMessage.send(pc, "결정체가 부족합니다.");
                            return false;
                        }
                        if (pc.getClanid() == 0) {
                            SC_SystemMessage.send(pc, "혈맹을 창설후에 사용가능합니다.");
                            return false;
                        }
                        if (pc.getCurrentMp() < 20) {
                            SC_ServerMessage.send(pc, 278);
                            return false;
                        }
                        if (pc.getId() == member.getId()) {
                            SC_SystemMessage.send(pc, "\\aH자기 자신에게는 사용할 수 없습니다");
                            return false;
                        }
                        if (pc.getClanid() != member.getClanid()) {
                            SC_SystemMessage.send(pc, "같은 혈원만 소환 가능합니다.");
                            return false;
                        }
                        int tx = member.getX();
                        int ty = member.getY();
                        if (!pc.getInventory().tryConsumeCombatReagent(41246, 200)) return false;
                        if (!pc.glanceCheck(tx, ty) || !member.glanceCheck(pc.getX(), pc.getY())) {
                            L1Location loc = L1Teleport.getInstance().소환텔레포트(pc, 1);
                            member.어드밴스(loc.getX(), loc.getY(), loc.getMapId(), member.getHeading(), 169, false, true);
                        } else {
                            L1Location loc = L1Teleport.getInstance().소환텔레포트(pc, -1);
                            member.어드밴스(loc.getX(), loc.getY(), loc.getMapId(), member.getHeading(), 169, false, true);
                        }
                        SC_SystemMessage.send(member, "\\aH군주가 당신을 소환하였습니다.");
                        SC_SkillSound.send(member, member.getId(), 12261);
                        SC_SkillSound.broadcast(member, member.getId(), 12261);
                        SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "\\f:콜 클랜 어드밴스로 혈맹원: [" + member.getName() + "]님을 소환하였습니다");
                        pc.setSkillEffect(L1SkillId.어드밴스대기, Config.어드밴스대기 * 1000);
                        L1SkillUse.cast(pc, L1SkillId.보조버프, pc.getId(), pc.getX(), pc.getY(), null, 0, L1SkillUse.TYPE_SPELLSC);
                        pc.setCurrentMp(pc.getCurrentMp() - 20);
                        pc.getInventory().updateItem(item, CUserInventory.COL_CHARGE_COUNT);
                        return true;
                    } else {
                        SC_SystemMessage.send(pc, "타겟이 올바르지않습니다.");
                    }
                } else {
                    SC_SystemMessage.send(pc, "파티가 아니거나 화면내에 파티에 속해있는 혈맹원들이 없습니다.");
                }
            } else {
                SC_SystemMessage.send(pc, "타겟이 올바르지않습니다.");
            }
        } else {
            pc.sendPackets("\\aH조건에 맞지 않아 사용할 수 없는 스킬입니다.");
            pc.sendPackets("\\aH조건: 군주      레벨: " + Config.어드밴스레벨 + " 이상");
        }
        return false;
    }
}
