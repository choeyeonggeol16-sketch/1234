package l1j.server.server.model.npc.ai.action;

import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1GuardInstance;
import l1j.server.server.model.Instance.L1CastleGuardInstance;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1World;

/**
 * 정지 상태 action.
 *
 * 객체를 생성하지 않는 stateless 구현이다.
 */
public final class CStandAction {

    private static final long GUARD_IDLE_MS = 40_000L;
    private static final long GUARD_RETRY_MS = 1_000L;

    private CStandAction() {
    }

    static boolean isGuard(final L1NpcInstance npc) {
        return npc instanceof L1GuardInstance || npc instanceof L1CastleGuardInstance;
    }

    static boolean atHome(final L1NpcInstance npc) {
        return npc.getX() == npc.getHomeX() && npc.getY() == npc.getHomeY();
    }

    static boolean canInterruptGuardStand(final L1NpcInstance npc) {
        final L1Character target = npc.getTarget();
        return target != null && (npc.isMovable() || npc.isAttackPosition(
                target, npc.getNpcTemplate().get_ranged()));
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

        if (isGuard(creature)) {
            // guard_fixed: keep the admitted target, but never chase it.
            if (creature.getTarget() != null) {
                if (canInterruptGuardStand(creature)) {
                    return CCreatureController.RESULT_DONE;
                }
                ActionAccess.sleep(creature, GUARD_RETRY_MS);
                return CCreatureController.RESULT_RUNNING;
            }
            if (creature.isMovable() && !atHome(creature)) {
                // A failed home request or external relocation must be retried.
                ActionAccess.sleep(creature, GUARD_RETRY_MS);
                return CCreatureController.RESULT_DONE;
            }
            if (!L1World.getInstance().hasRecognizePlayerFast(creature)) {
                return CCreatureController.RESULT_DONE;
            }
            // guard_stand MOVE_TO_FINISHED -> AddDoNothingDesire(40, 30).
            // Existing owner wheel provides the timed hold. An admitted combat
            // event interrupts it through AiRuntime; no extra timer is needed.
            ActionAccess.sleep(creature, GUARD_IDLE_MS);
            return CCreatureController.RESULT_RUNNING;
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
