package l1j.server.server.model.npc.ai.action;

import l1j.server.server.model.L1MobGroupInfo;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.ai.core.WorldCollisionHash;
import l1j.server.server.model.map.L1Map;

/**
 * L1 2D formation adapter. NC exposes formationId/formationIndex and
 * dMoveFormation; its exact coordinate formula is not assumed here.
 * Group identity and stable member slots belong to L1MobGroupInfo.
 * Existing CIO owner, pathfinding and tile-occupancy commit are reused.
 */
public final class dMoveFormation {
    private static final int[] DX = {0, 1, 1, 1, 0, -1, -1, -1};
    private static final int[] DY = {-1, -1, 0, 1, 1, 1, 0, -1};
    // Rear, rear-left/right, flanks, front diagonals, front.
    private static final int[] ORDER = {4, 3, 5, 2, 6, 1, 7, 0};
    private static final byte WAIT_FALLBACK = 64;
    private static final long RETRY_PRIMARY_MS = 1_000L;
    private static final WorldCollisionHash COLLISION = WorldCollisionHash.getInstance();

    private dMoveFormation() {}

    /** The leader consumes no position; each other member has a unique slot. */
    static int relativeIndex(final int member, final int leader) {
        if (member < 0 || leader < 0 || member == leader) return -1;
        return member < leader ? member : member - 1;
    }

    static long formationPosition(final int x, final int y, final int heading,
            final int index) {
        if (index < 0) return MoveIntentCache.NO_POS;
        final int radius = ((index >>> 3) + 1) * 2;
        final int dir = (heading + ORDER[index & 7]) & 7;
        return MoveIntentCache.pack(x + DX[dir] * radius, y + DY[dir] * radius);
    }

    private static boolean valid(final L1NpcInstance npc, final long pos) {
        if (pos == MoveIntentCache.NO_POS) return false;
        final int x = MoveIntentCache.unpackX(pos), y = MoveIntentCache.unpackY(pos);
        final L1Map map = npc.getMap();
        return map != null && map.isInMap(x, y) && map.isPassable(x, y)
                && ((x == npc.getX() && y == npc.getY())
                    || COLLISION.testStep((short) npc.getMapId(), x, y, npc.getId())
                        == WorldCollisionHash.MOVE_CLEAR);
    }

    private static long fallback(final L1NpcInstance npc, final L1NpcInstance leader,
            final long primary, final long rejected) {
        final int x = MoveIntentCache.unpackX(primary), y = MoveIntentCache.unpackY(primary);
        long best = MoveIntentCache.NO_POS, distance = Long.MAX_VALUE;
        // A one-tile sidestep normally avoids taking another assigned slot.
        for (int i = 0; i < 8; i++) {
            final int dir = (i + npc.getFormationIndex()) & 7;
            final long pos = MoveIntentCache.pack(x + DX[dir], y + DY[dir]);
            if (pos == rejected || !valid(npc, pos)) continue;
            if (MoveIntentCache.unpackX(pos) == leader.getX()
                    && MoveIntentCache.unpackY(pos) == leader.getY()) continue;
            final long dx = (long) MoveIntentCache.unpackX(pos) - npc.getX();
            final long dy = (long) MoveIntentCache.unpackY(pos) - npc.getY();
            final long d = dx * dx + dy * dy;
            if (d < distance) {
                best = pos;
                distance = d;
            }
        }
        if (best != MoveIntentCache.NO_POS) return best;
        final long nearby = CMoveAction.GetFollowPosition(npc, leader);
        return nearby != rejected && valid(npc, nearby) ? nearby : MoveIntentCache.NO_POS;
    }

    private static void remember(final L1NpcInstance npc, final L1NpcInstance leader,
            final long primary, final long goal, final long now) {
        MoveIntentCache.clearProgress(npc);
        MoveIntentCache.clearWait(npc);
        MoveIntentCache.setGoal(npc, MoveIntentCache.KIND_FORMATION, leader.getId(),
                MoveIntentCache.unpackX(primary), MoveIntentCache.unpackY(primary),
                MoveIntentCache.unpackX(goal), MoveIntentCache.unpackY(goal));
        if (goal != primary) {
            MoveIntentCache.beginWait(npc, now + RETRY_PRIMARY_MS, WAIT_FALLBACK);
        }
    }

    private static boolean sameGroup(final L1NpcInstance npc, final L1MobGroupInfo group,
            final L1NpcInstance leader) {
        return npc != null && group != null && leader != null && npc != leader
                && npc.getMobGroupInfo() == group && leader.getMobGroupInfo() == group
                && group.getLeader() == leader && !npc.isDead() && !npc.isDestroyed()
                && npc.getCurrentHp() > 0 && !leader.isDead() && !leader.isDestroyed()
                && leader.getCurrentHp() > 0 && npc.getMapId() == leader.getMapId();
    }

    /** One owner step, never stops AI merely because a slot/route is blocked. */
    static boolean follow(final L1NpcInstance npc, final L1MobGroupInfo group,
            final L1NpcInstance leader) {
        if (!sameGroup(npc, group, leader) || !npc.isMovable() || npc.isRest()) {
            clear(npc);
            if (npc != null) ActionAccess.sleep(npc, 500L);
            return false;
        }
        if (npc.발묶임상태()) {
            ActionAccess.sleep(npc, 500L);
            return false;
        }
        final int index = relativeIndex(npc.getFormationIndex(), leader.getFormationIndex());
        final long primary = formationPosition(leader.getX(), leader.getY(),
                leader.getHeading(), index);
        if (primary == MoveIntentCache.NO_POS) {
            clear(npc);
            ActionAccess.sleep(npc, 500L);
            return false;
        }

        final long now = System.currentTimeMillis();
        long goal = MoveIntentCache.getGoal(npc, MoveIntentCache.KIND_FORMATION,
                leader.getId(), MoveIntentCache.unpackX(primary),
                MoveIntentCache.unpackY(primary), 0);
        if (!valid(npc, goal) || (goal != primary
                && MoveIntentCache.waitUntil(npc) <= now)) {
            goal = valid(npc, primary) ? primary
                    : fallback(npc, leader, primary, MoveIntentCache.NO_POS);
            if (goal == MoveIntentCache.NO_POS) {
                clear(npc);
                ActionAccess.sleep(npc, 500L);
                return false;
            }
            remember(npc, leader, primary, goal, now);
        }
        // Validate the membership/leader again before committing a movement step.
        if (!sameGroup(npc, group, leader) || !npc.isMovable() || npc.isRest()
                || npc.발묶임상태()) {
            clear(npc);
            ActionAccess.sleep(npc, 500L);
            return false;
        }
        final byte result = CMoveAction.stepFollowGoal(npc, goal);
        if (result == CCreatureController.RESULT_BLOCKED
                || result == CCreatureController.RESULT_INVALID) {
            final long alternate = fallback(npc, leader, primary, goal);
            if (alternate != MoveIntentCache.NO_POS) {
                remember(npc, leader, primary, alternate, now);
            } else {
                // stepFollowGoal may already have cleared KIND_FORMATION.
                // Retire its remaining wait/progress and path on this owner.
                MoveIntentCache.clearAll(npc);
                SetMoving.clear(npc);
            }
            ActionAccess.sleep(npc, Math.max(npc.getSleepTime(), 300L));
        }
        return false;
    }

    static void clearIfActive(final L1NpcInstance npc) {
        if (MoveIntentCache.clearFormation(npc)) SetMoving.clear(npc);
    }

    static void clear(final L1NpcInstance npc) {
        clearIfActive(npc);
    }
}
