package l1j.server.server.model.npc.ai.action;

import l1j.server.server.model.Instance.L1NpcInstance;

/**
 * 정지 상태 action.
 *
 * 객체를 생성하지 않는 stateless 구현이다.
 */
public final class CStandAction {

    private CStandAction() {
    }

    /**
     * action 진입.
     */
    public static void set(final L1NpcInstance creature) {
        if (creature == null) {
            return;
        }

        CCreatureController.set(
                creature,
                CCreatureController.ACTION_STAND);

        onStart(creature);
    }

    /**
     * action 시작 시 한 번만 호출된다.
     * 현재는 별도 시작 상태가 없어 no-op이다.
     */
    public static void onStart(final L1NpcInstance creature) {
        if (creature == null) {
            return;
        }
    }

    /**
     * 한 번의 정지 tick.
     * 현재 서버 semantics에서는 한 tick 후 다시 상태를 평가한다.
     */
    public static byte onTick(final L1NpcInstance creature) {
        if (creature == null) {
            return CCreatureController.RESULT_INVALID;
        }

        return CCreatureController.RESULT_DONE;
    }

    /**
     * action 종료 처리.
     * DONE/INVALID이면 pending action 또는 THINK로 복귀한다.
     */
    public static void onEnd(
            final L1NpcInstance creature,
            final byte result) {

        if (creature == null) {
            return;
        }

        switch (result) {
        case CCreatureController.RESULT_DONE:
        case CCreatureController.RESULT_INVALID:
            CCreatureController.doPendingAction(creature);
            return;

        case CCreatureController.RESULT_RUNNING:
        case CCreatureController.RESULT_BLOCKED:
        case CCreatureController.RESULT_STOP:
        default:
            return;
        }
    }
}
