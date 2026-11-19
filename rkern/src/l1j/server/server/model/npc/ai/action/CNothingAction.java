package l1j.server.server.model.npc.ai.action;

import l1j.server.server.model.Instance.L1NpcInstance;

/**
 * 수행할 일이 없는 상태 action.
 *
 * 화면에 인식 가능한 플레이어가 없어
 * 현재 AI owner를 내려놓아도 되는 상태를 나타낸다.
 */
public final class CNothingAction {

    private CNothingAction() {
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
                CCreatureController.ACTION_NOTHING);

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
     * 한 번의 no-work tick.
     * STOP을 반환해 현재 AI 실행을 종료한다.
     */
    public static byte onTick(final L1NpcInstance creature) {
        if (creature == null) {
            return CCreatureController.RESULT_INVALID;
        }

        return CCreatureController.RESULT_STOP;
    }

    /**
     * action 종료 처리.
     * STOP은 상태를 NOTHING으로 유지한 채 AI 실행만 종료한다.
     * 외부 wake/event가 들어오면 Controller가 다시 THINK로 전환한다.
     */
    public static void onEnd(
            final L1NpcInstance creature,
            final byte result) {

        if (creature == null) {
            return;
        }

        switch (result) {
        case CCreatureController.RESULT_STOP:
            return;

        case CCreatureController.RESULT_INVALID:
            CCreatureController.think(creature);
            return;

        case CCreatureController.RESULT_RUNNING:
        case CCreatureController.RESULT_DONE:
        case CCreatureController.RESULT_BLOCKED:
        default:
            return;
        }
    }
}
