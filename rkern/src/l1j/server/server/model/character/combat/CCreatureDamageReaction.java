package l1j.server.server.model.character.combat;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.character.Attack.Helper.AttackSkillChanceConfig;
import l1j.server.server.model.character.combat.counterbarrier.CounterBarrierProcEngine;
import l1j.server.server.model.character.combat.inferno.InfernoProcEngine;
import l1j.server.server.model.character.combat.mortalbody.MortalBodyProcEngine;
import l1j.server.server.model.character.combat.titan.TitanProcEngine;
import l1j.server.server.model.character.action.CAttackAction.AttackTargetData;

/** Victim reaction boundary, corresponding to the damage-receive responsibility
 * in reference server CCreature. CAttackAction still owns the attack result and its commit.
 * No additional queue, worker, callback allocation or duplicate HP commit. */
public final class CCreatureDamageReaction {
    private CCreatureDamageReaction() {}

    /** Called exactly once after physical hit confirmation, before HP commit.
     * A true result means this dispatcher already finalized that hit. */
    public static boolean onPhysicalHit(AttackTargetData hit, L1Character attacker,
                                        L1Character defender) {
        return onPhysicalHit(hit, attacker, defender, true);
    }

    /** NPC physical AoE sends the ordinary attack animation only for its primary target. */
    public static boolean onPhysicalHit(AttackTargetData hit, L1Character attacker,
                                        L1Character defender, boolean animateNormalAttack) {
        if (attacker == null || attacker == defender || !(defender instanceof L1PcInstance pc)
                || pc.isDead()) return false;

        // Preserve the existing victim proc priority. ARMOR_BRAKE suppresses
        // Counter Barrier, but must not turn Titan Rock into Titan Blitz.
        final AttackSkillChanceConfig.Settings chances = AttackSkillChanceConfig.get();
        final boolean melee = hit.isShortDistance()
                && (!(attacker instanceof L1PcInstance a) || hasMeleeWeapon(a));
        if (CounterBarrierProcEngine.tryProc(pc, melee, chances.counterBarrierChance())) {
            hit.actionCounterBarrier(animateNormalAttack);
            hit.commitCounterBarrier();
            return true;
        }

        if (hit.isTitanPhysicalHit()) {
            final TitanProcEngine.Mode mode = hit.isRangedPhysicalHit()
                    ? TitanProcEngine.Mode.BLITZ : TitanProcEngine.Mode.ROCK;
            final int chance = mode == TitanProcEngine.Mode.ROCK
                    ? chances.titanRockChance() : chances.titanBlitzChance();
            if (TitanProcEngine.tryProc(attacker, pc, mode, chance)) {
                hit.set_hit(false);
                if (animateNormalAttack) hit.action();
                return true;
            }
        }

        if (MortalBodyProcEngine.tryProc(pc, chances.mortalBodyChance())) {
            hit.calcDamage();
            hit.actionMortalBody(animateNormalAttack);
            hit.commitMortalBody();
            hit.commit();
            return true;
        }

        final int step = InfernoProcEngine.rollStep(pc, melee, chances.infernoChance());
        if (step != 0) {
            hit.calcDamage(true);
            hit.actionInferno(step, animateNormalAttack);
            hit.commitInferno(step);
            hit.commit();
            return true;
        }
        return false;
    }

    /** Magic victim response: Titan Magic prevents the hit; Mortal Body does not. */
    public static boolean onMagicDamage(L1Character attacker, L1PcInstance defender) {
        if (attacker == null || defender == null || defender.isDead()) return false;
        final AttackSkillChanceConfig.Settings chances = AttackSkillChanceConfig.get();
        if (TitanProcEngine.tryProc(attacker, defender, TitanProcEngine.Mode.MAGIC,
                chances.titanMagicChance())) return true;
        MortalBodyProcEngine.onMagicDamage(attacker, defender, chances.mortalBodyChance());
        return false;
    }

    private static boolean hasMeleeWeapon(L1PcInstance attacker) {
        final L1ItemInstance weapon = attacker.getWeapon();
        if (weapon == null) return true;
        final int type = weapon.getItem().getType();
        return type != 4 && type != 9 && type != 10 && type != 13 && type != 17;
    }
}
