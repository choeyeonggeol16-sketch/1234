package l1j.server.server.model.npc.ai.action;

import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.ai.core.PathFinder;
import l1j.server.server.model.ai.core.PathNodeWorld;
import l1j.server.server.model.ai.core.WorldCollisionHash;
import l1j.server.server.model.character.animation.EActionCodes;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.npc.ai.AstarProvider;

public final class SetMoving {

    public static final byte RUNNING         = 0;
    public static final byte AT_DISTANCE     = 1;
    public static final byte INVALID         = 2;
    public static final byte PATH_FAILED     = 3;
    public static final byte DYNAMIC_BLOCKED = 4;

    private static final long REPEATED_COLLISION_MS = 300L;
    private static final int NEAR_DIRECT_TARGET_TILES = 12;
    private static final int TARGET_REPLAN_DRIFT_TILES = 4;

    /*
     * CalcMidDest constants recovered from L2Server:
     *   0x5ca778 = 0.8
     *   timer min = 100
     *   timer max = 800
     */
    private static final double CALC_MID_DISTANCE_SECONDS = 0.8d;
    private static final long CALC_MID_MIN_MS = 100L;
    private static final long CALC_MID_MAX_MS = 800L;

    private static final int[] DX =
            { 0, 1, 1, 1, 0, -1, -1, -1 };
    private static final int[] DY =
            { -1, -1, 0, 1, 1, 1, 0, -1 };

    private static final WorldCollisionHash COLLISION =
            WorldCollisionHash.getInstance();

    /* TEMP diagnostic: first creature that actually body-collides owns trace. */
    private static final boolean DEBUG_TRACE =
            Boolean.getBoolean("rkern.npc.chase.trace");
    private static volatile int DEBUG_TRACE_ACTOR_ID;

    static boolean debugTraceActor(final L1NpcInstance creature) {
        return DEBUG_TRACE
                && creature != null
                && DEBUG_TRACE_ACTOR_ID == creature.getId();
    }

    private static boolean claimDebugTraceActor(
            final L1NpcInstance creature) {

        if (!DEBUG_TRACE || creature == null) {
            return false;
        }

        int id = DEBUG_TRACE_ACTOR_ID;
        if (id == 0) {
            DEBUG_TRACE_ACTOR_ID = creature.getId();
            id = creature.getId();
            System.out.println(
                    "[CHASE-TRACE] claim=BODY_COLLISION actor=" + id
                    + " npcId=" + creature.getNpcId()
                    + " map=" + creature.getMapId()
                    + " pos=" + creature.getX() + "," + creature.getY());
        }

        return id == creature.getId();
    }

    public static byte tick(
            final L1NpcInstance creature,
            final L1Character target,
            final int desiredDistance) {

        if (!valid(creature, target)) {
            return INVALID;
        }

        return tickInternal(
                creature,
                target.getX(),
                target.getY(),
                Math.max(0, desiredDistance),
                target.getId(),
                false,
                true);
    }

    /**
     * Relative-action approach point. The original reference server action calculates
     * target + normalized(target -> creature) * action distance. L1 must
     * keep 8-way terrain/occupancy authoritative, so an unreachable or
     * occupied projected tile falls back to the ordinary live-target chase.
     * Reserved melee-slot movement is handled BEFORE this call.
     */
    public static byte tickRelative(
            final L1NpcInstance creature,
            final L1Character target,
            final int desiredDistance) {

        if (!valid(creature, target)) {
            return INVALID;
        }
        final int generation = creature.npcActionGeneration();
        final int sx = creature.getX();
        final int sy = creature.getY();
        final int tx = target.getX();
        final int ty = target.getY();
        final int deltaX = tx - sx;
        final int deltaY = ty - sy;

        // Keep the proven static corridor on long-range and blocked chases.
        // Use reference server's projected point only in near-field direct approaches.
        if (Math.max(Math.abs(deltaX), Math.abs(deltaY))
                > NEAR_DIRECT_TARGET_TILES) {
            return tick(creature, target, desiredDistance);
        }

        final long packed = CMoveToTargetRelativeAction.getDestination(
                sx, sy, tx, ty, Math.max(0, desiredDistance));
        final int goalX = (int) (packed >>> 32);
        final int goalY = (int) packed;
        final L1Map map = creature.getMap();

        final boolean projectedUsable =
                (goalX != sx || goalY != sy)
                && (goalX != tx || goalY != ty)
                && map != null
                && map.isInMap(goalX, goalY)
                && map.isPassable(goalX, goalY)
                && COLLISION.canEnter(
                        (short) creature.getMapId(), goalX, goalY,
                        creature.getId())
                && PathNodeWorld.moveStraight(map, sx, sy, goalX, goalY);

        // A path/occupancy probe can retire this action. Check before BOTH
        // the projected movement and the ordinary-chase fallback.
        if (creature.getTarget() != target
                || creature.npcActionGeneration() != generation
                || creature._relativeTargetId != target.getId()
                || creature._relativeMapId != creature.getMapId()
                || creature.getX() != sx || creature.getY() != sy
                || creature.isDead() || creature.getCurrentHp() <= 0
                || target.isDead() || target.getCurrentHp() <= 0
                || (target.isInvisble()
                        && !creature.getNpcTemplate().is_agrocoi())
                || target.getMapId() != creature.getMapId()
                || target.getX() != tx || target.getY() != ty
                || CCreatureController.currentAction(creature)
                        != CCreatureController.ACTION_MOVE_TO_TARGET_RELATIVE) {
            return INVALID;
        }

        if (!projectedUsable) {
            return tick(creature, target, desiredDistance);
        }

        // Same actor/target movement ownership, leases, and collision commit
        // as regular chase. Only the endpoint is projected into L1 tiles.
        // Do not execute a second movement tick on a collision: the caller
        // must preserve SetMoving's one-step collision and handoff policy.
        return tickInternal(
                creature, goalX, goalY, 0, target.getId(), false, true);
    }

    public static byte tickToPoint(
            final L1NpcInstance creature,
            final int targetX,
            final int targetY) {

        if (creature == null
                || creature.getMap() == null) {
            return INVALID;
        }

        return tickInternal(
                creature,
                targetX,
                targetY,
                0,
                pointKey(
                        creature.getMapId(),
                        targetX,
                        targetY),
                false,
                false);
    }

    /**
     * Blocked melee-slot hand-off. Use the existing PathFinder owner's small
     * occupancy scan to select a real first step around nearby creatures;
     * the normal chase continues to use CalcMidDest and the static PathNode
     * route. Every physical step still runs the original collision probe and
     * the NPC's atomic movement commit. No motion can bypass tile ownership.
     */
    public static byte tickToReservedAttackSlot(
            final L1NpcInstance creature,
            final L1Character target,
            final int targetX,
            final int targetY,
            final int slotX,
            final int slotY,
            final int generation) {

        if (!reservedChaseStillCurrent(creature, target, targetX,
                targetY, slotX, slotY, generation)) {
            return INVALID;
        }
        final int sx = creature.getX();
        final int sy = creature.getY();
        if (sx == slotX && sy == slotY) {
            return AT_DISTANCE;
        }

        prepareMoveKey(creature,
                pointKey(creature.getMapId(), slotX, slotY));
        final PathFinder pathFinder = AstarProvider.get();
        pathFinder.scanBodyRoute(creature, slotX, slotY, true);
        final int heading = pathFinder.bodyRouteFirstHeadingTo(slotX, slotY);
        if (!reservedChaseStillCurrent(creature, target, targetX,
                targetY, slotX, slotY, generation)) {
            return INVALID;
        }
        if (!validHeading(heading)) {
            return DYNAMIC_BLOCKED;
        }

        final long probe = COLLISION.moveCreatureProbe(
                (short) creature.getMapId(), sx, sy, heading,
                DX[heading], DY[heading], creature.getId());
        if (!WorldCollisionHash.moveProbeCanMove(probe)) {
            CCreatureController.sleep(creature, CALC_MID_MIN_MS);
            return RUNNING;
        }
        // A route scan and occupancy probe are not an atomic chase tick.
        // The target/action can be retired during either call. Never commit
        // the first physical step for an obsolete reserved attack slot.
        if (!reservedChaseStillCurrent(creature, target, targetX,
                targetY, slotX, slotY, generation)
                || creature.getX() != sx || creature.getY() != sy) {
            return INVALID;
        }
        if (!CMoveAction.moveOneStep(creature, heading)) {
            // Reservation raced between the route scan, probe and commit.
            CCreatureController.sleep(creature, CALC_MID_MIN_MS);
            return RUNNING;
        }
        // setDirectionMove() can finish a death/retarget/owner transition
        // synchronously. Its successor has already cleared its movement
        // state; the completed old step must not restore the old heading.
        if (!reservedChaseStillCurrent(creature, target, targetX,
                targetY, slotX, slotY, generation)) {
            return RUNNING;
        }
        creature._moveSide = (byte) heading;
        clearCollisionResponse(creature);
        return RUNNING;
    }

    private static boolean reservedChaseStillCurrent(
            final L1NpcInstance creature,
            final L1Character target,
            final int targetX,
            final int targetY,
            final int slotX,
            final int slotY,
            final int generation) {
        if (creature == null || creature.getMap() == null
                || creature.isDead() || creature.getCurrentHp() <= 0
                || target == null || target.isDead()
                || target.getCurrentHp() <= 0
                || (target.isInvisble()
                        && !creature.getNpcTemplate().is_agrocoi())
                || creature.getTarget() != target
                || creature.getMapId() != target.getMapId()
                || target.getX() != targetX || target.getY() != targetY
                || creature.npcActionGeneration() != generation
                || (CCreatureController.currentAction(creature)
                    != CCreatureController.ACTION_MOVE_TO_TARGET
                    && CCreatureController.currentAction(creature)
                    != CCreatureController.ACTION_MOVE_TO_TARGET_RELATIVE)) {
            return false;
        }
        if (!(creature instanceof L1MonsterInstance)) {
            return false;
        }
        final L1MonsterInstance monster =
                (L1MonsterInstance) creature;
        return monster._desireResumeAttackPos ==
                    (((slotX & 0xffff) << 16) | (slotY & 0xffff))
                && monster._desireResumeAttackTargetId == target.getId()
                && monster._desireResumeAttackTargetPos ==
                    (((targetX & 0xffff) << 16) | (targetY & 0xffff))
                && monster._desireResumeAttackMapId == (short) creature.getMapId();
    }

    /**
     * Exact point hand-off with straight raster steering.
     *
     * Used only when MOVE_AROUND releases one monster into a specifically
     * reserved melee slot. Normal chase/random-walk keep the grouped
     * coalesced steering that removes E/SE/E/SE micro-jitter.
     */
    public static byte tickToPointRaster(
            final L1NpcInstance creature,
            final int targetX,
            final int targetY) {

        if (creature == null
                || creature.getMap() == null) {
            return INVALID;
        }

        return tickInternal(
                creature,
                targetX,
                targetY,
                0,
                pointKey(
                        creature.getMapId(),
                        targetX,
                        targetY),
                true,
                false);
    }

    private static byte tickInternal(
            final L1NpcInstance creature,
            final int targetX,
            final int targetY,
            final int desiredDistance,
            final int moveKey,
            final boolean rasterSteering,
            final boolean combatTarget) {

        prepareMoveKey(creature, moveKey);

        final int sx = creature.getX();
        final int sy = creature.getY();

        if (debugTraceActor(creature)) {
            System.out.println(
                    "[CHASE-TICK] t=" + System.currentTimeMillis()
                    + " actor=" + creature.getId()
                    + " pos=" + sx + "," + sy
                    + " target=" + targetX + "," + targetY
                    + " dist=" + desiredDistance
                    + " seg=" + creature._moveSegmentX + "," + creature._moveSegmentY
                    + " segMs=" + creature._moveContactX
                    + " col=" + creature._moveCollision
                    + " resp=" + creature._moveHeading
                    + " sleep=" + creature.getSleepTime());
        }

        if (withinDistanceSquared(
                sx, sy,
                targetX, targetY,
                desiredDistance)) {

            clearSegment(creature);
            clearCollisionResponse(creature);
            return AT_DISTANCE;
        }

        if (combatTarget) {
            refreshLiveTarget(creature, sx, sy, targetX, targetY);
        }

        /*
         * Native does not rebuild the movement vector for every sub-step.
         * Consume the active CalcMidDest segment first.
         */
        int segmentHeading =
                segmentHeading(
                        creature,
                        rasterSteering);

        if (!validHeading(segmentHeading)
                || segmentRemainingMs(creature) <= 0) {

            final int built =
                    buildCalcMidSegment(
                            creature,
                            targetX,
                            targetY,
                            rasterSteering,
                            combatTarget);

            if (built == SEGMENT_PATH_FAILED) {
                clearSegment(creature);
                clearCollisionResponse(creature);
                return PATH_FAILED;
            }

            if (built == SEGMENT_WAIT) {
                return RUNNING;
            }

            segmentHeading =
                    segmentHeading(
                        creature,
                        rasterSteering);

            if (!validHeading(segmentHeading)) {
                return RUNNING;
            }
        }

        /*
         * Native CalcMidDest does not burn an entire walk interval just because
         * the snapped L1 step no longer matches the static corridor. Rebuild the
         * static destination + CalcMidDest immediately once in this same tick.
         * This removes the visible wall-front stop/restart introduced by the L1
         * adapter while still keeping GEO authoritative for the physical commit.
         */
        if (!staticPassable(
                creature,
                segmentHeading)) {

            invalidateStaticDestination(creature);
            expireSegmentKeepHeading(creature);

            final int rebuilt =
                    buildCalcMidSegment(
                            creature,
                            targetX,
                            targetY,
                            rasterSteering,
                            combatTarget);

            if (rebuilt == SEGMENT_PATH_FAILED) {
                clearSegment(creature);
                clearCollisionResponse(creature);
                return PATH_FAILED;
            }

            if (rebuilt != SEGMENT_OK) {
                return RUNNING;
            }

            segmentHeading =
                    segmentHeading(
                        creature,
                        rasterSteering);

            if (!validHeading(segmentHeading)
                    || !staticPassable(
                            creature,
                            segmentHeading)) {

                expireSegmentKeepHeading(creature);
                CCreatureController.sleep(
                        creature,
                        CALC_MID_MIN_MS);
                return RUNNING;
            }
        }

        final byte result = moveCreature(
                creature,
                segmentHeading);

        if (combatTarget
                && result == RUNNING
                && (creature.getX() != sx || creature.getY() != sy)) {

            // Retire the route marker without overwriting the successful
            // step's sprite walk interval. CALC_MID_MAX_MS bounds a planning
            // segment, not a post-step pause. The PathFinder is thread-local
            // and hash-indexed, so a worker change or another actor in the
            // same slot can re-arm this supposedly "one-time" transition.
            AstarProvider.get().consumeInitialChaseAstarTransition(creature);
        }

        return result;
    }

    /**
     * reference server target actions observe the current target every execution. In the
     * 2D grid adapter, only invalidate a cached PathNode waypoint when a live
     * target becomes directly approachable nearby, or has significantly
     * changed the long-range route. Do not run PathFind on every movement tick.
     */
    private static void refreshLiveTarget(
            final L1NpcInstance creature,
            final int sx,
            final int sy,
            final int targetX,
            final int targetY) {
        final int packed = ((targetX & 0xffff) << 16)
                | (targetY & 0xffff);
        final int previous = creature._moveTargetPos;
        if (previous == packed) {
            return;
        }
        if (previous == 0) {
            creature._moveTargetPos = packed;
            return;
        }
        final int oldX = (previous >>> 16) & 0xffff;
        final int oldY = previous & 0xffff;
        final int drift = Math.max(
                Math.abs(targetX - oldX), Math.abs(targetY - oldY));
        final int distance = Math.max(
                Math.abs(targetX - sx), Math.abs(targetY - sy));
        final boolean nearbyDirect =
                distance <= NEAR_DIRECT_TARGET_TILES
                && creature.getMap() != null
                && PathNodeWorld.moveStraight(
                        creature.getMap(), sx, sy, targetX, targetY);
        if (!nearbyDirect && drift < TARGET_REPLAN_DRIFT_TILES) {
            return;
        }
        creature._moveTargetPos = packed;
        invalidateStaticDestination(creature);
        clearSegment(creature);
        clearCollisionResponse(creature);
    }

    // ------------------------------------------------------------------
    // CalcMidDest
    // ------------------------------------------------------------------

    private static final int SEGMENT_OK = 0;
    private static final int SEGMENT_WAIT = 1;
    private static final int SEGMENT_PATH_FAILED = 2;

    /**
     * Builds one native-style CalcMidDest segment.
     *
     * PathFinder returns either the final destination or PathFind's persistent
     * firstDestination. That static destination is not replaced by a local
     * movement goal here.
     */
    private static int buildCalcMidSegment(
            final L1NpcInstance creature,
            final int targetX,
            final int targetY,
            final boolean rasterSteering,
            final boolean combatTarget) {

        final int sx = creature.getX();
        final int sy = creature.getY();

        final PathFinder pathFinder =
                AstarProvider.get();

        final PathFinder.Node destination =
                pathFinder.searchTail(
                        creature,
                        targetX,
                        targetY,
                        creature.getMapId(),
                        false);

        if (combatTarget) {
            pathFinder.armInitialChaseAstarTransition(creature);
        }

        if (debugTraceActor(creature)) {
            System.out.println(
                    "[CHASE-PATH] t=" + System.currentTimeMillis()
                    + " actor=" + creature.getId()
                    + " from=" + sx + "," + sy
                    + " target=" + targetX + "," + targetY
                    + " dest=" + (destination == null ? "null"
                            : destination.x + "," + destination.y)
                    + " result=" + pathFinder.getLastResult());
        }

        if (destination == null
                || (destination.x == sx
                        && destination.y == sy)) {

            final byte pathResult =
                    pathFinder.getLastResult();

            CCreatureController.sleep(
                    creature,
                    calcMidRetryDelay(creature));

            return pathResult == PathFinder.RESULT_NO_ROUTE
                    ? SEGMENT_PATH_FAILED
                    : SEGMENT_WAIT;
        }

        final L1Map map =
                creature.getMap();

        if (map == null) {
            return SEGMENT_PATH_FAILED;
        }

        final int dx = destination.x - sx;
        final int dy = destination.y - sy;

        if ((dx | dy) == 0) {
            CCreatureController.sleep(
                    creature,
                    CALC_MID_MIN_MS);
            return SEGMENT_WAIT;
        }

        final long walkInterval =
                normalizedWalkInterval(creature);

        final double speedTilesPerSecond =
                1000.0d / (double) walkInterval;

        final double destinationDistance =
                Math.sqrt(
                        (double) dx * dx
                                + (double) dy * dy);

        final double maxDistance =
                speedTilesPerSecond
                        * CALC_MID_DISTANCE_SECONDS;

        final double calcDistance =
                Math.min(
                        destinationDistance,
                        maxDistance);

        if (calcDistance <= 0.0d) {
            CCreatureController.sleep(
                    creature,
                    CALC_MID_MIN_MS);
            return SEGMENT_WAIT;
        }

        /*
         * CalcMidDest produces the actual partial arrival vector.  Keep that
         * vector, not the full PathFind destination delta.  More importantly,
         * a failed integer projection does NOT discard PathFind's persistent
         * firstDestination; native keeps that destination until it is reached.
         */
        final long clipped =
                clipStaticSegmentVector(
                        map,
                        sx,
                        sy,
                        destination.x,
                        destination.y,
                        calcDistance);

        if (clipped == 0L) {
            /* Cached firstDestination is no longer reachable from the snapped
             * L1 tile.  Replan instead of sleeping on the same stale waypoint. */
            invalidateStaticDestination(creature);
            clearSegment(creature);
            CCreatureController.sleep(
                    creature,
                    CALC_MID_MIN_MS);
            return SEGMENT_WAIT;
        }

        final int vectorX = (int) (clipped >> 32);
        final int vectorY = (int) clipped;

        /*
         * CalcMidDest's continuous vector points along the FULL PathFind
         * destination direction.  Using the rounded integer partial endpoint to
         * choose heading is what made short 0.8s segments alternate E/SE/E/SE.
         * Quantize from the full remaining destination vector. Monster chase
         * uses its nearest 45-degree heading instead of forcing the dominant
         * cardinal component to finish before the first diagonal step.
         */
        int heading =
                rasterSteering
                        ? firstRasterHeading(
                                sx,
                                sy,
                                destination.x,
                                destination.y)
                        : chaseSegmentHeading(
                                creature, dx, dy);

        /*
         * The integer partial endpoint is still the physical L1 arrival budget.
         * If the smooth heading cannot be committed on this exact tile, fall
         * back to the validated raster's first step; this is a GEO fallback,
         * not a local route choice.
         */
        if (!validHeading(heading)
                || !staticPassable(
                        creature,
                        heading)) {

            heading =
                    firstRasterHeading(
                            sx,
                            sy,
                            sx + vectorX,
                            sy + vectorY);
        }

        if (!validHeading(heading)
                || !staticPassable(
                        creature,
                        heading)) {

            invalidateStaticDestination(creature);
            clearSegment(creature);
            CCreatureController.sleep(
                    creature,
                    CALC_MID_MIN_MS);
            return SEGMENT_WAIT;
        }

        final double actualDistance =
                Math.sqrt(
                        (double) vectorX * vectorX
                                + (double) vectorY * vectorY);

        long segmentMs =
                Math.round(
                        (actualDistance
                                / speedTilesPerSecond)
                                * 1000.0d);

        if (segmentMs < CALC_MID_MIN_MS) {
            segmentMs = CALC_MID_MIN_MS;
        } else if (segmentMs > CALC_MID_MAX_MS) {
            segmentMs = CALC_MID_MAX_MS;
        }

        if (debugTraceActor(creature)) {
            System.out.println(
                    "[CHASE-MID] t=" + System.currentTimeMillis()
                    + " actor=" + creature.getId()
                    + " pathDest=" + destination.x + "," + destination.y
                    + " clip=" + vectorX + "," + vectorY
                    + " anchor=" + destination.x + "," + destination.y
                    + " moveVec=" + dx + "," + dy
                    + " firstDir=" + heading
                    + " segMs=" + segmentMs
                    + " walk=" + walkInterval);
        }

        /*
         * IMPORTANT L1 projection rule:
         *
         * The 0.8s CalcMidDest vector is only the native movement/time budget.
         * Do NOT use its rounded integer endpoint as the lattice steering
         * anchor.  Re-anchoring every 0.8s is exactly what produced
         * E/SE/E/SE micro-zigzag: one rounded segment became (2,0), the next
         * became (2,1), then (2,0) again.
         *
         * Keep PathFind's persistent firstDestination as the steering anchor
         * for the whole corridor. The timer still forces CalcMidDest refresh,
         * while angle selection keeps the SAME waypoint across refreshes.
         */
        storeSegment(
                creature,
                heading,
                (int) segmentMs,
                destination.x,
                destination.y,
                dx,
                dy);

        return SEGMENT_OK;
    }

    /**
     * Integer L1 representative of native MoveStraight partial arrival.
     *
     * This does not pick a different route. Every tested point is on the same
     * current->destination vector and is validated by PathNodeWorld's static
     * MoveStraight implementation.
     */
    private static long clipStaticSegmentVector(
            final L1Map map,
            final int sx,
            final int sy,
            final int destX,
            final int destY,
            final double maxDistance) {

        if (map == null || maxDistance <= 0.0d) {
            return 0L;
        }

        final int dx = destX - sx;
        final int dy = destY - sy;

        final double length =
                Math.sqrt(
                        (double) dx * dx
                                + (double) dy * dy);

        if (length <= 0.0d) {
            return 0L;
        }

        final double scale =
                Math.min(1.0d, maxDistance / length);

        int px =
                (int) Math.round(
                        sx + dx * scale);
        int py =
                (int) Math.round(
                        sy + dy * scale);

        if (px == sx && py == sy) {
            px = sx + Integer.compare(dx, 0);
            py = sy + Integer.compare(dy, 0);
        }

        if (map.isInMap(px, py)
                && PathNodeWorld.moveStraight(
                        map,
                        sx,
                        sy,
                        px,
                        py)) {
            return packVector(
                    px - sx,
                    py - sy);
        }

        /*
         * MoveStraight can stop at a shorter point on the SAME ray.  Shrink
         * only along that ray; never choose a local side route here.
         */
        final int whole =
                Math.max(
                        1,
                        (int) Math.floor(maxDistance));

        for (int tiles = whole - 1;
                tiles >= 1;
                tiles--) {

            final double partial =
                    Math.min(
                            1.0d,
                            (double) tiles / length);

            px = (int) Math.round(
                    sx + dx * partial);
            py = (int) Math.round(
                    sy + dy * partial);

            if (px == sx && py == sy
                    || !map.isInMap(px, py)) {
                continue;
            }

            if (PathNodeWorld.moveStraight(
                    map,
                    sx,
                    sy,
                    px,
                    py)) {
                return packVector(
                        px - sx,
                        py - sy);
            }
        }

        return 0L;
    }

    private static long packVector(
            final int x,
            final int y) {

        return ((long) x << 32)
                | (y & 0xffffffffL);
    }

    // ------------------------------------------------------------------
    // CWorldCollisionHash::MoveCreature adapter
    // ------------------------------------------------------------------

    /**
     * Executes one L1 whole-tile commit of the current native movement vector.
     *
     * The ordering follows SetMoving:
     *  1) current MoveCreature attempt
     *  2) update collision flag
     *  3) only if previous-collision && current-failed -> 300ms retry using the
     *     stored response vector
     */
    private static byte moveCreature(
            final L1NpcInstance creature,
            final int movementHeading) {

        final boolean previousCollision =
                (creature._moveCollision & 0x01) != 0
                        && validHeading(creature._moveHeading);

        final boolean previousDegenerate =
                (creature._moveCollision & 0x02) != 0;

        final int previousResponse =
                creature._moveHeading;

        int vectorX = segmentVectorX(creature);
        int vectorY = segmentVectorY(creature);

        if ((vectorX | vectorY) == 0) {
            vectorX = DX[movementHeading];
            vectorY = DY[movementHeading];
        }

        long probe =
                COLLISION.moveCreatureProbe(
                        (short) creature.getMapId(),
                        creature.getX(),
                        creature.getY(),
                        movementHeading,
                        vectorX,
                        vectorY,
                        creature.getId());

        if ((WorldCollisionHash.moveProbeCollided(probe)
                || !WorldCollisionHash.moveProbeCanMove(probe))) {
            claimDebugTraceActor(creature);
        }

        if (debugTraceActor(creature)) {
            System.out.println(
                    "[CHASE-PROBE] t=" + System.currentTimeMillis()
                    + " actor=" + creature.getId()
                    + " pos=" + creature.getX() + "," + creature.getY()
                    + " dir=" + movementHeading
                    + " vec=" + vectorX + "," + vectorY
                    + " can=" + WorldCollisionHash.moveProbeCanMove(probe)
                    + " col=" + WorldCollisionHash.moveProbeCollided(probe)
                    + " block=" + WorldCollisionHash.moveProbeBlockType(probe)
                    + " blocker=" + WorldCollisionHash.moveProbeBlockerId(probe)
                    + " resp=" + WorldCollisionHash.moveProbeResponseHeading(probe)
                    + " prevCol=" + previousCollision
                    + " prevResp=" + previousResponse);
        }

        /*
         * Native AL=1 may coexist with AH=1.  If this heading is physically
         * commit-able, move first and then store the NEW collision flag/vector
         * returned by this very MoveCreature result.
         */
        if (WorldCollisionHash.moveProbeCanMove(probe)) {
            if (CMoveAction.moveOneStep(
                    creature,
                    movementHeading)) {

                consumeSegmentStep(creature);
                final boolean contacted =
                        WorldCollisionHash.moveProbeCollided(probe);

                applyMoveCreatureResult(
                        creature,
                        probe,
                        -1,
                        false);

                /*
                 * Native AL=1 + AH=1 keeps the active movement state alive.
                 * Preserve this CalcMidDest segment and the returned collision
                 * response; the next tick can use the stored response if the
                 * same body contact becomes blocking.
                 */
                if (debugTraceActor(creature)) {
                    System.out.println(
                            "[CHASE-MOVE] t=" + System.currentTimeMillis()
                            + " actor=" + creature.getId()
                            + " kind=DIRECT"
                            + " after=" + creature.getX() + "," + creature.getY()
                            + " contacted=" + contacted
                            + " newCol=" + creature._moveCollision
                            + " newResp=" + creature._moveHeading
                            + " sleep=" + creature.getSleepTime());
                }
                return RUNNING;
            }

            /* Reservation/occupancy raced after the probe. */
            probe = COLLISION.moveCreatureProbe(
                    (short) creature.getMapId(),
                    creature.getX(),
                    creature.getY(),
                    movementHeading,
                    vectorX,
                    vectorY,
                    creature.getId());

            if (WorldCollisionHash.moveProbeCanMove(probe)) {
                /* Probe was clear but the actual tile reservation lost a race. */
                CCreatureController.sleep(
                        creature,
                        CALC_MID_MIN_MS);
                return RUNNING;
            }
        }

        final byte action = CCreatureController.currentAction(creature);
        final boolean monsterChase = creature instanceof L1MonsterInstance
                && (action == CCreatureController.ACTION_MOVE_TO_TARGET
                    || action == CCreatureController.ACTION_MOVE_TO_TARGET_RELATIVE);
        final int blockType = WorldCollisionHash.moveProbeBlockType(probe);
        // Another NPC's occupied or reserved next tile is a chance to pass,
        // at any target distance. Try a real forward step before waiting or
        // switching to wander; never turn a lateral shuffle into progress.
        if (monsterChase
                && (blockType == WorldCollisionHash.MOVE_NPC
                    || blockType == WorldCollisionHash.MOVE_RESERVED)
                && tryChaseOvertake(creature, movementHeading)) {
            return RUNNING;
        }

        // A reserved next tile can have no body-contact flag. Returning
        // RUNNING here bypasses the action's body-aware detour indefinitely.
        if (monsterChase && blockType == WorldCollisionHash.MOVE_RESERVED) {
            clearCollisionResponse(creature);
            creature._moveSide = (byte) movementHeading;
            return DYNAMIC_BLOCKED;
        }

        if (!WorldCollisionHash.moveProbeCollided(probe)) {
            clearCollisionResponse(creature);
            // A reservation-only race without a bypass is still a short retry,
            // not proof of a body-blocked attack lane.
            CCreatureController.sleep(creature, CALC_MID_MIN_MS);
            return RUNNING;
        }

        if (monsterChase) {
            clearCollisionResponse(creature);
            creature._moveSide = (byte) movementHeading;
            return DYNAMIC_BLOCKED;
        }

        final int currentResponse =
                WorldCollisionHash.moveProbeResponseHeading(
                        probe);

        final int blockerId =
                WorldCollisionHash.moveProbeBlockerId(
                        probe);

        /*
         * Native repeated-collision branch uses the PREVIOUS stored response
         * for movement+response retry.  Unlike the old adapter, the retry is
         * sent through MoveCreature again and its NEW AH/response replaces the
         * old state.
         */
        if (previousCollision
                && tryCollisionResolvedMove(
                        creature,
                        movementHeading,
                        previousResponse,
                        blockerId,
                        previousDegenerate)) {

            consumeSegmentStep(creature);

            /*
             * Keep the active CalcMidDest segment after a successful response
             * move.  Native updates the movement/collision state instead of
             * throwing the destination away and PathFinding again immediately.
             *
             * The native 300ms value is the collision retry timer.  On L1 a
             * successful move has already installed the sprite walk interval in
             * moveOneStep(); overwriting it here makes slow monsters accelerate
             * only while overtaking.
             */
            if (debugTraceActor(creature)) {
                System.out.println(
                        "[CHASE-MOVE] t=" + System.currentTimeMillis()
                        + " actor=" + creature.getId()
                        + " kind=REPEAT_RESOLVED"
                        + " after=" + creature.getX() + "," + creature.getY()
                        + " col=" + creature._moveCollision
                        + " resp=" + creature._moveHeading
                        + " sleep=" + creature.getSleepTime());
            }
            return RUNNING;
        }

        /*
         * First contact, or an old response that became unusable after geometry
         * changed. Use the current response immediately. The L1 whole-tile
         * adapter may probe the opposite FORWARD side if the preferred slide is
         * occupied by another creature, but it never permits a backward step.
         */
        if (validHeading(currentResponse)
                && tryCollisionResolvedMove(
                        creature,
                        movementHeading,
                        currentResponse,
                        blockerId,
                        WorldCollisionHash.moveProbeDegenerate(probe))) {

            consumeSegmentStep(creature);
            if (debugTraceActor(creature)) {
                System.out.println(
                        "[CHASE-MOVE] t=" + System.currentTimeMillis()
                        + " actor=" + creature.getId()
                        + " kind=FIRST_RESOLVED"
                        + " after=" + creature.getX() + "," + creature.getY()
                        + " col=" + creature._moveCollision
                        + " resp=" + creature._moveHeading
                        + " sleep=" + creature.getSleepTime());
            }
            return RUNNING;
        }

        if (validHeading(currentResponse)) {
            storeCollisionResponse(
                    creature,
                    currentResponse,
                    WorldCollisionHash.moveProbeDegenerate(probe));
        } else if (!previousCollision) {
            clearCollisionResponse(creature);
        }

        CCreatureController.sleep(
                creature,
                repeatedCollisionDelay(creature));
        return DYNAMIC_BLOCKED;
    }

    private static boolean tryChaseOvertake(
            final L1NpcInstance creature,
            final int movementHeading) {
        final L1Character target = creature.getTarget();
        if (target == null || target.isDead() || target.getCurrentHp() <= 0
                || creature.isDead() || creature.getCurrentHp() <= 0
                || target.getMapId() != creature.getMapId()
                || !validHeading(movementHeading)) {
            return false;
        }
        final int tx = target.getX();
        final int ty = target.getY();
        final int sx = creature.getX();
        final int sy = creature.getY();
        final long dx = (long) tx - sx;
        final long dy = (long) ty - sy;
        final long before = dx * dx + dy * dy;
        final int generation = creature.npcActionGeneration();
        // Actor-stable preference; changing blockers must not flip the side.
        final int side = (creature.getId() & 1) == 0 ? -1 : 1;
        for (int attempt = 0; attempt < 2; attempt++) {
            final int heading = (movementHeading + (attempt == 0 ? side : -side) + 8) & 7;
            final long nx = dx - DX[heading];
            final long ny = dy - DY[heading];
            if (nx * nx + ny * ny >= before
                    || !staticPassable(creature, heading)) {
                continue;
            }
            final long probe = COLLISION.moveCreatureProbe(
                    (short) creature.getMapId(), sx, sy, heading,
                    DX[heading], DY[heading], creature.getId());
            if (!WorldCollisionHash.moveProbeCanMove(probe)) {
                continue;
            }
            // A collision probe may overlap death/retarget on another owner.
            if (creature.isDead() || creature.getCurrentHp() <= 0
                    || creature.getTarget() != target
                    || creature.npcActionGeneration() != generation
                    || creature.getX() != sx || creature.getY() != sy
                    || target.isDead() || target.getCurrentHp() <= 0
                    || target.getMapId() != creature.getMapId()
                    || target.getX() != tx || target.getY() != ty) {
                return false;
            }
            if (!CMoveAction.moveOneStep(creature, heading)) {
                continue;
            }
            if (!creature.isDead() && creature.getTarget() == target
                    && creature.npcActionGeneration() == generation) {
                consumeSegmentStep(creature);
                applyMoveCreatureResult(creature, probe, -1, false);
                if (debugTraceActor(creature)) {
                    System.out.println("[CHASE-PASS-V3] actor=" + creature.getId()
                            + " from=" + sx + "," + sy
                            + " to=" + creature.getX() + "," + creature.getY()
                            + " heading=" + heading
                            + " sleep=" + creature.getSleepTime());
                }
            }
            return true;
        }
        return false;
    }

    /**
     * L1 whole-tile projection of native
     *   currentMovement + normalize(response) * 20 -> MoveCreature retry.
     *
     * No local path search is performed.  We quantize that single vector first;
     * if a coarse diagonal cannot be committed, the tangent itself is the only
     * fallback representation.  Both attempts go through MoveCreature again so
     * the new collision flag/response is authoritative.
     */
    private static boolean tryCollisionResolvedMove(
            final L1NpcInstance creature,
            final int movementHeading,
            final int responseHeading,
            final int blockerId,
            final boolean responseDegenerate) {

        if (!validHeading(movementHeading)
                || !validHeading(responseHeading)) {
            return false;
        }

        /*
         * Native retries movement + collision-response.  The old L1 adapter
         * had two dangerous fallbacks:
         *
         *   1) if movement + response cancelled to zero, combineHeading()
         *      returned response itself -- which can be the exact BACKWARD
         *      heading;
         *   2) only degenerate contacts were allowed to try the other side, so
         *      a normal response blocked by a second monster stopped the rear
         *      mob in dense packs.
         *
         * Whole-tile L1 needs a discrete projection of the same slide, but it
         * must never turn a collision response into retreat.  Try the native
         * response side first, then a deterministic FORWARD fan around the
         * current movement axis.  Only forward/sideways candidates are legal.
         */
        if (tryCollisionResponseSide(
                creature,
                movementHeading,
                responseHeading,
                responseDegenerate)) {
            return true;
        }

        return tryCrowdForwardFan(
                creature,
                movementHeading,
                responseHeading,
                responseDegenerate,
                blockerId);
    }

    private static boolean tryCollisionResponseSide(
            final L1NpcInstance creature,
            final int movementHeading,
            final int responseHeading,
            final boolean responseDegenerate) {

        final int combinedHeading =
                combineHeading(
                        movementHeading,
                        responseHeading);

        if (validHeading(combinedHeading)
                && isForwardOrLateral(
                        movementHeading,
                        combinedHeading)
                && tryMoveCreatureHeading(
                        creature,
                        combinedHeading,
                        DX[movementHeading]
                                + DX[responseHeading],
                        DY[movementHeading]
                                + DY[responseHeading],
                        responseHeading,
                        responseDegenerate)) {
            return true;
        }

        /*
         * A pure tangent step is allowed only when it is sideways/forward
         * relative to the chase vector.  A stale response is never allowed to
         * send the monster backward.
         */
        return responseHeading != combinedHeading
                && isForwardOrLateral(
                        movementHeading,
                        responseHeading)
                && tryMoveCreatureHeading(
                        creature,
                        responseHeading,
                        DX[responseHeading],
                        DY[responseHeading],
                        responseHeading,
                        responseDegenerate);
    }

    /**
     * Whole-tile crowd seam.
     *
     * Native continuous collision response can slide through gaps that do not
     * exist as a single L1 tile.  If the preferred response tile is occupied by
     * another creature, probe the local FORWARD fan without changing PathFind
     * destination and without ever retreating:
     *
     *   preferred forward-diagonal -> preferred side
     *   -> opposite forward-diagonal -> opposite side
     *
     * The preferred side comes from the native response.  For the rare exact
     * backward/ambiguous response, blocker/mover parity is used only to choose
     * a stable side; it never chooses a backward tile.
     */
    private static boolean tryCrowdForwardFan(
            final L1NpcInstance creature,
            final int movementHeading,
            final int responseHeading,
            final boolean responseDegenerate,
            final int blockerId) {

        int side =
                responseSide(
                        movementHeading,
                        responseHeading);

        if (side == 0) {
            side = (((creature.getId() ^ blockerId) & 1) == 0)
                    ? -1
                    : 1;
        }

        if (tryForwardFanHeading(
                creature,
                movementHeading,
                responseHeading,
                responseDegenerate,
                side)) {
            return true;
        }

        return tryForwardFanHeading(
                creature,
                movementHeading,
                responseHeading,
                responseDegenerate,
                -side);
    }

    private static boolean tryForwardFanHeading(
            final L1NpcInstance creature,
            final int movementHeading,
            final int responseHeading,
            final boolean responseDegenerate,
            final int side) {

        final int diagonal =
                (movementHeading + side + 8) & 7;

        if (isForwardOrLateral(
                movementHeading,
                diagonal)
                && diagonal != responseHeading
                && tryMoveCreatureHeading(
                        creature,
                        diagonal,
                        DX[diagonal],
                        DY[diagonal],
                        responseHeading,
                        responseDegenerate)) {
            return true;
        }

        final int lateral =
                (movementHeading + (side << 1) + 8) & 7;

        return isForwardOrLateral(
                        movementHeading,
                        lateral)
                && lateral != responseHeading
                && lateral != diagonal
                && tryMoveCreatureHeading(
                        creature,
                        lateral,
                        DX[lateral],
                        DY[lateral],
                        responseHeading,
                        responseDegenerate);
    }

    private static int responseSide(
            final int movementHeading,
            final int responseHeading) {

        int delta =
                (responseHeading - movementHeading) & 7;

        if (delta > 4) {
            delta -= 8;
        }

        if (delta > 0 && delta < 4) {
            return 1;
        }

        if (delta < 0 && delta > -4) {
            return -1;
        }

        return 0;
    }

    private static boolean isForwardOrLateral(
            final int movementHeading,
            final int candidateHeading) {

        if (!validHeading(movementHeading)
                || !validHeading(candidateHeading)) {
            return false;
        }

        return DX[movementHeading] * DX[candidateHeading]
                + DY[movementHeading] * DY[candidateHeading] >= 0;
    }

    private static boolean tryMoveCreatureHeading(
            final L1NpcInstance creature,
            final int heading,
            final int vectorX,
            final int vectorY,
            final int fallbackResponse,
            final boolean fallbackDegenerate) {

        if (!validHeading(heading)
                || !staticPassable(
                        creature,
                        heading)) {
            return false;
        }

        final long probe =
                COLLISION.moveCreatureProbe(
                        (short) creature.getMapId(),
                        creature.getX(),
                        creature.getY(),
                        heading,
                        vectorX,
                        vectorY,
                        creature.getId());

        if (!WorldCollisionHash.moveProbeCanMove(probe)) {
            return false;
        }

        if (!CMoveAction.moveOneStep(
                creature,
                heading)) {
            return false;
        }

        applyMoveCreatureResult(
                creature,
                probe,
                fallbackResponse,
                fallbackDegenerate);
        return true;
    }

    private static void applyMoveCreatureResult(
            final L1NpcInstance creature,
            final long probe,
            final int fallbackResponse,
            final boolean fallbackDegenerate) {

        if (!WorldCollisionHash.moveProbeCollided(probe)) {
            clearCollisionResponse(creature);
            return;
        }

        int response =
                WorldCollisionHash.moveProbeResponseHeading(
                        probe);

        boolean degenerate =
                WorldCollisionHash.moveProbeDegenerate(
                        probe);

        /*
         * L1 collapses bodies to exact tile centres, so head-on contacts hit the
         * synthetic collinear seam far more often than native continuous space.
         * Once a response side has been chosen, keep that side across further
         * degenerate contacts instead of letting a new blocker-id parity flip
         * left/right every tile inside a crowd.
         */
        if (degenerate
                && validHeading(fallbackResponse)) {
            response = fallbackResponse;
            degenerate = true;
        } else if (!validHeading(response)) {
            response = fallbackResponse;
            degenerate = fallbackDegenerate;
        }

        if (validHeading(response)) {
            storeCollisionResponse(
                    creature,
                    response,
                    degenerate);
        } else {
            clearCollisionResponse(creature);
        }
    }

    private static int combineHeading(
            final int movementHeading,
            final int responseHeading) {

        if (!validHeading(movementHeading)) {
            return responseHeading;
        }

        if (!validHeading(responseHeading)) {
            return movementHeading;
        }

        final int vx =
                DX[movementHeading]
                        + DX[responseHeading];

        final int vy =
                DY[movementHeading]
                        + DY[responseHeading];

        if ((vx | vy) == 0) {
            /*
             * movement and response cancelled each other. Native vector result
             * is zero; it is NOT permission to step in response (backward).
             */
            return -1;
        }

        final int heading =
                nearestVectorHeading8(
                        vx,
                        vy);

        return isForwardOrLateral(
                movementHeading,
                heading)
                        ? heading
                        : -1;
    }

    private static long repeatedCollisionDelay(
            final L1NpcInstance creature) {

        /* Native repeated-collision retry timer is 300ms. */
        return REPEATED_COLLISION_MS;
    }

    // ------------------------------------------------------------------
    // Primitive action state
    // ------------------------------------------------------------------

    /*
     * Existing L1NpcInstance primitive state; no per-NPC action object:
     *
     *   _ncMoveSide      : last lattice heading, -1 = no active segment
     *   _ncMoveContactX  : remaining CalcMidDest lifetime in milliseconds
     *   _ncMoveContactY  : packed native movement direction (s16 x/y)
     *   _ncMoveSegmentX/Y: persistent PathFind destination/waypoint anchor
     *   _ncMoveCollision : previous MoveCreature collision flag
     *   _ncMoveHeading   : stored collision response heading
     */

    private static int segmentHeading(
            final L1NpcInstance creature,
            final boolean rasterSteering) {

        if (creature._moveSide < 0
                || creature._moveContactX <= 0) {
            return -1;
        }

        final int currentX = creature.getX();
        final int currentY = creature.getY();
        final int endX = creature._moveSegmentX;
        final int endY = creature._moveSegmentY;

        if (currentX == endX && currentY == endY) {
            expireSegmentKeepHeading(creature);
            return -1;
        }

        final int remainingX = endX - currentX;
        final int remainingY = endY - currentY;
        final int previous = creature._moveSide;

        int heading =
                rasterSteering
                        ? firstRasterHeading(
                                currentX,
                                currentY,
                                endX,
                                endY)
                        : chaseSegmentHeading(
                                creature, remainingX, remainingY);

        /*
         * Monster chase follows the live remaining vector's nearest 8-way
         * angle. Other movement keeps the grouped projection. GEO wins per
         * physical tile; a blocked preferred heading uses the validated raster
         * step only for this tile.
         */
        if (!validHeading(heading)
                || !staticPassable(
                        creature,
                        heading)) {

            final int raster =
                    firstRasterHeading(
                            currentX,
                            currentY,
                            endX,
                            endY);

            if (validHeading(raster)
                    && staticPassable(
                            creature,
                            raster)) {
                heading = raster;
            }
        }

        if (!validHeading(heading)) {
            expireSegmentKeepHeading(creature);
            return -1;
        }

        if (debugTraceActor(creature)
                && previous != heading) {
            System.out.println(
                    "[CHASE-SEGMENT] t=" + System.currentTimeMillis()
                    + " actor=" + creature.getId()
                    + " result=TURN"
                    + " fromDir=" + previous
                    + " toDir=" + heading
                    + " remain=" + remainingX + "," + remainingY
                    + " end=" + endX + "," + endY);
        }

        creature._moveSide =
                (byte) heading;

        return heading;
    }

    /** Monster chase follows angle; unrelated movement retains its old policy. */
    private static int chaseSegmentHeading(
            final L1NpcInstance creature,
            final int dx,
            final int dy) {
        final byte action = CCreatureController.currentAction(creature);
        if (!(creature instanceof L1MonsterInstance)
                || (action != CCreatureController.ACTION_MOVE_TO_TARGET
                    && action != CCreatureController.ACTION_MOVE_TO_TARGET_RELATIVE)) {
            return coalescedSegmentHeading(dx, dy);
        }
        if ((dx | dy) == 0) {
            return -1;
        }
        final long ax = Math.abs((long) dx);
        final long ay = Math.abs((long) dy);
        // Same nearest-45-degree threshold already used by PathFinder.
        // At 4 o'clock, both vector components matter even when unequal.
        if (ax == 0L || ax * 10000L <= ay * 4142L) {
            return dy < 0 ? 0 : 4;
        }
        if (ay == 0L || ay * 10000L <= ax * 4142L) {
            return dx > 0 ? 2 : 6;
        }
        return dx > 0 ? (dy < 0 ? 1 : 3) : (dy < 0 ? 7 : 5);
    }

    /**
     * Minimal-turn whole-tile projection of a continuous CalcMidDest vector.
     * Consume the dominant cardinal component first, then finish diagonally.
     *
     * (5,1) => E,E,E,E,SE
     * (2,5) => S,S,S,SE,SE
     *
     * This is the L1 grid adapter for native continuous movement; it is not a
     * second path search and allocates no per-NPC state.
     */
    private static int coalescedSegmentHeading(
            final int remainingX,
            final int remainingY) {

        if ((remainingX | remainingY) == 0) {
            return -1;
        }

        final int sx = Integer.compare(remainingX, 0);
        final int sy = Integer.compare(remainingY, 0);
        final long ax = Math.abs((long) remainingX);
        final long ay = Math.abs((long) remainingY);

        if (ay == 0L || ax > ay) {
            return sx > 0 ? 2 : 6;
        }

        if (ax == 0L || ay > ax) {
            return sy < 0 ? 0 : 4;
        }

        if (sx > 0) {
            return sy < 0 ? 1 : 3;
        }

        return sy < 0 ? 7 : 5;
    }

    private static int firstRasterHeading(
            final int startX,
            final int startY,
            final int endX,
            final int endY) {

        if (startX == endX && startY == endY) {
            return -1;
        }

        final int dx = Math.abs(endX - startX);
        final int dy = Math.abs(endY - startY);
        final int stepX = Integer.compare(endX, startX);
        final int stepY = Integer.compare(endY, startY);
        final int err = dx - dy;
        final int e2 = err << 1;

        int nx = startX;
        int ny = startY;

        if (e2 > -dy) {
            nx += stepX;
        }
        if (e2 < dx) {
            ny += stepY;
        }

        return headingFromStep(
                nx - startX,
                ny - startY);
    }

    private static int headingFromStep(
            final int dx,
            final int dy) {

        if (dx == 0 && dy < 0) return 0;
        if (dx > 0 && dy < 0) return 1;
        if (dx > 0 && dy == 0) return 2;
        if (dx > 0 && dy > 0) return 3;
        if (dx == 0 && dy > 0) return 4;
        if (dx < 0 && dy > 0) return 5;
        if (dx < 0 && dy == 0) return 6;
        if (dx < 0 && dy < 0) return 7;
        return -1;
    }

    private static int segmentRemainingMs(
            final L1NpcInstance creature) {

        return creature._moveContactX;
    }

    private static void storeSegment(
            final L1NpcInstance creature,
            final int heading,
            final int remainingMs,
            final int destinationX,
            final int destinationY,
            final int movementVectorX,
            final int movementVectorY) {

        creature._moveSide =
                (byte) heading;

        creature._moveContactX =
                remainingMs;

        /*
         * Keep the continuous movement DIRECTION independent from the lattice
         * anchor.  Collision response only needs this ratio; packing the full
         * destination vector also prevents the 0.8s rounded clip from changing
         * the response direction every refresh.
         */
        creature._moveContactY =
                packSegmentVector(
                        movementVectorX,
                        movementVectorY);

        /*
         * Persistent steering anchor.  This is deliberately the PathFinder
         * destination, NOT current + rounded CalcMidDest clip.
         */
        creature._moveSegmentX = destinationX;
        creature._moveSegmentY = destinationY;
    }

    private static int segmentVectorX(
            final L1NpcInstance creature) {

        final int packed =
                (short) (creature._moveContactY >>> 16);

        if (packed != 0
                || ((short) creature._moveContactY) != 0) {
            return packed;
        }

        return creature._moveSegmentX - creature.getX();
    }

    private static int segmentVectorY(
            final L1NpcInstance creature) {

        final int packed =
                (short) creature._moveContactY;

        if (packed != 0
                || ((short) (creature._moveContactY >>> 16)) != 0) {
            return packed;
        }

        return creature._moveSegmentY - creature.getY();
    }

    /**
     * Preserve CalcMidDest's direction before 8-way heading quantization.
     * The collision response only needs the vector ratio, so values larger
     * than signed-16 range are scaled together without changing direction.
     */
    private static int packSegmentVector(
            final int x,
            final int y) {

        if ((x | y) == 0) {
            return 0;
        }

        long ax = Math.abs((long) x);
        long ay = Math.abs((long) y);
        final long max = Math.max(ax, ay);

        int px = x;
        int py = y;

        if (max > 32767L) {
            px = (int) (((long) x * 32767L) / max);
            py = (int) (((long) y * 32767L) / max);

            if (px == 0 && x != 0) {
                px = x > 0 ? 1 : -1;
            }
            if (py == 0 && y != 0) {
                py = y > 0 ? 1 : -1;
            }
        }

        return ((px & 0xffff) << 16)
                | (py & 0xffff);
    }

    private static void consumeSegmentStep(
            final L1NpcInstance creature) {

        if (creature.getX() == creature._moveSegmentX
                && creature.getY() == creature._moveSegmentY) {
            expireSegmentKeepHeading(creature);
            return;
        }

        int remainingMs =
                creature._moveContactX;

        remainingMs -=
                (int) normalizedWalkInterval(
                        creature);

        if (remainingMs <= 0) {
            expireSegmentKeepHeading(creature);
            return;
        }

        creature._moveContactX =
                remainingMs;
    }

    private static void expireSegmentKeepHeading(
            final L1NpcInstance creature) {

        /*
         * Timer expiry means "refresh CalcMidDest", not "forget PathFind's
         * steering anchor".  Keeping the waypoint here is what prevents every
         * 0.8s refresh from restarting the L1 turn phase.
         */
        creature._moveContactX = 0;
        creature._moveContactY = 0;
    }

    private static void clearSegment(
            final L1NpcInstance creature) {

        creature._moveSide = -1;
        creature._moveContactX = 0;
        creature._moveContactY = 0;
        creature._moveSegmentX = 0;
        creature._moveSegmentY = 0;
    }

    private static void storeCollisionResponse(
            final L1NpcInstance creature,
            final int responseHeading,
            final boolean degenerate) {

        creature._moveCollision =
                (byte) (degenerate ? 0x03 : 0x01);
        creature._moveHeading =
                (byte) responseHeading;
    }

    private static void clearCollisionResponse(
            final L1NpcInstance creature) {

        creature._moveCollision = 0;
        creature._moveHeading = -1;
    }

    private static void prepareMoveKey(
            final L1NpcInstance creature,
            final int moveKey) {

        if (creature._moveTargetId
                == moveKey) {
            return;
        }

        invalidateStaticDestination(creature);
        clearSegment(creature);
        clearCollisionResponse(creature);

        creature._pathFindFailCount = 0;
        creature._moveTargetId = moveKey;
        creature._moveTargetPos = 0;
    }

    private static void invalidateStaticDestination(
            final L1NpcInstance creature) {

        try {
            AstarProvider.get().invalidate(
                    creature,
                    creature.getMapId());
        } catch (Throwable ignore) {
        }
    }

    public static void clear(
            final L1NpcInstance creature) {

        if (creature == null) {
            return;
        }

        invalidateStaticDestination(creature);
        clearSegment(creature);
        clearCollisionResponse(creature);

        creature._moveTargetId = 0;
        creature._moveTargetPos = 0;
        creature._pathFindFailCount = 0;
    }

    // ------------------------------------------------------------------
    // Low-level helpers
    // ------------------------------------------------------------------

    private static long normalizedWalkInterval(
            final L1NpcInstance creature) {

        long interval =
                creature.getCurrentSpriteInterval(
                        EActionCodes.walk);

        if (interval < CALC_MID_MIN_MS) {
            interval = CALC_MID_MIN_MS;
        } else if (interval > CALC_MID_MAX_MS) {
            interval = CALC_MID_MAX_MS;
        }

        return interval;
    }

    private static long calcMidRetryDelay(
            final L1NpcInstance creature) {

        return normalizedWalkInterval(
                creature);
    }

    private static int nearestVectorHeading8(
            final int dx,
            final int dy) {

        if ((dx | dy) == 0) {
            return -1;
        }

        final int sx =
                Integer.compare(dx, 0);

        final int sy =
                Integer.compare(dy, 0);

        final long ax =
                Math.abs((long) dx);

        final long ay =
                Math.abs((long) dy);

        if (ax == 0L) {
            return sy < 0 ? 0 : 4;
        }

        if (ay == 0L) {
            return sx > 0 ? 2 : 6;
        }

        /*
         * Nearest 45-degree quantization of native atan2 heading.
         * tan(22.5deg) ~= 0.4142.
         */
        if (ax * 10000L
                <= ay * 4142L) {

            return sy < 0 ? 0 : 4;
        }

        if (ay * 10000L
                <= ax * 4142L) {

            return sx > 0 ? 2 : 6;
        }

        if (sx > 0) {
            return sy < 0 ? 1 : 3;
        }

        return sy < 0 ? 7 : 5;
    }

    private static boolean validHeading(
            final int heading) {

        return heading >= 0
                && heading <= 7;
    }

    private static boolean staticPassable(
            final L1NpcInstance creature,
            final int heading) {

        return staticPassable(
                creature.getMap(),
                creature.getX(),
                creature.getY(),
                heading);
    }

    private static boolean staticPassable(
            final L1Map map,
            final int x,
            final int y,
            final int heading) {

        return map != null
                && validHeading(heading)
                && map.isMovePassableGeoNpc(
                        x,
                        y,
                        heading);
    }

    private static int pointKey(
            final int mapId,
            final int x,
            final int y) {

        int key = 0x80000000;

        key ^= mapId * 0x1f1f1f1f;
        key ^= x * 0x045d9f3b;
        key ^= y * 0x119de1f3;

        return key == 0
                ? 0x80000001
                : key;
    }

    private static boolean valid(
            final L1NpcInstance creature,
            final L1Character target) {

        return creature != null
                && target != null
                && !target.isDead()
                && target.getCurrentHp() > 0
                && target.getMapId()
                        == creature.getMapId();
    }

    private static boolean withinDistanceSquared(
            final int x0,
            final int y0,
            final int x1,
            final int y1,
            final int distance) {

        final long dx = (long) x1 - x0;
        final long dy = (long) y1 - y0;
        final long d = Math.max(0, distance);

        return dx * dx + dy * dy
                <= d * d;
    }
}
