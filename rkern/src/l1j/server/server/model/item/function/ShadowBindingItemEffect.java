package l1j.server.server.model.item.function;

import static l1j.server.server.model.character.skill.L1SkillId.STATUS_FREEZE;
import static l1j.server.server.model.item.function.CreatureItemActions.*;


import l1j.server.Config;
import l1j.server.server.ActionCodes;
import l1j.server.server.model.Instance.L1EffectInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.world.effect.L1EffectSpawn;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_AttackPacket;
import l1j.server.server.model.network.core.sc.packet.SC_EffectLocation;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_Paralysis;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.shared.GameRandom;
import l1j.server.server.utils.GameUtils;

/** Item-specific effect extracted without changing its original execution body. */
final class ShadowBindingItemEffect {

    private ShadowBindingItemEffect() {}

    public static void 쉐도우(L1PcInstance pc, int targetId, L1ItemInstance item) {
        SC_AttackPacket.send(pc.getNetConnection(), pc, 0, ActionCodes.ACTION_Wand);
        SC_AttackPacket.broadcast(pc, 0, ActionCodes.ACTION_Wand);
        L1Object target = CWorld.getInstance().findObject(targetId);
        if ((pc.getType() == 4 && pc.getLevel() >= 80) || pc.isGm()) {
            if (target != null) {
                if (target instanceof L1PcInstance) {
                    L1PcInstance member = (L1PcInstance)target;
                    if (pc.getId() == member.getId()) {
                        SC_SystemMessage.send(pc, "\\aH자기 자신에게는 사용할 수 없습니다");
                        return;
                    }
                    int diffLocX = Math.abs(pc.getX() - member.getX());
                    int diffLocY = Math.abs(pc.getY() - member.getY());
                    if (diffLocX > 5 || diffLocY > 5) {
                        SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "\\fY거리가 멀어서 사용할 수 없습니다.");
                        SC_SystemMessage.send(pc, "\\fY거리가 멀어서 사용할 수 없습니다.");
                        return;
                    }
                    if (pc.hasSkillEffect(L1SkillId.쉐도우대기)) {
                        int n = pc.getSkillEffectTimeSec(L1SkillId.쉐도우대기);
                        SC_SystemMessage.send(pc, "\\fY쉐도우스탭 대기시간 : " + n + "초 후에 사용가능합니다.");
                        return;
                    }
                    if (GameUtils.isNonAction(pc) || pc.hasSkillEffect(L1SkillId.STATUS_FREEZE)) {
                        return;
                    }
                    if (pc.getSafetyZone() && (!(pc.getMapId() == 9 || pc.getMapId() == 1 || pc.getMapId() == 2))) {
                        SC_SystemMessage.send(pc, "세이프티존에서는 사용할 수 없습니다.");
                        return;
                    }
                    if (pc.isInvisble()) {
                        SC_SystemMessage.send(pc, "쉐도우 스텝은 투명상태로 시전이 불가능합니다.");
                        return;
                    }
                    if (pc.getCurrentMp() < 15) {
                        SC_ServerMessage.send(pc, 278);
                        return;
                    }
                    int tx = member.getX();
                    int ty = member.getY();
                    if (!pc.glanceCheck(tx, ty) || !member.glanceCheck(pc.getX(), pc.getY())) {
                        SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "\\fY타겟과의 사이에 장애물로 인해 사용할 수 없습니다.");
                        SC_SystemMessage.send(pc, "\\fY타겟과의 사이에 장애물로 인해 사용할 수 없습니다.");
                        return;
                    }
                    int 랜덤 = GameRandom.nextInt(100) + 1;
                    int 렙차 = pc.getLevel() - member.getLevel();
                    int 기본확률 = Config.쉐도우기본;
                    int 양렙차 = 기본확률 + (렙차 * Config.쉐도우양);
                    int 음렙차 = 기본확률 + (렙차 * Config.쉐도우음);
                    pc.start_teleport(member.getX(), member.getY(), member.getMapId(), pc.getHeading(), 169, false, false);
                    pc.setSkillEffect(L1SkillId.쉐도우대기, Config.쉐도우대기 * 1000);
                    pc.setCurrentMp(pc.getCurrentMp() - 15);
                    SC_EffectLocation.send(pc, pc.getX(), pc.getY(), 2235);
                    SC_EffectLocation.broadcast(pc, pc.getX(), pc.getY(), 2235);
                    if (렙차 >= 0) {
                        if (랜덤 <= 양렙차) {
                            if (양렙차 > 100) {
                                양렙차 = 100;
                            }
                            int 요정 = (int)((double)Config.쉐도우제약 * Config.리버레이션절감);
                            if (member.hasSkillEffect(L1SkillId.리버레이션)) {
                                member.setSkillEffect(STATUS_FREEZE, 요정);
                                L1EffectSpawn.getInstance().spawnEffect(771162, 요정, member.getX(), member.getY(), member.getMapId());
                            } else {
                                member.setSkillEffect(STATUS_FREEZE, Config.쉐도우제약);
                                L1EffectSpawn.getInstance().spawnEffect(771162, Config.쉐도우제약, member.getX(), member.getY(), member.getMapId());
                            }
                            SC_Paralysis.send(member, SC_Paralysis.TYPE_BIND, true);
                        } else {
                            pc.sendPackets("마법 사용: 실패(성공하지 못함)");
                        }
                        if (pc.isGm()) {
                            pc.sendPackets("쉐도우스텝: 확률[" + 랜덤 + "]   렙차[+" + 렙차 + "*" + Config.쉐도우양 + "+" + 기본확률 + "] = 확률범위[1~" + 양렙차 + "]");
                        }
                    } else {
                        if (랜덤 <= 음렙차) {
                            if (음렙차 < 30) {
                                음렙차 = 30;
                            }
                            int 요정 = (int)((double)Config.쉐도우제약 * Config.리버레이션절감);
                            if (member.hasSkillEffect(L1SkillId.리버레이션)) {
                                member.setSkillEffect(STATUS_FREEZE, 요정);
                                L1EffectSpawn.getInstance().spawnEffect(771162, 요정, member.getX(), member.getY(), member.getMapId());
                            } else {
                                member.setSkillEffect(STATUS_FREEZE, Config.쉐도우제약);
                                L1EffectSpawn.getInstance().spawnEffect(771162, Config.쉐도우제약, member.getX(), member.getY(), member.getMapId());
                            }
                            SC_Paralysis.send(member, SC_Paralysis.TYPE_BIND, true);
                        } else {
                            pc.sendPackets("마법 사용: 실패(성공하지 못함)");
                        }
                        if (pc.isGm()) {
                            pc.sendPackets("쉐도우스텝: 확률[" + 랜덤 + "]   렙차[-" + 렙차 + "*" + Config.쉐도우음 + "+" + 기본확률 + "] = 확률범위[1~" + 음렙차 + "]");
                        }
                    }
                }
                if (target instanceof L1NpcInstance) {
                    L1NpcInstance member1 = (L1NpcInstance)target;
                    int diffLocX1 = Math.abs(pc.getX() - member1.getX());
                    int diffLocY1 = Math.abs(pc.getY() - member1.getY());
                    if (diffLocX1 > 5 || diffLocY1 > 5) {
                        SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "\\fY거리가 멀어서 사용할 수 없습니다.");
                        SC_SystemMessage.send(pc, "\\fY거리가 멀어서 사용할 수 없습니다.");
                        return;
                    }
                    if (pc.getInventory().getTypeEquipped(1, 1) >= 1 || pc.getInventory().getTypeEquipped(1, 3) >= 1 || pc.getInventory().getTypeEquipped(1, 2) >= 1 || pc.getInventory().getTypeEquipped(1, 4) >= 1 || pc.getInventory().getTypeEquipped(1, 5) >= 1 || pc.getInventory().getTypeEquipped(1, 6) >= 1 || pc.getInventory().getTypeEquipped(1, 7) >= 1 || pc.getInventory().getTypeEquipped(1, 8) >= 1 || pc.getInventory().getTypeEquipped(1, 10) >= 1 || pc.getInventory().getTypeEquipped(1, 13) >= 1 || pc.getInventory().getTypeEquipped(1, 14) >= 1 || pc.getInventory().getTypeEquipped(1, 17) >= 1 || pc.getInventory().getTypeEquipped(1, 15) >= 1 || pc.getInventory().getTypeEquipped(1, 18) >= 1 || pc.getInventory().getTypeEquipped(1, 16) >= 1) {
                        SC_SystemMessage.send(pc, "사용조건(다크엘프): 이도류, 크로우 중 착용");
                        return;
                    }
                    if (pc.getInventory().getTypeEquipped(1, 1) >= 1 || pc.getInventory().getTypeEquipped(1, 3) >= 1 || pc.getInventory().getTypeEquipped(1, 2) >= 1 || pc.getInventory().getTypeEquipped(1, 4) >= 1 || pc.getInventory().getTypeEquipped(1, 5) >= 1 || pc.getInventory().getTypeEquipped(1, 6) >= 1 || pc.getInventory().getTypeEquipped(1, 7) >= 1 || pc.getInventory().getTypeEquipped(1, 8) >= 1 || pc.getInventory().getTypeEquipped(1, 10) >= 1 || pc.getInventory().getTypeEquipped(1, 13) >= 1 || pc.getInventory().getTypeEquipped(1, 14) >= 1 || pc.getInventory().getTypeEquipped(1, 17) >= 1 || pc.getInventory().getTypeEquipped(1, 15) >= 1 || pc.getInventory().getTypeEquipped(1, 18) >= 1 || pc.getInventory().getTypeEquipped(1, 16) >= 1) {
                        SC_SystemMessage.send(pc, "사용조건(다크엘프): 이도류, 크로우 중 착용");
                        return;
                    }
                    if (pc.hasSkillEffect(L1SkillId.쉐도우대기)) {
                        int n = pc.getSkillEffectTimeSec(L1SkillId.쉐도우대기);
                        SC_SystemMessage.send(pc, "\\fY쉐도우스탭 대기시간 : " + n + "초 후에 사용가능합니다.");
                        return;
                    }
                    if (GameUtils.isNonAction(pc) || pc.hasSkillEffect(L1SkillId.STATUS_FREEZE)) {
                        return;
                    }
                    if (pc.isInvisble()) {
                        SC_SystemMessage.send(pc, "쉐도우 스텝은 투명상태로 시전이 불가능합니다.");
                        return;
                    }
                    if (pc.getCurrentMp() < 15) {
                        SC_ServerMessage.send(pc, 278);
                        return;
                    }
                    int tx1 = member1.getX();
                    int ty1 = member1.getY();
                    if (!pc.glanceCheck(tx1, ty1) || !member1.glanceCheck(pc.getX(), pc.getY())) {
                        SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "\\fY타겟과의 사이에 장애물로 인해 사용할 수 없습니다.");
                        SC_SystemMessage.send(pc, "\\fY타겟과의 사이에 장애물로 인해 사용할 수 없습니다.");
                        return;
                    }
                    pc.start_teleport(member1.getX(), member1.getY(), member1.getMapId(), pc.getHeading(), 169, false, false);
                    SC_EffectLocation.send(pc, pc.getX(), pc.getY(), 2235);
                    SC_EffectLocation.broadcast(pc, pc.getX(), pc.getY(), 2235);
                    SC_SkillSound.send(member1, member1.getId(), 12531);
                    SC_SkillSound.broadcast(member1, member1.getId(), 12531);
                    pc.setCurrentMp(pc.getCurrentMp() - 15);
                    pc.setSkillEffect(L1SkillId.쉐도우대기, Config.쉐도우대기 * 1000);
                    int qq = Config.쉐도우몬스터;
                    int 랜덤1 = GameRandom.nextInt(100) + 1;
                    if (랜덤1 <= qq) {
                        member1.setSkillEffect(STATUS_FREEZE, Config.쉐도우제약);
                        SC_Paralysis.send(member1, SC_Paralysis.TYPE_BIND, true);
                        @SuppressWarnings("unused")
                        L1EffectInstance eff12 = L1EffectSpawn.getInstance().spawnEffect(771162, Config.쉐도우제약, member1.getX(), member1.getY(), member1.getMapId());
                    } else {
                    }
                    if (pc.isGm()) {
                        pc.sendPackets("제약: 확률[" + 랜덤1 + "]  확률범위[1~" + Config.쉐도우몬스터 + "]");
                    }
                }
            } else {
                SC_SystemMessage.send(pc, "타겟이 올바르지않습니다.");
            }
        } else {
            pc.sendPackets("\\aH조건에 맞지 않아 사용할 수 없는 스킬입니다.");
            pc.sendPackets("\\aH조건:다크엘프      레벨: 80 이상");
        }
    }
}
