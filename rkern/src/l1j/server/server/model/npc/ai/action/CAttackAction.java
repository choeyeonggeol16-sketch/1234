package l1j.server.server.model.npc.ai.action;

import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.npc.ai.CDesirePQ;
import l1j.server.server.model.L1Character;

/**
 * 공격 action.
 *
 * 객체를 생성하지 않는 stateless 구현이며,
 * target/current/pending/combat state는 L1NpcInstance가 보유한다.
 */
public final class CAttackAction {

    private CAttackAction() {
    }

    /**
     * action 진입.
     * current action을 ATTACK으로 바꾸고 시작 hook을 한 번 실행한다.
     */
    public static void set(final L1NpcInstance creature) {
        if (creature == null) {
            return;
        }

        CCreatureController.set(
                creature,
                CCreatureController.ACTION_ATTACK);

        onStart(creature);
    }

    /**
     * action 시작 시 한 번만 호출된다.
     *
     * 이전 추격에서 남은 blocked 상태를 제거한다.
     */
    public static void onStart(final L1NpcInstance creature) {
        if (creature == null) {
            return;
        }

        CMoveToTargetAction.clearBlocked(creature);
    }

    /**
     * 한 번의 공격 tick.
     *
     * RUNNING : Effect/Skill/기본 공격을 실행했으며 ATTACK 유지
     * DONE    : 공격 사거리 이탈
     * INVALID : target/action 전제가 깨짐
     */
    public static byte onTick(
            final L1NpcInstance creature,
            final L1Character target) {

        if (creature == null || target == null) {
            return CCreatureController.RESULT_INVALID;
        }

        // reference server dPAttack rejects an attacker with no health. The first CIO
        // action tick skips AI preflight, so preserve its hidden-state gate.
        if (creature.isDead()
                || creature.getCurrentHp() <= 0
                || creature.getHiddenStatus()
                        != L1NpcInstance.HIDDEN_STATUS_NONE) {
            return CCreatureController.RESULT_STOP;
        }

        if (target.isDead()
                || target.getCurrentHp() <= 0
                || target.getMapId() != creature.getMapId()
                || (target.isInvisble()
                        && !creature.getNpcTemplate().is_agrocoi())) {
            return CCreatureController.RESULT_INVALID;
        }

        // NC dPAttack finishes its desire execution by boosting that
        // ATTACK key once. No BoostDown occurs merely on retarget.
        if (creature instanceof L1MonsterInstance) {
            CDesirePQ.boostExecutedAttack((L1MonsterInstance) creature, target);
        }

        // 기존 전투 우선순위: Effect -> Skill -> 기본 공격.
        if (tryCombatAbility(creature, target)) {
            return CCreatureController.RESULT_RUNNING;
        }

        if (!creature.isAttackPosition(
                target,
                creature.getNpcTemplate().get_ranged())) {

            return CCreatureController.RESULT_DONE;
        }

        basicAttack(creature, target);
        return CCreatureController.RESULT_RUNNING;
    }

    /**
     * Effect -> Skill 우선순위를 유지하는 공용 전투 primitive.
     *
     * MoveToTargetAction도 같은 로직을 사용한다.
     *
     * CSkillAction2 객체를 NPC가 소유하지 않는다.
     * 이미 MobSkillTable에 로드된 L1MobSkill 정의를 static executor가 사용한다.
     */
    public static boolean tryCombatAbility(
            final L1NpcInstance creature,
            final L1Character target) {

        return dUseSkill.tryUse(creature, target);
    }

    public static void basicAttack(
            final L1NpcInstance creature,
            final L1Character target) {

        if (creature == null || target == null) {
            return;
        }

        CMoveToTargetAction.clearBlocked(creature);

        creature.setHeading(
                creature.targetDirection(
                        target.getX(),
                        target.getY()));

        creature.attackTarget(target);
    }

    /**
     * action 종료 처리.
     *
     * DONE    : 완료된 타겟의 공격을 종료하고 다음 욕구 선택으로 전환
     * INVALID : 아직 같은 타겟일 때에만 무효 타겟을 정리
     */
    public static void onEnd(
            final L1NpcInstance creature,
            final L1Character target,
            final byte result) {

        if (creature == null
                || CCreatureController.currentAction(creature)
                        != CCreatureController.ACTION_ATTACK) {
            return;
        }

        switch (result) {
        case CCreatureController.RESULT_DONE:
            // An AttackFinished completion belongs to the captured target.
            // Never interrupt an action already selected for a newer target.
            // The CIO wheel resumes desire selection on the next owner tick.
            if (creature.getTarget() == target) {
                CCreatureController.think(creature);
            }
            return;

        case CCreatureController.RESULT_INVALID:
            // Clearing a stale attack must not clear or reset a newer target.
            if (creature.getTarget() == target) {
                creature.tagertClear();
            }
            return;

        case CCreatureController.RESULT_RUNNING:
        case CCreatureController.RESULT_BLOCKED:
        case CCreatureController.RESULT_STOP:
        default:
            return;
        }
    }
}
