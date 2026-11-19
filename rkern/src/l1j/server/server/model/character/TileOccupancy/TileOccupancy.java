package l1j.server.server.model.character.TileOccupancy;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.locks.StampedLock;
import org.agrona.collections.Long2LongHashMap;

import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1WorldMap;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;

public final class TileOccupancy {

    private static final TileOccupancy INSTANCE = new TileOccupancy();
    public static TileOccupancy getInstance() { return INSTANCE; }

    private TileOccupancy() {}

    // Both creature types MUST use the same coordinate gate for a final tile claim.
    // Fixed-size stripes limit contention without a map-wide lock or hot-path allocation.
    private static final int CROSS_MASK = 4095;
    private static final Object[] CROSS_GATES = createCrossGates();

    private static Object[] createCrossGates() {
        final Object[] gates = new Object[CROSS_MASK + 1];
        for (int i = 0; i < gates.length; i++) {
            gates[i] = new Object();
        }
        return gates;
    }

    static int gateIndex(int mapId, int x, int y) {
        int h = mapId * 0x9e3779b9;
        h ^= x * 0x85ebca6b;
        h ^= y * 0xc2b2ae35;
        h ^= h >>> 16;
        return h & CROSS_MASK;
    }

    static Object gate(int index) { return CROSS_GATES[index]; }

    private boolean peerClear(int mapId, int x, int y, int actorId) {
        final TileOccupancyNpc other = TileOccupancyNpc.getInstance();
        final short map = (short) mapId;
        final int occupied = other.getOccupier(map, x, y);
        if (occupied != 0 && occupied != actorId) return false;
        final int reserved = other.getReservedBy(map, x, y);
        return reserved == 0 || reserved == actorId;
    }

    // Call only with BOTH source and destination coordinate gates held.
    private boolean moveGuarded(MapContext ctx, int mapId,
                                int sx, int sy, int dx, int dy, int actorId) {
        return peerClear(mapId, dx, dy, actorId)
                && ctx.move(ctx.getKey(sx, sy), ctx.getKey(dx, dy), actorId);
    }

    private boolean ownReservationClear(MapContext ctx, long key, int actorId) {
        final int owner = ctx.getReservedBy(key, System.currentTimeMillis());
        return owner == 0 || owner == actorId;
    }

    private boolean teleportGuarded(MapContext source, MapContext target,
                                    int oldX, int oldY, int newMapId,
                                    int newX, int newY, int actorId) {
        if (!peerClear(newMapId, newX, newY, actorId)
                || !ownReservationClear(target, target.getKey(newX, newY), actorId)
                || !target.tryOccupy(target.getKey(newX, newY), actorId)) return false;
        if (source != null) source.release(source.getKey(oldX, oldY), actorId);
        return true;
    }

    // =========================================================
    // 1. MapContext 
    // =========================================================
    private static final class MapContext {
        private final Long2LongHashMap grid;     // Key: 좌표 -> Value: objectId
        private final Long2LongHashMap reserves; // Key: 좌표 -> Value: Packed(objectId | expireAt)
        private final StampedLock lock = new StampedLock();

        MapContext(int initialCapacity) {
            // 초기용량을 고정하여 메모리 폭발 방지 (Agrona가 필요 시 자동 리사이징)
            this.grid = new Long2LongHashMap(initialCapacity, 0.5f, 0L);
            this.reserves = new Long2LongHashMap(512, 0.5f, 0L);
        }

        private long getKey(int x, int y) {
            return ((long) x << 32) | (y & 0xFFFFFFFFL);
        }

        public int getOccupier(long key) {
            final long stamp = lock.readLock();
            try {
                return (int) grid.get(key);
            } finally {
                lock.unlockRead(stamp);
            }
        }

        public boolean tryOccupy(long key, int objectId) {
            long stamp = lock.writeLock();
            int cur;
            try {
                cur = (int) grid.get(key);
                if (cur == 0 || cur == objectId) {
                    grid.put(key, objectId);
                    return true;
                }
            } finally {
                lock.unlockWrite(stamp);
            }

            if (TileOccupancy.INSTANCE.isGhostOccupier(cur)) {
                stamp = lock.writeLock();
                try {
                    long reCheck = grid.get(key);
                    if (reCheck == 0 || reCheck == cur || reCheck == objectId) {
                        grid.put(key, objectId);
                        return true;
                    }
                } finally {
                    lock.unlockWrite(stamp);
                }
            }
            return false;
        }

        public void release(long key, int objectId) {
            long stamp = lock.writeLock();
            try {
                if (grid.get(key) == objectId) {
                    grid.remove(key);
                }
            } finally {
                lock.unlockWrite(stamp);
            }
        }

        private boolean reservedForOther(long key, int objectId) {
            final long reservation = reserves.get(key);
            return reservation != 0L
                    && (int) (reservation >>> 32) != objectId
                    && (int) reservation - (int) System.currentTimeMillis() > 0;
        }

        public boolean move(long srcKey, long dstKey, int objectId) {
            long stamp = lock.writeLock();
            int curDst;
            try {
                curDst = (int) grid.get(dstKey);
                if ((curDst == 0 || curDst == objectId)
                        && !reservedForOther(dstKey, objectId)) {
                    grid.put(dstKey, objectId);
                    long curSrc = grid.get(srcKey);   
                    if (curSrc == objectId && srcKey != dstKey) {  // ← 내 것만 제거
                        grid.remove(srcKey);
                    }
                    return true;
                }
            } finally {
                lock.unlockWrite(stamp);
            }
            
            if (TileOccupancy.INSTANCE.isGhostOccupier(curDst)) {
                stamp = lock.writeLock();
                try {
                    long reCheck = grid.get(dstKey);
                    if ((reCheck == 0 || reCheck == curDst || reCheck == objectId)
                            && !reservedForOther(dstKey, objectId)) {
                        grid.put(dstKey, objectId);
                        long curSrc = grid.get(srcKey);   
                        if (curSrc == objectId && srcKey != dstKey) {  // ← 내 것만 제거
                            grid.remove(srcKey);
                        }
                        return true;
                    }
                } finally {
                    lock.unlockWrite(stamp);
                }
            }
            return false;
        }

        public boolean tryReserve(long key, int objectId, long ttlMs, long nowMs) {
            long stamp = lock.writeLock();
            int curOcc;
            try {
                curOcc = (int) grid.get(key);
                if (curOcc == 0 || curOcc == objectId) {
                    return reserveInternal(key, objectId, ttlMs, nowMs);
                }
            } finally {
                lock.unlockWrite(stamp);
            }

            if (TileOccupancy.INSTANCE.isGhostOccupier(curOcc)) {
                stamp = lock.writeLock();
                try {
                    long reCheck = grid.get(key);
                    if (reCheck == 0 || reCheck == curOcc || reCheck == objectId) {
                        return reserveInternal(key, objectId, ttlMs, nowMs);
                    }
                } finally {
                    lock.unlockWrite(stamp);
                }
            }
            return false;
        }

        private boolean reserveInternal(long key, int objectId, long ttlMs, long nowMs) {
            long resVal = reserves.get(key);
            if (resVal != 0) {
                int owner = (int) (resVal >>> 32);
                int expLow = (int) resVal;
                int nowLow = (int) nowMs;

                if ((expLow - nowLow) > 0 && owner != objectId) {
                    return false;
                }
            }

            long expMs = nowMs + Math.max(140L, ttlMs);
            long packed = (((long) objectId & 0xFFFFFFFFL) << 32) | (expMs & 0xFFFFFFFFL);
            reserves.put(key, packed);
            return true;
        }

        public void releaseReserve(long key, int objectId) {
            long stamp = lock.writeLock();
            try {
                long resVal = reserves.get(key);
                if (resVal != 0 && (int) (resVal >>> 32) == objectId) {
                    reserves.remove(key);
                }
            } finally {
                lock.unlockWrite(stamp);
            }
        }

        public int getReservedBy(long key, long nowMs) {
            final long stamp = lock.readLock();
            final long resVal;
            try {
                resVal = reserves.get(key);
            } finally {
                lock.unlockRead(stamp);
            }

            if (resVal == 0) return 0;
            if (((int) resVal - (int) nowMs) <= 0) return 0;

            return (int) (resVal >>> 32);
        }

        public void clear() {
            long stamp = lock.writeLock();
            try {
                grid.clear();
                reserves.clear();
            } finally {
                lock.unlockWrite(stamp);
            }
        }
    }

    private static final VarHandle CONTEXT =
            MethodHandles.arrayElementVarHandle(MapContext[].class);
    private final MapContext[] contexts = new MapContext[32768];
    private final Object initLock = new Object();

    private MapContext peekContext(int mapId) {
        return mapId < 0 || mapId >= contexts.length
                ? null : (MapContext) CONTEXT.getAcquire(contexts, mapId);
    }

    private MapContext getContext(int mapId) {
        if (mapId < 0 || mapId >= contexts.length) return null;
        MapContext ctx = (MapContext) CONTEXT.getAcquire(contexts, mapId);
        if (ctx == null) {
            synchronized (initLock) {
                ctx = (MapContext) CONTEXT.getAcquire(contexts, mapId);
                if (ctx == null) {
                    L1Map map = L1WorldMap.getInstance().getMap((short) mapId);
                    if (map != null) {
                        ctx = new MapContext(2048);
                        CONTEXT.setRelease(contexts, mapId, ctx);
                    }
                }
            }
        }
        return ctx;
    }

    // =========================================================
    // Public APIs
    // =========================================================

    public int getOccupier(int mapId, int x, int y) {
        MapContext ctx = peekContext(mapId);
        if (ctx == null) return 0;

        long k = ctx.getKey(x, y);
        int occupierId = ctx.getOccupier(k);
        if (occupierId == 0) return 0;

        if (isGhostOccupier(occupierId)) {
            ctx.release(k, occupierId);
            return 0;
        }
        return occupierId;
    }

    public boolean tryOccupyNow(int mapId, int x, int y, int objectId) {
        if (objectId == 0) return false;
        final MapContext ctx = getContext(mapId);
        if (ctx == null) return false;
        synchronized (gate(gateIndex(mapId, x, y))) {
            return peerClear(mapId, x, y, objectId)
                    && ownReservationClear(ctx, ctx.getKey(x, y), objectId)
                    && ctx.tryOccupy(ctx.getKey(x, y), objectId);
        }
    }

    public void releaseNow(int mapId, int x, int y, int objectId) {
        if (objectId == 0) return;
        final MapContext ctx = getContext(mapId);
        if (ctx == null) return;
        synchronized (gate(gateIndex(mapId, x, y))) {
            ctx.release(ctx.getKey(x, y), objectId);
        }
    }

    public boolean moveRelaxed(int mapId, int oldX, int oldY,
                               int newX, int newY, int objectId) {
        if (objectId == 0) return false;
        final MapContext ctx = getContext(mapId);
        if (ctx == null) return false;
        final int a = gateIndex(mapId, oldX, oldY);
        final int b = gateIndex(mapId, newX, newY);
        synchronized (gate(Math.min(a, b))) {
            synchronized (gate(Math.max(a, b))) {
                return moveGuarded(ctx, mapId, oldX, oldY, newX, newY, objectId);
            }
        }
    }

    public boolean teleportRelaxed(int oldMapId, int oldX, int oldY,
                                   int newMapId, int newX, int newY, int objectId) {
        if (objectId == 0) return false;
        final MapContext dst = getContext(newMapId);
        if (dst == null) return false;
        final MapContext src = getContext(oldMapId);
        final int a = gateIndex(oldMapId, oldX, oldY);
        final int b = gateIndex(newMapId, newX, newY);
        synchronized (gate(Math.min(a, b))) {
            synchronized (gate(Math.max(a, b))) {
                return teleportGuarded(src, dst, oldX, oldY,
                        newMapId, newX, newY, objectId);
            }
        }
    }

    public boolean canMoveTo(int mapId, int x, int y, int objectId, long reserveTtlMs) {
        if (objectId == 0) return false;
        final MapContext ctx = getContext(mapId);
        if (ctx == null) return false;
        synchronized (gate(gateIndex(mapId, x, y))) {
            if (!peerClear(mapId, x, y, objectId)) return false;
            final int occ = ctx.getOccupier(ctx.getKey(x, y));
            if (occ != 0 && occ != objectId && !isGhostOccupier(occ)) return false;
            return ctx.tryReserve(ctx.getKey(x, y), objectId,
                    reserveTtlMs, System.currentTimeMillis());
        }
    }

    public boolean commitMove(int mapId, int oldX, int oldY,
                              int newX, int newY, int objectId) {
        if (objectId == 0) return false;
        final MapContext ctx = getContext(mapId);
        if (ctx == null) return false;
        final int a = gateIndex(mapId, oldX, oldY);
        final int b = gateIndex(mapId, newX, newY);
        final long dstKey = ctx.getKey(newX, newY);
        synchronized (gate(Math.min(a, b))) {
            synchronized (gate(Math.max(a, b))) {
                try {
                    return moveGuarded(ctx, mapId, oldX, oldY, newX, newY, objectId);
                } finally {
                    ctx.releaseReserve(dstKey, objectId);
                }
            }
        }
    }

    public void cancelMove(int mapId, int newX, int newY, int objectId) {
        final MapContext ctx = getContext(mapId);
        if (ctx == null) return;
        synchronized (gate(gateIndex(mapId, newX, newY))) {
            ctx.releaseReserve(ctx.getKey(newX, newY), objectId);
        }
    }

    public int getReservedBy(int mapId, int x, int y) {
        final MapContext ctx = peekContext(mapId);
        return ctx == null ? 0 : ctx.getReservedBy(
                ctx.getKey(x, y), System.currentTimeMillis());
    }

    public void resetAll() {
        for (int i = 0; i < contexts.length; i++) {
            final MapContext ctx = (MapContext) CONTEXT.getAcquire(contexts, i);
            if (ctx != null) {
                ctx.clear();
                CONTEXT.setRelease(contexts, i, null);
            }
        }
    }

    public void destroy() {
        resetAll();
    }

    // =========================================================
    // Ghost Detection & Safe Cleanup
    // =========================================================

    public boolean isGhostOccupier(int occupierId) {
        if (occupierId == 0) return true;

        L1Object obj = L1World.getInstance().findObject(occupierId);
        if (obj == null) return true;

        if (obj instanceof L1PcInstance) {
            L1PcInstance pc = (L1PcInstance) obj;
            return pc.isDead() || pc.isGhost() || pc.isGmInvis();
        } else if (obj instanceof L1NpcInstance) {
            L1NpcInstance npc = (L1NpcInstance) obj;
            return npc.isDead() || npc.getHiddenStatus() != 0 || npc.isPassableNpc();
        }
        return false;
    }

}