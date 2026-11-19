package l1j.server.server.model.npc.ai.action;

import java.util.concurrent.ThreadLocalRandom;

import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1MobGroupInfo;
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

    private static final int FOLLOW_RADIUS = 2;
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
            return ActionAccess.followMaster(creature)
                    ? CCreatureController.RESULT_INVALID
                    : CCreatureController.RESULT_DONE;

        case CCreatureController.ACTION_FOLLOW_GROUP: {
            final L1MobGroupInfo group = creature.getMobGroupInfo();
            final L1NpcInstance leader =
                    group != null ? group.getLeader() : null;

            if (leader == null) {
                return CCreatureController.RESULT_INVALID;
            }

            return ActionAccess.followGroup(creature, leader)
                    ? CCreatureController.RESULT_INVALID
                    : CCreatureController.RESULT_DONE;
        }

        case CCreatureController.ACTION_MOVE:
            /*
             * PS_MOVE_AROUND has one owner only:
             * ACTION_MOVE_AROUND_NATIVE -> CMoveAroundAction.
             * ACTION_MOVE remains the legacy/generic no-target move hook.
             */
            ActionAccess.moveRandom(creature);
            return CCreatureController.RESULT_DONE;

        default:
            return CCreatureController.RESULT_INVALID;
        }
    }

    public static void onEnd(
            final L1NpcInstance creature,
            final byte action,
            final byte result) {

        if (creature == null) {
            return;
        }

        switch (result) {
        case CCreatureController.RESULT_DONE:
            CCreatureController.doPendingAction(creature);
            return;
        case CCreatureController.RESULT_INVALID:
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
            ActionAccess.clearPathFailureState(creature);

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

            if (now < ActionAccess.pathRetryAfter(creature)) {
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

                ActionAccess.clearPathFailureState(creature);

                return pathFinder.calcheading(
                        creature.getX(),
                        creature.getY(),
                        next.x,
                        next.y);
            }

            switch (pathFinder.getLastResult()) {
            case PathFinder.RESULT_NONE:
                ActionAccess.clearPathFailureState(creature);
                return DIR_RETRY;

            case PathFinder.RESULT_SOFT_BLOCK:
                ActionAccess.clearPathFailureState(creature);
                return DIR_SOFT_BLOCK;

            case PathFinder.RESULT_NO_ROUTE:
            default:
                ActionAccess.markPathFailed(
                        creature,
                        now + hardRetryDelayMs(
                                creature,
                                chebyshev(
                                        creature.getX(), creature.getY(),
                                        x, y)));
                return DIR_HARD_BLOCK;
            }

        } catch (Throwable e) {
            ActionAccess.markPathFailed(
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

        ActionAccess.clearPathFailureState(creature);
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

    /** master follow. */
    public static boolean followMaster(
            final L1NpcInstance creature) {

        if (creature == null) {
            return true;
        }

        final L1Character master = creature.getMaster();
        if (master == null
                || master.getMapId() != creature.getMapId()) {
            return true;
        }

        if (creature.getLocation()
                .getTileLineDistance(master.getLocation()) <= FOLLOW_RADIUS) {
            return false;
        }

        return followPosition(creature, master);
    }

    /** group leader follow. */
    public static boolean followGroup(
            final L1NpcInstance creature,
            final L1NpcInstance leader) {

        if (creature == null
                || leader == null
                || leader.getMapId() != creature.getMapId()) {
            return true;
        }

        if (creature.getLocation()
                .getTileLineDistance(leader.getLocation()) <= FOLLOW_RADIUS) {
            return false;
        }

        return followPosition(creature, leader);
    }

    /**
     * CNPC::GetFollowPosition 계열.
     * 선택한 slot을 anchor가 의미 있게 움직이기 전까지 유지한다.
     */
    private static boolean followPosition(
            final L1NpcInstance creature,
            final L1Character anchor) {

        long packed = MoveIntentCache.getGoal(
                creature,
                MoveIntentCache.KIND_FOLLOW,
                anchor.getId(),
                anchor.getX(),
                anchor.getY(),
                FOLLOW_ANCHOR_DRIFT);

        if (!validFollowGoal(creature, anchor, packed)) {
            packed = findFollowPosition(creature, anchor);

            if (packed != MoveIntentCache.NO_POS) {
                MoveIntentCache.setGoal(
                        creature,
                        MoveIntentCache.KIND_FOLLOW,
                        anchor.getId(),
                        anchor.getX(),
                        anchor.getY(),
                        MoveIntentCache.unpackX(packed),
                        MoveIntentCache.unpackY(packed));
            }
        }

        if (packed == MoveIntentCache.NO_POS) {
            ActionAccess.sleep(creature, 140L + (creature.getId() & 0x1f));
            return false;
        }

        final int goalX = MoveIntentCache.unpackX(packed);
        final int goalY = MoveIntentCache.unpackY(packed);

        if (creature.getX() == goalX
                && creature.getY() == goalY) {
            MoveIntentCache.clearProgress(creature);
            return false;
        }

        final int dir = direction(creature, goalX, goalY);

        if (dir == DIR_SOFT_BLOCK) {
            MoveIntentCache.clearGoal(creature);
            ActionAccess.sleep(
                    creature,
                    softDelayMs(creature, lastBlockType(), 1));
            return false;
        }

        if (dir == DIR_RETRY) {
            return false;
        }

        if (dir == DIR_HARD_BLOCK) {
            MoveIntentCache.clearGoal(creature);
            invalidatePath(creature);
            ActionAccess.sleep(
                    creature,
                    hardRetryDelayMs(
                            creature,
                            chebyshev(
                                    creature.getX(), creature.getY(),
                                    goalX, goalY)));
            return false;
        }

        if (dir == DIR_INVALID) {
            return true;
        }

        final int beforeX = creature.getX();
        final int beforeY = creature.getY();
        final boolean moved = ActionAccess.moveOneStep(creature, dir);

        if (!moved) {
            MoveIntentCache.clearGoal(creature);
            ActionAccess.sleep(
                    creature,
                    softDelayMs(
                            creature,
                            WorldCollisionHash.MOVE_RESERVED,
                            1));
            return false;
        }

        final int noProgress = MoveIntentCache.reportMove(
                creature,
                beforeX,
                beforeY,
                creature.getX(),
                creature.getY(),
                goalX,
                goalY);

        if (noProgress >= FOLLOW_NO_PROGRESS_REPLAN) {
            MoveIntentCache.clearGoal(creature);
            MoveIntentCache.clearProgress(creature);
            invalidatePath(creature);
            ActionAccess.sleep(creature, 180L + (creature.getId() & 0x1f));
        }

        return false;
    }

    private static boolean validFollowGoal(
            final L1NpcInstance creature,
            final L1Character anchor,
            final long packed) {

        if (packed == MoveIntentCache.NO_POS) {
            return false;
        }

        final int x = MoveIntentCache.unpackX(packed);
        final int y = MoveIntentCache.unpackY(packed);
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

    private static long findFollowPosition(
            final L1NpcInstance creature,
            final L1Character anchor) {

        final L1Map map = creature.getMap();
        if (map == null) {
            return MoveIntentCache.NO_POS;
        }

        final int base = anchor.targetDirection(
                creature.getX(), creature.getY());
        final byte[] order = (creature.getId() & 1) == 0
                ? SLOT_LEFT_FIRST
                : SLOT_RIGHT_FIRST;
        final short mapId = (short) creature.getMapId();
        final int actorId = creature.getId();

        for (int radius = FOLLOW_RADIUS; radius >= 1; radius--) {
            for (int i = 0; i < order.length; i++) {
                final int heading = (base + order[i]) & 7;
                final int x = anchor.getX() + DX[heading] * radius;
                final int y = anchor.getY() + DY[heading] * radius;

                if (!map.isPassable(x, y)) {
                    continue;
                }

                if (x == creature.getX() && y == creature.getY()) {
                    return MoveIntentCache.pack(x, y);
                }

                if (COLLISION.testStep(
                        mapId,
                        x,
                        y,
                        actorId) != WorldCollisionHash.MOVE_CLEAR) {
                    continue;
                }

                return MoveIntentCache.pack(x, y);
            }
        }

        return MoveIntentCache.NO_POS;
    }

    /** target이 없을 때의 기본 wander 구현. */
    public static void move(final L1NpcInstance creature) {
        if (creature == null) {
            return;
        }

        int distance = ActionAccess.randomMoveDistance(creature);
        int direction = ActionAccess.randomMoveDirection(creature);

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

        ActionAccess.randomMoveState(creature, distance, direction);

        if (direction < 0 || direction >= 8) {
            return;
        }

        final int dir = NpcAiUtil.checkObject(
                creature.getX(),
                creature.getY(),
                creature.getMapId(),
                direction);

        if (dir >= 0 && dir < 8) {
            ActionAccess.directionMove(creature, dir);
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
}
