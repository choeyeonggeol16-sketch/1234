package l1j.server.server.model.character.Combat.Inferno;

import java.util.concurrent.ThreadLocalRandom;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.utils.CommonUtil;

/** Inferno activation and multiplier only; the attack result owns final HP commit. */
public final class InfernoProcEngine {
    private InfernoProcEngine() {}

    /** Zero means not activated; otherwise returns the original 1..4 damage step. */
    public static int rollStep(L1PcInstance defender, boolean eligibleMelee, int chance) {
        if (!eligibleMelee || defender == null || defender.isDead()
                || !defender.hasSkillEffect(L1SkillId.INFERNO)
                || CommonUtil.isNonActionSkill(defender)) return 0;
        final L1ItemInstance weapon = defender.getWeapon();
        if (weapon == null || weapon.getItem().getType() != 1) return 0;
        if (chance <= 0 || (chance < 100
                && ThreadLocalRandom.current().nextInt(100) >= chance)) return 0;
        return ThreadLocalRandom.current().nextInt(4) + 1;
    }
}
