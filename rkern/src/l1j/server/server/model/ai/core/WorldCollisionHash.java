package l1j.server.server.model.ai.core;

import l1j.server.server.model.L1Object;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.TileOccupancy.TileOccupancy;
import l1j.server.server.model.character.TileOccupancy.TileOccupancyNpc;

/**
 * Runtime dynamic creature collision / occupancy facade.
 *
 * Static terrain belongs to L1Map / PathNodeWorld. This class owns only the
 * dynamic part of movement: player occupancy, NPC occupancy, NPC movement
 * reservations and occupancy commit/teleport operations.
 *
 * The moveCreatureProbe() path mirrors the responsibility of native
 * CWorldCollisionHash::MoveCreature: select the nearest body contact from the
 * swept step and return its collision-response direction. The response math is
 * the horizontal double-cross/tangent projection recovered from L2Server. L1's
 * exact tile-center collinear case uses only a deterministic pair-stable tangent
 * as the grid seam; there is no target scoring or random sidestep here.
 */
public final class WorldCollisionHash {

    public static final int MOVE_CLEAR = 0;
    public static final int MOVE_PC = 1;
    public static final int MOVE_NPC = 2;
    public static final int MOVE_RESERVED = 3;

    /* Kept for compatibility with older callers. */
    public static final int BLOCKED = 10000;

    /* Packed MoveCreature probe result. */
    private static final int PROBE_BLOCK_MASK = 0x0f;
    private static final int PROBE_RESPONSE_SHIFT = 4;
    private static final int PROBE_RESPONSE_MASK = 0x0f;
    private static final int PROBE_BLOCKER_SHIFT = 8;
    private static final long PROBE_CAN_MOVE_BIT = 1L << 40;
    private static final long PROBE_COLLIDED_BIT = 1L << 41;
    private static final long PROBE_DEGENERATE_BIT = 1L << 42;

    private static final int[] DX = { 0, 1, 1, 1, 0, -1, -1, -1 };
    private static final int[] DY = { -1, -1, 0, 1, 1, 1, 0, -1 };

    private static final WorldCollisionHash INSTANCE = new WorldCollisionHash();

    private final TileOccupancy pc = TileOccupancy.getInstance();
    private final TileOccupancyNpc npc = TileOccupancyNpc.getInstance();

    public static WorldCollisionHash getInstance() {
        return INSTANCE;
    }

    private WorldCollisionHash() {
    }

    /**
     * Tests the destination tile for one movement step.
     *
     * No path-search cost is produced here. Dynamic collision is a movement
     * constraint: a tile is either currently enterable or it is not. The real
     * reservation is still acquired by L1NpcInstance immediately before the
     * move is committed, so this method is side-effect free.
     */
    public int testStep(short mapId, int x, int y, int moverId) {
        try {
            final int occupier = pc.getOccupier(mapId, x, y);
            if (occupier != 0 && occupier != moverId) {
                return MOVE_PC;
            }
        } catch (Throwable ignore) {
            // Preserve the existing fail-open behavior of the old path code.
        }

        try {
            final int occupier = npc.getOccupier(mapId, x, y);
            if (occupier != 0 && occupier != moverId) {
                return MOVE_NPC;
            }
        } catch (Throwable ignore) {
        }

        try {
            final int reservedBy = npc.getReservedBy(mapId, x, y);
            if (reservedBy != 0 && reservedBy != moverId) {
                return MOVE_RESERVED;
            }
            final int pcReserved = pc.getReservedBy(mapId, x, y);
            if (pcReserved != 0 && pcReserved != moverId) {
                return MOVE_RESERVED;
            }
        } catch (Throwable ignore) {
        }

        return MOVE_CLEAR;
    }

    public boolean canEnter(short mapId, int x, int y, int moverId) {
        return testStep(mapId, x, y, moverId) == MOVE_CLEAR;
    }

    // ------------------------------------------------------------------
    // Native CWorldCollisionHash::MoveCreature body probe
    // ------------------------------------------------------------------

    /**
     * Allocation-free L1 projection of native MoveCreature's body scan.
     *
     * Native receives the continuous movement FVector before heading
     * quantization, scans collision-hash candidates, keeps the nearest contact,
     * and returns a collision-response FVector. L1's occupancy is whole-tile,
     * so the existing swept-step candidate set is retained here:
     * destination body plus the two orthogonal body cells for a diagonal step.
     *
     * vectorX/vectorY MUST be the pre-8-way CalcMidDest vector. Feeding the
     * quantized heading back here destroys the lateral component and is exactly
     * what makes head-on L1 contacts collapse into a permanent stop.
     *
     * Packed result:
     *   bits  0..3  : MOVE_* contact/block type
     *   bits  4..7  : response heading + 1 (0 means zero/no response)
     *   bits  8..39 : blocker object id (unsigned int payload)
     *   bit      40 : this movement heading can be committed
     *   bit      41 : a creature body was contacted
     *   bit      42 : L1 exact tile-center collinear seam was used
     */
    public long moveCreatureProbe(
            final short mapId,
            final int sx,
            final int sy,
            final int heading,
            final int vectorX,
            final int vectorY,
            final int moverId) {

        if (heading < 0 || heading > 7) {
            return packMoveProbe(
                    MOVE_CLEAR,
                    -1,
                    0,
                    false,
                    false,
                    false);
        }

        final int stepX = DX[heading];
        final int stepY = DY[heading];
        final int nx = sx + stepX;
        final int ny = sy + stepY;

        int bestType = MOVE_CLEAR;
        int bestId = 0;
        int bestX = 0;
        int bestY = 0;
        long bestProjection = Long.MAX_VALUE;
        long bestDistance2 = Long.MAX_VALUE;

        boolean destinationBodyBlocked = false;

        /*
         * Native MoveCreature works on continuous creature volumes, not only
         * destination-cell occupancy.  The old L1 projection checked lateral
         * bodies only for diagonal headings; after one sidestep a cardinal move
         * therefore forgot the blocker immediately and steered straight back
         * behind it.
         *
         * Sweep a radius-1 tile capsule around this one-tile segment.  This is
         * the allocation-free lattice analogue of the continuous body scan:
         * at most a 4x4 local cell window is inspected.
         */
        final int minX = Math.min(sx, nx) - 1;
        final int maxX = Math.max(sx, nx) + 1;
        final int minY = Math.min(sy, ny) - 1;
        final int maxY = Math.max(sy, ny) + 1;

        for (int by = minY; by <= maxY; by++) {
            for (int bx = minX; bx <= maxX; bx++) {
                if (bx == sx && by == sy) {
                    continue;
                }

                final long body =
                        bodyAt(
                                mapId,
                                bx,
                                by,
                                moverId);

                if (body == 0L
                        || !sweptBodyContact(
                                sx,
                                sy,
                                stepX,
                                stepY,
                                bx,
                                by)) {
                    continue;
                }

                if (bx == nx && by == ny) {
                    destinationBodyBlocked = true;
                }

                final long projection =
                        forwardProjection(
                                sx,
                                sy,
                                bx,
                                by,
                                vectorX,
                                vectorY);

                final long d2 =
                        distance2(
                                sx,
                                sy,
                                bx,
                                by);

                if (nearerContact(
                        projection,
                        d2,
                        bestProjection,
                        bestDistance2)) {

                    bestType = bodyType(body);
                    bestId = bodyId(body);
                    bestX = bx;
                    bestY = by;
                    bestProjection = projection;
                    bestDistance2 = d2;
                }
            }
        }

        int responseHeading = -1;
        boolean degenerateResponse = false;

        if (bestId != 0) {
            degenerateResponse =
                    isCollinearContact(
                            sx,
                            sy,
                            bestX,
                            bestY,
                            vectorX,
                            vectorY);

            responseHeading =
                    collisionResponseHeading(
                            sx,
                            sy,
                            bestX,
                            bestY,
                            vectorX,
                            vectorY,
                            moverId,
                            bestId);
        }

        /*
         * Reservation contention is a commit race, not a body response.  A
         * lateral body may coexist with a free destination (native success +
         * collision); only the destination body/reservation prevents the whole
         * L1 tile from being committed.
         */
        try {
            int reservedBy =
                    npc.getReservedBy(
                            mapId,
                            nx,
                            ny);
            if (reservedBy == 0) {
                reservedBy = pc.getReservedBy(mapId, nx, ny);
            }

            if (!destinationBodyBlocked
                    && reservedBy != 0
                    && reservedBy != moverId) {
                return packMoveProbe(
                        MOVE_RESERVED,
                        responseHeading,
                        bestId != 0 ? bestId : reservedBy,
                        false,
                        bestId != 0,
                        degenerateResponse);
            }
        } catch (Throwable ignore) {
        }

        if (bestId != 0) {
            return packMoveProbe(
                    bestType,
                    responseHeading,
                    bestId,
                    !destinationBodyBlocked,
                    true,
                    degenerateResponse);
        }

        return packMoveProbe(
                MOVE_CLEAR,
                -1,
                0,
                true,
                false,
                false);
    }

    /**
     * Radius-1 swept capsule test for one L1 tile commit.
     *
     * step is one of the 8 lattice directions. Contacts strictly behind the
     * start are ignored because movement is separating from them. A body that
     * is side-adjacent at t=0 remains a collision contact while we slide past
     * it, which is the piece the old destination/corner-only probe lost.
     */
    private static boolean sweptBodyContact(
            final int sx,
            final int sy,
            final int stepX,
            final int stepY,
            final int bx,
            final int by) {

        final long rx = (long) bx - sx;
        final long ry = (long) by - sy;

        final long len2 =
                (long) stepX * stepX
                        + (long) stepY * stepY;

        if (len2 == 0L) {
            return false;
        }

        final long dot =
                rx * stepX
                        + ry * stepY;

        if (dot < 0L) {
            return false;
        }

        if (dot >= len2) {
            final long ex = rx - stepX;
            final long ey = ry - stepY;
            return ex * ex + ey * ey <= 1L;
        }

        final long cross =
                rx * stepY
                        - ry * stepX;

        /*
         * perpendicularDistance^2 = cross^2 / len2 <= 1.
         */
        return cross * cross <= len2;
    }

    public static int moveProbeBlockType(final long probe) {
        return (int) probe & PROBE_BLOCK_MASK;
    }

    public static int moveProbeResponseHeading(final long probe) {
        final int code =
                ((int) (probe >>> PROBE_RESPONSE_SHIFT))
                        & PROBE_RESPONSE_MASK;

        return code == 0 ? -1 : code - 1;
    }

    public static int moveProbeBlockerId(final long probe) {
        return (int) (probe >>> PROBE_BLOCKER_SHIFT);
    }

    public static boolean moveProbeCanMove(final long probe) {
        return (probe & PROBE_CAN_MOVE_BIT) != 0L;
    }

    public static boolean moveProbeCollided(final long probe) {
        return (probe & PROBE_COLLIDED_BIT) != 0L;
    }

    public static boolean moveProbeDegenerate(final long probe) {
        return (probe & PROBE_DEGENERATE_BIT) != 0L;
    }

    private static boolean isCollinearContact(
            final int sx,
            final int sy,
            final int blockerX,
            final int blockerY,
            final int vectorX,
            final int vectorY) {

        final long rx = (long) blockerX - sx;
        final long ry = (long) blockerY - sy;

        return (rx | ry) != 0L
                && (vectorX | vectorY) != 0
                && rx * (long) vectorY
                        - ry * (long) vectorX == 0L;
    }

    /**
     * Native side/body response is a double-cross tangent projection.
     *
     *   R = blockerCenter - moverCurrent
     *   D = pre-quantized movement vector
     *   C = R x D
     *   response = C x R
     *            = D*(R.R) - R*(D.R)
     *
     * L1 resolves this in continuous coordinates. L1 collapses every body to a
     * tile center, so head-on contacts become exactly collinear far more often
     * and would otherwise produce a permanent zero response. For that single
     * degenerate L1 seam, choose one of the two equivalent tangents with a
     * stable mover/blocker pair bit. It is deterministic and allocation-free;
     * the same contact does not flip side every tick.
     */
    private static int collisionResponseHeading(
            final int sx,
            final int sy,
            final int blockerX,
            final int blockerY,
            final int vectorX,
            final int vectorY,
            final int moverId,
            final int blockerId) {

        final long rx = (long) blockerX - sx;
        final long ry = (long) blockerY - sy;

        if ((rx | ry) == 0L
                || (vectorX | vectorY) == 0) {
            return -1;
        }

        final long cross =
                rx * (long) vectorY
                        - ry * (long) vectorX;

        if (cross == 0L) {
            return collinearResponseHeading(
                    rx,
                    ry,
                    moverId,
                    blockerId);
        }

        final long responseX = -cross * ry;
        final long responseY = cross * rx;

        return nearestHeading8(
                responseX,
                responseY);
    }

    private static int collinearResponseHeading(
            final long rx,
            final long ry,
            final int moverId,
            final int blockerId) {

        long tangentX = -ry;
        long tangentY = rx;

        if ((tangentX | tangentY) == 0L) {
            return -1;
        }

        /*
         * Symmetric pair key: reversing mover/blocker keeps the same pair bit,
         * while R itself reverses, so two actors meeting head-on are naturally
         * projected to opposite physical sides instead of oscillating together.
         */
        if (((moverId ^ blockerId) & 1) != 0) {
            tangentX = -tangentX;
            tangentY = -tangentY;
        }

        return nearestHeading8(
                tangentX,
                tangentY);
    }

    private long bodyAt(
            final short mapId,
            final int x,
            final int y,
            final int moverId) {

        try {
            final int occupier =
                    pc.getOccupier(
                            mapId,
                            x,
                            y);

            if (occupier != 0
                    && occupier != moverId) {
                return packBody(
                        MOVE_PC,
                        occupier);
            }
        } catch (Throwable ignore) {
        }

        try {
            final int occupier =
                    npc.getOccupier(
                            mapId,
                            x,
                            y);

            if (occupier != 0
                    && occupier != moverId) {
                return packBody(
                        MOVE_NPC,
                        occupier);
            }
        } catch (Throwable ignore) {
        }

        return 0L;
    }

    private static long packBody(
            final int type,
            final int id) {

        return ((long) id << 32)
                | (type & 0xffffffffL);
    }

    private static int bodyType(final long body) {
        return (int) body;
    }

    private static int bodyId(final long body) {
        return (int) (body >>> 32);
    }

    private static long packMoveProbe(
            final int blockType,
            final int responseHeading,
            final int blockerId,
            final boolean canMove,
            final boolean collided,
            final boolean degenerate) {

        final int responseCode =
                responseHeading >= 0
                        && responseHeading <= 7
                        ? responseHeading + 1
                        : 0;

        long probe =
                (blockType & PROBE_BLOCK_MASK)
                        | ((long) responseCode
                                << PROBE_RESPONSE_SHIFT)
                        | ((blockerId & 0xffffffffL)
                                << PROBE_BLOCKER_SHIFT);

        if (canMove) {
            probe |= PROBE_CAN_MOVE_BIT;
        }
        if (collided) {
            probe |= PROBE_COLLIDED_BIT;
        }
        if (degenerate) {
            probe |= PROBE_DEGENERATE_BIT;
        }

        return probe;
    }

    private static long forwardProjection(
            final int sx,
            final int sy,
            final int bx,
            final int by,
            final int vectorX,
            final int vectorY) {

        final long dot =
                ((long) bx - sx) * vectorX
                        + ((long) by - sy) * vectorY;

        return dot > 0L
                ? dot
                : Long.MAX_VALUE - 1L;
    }

    private static long distance2(
            final int x0,
            final int y0,
            final int x1,
            final int y1) {

        final long dx = (long) x1 - x0;
        final long dy = (long) y1 - y0;

        return dx * dx + dy * dy;
    }

    private static boolean nearerContact(
            final long projection,
            final long distance2,
            final long bestProjection,
            final long bestDistance2) {

        return projection < bestProjection
                || (projection == bestProjection
                        && distance2 < bestDistance2);
    }

    private static int nearestHeading8(
            final long dx,
            final long dy) {

        if ((dx | dy) == 0L) {
            return -1;
        }

        final int sx = Long.compare(dx, 0L);
        final int sy = Long.compare(dy, 0L);
        final long ax = absNoOverflow(dx);
        final long ay = absNoOverflow(dy);

        if (ax == 0L) {
            return sy < 0 ? 0 : 4;
        }

        if (ay == 0L) {
            return sx > 0 ? 2 : 6;
        }

        /* tan(22.5deg) ~= 0.4142; divide first if multiplication may overflow. */
        if (ratioLe4142(ax, ay)) {
            return sy < 0 ? 0 : 4;
        }

        if (ratioLe4142(ay, ax)) {
            return sx > 0 ? 2 : 6;
        }

        if (sx > 0) {
            return sy < 0 ? 1 : 3;
        }

        return sy < 0 ? 7 : 5;
    }

    private static boolean ratioLe4142(
            final long numerator,
            final long denominator) {

        if (denominator == 0L) {
            return false;
        }

        if (numerator <= Long.MAX_VALUE / 10000L
                && denominator <= Long.MAX_VALUE / 4142L) {
            return numerator * 10000L
                    <= denominator * 4142L;
        }

        return (double) numerator / (double) denominator
                <= 0.4142d;
    }

    private static long absNoOverflow(final long value) {
        if (value == Long.MIN_VALUE) {
            return Long.MAX_VALUE;
        }
        return value < 0L ? -value : value;
    }

    /* PC movement reservation/commit facade. Keep legacy TileOccupancy semantics. */
    public boolean reservePcMove(int mapId, int x, int y, int actorId, long ttlMs) {
        return pc.canMoveTo(mapId, x, y, actorId, ttlMs);
    }

    public void cancelPcMove(int mapId, int x, int y, int actorId) {
        pc.cancelMove(mapId, x, y, actorId);
    }

    public boolean commitPcMove(
            int mapId,
            int fromX, int fromY,
            int toX, int toY,
            int actorId) {

        return pc.commitMove(mapId, fromX, fromY, toX, toY, actorId);
    }

    public boolean teleportPc(
            int oldMapId, int oldX, int oldY,
            int newMapId, int newX, int newY,
            int actorId) {

        return pc.teleportRelaxed(
                oldMapId, oldX, oldY,
                newMapId, newX, newY,
                actorId);
    }

    /** World registration collision hook. AOI/visibility does not belong here. */
    public void enterCreature(L1Object object) {
        if (object == null) {
            return;
        }

        if (object instanceof L1PcInstance) {
            final L1PcInstance actor = (L1PcInstance) object;
            pc.tryOccupyNow(
                    (short) actor.getMapId(),
                    actor.getX(),
                    actor.getY(),
                    actor.getId());
            return;
        }

        if (object instanceof L1NpcInstance) {
            final L1NpcInstance actor = (L1NpcInstance) object;
            if (actor.shouldUseNpcOccupancy()) {
                npc.tryOccupyNow(
                        (short) actor.getMapId(),
                        actor.getX(),
                        actor.getY(),
                        actor.getId());
            }
        }
    }

    /** World removal collision hook. AOI/visibility does not belong here. */
    public void leaveCreature(L1Object object) {
        if (object == null) {
            return;
        }

        if (object instanceof L1PcInstance) {
            final L1PcInstance actor = (L1PcInstance) object;
            pc.releaseNow(
                    (short) actor.getMapId(),
                    actor.getX(),
                    actor.getY(),
                    actor.getId());
            return;
        }

        if (object instanceof L1NpcInstance) {
            final L1NpcInstance actor = (L1NpcInstance) object;
            if (actor.shouldUseNpcOccupancy()) {
                npc.releaseNow(
                        (short) actor.getMapId(),
                        actor.getX(),
                        actor.getY(),
                        actor.getId());
            }
        }
    }

    /**
     * Compatibility path for any old caller that still expects a numeric cost.
     * New local movement should use testStep()/canEnter().
     */
    public int moveCost(short mapId, int x, int y,
                        int targetX, int targetY, int moverId) {
        return testStep(mapId, x, y, moverId) == MOVE_CLEAR ? 0 : BLOCKED;
    }

    /**
     * NPC/reservation obstruction along a segment, excluding the final tile.
     * This remains useful for LOS/chase checks in L1NpcInstance.
     */
    public boolean isNpcPathBlocked(short mapId, int selfId,
                                    int x0, int y0, int x1, int y1) {
        final int dx = Math.abs(x1 - x0);
        final int dy = Math.abs(y1 - y0);
        final int sx = x0 < x1 ? 1 : -1;
        final int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;

        while (x0 != x1 || y0 != y1) {
            final int e2 = err << 1;
            if (e2 > -dy) {
                err -= dy;
                x0 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y0 += sy;
            }

            if (x0 == x1 && y0 == y1) {
                break;
            }

            final int occupier = npc.getOccupier(mapId, x0, y0);
            if (occupier != 0 && occupier != selfId) {
                return true;
            }

            final int reservedBy = npc.getReservedBy(mapId, x0, y0);
            if (reservedBy != 0 && reservedBy != selfId) {
                return true;
            }
        }

        return false;
    }

    public void occupyNpcNow(short mapId, int x, int y, int actorId) {
        npc.tryOccupyNow(mapId, x, y, actorId);
    }

    public void releaseNpcNow(short mapId, int x, int y, int actorId) {
        npc.releaseNow(mapId, x, y, actorId);
    }

    public boolean reserveNpcMove(short mapId, int x, int y, int actorId, long ttlMs) {
        return npc.canMoveTo(mapId, x, y, actorId, ttlMs);
    }

    public void cancelNpcMove(short mapId, int x, int y, int actorId) {
        npc.cancelMove(mapId, x, y, actorId);
    }

    public boolean commitNpcMove(short mapId,
                                 int fromX, int fromY,
                                 int toX, int toY,
                                 int actorId) {
        return npc.commitMove(mapId, fromX, fromY, toX, toY, actorId);
    }

    public boolean teleportNpc(short oldMapId, int oldX, int oldY,
                               short newMapId, int newX, int newY,
                               int actorId) {
        return npc.teleportRelaxed(
                oldMapId, oldX, oldY,
                newMapId, newX, newY,
                actorId);
    }

    public int getPcOccupier(short mapId, int x, int y) {
        return pc.getOccupier(mapId, x, y);
    }

    public int getNpcOccupier(short mapId, int x, int y) {
        return npc.getOccupier(mapId, x, y);
    }

    public int getNpcReservedBy(short mapId, int x, int y) {
        return npc.getReservedBy(mapId, x, y);
    }
}
