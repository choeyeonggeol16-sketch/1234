package l1j.server.server.model.character.combat.mortalbody;

import java.util.concurrent.ThreadLocalRandom;
import l1j.server.server.ActionCodes;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.helper.SkillUseHelper;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;
import l1j.server.server.model.network.core.sc.packet.SC_EffectLocation;

/** src22 MortalBodyProcEngine rules without per-hit Callback allocation. */
public final class MortalBodyProcEngine {
    private static final int EFFECT = 6519;
    private static final int MIN_DAMAGE = 40;

    private MortalBodyProcEngine() {}

    public static boolean tryProc(L1Character defender, int chance) {
        if (defender == null || defender.isDead()
                || !defender.hasSkillEffect(L1SkillId.MORTAL_BODY)
                || SkillUseHelper.isSkillBlock(defender)) return false;
        return chance >= 100 || (chance > 0
                && ThreadLocalRandom.current().nextInt(100) < chance);
    }

    /** src22: -(victim AC / 2), minimum 40, attacker immunity reduction. */
    public static int calcReflectDamage(L1Character attacker, L1Character defender) {
        if (attacker == null || defender == null) return 0;
        int damage = -(defender.getAC().getAc() / 2);
        if (damage < MIN_DAMAGE) damage = MIN_DAMAGE;
        if (attacker instanceof L1PcInstance pc) {
            final double reduction = pc.getImmuneReduction();
            if (reduction > 0) damage -= (int) Math.round(damage * reduction);
        }
        return Math.max(0, damage);
    }

    /** Magic damage is not cancelled by Mortal Body; only retaliation is applied. */
    public static boolean onMagicDamage(L1Character attacker, L1Character defender, int chance) {
        if (attacker == null || defender == null || attacker == defender || !tryProc(defender, chance))
            return false;
        final int x = attacker.getX(), y = attacker.getY();
        SC_EffectLocation.sendAndBroadcast(attacker, x, y, EFFECT);
        if (attacker instanceof L1PcInstance pc) {
            SC_DoActionGFX.send(pc.getNetConnection(), pc.getId(), ActionCodes.ACTION_Damage);
        }
        SC_DoActionGFX.broadcast(attacker, ActionCodes.ACTION_Damage);
        final int damage = calcReflectDamage(attacker, defender);
        if (damage > 0) attacker.receiveDamage(defender, damage);
        return true;
    }
}
