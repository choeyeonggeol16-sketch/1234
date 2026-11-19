package l1j.server.server.model.npc.ai.action;

import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1NpcInstance;

/**
 * Verified native semantics:
 * - OnStart: 777ms initial timer.
 * - OnTick: while current owner-target Euclidean distance <= saved distance,
 *           schedule another 555ms and keep waiting.
 * - when target moves outside the saved distance, recreate CMoveToTargetAction.
 *
 * This is NOT the body-collision retry action. Body collision stays inside
 * SetMoving. OnStart does not clear SetMoving/PathFind state.
 */
public final class CMoveToTargetWaitAction {

    private static final long START_WAIT_MS = 777L;
    private static final long HOLD_WAIT_MS = 555L;

    private CMoveToTargetWaitAction() {
    }

    public static void set(
            final L1NpcInstance creature,
            final L1Character target,
            final int savedDistance,
            final byte pendingAction) {

        if (creature == null || target == null) {
            return;
        }

        final int distance = Math.max(0, savedDistance);
        // Preserve the original wait deadline when the same waiting action
        // is selected again; a genuine target/range/pending change starts a
        // fresh wait. Repeated SEE/ATTACKED events must not postpone chase.
        if (ActionAccess.currentAction(creature)
                    == CCreatureController.ACTION_MOVE_TO_TARGET_WAIT
                && creature.getTarget() == target
                && creature._waitTargetId == target.getId()
                && creature._waitDistance == distance
                && creature._waitPendingAction == pendingAction) {
            return;
        }

        creature._waitTargetId = target.getId();
        creature._waitDistance = distance;
        creature._waitPendingAction = pendingAction;

        CCreatureController.set(
                creature,
                CCreatureController.ACTION_MOVE_TO_TARGET_WAIT,
                pendingAction);

        onStart(creature);
    }

    public static void onStart(
            final L1NpcInstance creature) {

        if (creature == null) {
            return;
        }

        /* Native OnStart only arms the initial wait timer. */
        ActionAccess.sleep(
                creature,
                START_WAIT_MS);
    }

    public static byte onTick(
            final L1NpcInstance creature,
            final L1Character target) {

        if (creature == null) {
            return CCreatureController.RESULT_INVALID;
        }

        // A queued tick must never resume a successor action. The owner may
        // have switched targets or retired this WAIT during a target read.
        if (ActionAccess.currentAction(creature)
                != CCreatureController.ACTION_MOVE_TO_TARGET_WAIT) {
            return CCreatureController.RESULT_RUNNING;
        }
        final int generation = creature.npcActionGeneration();
        if (!valid(creature, target)
                || creature.getTarget() != target
                || creature._waitTargetId != target.getId()) {
            return CCreatureController.RESULT_INVALID;
        }

        final long dx =
                (long) target.getX() - creature.getX();
        final long dy =
                (long) target.getY() - creature.getY();
        final long distance2 = dx * dx + dy * dy;
        final long saved = Math.max(0, creature._waitDistance);

        // The target getters above are not an atomic snapshot of the action.
        // Never arm a stale WAIT timer or promote its old target into a new
        // MOVE_TO_TARGET if another action took ownership in between.
        if (creature.npcActionGeneration() != generation
                || ActionAccess.currentAction(creature)
                    != CCreatureController.ACTION_MOVE_TO_TARGET_WAIT
                || creature.getTarget() != target
                || creature._waitTargetId != target.getId()
                || creature.isDead()) {
            return CCreatureController.RESULT_RUNNING;
        }

        if (distance2 <= saved * saved) {
            ActionAccess.sleep(
                    creature,
                    HOLD_WAIT_MS);
            return CCreatureController.RESULT_RUNNING;
        }

        final byte pending = creature._waitPendingAction;
        // Native WAIT chooses relative chase only when both objects have a
        // compatible world/container. L1's 2-D container is its map id.
        final boolean sameMap = target.getMapId() == creature.getMapId();
        clear(creature);
        if (sameMap) {
            CMoveToTargetRelativeAction.set(creature, target, pending);
        } else {
            CMoveToTargetAction.set(creature, pending);
        }

        return CCreatureController.RESULT_RUNNING;
    }

    public static void onEnd(
            final L1NpcInstance creature,
            final L1Character target,
            final byte result) {

        if (creature == null
                || result != CCreatureController.RESULT_INVALID
                || ActionAccess.currentAction(creature)
                        != CCreatureController.ACTION_MOVE_TO_TARGET_WAIT) {
            return;
        }

        final L1Character current = creature.getTarget();
        final int waitingFor = creature._waitTargetId;
        if (current != null && current != target) {
            // A different target took over while this tick ran. Never retire
            // a newer WAIT that already belongs to that target.
            if (waitingFor == current.getId()) {
                return;
            }
            // This is still the old WAIT (or it no longer names a live
            // target). Retire its cadence and reschedule desire selection for
            // the new target; otherwise INVALID repeats forever on every tick.
            CCreatureController.rethinkNow(creature);
            return;
        }

        if (current != null && waitingFor != current.getId()) {
            // The target changed *before* onTick took its snapshot, so the
            // saved WAIT id and current target disagree even though the tick
            // was dispatched with that current target.
            CCreatureController.rethinkNow(creature);
            return;
        }

        // A genuinely invalid current WAIT may retire its own target.
        // Never clear an unrelated successor through a stale callback.
        clear(creature);
        if (current == target) {
            creature.tagertClear();
        } else {
            CCreatureController.rethinkNow(creature);
        }
    }

    public static void clear(
            final L1NpcInstance creature) {

        if (creature == null) {
            return;
        }

        creature._waitTargetId = 0;
        creature._waitDistance = 0;
        creature._waitPendingAction =
                CCreatureController.ACTION_NONE;
    }

    private static boolean valid(
            final L1NpcInstance creature,
            final L1Character target) {

        return creature != null
                && target != null
                && !target.isDead()
                && target.getCurrentHp() > 0
                && target.getMapId() == creature.getMapId()
                && (!target.isInvisble()
                        || creature.getNpcTemplate().is_agrocoi());
    }
}
