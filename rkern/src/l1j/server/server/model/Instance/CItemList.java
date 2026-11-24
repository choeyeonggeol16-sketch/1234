package l1j.server.server.model.Instance;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.Spliterator;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;

/** Intrusive, ordered inventory membership. The list monitor protects only links;
 * never invoke persistence, packets, or callers under it. Existing owner locks
 * still protect item counts, transactions and game state. Snapshot traversal
 * preserves legacy iteration across transfers/removals without copying on writes.
 */
public final class CItemList extends AbstractList<L1ItemInstance> implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final VarHandle OWNER;
    static {
        try {
            OWNER = MethodHandles.lookup().findVarHandle(L1ItemInstance.class, "inventoryLinkOwner", CItemList.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
    private transient L1ItemInstance first, last;
    private transient int size;
    private transient volatile List<L1ItemInstance> snapshot;

    private void claim(L1ItemInstance item) {
        Objects.requireNonNull(item, "inventory item");
        if (!OWNER.compareAndSet(item, null, this))
            throw new IllegalStateException("Item already linked: " + item.getId());
    }

    private void appendClaimed(L1ItemInstance item) {
        item.inventoryPrev = last;
        item.inventoryNext = null;
        if (last == null) first = item;
        else last.inventoryNext = item;
        last = item;
        size++;
        snapshot = null;
    }

    /** Reject a still-owned item before any caller changes counts or persistence. */
    public static void requireDetached(L1ItemInstance item) {
        Objects.requireNonNull(item, "inventory item");
        if (item.inventoryLinkOwner != null)
            throw new IllegalStateException("Item already linked: " + item.getId());
    }

    /** Reconnect snapshots contain new, detached objects. Claim ALL before
     * changing old membership, so a rejected row cannot empty the container. */
    public List<L1ItemInstance> replaceDetached(Collection<? extends L1ItemInstance> items) {
        final L1ItemInstance[] rows = items.toArray(new L1ItemInstance[0]);
        synchronized (this) {
            final List<L1ItemInstance> previous = snapshotLocked();
            int claimed = 0;
            try {
                for (; claimed < rows.length; claimed++) claim(rows[claimed]);
            } catch (RuntimeException | Error e) {
                while (claimed != 0) OWNER.setVolatile(rows[--claimed], null);
                throw e;
            }
            clearLocked();
            for (L1ItemInstance item : rows) appendClaimed(item);
            return previous;
        }
    }

    @Override public synchronized boolean add(L1ItemInstance item) {
        claim(item);
        appendClaimed(item);
        return true;
    }

    @Override public boolean addAll(Collection<? extends L1ItemInstance> items) {
        // Snapshot input before taking the monitor: no reciprocal list locks.
        final L1ItemInstance[] rows = items.toArray(new L1ItemInstance[0]);
        if (rows.length == 0) return false;
        synchronized (this) {
            int claimed = 0;
            try {
                for (; claimed < rows.length; claimed++) claim(rows[claimed]);
            } catch (RuntimeException | Error e) {
                while (claimed != 0) OWNER.setVolatile(rows[--claimed], null);
                throw e;
            }
            for (L1ItemInstance item : rows) appendClaimed(item);
            return true;
        }
    }

    private void unlink(L1ItemInstance item) {
        final L1ItemInstance prev = item.inventoryPrev, next = item.inventoryNext;
        if (prev == null) first = next;
        else prev.inventoryNext = next;
        if (next == null) last = prev;
        else next.inventoryPrev = prev;
        item.inventoryPrev = null;
        item.inventoryNext = null;
        size--;
        snapshot = null;
        OWNER.setVolatile(item, null);
    }

    @Override public synchronized boolean remove(Object value) {
        if (!(value instanceof L1ItemInstance item) || item.inventoryLinkOwner != this) return false;
        unlink(item);
        return true;
    }

    @Override public synchronized L1ItemInstance remove(int index) {
        final L1ItemInstance item = getLocked(index);
        unlink(item);
        return item;
    }

    @Override public synchronized void clear() { clearLocked(); }

    private void clearLocked() {
        L1ItemInstance item = first;
        first = last = null;
        size = 0;
        snapshot = null;
        while (item != null) {
            final L1ItemInstance next = item.inventoryNext;
            item.inventoryPrev = null;
            item.inventoryNext = null;
            OWNER.setVolatile(item, null);
            item = next;
        }
    }

    @Override public int size() {
        final List<L1ItemInstance> rows = snapshot;
        if (rows != null) return rows.size();
        synchronized (this) { return size; }
    }

    @Override public synchronized boolean contains(Object value) {
        return value instanceof L1ItemInstance item && item.inventoryLinkOwner == this;
    }

    @Override public synchronized int indexOf(Object value) {
        if (!(value instanceof L1ItemInstance owned) || owned.inventoryLinkOwner != this) return -1;
        int index = 0;
        for (L1ItemInstance item = first; item != null; item = item.inventoryNext, index++)
            if (item == value) return index;
        return -1;
    }

    @Override public int lastIndexOf(Object value) { return indexOf(value); }

    @Override public L1ItemInstance get(int index) {
        final List<L1ItemInstance> rows = snapshot;
        if (rows != null) return rows.get(index);
        synchronized (this) { return getLocked(index); }
    }

    private L1ItemInstance getLocked(int index) {
        Objects.checkIndex(index, size);
        // Do not build a full snapshot for a single positional lookup.
        final List<L1ItemInstance> rows = snapshot;
        if (rows != null) return rows.get(index);
        if (index < (size >>> 1)) {
            L1ItemInstance item = first;
            for (int i = 0; i < index; i++) item = item.inventoryNext;
            return item;
        }
        L1ItemInstance item = last;
        for (int i = size - 1; i > index; i--) item = item.inventoryPrev;
        return item;
    }

    public L1ItemInstance GetByIndex(int objectId) {
        // World registration can lag list publication during receipt replay.
        // Use the global index for registered objects, but the list remains
        // authoritative if registration has not happened yet.
        final L1Object object = L1World.getInstance().findObject(objectId);
        if (object instanceof L1ItemInstance item
                && item.getId() == objectId && contains(item)) return item;
        final List<L1ItemInstance> rows = snapshot;
        if (rows != null) {
            for (int i = 0, n = rows.size(); i < n; i++) {
                final L1ItemInstance item = rows.get(i);
                if (item.getId() == objectId) return item;
            }
            return null;
        }
        synchronized (this) {
            for (L1ItemInstance item = first; item != null; item = item.inventoryNext)
                if (item.getId() == objectId) return item;
            return null;
        }
    }

    public L1ItemInstance GetFirstItemByClassID(int classId) {
        final List<L1ItemInstance> rows = snapshot;
        if (rows != null) {
            for (int i = 0, n = rows.size(); i < n; i++) {
                final L1ItemInstance item = rows.get(i);
                if (item.getItem().getItemId() == classId) return item;
            }
            return null;
        }
        synchronized (this) {
            for (L1ItemInstance item = first; item != null; item = item.inventoryNext)
                if (item.getItem().getItemId() == classId) return item;
            return null;
        }
    }

    /** Check the number of matching instances, not their accumulated quantity. */
    public boolean HasInstancesByClassID(int classId, int count) {
        if (count <= 0) return true;
        final List<L1ItemInstance> rows = snapshot;
        if (rows != null) {
            for (int i = 0, n = rows.size(); i < n; i++)
                if (rows.get(i).getItemId() == classId && --count == 0) return true;
            return false;
        }
        synchronized (this) {
            for (L1ItemInstance item = first; item != null; item = item.inventoryNext)
                if (item.getItemId() == classId && --count == 0) return true;
            return false;
        }
    }

    public int GetItemCountByClassID(int classId) {
        int total = 0;
        final List<L1ItemInstance> rows = snapshot;
        if (rows != null) {
            for (int i = 0, n = rows.size(); i < n; i++) {
                final L1ItemInstance item = rows.get(i);
                if (item.getItemId() == classId) total += item.getCount();
            }
            return total;
        }
        synchronized (this) {
            for (L1ItemInstance item = first; item != null; item = item.inventoryNext)
                if (item.getItemId() == classId) total += item.getCount();
            return total;
        }
    }

    public boolean IsEquippedByClassID(int classId) {
        final List<L1ItemInstance> rows = snapshot;
        if (rows != null) {
            for (int i = 0, n = rows.size(); i < n; i++) {
                final L1ItemInstance item = rows.get(i);
                if (item.getItemId() == classId && item.isEquipped()) return true;
            }
            return false;
        }
        synchronized (this) {
            for (L1ItemInstance item = first; item != null; item = item.inventoryNext)
                if (item.getItemId() == classId && item.isEquipped()) return true;
            return false;
        }
    }

    private List<L1ItemInstance> snapshotLocked() {
        if (snapshot == null) {
            if (size == 0) return snapshot = Collections.emptyList();
            final L1ItemInstance[] rows = new L1ItemInstance[size];
            int index = 0;
            for (L1ItemInstance item = first; item != null; item = item.inventoryNext)
                rows[index++] = item;
            snapshot = Collections.unmodifiableList(Arrays.asList(rows));
        }
        return snapshot;
    }

    /** Immutable membership view; item state itself is not frozen. */
    public List<L1ItemInstance> snapshot() {
        // Immutable membership snapshot: a volatile load safely publishes
        // the cached rows. A racing writer invalidates under its lock.
        final List<L1ItemInstance> rows = snapshot;
        if (rows != null) return rows;
        synchronized (this) { return snapshotLocked(); }
    }
    @Override public Iterator<L1ItemInstance> iterator() { return snapshot().iterator(); }
    @Override public ListIterator<L1ItemInstance> listIterator(int index) { return snapshot().listIterator(index); }
    @Override public Spliterator<L1ItemInstance> spliterator() { return snapshot().spliterator(); }
    @Override public void forEach(Consumer<? super L1ItemInstance> action) { snapshot().forEach(action); }
    @Override public Object[] toArray() { return snapshot().toArray(); }
    @Override public <T> T[] toArray(T[] target) { return snapshot().toArray(target); }
    @Override public List<L1ItemInstance> subList(int from, int to) { return snapshot().subList(from, to); }

    /** Legacy random order operation; do not use List.set to duplicate links. */
    public synchronized void shuffle() {
        if (size < 2) return;
        final L1ItemInstance[] rows = snapshotLocked().toArray(new L1ItemInstance[size]);
        for (int i = rows.length - 1; i > 0; i--) {
            final int j = ThreadLocalRandom.current().nextInt(i + 1);
            final L1ItemInstance tmp = rows[i]; rows[i] = rows[j]; rows[j] = tmp;
        }
        for (int i = 0; i < rows.length; i++) {
            rows[i].inventoryPrev = i == 0 ? null : rows[i - 1];
            rows[i].inventoryNext = i == rows.length - 1 ? null : rows[i + 1];
        }
        first = rows[0]; last = rows[rows.length - 1]; snapshot = null;
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        final List<L1ItemInstance> rows = snapshot();
        out.writeInt(rows.size());
        for (L1ItemInstance item : rows) out.writeObject(item);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        final int count = in.readInt();
        if (count < 0) throw new IOException("Negative inventory size");
        for (int i = 0; i < count; i++) add((L1ItemInstance) in.readObject());
    }
}
