package l1j.server.server.model.item.function;

import l1j.server.server.model.network.core.sc.packet.SC_Paralysis;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import static l1j.server.server.model.character.skill.L1SkillId.EARTH_BIND;
import static l1j.server.server.model.character.skill.L1SkillId.SHOCK_STUN;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_FREEZE;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import l1j.server.Config;
import l1j.server.server.ActionCodes;
import l1j.server.server.model.L1EffectSpawn;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1PetInstance;
import l1j.server.server.model.Instance.L1SummonInstance;
import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.server.model.character.Attack.Helper.L1AttackHelper;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.network.core.sc.packet.SC_AttackPacket;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.utils.MJCommons;
import l1j.server.server.utils.eKind;
import static l1j.server.server.model.item.function.CreatureItemActions.*;

/** Item-specific effect extracted without changing its original execution body. */
final class ElvenStrikeItemEffect {
    private static final Random _random = CreatureItemActions._random;

    private ElvenStrikeItemEffect() {}

    public static boolean 엘븐스트라이크(L1PcInstance pc, int targetId, L1ItemInstance item) {
        // The acquired skill item is reusable; reject stale or mismatched input.
        if (pc == null || item == null || item.getItemId() != 70888
                || pc.getInventory().getItem(item.getId()) != item
                || pc.isDead() || pc.get_teleport()) return false;
        L1Object target = L1World.getInstance().findObject(targetId);
        if (!validTarget(pc, target)) return false;
        SC_AttackPacket.send(pc.getNetConnection(), pc, 0, ActionCodes.ACTION_Wand);
        SC_AttackPacket.broadcast(pc, 0, ActionCodes.ACTION_Wand);
        Random random = ThreadLocalRandom.current();
        int rnd = random.nextInt(100) + 1;
        if ((pc.getType() == 2 && pc.getLevel() >= 85) || pc.isGm()) {
            if (target != null) {
                if (target instanceof L1PcInstance) {
                    L1PcInstance member = (L1PcInstance)target;
                    int diffLocX = Math.abs(pc.getX() - member.getX());
                    int diffLocY = Math.abs(pc.getY() - member.getY());
                    if (diffLocX > 7 || diffLocY > 7) {
                        SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "\\fY거리가 멀어서 사용할 수 없습니다.");
                        SC_SystemMessage.send(pc, "\\fY거리가 멀어서 사용할 수 없습니다.");
                        return false;
                    }
                    if (pc.getId() == member.getId()) {
                        SC_SystemMessage.send(pc, "\\aH자기 자신에게는 사용할 수 없습니다");
                        return false;
                    }
                    int tx = member.getX();
                    int ty = member.getY();
                    if (!pc.glanceCheck(tx, ty) || !member.glanceCheck(pc.getX(), pc.getY())) {
                        SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "\\fY타겟과의 사이에 장애물로 인해 사용할 수 없습니다.");
                        SC_SystemMessage.send(pc, "\\fY타겟과의 사이에 장애물로 인해 사용할 수 없습니다.");
                        return false;
                    }
                    if (pc.hasSkillEffect(L1SkillId.엘븐스트라이크대기)) {
                        int n = pc.getSkillEffectTimeSec(L1SkillId.엘븐스트라이크대기);
                        SC_SystemMessage.send(pc, "\\fY엘븐스트라이크 대기시간 : " + n + "초 후에 사용가능합니다.");
                        return false;
                    }
                    if (MJCommons.isNonAction(pc)) {
                        return false;
                    }
                    if (pc.getSafetyZone() && (!(pc.getMapId() == 9 || pc.getMapId() == 1 || pc.getMapId() == 2))) {
                        SC_SystemMessage.send(pc, "세이프티존에서는 사용할 수 없습니다.");
                        return false;
                    }
                    if (pc.isInvisble()) {
                        SC_SystemMessage.send(pc, "엘븐스트라이크는 투명상태로 시전이 불가능합니다.");
                        return false;
                    }
                    if (!pc.getInventory().checkItem(40319, 2)) {
                        SC_SystemMessage.send(pc, "마법재료가 부족합니다.");
                        return false;
                    }
                    if (pc.getCurrentMp() < 20) {
                        SC_ServerMessage.send(pc, 278);
                        return false;
                    }
                    int 랜덤 = _random.nextInt(100) + 1;
                    int 렙차 = pc.getLevel() - member.getLevel();
                    int 기본확률 = Config.엘븐스트라이크기본;
                    int 양렙차 = 기본확률 + (렙차 * Config.엘븐스트라이크양);
                    int 음렙차 = 기본확률 + (렙차 * Config.엘븐스트라이크음);
                    int aa1 = random.nextInt(100) + 1;
                    int aa = member.getAC().getAc() / 2;
                    int dmg = (pc.getLevel() / 3 * Config.엘븐스트라이크배율) + aa1;
                    final int 엘븐스트라이크시간 = DurationChoiceItems.elven(random);
                    // Existing short inventory gate: a failed debit must not apply a skill.
                    if (!pc.getInventory().tryConsumeCombatReagent(40319, 2)) return false;
                    pc.setSkillEffect(L1SkillId.엘븐스트라이크대기, Config.엘븐스트라이크대기 * 1000);
                    pc.setCurrentMp(pc.getCurrentMp() - 20);
                    if (렙차 >= 0) {
                        if (랜덤 <= 양렙차) {
                            if (양렙차 > 100) {
                                양렙차 = 100;
                            }
                            int 요정 = (int)((double)엘븐스트라이크시간 * Config.리버레이션절감);
                            if (member.hasSkillEffect(L1SkillId.리버레이션)) {
                                member.setSkillEffect(STATUS_FREEZE, 요정);
                                L1EffectSpawn.getInstance().spawnEffect(551162, 요정, member.getX(), member.getY(), member.getMapId());
                            } else {
                                member.setSkillEffect(STATUS_FREEZE, 엘븐스트라이크시간);
                                L1EffectSpawn.getInstance().spawnEffect(551162, 엘븐스트라이크시간, member.getX(), member.getY(), member.getMapId());
                            }
                            SC_Paralysis.send(member, SC_Paralysis.TYPE_BIND, true);
                        } else {
                            pc.sendPackets("마법 사용: 실패(성공하지 못함)");
                        }
                        if (pc.isGm()) {
                            pc.sendPackets("스턴 확률[" + 랜덤 + "]   렙차[+" + 렙차 + "*" + Config.엘븐스트라이크양 + "+" + 기본확률 + "] = 확률범위[1~" + 양렙차 + "]");
                        }
                    } else {
                        if (랜덤 <= 음렙차) {
                            if (음렙차 < 30) {
                                음렙차 = 30;
                            }
                            int 요정 = (int)((double)엘븐스트라이크시간 * Config.리버레이션절감);
                            if (member.hasSkillEffect(L1SkillId.리버레이션)) {
                                member.setSkillEffect(STATUS_FREEZE, 요정);
                                L1EffectSpawn.getInstance().spawnEffect(551162, 요정, member.getX(), member.getY(), member.getMapId());
                            } else {
                                member.setSkillEffect(STATUS_FREEZE, 엘븐스트라이크시간);
                                L1EffectSpawn.getInstance().spawnEffect(551162, 엘븐스트라이크시간, member.getX(), member.getY(), member.getMapId());
                            }
                            SC_Paralysis.send(member, SC_Paralysis.TYPE_BIND, true);
                        } else {
                            pc.sendPackets("마법 사용: 실패(성공하지 못함)");
                        }
                        if (pc.isGm()) {
                            pc.sendPackets("스턴: 확률[" + 랜덤 + "]   렙차[-" + 렙차 + "*" + Config.엘븐스트라이크음 + "+" + 기본확률 + "] = 확률범위[1~" + 음렙차 + "]");
                        }
                    }
                    L1SkillUse.cast(pc, L1SkillId.보조버프, pc.getId(), pc.getX(), pc.getY(), null, 0, L1SkillUse.TYPE_SPELLSC);
                    SC_SkillSound.send(member, member.getId(), 15839);
                    SC_SkillSound.broadcast(member, member.getId(), 15839);
                    if (aa <= 0) {
                        dmg += aa;
                    }
                    if (dmg < 100) {
                        dmg = 100;
                    }
                    member.receiveDamage(pc, dmg);
                    return true;
                }
                if (target instanceof L1NpcInstance) {
                    L1NpcInstance member1 = (L1NpcInstance)target;
                    int diffLocX = Math.abs(pc.getX() - member1.getX());
                    int diffLocY = Math.abs(pc.getY() - member1.getY());
                    if (diffLocX > 7 || diffLocY > 7) {
                        SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "\\fY거리가 멀어서 사용할 수 없습니다.");
                        SC_SystemMessage.send(pc, "\\fY거리가 멀어서 사용할 수 없습니다.");
                        return false;
                    }
                    if (pc.getId() == member1.getId()) {
                        SC_SystemMessage.send(pc, "\\aH자기 자신에게는 사용할 수 없습니다");
                        return false;
                    }
                    int tx = member1.getX();
                    int ty = member1.getY();
                    if (!pc.glanceCheck(tx, ty) || !member1.glanceCheck(pc.getX(), pc.getY())) {
                        SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "\\fY타겟과의 사이에 장애물로 인해 사용할 수 없습니다.");
                        SC_SystemMessage.send(pc, "\\fY타겟과의 사이에 장애물로 인해 사용할 수 없습니다.");
                        return false;
                    }
                    if (pc.hasSkillEffect(L1SkillId.엘븐스트라이크대기)) {
                        int n = pc.getSkillEffectTimeSec(L1SkillId.엘븐스트라이크대기);
                        SC_SystemMessage.send(pc, "\\fY엘븐스트라이크 대기시간 : " + n + "초 후에 사용가능합니다.");
                        return false;
                    }
                    if (MJCommons.isNonAction(pc)) {
                        return false;
                    }
                    if (pc.getSafetyZone() && (!(pc.getMapId() == 9 || pc.getMapId() == 1 || pc.getMapId() == 2))) {
                        SC_SystemMessage.send(pc, "세이프티존에서는 사용할 수 없습니다.");
                        return false;
                    }
                    if (pc.isInvisble()) {
                        SC_SystemMessage.send(pc, "엘븐스트라이크는 투명상태로 시전이 불가능합니다.");
                        return false;
                    }
                    if (!pc.getInventory().checkItem(40319, 2)) {
                        SC_SystemMessage.send(pc, "마법재료가 부족합니다.");
                        return false;
                    }
                    if (pc.getCurrentMp() < 20) {
                        SC_ServerMessage.send(pc, 278);
                        return false;
                    }
                    int 랜덤 = _random.nextInt(100) + 1;
                    int 렙차 = pc.getLevel() - member1.getLevel();
                    int 기본확률 = Config.엘븐스트라이크기본;
                    int 양렙차 = 기본확률 + (렙차 * Config.엘븐스트라이크양);
                    int 음렙차 = 기본확률 + (렙차 * Config.엘븐스트라이크음);
                    int aa1 = random.nextInt(100) + 1;
                    int 몬스터 = random.nextInt(100) + 1;
                    int aa = member1.getAC().getAc() / 2;
                    int dmg = (pc.getLevel() / 3 * Config.엘븐스트라이크배율) + aa1;
                    final int 엘븐스트라이크시간 = DurationChoiceItems.elven(random);
                    // Existing short inventory gate: a failed debit must not apply a skill.
                    if (!pc.getInventory().tryConsumeCombatReagent(40319, 2)) return false;
                    pc.setSkillEffect(L1SkillId.엘븐스트라이크대기, Config.엘븐스트라이크대기 * 1000);
                    pc.setCurrentMp(pc.getCurrentMp() - 20);
                    if (몬스터 <= Config.엘븐스트라이크몬스터) {
                        member1.setSkillEffect(STATUS_FREEZE, 엘븐스트라이크시간);
                        L1EffectSpawn.getInstance().spawnEffect(551162, 엘븐스트라이크시간, member1.getX(), member1.getY(), member1.getMapId());
                        SC_Paralysis.send(member1, SC_Paralysis.TYPE_BIND, true);
                    } else {
                        pc.sendPackets("마법 사용: 실패(성공하지 못함)");
                    }
                    if (pc.isGm()) {
                        pc.sendPackets("스턴: 확률[" + 랜덤 + "]  몬스터확률범위[" + 몬스터 + "]");
                    }
                    L1SkillUse.cast(pc, L1SkillId.보조버프, pc.getId(), pc.getX(), pc.getY(), null, 0, L1SkillUse.TYPE_SPELLSC);
                    SC_SkillSound.send(member1, member1.getId(), 15839);
                    SC_SkillSound.broadcast(member1, member1.getId(), 15839);
                    if (aa <= 0) {
                        dmg += aa;
                    }
                    if (dmg < 100) {
                        dmg = 100;
                    }
                    member1.receiveDamage(pc, dmg);
                    return true;
                }
            } else {
                SC_SystemMessage.send(pc, "타겟이 올바르지않습니다.");
            }
        } else {
            pc.sendPackets("\\aH조건에 맞지 않아 사용할 수 없는 스킬입니다.");
            pc.sendPackets("\\aH조건:요정      레벨: 85 이상");
        }
        return false;
    }

    // Match the existing attack/skill target rules rather than treating any
    // globally resolved object as a legal attack target.
    private static boolean validTarget(L1PcInstance pc, L1Object target) {
        if (!(target instanceof L1Character ch) || ch == pc || ch.isDead()
                || ch.getMapId() != pc.getMapId() || ch.isInvisble()) return false;
        if (ch instanceof L1PcInstance other) {
            return !other.get_teleport() && !other.isGhost() && !other.isGmInvis()
                    && pc.getZoneType() != 1 && other.getZoneType() != 1
                    && !pc.checkNonPvP(pc, other);
        }
        if (!(ch instanceof L1NpcInstance npc) || npc.getNpcTemplate() == null
                || !L1AttackHelper.isAttackableNpcTarget(npc)) return false;
        if (npc instanceof L1MonsterInstance) return true;
        if (npc instanceof L1PetInstance pet) {
            if (pet.getMaster() == pc) return false;
        } else if (npc instanceof L1SummonInstance summon) {
            if (summon.getMaster() == pc) return false;
        } else if (npc instanceof L1CompanionInstance companion) {
            if (companion.get_master_id() == pc.getId()) return false;
        } else return false;
        return pc.getZoneType() != 1 && npc.getZoneType() != 1
                && !pc.checkNonPvP(pc, npc);
    }
}
