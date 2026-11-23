package l1j.server.server.model.npc.ai.action;

import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1NpcInstance;

/**
 * NC CNPC::dFollow + GetFollowPosition, adapted to the L1 2D tile world.
 *
 * All follower types use the existing CIO-owned CMoveAction path and
 * MoveIntentCache instead of choosing a new random destination each tick.
 * Real occupancy remains owned by WorldCollisionHash/TileOccupancyNpc.
 *
 * L1 pet/summon/companion command modes are handled by their existing
 * noTarget() implementations; this class only performs normal following.
 */
public final class dFollow {
    private dFollow() {
    }

    /**
     * @return true only when following cannot continue (the previous
     *         noTarget() contract); false when following/standing normally.
     */
    public static boolean follow(
            final L1NpcInstance follower,
            final L1Character master) {

        if (follower == null) {
            return true;
        }
        if (master == null) {
            clear(follower);
            return true;
        }

        if (follower.getMapId() != master.getMapId()
                || follower.getMap() == null) {
            clear(follower);
            return true;
        }

        // Preserve L1's 2-tile follow distance. A completed follow must
        // retire the cached goal so old positions cannot survive a teleport,
        // map switch, target replacement, or combat-to-follow transition.
        if (follower.getLocation()
                .getTileLineDistance(master.getLocation())
                <= CMoveAction.FOLLOW_RADIUS) {
            clear(follower);
            return false;
        }

        return CMoveAction.followPosition(follower, master);
    }

    /** Release the previous NC GetFollowPosition intention without allocating
     *  a new collection or adding a worker. The caller owns the NPC CIO tick. */
    public static void clear(final L1NpcInstance follower) {
        if (follower == null) return;
        MoveIntentCache.clearGoal(follower);
        MoveIntentCache.clearProgress(follower);
        // close-range noTarget() runs repeatedly on every follower.
        // Once the previous route is retired, do not invalidate A* state
        // again on every idle tick. Only live movement needs this cleanup.
        if (follower._moveTargetId != 0
                || follower._moveSide != -1
                || follower._moveCollision != 0
                || follower._pathFindFailCount != 0) {
            SetMoving.clear(follower);
        }
    }
}
