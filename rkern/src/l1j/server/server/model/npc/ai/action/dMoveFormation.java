package l1j.server.server.model.npc.ai.action;

import l1j.server.server.model.npc.spawn.group.L1MobGroupInfo;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.ai.core.WorldCollisionHash;
import l1j.server.server.model.map.L1Map;

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
        if (index < 0) return SetMoving.NO_POS;
        final int radius = ((index >>> 3) + 1) * 2;
        final int dir = (heading + ORDER[index & 7]) & 7;
        return SetMoving.pack(x + DX[dir] * radius, y + DY[dir] * radius);
    }

    private static boolean valid(final L1NpcInstance npc, final long pos) {
        if (pos == SetMoving.NO_POS) return false;
        final int x = SetMoving.unpackX(pos), y = SetMoving.unpackY(pos);
        final L1Map map = npc.getMap();
        return map != null && map.isInMap(x, y) && map.isPassable(x, y)
                && ((x == npc.getX() && y == npc.getY())
                    || COLLISION.testStep((short) npc.getMapId(), x, y, npc.getId())
                        == WorldCollisionHash.MOVE_CLEAR);
    }

    private static long fallback(final L1NpcInstance npc, final L1NpcInstance leader,
            final long primary, final long rejected) {
        final int x = SetMoving.unpackX(primary), y = SetMoving.unpackY(primary);
        long best = SetMoving.NO_POS, distance = Long.MAX_VALUE;
        // A one-tile sidestep normally avoids taking another assigned slot.
        for (int i = 0; i < 8; i++) {
            final int dir = (i + npc.getFormationIndex()) & 7;
            final long pos = SetMoving.pack(x + DX[dir], y + DY[dir]);
            if (pos == rejected || !valid(npc, pos)) continue;
            if (SetMoving.unpackX(pos) == leader.getX()
                    && SetMoving.unpackY(pos) == leader.getY()) continue;
            final long dx = (long) SetMoving.unpackX(pos) - npc.getX();
            final long dy = (long) SetMoving.unpackY(pos) - npc.getY();
            final long d = dx * dx + dy * dy;
            if (d < distance) {
                best = pos;
                distance = d;
            }
        }
        if (best != SetMoving.NO_POS) return best;
        final long nearby = CMoveAction.GetFollowPosition(npc, leader);
        return nearby != rejected && valid(npc, nearby) ? nearby : SetMoving.NO_POS;
    }

    private static void remember(final L1NpcInstance npc, final L1NpcInstance leader,
            final long primary, final long goal, final long now) {
        SetMoving.clearProgress(npc);
        dMoveFormation.clearWait(npc);
        CMoveAction.setGoal(npc, SetMoving.KIND_FORMATION, leader.getId(),
                SetMoving.unpackX(primary), SetMoving.unpackY(primary),
                SetMoving.unpackX(goal), SetMoving.unpackY(goal));
        if (goal != primary) {
            dMoveFormation.beginWait(npc, now + RETRY_PRIMARY_MS, WAIT_FALLBACK);
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
            if (npc != null) CCreatureController.sleep(npc, 500L);
            return false;
        }
        if (npc.발묶임상태()) {
            CCreatureController.sleep(npc, 500L);
            return false;
        }
        final int index = relativeIndex(npc.getFormationIndex(), leader.getFormationIndex());
        final long primary = formationPosition(leader.getX(), leader.getY(),
                leader.getHeading(), index);
        if (primary == SetMoving.NO_POS) {
            clear(npc);
            CCreatureController.sleep(npc, 500L);
            return false;
        }

        final long now = System.currentTimeMillis();
        long goal = CMoveAction.getGoal(npc, SetMoving.KIND_FORMATION,
                leader.getId(), SetMoving.unpackX(primary),
                SetMoving.unpackY(primary), 0);
        if (!valid(npc, goal) || (goal != primary
                && dMoveFormation.waitUntil(npc) <= now)) {
            goal = valid(npc, primary) ? primary
                    : fallback(npc, leader, primary, SetMoving.NO_POS);
            if (goal == SetMoving.NO_POS) {
                clear(npc);
                CCreatureController.sleep(npc, 500L);
                return false;
            }
            remember(npc, leader, primary, goal, now);
        }
        // Validate the membership/leader again before committing a movement step.
        if (!sameGroup(npc, group, leader) || !npc.isMovable() || npc.isRest()
                || npc.발묶임상태()) {
            clear(npc);
            CCreatureController.sleep(npc, 500L);
            return false;
        }
        final byte result = CMoveAction.stepFollowGoal(npc, goal);
        if (result == CCreatureController.RESULT_BLOCKED
                || result == CCreatureController.RESULT_INVALID) {
            final long alternate = fallback(npc, leader, primary, goal);
            if (alternate != SetMoving.NO_POS) {
                remember(npc, leader, primary, alternate, now);
            } else {
                // stepFollowGoal may already have cleared KIND_FORMATION.
                // Retire its remaining wait/progress and path on this owner.
                SetMoving.clearActionMoveState(npc);
                SetMoving.clear(npc);
            }
            CCreatureController.sleep(npc, Math.max(npc.getSleepTime(), 300L));
        }
        return false;
    }

    static void clearIfActive(final L1NpcInstance npc) {
        if (dMoveFormation.clearFormation(npc)) SetMoving.clear(npc);
    }

    static void clear(final L1NpcInstance npc) {
        clearIfActive(npc);
    }

    static void beginWait(
            final L1NpcInstance npc,
            final long untilMs,
            final byte reason) {

        if (npc == null || npc.getId() == 0) {
            return;
        }

        SetMoving.initActionMoveState(npc);

        npc._actionMoveWaitUntil = untilMs;
        npc._actionMoveWaitReason = reason;
    }

    static long waitUntil(final L1NpcInstance npc) {
        if (npc == null || npc.getId() == 0) {
            return 0L;
        }

        return SetMoving.hasActionMoveState(npc) ? npc._actionMoveWaitUntil : 0L;
    }

    static void clearWait(final L1NpcInstance npc) {
        if (npc == null || npc.getId() == 0) {
            return;
        }

        if (SetMoving.hasActionMoveState(npc)) {
            npc._actionMoveWaitUntil = 0L;
            npc._actionMoveWaitReason = 0;
        }
    }

    static boolean clearFormation(final L1NpcInstance npc) {
        if (npc == null || npc.getId() == 0) return false;
        if (!SetMoving.hasActionMoveState(npc) || npc._actionMoveKind != SetMoving.KIND_FORMATION) return false;
        SetMoving.clearActionMoveState(npc);
        return true;
    }
}
