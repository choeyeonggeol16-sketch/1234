package l1j.server.server.model.character.skill;

import static l1j.server.server.model.character.skill.L1SkillId.*;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.character.skill.passive.PassiveID;
import l1j.server.server.model.network.core.sc.packet.SC_SkillBrave;

/** Reconciles the second speed tier when an owning timer is removed. */
public final class BraveSpeedEffects {
    private static final int[] SPEED_SOURCES = {
        STATUS_FRUIT, HOLY_WALK, MOVING_ACCELERATION, STATUS_ELFBRAVE, STATUS_BRAVE, BLOOD_LUST, MOB_BERSERKERS
    };
    private static final int[] VISUAL_ONLY = {FOCUS_WAVE, HURRICANE, SAND_STORM, DANCING_BLADES};

    private BraveSpeedEffects() {}

    public static void cancelTimer(L1PcInstance pc, int skillId) {
        pc.killSkillEffectTimer(skillId);
        if (skillId == FOCUS_WAVE) pc.set_focus_wave_level(0);
        onEnd(pc, skillId);
    }

    public static void onEnd(L1Character target, int ending) {
        final int current = target.getBraveSpeed();
        int source = 0;
        int speed = 0;
        for (int id : SPEED_SOURCES) {
            if (id == ending || !target.hasSkillEffect(id)) continue;
            final int candidate = speedFor(target, id);
            if (source == 0 || candidate == current) {
                source = id;
                speed = candidate;
            }
            if (id == STATUS_FRUIT || candidate == current) break;
        }
        target.setBraveSpeed(speed);
        if (!(target instanceof L1PcInstance pc) || target.getId() == 0) return;

        if (source != STATUS_FRUIT) {
            for (int id : VISUAL_ONLY) {
                if (id != ending && pc.hasSkillEffect(id)) {
                    source = id;
                    break;
                }
            }
        }
        final int mode = switch (source) {
            // A different speed timer may end while focus stage three remains active.
            case FOCUS_WAVE -> pc.get_focus_wave_level() > 1
                    && pc.hasSkillEffect(포커스3단) ? 12 : 10;
            case HURRICANE -> 9;
            case BLOOD_LUST -> 6;
            case STATUS_ELFBRAVE -> 3;
            case SAND_STORM, DANCING_BLADES -> 1;
            default -> speed;
        };
        final int duration = source == 0 ? 0 : Math.max(0, pc.getSkillEffectTimeSec(source));
        SC_SkillBrave.send(pc, pc.getId(), mode, duration);
        SC_SkillBrave.broadcastFast(pc, pc.getId(), mode, 0);
    }

    private static int speedFor(L1Character target, int source) {
        return switch (source) {
            case STATUS_FRUIT -> target.isPassive(PassiveID.DARK_HORSE.toInt()) ? 3 : 4;
            case HOLY_WALK, MOVING_ACCELERATION -> 4;
            case STATUS_BRAVE -> target instanceof L1PcInstance pc ? pc.getStatusBraveMode() : 1;
            default -> 1;
        };
    }
}
