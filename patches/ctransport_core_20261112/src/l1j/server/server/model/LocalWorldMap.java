package l1j.server.server.model;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.StampedLock;

import gnu.trove.map.hash.TLongObjectHashMap;

import l1j.server.Config;
import l1j.server.server.model.Instance.L1PcInstance;

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
    private static final int SCREEN_RADIUS = 22;
    private static final int DELTA_ENTER = 1;
    private static final int DELTA_LEAVE = 2;
    private static final int DELTA_MOVE = 3;
    private static final int DELTA_VIEW_ENTER = 4;
    private static final int DELTA_VIEW_LEAVE = 5;
    private static final int DELTA_VIEW_MOVE = 6;
    private static final long NO_POSITION = -1L;
    private static final int CANONICAL_STRIPE_COUNT = 32;
    private static final int CANONICAL_STRIPE_MASK = CANONICAL_STRIPE_COUNT - 1;

    private static final class CanonicalStripe {
        final StampedLock lock = new StampedLock();
        final TLongObjectHashMap<CellBucket> cells = new TLongObjectHashMap<>(64);
    }

    private final CanonicalStripe[] _canonical =
            new CanonicalStripe[CANONICAL_STRIPE_COUNT];

    // Lazy one-list-per-completion-thread storage; no owner worker or new queue.
    private final ThreadLocal<ArrayList<L1Object>> _nearbyScratch =
            ThreadLocal.withInitial(() -> new ArrayList<>(64));

    private static final class SightScratch {
        final ArrayList<L1Object> viewers = new ArrayList<>(32);
        final ArrayList<L1Object> objects = new ArrayList<>(64);
        boolean inUse;
    }
    private final ThreadLocal<SightScratch> _sightScratch =
            ThreadLocal.withInitial(SightScratch::new);

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

    private static boolean isSanePosition(int mapId, int x, int y) {
        return mapId >= 0 && x >= 0 && y >= 0;
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
                | (((long) cellX & 0xffffL) << 16)
                | ((long) cellY & 0xffffL);
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
        return radius <= 0 ? 0 : (radius + CELL_SIZE - 1) >> CELL_SHIFT;
    }
    private static long packPosition(int mapId, int x, int y) {
        return (((long) mapId & 0xffffL) << 48)
                | (((long) x & 0xffffffL) << 24)
                | ((long) y & 0xffffffL);
    }
    private static int positionMap(long packed) { return (int) (packed >>> 48) & 0xffff; }
    private static int positionX(long packed) { return (int) (packed >>> 24) & 0xffffff; }
    private static int positionY(long packed) { return (int) packed & 0xffffff; }

    private static final class CellBucket {

        private static final int OBJECT_INIT_CAPACITY = 8;
        private static final int PLAYER_INIT_CAPACITY = 4;

        private L1Object[] _objects = new L1Object[OBJECT_INIT_CAPACITY];
        private int _objectCount;

        private L1PcInstance[] _players;
        private int _playerCount;


        // NC CTL_Map2 uses an object-indexed linkage instead of scanning a cell.
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
                            && L1World.getInstance().findObject(id) != obj) {
                        return;
                    }
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
                if (existing != obj && L1World.getInstance().findObject(id) != obj) {
                    return;
                }
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
                    final int last = --_objectCount;
                    _objects[i] = _objects[last];
                    _objects[last] = null;
                    break;
                }
            }
            if (obj instanceof L1PcInstance) {
                removePlayerUnsafe((L1PcInstance) obj);
            }
        }

        private void removeDenseUnsafe(L1Object obj) {
            final int index = findObjectSlot(obj.getId());
            if (index >= 0 && _objects[index] == obj) {
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
                }
            }
            if (obj instanceof L1PcInstance) {
                removePlayerUnsafe((L1PcInstance) obj);
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

            if (_playerCount == 0) {
                _players = null;
            }
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

            if (_playerCount == 0) {
                _players = null;
            }
        }

        private boolean isEmptyUnsafe() {
            return _objectCount == 0;
        }

        private void collectNearby(
                L1Object origin,
                int radius,
                boolean screenMode,
                List<L1Object> result) {

            final L1Object[] objects = _objects;

            for (int i = 0, size = _objectCount; i < size; i++) {
                final L1Object obj = objects[i];

                if (l1j.server.server.model.network.core.broadcast.CTLMFilter.nearby(origin, obj, radius, screenMode))
                    result.add(obj);
            }
        }

        private void collectNearbyPlayers(
                L1Object origin,
                int radius,
                boolean screenMode,
                List<L1PcInstance> result) {

            final L1PcInstance[] players = _players;
            final int playerCount = _playerCount;
            if (players == null || playerCount == 0) {
                return;
            }


            for (int i = 0; i < playerCount; i++) {
                final L1PcInstance pc = players[i];

                if (l1j.server.server.model.network.core.broadcast.CTLMFilter.nearby(origin, pc, radius, screenMode))
                    result.add(pc);
            }
        }

        private boolean hasNearbyPlayer(
                L1Object origin,
                int radius,
                boolean screenMode) {

            final L1PcInstance[] players = _players;
            final int playerCount = _playerCount;
            if (players == null || playerCount == 0) {
                return false;
            }


            for (int i = 0; i < playerCount; i++) {
                final L1PcInstance pc = players[i];

                if (l1j.server.server.model.network.core.broadcast.CTLMFilter.nearby(origin, pc, radius, screenMode))
                    return true;
            }

            return false;
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

    private void canonicalMove(long oldKey, long newKey, L1Object obj) {
        final int oldIndex = canonicalStripeIndex(oldKey);
        final int newIndex = canonicalStripeIndex(newKey);

        if (oldIndex == newIndex) {
            final CanonicalStripe stripe = _canonical[oldIndex];
            final long stamp = stripe.lock.writeLock();
            try {
                removeFromStripeUnsafe(stripe, oldKey, obj);
                addToStripeUnsafe(stripe, newKey, obj);
            } finally {
                stripe.lock.unlockWrite(stamp);
            }
            return;
        }

        final int firstIndex = Math.min(oldIndex, newIndex);
        final int secondIndex = Math.max(oldIndex, newIndex);
        final CanonicalStripe first = _canonical[firstIndex];
        final CanonicalStripe second = _canonical[secondIndex];
        final long firstStamp = first.lock.writeLock();
        final long secondStamp = second.lock.writeLock();

        try {
            removeFromStripeUnsafe(_canonical[oldIndex], oldKey, obj);
            addToStripeUnsafe(_canonical[newIndex], newKey, obj);
        } finally {
            second.lock.unlockWrite(secondStamp);
            first.lock.unlockWrite(firstStamp);
        }
    }

    private void applyCanonicalDelta(
            int op,
            long oldKey,
            long newKey,
            L1Object obj) {

        if (obj == null) {
            return;
        }

        /*
         * Canonical is a deferred compatibility mirror for owner-thread writes.
         * Therefore an older queued MOVE may be drained after the same object has
         * already moved again or left the world. Never resurrect an old position:
         * remove the stale source reference, but add only when the object's current
         * cluster position still matches this delta's destination.
         */
        // Hold the same object monitor as the producer while checking its
        // current cell and updating canonical stripes. Without this boundary,
        // a position change between check and add could recreate a stale cell.
        synchronized (obj) {
            final boolean present = hasClusterPosition(obj);
            final long currentKey = present ? currentCellKey(obj) : 0L;
            switch (op) {
            case DELTA_ENTER:
                if (present && currentKey == newKey) {
                    canonicalAdd(newKey, obj);
                }
                break;

            case DELTA_LEAVE:
                if (!present || currentKey != oldKey) {
                    canonicalRemove(oldKey, obj);
                }
                break;

            case DELTA_MOVE:
                if (!present || currentKey != oldKey) {
                    canonicalRemove(oldKey, obj);
                }
                if (present && currentKey == newKey) {
                    canonicalAdd(newKey, obj);
                }
                break;

            default:
                break;
            }
        }
    }

    private void commitSpatialDelta(int op, long oldKey, long newKey, L1Object obj) {
        applyCanonicalDelta(op, oldKey, newKey, obj);
    }

    private static boolean hasClusterPosition(L1Object obj) {
        return obj.getClusterMapId() >= 0
                && obj.getClusterX() >= 0
                && obj.getClusterY() >= 0;
    }

    private static long currentCellKey(L1Object obj) {
        return packedKey(
                obj.getClusterMapId(),
                obj.getClusterX(),
                obj.getClusterY());
    }

    private void collectSightRect(int mapId, int minX, int maxX,
            int minY, int maxY, int skipMapId,
            int skipMinX, int skipMaxX, int skipMinY, int skipMaxY,
            boolean playersOnly, ArrayList<L1Object> result) {
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cy = minY; cy <= maxY; cy++) {
                if (mapId == skipMapId
                        && l1j.server.server.model.network.core.broadcast.CTLMFilter.insideRectangle(
                                cx, cy, skipMinX, skipMaxX, skipMinY, skipMaxY)) continue;
                final long key = packedKey(mapId, cx, cy);
                final CanonicalStripe stripe = canonicalStripe(key);
                final long stamp = stripe.lock.readLock();
                try {
                    final CellBucket cell = stripe.cells.get(key);
                    if (cell == null) continue;
                    if (playersOnly) {
                        final L1PcInstance[] players = cell._players;
                        if (players != null) {
                            for (int i = 0; i < cell._playerCount; i++) {
                                if (players[i] != null) result.add(players[i]);
                            }
                        }
                    } else {
                        final L1Object[] objects = cell._objects;
                        for (int i = 0; i < cell._objectCount; i++) {
                            if (objects[i] != null) result.add(objects[i]);
                        }
                    }
                } finally {
                    stripe.lock.unlockRead(stamp);
                }
            }
        }
    }

    private void collectSightCandidates(long oldPos, long newPos,
            int radius, boolean playersOnly, ArrayList<L1Object> result) {
        result.clear();
        final int range = getCellRange(radius < 0 ? SCREEN_RADIUS : radius);
        int oldMap = -1;
        int oldMinX = 1, oldMaxX = 0, oldMinY = 1, oldMaxY = 0;
        if (oldPos != NO_POSITION) {
            oldMap = positionMap(oldPos);
            final int cx = toCell(positionX(oldPos));
            final int cy = toCell(positionY(oldPos));
            oldMinX = cx - range;
            oldMaxX = cx + range;
            oldMinY = cy - range;
            oldMaxY = cy + range;
            collectSightRect(oldMap, oldMinX, oldMaxX, oldMinY, oldMaxY,
                    Integer.MIN_VALUE, 1, 0, 1, 0, playersOnly, result);
        }
        if (newPos != NO_POSITION) {
            final int map = positionMap(newPos);
            final int cx = toCell(positionX(newPos));
            final int cy = toCell(positionY(newPos));
            collectSightRect(map, cx - range, cx + range,
                    cy - range, cy + range,
                    oldMap, oldMinX, oldMaxX, oldMinY, oldMaxY,
                    playersOnly, result);
        }
    }

    private void publishViewDelta(int op, long oldPosition,
            long newPosition, L1Object object) {
        if (object == null) return;
        final SightScratch local = _sightScratch.get();
        // Nested notifications cannot overwrite the outer candidate list.
        final SightScratch scratch = local.inUse ? new SightScratch() : local;
        scratch.inUse = true;
        try {
            final int radius = Config.PC_RECOGNIZE_RANGE;
            final boolean leaving = op == DELTA_VIEW_LEAVE;
            collectSightCandidates(oldPosition, newPosition, radius,
                    true, scratch.viewers);
            for (int i = 0, n = scratch.viewers.size(); i < n; i++) {
                final L1Object viewer = scratch.viewers.get(i);
                if (viewer != null && viewer != object) {
                    final L1PcInstance pc = (L1PcInstance) viewer;
                    pc.getCreatureController().ReconcileSight(object, leaving);
                }
            }
            // When the moved object is itself a player, also reconcile that
            // player's known objects. Never involve CIO shard affinity.
            if (object instanceof L1PcInstance) {
                final L1PcInstance pc = (L1PcInstance) object;
                collectSightCandidates(oldPosition, newPosition, radius,
                        false, scratch.objects);
                for (int i = 0, n = scratch.objects.size(); i < n; i++) {
                    final L1Object candidate = scratch.objects.get(i);
                    if (candidate != null && candidate != pc) {
                        pc.getCreatureController().ReconcileSight(candidate, leaving);
                    }
                }
            }
        } finally {
            scratch.viewers.clear();
            scratch.objects.clear();
            scratch.inUse = false;
        }
    }

    /** Canonical registration while caller may hold world/identity locks. */
    long enterSilently(L1Object obj) {
        if (obj == null) return NO_POSITION;
        synchronized (obj) {
            if (!isSanePosition(obj.getMapId(), obj.getX(), obj.getY())) {
                warnBadPosition(obj);
                if (hasClusterPosition(obj)) {
                    final long oldKey = currentCellKey(obj);
                    obj.setClusterPos(-1, -1);
                    obj.setClusterMapId(-1);
                    commitSpatialDelta(DELTA_LEAVE, oldKey, 0L, obj);
                } else {
                    obj.setClusterPos(-1, -1);
                    obj.setClusterMapId(-1);
                }
                return NO_POSITION;
            }
            final int map = obj.getMapId();
            final int cx = toCell(obj.getX());
            final int cy = toCell(obj.getY());
            if (hasClusterPosition(obj)
                    && obj.getClusterMapId() == map
                    && obj.getClusterX() == cx
                    && obj.getClusterY() == cy) return NO_POSITION;
            final boolean hadOld = hasClusterPosition(obj);
            final long oldKey = hadOld ? currentCellKey(obj) : 0L;
            final long newKey = packedKey(map, cx, cy);
            obj.setClusterPos(cx, cy);
            obj.setClusterMapId(map);
            commitSpatialDelta(hadOld ? DELTA_MOVE : DELTA_ENTER,
                    oldKey, newKey, obj);
            return packPosition(map, obj.getX(), obj.getY());
        }
    }

    public void enter(L1Object obj) {
        final long position = enterSilently(obj);
        if (position != NO_POSITION) reconcileEnter(obj, position);
    }

    void reconcileEnter(L1Object obj, long position) {
        if (position != NO_POSITION) {
            publishViewDelta(DELTA_VIEW_ENTER, NO_POSITION, position, obj);
        }
    }

    /** Return old packed position so caller can notify *after* its outer locks. */
    long leaveSilently(L1Object obj) {
        if (obj == null) return NO_POSITION;
        synchronized (obj) {
            if (!hasClusterPosition(obj)) return NO_POSITION;
            final long oldKey = currentCellKey(obj);
            final long oldPos = packPosition(obj.getMapId(), obj.getX(), obj.getY());
            obj.setClusterPos(-1, -1);
            obj.setClusterMapId(-1);
            commitSpatialDelta(DELTA_LEAVE, oldKey, 0L, obj);
            return oldPos;
        }
    }

    public void leave(L1Object obj) {
        final long oldPosition = leaveSilently(obj);
        if (oldPosition != NO_POSITION) reconcileLeave(obj, oldPosition);
    }

    void reconcileLeave(L1Object obj, long oldPosition) {
        if (oldPosition != NO_POSITION) {
            publishViewDelta(DELTA_VIEW_LEAVE, oldPosition, NO_POSITION, obj);
        }
    }

    public void relocate(L1Object obj) {
        relocate0(obj, false, 0, 0, 0);
    }

    public void relocate(L1Object obj, int oldMap, int oldX, int oldY) {
        relocate0(obj, true, oldMap, oldX, oldY);
    }

    void reconcileMove(L1Object obj, int oldMap, int oldX, int oldY,
            int newMap, int newX, int newY) {
        final long oldPos = packPosition(oldMap, oldX, oldY);
        if (isSanePosition(newMap, newX, newY)) {
            publishViewDelta(DELTA_VIEW_MOVE, oldPos,
                    packPosition(newMap, newX, newY), obj);
        } else {
            publishViewDelta(DELTA_VIEW_LEAVE, oldPos, NO_POSITION, obj);
        }
    }

    private void relocate0(L1Object obj, boolean publishView,
            int oldMap, int oldX, int oldY) {
        if (obj == null) return;
        int resultOp = 0;
        long oldPos = NO_POSITION;
        long newPos = NO_POSITION;
        synchronized (obj) {
            if (publishView) oldPos = packPosition(oldMap, oldX, oldY);
            if (!isSanePosition(obj.getMapId(), obj.getX(), obj.getY())) {
                warnBadPosition(obj);
                if (hasClusterPosition(obj)) {
                    final long oldKey = currentCellKey(obj);
                    obj.setClusterPos(-1, -1);
                    obj.setClusterMapId(-1);
                    commitSpatialDelta(DELTA_LEAVE, oldKey, 0L, obj);
                    if (publishView) resultOp = DELTA_VIEW_LEAVE;
                } else {
                    obj.setClusterPos(-1, -1);
                    obj.setClusterMapId(-1);
                }
            } else {
                final int map = obj.getMapId();
                final int x = obj.getX(), y = obj.getY();
                final int cx = toCell(x), cy = toCell(y);
                newPos = publishView ? packPosition(map, x, y) : NO_POSITION;
                if (!hasClusterPosition(obj)) {
                    obj.setClusterPos(cx, cy);
                    obj.setClusterMapId(map);
                    commitSpatialDelta(DELTA_ENTER, 0L, packedKey(map, cx, cy), obj);
                } else if (obj.getClusterMapId() != map
                        || obj.getClusterX() != cx
                        || obj.getClusterY() != cy) {
                    final long oldKey = currentCellKey(obj);
                    obj.setClusterPos(cx, cy);
                    obj.setClusterMapId(map);
                    commitSpatialDelta(DELTA_MOVE, oldKey, packedKey(map, cx, cy), obj);
                }
                if (publishView) resultOp = DELTA_VIEW_MOVE;
            }
        }
        if (resultOp != 0) publishViewDelta(resultOp, oldPos, newPos, obj);
    }

    /*
     * 일반 호출부는 그대로 사용 가능.
     */
    public List<L1Object> queryNearby(L1Object origin, int radius) {
        final ArrayList<L1Object> result =
                new ArrayList<>(radius <= 0 ? 8 : 32);

        queryNearby(origin, radius, result);

        return result;
    }

    /*
     * 호출할 때 기존 result 내용은 자동 clear.
     */
    public void queryNearby(
            L1Object origin,
            int radius,
            List<L1Object> result) {

        result.clear();

        final int mapId = origin.getMapId();
        final int cellX = toCell(origin.getX());
        final int cellY = toCell(origin.getY());

        final boolean screenMode = radius < 0;
        final int effectiveRadius = screenMode ? SCREEN_RADIUS : radius;
        final int cellRange = getCellRange(effectiveRadius);

        for (int dx = -cellRange; dx <= cellRange; dx++) {
            for (int dy = -cellRange; dy <= cellRange; dy++) {
                final long key = packedKey(mapId, cellX + dx, cellY + dy);
                final CanonicalStripe stripe = canonicalStripe(key);
                final long stamp = stripe.lock.readLock();
                try {
                    final CellBucket cell = stripe.cells.get(key);
                    if (cell != null) {
                        cell.collectNearby(origin, radius, screenMode, result);
                    }
                } finally {
                    stripe.lock.unlockRead(stamp);
                }
            }
        }
    }

    public List<L1PcInstance> queryNearbyPlayers(
            L1Object origin,
            int radius) {

        final ArrayList<L1PcInstance> result =
                new ArrayList<>(radius <= 0 ? 8 : 16);

        queryNearbyPlayers(origin, radius, result);

        return result;
    }

    /*
     * 재사용 버퍼 API.
     */
    public void queryNearbyPlayers(
            L1Object origin,
            int radius,
            List<L1PcInstance> result) {

        result.clear();

        final int mapId = origin.getMapId();
        final int cellX = toCell(origin.getX());
        final int cellY = toCell(origin.getY());

        final boolean screenMode = radius < 0;
        final int effectiveRadius = screenMode ? SCREEN_RADIUS : radius;
        final int cellRange = getCellRange(effectiveRadius);

        for (int dx = -cellRange; dx <= cellRange; dx++) {
            for (int dy = -cellRange; dy <= cellRange; dy++) {
                final long key = packedKey(mapId, cellX + dx, cellY + dy);
                final CanonicalStripe stripe = canonicalStripe(key);
                final long stamp = stripe.lock.readLock();
                try {
                    final CellBucket cell = stripe.cells.get(key);
                    if (cell != null) {
                        cell.collectNearbyPlayers(origin, radius, screenMode, result);
                    }
                } finally {
                    stripe.lock.unlockRead(stamp);
                }
            }
        }
    }

    public List<L1PcInstance> queryScreenPlayers(L1Object origin) {
        return queryNearbyPlayers(origin, -1);
    }

    public void queryScreenPlayers(
            L1Object origin,
            List<L1PcInstance> result) {

        queryNearbyPlayers(origin, -1, result);
    }

    public boolean hasNearbyPlayer(L1Object origin, int radius) {
        final int mapId = origin.getMapId();
        final int cellX = toCell(origin.getX());
        final int cellY = toCell(origin.getY());

        if (radius == 0) {
            final long key = packedKey(mapId, cellX, cellY);
            final CanonicalStripe stripe = canonicalStripe(key);
            final long stamp = stripe.lock.readLock();
            try {
                final CellBucket cell = stripe.cells.get(key);
                return cell != null && cell.hasNearbyPlayer(origin, 0, false);
            } finally {
                stripe.lock.unlockRead(stamp);
            }
        }

        final boolean screenMode = radius < 0;
        final int effectiveRadius = screenMode ? SCREEN_RADIUS : radius;
        final int cellRange = getCellRange(effectiveRadius);

        for (int dx = -cellRange; dx <= cellRange; dx++) {
            for (int dy = -cellRange; dy <= cellRange; dy++) {
                final long key = packedKey(mapId, cellX + dx, cellY + dy);
                final CanonicalStripe stripe = canonicalStripe(key);
                final long stamp = stripe.lock.readLock();
                try {
                    final CellBucket cell = stripe.cells.get(key);
                    if (cell != null
                            && cell.hasNearbyPlayer(origin, radius, screenMode)) {
                        return true;
                    }
                } finally {
                    stripe.lock.unlockRead(stamp);
                }
            }
        }

        return false;
    }

    // Per-completion-thread scratch: same non-owning, non-reentrant return contract
    // as the old owner-local queryNearbyScratch, without a CIO shard binding.
    public List<L1Object> queryNearbyReusable(L1Object origin, int radius) {
        final ArrayList<L1Object> result = _nearbyScratch.get();
        queryNearby(origin, radius, result);
        return result;
    }
}
