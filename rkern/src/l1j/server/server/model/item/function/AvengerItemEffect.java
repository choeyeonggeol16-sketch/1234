package l1j.server.server.model.item.function;

import static l1j.server.server.model.character.skill.L1SkillId.COUNTER_MAGIC;
import static l1j.server.server.model.item.function.CreatureItemActions.*;


import l1j.server.Config;
import l1j.server.server.ActionCodes;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.network.core.sc.packet.SC_AttackPacket;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.shared.GameRandom;
import l1j.server.server.utils.GameUtils;

/** Item-specific effect extracted without changing its original execution body. */
final class AvengerItemEffect {
    private AvengerItemEffect() {}

    public static boolean 어벤저(L1PcInstance pc, int targetId, L1ItemInstance item) {
        if (pc == null || item == null || item.getItemId() != 707779
                || pc.getInventory().GetByServerID(item.getId()) != item
                || pc.isDead() || pc.get_teleport()) return false;
        SC_AttackPacket.send(pc.getNetConnection(), pc, 0, ActionCodes.ACTION_Wand);
        SC_AttackPacket.broadcast(pc, 0, ActionCodes.ACTION_Wand);
        L1Object target = CWorld.getInstance().findObject(targetId);
        int rnd = GameRandom.nextInt(100) + 1;
        if ((pc.getType() == 4 && pc.getLevel() >= 85) || pc.isGm()) {
            if (target != null) {
                if (target instanceof L1PcInstance) {
                    L1PcInstance member = (L1PcInstance)target;
                    if (member.getMapId() != pc.getMapId() || member.isDead() || member.get_teleport()) return false;
                    int aa1 = GameRandom.nextInt(100) + 1;
                    int aa = member.getAc() / 2;
                    int dmg = (pc.getLevel() / 3 * Config.어벤저배율) + aa1;
                    if (pc.getId() == member.getId()) {
                        SC_SystemMessage.send(pc, "\\aH자기 자신에게는 사용할 수 없습니다");
                        return false;
                    }
                    int diffLocX = Math.abs(pc.getX() - member.getX());
                    int diffLocY = Math.abs(pc.getY() - member.getY());
                    if (diffLocX > 1 || diffLocY > 1) {
                        SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "\\fY거리가 멀어서 사용할 수 없습니다.");
                        SC_SystemMessage.send(pc, "\\fY거리가 멀어서 사용할 수 없습니다.");
                        return false;
                    }
                    if (pc.hasSkillEffect(L1SkillId.어벤저대기)) {
                        int n = pc.getSkillEffectTimeSec(L1SkillId.어벤저대기);
                        SC_SystemMessage.send(pc, "\\fY어벤저 대기시간 : " + n + "초 후에 사용가능합니다.");
                        return false;
                    }
                    if (pc.getInventory().getTypeEquipped(1, 1) >= 1 || pc.getInventory().getTypeEquipped(1, 3) >= 1 || pc.getInventory().getTypeEquipped(1, 2) >= 1 || pc.getInventory().getTypeEquipped(1, 4) >= 1 || pc.getInventory().getTypeEquipped(1, 5) >= 1 || pc.getInventory().getTypeEquipped(1, 6) >= 1 || pc.getInventory().getTypeEquipped(1, 7) >= 1 || pc.getInventory().getTypeEquipped(1, 8) >= 1 || pc.getInventory().getTypeEquipped(1, 10) >= 1 || pc.getInventory().getTypeEquipped(1, 13) >= 1 || pc.getInventory().getTypeEquipped(1, 14) >= 1 || pc.getInventory().getTypeEquipped(1, 17) >= 1 || pc.getInventory().getTypeEquipped(1, 15) >= 1 || pc.getInventory().getTypeEquipped(1, 18) >= 1 || pc.getInventory().getTypeEquipped(1, 16) >= 1) {
                        SC_SystemMessage.send(pc, "사용조건(다크엘프): 이도류, 크로우 중 착용");
                        return false;
                    }
                    if (GameUtils.isNonAction(pc)) {
                        return false;
                    }
                    if (pc.getSafetyZone() && (!(pc.getMapId() == 9 || pc.getMapId() == 1 || pc.getMapId() == 2))) {
                        SC_SystemMessage.send(pc, "세이프티존에서는 사용할 수 없습니다.");
                        return false;
                    }
                    if (pc.isInvisble()) {
                        SC_SystemMessage.send(pc, "어벤저는 투명상태로 시전이 불가능합니다.");
                        return false;
                    }
                    if (!pc.getInventory().HaveItemByClassId(40321, 2)) {
                        SC_SystemMessage.send(pc, "마법 재료가 부족합니다.");
                        return false;
                    }
                    if (pc.getCurrentMp() < 25) {
                        SC_ServerMessage.send(pc, 278);
                        return false;
                    }
                    if (!pc.getInventory().tryConsumeCombatReagent(40321, 2)) return false;
                    if (member.hasSkillEffect(COUNTER_MAGIC)) {
                        member.removeSkillEffect(COUNTER_MAGIC);
                        SC_SkillSound.broadcast(member, member.getId(), 10702);
                        SC_SkillSound.send(member, member.getId(), 10702);
                    } else {
                        if (member.getCurrentHp() <= member.getMaxHp() * 0.3) {
                            if (rnd <= Config.어벤저확률) {
                                if (!member.isGm()) member.death(pc, true);
                                if (pc.isGm()) {
                                    SC_SystemMessage.send(pc, "\\aH어벤져 확률: [" + rnd + "]  [1~" + Config.어벤저확률 + "]");
                                }
                            } else {
                                if (aa <= 0) {
                                    dmg += aa;
                                }
                                if (dmg < 100) {
                                    dmg = 100;
                                }
                                member.receiveDamage(pc, dmg);
                                if (pc.isGm()) {
                                    SC_SystemMessage.send(pc, "\\aH어벤져 데미지: " + dmg);
                                }
                            }
                        } else {
                            if (aa <= 0) {
                                dmg += aa;
                            }
                            if (dmg < 100) {
                                dmg = 100;
                            }
                            member.receiveDamage(pc, dmg);
                        }
                        if (pc.isGm()) {
                            SC_SystemMessage.send(pc, "\\aH어벤져 데미지: " + dmg);
                        }
                        SC_SkillSound.send(member, member.getId(), 14545);
                        SC_SkillSound.broadcast(member, member.getId(), 14545);
                    }
                    pc.setSkillEffect(L1SkillId.어벤저대기, Config.어벤저대기 * 1000);
                    pc.setCurrentMp(pc.getCurrentMp() - 25);
                    L1SkillUse.cast(pc, L1SkillId.보조버프, pc.getId(), pc.getX(), pc.getY(), null, 0, L1SkillUse.TYPE_SPELLSC);
                    return true;
                } else {
                    SC_SystemMessage.send(pc, "\\aHPC에게만 가능합니다.");
                }
            } else {
                SC_SystemMessage.send(pc, "타겟이 올바르지않습니다.");
            }
        } else {
            pc.sendPackets("\\aH조건에 맞지 않아 사용할 수 없는 스킬입니다.");
            pc.sendPackets("\\aH조건:다크엘프      레벨: 85 이상");
        }
        return false;
    }
}
