package l1j.server.server.model.npc.ai.action;

import java.util.concurrent.ThreadLocalRandom;

import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1MobGroupInfo;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.ai.core.PathFinder;
import l1j.server.server.model.ai.core.WorldCollisionHash;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.npc.ai.AstarProvider;
import l1j.server.server.utils.NpcAiUtil;

/**
 * NPC movement action.
 *
 * PathNodeWorld  : macro/static route
 * PathFinder     : CalcMidDest-style short segment + local/chase
 * CMoveAction     : movement result translation / follow destination
 * WorldCollision : runtime dynamic occupancy
 *
 * Per-tick allocation 없음.
 */
public final class CMoveAction {

    static final int DIR_INVALID    = -1;
    static final int DIR_RETRY      = -2;
    static final int DIR_HARD_BLOCK = -3;
    static final int DIR_SOFT_BLOCK = -4;

    static final int FOLLOW_RADIUS = 2;
    private static final int FOLLOW_ANCHOR_DRIFT = 2;
    private static final int FOLLOW_NO_PROGRESS_REPLAN = 4;

    private static final int[] DX = { 0, 1, 1, 1, 0, -1, -1, -1 };
    private static final int[] DY = { -1, -1, 0, 1, 1, 1, 0, -1 };

    private static final byte[] SLOT_LEFT_FIRST  = { 0, -1, 1, -2, 2, -3, 3, 4 };
    private static final byte[] SLOT_RIGHT_FIRST = { 0, 1, -1, 2, -2, 3, -3, 4 };

    private static final WorldCollisionHash COLLISION =
            WorldCollisionHash.getInstance();

    private CMoveAction() {
    }

    public static void set(
            final L1NpcInstance creature,
            final byte action) {

        if (creature == null) {
            return;
        }

        switch (action) {
        case CCreatureController.ACTION_FOLLOW_MASTER:
        case CCreatureController.ACTION_FOLLOW_GROUP:
        case CCreatureController.ACTION_MOVE:
        case CCreatureController.ACTION_MOVE_TO_POINT:
        case CCreatureController.ACTION_MOVE_TO_OBJECT:
            CCreatureController.set(creature, action);
            onStart(creature, action);
            return;
        default:
            CCreatureController.think(creature);
            return;
        }
    }

    public static void onStart(
            final L1NpcInstance creature,
            final byte action) {
        if (creature == null) {
            return;
        }
    }

    public static byte onTick(
            final L1NpcInstance creature,
            final byte action) {

        if (creature == null) {
            return CCreatureController.RESULT_INVALID;
        }

        switch (action) {
        case CCreatureController.ACTION_FOLLOW_MASTER:
            return creature.noTargetMaster()
                    ? CCreatureController.RESULT_INVALID
                    : CCreatureController.RESULT_DONE;

        case CCreatureController.ACTION_FOLLOW_GROUP: {
            if (!creature.isMovable() || creature.isRest()
                    || !L1World.getInstance().hasRecognizePlayerFast(creature)) {
                dMoveFormation.clear(creature);
                return CCreatureController.RESULT_DONE;
            }
            final L1MobGroupInfo group = creature.getMobGroupInfo();
            final L1NpcInstance leader =
                    group != null ? group.getActiveLeader() : null;
            if (group == null || group.isLeader(creature)) {
                // A follower became its own new group leader, or its group
                // was disbanded while the last CIO timer was pending.
                dMoveFormation.clear(creature);
                return CCreatureController.RESULT_DONE;
            }

            // A leader may die, leave the group, switch maps or be
            // replaced between selection and the CIO movement tick.
            // This is a suspended FOLLOW_GROUP, not an invalid AI actor:
            // never stop the whole NPC owner because its leader vanished.
            if (!validGroupLeader(creature, group, leader)) {
                dMoveFormation.clear(creature);
                CCreatureController.sleep(creature, 500L);
                return CCreatureController.RESULT_RUNNING;
            }

            return creature.noTargetGroup(leader)
                    ? CCreatureController.RESULT_INVALID
                    : CCreatureController.RESULT_DONE;
        }

        case CCreatureController.ACTION_MOVE_TO_POINT:
            return dMoveTo.onTick(creature);

        case CCreatureController.ACTION_MOVE_TO_OBJECT:
            return dMoveToTarget.onTick(creature);

        case CCreatureController.ACTION_MOVE:
            /*
             * PS_MOVE_AROUND has one owner only:
             * ACTION_MOVE_AROUND_NATIVE -> CMoveAroundAction.
             * ACTION_MOVE remains the legacy/generic no-target move hook.
             */
            creature.noTargetNormal();
            return CCreatureController.RESULT_DONE;

        default:
            return CCreatureController.RESULT_INVALID;
        }
    }

    public static void onEnd(
            final L1NpcInstance creature,
            final byte action,
            final byte result) {

        if (creature == null
                || CCreatureController.currentAction(creature) != action) {
            return;
        }

        switch (result) {
        case CCreatureController.RESULT_DONE:
            // NC dMoveTo removes its exact key before running
            // MoveToFinished(0x41). Run the virtual callback only after the
            // prior action is fully retired: a callback that submits MoveTo B
            // must not be overwritten by A's subsequent doPendingAction().
            if (action == CCreatureController.ACTION_MOVE_TO_POINT) {
                final long completedXY = creature._scriptMoveGoalXY;
                final boolean returning = dMoveTo.isReturningHome(creature);
                clearScriptMove(creature);
                CCreatureController.doPendingAction(creature);
                if (returning) return;
                creature.onMoveToFinished(
                        SetMoving.unpackX(completedXY),
                        SetMoving.unpackY(completedXY));
                // guard_stand: after the exact return action completes, enter
                // its timed idle action without a companion tick. Do not
                // overwrite a new action submitted by the completion callback.
                if (CStandAction.isGuard(creature)
                        && CCreatureController.currentAction(creature) == CCreatureController.ACTION_THINK
                        && creature.getTarget() == null && CStandAction.atHome(creature)
                        && !creature.isDead() && !creature.isDestroyed()
                        && creature.getCurrentHp() > 0) {
                    CStandAction.set(creature);
                    final byte idle = CStandAction.onTick(creature);
                    if (idle != CCreatureController.RESULT_RUNNING) {
                        CStandAction.onEnd(creature, idle);
                    }
                }
                return;
            }
            if (action == CCreatureController.ACTION_MOVE_TO_OBJECT) {
                clearScriptMove(creature);
            }
            CCreatureController.doPendingAction(creature);
            return;
        case CCreatureController.RESULT_INVALID:
            if (action == CCreatureController.ACTION_MOVE_TO_POINT
                    || action == CCreatureController.ACTION_MOVE_TO_OBJECT) {
                clearScriptMove(creature);
                CCreatureController.think(creature);
                return;
            }
            CCreatureController.set(
                    creature,
                    CCreatureController.ACTION_NOTHING);
            return;
        case CCreatureController.RESULT_RUNNING:
        case CCreatureController.RESULT_BLOCKED:
        case CCreatureController.RESULT_STOP:
        default:
            return;
        }
    }

    /** Retire native scripted MoveTo payload, never the combat _target. */
    static void clearScriptMove(final L1NpcInstance creature) {
        if (creature == null) return;
        creature._scriptMoveGoalXY = 0L;
        creature._scriptMoveTargetId = 0;
        creature._scriptMoveStopDistance = 0;
        creature._scriptMoveMapId = -1;
        creature._pathFindFailCount = 0;
        invalidatePath(creature);
    }

    /**
     * L1NpcInstance.moveDirection() 실제 구현.
     * Point 객체를 만들지 않고 tile-line distance를 직접 계산한다.
     */
    public static int direction(
            final L1NpcInstance creature,
            final int x,
            final int y) {

        if (creature == null) {
            return DIR_INVALID;
        }

        final int distance = chebyshev(
                creature.getX(), creature.getY(), x, y);

        return directionInternal(creature, x, y, distance);
    }

    public static int direction(
            final L1NpcInstance creature,
            final int x,
            final int y,
            final double distance) {

        if (creature == null) {
            return DIR_INVALID;
        }

        return directionInternal(creature, x, y, distance);
    }

/*
     * Direction 계산은 이동 결과만 반환한다.
     * retarget/hate/give-up 같은 combat policy는 CMoveToTargetAction 책임이다.
     */
    private static int directionInternal(
            final L1NpcInstance creature,
            final int x,
            final int y,
            final double distance) {

        if (creature.isSleeped() && distance >= 2D) {
            return DIR_RETRY;
        }

        /*
         * 아주 먼 대상은 기존 fast direct-heading을 유지한다.
         * 단 checkObject 실패를 INVALID로 뭉개지 않고 dynamic/static으로 분류한다.
         */
        if (distance > 40D) {
            CMoveAction.clearPathFailureState(creature);

            final int wanted = creature.targetDirection(x, y);
            final int dir = NpcAiUtil.checkObject(
                    creature.getX(),
                    creature.getY(),
                    creature.getMapId(),
                    wanted);

            if (dir >= 0 && dir < 8) {
                return dir;
            }
            return classifyImmediateBlock(creature, wanted);
        }

        return pathDirection(
                creature,
                x,
                y,
                creature.getMapId());
    }

    /**
     * PathFinder 결과를 allocation 없는 int state로 번역한다.
     *
     * 0..7           : STEP
     * DIR_RETRY      : retry gate / already at segment destination
     * DIR_SOFT_BLOCK : runtime dynamic obstruction
     * DIR_HARD_BLOCK : static/no-route/exception
     */
    static int pathDirection(
            final L1NpcInstance creature,
            final int x,
            final int y,
            final int mapId) {

        try {
            final long now = System.currentTimeMillis();

            if (now < CMoveAction.pathRetryAfter(creature)) {
                return DIR_RETRY;
            }

            final PathFinder pathFinder = AstarProvider.get();
            final PathFinder.Node next =
                    pathFinder.searchTail(
                            creature,
                            x,
                            y,
                            mapId,
                            true);

            if (next != null
                    && (next.x != creature.getX()
                            || next.y != creature.getY())) {

                CMoveAction.clearPathFailureState(creature);

                return pathFinder.calcheading(
                        creature.getX(),
                        creature.getY(),
                        next.x,
                        next.y);
            }

            switch (pathFinder.getLastResult()) {
            case PathFinder.RESULT_NONE:
                CMoveAction.clearPathFailureState(creature);
                return DIR_RETRY;

            case PathFinder.RESULT_SOFT_BLOCK:
                CMoveAction.clearPathFailureState(creature);
                return DIR_SOFT_BLOCK;

            case PathFinder.RESULT_NO_ROUTE:
            default:
                CMoveAction.markPathFailed(
                        creature,
                        now + hardRetryDelayMs(
                                creature,
                                chebyshev(
                                        creature.getX(), creature.getY(),
                                        x, y)));
                return DIR_HARD_BLOCK;
            }

        } catch (Throwable e) {
            CMoveAction.markPathFailed(
                    creature,
                    System.currentTimeMillis()
                            + hardRetryDelayMs(
                                    creature,
                                    chebyshev(
                                            creature.getX(), creature.getY(),
                                            x, y)));
            return DIR_HARD_BLOCK;
        }
    }

    static int lastBlockType() {
        try {
            return AstarProvider.get().getLastBlockType();
        } catch (Throwable t) {
            return WorldCollisionHash.MOVE_RESERVED;
        }
    }

    static void invalidatePath(final L1NpcInstance creature) {
        if (creature == null) {
            return;
        }

        try {
            AstarProvider.get().invalidate(
                    creature,
                    creature.getMapId());
        } catch (Throwable ignore) {
        }

        CMoveAction.clearPathFailureState(creature);
    }

    static long hardRetryDelayMs(
            final L1NpcInstance creature,
            final int distance) {

        final int d = Math.min(Math.max(distance, 0), 30);
        long delay = 240L + (d * 18L);
        if (delay > 780L) {
            delay = 780L;
        }

        return delay + (creature.getId() & 0x1f);
    }

    /** Existing NC dFollow adapter owns all L1 master-follow decisions. */
    public static boolean followMaster(final L1NpcInstance creature) {
        return dFollow.follow(
                creature, creature != null ? creature.getMaster() : null);
    }

    /** A group membership transition never follows a dead/foreign leader. */
    private static boolean validGroupLeader(
            final L1NpcInstance creature, final L1MobGroupInfo group,
            final L1NpcInstance leader) {
        return creature != null && group != null && leader != null
                && creature != leader
                && leader.getMobGroupInfo() == group
                && creature.getMobGroupInfo() == group
                && !leader.isDead() && !leader.isDestroyed()
                && leader.getCurrentHp() > 0
                && leader.getMapId() == creature.getMapId();
    }

    /** Group followers own a formation slot, separate from pet/master follow. */
    public static boolean followGroup(
            final L1NpcInstance creature, final L1NpcInstance leader) {
        final L1MobGroupInfo group =
                creature != null ? creature.getMobGroupInfo() : null;
        final L1NpcInstance activeLeader =
                group != null ? group.getActiveLeader() : null;
        if (!validGroupLeader(creature, group, activeLeader)) {
            dMoveFormation.clear(creature);
            if (creature != null) {
                CCreatureController.sleep(creature, 500L);
            }
            // Preserve CMoveAction's noTargetGroup contract (true means
            // invalid AI). Lack of a leader only postpones this movement.
            return false;
        }
        return dMoveFormation.follow(creature, group, activeLeader);
    }

    /**
     * CNPC::GetFollowPosition 계열.
     * 선택한 slot을 anchor가 의미 있게 움직이기 전까지 유지한다.
     */
    static boolean followPosition(
            final L1NpcInstance creature,
            final L1Character anchor) {

        long packed = CMoveAction.getGoal(
                creature,
                SetMoving.KIND_FOLLOW,
                anchor.getId(),
                anchor.getX(),
                anchor.getY(),
                FOLLOW_ANCHOR_DRIFT);

        if (!validFollowGoal(creature, anchor, packed)) {
            packed = GetFollowPosition(creature, anchor);

            if (packed != SetMoving.NO_POS) {
                CMoveAction.setGoal(
                        creature,
                        SetMoving.KIND_FOLLOW,
                        anchor.getId(),
                        anchor.getX(),
                        anchor.getY(),
                        SetMoving.unpackX(packed),
                        SetMoving.unpackY(packed));
            }
        }

        return stepFollowGoal(creature, packed) == CCreatureController.RESULT_INVALID;
    }

    /** Shared path/occupancy step; formation may replan after BLOCKED. */
    static byte stepFollowGoal(final L1NpcInstance creature, final long packed) {
        if (packed == SetMoving.NO_POS) {
            CCreatureController.sleep(creature, 140L + (creature.getId() & 0x1f));
            return CCreatureController.RESULT_RUNNING;
        }

        final int goalX = SetMoving.unpackX(packed);
        final int goalY = SetMoving.unpackY(packed);

        if (creature.getX() == goalX
                && creature.getY() == goalY) {
            SetMoving.clearProgress(creature);
            return CCreatureController.RESULT_DONE;
        }

        final int dir = direction(creature, goalX, goalY);

        if (dir == DIR_SOFT_BLOCK) {
            CMoveAction.clearGoal(creature);
            CCreatureController.sleep(
                    creature,
                    softDelayMs(creature, lastBlockType(), 1));
            return CCreatureController.RESULT_BLOCKED;
        }

        if (dir == DIR_RETRY) {
            return CCreatureController.RESULT_RUNNING;
        }

        if (dir == DIR_HARD_BLOCK) {
            CMoveAction.clearGoal(creature);
            invalidatePath(creature);
            CCreatureController.sleep(
                    creature,
                    hardRetryDelayMs(
                            creature,
                            chebyshev(
                                    creature.getX(), creature.getY(),
                                    goalX, goalY)));
            return CCreatureController.RESULT_BLOCKED;
        }

        if (dir == DIR_INVALID) {
            return CCreatureController.RESULT_INVALID;
        }

        final int beforeX = creature.getX();
        final int beforeY = creature.getY();
        final boolean moved = CMoveAction.moveOneStep(creature, dir);

        if (!moved) {
            CMoveAction.clearGoal(creature);
            CCreatureController.sleep(
                    creature,
                    softDelayMs(
                            creature,
                            WorldCollisionHash.MOVE_RESERVED,
                            1));
            return CCreatureController.RESULT_BLOCKED;
        }

        final int noProgress = CMoveAction.reportMove(
                creature,
                beforeX,
                beforeY,
                creature.getX(),
                creature.getY(),
                goalX,
                goalY);

        if (noProgress >= FOLLOW_NO_PROGRESS_REPLAN) {
            CMoveAction.clearGoal(creature);
            SetMoving.clearProgress(creature);
            invalidatePath(creature);
            CCreatureController.sleep(creature, 180L + (creature.getId() & 0x1f));
            return CCreatureController.RESULT_BLOCKED;
        }

        return CCreatureController.RESULT_RUNNING;
    }

    private static boolean validFollowGoal(
            final L1NpcInstance creature,
            final L1Character anchor,
            final long packed) {

        if (packed == SetMoving.NO_POS) {
            return false;
        }

        final int x = SetMoving.unpackX(packed);
        final int y = SetMoving.unpackY(packed);
        final L1Map map = creature.getMap();

        if (map == null || !map.isPassable(x, y)) {
            return false;
        }

        if (chebyshev(x, y, anchor.getX(), anchor.getY()) > FOLLOW_RADIUS + 1) {
            return false;
        }

        if (x == creature.getX() && y == creature.getY()) {
            return true;
        }

        return COLLISION.testStep(
                (short) creature.getMapId(),
                x,
                y,
                creature.getId()) == WorldCollisionHash.MOVE_CLEAR;
    }

    static long GetFollowPosition(
            final L1NpcInstance creature,
            final L1Character anchor) {

        final L1Map map = creature.getMap();
        if (map == null) {
            return SetMoving.NO_POS;
        }

        final int base = anchor.targetDirection(
                creature.getX(), creature.getY());
        final byte[] order = (creature.getId() & 1) == 0
                ? SLOT_LEFT_FIRST
                : SLOT_RIGHT_FIRST;
        final short mapId = (short) creature.getMapId();
        final int actorId = creature.getId();

        /*
         * NC GetFollowPosition scans all eight slots and chooses the valid
         * one nearest to the follower. L1's 2D projection keeps the existing
         * radius-2 ring (radius 1 only when the outer ring is unavailable),
         * actual TileOccupancy collision, and stable movement action state goal.
         *
         * Tie order is deterministic per NPC, so 20 followers cannot start
         * oscillating because of per-tick random slot selection.
         */
        for (int radius = FOLLOW_RADIUS; radius >= 1; radius--) {
            long bestGoal = SetMoving.NO_POS;
            long bestDistanceSq = Long.MAX_VALUE;
            for (int i = 0; i < order.length; i++) {
                final int heading = (base + order[i]) & 7;
                final int x = anchor.getX() + DX[heading] * radius;
                final int y = anchor.getY() + DY[heading] * radius;

                if (!map.isPassable(x, y)) {
                    continue;
                }
                if ((x != creature.getX() || y != creature.getY())
                        && COLLISION.testStep(
                                mapId, x, y, actorId)
                                != WorldCollisionHash.MOVE_CLEAR) {
                    continue;
                }

                final long dx = (long) x - creature.getX();
                final long dy = (long) y - creature.getY();
                final long distanceSq = dx * dx + dy * dy;
                if (distanceSq < bestDistanceSq) {
                    bestDistanceSq = distanceSq;
                    bestGoal = SetMoving.pack(x, y);
                }
            }
            if (bestGoal != SetMoving.NO_POS) {
                return bestGoal;
            }
        }

        return SetMoving.NO_POS;
    }

    /** target이 없을 때의 기본 wander 구현. */
    public static void move(final L1NpcInstance creature) {
        // The idle action may have been selected before a dialog/rest or
        // template change. Recheck the physical movement gate on its owner.
        if (creature == null || !creature.isMovable() || creature.isRest()) {
            return;
        }

        int distance = CMoveAction.randomMoveDistance(creature);
        int direction = CMoveAction.randomMoveDirection(creature);

        if (distance == 0) {
            distance = ThreadLocalRandom.current().nextInt(5) + 1;
            direction = ThreadLocalRandom.current().nextInt(20);

            if (creature.getHomeX() != 0
                    && creature.getHomeY() != 0
                    && direction < 8
                    && ThreadLocalRandom.current().nextInt(3) == 0) {

                final int homeDir = creature.moveDirection(
                        creature.getHomeX(),
                        creature.getHomeY());

                if (homeDir >= 0 && homeDir < 8) {
                    direction = homeDir;
                }
            }
        } else {
            distance--;
        }

        CMoveAction.randomMoveState(creature, distance, direction);

        if (direction < 0 || direction >= 8) {
            return;
        }

        final int dir = NpcAiUtil.checkObject(
                creature.getX(),
                creature.getY(),
                creature.getMapId(),
                direction);

        if (dir >= 0 && dir < 8) {
            CMoveAction.directionMove(creature, dir);
        }
    }

    private static int classifyImmediateBlock(
            final L1NpcInstance creature,
            final int wanted) {

        if (wanted < 0 || wanted > 7) {
            return DIR_HARD_BLOCK;
        }

        final L1Map map = creature.getMap();
        if (map == null
                || !map.isMovePassableGeoNpc(
                        creature.getX(), creature.getY(), wanted)) {
            return DIR_HARD_BLOCK;
        }

        final int nx = creature.getX() + DX[wanted];
        final int ny = creature.getY() + DY[wanted];

        return COLLISION.testStep(
                (short) creature.getMapId(),
                nx,
                ny,
                creature.getId()) == WorldCollisionHash.MOVE_CLEAR
                        ? DIR_HARD_BLOCK
                        : DIR_SOFT_BLOCK;
    }

    private static long softDelayMs(
            final L1NpcInstance creature,
            final int blockType,
            final int streak) {

        final int s = Math.min(Math.max(streak, 0), 8);
        final int spread = creature.getId() & 0x1f;

        switch (blockType) {
        case WorldCollisionHash.MOVE_RESERVED:
            return 70L + (s * 12L) + spread;
        case WorldCollisionHash.MOVE_PC:
            return 110L + (s * 16L) + spread;
        case WorldCollisionHash.MOVE_NPC:
            return 130L + (s * 18L) + spread;
        default:
            return 100L + (s * 14L) + spread;
        }
    }

    private static int chebyshev(
            final int x0,
            final int y0,
            final int x1,
            final int y1) {
        return Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
    }

    public static boolean moveOneStep(
            final L1NpcInstance npc,
            final int dir) {

        if (dir < 0 || dir > 7) {
            return false;
        }

        final int beforeX = npc.getX();
        final int beforeY = npc.getY();

        npc.setSleepTime(npc.setDirectionMove(dir));

        return beforeX != npc.getX()
                || beforeY != npc.getY();
    }

    public static int incrementMoveStuck(final L1NpcInstance npc) {
        return ++npc._stuckCount;
    }

    public static int moveStuck(final L1NpcInstance npc) {
        return npc._stuckCount;
    }

    public static void moveStuck(
            final L1NpcInstance npc,
            final int value) {
        npc._stuckCount = value;
    }

    public static void resetMoveBlockedState(final L1NpcInstance npc) {
        npc._stuckCount = 0;
        npc._pathRetryAfter = 0L;
    }

    public static long pathRetryAfter(final L1NpcInstance npc) {
        return npc._pathRetryAfter;
    }

    public static void clearPathFailureState(final L1NpcInstance npc) {
        npc._pathRetryAfter = 0L;
    }

    public static void markPathFailed(
            final L1NpcInstance npc,
            final long retryAt) {
        npc._pathRetryAfter = retryAt;
    }

    public static int randomMoveDistance(final L1NpcInstance npc) {
        return npc._randomMoveDistance;
    }

    public static int randomMoveDirection(final L1NpcInstance npc) {
        return npc._randomMoveDirection;
    }

    public static void randomMoveState(
            final L1NpcInstance npc,
            final int distance,
            final int direction) {
        npc._randomMoveDistance = distance;
        npc._randomMoveDirection = direction;
    }

    public static void directionMove(
            final L1NpcInstance npc,
            final int dir) {
        npc.setSleepTime(npc.setDirectionMove(dir));
    }

    static long getGoal(
            final L1NpcInstance npc,
            final byte kind,
            final int anchorId,
            final int anchorX,
            final int anchorY,
            final int maxAnchorDrift) {

        if (npc == null || npc.getId() == 0) {
            return SetMoving.NO_POS;
        }

        if (!SetMoving.hasActionMoveState(npc)
                || npc._actionMoveKind != kind
                || npc._actionMoveAnchorId != anchorId) {
            return SetMoving.NO_POS;
        }

        final long anchor = npc._actionMoveAnchorPos;
        if (anchor == SetMoving.NO_POS
                || chebyshev(
                        anchorX,
                        anchorY,
                        SetMoving.unpackX(anchor),
                        SetMoving.unpackY(anchor)) > maxAnchorDrift) {
            SetMoving.clearActionMoveState(npc);
            return SetMoving.NO_POS;
        }

        return npc._actionMoveGoalPos;
    }

    static void setGoal(
            final L1NpcInstance npc,
            final byte kind,
            final int anchorId,
            final int anchorX,
            final int anchorY,
            final int goalX,
            final int goalY) {

        if (npc == null || npc.getId() == 0) {
            return;
        }

        SetMoving.initActionMoveState(npc);
        npc._actionMoveKind = kind;
        npc._actionMoveAnchorId = anchorId;
        npc._actionMoveAnchorPos = SetMoving.pack(anchorX, anchorY);
        npc._actionMoveGoalPos = SetMoving.pack(goalX, goalY);
    }

    static void clearGoal(final L1NpcInstance npc) {
        clearGoalAndReport(npc);
    }

    static boolean clearGoalAndReport(final L1NpcInstance npc) {
        if (npc == null || npc.getId() == 0) {
            return false;
        }

        if (!SetMoving.hasActionMoveState(npc)) {
            return false;
        }
        final boolean hadGoal =
                npc._actionMoveKind != SetMoving.KIND_NONE
                || npc._actionMoveGoalPos != SetMoving.NO_POS;
        npc._actionMoveKind = SetMoving.KIND_NONE;
        npc._actionMoveAnchorId = 0;
        npc._actionMoveAnchorPos = SetMoving.NO_POS;
        npc._actionMoveGoalPos = SetMoving.NO_POS;
        return hadGoal;
    }

    static int reportMove(
            final L1NpcInstance npc,
            final int beforeX,
            final int beforeY,
            final int afterX,
            final int afterY,
            final int goalX,
            final int goalY) {

        if (npc == null || npc.getId() == 0) {
            return 0;
        }

        SetMoving.initActionMoveState(npc);

        final int beforeH = chebyshev(beforeX, beforeY, goalX, goalY);
        final int afterH = chebyshev(afterX, afterY, goalX, goalY);
        final long previousOrigin = npc._actionMovePreviousOrigin;
        final long after = SetMoving.pack(afterX, afterY);

        int count = npc._actionMoveNoProgress & 0xff;
        final int beforeCount = count;

        if (afterH < beforeH) {
            count = 0;
        } else if (previousOrigin != SetMoving.NO_POS && after == previousOrigin) {
            count += 2;
        } else {
            count++;
        }

        /* threshold를 +2 cycle increment가 건너뛰지 않게 crossing을 정규화한다. */
        if (beforeCount < 4 && count > 4) {
            count = 4;
        } else if (beforeCount < 8 && count > 8) {
            count = 8;
        }

        if (count > 127) {
            count = 127;
        }

        npc._actionMoveNoProgress = (byte) count;
        npc._actionMovePreviousOrigin = SetMoving.pack(beforeX, beforeY);
        return count;
    }
}
