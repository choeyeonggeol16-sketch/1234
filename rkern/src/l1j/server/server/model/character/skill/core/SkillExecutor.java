package l1j.server.server.model.character.skill.core;

import l1j.server.server.model.L1Character;
import l1j.server.server.model.world.effect.L1EffectSpawn;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.character.skill.helper.SkillUseHelper;
import l1j.server.server.templates.L1Skills;

public enum SkillExecutor {
    INSTANCE;

    public void execute(L1SkillUse su) {
        if (su == null) {
            return;
        }

        final L1Skills skill = su.exSkill();
        final L1Character user = su.hdUser();

        if (skill == null || user == null) {
            return;
        }

        final int skillId = su.hdSkillId();
        final L1PcInstance player = su.hdPlayer();

        final boolean detectionSkill = (skillId == L1SkillId.DETECTION
                || skillId == L1SkillId.IZE_BREAK
                || skillId == L1SkillId.EYE_OF_DRAGON
                || skillId == L1SkillId.COUNTER_DETECTION);

        if (!detectionSkill
                && player != null
                && player.isInvisble()) {

            final int t = skill.getType();

            if (t == L1Skills.TYPE_ATTACK
                    || t == L1Skills.TYPE_CURSE
                    || t == L1Skills.TYPE_PROBABILITY) {
                player.delInvis();
            }
        }

        if (skillId == L1SkillId.LIFE_STREAM) {
            L1EffectSpawn.getInstance().spawnEffect(
                    81169,
                    skill.getBuffDuration() * 1000,
                    su.exTargetX(),
                    su.exTargetY(),
                    user.getMapId());
            return;
        }

        if (skillId == L1SkillId.FIRE_WALL) {
            L1EffectSpawn.getInstance().doSpawnFireWall(user, su.exTargetX(), su.exTargetY());
            return;
        }

        su.exSetCounterMagicEnabled(!SkillUseHelper.isExceptCounterMagic(skillId));

        if ((skillId == L1SkillId.SHOCK_STUN
                || skillId == L1SkillId.BONE_BREAK
                || skillId == L1SkillId.EMPIRE
                || skillId == L1SkillId.DESPERADO)
                && user instanceof L1PcInstance) {

            final L1Character primary = su.hdTarget();
            if (primary != null) {
                primary.onAction(player);
            }
        }

        if (!TargetRules.INSTANCE.isTargetCalc(su, su.hdTarget())) {
            return;
        }

        final int targetCount = su.exTargetCount();
        for (int i = 0; i < targetCount; i++) {
            final L1Character target = su.exTargetAt(i);
            if (target == null) {
                continue;
            }

            final TargetSkillProcessor.Outcome out =
                    TargetSkillProcessor.INSTANCE.processOne(
                            su,
                            target,
                            su.exTargetCalcAt(i));

            if (out == TargetSkillProcessor.Outcome.RETURN_ALL) {
                su.exCompactTargets();
                return;
            }

            if (out == TargetSkillProcessor.Outcome.REMOVE_AND_CONTINUE) {
                su.exMarkTargetRemoved(i);
            }
        }

        su.exCompactTargets();

        if (detectionSkill) {
            su.hdDetection(player, true);
        }
    }
}
