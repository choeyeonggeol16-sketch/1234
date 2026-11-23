package l1j.server.server.model.npc.ai.action;

import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.npc.ai.NpcServer;

/**
 * NC CNPC::dMoveToTarget (0x004972ac), general-purpose scripted target
 * movement. Distinct from L1's ATTACK/CHASE CMoveToTargetAction.
 *
 * Native: drop invisible/missing target, abort beyond 4000 world units,
 * stop within stopDistance + collision radii, send midpoint destination,
 * retry every 1000ms and retire after >15 path troubles. L1 adaptation:
 * 4000 units -> 40 tiles; character occupied tile -> one-tile radius;
 * midpoint -> Chebyshev grid midpoint; all physical steps use the existing
 * L1 movement/occupancy commit. No per-tick objects or new workers.
 */
public final class dMoveToTarget {
    private static final long NEXT_MS = 1_000L;
    private static final int MAX_DISTANCE_TILES = 40;
    private static final int MAX_PATH_FAILURES = 15;

    private dMoveToTarget() {
    }

    /** Install a scripted MOVE_TO_TARGET on the NPC's fixed CIO owner. */
    public static boolean request(
            final L1NpcInstance npc,
            final int targetObjectId,
            final int stopDistanceTiles) {
        if (npc == null || targetObjectId <= 0
                || !NpcServer.getInstance().isOwnerThread(npc)
                || !npc.aiEngineIsRunning()
                || stopDistanceTiles < 0) {
            return false;
        }
        final L1Object target =
                L1World.getInstance().findObject(targetObjectId);
        if (!validTarget(npc, target)) {
            return false;
        }
        npc._scriptMoveMapId = npc.getMapId();
        npc._scriptMoveTargetId = targetObjectId;
        npc._scriptMoveStopDistance = stopDistanceTiles;
        npc._scriptMoveGoalXY = 0L;
        npc._pathFindFailCount = 0;
        CMoveAction.invalidatePath(npc);
        CCreatureController.submit(npc, CCreatureController.ACTION_MOVE_TO_OBJECT);
        return true;
    }

    /** Resolve only the captured object-ID at each native-style action tick. */
    static byte onTick(final L1NpcInstance npc) {
        if (npc == null || npc._scriptMoveTargetId <= 0
                || npc._scriptMoveMapId != npc.getMapId()) {
            return CCreatureController.RESULT_INVALID;
        }
        final L1Object target = L1World.getInstance()
                .findObject(npc._scriptMoveTargetId);
        return dMoveToTarget(npc, target, npc._scriptMoveStopDistance);
    }

    /** One step of NC noncombat dMoveToTarget; does not alter combat hate. */
    public static byte dMoveToTarget(
            final L1NpcInstance npc,
            final L1Object target,
            final int stopDistanceTiles) {
        if (npc == null || npc.isDestroyed() || npc.isDead()
                || npc.getCurrentHp() <= 0
                || !validTarget(npc, target)) {
            return CCreatureController.RESULT_INVALID;
        }

        final int x = npc.getX();
        final int y = npc.getY();
        final int tx = target.getX();
        final int ty = target.getY();
        final long dx = (long) tx - x;
        final long dy = (long) ty - y;
        // Native dMoveToTarget rejects distance >4000, not >=4000.
        if (outsideLeash(dx, dy)) {
            return CCreatureController.RESULT_INVALID;
        }

        // The original collision-radius sum maps to one L1 tile for
        // characters (whose tile is occupied), zero for ground objects.
        final int radius = target instanceof L1Character ? 1 : 0;
        final long required = Math.max(0L, (long) stopDistanceTiles) + radius;
        if (reached(dx, dy, required)) {
            npc._pathFindFailCount = 0;
            return CCreatureController.RESULT_DONE;
        }
        if (!npc.isMovable()) {
            return CCreatureController.RESULT_INVALID;
        }
        if (npc.발묶임상태()) {
            ActionAccess.sleep(npc, NEXT_MS);
            return CCreatureController.RESULT_RUNNING;
        }
        if (npc._pathFindFailCount > MAX_PATH_FAILURES) {
            return CCreatureController.RESULT_INVALID;
        }

        // Native destination = (actor + target) / 2. Preserve the midway
        // cadence in 2D, avoiding the same-tile midpoint at distance one.
        int goalX = x + (int) (dx / 2L);
        int goalY = y + (int) (dy / 2L);
        if (goalX == x && goalY == y) {
            goalX = tx;
            goalY = ty;
        }

        final int dir = CMoveAction.direction(npc, goalX, goalY);
        if (dir >= 0 && dir < 8) {
            if (ActionAccess.moveOneStep(npc, dir)) {
                npc._pathFindFailCount = 0;
                ActionAccess.sleep(npc, Math.max(NEXT_MS, npc.getSleepTime()));
                return CCreatureController.RESULT_RUNNING;
            }
            // Occupancy can change after direction resolution.
            ActionAccess.sleep(npc, NEXT_MS);
            return CCreatureController.RESULT_RUNNING;
        }

        if (dir == CMoveAction.DIR_HARD_BLOCK
                && ++npc._pathFindFailCount > MAX_PATH_FAILURES) {
            return CCreatureController.RESULT_INVALID;
        }
        if (dir == CMoveAction.DIR_INVALID) {
            return CCreatureController.RESULT_INVALID;
        }
        ActionAccess.sleep(npc, NEXT_MS);
        return CCreatureController.RESULT_RUNNING;
    }

    /** Euclidean 2D projection of native 4000-unit search boundary. */
    static boolean outsideLeash(final long dx, final long dy) {
        return dx * dx + dy * dy > (long) MAX_DISTANCE_TILES * MAX_DISTANCE_TILES;
    }

    /** L1 Chebyshev approach distance and the object's occupancy radius. */
    static boolean reached(final long dx, final long dy, final long stop) {
        return Math.max(Math.abs(dx), Math.abs(dy)) <= stop;
    }

    static boolean validTarget(final L1NpcInstance npc, final L1Object target) {
        if (npc == null || target == null
                || target == npc
                || npc.getMapId() != target.getMapId()) {
            return false;
        }
        if (target instanceof L1Character) {
            final L1Character living = (L1Character) target;
            if (living.isDead() || living.getCurrentHp() <= 0
                    || (living.isInvisble()
                            && !npc.getNpcTemplate().is_agrocoi())) {
                return false;
            }
        }
        if (target instanceof L1NpcInstance
                && ((L1NpcInstance) target).isDestroyed()) {
            return false;
        }
        return true;
    }
}
