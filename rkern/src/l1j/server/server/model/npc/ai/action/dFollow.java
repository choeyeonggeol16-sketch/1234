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

        if (follower == null || master == null) {
            return true;
        }

        if (follower.getMapId() != master.getMapId()
                || follower.getMap() == null) {
            MoveIntentCache.clearGoal(follower);
            MoveIntentCache.clearProgress(follower);
            SetMoving.clear(follower);
            return true;
        }

        // Preserve L1's 2-tile follow distance. A completed follow must
        // retire the cached goal so old positions cannot survive a teleport,
        // map switch, target replacement, or combat-to-follow transition.
        if (follower.getLocation()
                .getTileLineDistance(master.getLocation())
                <= CMoveAction.FOLLOW_RADIUS) {
            MoveIntentCache.clearGoal(follower);
            MoveIntentCache.clearProgress(follower);
            SetMoving.clear(follower);
            return false;
        }

        return CMoveAction.followPosition(follower, master);
    }
}
