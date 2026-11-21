package l1j.server.server.model.character.skill;

import java.util.function.IntPredicate;
import l1j.server.server.model.L1Character;

import static l1j.server.server.model.character.skill.L1SkillId.*;

/** Shared classification of action restrictions owned by skill timers. */
public final class ControlSkillEffects {
    private static final IntPredicate STUN = ControlSkillEffects::isStun;
    private static final IntPredicate FREEZE = ControlSkillEffects::isSkillFreeze;
    private static final IntPredicate NPC_CONTROL = id -> isStun(id) || isSkillFreeze(id);
    private static final IntPredicate CAST_LOCK = id -> isStun(id) || switch (id) {
        case EARTH_BIND, ICE_LANCE, STATUS_FREEZE -> true;
        default -> false;
    };

    private ControlSkillEffects() {}

    public static boolean isStun(int id) {
        return switch (id) {
            case SHOCK_STUN, EMPIRE, BONE_BREAK, MOB_SHOCKSTUN_30, MOB_RANGESTUN_18,
                    MOB_RANGESTUN_19, MOB_RANGESTUN_20, Mob_RANGESTUN_30,
                    ANTA_MESSAGE_6, ANTA_MESSAGE_7, ANTA_MESSAGE_8, ANTA_SHOCKSTUN,
                    OMAN_STUN, Maeno_STUN -> true;
            default -> false;
        };
    }

    public static boolean hasStun(L1Character target, int endingSkill) {
        return target.hasSkillEffectMatching(STUN, endingSkill);
    }

    public static boolean isSkillFreeze(int skillId) {
        return skillId == ICE_LANCE || skillId == EARTH_BIND;
    }

    public static boolean hasSkillFreeze(L1Character target, int endingSkill) {
        return target.hasSkillEffectMatching(FREEZE, endingSkill);
    }

    public static boolean hasNpcControl(L1Character target) {
        return target.hasSkillEffectMatching(NPC_CONTROL, -1);
    }

    public static boolean isSleep(int skillId) {
        return skillId == DARK_BLIND || skillId == PHANTASM || skillId == FOG_OF_SLEEPING;
    }

    public static boolean blocksCast(L1Character target) {
        return target.isParalyzed() || target.isSleeped() || target.hasSkillEffectMatching(CAST_LOCK, -1);
    }
}
