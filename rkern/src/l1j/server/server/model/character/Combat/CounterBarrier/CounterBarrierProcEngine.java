package l1j.server.server.model.character.Combat.CounterBarrier;

import java.util.concurrent.ThreadLocalRandom;
import l1j.server.MJPassiveSkill.MJPassiveID;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.utils.CommonUtil;

/** Victim-side Counter Barrier proc conditions. Damage/graphics stay on the attack result. */
public final class CounterBarrierProcEngine {
    private CounterBarrierProcEngine() {}

    public static boolean tryProc(L1PcInstance defender, boolean eligibleMelee, int configuredChance) {
        if (!eligibleMelee || defender == null || defender.isDead()
                || !defender.hasSkillEffect(L1SkillId.COUNTER_BARRIER)
                || CommonUtil.isNonActionSkill(defender)) return false;
        // Without a two-handed sword the legacy damage calculation returned zero,
        // but still cancelled the incoming hit. Do not allow a free parry.
        if (defender.getWeapon() == null || defender.getWeapon().getItem().getType() != 3)
            return false;
        final int veteran = defender.isPassive(MJPassiveID.COUNTER_BARRIER_VETERAN.toInt())
                && defender.getLevel() >= 85 ? defender.getLevel() - 85 : 0;
        final int chance = configuredChance + veteran;
        return chance >= 100 || (chance > 0
                && ThreadLocalRandom.current().nextInt(100) < chance);
    }
}
