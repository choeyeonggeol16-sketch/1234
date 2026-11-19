package l1j.server.server.model.character.skill.core;

import l1j.server.server.model.L1Character;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.character.skill.helper.SkillUseHelper;

public enum RecastPolicy {

    INSTANCE;

    /**
     * 이미 걸려 있는 동일 스킬의 재시전 처리.
     * @return true면 효과 시간만 갱신하고
     *         이번 타겟의 이후 계산을 건너뛴다.
     */
    public boolean refreshAndSkipFurther(final L1SkillUse su, final L1Character target) {

        if (su == null || target == null) {
            return false;
        }

        final int skillId =  su.hdSkillId();
        if (SkillUseHelper.isRecastSkipRefresh(skillId)) {
            return false;
        }

        /*
         * 기존 효과가 있어도 일반 refresh 처리하지 않는 예외.
         */
        if (SkillUseHelper.isRecastException(skillId)) {
            return false;
        }

        /*
         * 기존 동일 효과가 없다면 신규 적용 경로.
         */
        if (!target.hasSkillEffect(skillId)) {
            return false;
        }

        /*
         * 이미 걸린 동일 효과의 duration refresh.
         */
        su.exAddMagicList(target, true);

        return skillId != L1SkillId.SHAPE_CHANGE;
    }
}