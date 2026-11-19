package l1j.server.server.model.character.skill.core;

import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1ClanJoinInstance;
import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.templates.L1Skills;

public enum SkillUseInitializer {
    INSTANCE;

    public boolean init(
            L1SkillUse su,
            L1PcInstance player,
            int skillId,
            int targetId,
            int x,
            int y,
            String message,
            int time,
            int type,
            L1Character attacker) {

    	if (su == null || (player == null && attacker == null)) {
    	    return false;
    	}

        su.resetStateForCheck();
        su.setCheckedUseSkill(true);

        // 스킬 세팅
        if (!su.initSkill(skillId, x, y, time, type)) {
            return false;
        }

        // 스킬 정보
        final L1Skills skill = su.exSkill();
        if (skill == null) {
            return false;
        }

        final String skillTarget = skill.getTarget();
        if (skillTarget == null) {
            return false;
        }

        // 사용자 세팅
        if (!su.setupUser(player, attacker)) {
            return false;
        }

        // 기본 타겟 정보
        su.setupTargetBasics(skillTarget, targetId);

        // 타입별 사용 가능 체크
        if (!SkillUsabilityPolicy.INSTANCE.checkUsabilityByType(su, type)) {
            return false;
        }

        /*
         * 지면 스킬
         *
         * FIRE_WALL / LIFE_STREAM 은
         * 월드 오브젝트 target resolve가 필요 없음.
         */
        if (su.isGroundSkill(skillId)) {
            su.exSetCalcTypeForGround();
            return true;
        }

        // 실제 타겟 오브젝트 resolve
        if (!resolveTargetObject(su)) {
            return false;
        }

        // calcType 결정
        su.determineCalcTypeAndTargetNpc(skillTarget);

        // 컴패니언 buff 금지
        if (!checkCompanionBuffForbidden(su, skillTarget)) {
            return false;
        }

        // 북마크 / 아이템 대상 정보
        su.applyBookmarksAndItemTarget(
                targetId,
                x,
                y);

        // PK 플래그
        setPkFlagIfNeeded(
                su,
                skillTarget);

        su.makeTargetList();

        if (su.exTargetsEmpty()
                && su.hdUser() instanceof L1NpcInstance) {

            return false;
        }

        return true;
    }

    /**
     * targetId -> 실제 L1Character resolve
     */
    private boolean resolveTargetObject(L1SkillUse su) {
        final int targetId = su.hdTargetId();

        if (targetId == 0) {
            return false;
        }

        final L1Object obj =
                L1World.getInstance().findObject(targetId);

        if (obj == null) {
            return false;
        }

        // 아이템은 스킬 캐릭터 타겟이 아님
        if (obj instanceof L1ItemInstance) {
            return false;
        }

        // 혈맹 가입 관련 객체 제외
        if (obj instanceof L1ClanJoinInstance) {
            return false;
        }

        if (!(obj instanceof L1Character)) {
            return false;
        }

        su.exSetResolvedTarget(
                (L1Character) obj);

        return true;
    }

    /**
     * 컴패니언에는 일반 buff 타겟 금지.
     */
    private boolean checkCompanionBuffForbidden(
            L1SkillUse su,
            String skillTarget) {

        final L1NpcInstance targetNpc =
                su.exTargetNpc();

        if (targetNpc instanceof L1CompanionInstance) {
            return !"buff".equals(skillTarget);
        }

        return true;
    }

    /**
     * 공격 대상이 일반 몬스터가 아닌 경우
     * PvP 성격의 스킬로 표시.
     */
    private void setPkFlagIfNeeded(
            L1SkillUse su,
            String skillTarget) {

        final L1Character user =
                su.hdUser();

        final L1Character target =
                su.hdTarget();

        if (user == null || target == null) {
            return;
        }

        if (!(target instanceof L1MonsterInstance)
                && "attack".equals(skillTarget)
                && user.getId() != su.hdTargetId()) {

            su.exSetPK(true);
        }
    }
}