package l1j.server.server.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.concurrent.locks.StampedLock;

import gnu.trove.list.array.TLongArrayList;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.procedure.TLongProcedure;

import l1j.server.Config;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.inventory.L1GroundInventory;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.network.core.broadcast.CTLMFilter;

/**
 * Concurrent canonical 8x8 world map. No CIO shard replica, spatial delta pipe,
 * external MPSC queue, or timer-dependent AOI drain.
 *
 * Mutations complete under the original object monitor + 32 striped locks.
 * Sight candidate snapshots are collected under short read locks and submitted
 * after releasing those locks to each PC's CCreatureController serializer.
 * Existing broad/sends and world collision systems are unchanged.
 */
public final class LocalWorldMap {

    private static final int CELL_SHIFT = 3;
    private static final int CELL_SIZE = 1 << CELL_SHIFT;
    private static final int CELL_MASK = 0xffff;
    static final int MAP_MASK = 0xffff;
    private static final int COORDINATE_MASK = 0xffffff;
    private static final int MAX_TILE = ((CELL_MASK + 1) << CELL_SHIFT) - 1;
    private static final int SMALL_QUERY_CELLS = 256;
    private static final int SCREEN_RADIUS = 22;
    private static final int DELTA_VIEW_ENTER = 4;
    private static final int DELTA_VIEW_LEAVE = 5;
    private static final int DELTA_VIEW_MOVE = 6;
    static final long NO_POSITION = -1L;
    private static final int CANONICAL_STRIPE_COUNT = 32;
    private static final int CANONICAL_STRIPE_MASK = CANONICAL_STRIPE_COUNT - 1;
    private static final int RETAINED_SCAN_SIZE = 512;
    private static final ThreadLocal<IdentityHashMap<L1Object, Boolean>> SCAN_SEEN =
            ThreadLocal.withInitial(IdentityHashMap::new);
    private static final ThreadLocal<TIntIntHashMap> SCAN_IDS =
            ThreadLocal.withInitial(() -> new TIntIntHashMap(16, 0.5f, 0, -1));
    private static final ThreadLocal<CellQuery> CELL_QUERY = ThreadLocal.withInitial(CellQuery::new);

    private static final class CanonicalStripe {
        final StampedLock lock = new StampedLock();
        final TLongObjectHashMap<CellBucket> cells = new TLongObjectHashMap<>(64);
    }

    private final CanonicalStripe[] _canonical =
            new CanonicalStripe[CANONICAL_STRIPE_COUNT];

    private static final class SightScratch {
        final ArrayList<L1Object> viewers = new ArrayList<>(32);
        final ArrayList<L1Object> objects = new ArrayList<>(64);
        boolean inUse;
    }
    private final ThreadLocal<SightScratch> _sightScratch =
            ThreadLocal.withInitial(SightScratch::new);

    // Unlike the legacy single queryNearbyReusable buffer, this scoped
    // snapshot survives callbacks that recursively query nearby objects.
    private static final class NearbyFrame {
        @SuppressWarnings("unchecked")
        ArrayList<L1Object>[] slots =
                (ArrayList<L1Object>[]) new ArrayList<?>[4];
        int depth;
    }
    private final ThreadLocal<NearbyFrame> _nearbyFrames =
            ThreadLocal.withInitial(NearbyFrame::new);

    /** Borrow a reentrant thread-local snapshot; release in finally. */
    public List<L1Object> borrowNearby(L1Object origin, int radius) {
        final NearbyFrame frame = _nearbyFrames.get();
        final int depth = frame.depth;
        if (depth == frame.slots.length)
            frame.slots = Arrays.copyOf(frame.slots, depth << 1);
        ArrayList<L1Object> result = frame.slots[depth];
        if (result == null) {
            result = new ArrayList<>(64);
            frame.slots[depth] = result;
        }
        frame.depth = depth + 1;
        try {
            queryNearby(origin, radius, result);
            return result;
        } catch (RuntimeException | Error failure) {
            result.clear();
            frame.depth = depth;
            throw failure;
        }
    }

    public void releaseNearby(List<L1Object> result) {
        final NearbyFrame frame = _nearbyFrames.get();
        final int depth = frame.depth - 1;
        if (depth < 0 || frame.slots[depth] != result)
            throw new IllegalStateException("AOI nearby release out of order");
        frame.depth = depth;
        if (result.size() > RETAINED_SCAN_SIZE) frame.slots[depth] = null;
        result.clear();
    }

    private static volatile long _lastPosWarn;

    public LocalWorldMap() {
        for (int i = 0; i < CANONICAL_STRIPE_COUNT; i++) {
            _canonical[i] = new CanonicalStripe();
        }
    }

    public int getCellCount() {
        int total = 0;
        for (CanonicalStripe stripe : _canonical) {
            final long stamp = stripe.lock.readLock();
            try {
                total += stripe.cells.size();
            } finally {
                stripe.lock.unlockRead(stamp);
            }
        }
        return total;
    }

    static boolean isSanePosition(int mapId, int x, int y) {
        return mapId >= 0 && mapId < MAP_MASK && x >= 0 && x <= MAX_TILE && y >= 0 && y <= MAX_TILE;
    }
    private static void warnBadPosition(L1Object obj) {
        final long now = System.currentTimeMillis();
        if (now - _lastPosWarn > 10_000L) {
            _lastPosWarn = now;
            System.out.println("[LocalWorldMap] invalid coordinates: id="
                    + obj.getId() + " map=" + obj.getMapId()
                    + " x=" + obj.getX() + " y=" + obj.getY());
        }
    }
    private static long packedKey(int mapId, int cellX, int cellY) {
        return (((long) mapId & 0xffffffffL) << 32)
                | (((long) cellX & CELL_MASK) << 16)
                | ((long) cellY & CELL_MASK);
    }
    private static int canonicalStripeIndex(long key) {
        long z = key;
        z ^= z >>> 33;
        z *= 0xff51afd7ed558ccdL;
        z ^= z >>> 33;
        z *= 0xc4ceb9fe1a85ec53L;
        z ^= z >>> 33;
        return (int) z & CANONICAL_STRIPE_MASK;
    }
    private CanonicalStripe canonicalStripe(long key) {
        return _canonical[canonicalStripeIndex(key)];
    }
    private static int toCell(int coordinate) {
        return coordinate >> CELL_SHIFT;
    }
    private static int getCellRange(int radius) {
        return radius <= 0 ? 0 : (Math.min(radius, MAX_TILE) + CELL_SIZE - 1) >> CELL_SHIFT;
    }
    static long packPosition(int mapId, int x, int y) {
        return (((long) mapId & MAP_MASK) << 48)
                | (((long) x & COORDINATE_MASK) << 24)
                | ((long) y & COORDINATE_MASK);
    }
    static int positionMap(long packed) { return (int) (packed >>> 48) & MAP_MASK; }
    static int positionX(long packed) { return (int) (packed >>> 24) & COORDINATE_MASK; }
    static int positionY(long packed) { return (int) packed & COORDINATE_MASK; }
    static int clusterCell(int tile) { return tile == COORDINATE_MASK ? -1 : toCell(tile); }
    static int clusterTile(int cell) { return cell < 0 || cell > CELL_MASK ? COORDINATE_MASK : cell << CELL_SHIFT; }

    static long publishedPosition(L1Object object) {
        final long position = object.clusterPosition();
        return positionMap(position) == MAP_MASK || positionX(position) > MAX_TILE
                || positionY(position) > MAX_TILE ? NO_POSITION : position;
    }

    /** Call under the object monitor when pairing this generation with its published tile. */
    static L1Map publishedTerrain(L1Object object) {
        return object.clusterTerrain();
    }

    private static final class CellBucket {

        private static final int OBJECT_INIT_CAPACITY = 8;
        private static final int PLAYER_INIT_CAPACITY = 4;

        private L1Object[] _objects = new L1Object[OBJECT_INIT_CAPACITY];
        private int _objectCount;
        private int _groundCount; // item objects + NPC lookup tiles

        private L1PcInstance[] _players;
        private int _playerCount;


        // On L1 maps the usual 8x8 cell is sparse: keep its compact linear array.
        // Only dense cells build an allocation-free int -> array-slot index.
        private static final int DENSE_THRESHOLD = 128;
        private static final int DENSE_RELEASE = 96;
        private int[] _denseSlots;

        private static int hashObjectId(int x) {
            x ^= x >>> 16;
            x *= 0x7feb352d;
            x ^= x >>> 15;
            x *= 0x846ca68b;
            return x ^ (x >>> 16);
        }

        private int findObjectSlot(int id) {
            final int[] dense = _denseSlots;
            if (dense == null) {
                for (int i = 0; i < _objectCount; i++) {
                    if (_objects[i].getId() == id) {
                        return i;
                    }
                }
                return -1;
            }

            final int mask = dense.length - 1;
            int pos = hashObjectId(id) & mask;
            int entry;
            while ((entry = dense[pos]) != 0) {
                final int index = entry - 1;
                if (_objects[index].getId() == id) {
                    return index;
                }
                pos = (pos + 1) & mask;
            }
            return -1;
        }

        private void buildDenseIndex(int minCapacity) {
            int capacity = 128;
            while (capacity < minCapacity) {
                capacity <<= 1;
            }
            final int[] dense = new int[capacity];
            for (int i = 0; i < _objectCount; i++) {
                insertDenseEntry(dense, i + 1, _objects[i].getId());
            }
            _denseSlots = dense;
        }

        private static void insertDenseEntry(int[] dense, int entry, int id) {
            final int mask = dense.length - 1;
            int pos = hashObjectId(id) & mask;
            while (dense[pos] != 0) {
                pos = (pos + 1) & mask;
            }
            dense[pos] = entry;
        }

        private void removeDenseEntry(int id) {
            final int[] dense = _denseSlots;
            final int mask = dense.length - 1;
            int pos = hashObjectId(id) & mask;
            while (dense[pos] != 0 && _objects[dense[pos] - 1].getId() != id) {
                pos = (pos + 1) & mask;
            }
            if (dense[pos] == 0) {
                return;
            }

            // Back-shift by reinserting the following probe cluster; no
            // tombstone objects or long-lived deleted entries.
            dense[pos] = 0;
            pos = (pos + 1) & mask;
            while (dense[pos] != 0) {
                final int entry = dense[pos];
                dense[pos] = 0;
                insertDenseEntry(dense, entry, _objects[entry - 1].getId());
                pos = (pos + 1) & mask;
            }
        }

        private void updateDenseSlot(int id, int updatedIndex) {
            final int[] dense = _denseSlots;
            final int mask = dense.length - 1;
            int pos = hashObjectId(id) & mask;
            while (dense[pos] != 0) {
                if (_objects[dense[pos] - 1].getId() == id) {
                    dense[pos] = updatedIndex + 1;
                    return;
                }
                pos = (pos + 1) & mask;
            }
            throw new IllegalStateException("missing spatial index object: " + id);
        }

        private void addUnsafe(L1Object obj) {
            if (_denseSlots != null) {
                addDenseUnsafe(obj);
                return;
            }

            // Preserve the original flat-array hot path for sparse 2D cells.
            final int id = obj.getId();
            for (int i = 0; i < _objectCount; i++) {
                if (_objects[i].getId() == id) {
                    final L1Object existing = _objects[i];
                    if (existing != obj
                            && CWorld.getInstance().findObject(id) != obj) {
                        return;
                    }
                    replaceGroundCount(existing, obj);
                    _objects[i] = obj;
                    removePlayerByIdUnsafe(id);
                    if (obj instanceof L1PcInstance) {
                        addPlayerUnsafe((L1PcInstance) obj);
                    }
                    return;
                }
            }

            if (_objectCount == _objects.length) {
                final L1Object[] grown = new L1Object[_objects.length << 1];
                System.arraycopy(_objects, 0, grown, 0, _objectCount);
                _objects = grown;
            }
            _objects[_objectCount++] = obj;
            if (isGroundEntry(obj)) _groundCount++;
            if (_objectCount == DENSE_THRESHOLD) {
                buildDenseIndex(_objectCount * 2);
            }
            if (obj instanceof L1PcInstance) {
                addPlayerUnsafe((L1PcInstance) obj);
            }
        }

        private void addDenseUnsafe(L1Object obj) {
            final int id = obj.getId();
            final int index = findObjectSlot(id);
            if (index >= 0) {
                final L1Object existing = _objects[index];
                if (existing != obj && CWorld.getInstance().findObject(id) != obj) {
                    return;
                }
                replaceGroundCount(existing, obj);
                _objects[index] = obj;
                removePlayerByIdUnsafe(id);
                if (obj instanceof L1PcInstance) {
                    addPlayerUnsafe((L1PcInstance) obj);
                }
                return;
            }

            if (_objectCount == _objects.length) {
                final L1Object[] grown = new L1Object[_objects.length << 1];
                System.arraycopy(_objects, 0, grown, 0, _objectCount);
                _objects = grown;
            }
            final int newSlot = _objectCount++;
            _objects[newSlot] = obj;
            if (isGroundEntry(obj)) _groundCount++;
            final int[] dense = _denseSlots;
            if (_objectCount * 2 > dense.length) {
                buildDenseIndex(_objectCount * 2);
            } else {
                insertDenseEntry(dense, newSlot + 1, id);
            }
            if (obj instanceof L1PcInstance) {
                addPlayerUnsafe((L1PcInstance) obj);
            }
        }

        // Player AOI ignores ground tile buckets, but NPCs still need them.
        // Count both tile buckets and items as ground-only cell entries so
        // a stationary viewport can skip an entire crowded 8x8 tile cell.
        private static boolean isGroundEntry(L1Object object) {
            return object instanceof L1ItemInstance
                    || object instanceof L1GroundInventory;
        }

        private void replaceGroundCount(L1Object previous, L1Object current) {
            if (isGroundEntry(previous)) _groundCount--;
            if (isGroundEntry(current)) _groundCount++;
        }

        private void addPlayerUnsafe(L1PcInstance pc) {
            L1PcInstance[] players = _players;

            if (players == null) {
                players = new L1PcInstance[PLAYER_INIT_CAPACITY];
                _players = players;
            } else if (_playerCount == players.length) {
                final L1PcInstance[] grown = new L1PcInstance[players.length << 1];
                System.arraycopy(players, 0, grown, 0, _playerCount);
                players = grown;
                _players = grown;
            }

            players[_playerCount++] = pc;
        }

        private void removeUnsafe(L1Object obj) {
            if (obj == null) {
                return;
            }
            if (_denseSlots != null) {
                removeDenseUnsafe(obj);
                return;
            }

            // The short scan beats a hash lookup for ordinary sparse cells.
            for (int i = 0; i < _objectCount; i++) {
                if (_objects[i] == obj) {
                    if (isGroundEntry(obj)) _groundCount--;
                    final int last = --_objectCount;
                    _objects[i] = _objects[last];
                    _objects[last] = null;
                    break;
                }
            }
            if (obj instanceof L1PcInstance) {
                removePlayerUnsafe((L1PcInstance) obj);
            }
            trimObjectCapacity();
        }

        private void removeDenseUnsafe(L1Object obj) {
            final int index = findObjectSlot(obj.getId());
            if (index >= 0 && _objects[index] == obj) {
                if (isGroundEntry(obj)) _groundCount--;
                final int last = _objectCount - 1;
                removeDenseEntry(obj.getId());
                if (index != last) {
                    updateDenseSlot(_objects[last].getId(), index);
                }
                _objectCount = last;
                _objects[index] = _objects[last];
                _objects[last] = null;
                if (_objectCount < DENSE_RELEASE) {
                    _denseSlots = null;
                } else if (_denseSlots.length >= (long) _objectCount * 8L) {
                    // Dense item piles can shrink to a handful of objects:
                    // release the oversized id-index before the cell retires.
                    buildDenseIndex(_objectCount << 1);
                }
                trimObjectCapacity();
            }
            if (obj instanceof L1PcInstance) {
                removePlayerUnsafe((L1PcInstance) obj);
            }
        }

        /** Shrink only after a 4x occupancy drop; never allocate on reads/moves. */
        private void trimObjectCapacity() {
            final int capacity = _objects.length;
            if (capacity <= 32 || _objectCount > (capacity >>> 2)) return;
            final int reduced = Math.max(32, capacity >>> 1);
            final L1Object[] compact = new L1Object[reduced];
            System.arraycopy(_objects, 0, compact, 0, _objectCount);
            _objects = compact;
        }

        private void trimPlayerCapacity() {
            final L1PcInstance[] players = _players;
            if (players == null) return;
            if (_playerCount == 0) {
                _players = null;
            } else if (players.length > 16 && _playerCount <= (players.length >>> 2)) {
                final L1PcInstance[] compact = new L1PcInstance[Math.max(16, players.length >>> 1)];
                System.arraycopy(players, 0, compact, 0, _playerCount);
                _players = compact;
            }
        }

        private void removePlayerUnsafe(L1PcInstance pc) {
            final L1PcInstance[] players = _players;
            if (players == null || pc == null) {
                return;
            }

            for (int i = 0; i < _playerCount; i++) {
                if (players[i] == pc) {
                    final int last = --_playerCount;
                    players[i] = players[last];
                    players[last] = null;
                            break;
                }
            }

            trimPlayerCapacity();
        }

        private void removePlayerByIdUnsafe(int id) {
            final L1PcInstance[] players = _players;
            if (players == null) {
                return;
            }

            for (int i = 0; i < _playerCount; i++) {
                if (players[i].getId() == id) {
                    final int last = --_playerCount;
                    players[i] = players[last];
                    players[last] = null;
                            break;
                }
            }

            trimPlayerCapacity();
        }

        private boolean isEmptyUnsafe() {
            return _objectCount == 0;
        }

    }

    private static void addToStripeUnsafe(
            CanonicalStripe stripe,
            long key,
            L1Object obj) {

        CellBucket cell = stripe.cells.get(key);
        if (cell == null) {
            cell = new CellBucket();
            stripe.cells.put(key, cell);
        }
        cell.addUnsafe(obj);
    }

    private static void removeFromStripeUnsafe(
            CanonicalStripe stripe,
            long key,
            L1Object obj) {

        final CellBucket cell = stripe.cells.get(key);
        if (cell == null) {
            return;
        }

        cell.removeUnsafe(obj);
        if (cell.isEmptyUnsafe()) {
            stripe.cells.remove(key);
        }
    }

    private void canonicalAdd(long key, L1Object obj) {
        final CanonicalStripe stripe = canonicalStripe(key);
        final long stamp = stripe.lock.writeLock();
        try {
            addToStripeUnsafe(stripe, key, obj);
        } finally {
            stripe.lock.unlockWrite(stamp);
        }
    }

    private void canonicalRemove(long key, L1Object obj) {
        final CanonicalStripe stripe = canonicalStripe(key);
        final long stamp = stripe.lock.writeLock();
        try {
            removeFromStripeUnsafe(stripe, key, obj);
        } finally {
            stripe.lock.unlockWrite(stamp);
        }
    }

    private static long cellKey(long position) {
        return packedKey(positionMap(position), toCell(positionX(position)), toCell(positionY(position)));
    }

    private static long livePosition(L1Object object, L1Map terrain) {
        final int map = terrain.getId(), x = object.getX(), y = object.getY();
        if (isSanePosition(map, x, y)) return packPosition(map, x, y);
        warnBadPosition(object);
        return NO_POSITION;
    }

    /** Caller holds the object monitor, ahead of the stripe locks. */
    private void commitPosition(L1Object object, long previous, long current, L1Map terrain) {
        final long oldKey = previous == NO_POSITION ? NO_POSITION : cellKey(previous);
        final long newKey = current == NO_POSITION ? NO_POSITION : cellKey(current);
        object.publishClusterPosition(current, current == NO_POSITION ? null : terrain);
        if (oldKey == newKey) return;
        if (oldKey != NO_POSITION) canonicalRemove(oldKey, object);
        if (newKey != NO_POSITION) canonicalAdd(newKey, object);
    }

    /** Exact viewer -> tile predicate, matching Point.isInScreen and canSeeObject. */
    private static boolean groundVisibleAt(long position, L1Object tile, int radius) {
        if (position == NO_POSITION || positionMap(position) != tile.getMapId()) return false;
        final int dx = tile.getX() - positionX(position);
        final int dy = tile.getY() - positionY(position);
        if (radius != -1) return Math.max(Math.abs(dx), Math.abs(dy)) <= radius;
        return insideScreen(dx, dy);
    }

    /** Point.isInScreen uses an asymmetric 20–22 tile border. */
    private static boolean insideScreen(int dx, int dy) {
        final int distance = Math.abs(dx) + Math.abs(dy);
        if (distance <= 19) return true;
        if (distance > SCREEN_RADIUS) return false;
        final int shifted = Math.abs(dx + 20) + Math.abs(dy + 20);
        return shifted >= 23 && shifted <= 56;
    }

    private static int minAxisDistance(int value, int lo, int hi) {
        return Math.max(0, Math.max(lo - value, value - hi));
    }

    private static int maxAxisDistance(int value, int lo, int hi) {
        return Math.max(Math.abs(lo - value), Math.abs(hi - value));
    }

    /** 0: entirely outside, 1: entirely inside, -1: test boundary tiles. */
    private static int groundCellSight(long position, int map, int cx, int cy, int radius) {
        if (position == NO_POSITION || positionMap(position) != map) return 0;
        final int x = positionX(position), y = positionY(position);
        final int lx = cx << CELL_SHIFT, ly = cy << CELL_SHIFT;
        final int hx = lx + CELL_SIZE - 1, hy = ly + CELL_SIZE - 1;
        final int nearX = minAxisDistance(x, lx, hx), nearY = minAxisDistance(y, ly, hy);
        final int farX = maxAxisDistance(x, lx, hx), farY = maxAxisDistance(y, ly, hy);
        if (radius != -1) {
            if (Math.max(nearX, nearY) > radius) return 0;
            return Math.max(farX, farY) <= radius ? 1 : -1;
        }
        final int nearest = nearX + nearY, farthest = farX + farY;
        if (nearest > SCREEN_RADIUS) return 0;
        if (farthest <= 19) return 1;
        final int nearShift = minAxisDistance(x - 20, lx, hx) + minAxisDistance(y - 20, ly, hy);
        final int farShift = maxAxisDistance(x - 20, lx, hx) + maxAxisDistance(y - 20, ly, hy);
        if (nearest > 19 && (farShift < 23 || nearShift > 56)) return 0;
        if (farthest <= SCREEN_RADIUS && nearShift >= 23 && farShift <= 56) return 1;
        return -1;
    }

    private static boolean unchangedGroundCell(long oldPos, long newPos,
            int map, int cx, int cy, int radius) {
        if (oldPos == newPos) return true;
        final int oldSight = groundCellSight(oldPos, map, cx, cy, radius);
        return oldSight >= 0 && oldSight == groundCellSight(newPos, map, cx, cy, radius);
    }

    private void collectSightRect(int mapId, int minX, int maxX,
            int minY, int maxY, int skipMapId,
            int skipMinX, int skipMaxX, int skipMinY, int skipMaxY,
            boolean playersOnly, boolean groundDelta, long oldPos, long newPos,
            int radius, ArrayList<L1Object> result) {
        if (mapId == skipMapId && minX >= skipMinX && maxX <= skipMaxX
                && minY >= skipMinY && maxY <= skipMaxY) return;
        if (scanGridCells(minX, minY, maxX, maxY)) {
            for (int cx = minX; cx <= maxX; cx++) {
                for (int cy = minY; cy <= maxY; cy++) {
                    collectSightCell(packedKey(mapId, cx, cy), mapId, cx, cy, skipMapId,
                            skipMinX, skipMaxX, skipMinY, skipMaxY,
                            playersOnly, groundDelta, oldPos, newPos, radius, result);
                }
            }
        } else {
            final TLongArrayList keys = occupiedCellKeys(mapId, minX, minY, maxX, maxY);
            for (int i = 0; i < keys.size(); i++) {
                final long key = keys.getQuick(i);
                collectSightCell(key, mapId, (int) (key >>> 16) & CELL_MASK, (int) key & CELL_MASK,
                        skipMapId, skipMinX, skipMaxX, skipMinY, skipMaxY,
                        playersOnly, groundDelta, oldPos, newPos, radius, result);
            }
        }
    }

    private void collectSightCell(long key, int mapId, int cx, int cy,
            int skipMapId, int skipMinX, int skipMaxX, int skipMinY, int skipMaxY,
            boolean playersOnly, boolean groundDelta, long oldPos, long newPos,
            int radius, ArrayList<L1Object> result) {
        if (mapId == skipMapId
                && CTLMFilter.insideRectangle(
                        cx, cy, skipMinX, skipMaxX, skipMinY, skipMaxY)) return;
        final CanonicalStripe stripe = canonicalStripe(key);
        final long stamp = stripe.lock.readLock();
        try {
            final CellBucket cell = stripe.cells.get(key);
            if (cell == null) return;
            if (playersOnly) {
                final L1PcInstance[] players = cell._players;
                if (players != null) {
                    for (int i = 0; i < cell._playerCount; i++) {
                        if (players[i] != null) result.add(players[i]);
                    }
                }
            } else {
                // Interior ground-only cells have no sight work. In mixed
                // cells preserve creature reconciliation, but visit ground
                // item objects only on the exact old/new tile difference.
                final boolean sameGroundSight = groundDelta && cell._groundCount != 0
                        && unchangedGroundCell(oldPos, newPos, mapId, cx, cy, radius);
                if (sameGroundSight && cell._groundCount == cell._objectCount) return;
                final L1Object[] objects = cell._objects;
                for (int i = 0; i < cell._objectCount; i++) {
                    final L1Object candidate = objects[i];
                    if (candidate == null || candidate instanceof L1GroundInventory) continue;
                    if (groundDelta && candidate instanceof L1ItemInstance
                            && (sameGroundSight || groundVisibleAt(oldPos, candidate, radius)
                                    == groundVisibleAt(newPos, candidate, radius))) continue;
                    result.add(candidate);
                }
            }
        } finally {
            stripe.lock.unlockRead(stamp);
        }
    }

    private void collectSightCandidates(long oldPos, long newPos,
            int radius, boolean playersOnly, boolean groundDelta, ArrayList<L1Object> result) {
        result.clear();
        final int range = getCellRange(radius < 0 ? SCREEN_RADIUS : radius);
        int oldMap = -1;
        int oldMinX = 1, oldMaxX = 0, oldMinY = 1, oldMaxY = 0;
        if (oldPos != NO_POSITION) {
            oldMap = positionMap(oldPos);
            final int cx = toCell(positionX(oldPos));
            final int cy = toCell(positionY(oldPos));
            oldMinX = Math.max(0, cx - range);
            oldMaxX = Math.min(CELL_MASK, cx + range);
            oldMinY = Math.max(0, cy - range);
            oldMaxY = Math.min(CELL_MASK, cy + range);
            collectSightRect(oldMap, oldMinX, oldMaxX, oldMinY, oldMaxY,
                    Integer.MIN_VALUE, 1, 0, 1, 0, playersOnly, groundDelta, oldPos, newPos, radius, result);
        }
        if (newPos != NO_POSITION) {
            final int map = positionMap(newPos);
            final int cx = toCell(positionX(newPos));
            final int cy = toCell(positionY(newPos));
            collectSightRect(map, Math.max(0, cx - range), Math.min(CELL_MASK, cx + range),
                    Math.max(0, cy - range), Math.min(CELL_MASK, cy + range),
                    oldMap, oldMinX, oldMaxX, oldMinY, oldMaxY,
                    playersOnly, groundDelta, oldPos, newPos, radius, result);
        }
    }

    private void publishViewDelta(int op, long oldPosition,
            long newPosition, L1Object object, L1Map unchangedTerrain) {
        // Tile inventories remain spatial for NPC/pet looting, but they are
        // never sent as player AOI objects. Only their CItem entries are.
        if (object == null || object instanceof L1GroundInventory) return;
        final SightScratch local = _sightScratch.get();
        // Nested notifications cannot overwrite the outer candidate list.
        final SightScratch scratch = local.inUse ? new SightScratch() : local;
        scratch.inUse = true;
        try {
            final int radius = Config.PC_RECOGNIZE_RANGE;
            final boolean leaving = op == DELTA_VIEW_LEAVE;
            collectSightCandidates(oldPosition, newPosition, radius,
                    true, false, scratch.viewers);
            for (int i = 0, n = scratch.viewers.size(); i < n; i++) {
                final L1Object viewer = scratch.viewers.get(i);
                if (viewer != null && viewer != object) {
                    final L1PcInstance pc = (L1PcInstance) viewer;
                    pc.getCreatureController().ReconcileSight(object, leaving);
                }
            }
            // When the moved object is itself a player, also reconcile that
            // player's known objects. Never involve CIO shard affinity.
            if (object instanceof L1PcInstance pc && publishedPosition(pc) != NO_POSITION) {
                // Movement between two established positions changes only the
                // ground sight boundary. Login, leave and map resets retain full
                // reconciliation; rejected SENDs retain the existing repair path.
                final boolean groundDelta = op == DELTA_VIEW_MOVE
                        && unchangedTerrain != null && pc.getMap() == unchangedTerrain
                        && publishedPosition(pc) == newPosition
                        && (pc.groundSightEpoch() & 1) != 0;
                collectSightCandidates(oldPosition, newPosition, radius,
                        false, groundDelta, scratch.objects);
                for (int i = 0, n = scratch.objects.size(); i < n; i++) {
                    final L1Object candidate = scratch.objects.get(i);
                    if (candidate != null && candidate != pc) {
                        pc.getCreatureController().ReconcileSight(candidate, leaving);
                    }
                }
                // A failed ground SEND is repaired on the next real movement,
                // not by a recursive refresh that could spin on a full socket.
                if (pc.needsGroundSightRepair())
                    pc.getCreatureController().RefreshSight();
            }
        } finally {
            if (scratch == local && (scratch.viewers.size() > RETAINED_SCAN_SIZE
                    || scratch.objects.size() > RETAINED_SCAN_SIZE)) _sightScratch.remove();
            scratch.viewers.clear();
            scratch.objects.clear();
            scratch.inUse = false;
        }
    }

    /** Canonical registration while caller may hold world/identity locks. */
    long enterSilently(L1Object object) {
        if (object == null) return NO_POSITION;
        synchronized (object) {
            final long previous = publishedPosition(object);
            final L1Map previousTerrain = publishedTerrain(object);
            final L1Map terrain = object.getMap();
            final long current = livePosition(object, terrain);
            commitPosition(object, previous, current, terrain);
            return previousTerrain == terrain && previous != NO_POSITION && current != NO_POSITION
                    && cellKey(previous) == cellKey(current)
                    ? NO_POSITION : current;
        }
    }

    public void enter(L1Object object) {
        reconcileEnter(object, enterSilently(object));
    }

    void reconcileEnter(L1Object object, long position) {
        if (position != NO_POSITION) publishViewDelta(DELTA_VIEW_ENTER, NO_POSITION, position, object, null);
    }

    /** Return the last published tile so the caller can notify after its outer locks. */
    long leaveSilently(L1Object object) {
        if (object == null) return NO_POSITION;
        synchronized (object) {
            final long previous = publishedPosition(object);
            commitPosition(object, previous, NO_POSITION, null);
            return previous;
        }
    }

    public void leave(L1Object object) {
        reconcileLeave(object, leaveSilently(object));
    }

    void reconcileLeave(L1Object object, long position) {
        if (position != NO_POSITION) publishViewDelta(DELTA_VIEW_LEAVE, position, NO_POSITION, object, null);
    }

    public void relocate(L1Object object) {
        relocateSilently(object);
    }

    long relocateSilently(L1Object object) {
        if (object == null) return NO_POSITION;
        synchronized (object) {
            final L1Map terrain = object.getMap();
            final long current = livePosition(object, terrain);
            commitPosition(object, publishedPosition(object), current, terrain);
            return current;
        }
    }

    public void relocate(L1Object object, int oldMap, int oldX, int oldY) {
        if (object == null) return;
        final long previous, current;
        final L1Map unchangedTerrain;
        synchronized (object) {
            previous = publishedPosition(object);
            final L1Map before = publishedTerrain(object);
            current = relocateSilently(object);
            unchangedTerrain = before == publishedTerrain(object) ? before : null;
        }
        reconcileMove(object, previous, current, unchangedTerrain);
    }

    void reconcileMove(L1Object object, long previous, long current, L1Map unchangedTerrain) {
        if (previous == NO_POSITION) reconcileEnter(object, current);
        else if (current == NO_POSITION) reconcileLeave(object, previous);
        else publishViewDelta(DELTA_VIEW_MOVE, previous, current, object, unchangedTerrain);
    }

    public List<L1Object> queryNearby(L1Object origin, int radius) {
        final ArrayList<L1Object> result = new ArrayList<>(radius <= 0 ? 8 : 32);
        queryNearby(origin, radius, result);
        return result;
    }

    public void queryNearby(L1Object origin, int radius, List<L1Object> result) {
        collectObjectsAround(origin, radius < 0 ? -1 : radius, result);
    }

    /** Legacy visible-object semantics use only -1 for screen distance. */
    public void collectVisibleObjects(L1Object origin, int radius, List<L1Object> result) {
        collectObjectsAround(origin, radius, result);
    }

    private void collectObjectsAround(L1Object origin, int radius, List<L1Object> result) {
        result.clear();
        if (origin == null || radius < -1) return;
        final List<L1Object> target = lockedDestination(result);
        queryRadius(origin.getMap(), origin.getX(), origin.getY(), origin, radius, false, target, null);
        finishQuery(result, target);
    }

    public void queryPoint(L1Location center, int radius, List<L1Object> result) {
        result.clear();
        if (center == null || radius < 0) return;
        final List<L1Object> target = lockedDestination(result);
        queryRadius(center.getMap(), center.getX(), center.getY(), null, radius, false, target, null);
        finishQuery(result, target);
    }

    /** Inclusive tile bounds on the exact map generation. */
    public void queryRectangle(L1Map map, int minX, int minY, int maxX, int maxY, List<L1Object> result) {
        result.clear();
        final List<L1Object> target = lockedDestination(result);
        scanRectangle(map, minX, minY, maxX, maxY, null, 0, 0, false, false, target, null);
        finishQuery(result, target);
    }

    public List<L1PcInstance> queryNearbyPlayers(L1Object origin, int radius) {
        final ArrayList<L1PcInstance> result = new ArrayList<>(radius <= 0 ? 8 : 16);
        queryNearbyPlayers(origin, radius, result);
        return result;
    }

    public void queryNearbyPlayers(L1Object origin, int radius, List<L1PcInstance> result) {
        collectVisiblePlayers(origin, radius < 0 ? -1 : radius, result);
    }

    public void collectVisiblePlayers(L1Object origin, int radius, List<L1PcInstance> result) {
        result.clear();
        if (origin == null || radius < -1) return;
        final List<L1PcInstance> target = lockedDestination(result);
        queryRadius(origin.getMap(), origin.getX(), origin.getY(), origin, radius, true, null, target);
        finishQuery(result, target);
    }

    public List<L1PcInstance> queryScreenPlayers(L1Object origin) {
        return queryNearbyPlayers(origin, -1);
    }

    public void queryScreenPlayers(L1Object origin, List<L1PcInstance> result) {
        queryNearbyPlayers(origin, -1, result);
    }

    public boolean hasNearbyPlayer(L1Object origin, int radius) {
        return origin != null && queryRadius(origin.getMap(), origin.getX(), origin.getY(), origin,
                radius < 0 ? -1 : radius, true, null, null);
    }

    private boolean queryRadius(L1Map map, int x, int y, L1Object origin, int radius, boolean playersOnly,
            List<L1Object> objects, List<L1PcInstance> players) {
        final boolean screen = radius == -1;
        final long range = screen ? SCREEN_RADIUS : radius;
        return scanRectangle(map, x - range, y - range, x + range, y + range,
                origin, x, y, screen, playersOnly, objects, players);
    }

    private boolean scanRectangle(L1Map map, long minX, long minY, long maxX, long maxY,
            L1Object origin, int originX, int originY, boolean screen, boolean playersOnly,
            List<L1Object> objects, List<L1PcInstance> players) {
        if (map == null) return false;
        final int mapId = map.getId();
        if (mapId < 0) return false;
        minX = Math.max(0L, minX);
        minY = Math.max(0L, minY);
        maxX = Math.min(MAX_TILE, maxX);
        maxY = Math.min(MAX_TILE, maxY);
        if (minX > maxX || minY > maxY) return false;

        final int originId = origin == null ? 0 : origin.getId();
        final CellQuery local = CELL_QUERY.get();
        final CellQuery scan = local.map == null ? local : new CellQuery();
        scan.map = map;
        scan.minX = (int) minX;
        scan.minY = (int) minY;
        scan.maxX = (int) maxX;
        scan.maxY = (int) maxY;
        scan.excludesOrigin = origin != null;
        scan.originId = originId;
        scan.originX = originX;
        scan.originY = originY;
        scan.screen = screen;
        scan.playersOnly = playersOnly;
        scan.objects = objects;
        scan.players = players;
        try {
            return scanCells(mapId, scan);
        } finally {
            scan.map = null;
            scan.objects = null;
            scan.players = null;
        }
    }

    private boolean scanCells(int mapId, CellQuery scan) {
        final int minX = toCell(scan.minX), minY = toCell(scan.minY);
        final int maxX = toCell(scan.maxX), maxY = toCell(scan.maxY);
        if (scanGridCells(minX, minY, maxX, maxY)) {
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    if (scanCell(packedKey(mapId, x, y), scan)) return true;
                }
            }
            return false;
        }

        final TLongArrayList keys = occupiedCellKeys(mapId, minX, minY, maxX, maxY);
        for (int i = 0, size = keys.size(); i < size; i++) {
            if (scanCell(keys.getQuick(i), scan)) return true;
        }
        return false;
    }

    private boolean scanGridCells(int minX, int minY, int maxX, int maxY) {
        final long area = (long) (maxX - minX + 1) * (maxY - minY + 1);
        // Only broad rectangles pay to inspect occupancy. Ordinary local lookups never scan the map.
        return area <= SMALL_QUERY_CELLS || area <= getCellCount();
    }

    private TLongArrayList occupiedCellKeys(int mapId, int minX, int minY, int maxX, int maxY) {
        // Avoid enumerating empty cells for a range wider than the populated index.
        final TLongArrayList keys = new TLongArrayList();
        final TLongProcedure collect = key -> {
            if ((int) (key >>> 32) == mapId
                    && CTLMFilter.insideRectangle((int) (key >>> 16) & CELL_MASK, (int) key & CELL_MASK,
                            minX, maxX, minY, maxY)) keys.add(key);
            return true;
        };
        for (CanonicalStripe stripe : _canonical) {
            final long stamp = stripe.lock.readLock();
            try {
                stripe.cells.forEachKey(collect);
            } finally {
                stripe.lock.unlockRead(stamp);
            }
        }
        keys.sort();
        return keys;
    }

    private boolean scanCell(long key, CellQuery scan) {
        final CanonicalStripe stripe = canonicalStripe(key);
        final long stamp = stripe.lock.readLock();
        try {
            final CellBucket cell = stripe.cells.get(key);
            return cell != null && scan.collect(cell);
        } finally {
            stripe.lock.unlockRead(stamp);
        }
    }

    private static final class CellQuery {
        L1Map map;
        int minX, minY, maxX, maxY;
        int originId, originX, originY;
        boolean excludesOrigin, screen, playersOnly;
        List<L1Object> objects;
        List<L1PcInstance> players;

        boolean collect(CellBucket cell) {
            final L1Object[] candidates = playersOnly ? cell._players : cell._objects;
            final int size = playersOnly ? cell._playerCount : cell._objectCount;
            for (int i = 0; i < size; i++) {
                final L1Object candidate = candidates[i];
                if (candidate.getMap() != map || (excludesOrigin && candidate.getId() == originId)) continue;
                final int x = candidate.getX(), y = candidate.getY();
                if (!CTLMFilter.insideRectangle(x, y, minX, maxX, minY, maxY)
                        || (screen && !insideScreen(x - originX, y - originY))) continue;
                if (objects != null) objects.add(candidate);
                else if (players != null) players.add((L1PcInstance) candidate);
                else return true;
            }
            return false;
        }
    }

    private static <T> List<T> lockedDestination(List<T> result) {
        // Custom list methods may enter world code, so call them without a stripe lock.
        return result.getClass() == ArrayList.class ? result : new ArrayList<>();
    }

    private static <T extends L1Object> void finishQuery(List<T> result, List<T> target) {
        final int size = target.size();
        if (size > 1) {
            // A concurrent move or replacement may appear in two different cells.
            final TIntIntHashMap ids = scanIds(size);
            IdentityHashMap<L1Object, Boolean> seen = null;
            try {
                int write = 0;
                for (int read = 0; read < size; read++) {
                    final T candidate = target.get(read);
                    final int id = candidate.getId();
                    if (id > 0) {
                        final int previous = ids.putIfAbsent(id, write);
                        if (previous >= 0) {
                            if (publishedPosition(target.get(previous)) == NO_POSITION
                                    && publishedPosition(candidate) != NO_POSITION) target.set(previous, candidate);
                            continue;
                        }
                    } else {
                        if (seen == null) seen = scanReferences(size);
                        if (seen.put(candidate, Boolean.TRUE) != null) continue;
                    }
                    if (write != read) target.set(write, candidate);
                    write++;
                }
                if (write < size) target.subList(write, size).clear();
            } finally {
                ids.clear();
                if (seen != null) seen.clear();
            }
        }
        if (target != result) {
            for (T entry : target) result.add(entry);
        }
    }

    private static TIntIntHashMap scanIds(int size) {
        final TIntIntHashMap cached = SCAN_IDS.get();
        return size <= RETAINED_SCAN_SIZE && cached.isEmpty()
                ? cached : new TIntIntHashMap(size, 0.5f, 0, -1);
    }

    private static IdentityHashMap<L1Object, Boolean> scanReferences(int size) {
        final IdentityHashMap<L1Object, Boolean> cached = SCAN_SEEN.get();
        return size <= RETAINED_SCAN_SIZE && cached.isEmpty() ? cached : new IdentityHashMap<>(size);
    }

    /** Compatibility entry point. Use borrowNearby/releaseNearby for a scoped reusable list. */
    public List<L1Object> queryNearbyReusable(L1Object origin, int radius) {
        return queryNearby(origin, radius);
    }
}
