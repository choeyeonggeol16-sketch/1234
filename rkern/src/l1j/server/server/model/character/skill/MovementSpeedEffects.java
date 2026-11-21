package l1j.server.server.model.character.skill;

import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.network.core.sc.packet.SC_SkillHaste;

import static l1j.server.server.model.character.skill.L1SkillId.*;

/** Resolves the first movement-speed tier using the existing skill timers. */
public final class MovementSpeedEffects {
    private static final int NORMAL = 0;
    private static final int HASTED = 1;
    private static final int SLOWED = 2;

    private MovementSpeedEffects() {}

    public static int activeSlow(L1Character target, int except) {
        if (except != SLOW && target.hasSkillEffect(SLOW)) return SLOW;
        if (except != MOB_SLOW_1 && target.hasSkillEffect(MOB_SLOW_1)) return MOB_SLOW_1;
        if (except != MOB_SLOW_18 && target.hasSkillEffect(MOB_SLOW_18)) return MOB_SLOW_18;
        return 0;
    }

    public static int activeHaste(L1Character target, int except) {
        if (except != HASTE && target.hasSkillEffect(HASTE)) return HASTE;
        if (except != GREATER_HASTE && target.hasSkillEffect(GREATER_HASTE)) return GREATER_HASTE;
        if (except != STATUS_HASTE && target.hasSkillEffect(STATUS_HASTE)) return STATUS_HASTE;
        return 0;
    }

    public static boolean appliedOnCast(L1Character target, int skillId) {
        return switch (skillId) {
            case SLOW, MOB_SLOW_1, MOB_SLOW_18 -> target.getMoveSpeed() == SLOWED;
            case HASTE, GREATER_HASTE -> target.getMoveSpeed() == HASTED && !hasEquippedHaste(target);
            default -> true;
        };
    }

    public static void endSlow(L1Character target, int skillId) {
        if (target.getMoveSpeed() != SLOWED || activeSlow(target, skillId) != 0) return;

        // Independent haste sources can outlive the final slow timer. Do not
        // erase them by forcing normal speed as the slow effect disappears.
        final int haste = activeHaste(target, 0);
        final boolean equipped = hasEquippedHaste(target);
        final boolean npcBerserk = target instanceof L1NpcInstance
                && target.hasSkillEffect(MOB_BERSERKERS);
        if (haste != 0 || equipped || npcBerserk) {
            target.setMoveSpeed(HASTED);
            if (target.getId() != 0) {
                if (target instanceof L1PcInstance pc) {
                    final int duration = haste == 0 ? 0
                            : Math.max(0, pc.getSkillEffectTimeSec(haste));
                    SC_SkillHaste.send(pc, pc.getId(), HASTED, duration);
                }
                SC_SkillHaste.broadcastFast(target, target.getId(), HASTED, 0);
            }
            return;
        }
        reset(target, true);
    }

    public static void endHaste(L1Character target, int skillId) {
        if (target.getMoveSpeed() != HASTED || hasEquippedHaste(target)
                || activeHaste(target, skillId) != 0) return;
        if (target instanceof L1NpcInstance && skillId != MOB_BERSERKERS && target.hasSkillEffect(MOB_BERSERKERS))
            return;
        reset(target, false);
    }

    public static boolean cancelSlowWithPotion(L1PcInstance pc) {
        final int slow = activeSlow(pc, 0);
        if (slow == 0) return false;
        pc.killSkillEffectTimer(slow);
        endSlow(pc, slow);
        return true;
    }

    private static boolean hasEquippedHaste(L1Character target) {
        return target instanceof L1PcInstance pc && pc.getHasteItemEquipped() > 0;
    }

    private static void reset(L1Character target, boolean broadcastNpc) {
        target.setMoveSpeed(NORMAL);
        if (target.getId() == 0) return;
        if (target instanceof L1PcInstance pc) {
            SC_SkillHaste.send(pc, pc.getId(), NORMAL, 0);
            SC_SkillHaste.broadcastFast(pc, pc.getId(), NORMAL, 0);
        } else if (broadcastNpc) {
            SC_SkillHaste.broadcastFast(target, target.getId(), NORMAL, 0);
        }
    }
}
