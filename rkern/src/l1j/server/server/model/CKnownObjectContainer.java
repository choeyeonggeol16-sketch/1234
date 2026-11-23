package l1j.server.server.model;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import l1j.server.server.model.Instance.L1PcInstance;
public final class CKnownObjectContainer {
    private static final L1Object[] NO_OBJECTS = new L1Object[0];
    private static final L1PcInstance[] NO_PLAYERS = new L1PcInstance[0];
    private static final int INITIAL_CAPACITY = 16;
    public static final CKnownObjectContainer CLOSED = new CKnownObjectContainer(true);
    private int[] hashes;
    private L1Object[] table;
    private int[] playerPositions;
    private L1PcInstance[] players;
    private int count;
    private int playerCount;
    private boolean closed;
    private L1Object[] objectSnapshot = NO_OBJECTS;
    private L1PcInstance[] playerSnapshot = NO_PLAYERS;
    private boolean objectsDirty;
    private boolean playersDirty;
    private Set<L1Object> objectsView;
    private Set<L1PcInstance> playersView;

    // Shared by all characters: one scratch chain per thread, not per PC.
    private static final int MAX_RETAINED_SCAN = 2048;
    private static final class ScanScratch {
        @SuppressWarnings("unchecked")
        ArrayList<L1Object>[] objects = (ArrayList<L1Object>[]) new ArrayList<?>[4];
        @SuppressWarnings("unchecked")
        ArrayList<L1PcInstance>[] players = (ArrayList<L1PcInstance>[]) new ArrayList<?>[4];
        int objectDepth;
        int playerDepth;
    }
    private static final ThreadLocal<ScanScratch> SCAN = ThreadLocal.withInitial(ScanScratch::new);

    /**
     * Stable reentrant snapshot without per-movement array allocation.
     * Release in finally, on the same thread, before returning from the scan.
     * Callbacks are never executed while holding this index's monitor.
     */
    public List<L1Object> borrowObjects() {
        final ScanScratch scratch = SCAN.get();
        final int depth = scratch.objectDepth;
        if (depth == scratch.objects.length)
            scratch.objects = Arrays.copyOf(scratch.objects, depth << 1);
        ArrayList<L1Object> result = scratch.objects[depth];
        if (result == null) scratch.objects[depth] = result = new ArrayList<>(64);
        scratch.objectDepth = depth + 1;
        try {
            synchronized (this) {
                result.ensureCapacity(count);
                if (table != null)
                    for (L1Object obj : table) if (obj != null) result.add(obj);
            }
            return result;
        } catch (RuntimeException | Error failure) {
            result.clear();
            scratch.objectDepth = depth;
            throw failure;
        }
    }

    public static void releaseObjects(List<L1Object> result) {
        final ScanScratch scratch = SCAN.get();
        final int depth = scratch.objectDepth - 1;
        if (depth < 0 || scratch.objects[depth] != result)
            throw new IllegalStateException("Known-object scan released out of order");
        scratch.objectDepth = depth;
        if (result.size() > MAX_RETAINED_SCAN) scratch.objects[depth] = null;
        result.clear(); // No object references retained by a reused buffer.
    }

    public List<L1PcInstance> borrowPlayers() {
        final ScanScratch scratch = SCAN.get();
        final int depth = scratch.playerDepth;
        if (depth == scratch.players.length)
            scratch.players = Arrays.copyOf(scratch.players, depth << 1);
        ArrayList<L1PcInstance> result = scratch.players[depth];
        if (result == null) scratch.players[depth] = result = new ArrayList<>(16);
        scratch.playerDepth = depth + 1;
        try {
            synchronized (this) {
                result.ensureCapacity(playerCount);
                if (players != null)
                    for (int i = 0; i < playerCount; i++) result.add(players[i]);
            }
            return result;
        } catch (RuntimeException | Error failure) {
            result.clear();
            scratch.playerDepth = depth;
            throw failure;
        }
    }

    public static void releasePlayers(List<L1PcInstance> result) {
        final ScanScratch scratch = SCAN.get();
        final int depth = scratch.playerDepth - 1;
        if (depth < 0 || scratch.players[depth] != result)
            throw new IllegalStateException("Known-player scan released out of order");
        scratch.playerDepth = depth;
        if (result.size() > MAX_RETAINED_SCAN) scratch.players[depth] = null;
        result.clear();
    }
    public CKnownObjectContainer() {
        this(false);
    }
private CKnownObjectContainer(boolean closed) {
    this.closed = closed;
}
private static int spread(int hash) {
    hash ^= hash >>> 16;
    hash *= 0x7feb352d;
    hash ^= hash >>> 15;
    hash *= 0x846ca68b;
    return hash ^ (hash >>> 16);
}
private void init() {
    if (table == null) {
        hashes = new int[INITIAL_CAPACITY];
        table = new L1Object[INITIAL_CAPACITY];
    }
}
private int locate(L1Object obj, int hash) {
    if (table == null) return -1;
    final int mask = table.length - 1;
    int slot = spread(hash) & mask;
    while (table[slot] != null) {
        if (hashes[slot] == hash && table[slot] == obj) return slot;
        slot = (slot + 1) & mask;
    }
return -1;
}
public synchronized boolean contains(L1Object obj) {
    return obj != null && locate(obj, System.identityHashCode(obj)) >= 0;
}
public synchronized int size() {
    return count;
}
public synchronized int playerSize() {
    return playerCount;
}
public synchronized boolean add(L1Object obj) {
    if (closed || obj == null) return false;
    init();
    final int hash = System.identityHashCode(obj);
    if (locate(obj, hash) >= 0) return false;
    if ((count + 1) * 3 >= table.length * 2) resize(table.length << 1);
    final int mask = table.length - 1;
    int slot = spread(hash) & mask;
    while (table[slot] != null) slot = (slot + 1) & mask;
    table[slot] = obj;
    hashes[slot] = hash;
    ++count;
    objectSnapshot = NO_OBJECTS;
    objectsDirty = true;
    if (obj instanceof L1PcInstance pc) {
        if (players == null) {
            players = new L1PcInstance[4];
            playerPositions = new int[table.length];
        } else if (playerCount == players.length) {
        players = Arrays.copyOf(players, players.length << 1);
    }
players[playerCount] = pc;
playerPositions[slot] = ++playerCount;
playerSnapshot = NO_PLAYERS;
playersDirty = true;
}
return true;
}
private void resize(int capacity) {
    final L1Object[] old = table;
    final int[] oldHashes = hashes;
    final int[] oldPositions = playerPositions;
    table = new L1Object[capacity];
    hashes = new int[capacity];
    playerPositions = oldPositions == null ? null : new int[capacity];
    final int mask = capacity - 1;
    for (int i = 0; i < old.length; i++) {
        final L1Object obj = old[i];
        if (obj == null) continue;
        final int hash = oldHashes[i];
        int slot = spread(hash) & mask;
        while (table[slot] != null) slot = (slot + 1) & mask;
        table[slot] = obj;
        hashes[slot] = hash;
        if (playerPositions != null) playerPositions[slot] = oldPositions[i];
    }
}
public synchronized boolean remove(L1Object obj) {
    if (closed || obj == null) return false;
    final int slot = locate(obj, System.identityHashCode(obj));
    if (slot < 0) return false;
    if (obj instanceof L1PcInstance) {
        final int index = playerPositions[slot] - 1;
        final int last = --playerCount;
        final L1PcInstance swapped = players[last];
        players[index] = swapped;
        players[last] = null;
        if (index != last) {
            final int swappedSlot = locate(swapped, System.identityHashCode(swapped));
            playerPositions[swappedSlot] = index + 1;
        }
    playerSnapshot = NO_PLAYERS;
    playersDirty = true;
}
final int mask = table.length - 1;
table[slot] = null;
hashes[slot] = 0;
if (playerPositions != null) playerPositions[slot] = 0;
int scan = (slot + 1) & mask;
while (table[scan] != null) {
    final L1Object moved = table[scan];
    final int hash = hashes[scan];
    final int playerIndex = playerPositions == null ? 0 : playerPositions[scan];
    table[scan] = null;
    hashes[scan] = 0;
    if (playerPositions != null) playerPositions[scan] = 0;
    int destination = spread(hash) & mask;
    while (table[destination] != null) destination = (destination + 1) & mask;
    table[destination] = moved;
    hashes[destination] = hash;
    if (playerPositions != null) playerPositions[destination] = playerIndex;
    scan = (scan + 1) & mask;
}
--count;
objectSnapshot = NO_OBJECTS;
objectsDirty = true;
if (count == 0) {
    hashes = null;
    table = null;
    playerPositions = null;
    players = null;
} else if (table.length > 64 && count < (table.length >>> 3)) {
resize(table.length >>> 1);
}
return true;
}
public synchronized void clear() {
    if (closed) return;
    hashes = null;
    table = null;
    playerPositions = null;
    players = null;
    count = 0;
    playerCount = 0;
    objectSnapshot = NO_OBJECTS;
    playerSnapshot = NO_PLAYERS;
    objectsDirty = false;
    playersDirty = false;
}
public synchronized void close() {
    if (!closed) {
        clear();
        closed = true;
    }
}
public synchronized L1Object[] objectsSnapshot() {
    if (!objectsDirty) return objectSnapshot;
    final L1Object[] snapshot = new L1Object[count];
    int n = 0;
    if (table != null) {
        for (L1Object obj : table) if (obj != null) snapshot[n++] = obj;
    }
objectSnapshot = snapshot;
objectsDirty = false;
return snapshot;
}
public synchronized L1PcInstance[] playersSnapshot() {
    if (!playersDirty) return playerSnapshot;
    playerSnapshot = playerCount == 0 ? NO_PLAYERS : Arrays.copyOf(players, playerCount);
    playersDirty = false;
    return playerSnapshot;
}
public synchronized Set<L1Object> objectsView() {
    if (objectsView == null) objectsView = new View<>(false);
    return objectsView;
}
public synchronized Set<L1PcInstance> playersView() {
    if (playersView == null) playersView = new View<>(true);
    return playersView;
}
private final class View<T extends L1Object> extends AbstractSet<T> {
    private final boolean playersOnly;
    View(boolean playersOnly) {
        this.playersOnly = playersOnly;
    }
@Override public int size() { return playersOnly ? playerSize() : CKnownObjectContainer.this.size(); }
@Override public boolean contains(Object obj) {
    if (!(obj instanceof L1Object)) return false;
    return (!playersOnly || obj instanceof L1PcInstance)
    && CKnownObjectContainer.this.contains((L1Object) obj);
}
@Override public boolean add(T obj) {
    return (!playersOnly || obj instanceof L1PcInstance) && CKnownObjectContainer.this.add(obj);
}
@Override public boolean remove(Object obj) {
    return contains(obj) && CKnownObjectContainer.this.remove((L1Object) obj);
}
@Override public void clear() {
    if (!playersOnly) {
        CKnownObjectContainer.this.clear();
        return;
    }
for (L1PcInstance pc : playersSnapshot()) CKnownObjectContainer.this.remove(pc);
}
@Override public Iterator<T> iterator() {
    final L1Object[] snapshot = playersOnly ? playersSnapshot() : objectsSnapshot();
    return new Iterator<>() {
        private int cursor;
        private L1Object previous;
        @Override public boolean hasNext() { return cursor < snapshot.length; }
        @Override @SuppressWarnings("unchecked") public T next() {
            if (!hasNext()) throw new NoSuchElementException();
            return (T) (previous = snapshot[cursor++]);
        }
    @Override public void remove() {
        if (previous == null) throw new IllegalStateException();
        CKnownObjectContainer.this.remove(previous);
        previous = null;
    }
};
}
}
}
