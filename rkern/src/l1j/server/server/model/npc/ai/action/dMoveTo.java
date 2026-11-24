package l1j.server.server.model.npc.ai.action;

import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.ai.core.WorldCollisionHash;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.npc.ai.NpcServer;

/**
 * NC CNPC::dMoveTo (0x004934a4), adapted to L1's 2D eight-direction tiles.
 *
 * NC continuous-world distances of 8192 and sqrt(1000) map to an 82-tile
 * long jump and an exact-tile arrival respectively (100 world units/tile).
 * Existing L1 movement and teleport commits own collision, broadcasts,
 * visibility, destination reservation and world changes.
 *
 * The request API is strictly CIO-owner-only. Its payload lives in the
 * NPC's primitive fields, not an allocated command or a second work queue.
 */
public final class dMoveTo {
    private static final long NEXT_MS = 1_000L;
    private static final long TELEPORT_MS = 3_000L;
    private static final long TELEPORT_DISTANCE_SQ = 82L * 82L;
    private static final int PATH_FAIL_TELEPORT = 20;
    private static final WorldCollisionHash COLLISION =
            WorldCollisionHash.getInstance();

    private dMoveTo() {
    }

    /** Native-like positional desire entry, installed on its fixed CIO owner. */
    public static boolean request(
            final L1NpcInstance npc,
            final int x,
            final int y) {
        if (npc == null
                || !NpcServer.getInstance().isOwnerThread(npc)
                || !npc.aiEngineIsRunning()
                || !validDestination(npc, x, y)) {
            return false;
        }
        npc._scriptMoveMapId = npc.getMapId();
        npc._scriptMoveGoalXY = MoveIntentCache.pack(x, y);
        npc._scriptMoveTargetId = 0;
        npc._scriptMoveStopDistance = 0;
        npc._pathFindFailCount = 0;
        CMoveAction.invalidatePath(npc);
        CCreatureController.submit(npc, CCreatureController.ACTION_MOVE_TO_POINT);
        return true;
    }

    /** Execute the stored DESIRE_MOVE_TO through the existing timer wheel. */
    static byte onTick(final L1NpcInstance npc) {
        if (npc == null || npc._scriptMoveMapId != npc.getMapId()) {
            return CCreatureController.RESULT_INVALID;
        }
        return dMoveTo(npc,
                MoveIntentCache.unpackX(npc._scriptMoveGoalXY),
                MoveIntentCache.unpackY(npc._scriptMoveGoalXY));
    }

    /** One owner tick, without allocating a Point/path object. */
    public static byte dMoveTo(
            final L1NpcInstance npc,
            final int x,
            final int y) {
        if (npc == null || npc.isDestroyed()
                || npc.isDead() || npc.getCurrentHp() <= 0
                || !validDestination(npc, x, y)) {
            return CCreatureController.RESULT_INVALID;
        }

        final long dx = (long) x - npc.getX();
        final long dy = (long) y - npc.getY();
        final long distanceSq = dx * dx + dy * dy;

        // NC dMoveTo: completion radius sqrt(1000) = 31.62 units;
        // on L1's 100-unit tiles, only the exact destination is inside.
        if (distanceSq == 0L) {
            npc._pathFindFailCount = 0;
            return CCreatureController.RESULT_DONE;
        }
        if (!npc.isMovable()) {
            return CCreatureController.RESULT_INVALID;
        }
        if (npc.발묶임상태()) {
            CCreatureController.sleep(npc, NEXT_MS);
            return CCreatureController.RESULT_RUNNING;
        }

        if (distanceSq >= TELEPORT_DISTANCE_SQ) {
            if (teleportIfFree(npc, x, y)) {
                CCreatureController.sleep(npc, TELEPORT_MS);
                npc._pathFindFailCount = 0;
                return CCreatureController.RESULT_RUNNING;
            }
            CCreatureController.sleep(npc, NEXT_MS);
            return CCreatureController.RESULT_RUNNING;
        }

        final int dir = CMoveAction.direction(npc, x, y);
        if (dir >= 0 && dir < 8) {
            if (CMoveAction.moveOneStep(npc, dir)) {
                npc._pathFindFailCount = 0;
                CCreatureController.sleep(npc, Math.max(NEXT_MS, npc.getSleepTime()));
                return CCreatureController.RESULT_RUNNING;
            }
            // Reservation lost between path probe and physical commit.
            CCreatureController.sleep(npc, NEXT_MS);
            return CCreatureController.RESULT_RUNNING;
        }

        if (dir == CMoveAction.DIR_HARD_BLOCK) {
            if (++npc._pathFindFailCount > PATH_FAIL_TELEPORT) {
                // NC teleports after >20 consecutive path troubles.
                // L1 may teleport only into a genuinely free map cell.
                if (teleportIfFree(npc, x, y)) {
                    npc._pathFindFailCount = 0;
                    CCreatureController.sleep(npc, TELEPORT_MS);
                    return CCreatureController.RESULT_RUNNING;
                }
                // Never teleport onto a PC, NPC, door or occupied cell.
                // Keep the desire alive and retry when occupancy changes.
                npc._pathFindFailCount = PATH_FAIL_TELEPORT;
            }
        } else if (dir == CMoveAction.DIR_INVALID) {
            return CCreatureController.RESULT_INVALID;
        }

        // NC uses 1000ms both for a move request and a blocked route pass.
        CCreatureController.sleep(npc, NEXT_MS);
        return CCreatureController.RESULT_RUNNING;
    }

    static boolean validDestination(
            final L1NpcInstance npc, final int x, final int y) {
        final L1Map map = npc != null ? npc.getMap() : null;
        return map != null && map.isInMap(x, y) && map.isPassable(x, y);
    }

    static boolean isLongMove(
            final int fromX, final int fromY, final int x, final int y) {
        final long dx = (long) x - fromX;
        final long dy = (long) y - fromY;
        return dx * dx + dy * dy >= TELEPORT_DISTANCE_SQ;
    }

    private static boolean teleportIfFree(
            final L1NpcInstance npc, final int x, final int y) {
        if (!validDestination(npc, x, y)
                || COLLISION.testStep(
                        (short) npc.getMapId(), x, y, npc.getId())
                        != WorldCollisionHash.MOVE_CLEAR) {
            return false;
        }
        // Pre-claim the destination using the existing cross-PC/NPC
        // occupancy gates BEFORE teleport() mutates world coordinates and
        // broadcasts packets. Its internal teleportNpcOcc() is idempotent
        // for our already-owned tile. A testStep-only preflight could lose
        // the race and leave the NPC visibly on an unowned occupied tile.
        final int fromMap = npc.getMapId();
        final int fromX = npc.getX();
        final int fromY = npc.getY();
        if (npc.shouldUseNpcOccupancy()
                && !npc.teleportNpcOcc(
                        fromMap, fromX, fromY, fromMap, x, y)) {
            return false;
        }
        npc.teleport(x, y, npc.getHeading());
        CMoveAction.invalidatePath(npc);
        return npc.getX() == x && npc.getY() == y;
    }
}
