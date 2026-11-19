package l1j.server.server.model.ai.core;

import java.util.Arrays;

import l1j.server.server.model.map.L1Map;

final class PathMaker {

    private static final int E = 0;
    private static final int W = 1;
    private static final int S = 2;
    private static final int N = 3;

    private static final int[] CDX = { 1, -1,  0,  0 };
    private static final int[] CDY = { 0,  0,  1, -1 };
    private static final int[] CARD_TO_L1_HEADING = { 2, 6, 4, 0 };

    private static final int[] LEFT     = { N, S, E, W };
    private static final int[] RIGHT    = { S, N, W, E };
    private static final int[] OPPOSITE = { W, E, N, S };

    private static final int BUCKET_SHIFT = 4;
    private static final int BUCKET_SIZE = 1 << BUCKET_SHIFT;

    private static final int MAX_SIBLINGS = 8;

    private static final int MAX_BOUNDARY_STEPS = 30_000;

    private static final int STRAIGHT_NODE_INTERVAL = 20;

    private static final long SIMPLIFY_CHORD_DIST2 = 8L * 8L;

    private static final double COS_160 = Math.cos(Math.toRadians(160.0));

    private static final long DEDUPE_WORLD_DIST2 = 7L * 7L;

    private static final double GEO_WORLD = 16.0;
    private static final double GEO_CENTER = 8.0;

    private static final byte VISIT_NORMAL = 0x01;
    private static final byte VISIT_ASYM = 0x02;

    private static final byte NODE_TYPE_BOUNDARY = 1;
    private static final byte NODE_TYPE_FILL = 3;

    private PathMaker() {
    }

    static PathGraph build(final L1Map map) {
        final Builder b = new Builder(map);

        b.buildNormalBoundaryNodes();
        b.buildAsymmetricBoundaryNodes();
        b.compactNodes();

        b.fillEmptyBuckets();
        b.buildDirectedGraph();

        return b.freeze();
    }

    private static final class Builder {
        private final L1Map map;
        private final int mapId;
        private final int baseX;
        private final int baseY;
        private final int width;
        private final int height;

        private final byte[] visit;

        private final int bucketBaseX;
        private final int bucketBaseY;
        private final int bucketWidth;
        private final int bucketHeight;
        private final int[] bucketHead;

        private int[] nodeX = new int[1024];
        private int[] nodeY = new int[1024];
        private byte[] nodeType = new byte[1024];
        private byte[] nodeActive = new byte[1024];
        private int[] nodeNextInBucket = new int[1024];

        private byte[] linkCount = new byte[1024];
        private int[] links = new int[1024 * MAX_SIBLINGS];

        private int nodeCount;
        private int activeNodeCount;

        Builder(final L1Map map) {
            this.map = map;
            this.mapId = map.getId();
            this.baseX = map.getX();
            this.baseY = map.getY();
            this.width = map.getWidth();
            this.height = map.getHeight();

            final long tileCount = (long) width * height;
            if (tileCount > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("map too large: " + width + "x" + height);
            }
            visit = new byte[(int) tileCount];

            bucketBaseX = baseX >> BUCKET_SHIFT;
            bucketBaseY = baseY >> BUCKET_SHIFT;
            final int maxX = (baseX + Math.max(0, width - 1)) >> BUCKET_SHIFT;
            final int maxY = (baseY + Math.max(0, height - 1)) >> BUCKET_SHIFT;
            bucketWidth = Math.max(1, maxX - bucketBaseX + 1);
            bucketHeight = Math.max(1, maxY - bucketBaseY + 1);
            bucketHead = new int[bucketWidth * bucketHeight];
            Arrays.fill(bucketHead, -1);

            Arrays.fill(nodeNextInBucket, -1);
            Arrays.fill(links, -1);
        }

        void buildNormalBoundaryNodes() {
            final int endX = baseX + width;
            final int endY = baseY + height;

            for (int x = baseX; x < endX; x++) {
                for (int y = baseY; y < endY; y++) {
                    if (!map.isInMap(x, y)) {
                        continue;
                    }

                    final int vi = tileIndex(x, y);
                    if ((visit[vi] & VISIT_NORMAL) != 0) {
                        continue;
                    }

                    final int mask = cardinalMask(x, y);
                    if (Integer.bitCount(mask) != 3) {
                        continue;
                    }

                    final int blocked = missingCardinal(mask);
                    if (blocked < 0) {
                        continue;
                    }

                    visit[vi] |= VISIT_NORMAL;

                    startNormalWalker(x, y, RIGHT[blocked], false);
                    startNormalWalker(x, y, LEFT[blocked], true);
                }
            }
        }

        private void startNormalWalker(
                final int seedX,
                final int seedY,
                final int heading,
                final boolean mirrored) {

            if (!canCardinal(seedX, seedY, heading)) {
                return;
            }

            final int x = seedX + CDX[heading];
            final int y = seedY + CDY[heading];
            if (!map.isInMap(x, y)) {
                return;
            }

            walkNormalBoundary(x, y, heading, mirrored);
        }

        private void walkNormalBoundary(
                int x,
                int y,
                int heading,
                final boolean mirrored) {

            int prevTurn = 0;
            int straightCount = 0;

            for (int steps = 0; steps < MAX_BOUNDARY_STEPS; steps++) {
                if (!map.isInMap(x, y)) {
                    return;
                }

                final int vi = tileIndex(x, y);
                if ((visit[vi] & VISIT_NORMAL) != 0) {
                    return;
                }
                visit[vi] |= VISIT_NORMAL;

                final int firstTurn = mirrored ? RIGHT[heading] : LEFT[heading];
                final int secondTurn = mirrored ? LEFT[heading] : RIGHT[heading];

                if (canCardinal(x, y, firstTurn)) {
                    addNode(x, y, NODE_TYPE_BOUNDARY);

                    if (prevTurn == -1) {
                        return;
                    }

                    straightCount = 0;
                    heading = firstTurn;
                    x += CDX[heading];
                    y += CDY[heading];
                    prevTurn = -1;
                    continue;
                }

                if (canCardinal(x, y, heading)) {
                    if (++straightCount == STRAIGHT_NODE_INTERVAL) {
                        addNode(x, y, NODE_TYPE_BOUNDARY);
                        straightCount = 0;
                    }

                    x += CDX[heading];
                    y += CDY[heading];
                    prevTurn = 0;
                    continue;
                }

                if (canCardinal(x, y, secondTurn)) {
                    trySimplifyAtCorner(x, y);

                    straightCount = 0;
                    heading = secondTurn;
                    x += CDX[heading];
                    y += CDY[heading];
                    prevTurn = 1;
                    continue;
                }

                return;
            }
        }

        void buildAsymmetricBoundaryNodes() {
            final int endX = baseX + width;
            final int endY = baseY + height;

            for (int x = baseX; x < endX; x++) {
                for (int y = baseY; y < endY; y++) {
                    if (!map.isInMap(x, y)) {
                        continue;
                    }

                    final int vi = tileIndex(x, y);
                    if ((visit[vi] & VISIT_ASYM) != 0) {
                        continue;
                    }
                    visit[vi] |= VISIT_ASYM;

                    if (cardinalMask(x, y) != 0x0F) {
                        continue;
                    }

                    int asymDir = -1;
                    int asymCount = 0;
                    for (int d = 0; d < 4; d++) {
                        if (isAsymmetricEdge(x, y, d)) {
                            asymDir = d;
                            asymCount++;
                        }
                    }

                    if (asymCount != 1) {
                        continue;
                    }

                    startAsymmetricWalker(x, y, RIGHT[asymDir], asymDir, false);
                    startAsymmetricWalker(x, y, LEFT[asymDir], asymDir, true);
                }
            }
        }

        private void startAsymmetricWalker(
                final int seedX,
                final int seedY,
                final int heading,
                final int boundaryDir,
                final boolean mirrored) {

            if (!canBidirectionalCardinal(seedX, seedY, heading)) {
                return;
            }

            final int x = seedX + CDX[heading];
            final int y = seedY + CDY[heading];
            if (!map.isInMap(x, y)) {
                return;
            }

            walkAsymmetricBoundary(x, y, heading, boundaryDir, mirrored);
        }

        private void walkAsymmetricBoundary(
                int x,
                int y,
                int heading,
                int boundaryDir,
                final boolean mirrored) {

            int straightCount = 0;

            for (int steps = 0; steps < MAX_BOUNDARY_STEPS; steps++) {
                if (!map.isInMap(x, y)) {
                    return;
                }

                final int vi = tileIndex(x, y);
                if ((visit[vi] & VISIT_ASYM) != 0) {
                    return;
                }
                visit[vi] |= VISIT_ASYM;

                final int unique = uniqueAsymmetricDirection(x, y);
                if (unique < 0) {
                    addNode(x, y, NODE_TYPE_BOUNDARY);
                    return;
                }
                boundaryDir = unique;

                final int toward = mirrored ? RIGHT[heading] : LEFT[heading];
                final int away = mirrored ? LEFT[heading] : RIGHT[heading];

                if (canAsymBoundaryAdvance(x, y, toward, boundaryDir)) {
                    heading = toward;
                    straightCount = 0;
                    x += CDX[heading];
                    y += CDY[heading];
                    continue;
                }

                if (canAsymBoundaryAdvance(x, y, heading, boundaryDir)) {
                    if (++straightCount == STRAIGHT_NODE_INTERVAL) {
                        addNode(x, y, NODE_TYPE_BOUNDARY);
                        straightCount = 0;
                    }
                    x += CDX[heading];
                    y += CDY[heading];
                    continue;
                }

                if (canAsymBoundaryAdvance(x, y, away, boundaryDir)) {
                    heading = away;
                    straightCount = 0;
                    x += CDX[heading];
                    y += CDY[heading];
                    continue;
                }

                addNode(x, y, NODE_TYPE_BOUNDARY);
                return;
            }
        }

        private boolean canAsymBoundaryAdvance(
                final int x,
                final int y,
                final int moveDir,
                final int currentBoundaryDir) {

            if (!canBidirectionalCardinal(x, y, moveDir)) {
                return false;
            }

            final int nx = x + CDX[moveDir];
            final int ny = y + CDY[moveDir];
            if (!map.isInMap(nx, ny)) {
                return false;
            }

            final int nextBoundary = uniqueAsymmetricDirection(nx, ny);
            if (nextBoundary < 0) {
                return false;
            }

            return nextBoundary == currentBoundaryDir
                    || nextBoundary == LEFT[currentBoundaryDir]
                    || nextBoundary == RIGHT[currentBoundaryDir];
        }

        void fillEmptyBuckets() {
            for (int bx = 0; bx < bucketWidth; bx++) {
                for (int by = 0; by < bucketHeight; by++) {
                    final int bucket = bx * bucketHeight + by;
                    if (bucketHead[bucket] >= 0) {
                        continue;
                    }

                    final int bucketStartX = (bucketBaseX + bx) << BUCKET_SHIFT;
                    final int bucketStartY = (bucketBaseY + by) << BUCKET_SHIFT;
                    final int minX = Math.max(baseX, bucketStartX);
                    final int minY = Math.max(baseY, bucketStartY);
                    final int maxX = Math.min(baseX + width, bucketStartX + BUCKET_SIZE);
                    final int maxY = Math.min(baseY + height, bucketStartY + BUCKET_SIZE);

                    final long packed = findType3Representative(minX, minY, maxX, maxY);
                    if (packed == Long.MIN_VALUE) {
                        continue;
                    }

                    final int x = (int) (packed >> 32);
                    final int y = (int) packed;
                    addNode(x, y, NODE_TYPE_FILL);
                }
            }
        }

        private long findType3Representative(
                final int minX,
                final int minY,
                final int maxX,
                final int maxY) {

            final int SUB = 8; 

            for (int sx = minX; sx < maxX; sx += SUB) {
                final int sxEnd = Math.min(maxX, sx + SUB);
                for (int sy = minY; sy < maxY; sy += SUB) {
                    final int syEnd = Math.min(maxY, sy + SUB);

                    for (int x = sx; x < sxEnd; x++) {
                        for (int y = sy; y < syEnd; y++) {
                            if (!map.isInMap(x, y)) {
                                continue;
                            }

                            if (cardinalMask(x, y) == 0) {
                                continue;
                            }

                            return ((long) x << 32) | (y & 0xFFFFFFFFL);
                        }
                    }
                }
            }

            return Long.MIN_VALUE;
        }

        void buildDirectedGraph() {
            if (nodeCount == 0) {
                return;
            }

            final int heapStride = nodeCount;
            final int[] heapNode = new int[heapStride * 4];
            final long[] heapDist2 = new long[heapStride * 4];
            final int[] heapSize = new int[4];

            for (int source = 0; source < nodeCount; source++) {
                Arrays.fill(heapSize, 0);

                final int sx = nodeX[source];
                final int sy = nodeY[source];
                final int sbx = (sx >> BUCKET_SHIFT) - bucketBaseX;
                final int sby = (sy >> BUCKET_SHIFT) - bucketBaseY;

                final int minBx = Math.max(0, sbx - 1);
                final int maxBx = Math.min(bucketWidth - 1, sbx + 1);
                final int minBy = Math.max(0, sby - 1);
                final int maxBy = Math.min(bucketHeight - 1, sby + 1);

                for (int bx = minBx; bx <= maxBx; bx++) {
                    for (int by = minBy; by <= maxBy; by++) {
                        int target = bucketHead[bx * bucketHeight + by];
                        while (target >= 0) {
                            if (target != source) {
                                if (moveStraight3(
                                        sx, sy,
                                        nodeX[target], nodeY[target])) {

                                    final int dx = nodeX[target] - sx;
                                    final int dy = nodeY[target] - sy;
                                    final long dist2 = (long) dx * dx + (long) dy * dy;
                                    final int sector = directionSector4(dx, dy);

                                    heapPush(
                                            sector,
                                            target,
                                            dist2,
                                            heapStride,
                                            heapNode,
                                            heapDist2,
                                            heapSize);
                                }
                            }
                            target = nodeNextInBucket[target];
                        }
                    }
                }

                int remain = MAX_SIBLINGS;
                while (remain > 0) {
                    boolean tookAny = false;

                    for (int sector = 0; sector < 4 && remain > 0; sector++) {
                        if (heapSize[sector] == 0) {
                            continue;
                        }

                        final int target = heapPop(
                                sector,
                                heapStride,
                                heapNode,
                                heapDist2,
                                heapSize);

                        if (addDirectedLink(source, target)) {
                            remain--;
                        }
                        tookAny = true;
                    }

                    if (!tookAny) {
                        break;
                    }
                }
            }
        }

        private static int directionSector4(final int dx, final int dy) {
            double a = Math.atan2((double) dy, (double) dx)
                    + Math.PI
                    + Math.PI / 6.0;

            final double twoPi = Math.PI * 2.0;
            if (a >= twoPi) {
                a -= twoPi;
            }
            if (a < 0.0) {
                a += twoPi;
            }

            final int q = (int) ((a / -Math.PI) * 2.0);
            int nativeSector = 4 - q;
            if (nativeSector < 4) nativeSector = 4;
            if (nativeSector > 7) nativeSector = 7;
            return nativeSector - 4;
        }

        private static void heapPush(
                final int sector,
                final int node,
                final long dist2,
                final int stride,
                final int[] heapNode,
                final long[] heapDist2,
                final int[] heapSize) {

            final int base = sector * stride;
            int pos = heapSize[sector]++;

            while (pos > 0) {
                final int parent = (pos - 1) >>> 1;
                final long pd = heapDist2[base + parent];
                final int pn = heapNode[base + parent];
                if (pd < dist2 || (pd == dist2 && pn <= node)) {
                    break;
                }

                heapDist2[base + pos] = pd;
                heapNode[base + pos] = pn;
                pos = parent;
            }

            heapDist2[base + pos] = dist2;
            heapNode[base + pos] = node;
        }

        private static int heapPop(
                final int sector,
                final int stride,
                final int[] heapNode,
                final long[] heapDist2,
                final int[] heapSize) {

            final int base = sector * stride;
            final int result = heapNode[base];
            final int size = --heapSize[sector];
            if (size == 0) {
                return result;
            }

            final int lastNode = heapNode[base + size];
            final long lastDist = heapDist2[base + size];
            int pos = 0;

            while (true) {
                int child = (pos << 1) + 1;
                if (child >= size) {
                    break;
                }

                int best = child;
                final int right = child + 1;
                if (right < size) {
                    final long ld = heapDist2[base + child];
                    final long rd = heapDist2[base + right];
                    final int ln = heapNode[base + child];
                    final int rn = heapNode[base + right];
                    if (rd < ld || (rd == ld && rn < ln)) {
                        best = right;
                    }
                }

                final long bd = heapDist2[base + best];
                final int bn = heapNode[base + best];
                if (lastDist < bd || (lastDist == bd && lastNode <= bn)) {
                    break;
                }

                heapDist2[base + pos] = bd;
                heapNode[base + pos] = bn;
                pos = best;
            }

            heapDist2[base + pos] = lastDist;
            heapNode[base + pos] = lastNode;
            return result;
        }

        private boolean moveStraight3(
                final int x0,
                final int y0,
                final int x1,
                final int y1) {

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

                if (ddx != 0 && ddy != 0) {
                    final int xDir = ddx > 0 ? E : W;
                    final int yDir = ddy > 0 ? S : N;

                    if (!canCardinal(cx, cy, xDir)) {
                        return false;
                    }
                    final int xMid = cx + CDX[xDir];
                    final int yMid = cy + CDY[xDir];
                    if (!canCardinal(xMid, yMid, yDir)) {
                        return false;
                    }

                    if (!canCardinal(cx, cy, yDir)) {
                        return false;
                    }
                    final int xMid2 = cx + CDX[yDir];
                    final int yMid2 = cy + CDY[yDir];
                    if (!canCardinal(xMid2, yMid2, xDir)) {
                        return false;
                    }
                } else if (ddx != 0) {
                    if (!canCardinal(cx, cy, ddx > 0 ? E : W)) {
                        return false;
                    }
                } else {
                    if (!canCardinal(cx, cy, ddy > 0 ? S : N)) {
                        return false;
                    }
                }

                cx = nx;
                cy = ny;
            }

            return cx == x1 && cy == y1;
        }

        private static int floorGeoCell(final double world) {
            return (int) Math.floor(world / GEO_WORLD);
        }

        private int addNode(final int x, final int y, final byte type) {
            if (!map.isInMap(x, y)) {
                return -1;
            }

            final int bucket = bucketIndex(x, y);
            if (bucket < 0) {
                return -1;
            }

            for (int n = bucketHead[bucket]; n >= 0; n = nodeNextInBucket[n]) {
                if (nodeActive[n] == 0) {
                    continue;
                }

                final long dxWorld = (long) (nodeX[n] - x) << 4;
                final long dyWorld = (long) (nodeY[n] - y) << 4;
                final long dist2 = dxWorld * dxWorld + dyWorld * dyWorld;
                if (dist2 < DEDUPE_WORLD_DIST2) {
                    nodeX[n] = x;
                    nodeY[n] = y;
                    nodeType[n] = type;
                    return n;
                }
            }

            ensureNodeCapacity(nodeCount + 1);
            final int node = nodeCount++;
            activeNodeCount++;

            nodeX[node] = x;
            nodeY[node] = y;
            nodeType[node] = type;
            nodeActive[node] = 1;
            linkCount[node] = 0;

            final int linkBase = node * MAX_SIBLINGS;
            Arrays.fill(links, linkBase, linkBase + MAX_SIBLINGS, -1);

            nodeNextInBucket[node] = bucketHead[bucket];
            bucketHead[bucket] = node;

            return node;
        }

        private void trySimplifyAtCorner(final int cx, final int cy) {
            final int b = lastActiveNode();
            if (b < 0) return;
            final int a = previousActiveNode(b);
            if (a < 0) return;

            final long acx = (long) nodeX[a] - cx;
            final long acy = (long) nodeY[a] - cy;
            final long acDist2 = acx * acx + acy * acy;
            if (acDist2 > SIMPLIFY_CHORD_DIST2) {
                return;
            }

            if (!moveStraight3(nodeX[a], nodeY[a], cx, cy)
                    || !moveStraight3(cx, cy, nodeX[a], nodeY[a])) {
                return;
            }

            final double bax = (double) nodeX[a] - nodeX[b];
            final double bay = (double) nodeY[a] - nodeY[b];
            final double bcx = (double) cx - nodeX[b];
            final double bcy = (double) cy - nodeY[b];

            final double lenA2 = bax * bax + bay * bay;
            final double lenC2 = bcx * bcx + bcy * bcy;
            if (lenA2 == 0.0 || lenC2 == 0.0) {
                return;
            }

            final double cos = (bax * bcx + bay * bcy)
                    / Math.sqrt(lenA2 * lenC2);

            if (cos < COS_160) {
                nodeActive[b] = 0;
                activeNodeCount--;
            }
        }

        private int lastActiveNode() {
            for (int i = nodeCount - 1; i >= 0; i--) {
                if (nodeActive[i] != 0) return i;
            }
            return -1;
        }

        private int previousActiveNode(final int before) {
            for (int i = before - 1; i >= 0; i--) {
                if (nodeActive[i] != 0) return i;
            }
            return -1;
        }

        private void compactNodes() {
            if (activeNodeCount == nodeCount) {
                rebuildBuckets();
                return;
            }

            int w = 0;
            for (int i = 0; i < nodeCount; i++) {
                if (nodeActive[i] == 0) {
                    continue;
                }

                if (w != i) {
                    nodeX[w] = nodeX[i];
                    nodeY[w] = nodeY[i];
                    nodeType[w] = nodeType[i];
                }
                nodeActive[w] = 1;
                linkCount[w] = 0;
                w++;
            }

            for (int i = w; i < nodeCount; i++) {
                nodeActive[i] = 0;
                linkCount[i] = 0;
            }

            nodeCount = w;
            activeNodeCount = w;
            rebuildBuckets();
        }

        private void rebuildBuckets() {
            Arrays.fill(bucketHead, -1);
            Arrays.fill(nodeNextInBucket, 0, nodeCount, -1);

            for (int i = 0; i < nodeCount; i++) {
                if (nodeActive[i] == 0) {
                    continue;
                }

                final int bucket = bucketIndex(nodeX[i], nodeY[i]);
                if (bucket < 0) {
                    continue;
                }

                nodeNextInBucket[i] = bucketHead[bucket];
                bucketHead[bucket] = i;
            }
        }

        private int bucketIndex(final int x, final int y) {
            final int bx = (x >> BUCKET_SHIFT) - bucketBaseX;
            final int by = (y >> BUCKET_SHIFT) - bucketBaseY;
            if ((bx | by) < 0 || bx >= bucketWidth || by >= bucketHeight) {
                return -1;
            }
            return bx * bucketHeight + by;
        }

        private int cardinalMask(final int x, final int y) {
            int mask = 0;
            if (canCardinal(x, y, E)) mask |= 1 << E;
            if (canCardinal(x, y, W)) mask |= 1 << W;
            if (canCardinal(x, y, S)) mask |= 1 << S;
            if (canCardinal(x, y, N)) mask |= 1 << N;
            return mask;
        }

        private boolean canCardinal(final int x, final int y, final int dir) {
            final int nx = x + CDX[dir];
            final int ny = y + CDY[dir];
            return map.isInMap(x, y)
                    && map.isInMap(nx, ny)
                    && map.isMovePassableGeoNpc(x, y, CARD_TO_L1_HEADING[dir]);
        }

        private boolean canBidirectionalCardinal(
                final int x,
                final int y,
                final int dir) {

            if (!canCardinal(x, y, dir)) {
                return false;
            }
            final int nx = x + CDX[dir];
            final int ny = y + CDY[dir];
            return canCardinal(nx, ny, OPPOSITE[dir]);
        }

        private boolean isAsymmetricEdge(final int x, final int y, final int dir) {
            if (!canCardinal(x, y, dir)) {
                return false;
            }

            final int nx = x + CDX[dir];
            final int ny = y + CDY[dir];
            return map.isInMap(nx, ny)
                    && !canCardinal(nx, ny, OPPOSITE[dir]);
        }

        private int uniqueAsymmetricDirection(final int x, final int y) {
            if (cardinalMask(x, y) != 0x0F) {
                return -1;
            }

            int found = -1;
            int count = 0;
            for (int d = 0; d < 4; d++) {
                if (isAsymmetricEdge(x, y, d)) {
                    found = d;
                    count++;
                }
            }
            return count == 1 ? found : -1;
        }

        private static int missingCardinal(final int mask) {
            for (int d = 0; d < 4; d++) {
                if ((mask & (1 << d)) == 0) {
                    return d;
                }
            }
            return -1;
        }

        private int tileIndex(final int x, final int y) {
            return (x - baseX) * height + (y - baseY);
        }

        private boolean addDirectedLink(final int from, final int to) {
            if (from == to) {
                return false;
            }

            final int count = linkCount[from] & 0xFF;
            if (count >= MAX_SIBLINGS) {
                return false;
            }

            final int base = from * MAX_SIBLINGS;
            for (int i = 0; i < count; i++) {
                if (links[base + i] == to) {
                    return false;
                }
            }

            links[base + count] = to;
            linkCount[from] = (byte) (count + 1);
            return true;
        }

        PathGraph freeze() {
            final int[] xs = Arrays.copyOf(nodeX, nodeCount);
            final int[] ys = Arrays.copyOf(nodeY, nodeCount);
            final int[] edgeStart = new int[nodeCount];
            final byte[] edgeCount = Arrays.copyOf(linkCount, nodeCount);

            int totalEdges = 0;

            for (int i = 0; i < nodeCount; i++) {
                final int degree = edgeCount[i] & 0xFF;
                edgeStart[i] = totalEdges;
                totalEdges += degree;
            }

            final int[] edgeTarget = new int[totalEdges];
            int w = 0;
            for (int i = 0; i < nodeCount; i++) {
                final int count = edgeCount[i] & 0xFF;
                final int base = i * MAX_SIBLINGS;
                for (int j = 0; j < count; j++) {
                    edgeTarget[w++] = links[base + j];
                }
            }

            return new PathGraph(
                    mapId,
                    baseX,
                    baseY,
                    width,
                    height,
                    xs,
                    ys,
                    edgeStart,
                    edgeCount,
                    edgeTarget);
        }

        private void ensureNodeCapacity(final int needed) {
            if (needed <= nodeX.length) {
                return;
            }

            int cap = nodeX.length << 1;
            while (cap < needed) {
                cap <<= 1;
            }

            nodeX = Arrays.copyOf(nodeX, cap);
            nodeY = Arrays.copyOf(nodeY, cap);
            nodeType = Arrays.copyOf(nodeType, cap);
            nodeActive = Arrays.copyOf(nodeActive, cap);
            nodeNextInBucket = Arrays.copyOf(nodeNextInBucket, cap);
            linkCount = Arrays.copyOf(linkCount, cap);

            final int oldLinks = links.length;
            links = Arrays.copyOf(links, cap * MAX_SIBLINGS);
            Arrays.fill(links, oldLinks, links.length, -1);
        }
    }
}