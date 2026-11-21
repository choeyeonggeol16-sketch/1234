package l1j.server.server.model.character.skill.core;

import l1j.server.Config;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.animation.EActionCodes;
import l1j.server.server.model.character.animation.SpriteInformation;
import l1j.server.server.model.character.animation.loader.SpriteInformationLoader;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.templates.L1Skills;

public enum SkillDelayPolicy {
    INSTANCE;

    public void applyAfterCast(L1SkillUse su) {
        if (su == null || su.exSkill() == null) return;

        final L1PcInstance pc = su.hdPlayer();
        if (pc == null) return;

        final int gateMs = calcGateMs(pc, su.exSkill());

        pc.setNextSkillDelay(gateMs);

    }

    private static final long SPELL_MIN_GATE_MS = 100L;

    public static int calcGateMs(
            L1PcInstance pc,
            L1Skills skill) {

        if (pc == null || skill == null) {
            return (int) SPELL_MIN_GATE_MS;
        }

        final SpriteInformationLoader loader =
                SpriteInformationLoader.getInstance();

        final SpriteInformation spr =
                loader.get(pc.getCurrentSpriteId());

        if (spr == null) {
            return (int) SPELL_MIN_GATE_MS;
        }

        final int skillId = skill.getSkillId();

        final EActionCodes action;

        if ("attack".equals(skill.getTarget())
                && skillId != 18) {

            action = EActionCodes.spell_dir;

        } else {

            action = EActionCodes.spell_nodir;
        }

        long interval;
        try {
            interval = (long) spr.getInterval(pc, action);
        } catch (Throwable t) {
            interval = SPELL_MIN_GATE_MS;
        }

        if (interval < 0) {
            interval = 0;
        }

        long gate =
                interval - Config.SPELL_NETWORK_TOLERANCE;

        if (gate < SPELL_MIN_GATE_MS) {
            gate = SPELL_MIN_GATE_MS;
        }

        if (gate > Integer.MAX_VALUE) {
            gate = Integer.MAX_VALUE;
        }

        return (int) gate;
    }
}