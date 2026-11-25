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
import l1j.server.server.templates.L1Item;

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

    // OFF CInventory::GetCarryingWeight caches the aggregate. Store the raw
    // L1 per-row rounded sum, not the rate-adjusted value. Negative means dirty.
    private transient volatile long carryingWeight;
    private transient volatile long weightEpoch;

    public long GetCarryingWeight() {
        final long epoch = L1Item.WeightEpoch();
        // Read the publication stamp before its value.
        final long cachedEpoch = weightEpoch;
        final long cached = carryingWeight;
        if (cached >= 0 && cachedEpoch == epoch) return cached;
        return refreshCarryingWeight(false);
    }

    /** Explicit OFF-style validation; rebuild even if the cached value is clean. */
    public long ValidateCarringWeight() { return refreshCarryingWeight(true); }

    /** Rebuild on membership or template-generation changes; no snapshot. */
    private synchronized long refreshCarryingWeight(boolean force) {
        for (;;) {
            final long epoch = L1Item.WeightEpoch();
            if (!force && carryingWeight >= 0 && weightEpoch == epoch) return carryingWeight;
            long sum = 0;
            for (L1ItemInstance item = first; item != null; item = item.inventoryNext)
                sum += item.getWeight();
            if (L1Item.WeightEpoch() != epoch) continue;
            carryingWeight = sum;
            weightEpoch = epoch;
            return sum;
        }
    }

    // Called with this list's existing monitor held by the item setter.
    int weightBeforeChange(L1ItemInstance item) {
        assert Thread.holdsLock(this);
        return carryingWeight >= 0 && weightEpoch == L1Item.WeightEpoch()
                ? item.getWeight() : -1;
    }

    void weightChanged(L1ItemInstance item, int before) {
        assert Thread.holdsLock(this);
        if (before >= 0 && carryingWeight >= 0 && weightEpoch == L1Item.WeightEpoch())
            carryingWeight += (long) item.getWeight() - before;
        else carryingWeight = -1L;
    }

    // Defensive publication race: a formerly detached setter must invalidate
    // a newly linked destination, without acquiring any other list monitor.
    synchronized void invalidateCarryingWeight() { carryingWeight = -1L; }

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
        carryingWeight = -1L;
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

    @Override public boolean add(L1ItemInstance item) { return Push(item); }

    // OFF CInventory::Push; keep L1's existing append/selection order.
    public synchronized boolean Push(L1ItemInstance item) {
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
        carryingWeight = -1L;
        OWNER.setVolatile(item, null);
    }

    @Override public boolean remove(Object value) {
        return value instanceof L1ItemInstance item && Pop(item);
    }

    public synchronized boolean Pop(L1ItemInstance item) {
        if (item == null || item.inventoryLinkOwner != this) return false;
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
        carryingWeight = -1L;
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

    @Override public boolean contains(Object value) {
        return value instanceof L1ItemInstance item && Have(item);
    }

    public synchronized boolean Have(L1ItemInstance item) {
        return item != null && item.inventoryLinkOwner == this;
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

    public L1ItemInstance GetByServerID(int objectId) {
        // World registration can lag list publication during receipt replay.
        // Use the global index for registered objects, but the list remains
        // authoritative if registration has not happened yet.
        final L1Object object = L1World.getInstance().findObject(objectId);
        if (object instanceof L1ItemInstance item
                && item.getId() == objectId && Have(item)) return item;
        // Identity lookup is used by mutation paths. Unlike snapshot iteration,
        // it must not accept a detached row from a racing old snapshot.
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

    /** No snapshot or temporary collection for a compatible stack lookup. */
    public synchronized L1ItemInstance GetMergeableStack(L1ItemInstance incoming, boolean ground) {
        for (L1ItemInstance item = first; item != null; item = item.inventoryNext) {
            if (item != incoming && item.getCount() > 0 && (!ground || !item.isGroundPickupPending())
                    && L1ItemInstance.CanMergeStack(item, incoming)
                    && (!ground || item.SameGroundLootRights(incoming))) return item;
        }
        return null;
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

    /** Legacy non-stackable count: one per matching unequipped instance. */
    public int CountUnequippedInstancesByClassID(int classId) {
        int total = 0;
        final List<L1ItemInstance> rows = snapshot;
        if (rows != null) {
            for (int i = 0, n = rows.size(); i < n; i++) {
                final L1ItemInstance item = rows.get(i);
                if (item.getItemId() == classId && !item.isEquipped()) total++;
            }
            return total;
        }
        synchronized (this) {
            for (L1ItemInstance item = first; item != null; item = item.inventoryNext)
                if (item.getItemId() == classId && !item.isEquipped()) total++;
            return total;
        }
    }

    public int GetItemCountByClassID(int classId) {
        return (int) Math.min(Integer.MAX_VALUE, GetItemAmount(classId));
    }

    /** OFF uses a 64-bit accumulator; preserve L1's quantity semantics. */
    public long GetItemAmount(int classId) {
        long total = 0;
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
        return GetFirstEquippedItemByClassID(classId) != null;
    }

    public L1ItemInstance GetFirstEquippedItemByClassID(int classId) {
        final List<L1ItemInstance> rows = snapshot;
        if (rows != null) {
            for (int i = 0, n = rows.size(); i < n; i++) {
                final L1ItemInstance item = rows.get(i);
                if (item.getItemId() == classId && item.isEquipped()) return item;
            }
            return null;
        }
        synchronized (this) {
            for (L1ItemInstance item = first; item != null; item = item.inventoryNext)
                if (item.getItemId() == classId && item.isEquipped()) return item;
            return null;
        }
    }

    private static final L1ItemInstance[] NO_ITEMS = new L1ItemInstance[0];

    public synchronized L1ItemInstance GetFirstItemByDescID(int descId) {
        for (L1ItemInstance item = first; item != null; item = item.inventoryNext)
            if (item.getItem().getItemDescId() == descId) return item;
        return null;
    }

    public L1ItemInstance[] GetItemListByClassID(int classId) {
        return SelectItems(classId, 0, true, false, false);
    }

    public L1ItemInstance[] GetItemListByDescID(int descId) {
        return SelectItems(0, descId, false, true, false);
    }

    public L1ItemInstance[] GetItemListByClassID(int classId, int descId) {
        return SelectItems(classId, descId, true, true, false);
    }

    public L1ItemInstance[] GetUnequippedItemListByClassID(int classId) {
        return SelectItems(classId, 0, true, false, true);
    }

    /** OFF-style linked selection, preserving append order. No iterator,
     * snapshot construction or ArrayList; a single match needs one result array.
     * Evaluate mutable predicates only once, not in a count/fill double pass.
     */
    private synchronized L1ItemInstance[] SelectItems(int classId, int descId,
            boolean matchClass, boolean matchDesc, boolean unequipped) {
        L1ItemInstance firstMatch = null;
        L1ItemInstance[] result = null;
        int count = 0;
        for (L1ItemInstance item = first; item != null; item = item.inventoryNext) {
            if ((matchClass && item.getItemId() != classId)
                    || (matchDesc && item.getItem().getItemDescId() != descId)
                    || (unequipped && item.isEquipped())) continue;
            if (count == 0) {
                firstMatch = item;
            } else {
                if (result == null) {
                    result = new L1ItemInstance[Math.min(size, 4)];
                    result[0] = firstMatch;
                } else if (count == result.length) {
                    result = Arrays.copyOf(result, (int) Math.min((long) size, 2L * count));
                }
                result[count] = item;
            }
            count++;
        }
        if (count == 0) return NO_ITEMS;
        if (count == 1) return new L1ItemInstance[] { firstMatch };
        return count == result.length ? result : Arrays.copyOf(result, count);
    }

    /** Recipe quantity query uses the existing list monitor and no temporary rows. */
    public synchronized long GetRecipeMaterialAmount(int classId, int enchant,
            boolean matchEnchant, boolean countInstances) {
        long total = 0L;
        for (L1ItemInstance item = first; item != null; item = item.inventoryNext) {
            if (item.isEquipped() || item.getItemId() != classId
                    || (matchEnchant && item.getEnchantLevel() != enchant)) continue;
            final int count = item.getCount();
            if (count > 0) total += countInstances && !item.isStackable() ? 1 : count;
        }
        return total;
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
