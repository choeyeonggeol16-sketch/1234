package l1j.server.server.model.item.function;

import l1j.server.server.model.network.core.sc.packet.SC_Paralysis;
import l1j.server.server.model.network.core.sc.packet.SC_EffectLocation;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_FREEZE;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import l1j.server.Config;
import l1j.server.server.ActionCodes;
import l1j.server.server.model.L1EffectSpawn;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1EffectInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.poison.L1DamagePoison;
import l1j.server.server.model.network.core.sc.packet.SC_AttackPacket;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.utils.MJCommons;
import l1j.server.server.utils.eKind;
import static l1j.server.server.model.item.function.CreatureItemActions.*;

/** Item-specific effect extracted without changing its original execution body. */
final class DemolitionItemEffect {
    private DemolitionItemEffect() {}

    public static boolean 데몰리션(L1PcInstance pc, int targetId, L1ItemInstance item) {
        if (pc == null || item == null || item.getItemId() != 707781
                || pc.getInventory().getItem(item.getId()) != item
                || pc.isDead() || pc.get_teleport()) return false;
        SC_AttackPacket.send(pc.getNetConnection(), pc, 0, ActionCodes.ACTION_Wand);
        SC_AttackPacket.broadcast(pc, 0, ActionCodes.ACTION_Wand);
        L1Object target = L1World.getInstance().findObject(targetId);
        Random random = ThreadLocalRandom.current();
        int rnd = random.nextInt(100) + 1;
        if ((pc.getType() == 7 && pc.getLevel() >= 85) || pc.isGm()) {
            if (target != null) {
                if (target instanceof L1PcInstance) {
                    L1PcInstance member = (L1PcInstance)target;
                    if (member.getMapId() != pc.getMapId() || member.isDead() || member.get_teleport()) return false;
                    int diffLocX = Math.abs(pc.getX() - member.getX());
                    int diffLocY = Math.abs(pc.getY() - member.getY());
                    if (diffLocX > 6 || diffLocY > 6) {
                        SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "\\fY거리가 멀어서 사용할 수 없습니다.");
                        SC_SystemMessage.send(pc, "\\fY거리가 멀어서 사용할 수 없습니다.");
                        return false;
                    }
                    if (pc.hasSkillEffect(L1SkillId.데몰리션대기)) {
                        int n = pc.getSkillEffectTimeSec(L1SkillId.데몰리션대기);
                        SC_SystemMessage.send(pc, "\\fY데몰리션 대기시간 : " + n + "초 후에 사용가능합니다.");
                        return false;
                    }
                    if (pc.isSkillDelay()) {
                        SC_SystemMessage.send(pc, "\\f3스킬 딜레이중이므로 사용할 수 없습니다");
                        return false;
                    }
                    if (pc.getSafetyZone() && (!(pc.getMapId() == 9 || pc.getMapId() == 1 || pc.getMapId() == 2))) {
                        SC_SystemMessage.send(pc, "세이프티존에서는 사용할 수 없습니다.");
                        return false;
                    }
                    if (MJCommons.isNonAction(pc)) {
                        return false;
                    }
                    if (pc.isInvisble()) {
                        SC_SystemMessage.send(pc, "데몰리션은 투명상태로 시전이 불가능합니다.");
                        return false;
                    }
                    if (pc.getInventory().getTypeEquipped(1, 6) < 1) {
                        SC_SystemMessage.send(pc, "한손 둔기 착용 시에만 사용가능합니다.");
                        return false;
                    }
                    if (!pc.getInventory().checkItem(41246, 100)) {
                        SC_SystemMessage.send(pc, "마법 재료가 부족합니다.");
                        return false;
                    }
                    if (pc.getCurrentMp() < Config.데몰리션엠피) {
                        SC_ServerMessage.send(pc, 278);
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
                    int attackLevel = 0;
                    int defenseLevel = 0;
                    int probability = 0;
                    attackLevel = pc.getLevel();
                    defenseLevel = member.getLevel();
                    int shockStunPoint = pc.getResistance().getSpecialPierce(eKind.FEAR) + pc.getResistance().getSpecialPierce(eKind.ALL);
                    final int 데몰리션시간 = DurationChoiceItems.demolition(random);
                    if (shockStunPoint > 0) {
                        attackLevel += Math.round(((double)shockStunPoint * (double)Config.STUNHIT_TO_LEVEL11));
                    }
                    probability = (int)+Config.데페기본확률 + ((attackLevel - defenseLevel) * 2);
                    probability += pc.getImpactUp();
                    int resistance = (member.getResistance().getSpecialResistance(eKind.FEAR) + member.getResistance().getSpecialResistance(eKind.ALL)) / Config.내성배율;
                    if (resistance > 0) {
                        probability -= resistance;
                    }
                    if (probability < 15) {
                        probability = 15;
                    }
                    if (probability > 100) {
                        probability = 100;
                    }
                    if (pc.isGm()) {
                        SC_SystemMessage.send(pc, "데몰리션 제약확률: " + rnd + "    \\aH범위 [1~" + probability + "]");
                    }
                    if (!pc.getInventory().tryConsumeCombatReagent(41246, 100)) return false;
                    int actionId = ActionCodes.ACTION_SkillBuff;
                    SC_DoActionGFX.send(pc.getNetConnection(), pc.getId(), actionId);
                    SC_DoActionGFX.broadcast(pc, actionId);
                    pc.setSkillEffect(L1SkillId.데몰리션대기, Config.데몰리션대기 * 1000);
                    pc.setCurrentMp(pc.getCurrentMp() - Config.데몰리션엠피);
                    if (rnd <= probability) {
                        member.setSkillEffect(L1SkillId.쉐도우제약, 데몰리션시간);
                        SC_Paralysis.send(member, SC_Paralysis.TYPE_BIND, true);
                        SC_SkillSound.send(member, member.getId(), 14449);
                        SC_SkillSound.broadcast(member, member.getId(), 14449);
                        int 요정 = (int)((double)데몰리션시간 * Config.리버레이션절감);
                        if (member.hasSkillEffect(L1SkillId.리버레이션)) {
                            L1DamagePoison.doInfection(pc, member, 800, pc.getLevel() * 2 / Config.데몰리션대미지, true, 요정);
                            L1EffectSpawn.getInstance().spawnEffect(9418, 요정, member.getX(), member.getY(), member.getMapId());
                            L1EffectSpawn.getInstance().spawnEffect(881162, 요정, member.getX(), member.getY(), member.getMapId());
                            member.setSkillEffect(STATUS_FREEZE, 요정);
                        } else {
                            L1DamagePoison.doInfection(pc, member, 800, pc.getLevel() * 2 / Config.데몰리션대미지, true, 데몰리션시간);
                            L1EffectSpawn.getInstance().spawnEffect(9418, 데몰리션시간, member.getX(), member.getY(), member.getMapId());
                            L1EffectSpawn.getInstance().spawnEffect(881162, 데몰리션시간, member.getX(), member.getY(), member.getMapId());
                            member.setSkillEffect(STATUS_FREEZE, 데몰리션시간);
                        }
                    } else {
                        SC_SkillSound.send(member, member.getId(), 17233);
                        SC_SkillSound.broadcast(member, member.getId(), 17233);
                        SC_SkillSound.send(member, member.getId(), 14448);
                        SC_SkillSound.broadcast(member, member.getId(), 14448);
                        pc.sendPackets("마법 사용: 실패(성공하지 못함)");
                    }
                    return true;
                } else {
                    SC_SystemMessage.send(pc, "\\aHPC에게만 가능합니다.");
                }
            } else {
                SC_SystemMessage.send(pc, "타겟이 올바르지않습니다.");
            }
        } else {
            pc.sendPackets("\\aH조건에 맞지 않아 사용할 수 없는 스킬입니다.");
            pc.sendPackets("\\aH조건:전사      레벨: 85 이상");
        }
        return false;
    }
}
