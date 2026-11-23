package l1j.server.server.model.npc.ai.action;

import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.ai.core.WorldCollisionHash;
import l1j.server.server.model.map.L1Map;

/**
 * NC CNPC::dFlee / dFleeEx projected onto L1's eight-direction tile world.
 *
 * dFlee  (NC 0x00492948): complete at >= default flee distance.
 * dFleeEx(NC 0x00492e14): complete only at > caller distance.
 *
 * Both use the existing CIO owner, NPC movement commit/occupancy and no
 * private worker or queue. NC's 120..10 world-unit destination backoff
 * cannot be copied to L1 cells: the projection tests the outward tile first,
 * then the two neighboring outward headings. A failed move retries after
 * 1000ms rather than abandoning the target or busy-spinning.
 */
public final class dFlee {
    // L1's existing 15-tile flee range; NC uses 1500 continuous world units.
    static final int DEFAULT_DISTANCE_TILES = 15;
    private static final long RETRY_MS = 1000L;

    private static final int[] DX = {0, 1, 1, 1, 0, -1, -1, -1};
    private static final int[] DY = {-1, -1, 0, 1, 1, 1, 0, -1};
    private static final WorldCollisionHash COLLISION =
            WorldCollisionHash.getInstance();

    private dFlee() {
    }

    /** Standard NC FLEE desire. Result is a CCreatureController action result. */
    public static byte dFlee(
            final L1NpcInstance creature,
            final L1Character target) {
        if (creature == null || target == null || !validActor(creature)) {
            return CCreatureController.RESULT_INVALID;
        }
        if (!validTarget(creature, target)) {
            // NC removes this exact FLEE on target death / disappearance.
            return CCreatureController.RESULT_DONE;
        }
        final int distance = creature.getLocation()
                .getTileLineDistance(target.getLocation());
        if (reachedDistance(distance, DEFAULT_DISTANCE_TILES, false)) {
            return CCreatureController.RESULT_DONE;
        }
        return moveAway(creature, target);
    }

    /**
     * NC dFleeEx(targetObjectId, fleeDistance): 1 = no movement necessary,
     * 0 = movement/retry submitted. Return values here are action codes.
     *
     * Not used as the generic escape policy: the caller must explicitly ask
     * for a distance-constrained flee, just as in native NPC script actions.
     */
    public static byte dFleeEx(
            final L1NpcInstance creature,
            final int targetObjectId,
            final int fleeDistanceTiles) {
        if (creature == null || targetObjectId <= 0) {
            return CCreatureController.RESULT_INVALID;
        }
        final L1Object object = L1World.getInstance().findObject(targetObjectId);
        return dFleeEx(creature,
                object instanceof L1Character ? (L1Character) object : null,
                fleeDistanceTiles);
    }

    /** Direct target form for already-resolved CIO action targets. */
    public static byte dFleeEx(
            final L1NpcInstance creature,
            final L1Character target,
            final int fleeDistanceTiles) {
        if (creature == null || target == null
                || !validActor(creature) || !validTarget(creature, target)) {
            return CCreatureController.RESULT_INVALID;
        }
        final int distance = creature.getLocation()
                .getTileLineDistance(target.getLocation());
        if (reachedDistance(distance, fleeDistanceTiles, true)) {
            return CCreatureController.RESULT_DONE;
        }
        return moveAway(creature, target);
    }

    // Normal flee uses >=; native dFleeEx uses strictly >.
    static boolean reachedDistance(
            final int distance,
            final int fleeDistance,
            final boolean extended) {
        return extended ? distance > fleeDistance : distance >= fleeDistance;
    }

    private static boolean validActor(final L1NpcInstance creature) {
        return !creature.isDead() && creature.getCurrentHp() > 0;
    }

    private static boolean validTarget(
            final L1NpcInstance creature, final L1Character target) {
        return !target.isDead() && target.getCurrentHp() > 0
                && target.getMapId() == creature.getMapId();
    }

    /** NC's outward vector reduced to one L1 occupied tile per owner tick. */
    private static byte moveAway(
            final L1NpcInstance creature,
            final L1Character target) {
        final L1Map map = creature.getMap();
        if (map == null || !creature.isMovable()) {
            ActionAccess.sleep(creature, RETRY_MS);
            return CCreatureController.RESULT_RUNNING;
        }

        // NC uses an equal-component vector when actor and target overlap.
        final int away = creature.getX() == target.getX()
                        && creature.getY() == target.getY()
                ? 3 // (positive x, positive y): southeast on L1 tiles.
                : creature.targetReverseDirection(target.getX(), target.getY());

        // Native shortens its ray on geometry failure. In 2D a ray shorter
        // than one tile has no destination; prefer its adjacent headings.
        final int x = creature.getX();
        final int y = creature.getY();
        final int mapId = creature.getMapId();
        for (int step = 0; step < 3; step++) {
            final int heading = step == 0 ? away
                    : (away + (step == 1 ? 7 : 1)) & 7;
            final int nextX = x + DX[heading];
            final int nextY = y + DY[heading];

            if (!map.isMovePassableGeoNpc(x, y, heading)
                    || !map.isPassable(nextX, nextY)
                    || COLLISION.testStep(
                            (short) mapId, nextX, nextY, creature.getId())
                            != WorldCollisionHash.MOVE_CLEAR) {
                continue;
            }
            if (ActionAccess.moveOneStep(creature, heading)) {
                ActionAccess.sleep(creature,
                        Math.max(RETRY_MS, creature.getSleepTime()));
                return CCreatureController.RESULT_RUNNING;
            }
        }

        // No accepted tile. Do not send invalid directions or create a
        // persistent path-failure loop; revisit on the existing CIO wheel.
        ActionAccess.sleep(creature, RETRY_MS);
        return CCreatureController.RESULT_RUNNING;
    }
}
