package l1j.server.server.model.ai.core;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1WorldMap;

/**
 *   런타임 path graph.
 */
public final class PathNodeWorld {
    private static final Logger LOG = Logger.getLogger(PathNodeWorld.class.getName());
    private static final PathNodeWorld INSTANCE = new PathNodeWorld();

    private static final boolean DEBUG_PATH_FAIL = false;
    private static final boolean DEBUG_PATH_TRACE = false;

    private static final byte FAIL_NONE       = 0;
    private static final byte FAIL_LIMIT      = 1;
    private static final byte FAIL_OPEN_EMPTY = 2;

    private static final int MAGIC = 0x4E504E31; // NPN1
    private static final int VERSION = 2;

    private static final int CANDIDATE_SECTORS = 16;

    private static final int NATIVE_CANDIDATE_SECTION_RADIUS = 1;

    private static final double NATIVE_INV_PI = 0.3183099524062959;

    private static final double GEO_WORLD = 16.0;
    private static final double GEO_CENTER = 8.0;

    private static final int GEO_E = 0;
    private static final int GEO_W = 1;
    private static final int GEO_S = 2;
    private static final int GEO_N = 3;
    private static final int[] GEO_DX = { 1, -1, 0, 0 };
    private static final int[] GEO_DY = { 0, 0, 1, -1 };
    private static final int[] GEO_TO_L1_HEADING = { 2, 6, 4, 0 };

    public static final int SEARCH_LIMIT_MOVE_TO_TARGET = 0x190;
    public static final int SEARCH_LIMIT_LONG = 0x3E8;

    private static final File CACHE_DIR = new File("./data/pathnode-l1");

    public static final long NO_PATH = Long.MIN_VALUE;

    private final ConcurrentHashMap<Integer, PathGraph> graphs = new ConcurrentHashMap<>();
    private final ThreadLocal<SearchWorkspace> workspaces = ThreadLocal.withInitial(SearchWorkspace::new);

    public static PathNodeWorld getInstance() {
        return INSTANCE;
    }

    private PathNodeWorld() {
    }

    /** Load()처럼 이미 컴파일된 graph만 메모리에 올린다. */
    public void preload(int mapId) {
        final PathGraph g = load(mapId);
        if (g != null) {
            graphs.put(mapId, g);
        }
    }

    /** PathMaker 단계. 서버 기동 전/관리 단계에서 한 번 실행하고 cache를 만든다. */
    public void rebuild(int mapId) {
        final L1Map map = L1WorldMap.getInstance().getMap((short) mapId);
        if (map == null || map.isNull()) return;
        final PathGraph graph = PathMaker.build(map);
        graphs.put(mapId, graph);
        save(graph);
    }

    public long pathFind(int mapId, int fromX, int fromY, int toX, int toY) {
        return pathFind(
                mapId,
                fromX, fromY,
                toX, toY,
                SEARCH_LIMIT_MOVE_TO_TARGET);
    }

    public long pathFind(
            int mapId,
            int fromX,
            int fromY,
            int toX,
            int toY,
            int maxExpansions) {

        final L1Map map = L1WorldMap.getInstance().getMap((short) mapId);
        if (map == null || map.isNull()) return NO_PATH;

        if (moveStraight(map, fromX, fromY, toX, toY)) {
            if (DEBUG_PATH_TRACE) {
                System.out.println(
                        "[PNODE-DIRECT] map=" + mapId
                        + " from=" + fromX + "," + fromY
                        + " target=" + toX + "," + toY);
            }
            return pack(toX, toY);
        }

        return pathFindBlocked(
                mapId, map,
                fromX, fromY,
                toX, toY,
                maxExpansions);
    }

    public long pathFindBlocked(
            int mapId,
            L1Map map,
            int fromX,
            int fromY,
            int toX,
            int toY) {

        return pathFindBlocked(
                mapId, map,
                fromX, fromY,
                toX, toY,
                SEARCH_LIMIT_MOVE_TO_TARGET);
    }

    public long pathFindBlocked(
            int mapId,
            L1Map map,
            int fromX,
            int fromY,
            int toX,
            int toY,
            int maxExpansions) {

        if (map == null || map.isNull()) return NO_PATH;

        final PathGraph graph = graph(mapId);
        if (graph == null || graph.nodeCount() == 0) {
            return NO_PATH;
        }

        final SearchWorkspace ws = workspaces.get();
        ws.begin(graph.nodeCount());

        if (DEBUG_PATH_TRACE) {
            System.out.println(
                    "[PNODE-ASTAR-REQ] map=" + mapId
                    + " from=" + fromX + "," + fromY
                    + " target=" + toX + "," + toY
                    + " limit=" + maxExpansions);
        }

        final int startCount = collectDirectionalCandidates(
                graph,
                map,
                fromX,
                fromY,
                true,
                NATIVE_CANDIDATE_SECTION_RADIUS,
                ws.startCandidate,
                ws.startCandidateDist);

        if (startCount == 0) {
            debugCandidateProbe(
                    "START",
                    graph, map,
                    fromX, fromY,
                    mapId,
                    ws);
            debugPathFail(
                    "START_ZERO",
                    mapId,
                    fromX, fromY,
                    toX, toY,
                    0, -1,
                    0,
                    maxExpansions);
            return NO_PATH;
        }

        final int goalCount = collectDirectionalCandidates(
                graph,
                map,
                toX,
                toY,
                false,
                NATIVE_CANDIDATE_SECTION_RADIUS,
                ws.goalCandidate,
                ws.goalCandidateDist);

        if (goalCount == 0) {
            debugCandidateProbe(
                    "GOAL",
                    graph, map,
                    toX, toY,
                    mapId,
                    ws);
            debugPathFail(
                    "GOAL_ZERO",
                    mapId,
                    fromX, fromY,
                    toX, toY,
                    startCount, 0,
                    0,
                    maxExpansions);
            return NO_PATH;
        }

        final int reached = findGraphPath(
                graph,
                map,
                fromX,
                fromY,
                toX,
                toY,
                startCount,
                goalCount,
                maxExpansions,
                ws);

        if (reached < 0) {
            final String reason =
                    ws.failReason == FAIL_LIMIT
                            ? "LIMIT_" + maxExpansions
                            : "OPEN_EMPTY";

            debugPathFail(
                    reason,
                    mapId,
                    fromX, fromY,
                    toX, toY,
                    startCount, goalCount,
                    ws.expandedCount,
                    maxExpansions);
            return NO_PATH;
        }

        final int first = firstDestination(ws, reached);
        if (first < 0) {
            if (DEBUG_PATH_TRACE) {
                System.out.println(
                        "[PNODE-ASTAR-FAIL] reason=NO_FIRST_DEST"
                        + " map=" + mapId
                        + " from=" + fromX + "," + fromY
                        + " target=" + toX + "," + toY
                        + " startCount=" + startCount
                        + " goalCount=" + goalCount
                        + " expanded=" + ws.expandedCount);
            }
            return NO_PATH;
        }

        if (DEBUG_PATH_TRACE) {
            System.out.println(
                    "[PNODE-ASTAR-OK] map=" + mapId
                    + " from=" + fromX + "," + fromY
                    + " target=" + toX + "," + toY
                    + " startCount=" + startCount
                    + " goalCount=" + goalCount
                    + " expanded=" + ws.expandedCount
                    + " reachedNode=" + reached
                    + " firstNode=" + first
                    + " waypoint=" + graph.nodeX[first] + "," + graph.nodeY[first]);
        }

        return pack(graph.nodeX[first], graph.nodeY[first]);
    }

    private static void debugCandidateProbe(
            final String role,
            final PathGraph g,
            final L1Map map,
            final int x,
            final int y,
            final int mapId,
            final SearchWorkspace ws) {

        if (!DEBUG_PATH_TRACE) {
            return;
        }

        int nearbyNodes = 0;
        int samePos = 0;
        int nearest = -1;
        int nearestDist2 = Integer.MAX_VALUE;

        final int sx0 = (x >> 4) - g.sectionBaseX;
        final int sy0 = (y >> 4) - g.sectionBaseY;
        final int minX = Math.max(0, sx0 - NATIVE_CANDIDATE_SECTION_RADIUS);
        final int maxX = Math.min(g.sectionWidth - 1, sx0 + NATIVE_CANDIDATE_SECTION_RADIUS);
        final int minY = Math.max(0, sy0 - NATIVE_CANDIDATE_SECTION_RADIUS);
        final int maxY = Math.min(g.sectionHeight - 1, sy0 + NATIVE_CANDIDATE_SECTION_RADIUS);

        for (int sx = minX; sx <= maxX; sx++) {
            for (int sy = minY; sy <= maxY; sy++) {
                int node = g.sectionHead[sx * g.sectionHeight + sy];
                while (node >= 0) {
                    nearbyNodes++;
                    final int dx = g.nodeX[node] - x;
                    final int dy = g.nodeY[node] - y;
                    final int dist2 = dx * dx + dy * dy;
                    if (dist2 == 0) {
                        samePos++;
                    } else if (dist2 < nearestDist2) {
                        nearestDist2 = dist2;
                        nearest = node;
                    }
                    node = g.nodeNextInSection[node];
                }
            }
        }

        final int p2n = collectDirectionalCandidates(
                g, map,
                x, y,
                true,
                NATIVE_CANDIDATE_SECTION_RADIUS,
                ws.diagCandidate,
                ws.diagCandidateDist);

        final int n2p = collectDirectionalCandidates(
                g, map,
                x, y,
                false,
                NATIVE_CANDIDATE_SECTION_RADIUS,
                ws.diagCandidate,
                ws.diagCandidateDist);

        boolean nearestP2N = false;
        boolean nearestN2P = false;
        int nearestX = 0;
        int nearestY = 0;

        if (nearest >= 0) {
            nearestX = g.nodeX[nearest];
            nearestY = g.nodeY[nearest];
            nearestP2N = moveStraight(map, x, y, nearestX, nearestY);
            nearestN2P = moveStraight(map, nearestX, nearestY, x, y);
        }

        System.out.println(
                "[PNODE-CAND] role=" + role
                + " map=" + mapId
                + " point=" + x + "," + y
                + " section=" + sx0 + "," + sy0
                + " nearbyNodes=" + nearbyNodes
                + " samePos=" + samePos
                + " p2nCount=" + p2n
                + " n2pCount=" + n2p
                + " nearestNode=" + nearest
                + " nearestPos=" + nearestX + "," + nearestY
                + " nearestDist2=" + (nearest >= 0 ? nearestDist2 : -1)
                + " nearestP2N=" + nearestP2N
                + " nearestN2P=" + nearestN2P);
    }

    private static void debugPathFail(
            final String reason,
            final int mapId,
            final int fromX,
            final int fromY,
            final int toX,
            final int toY,
            final int startCount,
            final int goalCount,
            final int expanded,
            final int limit) {

        if (!DEBUG_PATH_FAIL) {
            return;
        }

        System.out.println(
                "[PNODE-FAIL] reason=" + reason
                + " map=" + mapId
                + " from=" + fromX + "," + fromY
                + " target=" + toX + "," + toY
                + " startCount=" + startCount
                + " goalCount=" + goalCount
                + " expanded=" + expanded
                + " limit=" + limit);
    }

    private static int collectDirectionalCandidates(
            PathGraph g,
            L1Map map,
            int x,
            int y,
            boolean pointToNode,
            int sectionRadius,
            int[] candidates,
            int[] bestDist) {

        for (int i = 0; i < CANDIDATE_SECTORS; i++) {
            candidates[i] = -1;
            bestDist[i] = Integer.MAX_VALUE;
        }

        final int sx0 = (x >> 4) - g.sectionBaseX;
        final int sy0 = (y >> 4) - g.sectionBaseY;
        final int minX = Math.max(0, sx0 - sectionRadius);
        final int maxX = Math.min(g.sectionWidth - 1, sx0 + sectionRadius);
        final int minY = Math.max(0, sy0 - sectionRadius);
        final int maxY = Math.min(g.sectionHeight - 1, sy0 + sectionRadius);

        for (int sx = minX; sx <= maxX; sx++) {
            for (int sy = minY; sy <= maxY; sy++) {
                int node = g.sectionHead[sx * g.sectionHeight + sy];

                while (node >= 0) {
                    final int nx = g.nodeX[node];
                    final int ny = g.nodeY[node];
                    final int dx = nx - x;
                    final int dy = ny - y;
                    final int dist2 = dx * dx + dy * dy;

                    if (dist2 != 0) {
                        final int sector = nativeSector16(dx, dy);

                        if (dist2 < bestDist[sector]) {
                            final boolean visible = pointToNode
                                    ? moveStraight(map, x, y, nx, ny)
                                    : moveStraight(map, nx, ny, x, y);

                            if (visible) {
                                candidates[sector] = node;
                                bestDist[sector] = dist2;
                            }
                        }
                    }

                    node = g.nodeNextInSection[node];
                }
            }
        }

        int count = 0;
        for (int i = 0; i < CANDIDATE_SECTORS; i++) {
            final int node = candidates[i];
            if (node >= 0) {
                candidates[count++] = node;
            }
        }
        for (int i = count; i < CANDIDATE_SECTORS; i++) {
            candidates[i] = -1;
        }
        return count;
    }

    private static int nativeSector16(int dx, int dy) {
        final int q = (int) (Math.atan2((double) dy, (double) dx)
                * NATIVE_INV_PI
                * -8.0);
        final int sector = 8 - q;
        if (sector < 0) return 0;
        if (sector > 15) return 15;
        return sector;
    }

    private PathGraph graph(int mapId) {
    	PathGraph g = graphs.get(mapId);
        if (g != null) return g;

        synchronized (graphs) {
            g = graphs.get(mapId);
            if (g != null) return g;

            g = load(mapId);
            if (g != null) {
                graphs.put(mapId, g);
            }
            return g;
        }
    }

    private static int findGraphPath(
            PathGraph g,
            L1Map map,
            int fromX,
            int fromY,
            int toX,
            int toY,
            int startCount,
            int goalCount,
            int maxExpansions,
            SearchWorkspace ws) {

        for (int i = 0; i < goalCount; i++) {
            ws.goalMark[ws.goalCandidate[i]] = ws.searchId;
        }

        for (int i = 0; i < startCount; i++) {
            final int start = ws.startCandidate[i];
            final double initialG = distanceCost(
                    fromX, fromY,
                    g.nodeX[start], g.nodeY[start]);

            if (!ws.visit(start, initialG, true)) {
                continue;
            }

            ws.pushRecord(
                    start,
                    start,
                    1,
                    initialG,
                    distanceCost(
                            g.nodeX[start], g.nodeY[start],
                            toX, toY));
        }

        int expanded = 0;
        ws.failReason = FAIL_NONE;
        ws.expandedCount = 0;

        while (ws.openSize > 0) {
        	
            if (expanded++ > maxExpansions) {
                ws.failReason = FAIL_LIMIT;
                ws.expandedCount = expanded - 1;
                return -1;
            }

            final int current = ws.popRecord();
            final int currentFirst = ws.popFirst;
            final int currentDepth = ws.popDepth;
            final double currentG = ws.popG;

            if (ws.goalMark[current] == ws.searchId) {
                ws.reachedNode = current;
                ws.resultFirst = currentFirst;
                ws.expandedCount = expanded;
                return current;
            }

            final int begin = g.edgeStart[current];
            final int count = g.edgeCount[current] & 0xFF;

            for (int i = 0; i < count; i++) {
                final int next = g.edgeTarget[begin + i];

                if (currentDepth == 1) {
                    if (g.nodeX[next] != fromX
                            || g.nodeY[next] != fromY) {

                        final double directG = distanceCost(
                                fromX, fromY,
                                g.nodeX[next], g.nodeY[next]);

                        if (!ws.visit(next, directG, false)) {
                            continue;
                        }

                        if (directG > 0.1
                                && moveStraight(
                                        map,
                                        fromX,
                                        fromY,
                                        g.nodeX[next],
                                        g.nodeY[next])) {

                            if (ws.visit(next, directG, true)) {
                                ws.pushRecord(
                                        next,
                                        next,
                                        1,
                                        directG,
                                        distanceCost(
                                                g.nodeX[next], g.nodeY[next],
                                                toX, toY));
                            }
                            continue;
                        }
                    }
                }

                final double tentative = currentG
                        + distanceCost(
                                g.nodeX[current], g.nodeY[current],
                                g.nodeX[next], g.nodeY[next]);

                if (!ws.visit(next, tentative, true)) {
                    continue;
                }

                ws.pushRecord(
                        next,
                        currentFirst,
                        currentDepth + 1,
                        tentative,
                        distanceCost(
                                g.nodeX[next], g.nodeY[next],
                                toX, toY));
            }
        }

        ws.failReason = FAIL_OPEN_EMPTY;
        ws.expandedCount = expanded;
        return -1;
    }

    private static int firstDestination(
            SearchWorkspace ws,
            int reached) {

        return reached < 0 ? -1 : ws.resultFirst;
    }

    private static double distanceCost(int x0, int y0, int x1, int y1) {
        final double dx = (double) x1 - x0;
        final double dy = (double) y1 - y0;
        return Math.sqrt(dx * dx + dy * dy);
    }

    public static boolean isStepPassableStrict(
            final L1Map map,
            final int x,
            final int y,
            final int heading) {

        return map != null
                && (heading & ~7) == 0
                && map.isMovePassableGeoNpc(
                        x,
                        y,
                        heading);
    }

    public static int firstStraightHeading(
            final L1Map map,
            final int x0,
            final int y0,
            final int x1,
            final int y1) {

        if (map == null || (x0 == x1 && y0 == y1)) {
            return -1;
        }

        final double startX = x0 * GEO_WORLD + GEO_CENTER;
        final double startY = y0 * GEO_WORLD + GEO_CENTER;
        final double endX = x1 * GEO_WORLD + GEO_CENTER;
        final double endY = y1 * GEO_WORLD + GEO_CENTER;

        final double vx = endX - startX;
        final double vy = endY - startY;
        final double dist2 = vx * vx + vy * vy;
        if (dist2 < 0.000001) {
            return -1;
        }

        final double dist = Math.sqrt(dist2);
        final double step = Math.min(GEO_WORLD, dist);
        final double scale = step / dist;

        double px = startX + vx * scale;
        double py = startY + vy * scale;

        int nx = floorGeoCell(px);
        int ny = floorGeoCell(py);

        if (step >= dist) {
            nx = x1;
            ny = y1;
        }

        final int ddx = nx - x0;
        final int ddy = ny - y0;
        if ((ddx == 0 && ddy == 0)
                || Math.abs(ddx) > 1
                || Math.abs(ddy) > 1
                || !geoTransitionPassable(map, x0, y0, nx, ny)) {
            return -1;
        }

        return heading(ddx, ddy);
    }

    public static boolean moveStraight(
            final L1Map map,
            final int x0,
            final int y0,
            final int x1,
            final int y1) {

        if (map == null) {
            return false;
        }
        if (x0 == x1 && y0 == y1) {
            return true;
        }

        final double startX = x0 * GEO_WORLD + GEO_CENTER;
        final double startY = y0 * GEO_WORLD + GEO_CENTER;
        final double endX = x1 * GEO_WORLD + GEO_CENTER;
        final double endY = y1 * GEO_WORLD + GEO_CENTER;

        final double vx = endX - startX;
        final double vy = endY - startY;
        final double dist2 = vx * vx + vy * vy;
        if (dist2 < 0.000001) {
            return true;
        }

        final double dist = Math.sqrt(dist2);
        final double ux = vx / dist;
        final double uy = vy / dist;
        final double stepX = ux * GEO_WORLD;
        final double stepY = uy * GEO_WORLD;

        double px = startX;
        double py = startY;
        double travelled = 0.0;

        int cx = x0;
        int cy = y0;

        final int limit = (int) Math.ceil(dist / GEO_WORLD) + 4;
        for (int i = 0; i < limit && (cx != x1 || cy != y1); i++) {
            final double remain = dist - travelled;
            if (remain <= GEO_WORLD) {
                px = endX;
                py = endY;
                travelled = dist;
            } else {
                px += stepX;
                py += stepY;
                travelled += GEO_WORLD;
            }

            int nx = floorGeoCell(px);
            int ny = floorGeoCell(py);

            if (travelled >= dist) {
                nx = x1;
                ny = y1;
            }

            final int ddx = nx - cx;
            final int ddy = ny - cy;

            if (ddx == 0 && ddy == 0) {
                continue;
            }
            if (Math.abs(ddx) > 1 || Math.abs(ddy) > 1) {
                return false;
            }
            if (!geoTransitionPassable(map, cx, cy, nx, ny)) {
                return false;
            }

            cx = nx;
            cy = ny;
        }

        return cx == x1 && cy == y1;
    }

    private static boolean geoTransitionPassable(
            final L1Map map,
            final int x,
            final int y,
            final int nx,
            final int ny) {

        if (!map.isInMap(x, y) || !map.isInMap(nx, ny)) {
            return false;
        }

        final int ddx = nx - x;
        final int ddy = ny - y;

        if (ddx == 0) {
            return ddy != 0
                    && canGeoCardinal(map, x, y, ddy > 0 ? GEO_S : GEO_N);
        }
        if (ddy == 0) {
            return canGeoCardinal(map, x, y, ddx > 0 ? GEO_E : GEO_W);
        }

        final int xDir = ddx > 0 ? GEO_E : GEO_W;
        final int yDir = ddy > 0 ? GEO_S : GEO_N;

        /* X -> Y must work. */
        if (!canGeoCardinal(map, x, y, xDir)) {
            return false;
        }
        final int xMid = x + GEO_DX[xDir];
        final int yMid = y + GEO_DY[xDir];
        if (!canGeoCardinal(map, xMid, yMid, yDir)) {
            return false;
        }

        /* Y -> X must ALSO work. */
        if (!canGeoCardinal(map, x, y, yDir)) {
            return false;
        }
        final int xMid2 = x + GEO_DX[yDir];
        final int yMid2 = y + GEO_DY[yDir];
        return canGeoCardinal(map, xMid2, yMid2, xDir);
    }

    private static boolean canGeoCardinal(
            final L1Map map,
            final int x,
            final int y,
            final int dir) {

        final int nx = x + GEO_DX[dir];
        final int ny = y + GEO_DY[dir];
        return map.isInMap(x, y)
                && map.isInMap(nx, ny)
                && map.isMovePassableGeoNpc(x, y, GEO_TO_L1_HEADING[dir]);
    }

    private static int floorGeoCell(final double world) {
        return (int) Math.floor(world / GEO_WORLD);
    }

    private static int heading(int dx, int dy) {
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

    public static long pack(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }

    public static int unpackX(long packed) {
        return (int) (packed >> 32);
    }

    public static int unpackY(long packed) {
        return (int) packed;
    }

    private static File fileFor(int mapId) {
        return new File(CACHE_DIR, mapId + ".pnode");
    }

    private static void save(PathGraph g) {
        try {
            if (!CACHE_DIR.exists() && !CACHE_DIR.mkdirs()) return;
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(fileFor(g.mapId))))) {
                out.writeInt(MAGIC);
                out.writeInt(VERSION);
                out.writeInt(g.mapId);
                out.writeInt(g.baseX);
                out.writeInt(g.baseY);
                out.writeInt(g.width);
                out.writeInt(g.height);
                out.writeInt(g.nodeX.length);
                out.writeInt(g.edgeTarget.length);
                for (int i = 0; i < g.nodeX.length; i++) {
                    out.writeInt(g.nodeX[i]);
                    out.writeInt(g.nodeY[i]);
                    out.writeInt(g.edgeStart[i]);
                    out.writeByte(g.edgeCount[i]);
                }
                for (int v : g.edgeTarget) out.writeInt(v);
            }
        } catch (Throwable t) {
            LOG.log(Level.WARNING, "[NpcPathNodeWorld] pathnode save failed map=" + g.mapId, t);
        }
    }

    private static PathGraph load(int mapId) {
        final File file = fileFor(mapId);
        if (!file.isFile()) return null;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            if (in.readInt() != MAGIC || in.readInt() != VERSION) return null;
            final int storedMapId = in.readInt();
            if (storedMapId != mapId) return null;
            final int baseX = in.readInt();
            final int baseY = in.readInt();
            final int width = in.readInt();
            final int height = in.readInt();
            final int nodes = in.readInt();
            final int edges = in.readInt();
            if (nodes < 0 || edges < 0) return null;

            final int[] xs = new int[nodes];
            final int[] ys = new int[nodes];
            final int[] edgeStart = new int[nodes];
            final byte[] edgeCount = new byte[nodes];
            for (int i = 0; i < nodes; i++) {
                xs[i] = in.readInt();
                ys[i] = in.readInt();
                edgeStart[i] = in.readInt();
                edgeCount[i] = in.readByte();
            }
            final int[] edgeTarget = new int[edges];
            for (int i = 0; i < edges; i++) edgeTarget[i] = in.readInt();
            return new PathGraph(mapId, baseX, baseY, width, height,
                    xs, ys, edgeStart, edgeCount, edgeTarget);
        } catch (IOException e) {
            return null;
        }
    }

    private static final class SearchWorkspace {
        int searchId;
        int openSize;
        byte failReason;
        int expandedCount;

        int[] seen = new int[0];
        double[] bestG = new double[0];
        int[] goalMark = new int[0];

        int[] openNode = new int[0];
        int[] openFirst = new int[0];
        int[] openDepth = new int[0];
        double[] openG = new double[0];
        double[] openH = new double[0];

        int popFirst;
        int popDepth;
        double popG;
        double popH;

        int reachedNode;
        int resultFirst;

        final int[] startCandidate = new int[CANDIDATE_SECTORS];
        final int[] startCandidateDist = new int[CANDIDATE_SECTORS];
        final int[] goalCandidate = new int[CANDIDATE_SECTORS];
        final int[] goalCandidateDist = new int[CANDIDATE_SECTORS];
        final int[] diagCandidate = new int[CANDIDATE_SECTORS];
        final int[] diagCandidateDist = new int[CANDIDATE_SECTORS];

        void begin(int nodes) {
            ensureNodes(nodes);
            openSize = 0;
            failReason = FAIL_NONE;
            expandedCount = 0;
            reachedNode = -1;
            resultFirst = -1;

            if (++searchId == 0) {
                Arrays.fill(seen, 0);
                Arrays.fill(goalMark, 0);
                searchId = 1;
            }
        }

        boolean visit(final int node, final double newG, final boolean update) {
            if (seen[node] != searchId) {
                if (update) {
                    seen[node] = searchId;
                    bestG[node] = newG;
                }
                return true;
            }

            if (!(newG < bestG[node])) {
                return false;
            }

            if (update) {
                bestG[node] = newG;
            }
            return true;
        }

        void pushRecord(
                final int node,
                final int first,
                final int depth,
                final double g,
                final double h) {

            ensureOpen(openSize + 1);

            int hole = openSize++;
            final double f = g + h;

            while (hole > 0) {
                final int parent = (hole - 1) >>> 1;
                if (recordF(parent) <= f) {
                    break;
                }
                copyRecord(parent, hole);
                hole = parent;
            }

            setRecord(hole, node, first, depth, g, h);
        }

        int popRecord() {
            final int node = openNode[0];
            popFirst = openFirst[0];
            popDepth = openDepth[0];
            popG = openG[0];
            popH = openH[0];

            final int newSize = --openSize;
            if (newSize == 0) {
                return node;
            }

            final int valueNode = openNode[newSize];
            final int valueFirst = openFirst[newSize];
            final int valueDepth = openDepth[newSize];
            final double valueG = openG[newSize];
            final double valueH = openH[newSize];
            final double valueF = valueG + valueH;

            int hole = 0;
            int secondChild = 2;

            while (secondChild < newSize) {
                final int right = secondChild;
                final int left = right - 1;
                final int child = recordF(left) < recordF(right)
                        ? left
                        : right;

                copyRecord(child, hole);
                hole = child;
                secondChild = (hole << 1) + 2;
            }

            if (secondChild == newSize) {
                final int left = secondChild - 1;
                copyRecord(left, hole);
                hole = left;
            }

            /* push_heap(value) into the hole range. */
            while (hole > 0) {
                final int parent = (hole - 1) >>> 1;
                if (recordF(parent) <= valueF) {
                    break;
                }
                copyRecord(parent, hole);
                hole = parent;
            }

            setRecord(
                    hole,
                    valueNode,
                    valueFirst,
                    valueDepth,
                    valueG,
                    valueH);

            return node;
        }

        private double recordF(final int index) {
            return openG[index] + openH[index];
        }

        private void copyRecord(final int from, final int to) {
            openNode[to] = openNode[from];
            openFirst[to] = openFirst[from];
            openDepth[to] = openDepth[from];
            openG[to] = openG[from];
            openH[to] = openH[from];
        }

        private void setRecord(
                final int index,
                final int node,
                final int first,
                final int depth,
                final double g,
                final double h) {

            openNode[index] = node;
            openFirst[index] = first;
            openDepth[index] = depth;
            openG[index] = g;
            openH[index] = h;
        }

        private void ensureNodes(final int nodes) {
            if (seen.length >= nodes) {
                return;
            }

            int cap = Math.max(256, seen.length);
            while (cap < nodes) {
                cap <<= 1;
            }

            seen = Arrays.copyOf(seen, cap);
            bestG = Arrays.copyOf(bestG, cap);
            goalMark = Arrays.copyOf(goalMark, cap);
        }

        private void ensureOpen(final int required) {
            if (openNode.length >= required) {
                return;
            }

            int cap = Math.max(64, openNode.length);
            while (cap < required) {
                cap <<= 1;
            }

            openNode = Arrays.copyOf(openNode, cap);
            openFirst = Arrays.copyOf(openFirst, cap);
            openDepth = Arrays.copyOf(openDepth, cap);
            openG = Arrays.copyOf(openG, cap);
            openH = Arrays.copyOf(openH, cap);
        }
    }
    
    public void dumpPathNodes(
            final int mapId,
            final int centerX,
            final int centerY,
            final int radius) {

        final PathGraph g = graph(mapId);

        if (g == null) {
            System.out.println(
                    "[PNODE-DUMP] graph not loaded map=" + mapId);
            return;
        }

        final int minX = centerX - radius;
        final int maxX = centerX + radius;
        final int minY = centerY - radius;
        final int maxY = centerY + radius;

        System.out.println(
                "[PNODE-DUMP] BEGIN map=" + mapId
                + " center=" + centerX + "," + centerY
                + " radius=" + radius
                + " nodes=" + g.nodeX.length);

        for (int node = 0; node < g.nodeX.length; node++) {

            final int x = g.nodeX[node];
            final int y = g.nodeY[node];

            if (x < minX || x > maxX
                    || y < minY || y > maxY) {
                continue;
            }

            final int begin = g.edgeStart[node];
            final int count = g.edgeCount[node] & 0xFF;

            final StringBuilder sb = new StringBuilder(256);

            sb.append("[PNODE-DUMP] NODE id=")
              .append(node)
              .append(" pos=")
              .append(x)
              .append(',')
              .append(y)
              .append(" edges=")
              .append(count);

            for (int i = 0; i < count; i++) {

                final int target = g.edgeTarget[begin + i];

                final int tx = g.nodeX[target];
                final int ty = g.nodeY[target];

                final int dx = tx - x;
                final int dy = ty - y;

                sb.append(" | ")
                  .append(i)
                  .append(":id=")
                  .append(target)
                  .append('@')
                  .append(tx)
                  .append(',')
                  .append(ty)
                  .append(" d=")
                  .append(dx)
                  .append(',')
                  .append(dy);
            }

            System.out.println(sb);
        }

        System.out.println("[PNODE-DUMP] END");
    }
}
