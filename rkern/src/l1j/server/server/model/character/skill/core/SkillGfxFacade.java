package l1j.server.server.model.character.skill.core;

import l1j.server.server.model.L1Character;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.character.skill.helper.SkillGfxHelper;

public enum SkillGfxFacade {
    INSTANCE;

    public void send(L1SkillUse su, boolean isSkillAction) {
        if (su == null) {
            return;
        }

        final int targetCount = su.exTargetCount();
        final L1Character[] targets = su.exTargetBackingArray();

        SkillGfxHelper.sendGrfx(
                isSkillAction,
                su.exSkill(),
                su.hdSkillId(),
                su.hdUser(),
                su.hdPlayer(),
                su.hdTarget(),
                targetCount,
                targets,
                su.exTargetX(),
                su.exTargetY(),
                su.hdGetBuffIconDuration(),
                su.exSkillTimeSec(),
                su.hdCalcType());
    }
}
