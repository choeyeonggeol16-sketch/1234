package l1j.server.server.model.item.function;

import static l1j.server.server.model.character.skill.L1SkillId.EARTH_BIND;
import static l1j.server.server.model.character.skill.L1SkillId.SHOCK_STUN;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_FREEZE;
import static l1j.server.server.model.item.function.CreatureItemActions.*;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import l1j.server.Config;
import l1j.server.server.ActionCodes;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1EffectSpawn;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.network.core.sc.packet.SC_AttackPacket;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_Paralysis;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.utils.GameUtils;
import l1j.server.server.utils.eKind;

/** Item-specific effect extracted without changing its original execution body. */
final class BlazeStunItemEffect {
    private static final Random _random = CreatureItemActions._random;

    private BlazeStunItemEffect() {}

    public static void 블레이즈스턴(L1PcInstance pc, int targetId, L1ItemInstance item) {
        SC_AttackPacket.send(pc.getNetConnection(), pc, 0, ActionCodes.ACTION_Wand);
        SC_AttackPacket.broadcast(pc, 0, ActionCodes.ACTION_Wand);
        L1Object target = L1World.getInstance().findObject(targetId);
        Random random = ThreadLocalRandom.current();
        if ((pc.getType() == 1 && pc.getLevel() >= 50) || pc.isGm()) {
            if (target != null) {
                if (target instanceof L1PcInstance) {
                    L1PcInstance member = (L1PcInstance)target;
                    int diffLocX = Math.abs(pc.getX() - member.getX());
                    int diffLocY = Math.abs(pc.getY() - member.getY());
                    if (diffLocX > 7 || diffLocY > 7) {
                        SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "\\fY거리가 멀어서 사용할 수 없습니다.");
                        SC_SystemMessage.send(pc, "\\fY거리가 멀어서 사용할 수 없습니다.");
                        return;
                    }
                    if (pc.getId() == member.getId()) {
                        SC_SystemMessage.send(pc, "\\aH자기 자신에게는 사용할 수 없습니다");
                        return;
                    }
                    int tx = member.getX();
                    int ty = member.getY();
                    if (!pc.glanceCheck(tx, ty) || !member.glanceCheck(pc.getX(), pc.getY())) {
                        SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "\\fY타겟과의 사이에 장애물로 인해 사용할 수 없습니다.");
                        SC_SystemMessage.send(pc, "\\fY타겟과의 사이에 장애물로 인해 사용할 수 없습니다.");
                        return;
                    }
                    if (pc.hasSkillEffect(L1SkillId.엘븐스트라이크대기)) {
                        int n = pc.getSkillEffectTimeSec(L1SkillId.엘븐스트라이크대기);
                        SC_SystemMessage.send(pc, "\\fY" + n + "초 후에 사용가능합니다.");
                        return;
                    }
                    if (GameUtils.isNonAction(pc)) {
                        return;
                    }
                    if (pc.hasSkillEffect(L1SkillId.SHOCK_STUN) || pc.hasSkillEffect(L1SkillId.EMPIRE) || pc.hasSkillEffect(L1SkillId.EARTH_BIND) || pc.hasSkillEffect(L1SkillId.BONE_BREAK)) {
                        return;
                    }
                    if (member.hasSkillEffect(L1SkillId.SHOCK_STUN) || member.hasSkillEffect(L1SkillId.EMPIRE) || member.hasSkillEffect(L1SkillId.EARTH_BIND) || member.hasSkillEffect(L1SkillId.BONE_BREAK)) {
                        return;
                    }
                    if (pc.getSafetyZone() && (!(pc.getMapId() == 9 || pc.getMapId() == 1 || pc.getMapId() == 2))) {
                        SC_SystemMessage.send(pc, "세이프티존에서는 사용할 수 없습니다.");
                        return;
                    }
                    if (pc.isInvisble()) {
                        SC_SystemMessage.send(pc, "투명상태로 시전이 불가능합니다.");
                        return;
                    }
                    if (pc.getCurrentMp() < 20) {
                        SC_ServerMessage.send(pc, 278);
                        return;
                    }
                    int 랜덤 = _random.nextInt(100) + 1;
                    int 렙차 = pc.getLevel() - member.getLevel();
                    int 기본확률 = Config.ngsdefault;
                    int 적중 = pc.getResistance().getSpecialPierce(eKind.ABILITY) + pc.getResistance().getSpecialPierce(eKind.ALL);
                    int 내성 = member.getResistance().getSpecialResistance(eKind.ABILITY) + member.getResistance().getSpecialResistance(eKind.ALL);
                    int stunpoint = 0;
                    int 양렙차 = (렙차 * Config.ngslvpoint);
                    int 음렙차 = (렙차 * Config.ngslvpoint);
                    int aa1 = random.nextInt(100) + 1;
                    int aa = member.getAC().getAc() / 2;
                    int probability = 0;
                    int dmg = (pc.getLevel() / 3 * Config.엘븐스트라이크배율) + aa1;
                    if (적중 > 0) {
                        stunpoint += Math.round(((double)적중 * (double)Config.ngstunpoint));
                    }
                    probability = (int)+Config.ngsdefault + stunpoint;
                    probability += 양렙차;
                    probability -= 내성;
                    final int 엘븐스트라이크시간 = DurationChoiceItems.blaze(random);
                    SC_SkillSound.send(pc, member.getId(), 5146);
                    SC_SkillSound.broadcast(pc, member.getId(), 5146);
                    pc.setCurrentMp(pc.getCurrentMp() - 20);
                    if (렙차 >= 0) {
                        if (랜덤 <= probability) {
                            if (probability > 100) {
                                probability = 100;
                            }
                            int 요정 = (int)((double)엘븐스트라이크시간 * Config.리버레이션절감);
                            if (member.hasSkillEffect(L1SkillId.리버레이션)) {
                                L1EffectSpawn.getInstance().spawnEffect(460000353, 요정, member.getX(), member.getY(), member.getMapId());
                                member.setSkillEffect(L1SkillId.SHOCK_STUN, 요정);
                                SC_Paralysis.send(member, SC_Paralysis.TYPE_STUN, true);
                                pc.setSkillEffect(L1SkillId.엘븐스트라이크대기, 15 * 1000);
                            } else {
                                L1EffectSpawn.getInstance().spawnEffect(460000353, 엘븐스트라이크시간, member.getX(), member.getY(), member.getMapId());
                                member.setSkillEffect(L1SkillId.SHOCK_STUN, 엘븐스트라이크시간);
                                pc.setSkillEffect(L1SkillId.엘븐스트라이크대기, 15 * 1000);
                            }
                            SC_Paralysis.send(member, SC_Paralysis.TYPE_STUN, true);
                        } else {
                            pc.setSkillEffect(L1SkillId.엘븐스트라이크대기, Config.엘븐스트라이크대기 * 1000);
                            pc.sendPackets("마법 사용: 실패(성공하지 못함)");
                        }
                        if (pc.isGm()) {
                            pc.sendPackets("스턴: 확률[" + 랜덤 + "]   렙차[+" + 렙차 + "*" + Config.엘븐스트라이크양 + "+" + 기본확률 + "] = 확률범위[1~" + 양렙차 + "]");
                        }
                    } else {
                        if (랜덤 <= probability) {
                            if (probability < 30) {
                                probability = 30;
                            }
                            int 요정 = (int)((double)엘븐스트라이크시간 * Config.리버레이션절감);
                            if (member.hasSkillEffect(L1SkillId.리버레이션)) {
                                L1EffectSpawn.getInstance().spawnEffect(460000353, 요정, member.getX(), member.getY(), member.getMapId());
                                member.setSkillEffect(L1SkillId.SHOCK_STUN, 요정);
                                pc.setSkillEffect(L1SkillId.엘븐스트라이크대기, 15 * 1000);
                            } else {
                                L1EffectSpawn.getInstance().spawnEffect(460000353, 엘븐스트라이크시간, member.getX(), member.getY(), member.getMapId());
                                member.setSkillEffect(L1SkillId.SHOCK_STUN, 엘븐스트라이크시간);
                                pc.setSkillEffect(L1SkillId.엘븐스트라이크대기, 15 * 1000);
                            }
                            SC_Paralysis.send(member, SC_Paralysis.TYPE_STUN, true);
                        } else {
                            pc.sendPackets("마법 사용: 실패(성공하지 못함)");
                            pc.setSkillEffect(L1SkillId.엘븐스트라이크대기, Config.엘븐스트라이크대기 * 1000);
                        }
                        if (pc.isGm()) {
                            pc.sendPackets("블레이드 스턴: 확률[" + 랜덤 + "]   렙차[-" + 렙차 + "*" + Config.엘븐스트라이크음 + "+" + 기본확률 + "] = 확률범위[1~" + 음렙차 + "]");
                        }
                    }
                    L1SkillUse.cast(pc, L1SkillId.보조버프, pc.getId(), pc.getX(), pc.getY(), null, 0, L1SkillUse.TYPE_SPELLSC);
                    if (aa <= 0) {
                        dmg += aa;
                    }
                    if (dmg < 100) {
                        dmg = 100;
                    }
                    member.receiveDamage(pc, dmg);
                }
                if (target instanceof L1NpcInstance) {
                    SC_SystemMessage.send(pc, "타겟이 올바르지않습니다.");
                    return;
                }
            } else {
                SC_SystemMessage.send(pc, "타겟이 올바르지않습니다.");
            }
        } else {
            pc.sendPackets("\\aH조건에 맞지 않아 사용할 수 없는 스킬입니다.");
        }
    }
}
