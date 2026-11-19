package l1j.server.server.model.npc.ai.action;

import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1NpcInstance;

/**
 * NC CMoveToTargetRelativeAction: distinct actor, target and container refs.
 *
 * Native GetTargetPos selects a target-position provider and projects it
 * through a container's coordinate frame. L1 maps have fixed 2-D tile frames:
 * the container reference is represented by the map id. All 8-direction
 * terrain, dynamic occupancy, reservations and fight hand-off remain owned by
 * the existing SetMoving / CMoveToTargetAction movement pipeline.
 *
 * The 3-D projected-vector timing/orientation is not copied to L1; doing so
 * would fabricate non-existent tile coordinates and bypass collision rules.
 */
public final class CMoveToTargetRelativeAction {
    private CMoveToTargetRelativeAction() {
    }

    public static void set(
            final L1NpcInstance creature,
            final L1Character target,
            final byte pendingAction) {
        if (creature == null || target == null
                || target.getId() == 0
                || creature.getTarget() != target
                || target.getMapId() != creature.getMapId()) {
            if (creature != null) {
                CCreatureController.rethinkNow(creature);
            }
            return;
        }

        if (ActionAccess.currentAction(creature)
                    == CCreatureController.ACTION_MOVE_TO_TARGET_RELATIVE
                && creature._relativeTargetId == target.getId()
                && creature._relativeMapId == creature.getMapId()) {
            ActionAccess.pendingAction(creature, pendingAction);
            return;
        }

        CCreatureController.set(creature,
                CCreatureController.ACTION_MOVE_TO_TARGET_RELATIVE,
                pendingAction);
        // Preserve existing chase movement/collision initialization.
        CMoveToTargetAction.onStart(creature);
        creature._relativeTargetId = target.getId();
        creature._relativeMapId = creature.getMapId();
    }

    public static void tick(
            final L1NpcInstance creature,
            final L1Character target) {
        if (creature == null || ActionAccess.currentAction(creature)
                != CCreatureController.ACTION_MOVE_TO_TARGET_RELATIVE) {
            return;
        }

        final int generation = creature.npcActionGeneration();
        if (target == null || creature.getTarget() != target
                || target.getId() != creature._relativeTargetId
                || creature.getMapId() != creature._relativeMapId
                || target.getMapId() != creature._relativeMapId) {
            CCreatureController.rethinkNow(creature);
            return;
        }

        final byte result = CMoveToTargetAction.onTick(
                creature, target, true,
                CCreatureController.ACTION_MOVE_TO_TARGET_RELATIVE);
        if (result != CCreatureController.RESULT_RUNNING
                && creature.npcActionGeneration() == generation
                && ActionAccess.currentAction(creature)
                    == CCreatureController.ACTION_MOVE_TO_TARGET_RELATIVE
                && creature.getTarget() == target
                && creature._relativeTargetId == target.getId()
                && creature._relativeMapId == creature.getMapId()
                && creature._relativeMapId == target.getMapId()) {
            CMoveToTargetAction.onEnd(
                    creature, target, result,
                    CCreatureController.ACTION_MOVE_TO_TARGET_RELATIVE);
        }
    }

    /**
     * CMoveToTargetRelativeAction::GetDestination, L1 2-D projection.
     *
     * NC uses targetPos + normalize(actorPos - targetPos) * actionDistance.
     * The L1 adaptation uses integer tile coordinates and returns packed
     * primitive coordinates, avoiding per-tick FVector / Point allocation.
     * This is a preferred approach point, never an occupancy reservation.
     */
    static long getDestination(final int actorX,
                               final int actorY,
                               final int targetX,
                               final int targetY,
                               final int distance) {
        final long dx = (long) actorX - targetX;
        final long dy = (long) actorY - targetY;
        final double length = Math.sqrt((double) dx * dx + (double) dy * dy);
        if (length < 1.0e-9d || distance <= 0) {
            return ((long) targetX << 32) | (targetY & 0xffffffffL);
        }
        final double scale = (double) distance / length;
        final int x = targetX + (int) Math.round(dx * scale);
        final int y = targetY + (int) Math.round(dy * scale);
        return ((long) x << 32) | (y & 0xffffffffL);
    }

    public static void clear(final L1NpcInstance creature) {
        if (creature == null) {
            return;
        }
        creature._relativeTargetId = 0;
        creature._relativeMapId = -1;
    }
}
