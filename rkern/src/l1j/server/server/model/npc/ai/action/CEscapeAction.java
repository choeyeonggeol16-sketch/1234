package l1j.server.server.model.npc.ai.action;

import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1NpcInstance;

/**
 * CIO lifecycle bridge for native CNPC::dFlee / dFleeEx.
 *
 * The native distance and movement rules live in dFlee; this adapter keeps
 * the existing L1 ACTION_ESCAPE and target/action hand-off semantics.
 */
public final class CEscapeAction {

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
        return dFlee.dFlee(creature, target);
    }

    /** Native dFleeEx variant for distance-constrained NPC actions. */
    public static byte onTickEx(
            final L1NpcInstance creature,
            final L1Character target,
            final int fleeDistanceTiles) {
        return dFlee.dFleeEx(creature, target, fleeDistanceTiles);
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
            // NC removes only the finished FLEE's target key. Never wipe a
            // newly chosen target B when stale flee A finishes on the CIO
            // owner. targetRemove() retires A's hate/desire exclusively.
            creature.targetRemove(target);
            if (ActionAccess.currentAction(creature)
                    == CCreatureController.ACTION_ESCAPE) {
                CCreatureController.think(creature);
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
