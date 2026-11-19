package l1j.server.server.model.npc.ai.action;

import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.utils.NpcAiUtil;

/**
 * 타겟 반대 방향으로 이동하는 도망 action.
 *
 * 객체를 만들지 않는 stateless 구현이며,
 * 실제 위치/상태는 L1NpcInstance가 보유한다.
 */
public final class CEscapeAction {

    private static final int ESCAPE_DISTANCE = 15;

    private CEscapeAction() {
    }

    /**
     * action 진입.
     * current action을 ESCAPE로 바꾸고 시작 hook을 한 번 실행한다.
     */
    public static void set(final L1NpcInstance creature) {
        if (creature == null) {
            return;
        }

        CCreatureController.set(
                creature,
                CCreatureController.ACTION_ESCAPE);

        onStart(creature);
    }

    /**
     * action 시작 시 한 번만 호출된다.
     *
     * 이전 추격에서 남은 blocked 상태는 도망 action과 무관하므로 제거한다.
     */
    public static void onStart(final L1NpcInstance creature) {
        if (creature == null) {
            return;
        }

        CMoveToTargetAction.clearBlocked(creature);
    }

    /**
     * 한 번의 도망 action tick.
     *
     * RUNNING : 도망 이동을 수행함
     * DONE    : 충분히 멀어져 action 종료
     * INVALID : target/action 전제가 깨짐
     */
    public static byte onTick(
            final L1NpcInstance creature,
            final L1Character target) {

        if (creature == null || target == null) {
            return CCreatureController.RESULT_INVALID;
        }

        if (target.isDead()
                || target.getCurrentHp() <= 0
                || target.getMapId() != creature.getMapId()) {
            return CCreatureController.RESULT_INVALID;
        }

        if (creature.getLocation()
                .getTileLineDistance(target.getLocation()) > ESCAPE_DISTANCE) {
            return CCreatureController.RESULT_DONE;
        }

        int dir = creature.targetReverseDirection(
                target.getX(),
                target.getY());

        dir = NpcAiUtil.checkObject(
                creature.getX(),
                creature.getY(),
                creature.getMapId(),
                dir);

        // 이동 불가 방향이어도 기존 sleep semantics는 유지한다.
        ActionAccess.directionMove(creature, dir);

        return CCreatureController.RESULT_RUNNING;
    }

    /**
     * action 종료 처리.
     *
     * DONE/INVALID 모두 현재 타겟을 정리하고 다음 판단으로 넘긴다.
     */
    public static void onEnd(
            final L1NpcInstance creature,
            final L1Character target,
            final byte result) {

        if (creature == null) {
            return;
        }

        switch (result) {
        case CCreatureController.RESULT_DONE:
        case CCreatureController.RESULT_INVALID:
            creature.tagertClear();
            return;

        case CCreatureController.RESULT_RUNNING:
        case CCreatureController.RESULT_BLOCKED:
        case CCreatureController.RESULT_STOP:
        default:
            return;
        }
    }
}
