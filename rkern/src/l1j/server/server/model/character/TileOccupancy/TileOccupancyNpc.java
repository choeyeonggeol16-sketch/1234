package l1j.server.server.model.character.TileOccupancy;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.locks.StampedLock;
import org.agrona.collections.Long2LongHashMap;

import l1j.server.server.model.L1Object;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1WorldMap;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;

public final class TileOccupancyNpc {

    private static final TileOccupancyNpc INSTANCE = new TileOccupancyNpc();
    public static TileOccupancyNpc getInstance() { return INSTANCE; }

    private TileOccupancyNpc() {}

    private boolean peerClear(short mapId, int x, int y, int actorId) {
        final TileOccupancy other = TileOccupancy.getInstance();
        final int occupied = other.getOccupier(mapId, x, y);
        if (occupied != 0 && occupied != actorId) return false;
        final int reserved = other.getReservedBy(mapId, x, y);
        return reserved == 0 || reserved == actorId;
    }

    private boolean moveGuarded(MapContext ctx, short mapId,
                                int sx, int sy, int dx, int dy, int actorId) {
        return peerClear(mapId, dx, dy, actorId)
                && ownReservationClear(ctx, ctx.getKey(dx, dy), actorId)
                && ctx.move(ctx.getKey(sx, sy), ctx.getKey(dx, dy), actorId);
    }

    private boolean ownReservationClear(MapContext ctx, long key, int actorId) {
        final int owner = getLiveReservedBy(ctx, key);
        return owner == 0 || owner == actorId;
    }

    // World lookup stays outside MapContext's StampedLock. A dead owner must
    // not keep an otherwise empty tile blocked until its reservation TTL.
    private int getLiveReservedBy(MapContext ctx, long key) {
        final int owner = ctx.getReservedBy(key, System.currentTimeMillis());
        if (owner != 0 && isGhostOccupier(owner)) {
            ctx.releaseReserve(key, owner);
            // A different owner may have claimed between the read and release.
            return ctx.getReservedBy(key, System.currentTimeMillis());
        }
        return owner;
    }

    private boolean teleportGuarded(MapContext source, MapContext target,
                                    int oldX, int oldY, short newMapId,
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
        private final L1Map terrain;
        private final Long2LongHashMap cells;
        private final Long2LongHashMap reserves;
        private final StampedLock lock = new StampedLock();

        MapContext(L1Map terrain, int initialCapacity) {
            this.terrain = terrain;
            this.cells = new Long2LongHashMap(initialCapacity, 0.5f, 0L);
            this.reserves = new Long2LongHashMap(1024, 0.5f, 0L);
        }

        private long getKey(int x, int y) {
            return ((long) x << 32) | (y & 0xFFFFFFFFL);
        }

        public int getOccupier(long key) {
            long stamp = lock.readLock();
            try {
                return (int) cells.get(key);
            } finally {
                lock.unlockRead(stamp);
            }
        }

        public boolean tryOccupy(long key, int objectId) {
            long stamp = lock.writeLock();
            int cur;
            try {
                cur = (int) cells.get(key);
                if (cur == 0 || cur == objectId) {
                    cells.put(key, objectId);
                    return true;
                }
            } finally {
                lock.unlockWrite(stamp);
            }

            if (TileOccupancyNpc.INSTANCE.isGhostOccupier(cur)) {
                stamp = lock.writeLock();
                try {
                    long reCheck = cells.get(key);
                    if (reCheck == cur || reCheck == 0) {
                        cells.put(key, objectId);
                        return true;
                    }
                    return reCheck == objectId;
                } finally {
                    lock.unlockWrite(stamp);
                }
            }
            return false;
        }

        public void release(long key, int objectId) {
            long stamp = lock.writeLock();
            try {
                if (cells.get(key) == objectId) {
                    cells.remove(key);
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
                curDst = (int) cells.get(dstKey);
                if ((curDst == 0 || curDst == objectId)
                        && !reservedForOther(dstKey, objectId)) {
                    cells.put(dstKey, objectId);
                    long curSrc = cells.get(srcKey);   
                    if (curSrc == objectId && srcKey != dstKey) { 
                    	cells.remove(srcKey);
                    }
                    return true;
                }
            } finally {
                lock.unlockWrite(stamp);
            }

            if (TileOccupancyNpc.INSTANCE.isGhostOccupier(curDst)) {
                stamp = lock.writeLock();
                try {
                    long reCheck = cells.get(dstKey);
                    if ((reCheck == curDst || reCheck == 0)
                            && !reservedForOther(dstKey, objectId)) {
                        cells.put(dstKey, objectId);
                        long curSrc = cells.get(srcKey);   
                        if (curSrc == objectId && srcKey != dstKey) { 
                        	cells.remove(srcKey);
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
            int occ;
            try {
                occ = (int) cells.get(key);
                if (occ == 0 || occ == objectId) {
                    return checkAndPutReserve(key, objectId, ttlMs, nowMs);
                }
            } finally {
                lock.unlockWrite(stamp);
            }

            if (TileOccupancyNpc.INSTANCE.isGhostOccupier(occ)) {
                stamp = lock.writeLock();
                try {
                    long reCheck = cells.get(key);
                    if (reCheck == occ || reCheck == 0) {
                        return checkAndPutReserve(key, objectId, ttlMs, nowMs);
                    }
                } finally {
                    lock.unlockWrite(stamp);
                }
            }
            return false;
        }

        private boolean checkAndPutReserve(long key, int objectId, long ttlMs, long nowMs) {
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
            long resVal;
            long stamp = lock.readLock();
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
                cells.clear();
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


    private MapContext peekContext(int id) {
        if (id < 0 || id >= contexts.length) return null;
        MapContext ctx = (MapContext) CONTEXT.getAcquire(contexts, id);
        return ctx != null && ctx.terrain == L1WorldMap.getInstance().findMap(id) ? ctx : null;
    }

    private MapContext getContext(int id) {
        if (id < 0 || id >= contexts.length) return null;
        MapContext ctx = peekContext(id);
        if (ctx != null) return ctx;
        synchronized (initLock) {
            L1Map terrain = L1WorldMap.getInstance().findMap(id);
            if (terrain == null || terrain.isNull()) return null;
            ctx = (MapContext) CONTEXT.getAcquire(contexts, id);
            if (ctx == null || ctx.terrain != terrain) {
                ctx = new MapContext(terrain, Math.min(4096, Math.max(256, (int) Math.min(Integer.MAX_VALUE, (long) terrain.getWidth() * terrain.getHeight() / 8))));
                CONTEXT.setRelease(contexts, id, ctx);
            }
            return ctx;
        }
    }

    public void retireMap(L1Map terrain) {
        int id = terrain.getId();
        if (id < 0 || id >= contexts.length) return;
        synchronized (initLock) {
            MapContext ctx = (MapContext) CONTEXT.getAcquire(contexts, id);
            if (ctx != null && ctx.terrain == terrain) CONTEXT.setRelease(contexts, id, null);
        }
    }


    // =========================================================
    // Public APIs
    // =========================================================

    public int getOccupier(short mapId, int x, int y) {
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

    public boolean tryOccupyNow(short mapId, int x, int y, int occupierId) {
        return tryOccupyNow(getContext(mapId), mapId, x, y, occupierId);
    }

    public boolean tryOccupyNow(L1Map terrain, int x, int y, int occupierId) {
        if (terrain == null) return false;
        final short mapId = (short) terrain.getId();
        final MapContext ctx = getContext(mapId);
        if (ctx == null || ctx.terrain != terrain) return false;
        return tryOccupyNow(ctx, mapId, x, y, occupierId);
    }

    private boolean tryOccupyNow(MapContext ctx, short mapId, int x, int y, int occupierId) {
        if (occupierId == 0) return false;
        if (ctx == null) return false;
        synchronized (TileOccupancy.gate(TileOccupancy.gateIndex(mapId, x, y))) {
            return peerClear(mapId, x, y, occupierId)
                    && ownReservationClear(ctx, ctx.getKey(x, y), occupierId)
                    && ctx.tryOccupy(ctx.getKey(x, y), occupierId);
        }
    }

    /** Rind may share a tile only with the player selected for its landing. */
    public boolean claimLandingTile(L1NpcInstance npc, L1PcInstance target, L1Map terrain, int x, int y) {
        final short mapId = npc.getMapId();
        final MapContext ctx = getContext(mapId);
        if (ctx == null || ctx.terrain != terrain) return false;
        synchronized (TileOccupancy.gate(TileOccupancy.gateIndex(mapId, x, y))) {
            final CWorld world = CWorld.getInstance();
            if (npc.getMap() != terrain || target.getMap() != terrain || target.getX() != x || target.getY() != y
                    || target.isDead() || target.isLogoutStarted() || world.findObject(target.getId()) != target
                    || world.findObject(npc.getId()) != npc) return false;
            final TileOccupancy players = TileOccupancy.getInstance();
            if (players.getOccupier(mapId, x, y) != target.getId()) return false;
            final int reserved = players.getReservedBy(mapId, x, y);
            final long key = ctx.getKey(x, y);
            return (reserved == 0 || reserved == target.getId())
                    && ownReservationClear(ctx, key, npc.getId()) && ctx.tryOccupy(key, npc.getId());
        }
    }

    public void releaseNow(short mapId, int x, int y, int occupierId) {
        releaseNow(peekContext(mapId), mapId, x, y, occupierId);
    }

    public void releaseNow(L1Map terrain, int x, int y, int occupierId) {
        if (terrain == null) return;
        final short mapId = (short) terrain.getId();
        final MapContext ctx = peekContext(mapId);
        if (ctx == null || ctx.terrain != terrain) return;
        releaseNow(ctx, mapId, x, y, occupierId);
    }

    private void releaseNow(MapContext ctx, short mapId, int x, int y, int occupierId) {
        if (occupierId == 0) return;
        if (ctx == null) return;
        synchronized (TileOccupancy.gate(TileOccupancy.gateIndex(mapId, x, y))) {
            ctx.release(ctx.getKey(x, y), occupierId);
        }
    }

    public boolean moveRelaxed(short mapId, int sx, int sy,
                               int dx, int dy, int occupierId) {
        if (occupierId == 0) return false;
        final MapContext ctx = getContext(mapId);
        if (ctx == null) return false;
        final int a = TileOccupancy.gateIndex(mapId, sx, sy);
        final int b = TileOccupancy.gateIndex(mapId, dx, dy);
        synchronized (TileOccupancy.gate(Math.min(a, b))) {
            synchronized (TileOccupancy.gate(Math.max(a, b))) {
                return moveGuarded(ctx, mapId, sx, sy, dx, dy, occupierId);
            }
        }
    }

    public boolean teleportRelaxed(short oldMapId, int oldX, int oldY,
                                   short newMapId, int newX, int newY, int occupierId) {
        if (occupierId == 0) return false;
        final MapContext dst = getContext(newMapId);
        if (dst == null) return false;
        final MapContext src = peekContext(oldMapId);
        final int a = TileOccupancy.gateIndex(oldMapId, oldX, oldY);
        final int b = TileOccupancy.gateIndex(newMapId, newX, newY);
        synchronized (TileOccupancy.gate(Math.min(a, b))) {
            synchronized (TileOccupancy.gate(Math.max(a, b))) {
                return teleportGuarded(src, dst, oldX, oldY,
                        newMapId, newX, newY, occupierId);
            }
        }
    }

    public boolean canMoveTo(short mapId, int x, int y, int occupierId, long reserveTtlMs) {
        return canMoveTo(getContext(mapId), mapId, x, y, occupierId, reserveTtlMs);
    }

    public boolean canMoveTo(L1Map terrain, int x, int y, int occupierId, long reserveTtlMs) {
        if (terrain == null) return false;
        final short mapId = (short) terrain.getId();
        final MapContext ctx = getContext(mapId);
        return ctx != null && ctx.terrain == terrain && canMoveTo(ctx, mapId, x, y, occupierId, reserveTtlMs);
    }

    private boolean canMoveTo(MapContext ctx, short mapId, int x, int y, int occupierId, long reserveTtlMs) {
        if (occupierId == 0 || ctx == null) return false;
        synchronized (TileOccupancy.gate(TileOccupancy.gateIndex(mapId, x, y))) {
            if (!peerClear(mapId, x, y, occupierId)) return false;
            final int occ = ctx.getOccupier(ctx.getKey(x, y));
            if (occ != 0 && occ != occupierId && !isGhostOccupier(occ)) return false;
            if (!ownReservationClear(ctx, ctx.getKey(x, y), occupierId)) return false;
            return ctx.tryReserve(ctx.getKey(x, y), occupierId,
                    reserveTtlMs, System.currentTimeMillis());
        }
    }

    public boolean commitMove(short mapId, int sx, int sy,
                              int dx, int dy, int occupierId) {
        if (occupierId == 0) return false;
        final MapContext ctx = getContext(mapId);
        if (ctx == null) return false;
        final int a = TileOccupancy.gateIndex(mapId, sx, sy);
        final int b = TileOccupancy.gateIndex(mapId, dx, dy);
        final long dstKey = ctx.getKey(dx, dy);
        synchronized (TileOccupancy.gate(Math.min(a, b))) {
            synchronized (TileOccupancy.gate(Math.max(a, b))) {
                try {
                    return moveGuarded(ctx, mapId, sx, sy, dx, dy, occupierId);
                } finally {
                    ctx.releaseReserve(dstKey, occupierId);
                }
            }
        }
    }

    public void cancelMove(short mapId, int dx, int dy, int occupierId) {
        cancelMove(peekContext(mapId), mapId, dx, dy, occupierId);
    }

    public void cancelMove(L1Map terrain, int dx, int dy, int occupierId) {
        if (terrain == null) return;
        final short mapId = (short) terrain.getId();
        final MapContext ctx = peekContext(mapId);
        if (ctx == null || ctx.terrain != terrain) return;
        cancelMove(ctx, mapId, dx, dy, occupierId);
    }

    private void cancelMove(MapContext ctx, short mapId, int dx, int dy, int occupierId) {
        if (ctx == null) return;
        synchronized (TileOccupancy.gate(TileOccupancy.gateIndex(mapId, dx, dy))) {
            ctx.releaseReserve(ctx.getKey(dx, dy), occupierId);
        }
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

        L1Object obj = CWorld.getInstance().findObject(occupierId);
        if (obj == null) return true;

        if (obj instanceof L1PcInstance pc) {
            return pc.isDead() || pc.isGhost() || pc.isGmInvis();
        } else if (obj instanceof L1NpcInstance npc) {
            return npc.isDead() || npc.getHiddenStatus() != 0 || npc.isPassableNpc();
        }
        return false;
    }
    
    public int getReservedBy(short mapId, int x, int y) {
        MapContext ctx = peekContext(mapId);
        if (ctx == null) return 0;
        return getLiveReservedBy(ctx, ctx.getKey(x, y));
    }
    
}