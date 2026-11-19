package l1j.server.server.model.ai.core;

import l1j.server.server.model.L1Object;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1WorldMap;

public final class PathFinder {

    /* Static route trace only. Disable after validation. */
    private static final boolean DEBUG_FLOW = false;

    private static final int TRACE_RAW    = 0;
    private static final int TRACE_DIRECT = 1;
    private static final int TRACE_CACHE  = 2;
    private static final int TRACE_ASTAR  = 3;

    public static final byte RESULT_NONE       = 0;
    public static final byte RESULT_STEP       = 1;
    public static final byte RESULT_SOFT_BLOCK = 2;
    public static final byte RESULT_NO_ROUTE   = 3;

    private byte lastResult = RESULT_NONE;
    private int lastBlockType = WorldCollisionHash.MOVE_CLEAR;

    public byte getLastResult() {
        return lastResult;
    }

    public int getLastBlockType() {
        return lastBlockType;
    }

    public static final class Node {

        public int x, y, g, h, f;
        public Node prev;

        public void reuse(int x, int y, int g, int h, Node prev) {
            this.x = x;
            this.y = y;
            this.g = g;
            this.h = h;
            this.f = g + h;
            this.prev = prev;
        }
    }

    /*
     * Bounded L1 body-occupancy route, separate from the persistent static
     * PathNode destination. This is only requested for a blocked melee-slot
     * hand-off. Fixed primitive workspace belongs to the existing per-owner
     * AstarProvider PathFinder; no per-search nodes, queue or worker allocation.
     */
    private static final int BODY_ROUTE_RADIUS = 32;
    private static final int BODY_ROUTE_LOCAL_RADIUS = 12;
    private static final int BODY_ROUTE_SIDE = BODY_ROUTE_RADIUS * 2 + 1;
    private static final int BODY_ROUTE_CELLS = BODY_ROUTE_SIDE * BODY_ROUTE_SIDE;
    private static final int[] BODY_ROUTE_ORDER = { 0, 1, 7, 2, 6, 3, 5, 4 };
    private final int[] bodyRouteSeen = new int[BODY_ROUTE_CELLS];
    private final short[] bodyRouteDistance = new short[BODY_ROUTE_CELLS];
    private final byte[] bodyRouteFirst = new byte[BODY_ROUTE_CELLS];
    private final int[] bodyRouteQueue = new int[BODY_ROUTE_CELLS];
    private int bodyRouteEpoch;
    private int bodyRouteOriginX;
    private int bodyRouteOriginY;

    /**
     * Dynamic reachability for the target's eight melee slots or a reserved
     * slot's next physical step. The static graph stays authoritative for
     * normal chase; this bounded L1 grid test is not a second world A*.
     *
     * Returns no route if the tile lies outside the bounded neighbourhood.
     * Occupancy can change immediately after the scan: movement still needs
     * the ordinary atomic WorldCollisionHash reservation/commit.
     */
    public void scanBodyRoute(
            final L1Object actor,
            final int goalX,
            final int goalY,
            final boolean stopAtGoal) {
        scanBodyRouteInternal(actor, goalX, goalY, stopAtGoal, 0);
    }

    /**
     * Scan only until every currently usable melee-ring candidate is reached.
     * The caller supplies the eight-bit slot mask after checking static attack
     * geometry and occupancy. Ordinary chases use a small local window; a miss
     * retries a larger bounded window covering actor and target. No ownership is
     * reserved by this scan: the caller must claim its chosen slot afterwards.
     */
    public void scanBodyRouteToMeleeRing(
            final L1Object actor,
            final int targetX,
            final int targetY,
            final int candidateMask) {
        scanBodyRouteInternal(actor, targetX, targetY,
                false, candidateMask & 0xff);
    }

    private void scanBodyRouteInternal(
            final L1Object actor,
            final int goalX,
            final int goalY,
            final boolean stopAtGoal,
            final int candidateMask) {
        final int sx = actor == null ? 0 : actor.getX();
        final int sy = actor == null ? 0 : actor.getY();
        // Preserve the cheap local search for ordinary crowds. Failure within
        // this window does not establish that another entrance is unreachable.
        scanBodyRouteWindow(actor, goalX, goalY, stopAtGoal, candidateMask,
                sx, sy, BODY_ROUTE_LOCAL_RADIUS);
        if (actor == null) return;
        if (stopAtGoal && bodyRouteDistanceTo(goalX, goalY) >= 0) return;
        if (!stopAtGoal && candidateMask != 0) {
            for (int h = 0; h < 8; h++) {
                if ((candidateMask & (1 << h)) != 0
                        && bodyRouteDistanceTo(goalX + DX[h], goalY + DY[h]) >= 0) {
                    return;
                }
            }
        }
        // Center the larger bounded window between actor and destination so
        // BOTH ends and opposite-side melee slots remain inside the workspace.
        // Reuse the same primitive buffers; no queue/nodes allocated per scan.
        scanBodyRouteWindow(actor, goalX, goalY, stopAtGoal, candidateMask,
                (int) (((long) sx + goalX) / 2L),
                (int) (((long) sy + goalY) / 2L), BODY_ROUTE_RADIUS);
    }

    private void scanBodyRouteWindow(
            final L1Object actor,
            final int goalX,
            final int goalY,
            final boolean stopAtGoal,
            int remainingRingMask,
            final int centerX,
            final int centerY,
            final int radius) {
        int epoch = ++bodyRouteEpoch;
        if (epoch <= 0) {
            java.util.Arrays.fill(bodyRouteSeen, 0);
            epoch = bodyRouteEpoch = 1;
        }

        bodyRouteOriginX = centerX;
        bodyRouteOriginY = centerY;
        if (actor == null) {
            return;
        }

        final short mapId = (short) actor.getMapId();
        final L1Map map = L1WorldMap.getInstance().getMap(mapId);
        if (map == null || map.isNull()) {
            return;
        }

        final int actorId = actor.getId();
        final WorldCollisionHash collision = WorldCollisionHash.getInstance();
        final int rootX = actor.getX() - centerX + BODY_ROUTE_RADIUS;
        final int rootY = actor.getY() - centerY + BODY_ROUTE_RADIUS;
        final int minCell = BODY_ROUTE_RADIUS - radius;
        final int maxCell = BODY_ROUTE_RADIUS + radius;
        if (rootX < minCell || rootX > maxCell
                || rootY < minCell || rootY > maxCell) return;
        final int root = rootY * BODY_ROUTE_SIDE + rootX;
        bodyRouteSeen[root] = epoch;
        bodyRouteDistance[root] = 0;
        bodyRouteFirst[root] = -1;
        int read = 0;
        int write = 1;
        bodyRouteQueue[0] = root;

        final int favored = headingFromDelta(
                Integer.compare(goalX, actor.getX()),
                Integer.compare(goalY, actor.getY()));
        final int preferred = favored < 0 ? 0 : favored;

        while (read < write) {
            final int at = bodyRouteQueue[read++];
            final int cx = at % BODY_ROUTE_SIDE;
            final int cy = at / BODY_ROUTE_SIDE;
            final int x = bodyRouteOriginX + cx - BODY_ROUTE_RADIUS;
            final int y = bodyRouteOriginY + cy - BODY_ROUTE_RADIUS;
            final int nextDist = bodyRouteDistance[at] + 1;

            for (int i = 0; i < 8; i++) {
                final int heading = (preferred + BODY_ROUTE_ORDER[i]) & 7;
                final int nx = cx + DX[heading];
                final int ny = cy + DY[heading];
                if (nx < minCell || ny < minCell
                        || nx > maxCell || ny > maxCell) {
                    continue;
                }
                final int index = ny * BODY_ROUTE_SIDE + nx;
                if (bodyRouteSeen[index] == epoch) {
                    continue;
                }
                final int worldX = x + DX[heading];
                final int worldY = y + DY[heading];
                if (!PathNodeWorld.isStepPassableStrict(map, x, y, heading)
                        || !collision.canEnter(mapId, worldX, worldY, actorId)) {
                    continue;
                }
                // Do not pass between two occupied corner cells on a diagonal.
                if (DX[heading] != 0 && DY[heading] != 0
                        && !collision.canEnter(mapId, x + DX[heading], y, actorId)
                        && !collision.canEnter(mapId, x, y + DY[heading], actorId)) {
                    continue;
                }
                bodyRouteSeen[index] = epoch;
                bodyRouteDistance[index] = (short) nextDist;
                bodyRouteFirst[index] = (byte) (at == root
                        ? heading : bodyRouteFirst[at]);
                if (stopAtGoal && worldX == goalX && worldY == goalY) {
                    return;
                }
                if (remainingRingMask != 0) {
                    final int ox = worldX - goalX;
                    final int oy = worldY - goalY;
                    if (ox >= -1 && ox <= 1
                            && oy >= -1 && oy <= 1
                            && (ox | oy) != 0) {
                        final int ringHeading = headingFromDelta(ox, oy);
                        if (ringHeading >= 0) {
                            remainingRingMask &= ~(1 << ringHeading);
                            if (remainingRingMask == 0) {
                                return;
                            }
                        }
                    }
                }
                bodyRouteQueue[write++] = index;
            }
        }
    }

    public int bodyRouteDistanceTo(final int x, final int y) {
        final int dx = x - bodyRouteOriginX + BODY_ROUTE_RADIUS;
        final int dy = y - bodyRouteOriginY + BODY_ROUTE_RADIUS;
        if (dx < 0 || dx >= BODY_ROUTE_SIDE
                || dy < 0 || dy >= BODY_ROUTE_SIDE) {
            return -1;
        }
        final int index = dy * BODY_ROUTE_SIDE + dx;
        return bodyRouteSeen[index] == bodyRouteEpoch
                ? bodyRouteDistance[index] : -1;
    }

    public int bodyRouteFirstHeadingTo(final int x, final int y) {
        final int dx = x - bodyRouteOriginX + BODY_ROUTE_RADIUS;
        final int dy = y - bodyRouteOriginY + BODY_ROUTE_RADIUS;
        if (dx < 0 || dx >= BODY_ROUTE_SIDE
                || dy < 0 || dy >= BODY_ROUTE_SIDE) {
            return -1;
        }
        final int index = dy * BODY_ROUTE_SIDE + dx;
        return bodyRouteSeen[index] == bodyRouteEpoch
                ? bodyRouteFirst[index] : -1;
    }

    public static boolean DEBUG_PATH = false;
    public static boolean DEBUG_DIAG_REJECT = false;

    public static int diagAllowCount;
    public static int diagRejectCount;

    private static final int ROUTE_CACHE_SIZE = 4096;
    private static final int ROUTE_CACHE_MASK = ROUTE_CACHE_SIZE - 1;

    /**
     * Combat local-chase boundary.
     * The 40-tile boundary remains; PathNodeWorld still owns macro/static
     * first-destination routing outside that local range.
     */
    private static final int NATIVE_ASTAR_MAX_DISTANCE = 40;

    /* Generic/follow legacy one-tile cache only. Native combat does not use
     * target/actor drift invalidation. */
    private static final int ROUTE_TARGET_DRIFT = 4;
    private static final int ROUTE_ACTOR_DRIFT = 4;

    private final long[] routeKey = new long[ROUTE_CACHE_SIZE];
    private final long[] routeTargetAnchor = new long[ROUTE_CACHE_SIZE];
    private final long[] routeWaypoint = new long[ROUTE_CACHE_SIZE];
    private final long[] routeLastPos = new long[ROUTE_CACHE_SIZE];

    /*
     * Native CMoveToTargetAction pathnode destination state.
     *
     * No target-drift or actor-drift invalidation. Native keeps the first
     * PathNode destination until the mover reaches it; target motion does not
     * force a new PathFind.
     */
    private final long[] nativeRouteKey = new long[ROUTE_CACHE_SIZE];
    private final long[] nativeWaypoint = new long[ROUTE_CACHE_SIZE];

    /*
     * One-time combat A* entry: 0 = not armed, 1 = pending first successful
     * A* chase step, 2 = consumed. A direct chase never arms this pause.
     */
    private final int[] initialChaseActor = new int[ROUTE_CACHE_SIZE];
    private final byte[] initialChaseState = new byte[ROUTE_CACHE_SIZE];

    /*
     * Per-call observation only. True only when the current native searchTail()
     * actually created a new PathFind first-destination, not on cache hits.
     */
    private boolean lastNativeAstarBuilt;

    private static final int[] DX =
            { 0, 1, 1, 1, 0, -1, -1, -1 };
    private static final int[] DY =
            { -1, -1, 0, 1, 1, 1, 0, -1 };

    /*
     * PathFinder owns only the static route -> one-tile projection.
     * PathNodeWorld chooses the final/waypoint destination.  Native keeps an
     * FVector and derives heading from atan2(arrival-current); L1 projects that
     * vector to the nearest 8-way tile heading.  No local sidestep lives here.
     */
    private final Node source = new Node();
    private final Node next = new Node();
    private final Node nativeDestination = new Node();

    /**
     * Static route entry.
     *
     * Combat/native movement (useObjects=false) returns a destination, not a
     * one-tile step. SetMoving owns CalcMidDest/heading/collision response.
     *
     * Generic movement (useObjects=true) keeps the legacy one-tile adapter so
     * follow/group movement is not changed by this combat restoration.
     */
    public Node searchTail(
            final L1Object actor,
            final int targetX,
            final int targetY,
            final int mapId,
            final boolean useObjects) {

        if (!useObjects) {
            lastResult = RESULT_NO_ROUTE;
            lastBlockType = WorldCollisionHash.MOVE_CLEAR;
            lastNativeAstarBuilt = false;

            observeInitialChaseActor(actor);

            if (actor == null || actor.getMapId() != mapId) {
                return null;
            }

            final int distance =
                    chebyshev(
                            actor.getX(),
                            actor.getY(),
                            targetX,
                            targetY);

            final boolean hasGraph =
                    PathNodeMaps.contains(mapId);

            if (!hasGraph) {
                return searchNativeDirectDestination(
                        actor,
                        targetX,
                        targetY,
                        mapId,
                        distance,
                        "NO_GRAPH");
            }

            /*
             * Keep the 40-tile PathFinder boundary, but do not confuse it with
             * the macro/static route owner.  Beyond the local chase boundary
             * PathNodeWorld still has to return the first macro destination;
             * returning the raw target here makes a far NPC walk straight into
             * the first wall forever.
             */
            if (distance > NATIVE_ASTAR_MAX_DISTANCE) {
                return searchNativeMacroDestination(
                        actor,
                        targetX,
                        targetY,
                        mapId);
            }

            return searchNativeDestination(
                    actor,
                    targetX,
                    targetY,
                    mapId,
                    PathNodeWorld.SEARCH_LIMIT_MOVE_TO_TARGET);
        }

        return searchLegacyStep(
                actor,
                targetX,
                targetY,
                mapId,
                useObjects);
    }

    /**
     * Combat fallback only for maps with no PathNode graph.
     * Return the real target coordinate as the destination.
     */
    private Node searchNativeDirectDestination(
            final L1Object actor,
            final int targetX,
            final int targetY,
            final int mapId,
            final int distance,
            final String reason) {

        lastResult = RESULT_NO_ROUTE;
        lastBlockType = WorldCollisionHash.MOVE_CLEAR;

        if (actor == null || actor.getMapId() != mapId) {
            return null;
        }

        final int sx = actor.getX();
        final int sy = actor.getY();

        if (sx == targetX && sy == targetY) {
            clearNativeWaypoint(actor, mapId);
            lastResult = RESULT_NONE;
            return null;
        }

        final L1Map map =
                L1WorldMap.getInstance().getMap((short) mapId);

        if (map == null) {
            clearNativeWaypoint(actor, mapId);
            return null;
        }

        /*
         * If the chase leaves the near-field A* range, an old PathNode
         * destination must not survive and keep steering the actor away from
         * the real target.
         */
        clearNativeWaypoint(actor, mapId);

        nativeDestination.reuse(
                targetX,
                targetY,
                0,
                0,
                null);

        lastResult = RESULT_STEP;

        if (DEBUG_FLOW) {
            System.out.println(
                    "[NPC-PATH] mode=RAW result=DEST"
                    + " bypass=" + reason
                    + " actor=" + actor.getId()
                    + " map=" + mapId
                    + " from=" + sx + "," + sy
                    + " dest=" + targetX + "," + targetY
                    + " target=" + targetX + "," + targetY
                    + " distance=" + distance);
        }

        return nativeDestination;
    }

    /**
     * Far combat chase.  The local PathFinder cutoff remains 40 tiles; this
     * path only asks PathNodeWorld (the macro/static route owner) for the same
     * persistent first destination used by the near chase.
     */
    private Node searchNativeMacroDestination(
            final L1Object actor,
            final int targetX,
            final int targetY,
            final int mapId) {

        /*
         * Far/macro pursuit is deliberately separate from the native
         * MoveToTarget near-field search budget.  The 40-tile boundary remains:
         * near chase keeps the recovered 0x190 (400) budget, while the macro
         * graph bridge uses PathNodeWorld's existing 0x3E8 (1000) budget.
         *
         * Without this split, a rear NPC can sit on the same tile forever:
         * every replan enters the macro graph but is still aborted at 401
         * expansions before a firstDestination is produced.
         */
        return searchNativeDestination(
                actor,
                targetX,
                targetY,
                mapId,
                PathNodeWorld.SEARCH_LIMIT_LONG);
    }

    /**
     * CMoveToTargetAction::SetMoving static destination contract.
     *
     * Native behavior:
     * - keep a PathNode first-destination until it is reached;
     * - do not discard it because the final target moved;
     * - do not re-test direct target visibility while that waypoint is active;
     * - once reached, call PathFind again.
     */
    private Node searchNativeDestination(
            final L1Object actor,
            final int targetX,
            final int targetY,
            final int mapId,
            final int maxExpansions) {

        lastResult = RESULT_NO_ROUTE;
        lastBlockType = WorldCollisionHash.MOVE_CLEAR;

        if (actor == null || actor.getMapId() != mapId) {
            return null;
        }

        final int sx = actor.getX();
        final int sy = actor.getY();

        if (sx == targetX && sy == targetY) {
            lastResult = RESULT_NONE;
            return null;
        }

        final L1Map map =
                L1WorldMap.getInstance().getMap((short) mapId);

        if (map == null) {
            return null;
        }

        long destination =
                getNativeWaypoint(
                        actor,
                        mapId,
                        sx,
                        sy);

        int traceMode = TRACE_CACHE;

        if (destination == PathNodeWorld.NO_PATH) {
            destination =
                    PathNodeWorld.getInstance().pathFind(
                            mapId,
                            sx,
                            sy,
                            targetX,
                            targetY,
                            maxExpansions);

            if (destination == PathNodeWorld.NO_PATH) {
                clearNativeWaypoint(actor, mapId);
                lastResult = RESULT_NO_ROUTE;

                if (DEBUG_FLOW) {
                    System.out.println(
                            "[NPC-PATH] mode=ASTAR_FAIL"
                            + " actor=" + actor.getId()
                            + " map=" + mapId
                            + " from=" + sx + "," + sy
                            + " target=" + targetX + "," + targetY
                            + " distance="
                            + chebyshev(
                                    sx, sy,
                                    targetX, targetY));
                }

                return null;
            }

            final int dx =
                    PathNodeWorld.unpackX(destination);
            final int dy =
                    PathNodeWorld.unpackY(destination);

            if (dx == targetX && dy == targetY) {
                traceMode = TRACE_DIRECT;
            } else {
                traceMode = TRACE_ASTAR;
                lastNativeAstarBuilt = true;
                cacheNativeWaypoint(
                        actor,
                        mapId,
                        destination);
            }
        }

        final int destX =
                PathNodeWorld.unpackX(destination);
        final int destY =
                PathNodeWorld.unpackY(destination);

        nativeDestination.reuse(
                destX,
                destY,
                0,
                0,
                null);

        lastResult = RESULT_STEP;

        if (DEBUG_FLOW) {
            System.out.println(
                    "[NPC-PATH] mode=" + traceModeName(traceMode)
                    + " result=DEST"
                    + " actor=" + actor.getId()
                    + " map=" + mapId
                    + " from=" + sx + "," + sy
                    + " dest=" + destX + "," + destY
                    + " target=" + targetX + "," + targetY);
        }

        return nativeDestination;
    }

    private Node searchLegacyStep(L1Object actor, int targetX, int targetY, int mapId, boolean useObjects) {
        lastResult = RESULT_NO_ROUTE;
        lastBlockType = WorldCollisionHash.MOVE_CLEAR;

        if (actor == null || actor.getMapId() != mapId) {
            return null;
        }

        final int sx = actor.getX();
        final int sy = actor.getY();

        if (sx == targetX && sy == targetY) {
            lastResult = RESULT_NONE;
            return null;
        }

        final L1Map map = L1WorldMap.getInstance().getMap((short) mapId);

        if (map == null) {
            return null;
        }

        final int targetDistance =
                chebyshev(sx, sy, targetX, targetY);

        int localTargetX = targetX;
        int localTargetY = targetY;
        int traceMode = TRACE_RAW;

        if (PathNodeMaps.contains(mapId)) {
            if (PathNodeWorld.moveStraight(map, sx, sy, targetX, targetY)) {
                traceMode = TRACE_DIRECT;
                clearCachedWaypoint(actor, mapId);

                if (DEBUG_FLOW) {
                    System.out.println(
                            "[NPC-PATH] mode=DIRECT"
                            + " actor=" + actor.getId()
                            + " map=" + mapId
                            + " from=" + sx + "," + sy
                            + " target=" + targetX + "," + targetY
                            + " distance=" + targetDistance);
                }
            } else {
                long waypoint = getCachedWaypoint(
                        actor, mapId,
                        sx, sy,
                        targetX, targetY);

                if (waypoint != PathNodeWorld.NO_PATH) {
                    traceMode = TRACE_CACHE;
                    if (DEBUG_FLOW) {
                        System.out.println(
                                "[NPC-PATH] mode=CACHE_HIT"
                                + " actor=" + actor.getId()
                                + " map=" + mapId
                                + " from=" + sx + "," + sy
                                + " target=" + targetX + "," + targetY
                                + " waypoint="
                                + PathNodeWorld.unpackX(waypoint) + ","
                                + PathNodeWorld.unpackY(waypoint));
                    }
                } else {
                    if (DEBUG_FLOW) {
                        System.out.println(
                                "[NPC-PATH] mode=ASTAR_REQ"
                                + " actor=" + actor.getId()
                                + " map=" + mapId
                                + " from=" + sx + "," + sy
                                + " target=" + targetX + "," + targetY
                                + " distance=" + targetDistance);
                    }

                    waypoint = PathNodeWorld.getInstance().pathFindBlocked(
                            mapId, map, sx, sy, targetX, targetY);

                    if (waypoint != PathNodeWorld.NO_PATH) {
                        traceMode = TRACE_ASTAR;
                        cacheWaypoint(actor, mapId, sx, sy, targetX, targetY, waypoint);

                        if (DEBUG_FLOW) {
                            System.out.println(
                                    "[NPC-PATH] mode=ASTAR_OK"
                                    + " actor=" + actor.getId()
                                    + " map=" + mapId
                                    + " from=" + sx + "," + sy
                                    + " target=" + targetX + "," + targetY
                                    + " waypoint="
                                    + PathNodeWorld.unpackX(waypoint) + ","
                                    + PathNodeWorld.unpackY(waypoint));
                        }
                    }
                }

                if (waypoint == PathNodeWorld.NO_PATH) {
                    clearCachedWaypoint(actor, mapId);
                    lastResult = RESULT_NO_ROUTE;

                    if (DEBUG_FLOW) {
                        System.out.println(
                                "[NPC-PATH] mode=ASTAR_FAIL"
                                + " actor=" + actor.getId()
                                + " map=" + mapId
                                + " from=" + sx + "," + sy
                                + " target=" + targetX + "," + targetY
                                + " distance=" + targetDistance);
                    }
                    return null;
                } else {
                    localTargetX = PathNodeWorld.unpackX(waypoint);
                    localTargetY = PathNodeWorld.unpackY(waypoint);
                }
            }
        }

        final int stepTargetX = localTargetX;
        final int stepTargetY = localTargetY;

        final int heading = projectedStraightHeading(
                map,
                sx, sy,
                stepTargetX, stepTargetY);

        if (heading < 0) {
            /*
             * For a cached PathFind segment, this now means either:
             *   - the actor left the exact raster segment unexpectedly, or
             *   - GEO changed after PathFind (for example a door state change).
             *
             * It no longer means "Bresenham was restarted with a new phase".
             */
            clearCachedWaypoint(actor, mapId);
            lastResult = RESULT_NO_ROUTE;

            if (DEBUG_FLOW) {
                System.out.println(
                        "[NPC-PATH] mode=" + traceModeName(traceMode)
                        + " result=STATIC_SEGMENT_BROKEN"
                        + " actor=" + actor.getId()
                        + " from=" + sx + "," + sy
                        + " target=" + targetX + "," + targetY
                        + " stepTarget=" + stepTargetX + "," + stepTargetY);
            }
            return null;
        }

        final int nx = sx + DX[heading];
        final int ny = sy + DY[heading];
        final int stepCost =
                (DX[heading] != 0 && DY[heading] != 0)
                        ? 14
                        : 10;

        source.reuse(sx, sy, 0, 0, null);
        next.reuse(nx, ny, stepCost, 0, source);

        if (DEBUG_FLOW) {
            System.out.println(
                    "[NPC-PATH] mode=" + traceModeName(traceMode)
                    + " result=STEP"
                    + " actor=" + actor.getId()
                    + " from=" + sx + "," + sy
                    + " to=" + nx + "," + ny
                    + " target=" + targetX + "," + targetY
                    + " stepTarget=" + stepTargetX + "," + stepTargetY
                    + " distance=" + targetDistance);
        }

        lastResult = RESULT_STEP;
        return next;
    }


    public int getXY(int heading, boolean xAxis) {
        return xAxis ? DX[heading] : DY[heading];
    }

    public int calcheading(int myX, int myY, int targetX, int targetY) {
        return headingFromDelta(
                Integer.compare(targetX, myX),
                Integer.compare(targetY, myY));
    }

    public void armInitialChaseAstarTransition(
            final L1Object actor) {

        if (!lastNativeAstarBuilt
                || actor == null
                || actor.getId() == 0) {
            return;
        }

        final int actorId = actor.getId();
        final int slot = routeSlot(actorId);

        if (initialChaseActor[slot] == actorId
                && initialChaseState[slot] == 0) {
            initialChaseState[slot] = 1;
        }
    }

    public boolean consumeInitialChaseAstarTransition(
            final L1Object actor) {

        if (actor == null || actor.getId() == 0) {
            return false;
        }

        final int actorId = actor.getId();
        final int slot = routeSlot(actorId);

        if (initialChaseActor[slot] != actorId
                || initialChaseState[slot] != 1
                || nativeRouteKey[slot] != routeKey(actorId, actor.getMapId())) {
            return false;
        }

        initialChaseState[slot] = 2;
        return true;
    }

    private void observeInitialChaseActor(
            final L1Object actor) {

        if (actor == null || actor.getId() == 0) {
            return;
        }

        final int actorId = actor.getId();
        final int slot = routeSlot(actorId);

        if (initialChaseActor[slot] != actorId) {
            initialChaseActor[slot] = actorId;
            initialChaseState[slot] = 0;
        }
    }

    public void invalidate(L1Object actor, int mapId) {
        if (actor == null || actor.getId() == 0) {
            return;
        }

        final int actorId = actor.getId();
        final int slot = routeSlot(actorId);
        final long key = routeKey(actorId, mapId);

        if (routeKey[slot] == key) {
            routeKey[slot] = 0L;
        }
        if (nativeRouteKey[slot] == key) {
            nativeRouteKey[slot] = 0L;
        }
    }

    // ------------------------------------------------------------------
    // Native MoveToTarget PathNode destination cache
    // ------------------------------------------------------------------

    private long getNativeWaypoint(
            final L1Object actor,
            final int mapId,
            final int sx,
            final int sy) {

        final int actorId = actor.getId();
        if (actorId == 0) {
            return PathNodeWorld.NO_PATH;
        }

        final int slot = routeSlot(actorId);
        final long key = routeKey(actorId, mapId);

        if (nativeRouteKey[slot] != key) {
            return PathNodeWorld.NO_PATH;
        }

        final long waypoint = nativeWaypoint[slot];
        final int wx = PathNodeWorld.unpackX(waypoint);
        final int wy = PathNodeWorld.unpackY(waypoint);

        /*
         * Native's reach threshold is sub-Geo-cell. On L1's integer grid this
         * collapses to the waypoint tile itself.
         */
        if (sx == wx && sy == wy) {
            nativeRouteKey[slot] = 0L;
            return PathNodeWorld.NO_PATH;
        }

        return waypoint;
    }

    private void cacheNativeWaypoint(
            final L1Object actor,
            final int mapId,
            final long waypoint) {

        final int actorId = actor.getId();
        if (actorId == 0) {
            return;
        }

        final int slot = routeSlot(actorId);

        nativeRouteKey[slot] = 0L;
        nativeWaypoint[slot] = waypoint;
        nativeRouteKey[slot] = routeKey(actorId, mapId);
    }

    private void clearNativeWaypoint(
            final L1Object actor,
            final int mapId) {

        final int actorId = actor.getId();
        if (actorId == 0) {
            return;
        }

        final int slot = routeSlot(actorId);

        if (nativeRouteKey[slot] == routeKey(actorId, mapId)) {
            nativeRouteKey[slot] = 0L;
        }
    }

    // ------------------------------------------------------------------
    // PathNode waypoint cache
    // ------------------------------------------------------------------

    private long getCachedWaypoint(
            L1Object actor,
            int mapId,
            int sx,
            int sy,
            int targetX,
            int targetY) {

        final int actorId = actor.getId();

        if (actorId == 0) {
            return PathNodeWorld.NO_PATH;
        }

        final int slot = routeSlot(actorId);
        final long key = routeKey(actorId, mapId);

        if (routeKey[slot] != key) {
            return PathNodeWorld.NO_PATH;
        }

        final long anchor = routeTargetAnchor[slot];

        if (chebyshev(targetX, targetY, PathNodeWorld.unpackX(anchor), PathNodeWorld.unpackY(anchor)) > ROUTE_TARGET_DRIFT) {
            if (DEBUG_FLOW) {
                System.out.println(
                        "[NPC-PATH] mode=CACHE_DROP reason=TARGET_DRIFT"
                        + " actor=" + actorId
                        + " map=" + mapId
                        + " from=" + sx + "," + sy
                        + " target=" + targetX + "," + targetY
                        + " anchor=" + PathNodeWorld.unpackX(anchor) + "," + PathNodeWorld.unpackY(anchor));
            }
            routeKey[slot] = 0L;
            return PathNodeWorld.NO_PATH;
        }

        final long last = routeLastPos[slot];
        final int lastX = PathNodeWorld.unpackX(last);
        final int lastY = PathNodeWorld.unpackY(last);

        if (chebyshev(sx, sy, lastX, lastY) > ROUTE_ACTOR_DRIFT) {
            if (DEBUG_FLOW) {
                System.out.println(
                        "[NPC-PATH] mode=CACHE_DROP reason=ACTOR_DRIFT"
                        + " actor=" + actorId
                        + " map=" + mapId
                        + " from=" + sx + "," + sy
                        + " last=" + lastX + "," + lastY
                        + " target=" + targetX + "," + targetY);
            }
            routeKey[slot] = 0L;
            return PathNodeWorld.NO_PATH;
        }

        final long waypoint = routeWaypoint[slot];
        final int wx = PathNodeWorld.unpackX(waypoint);
        final int wy = PathNodeWorld.unpackY(waypoint);

        if (sx == wx && sy == wy) {
            if (DEBUG_FLOW) {
                System.out.println(
                        "[NPC-PATH] mode=CACHE_DROP reason=WAYPOINT_REACHED"
                        + " actor=" + actorId
                        + " map=" + mapId
                        + " at=" + sx + "," + sy
                        + " target=" + targetX + "," + targetY);
            }
            routeKey[slot] = 0L;
            return PathNodeWorld.NO_PATH;
        }

        /*
         * C3 retail keeps PathFind's return-1 destination in the move action
         * and continues consuming that same destination through CalcMidDest.
         * It does NOT re-run a full current-position -> waypoint visibility
         * test after every movement tick.
         *
         * L1 snaps every move to an integer tile, so re-running Bresenham from
         * the snapped tile changes the raster phase.  At a wall tip that can
         * falsely invalidate an originally valid PathFind segment and make the
         * next A* choose the opposite first destination, producing a two-tile
         * ping-pong.  Keep the waypoint until it is reached, the target drifts,
         * or the actor actually jumps away.  projectedStraightHeading() still
         * validates every physical one-tile move before it is returned.
         */
        routeLastPos[slot] = PathNodeWorld.pack(sx, sy);
        return waypoint;
    }

    private void cacheWaypoint(L1Object actor, int mapId, int sx, int sy, int targetX, int targetY, long waypoint) {
        final int actorId = actor.getId();

        if (actorId == 0) {
            return;
        }

        final int slot = routeSlot(actorId);

        routeKey[slot] = 0L;
        routeTargetAnchor[slot] = PathNodeWorld.pack(targetX, targetY);
        routeWaypoint[slot] = waypoint;
        routeLastPos[slot] = PathNodeWorld.pack(sx, sy);
        routeKey[slot] = routeKey(actorId, mapId);
    }


    private void clearCachedWaypoint(L1Object actor, int mapId) {
        final int actorId = actor.getId();

        if (actorId == 0) {
            return;
        }

        final int slot = routeSlot(actorId);

        if (routeKey[slot] == routeKey(actorId, mapId)) {
            routeKey[slot] = 0L;
        }
    }

    /**
     * L1 projection of native SetMoving's continuous vector heading.
     *
     * Use only the nearest 8-way heading to the same PathFind/CalcMidDest
     * destination.  If that tile is not statically legal, use the exact first
     * MoveStraight3 cell crossing instead of inventing a side direction.
     */
    private static int projectedStraightHeading(
            final L1Map map,
            final int x,
            final int y,
            final int targetX,
            final int targetY) {

        final int projected = nearestVectorHeading8(
                x, y,
                targetX, targetY);

        if (projected >= 0) {
            final int nx = x + DX[projected];
            final int ny = y + DY[projected];

            if (map.isInMap(nx, ny)
                    && PathNodeWorld.moveStraight(
                            map,
                            x, y,
                            nx, ny)) {
                return projected;
            }
        }

        return PathNodeWorld.firstStraightHeading(
                map,
                x, y,
                targetX, targetY);
    }

    /**
     * Nearest 45-degree L1 heading to a continuous XY vector.
     * tan(22.5 deg) ~= 0.4142.  Integer comparisons avoid atan2() in the hot
     * path and keep the result stable for long shallow/steep segments.
     */
    private static int nearestVectorHeading8(
            final int x,
            final int y,
            final int targetX,
            final int targetY) {

        final int dx = targetX - x;
        final int dy = targetY - y;

        if ((dx | dy) == 0) {
            return -1;
        }

        final int sx = Integer.compare(dx, 0);
        final int sy = Integer.compare(dy, 0);
        final long ax = Math.abs((long) dx);
        final long ay = Math.abs((long) dy);

        if (ax == 0L) {
            return sy < 0 ? 0 : 4;
        }
        if (ay == 0L) {
            return sx > 0 ? 2 : 6;
        }

        /* Within 22.5 degrees of the vertical axis. */
        if (ax * 10000L <= ay * 4142L) {
            return sy < 0 ? 0 : 4;
        }

        /* Within 22.5 degrees of the horizontal axis. */
        if (ay * 10000L <= ax * 4142L) {
            return sx > 0 ? 2 : 6;
        }

        return headingFromDelta(sx, sy);
    }

    private static int headingFromDelta(
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

    private static String traceModeName(final int mode) {
        switch (mode) {
        case TRACE_DIRECT: return "DIRECT";
        case TRACE_CACHE:  return "CACHE";
        case TRACE_ASTAR:  return "ASTAR";
        default:           return "RAW";
        }
    }

    private static long routeKey(int actorId, int mapId) {
        return ((long) actorId << 32) ^ (mapId & 0xFFFFFFFFL);
    }

    private static int routeSlot(int actorId) {
        return actorId & ROUTE_CACHE_MASK;
    }

    private static int chebyshev(int x0, int y0, int x1, int y1) {
        return Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
    }


}