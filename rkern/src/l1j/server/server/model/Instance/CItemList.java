package l1j.server.server.model.Instance;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;

import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.set.hash.TIntHashSet;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.CWorld;
import l1j.server.server.shared.GameRandom;
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
    private transient int nextInventoryOrder;
    private transient volatile List<L1ItemInstance> snapshot;

    public static final boolean AUDIT_PERSISTENCE = Boolean.getBoolean("rkern.inventory.auditDirty");
    private static final L1ItemInstance[] NO_CANDIDATES = new L1ItemInstance[0];
    private transient volatile PersistenceCandidates persistence;

    private static final class PersistenceCandidates {
        TIntObjectHashMap<L1ItemInstance> rows;
        long revision;

        void remove(int id, L1ItemInstance item) {
            if (rows == null || rows.get(id) != item) return;
            rows.remove(id);
            if (rows.isEmpty()) rows = null;
        }
    }

    public void enablePersistenceTracking() { persistence = new PersistenceCandidates(); }

    boolean tracksPersistence() { return persistence != null; }

    void notePersistenceObjectIdChange(L1ItemInstance item, int previousId) {
        assert Thread.holdsLock(this);
        if (persistence == null) return;
        persistence.remove(previousId, item);
        notePersistenceChange(item);
    }

    synchronized void notePersistenceChange(L1ItemInstance item) {
        final PersistenceCandidates candidates = persistence;
        if (candidates == null || item.inventoryLinkOwner != this) return;
        if (candidates.rows == null) candidates.rows = new TIntObjectHashMap<>();
        candidates.rows.put(item.getId(), item);
        candidates.revision++;
    }

    public synchronized long persistenceRevision() {
        return persistence == null ? 0 : persistence.revision;
    }

    public synchronized L1ItemInstance[] dirtyPersistenceCandidates() {
        if (persistence == null || persistence.rows == null) return NO_CANDIDATES;
        return persistence.rows.values(new L1ItemInstance[persistence.rows.size()]);
    }

    /** A clean image may retire only if no tracked mutation or SQL acknowledgement overlapped. */
    public synchronized boolean hasMissedPersistenceChange(L1ItemInstance item, int columns, long revision) {
        final PersistenceCandidates candidates = persistence;
        if (candidates == null || item.inventoryLinkOwner != this || revision != candidates.revision) return false;
        if (columns == 0 && !item.hasMutablePersistenceTimes()) {
            candidates.remove(item.getId(), item);
            return false;
        }
        final boolean missed = candidates.rows == null || candidates.rows.get(item.getId()) != item;
        if (missed) notePersistenceChange(item);
        return missed && columns != 0;
    }

    private static final int INDEX_MIN_SIZE = 8;
    private static final Object SMALL_LIST = new Object();
    private static final L1ItemInstance[] NO_ITEMS = new L1ItemInstance[0];
    private static final Comparator<L1ItemInstance> INVENTORY_ORDER =
            Comparator.comparingInt(item -> item.inventoryOrder);

    private enum ItemKey {
        CLASS(L1ItemInstance::getItemId),
        TEMPLATE(item -> item.getItem() == null ? item.getItemId() : item.getItem().getItemId()),
        DESCRIPTION(item -> item.getItem() == null ? 0 : item.getItem().getItemDescId()),
        BLESS(item -> item.getItem() == null ? -1 : item.getItem().getBless()),
        TYPE(item -> item.getItem() == null ? -1 : item.getItem().getType()),
        KIND(item -> item.getItem() == null ? -1 : item.getItem().getType2()),
        OBJECT(L1ItemInstance::getId);

        final ToIntFunction<L1ItemInstance> value;
        ItemKey(ToIntFunction<L1ItemInstance> value) { this.value = value; }
        int of(L1ItemInstance item) { return value.applyAsInt(item); }
    }

    private static final int INDEX_COUNT = ItemKey.values().length;

    // Lazily built under the existing list monitor. Small NPC or ground
    // inventories need no maps; unique keys store the row directly.
    private transient ItemIndex[] indexes;
    private transient boolean distinctTemplateClasses;
    private transient ArrayList<L1ItemInstance> equipped;
    private transient boolean equippedDirty;

    private static final class ItemIndex {
        private record Multiple(ArrayList<L1ItemInstance> rows) {}

        final ItemKey key;
        final TIntObjectHashMap<Object> buckets;

        ItemIndex(ItemKey key, int capacity) {
            this.key = key;
            buckets = new TIntObjectHashMap<>(switch (key) {
                case BLESS -> 1;
                case TYPE, KIND -> 8;
                default -> capacity;
            });
        }

        void add(L1ItemInstance item) {
            final int id = key.of(item);
            if (key == ItemKey.BLESS && id != 2) return;
            final Object previous = buckets.get(id);
            if (previous == null) {
                buckets.put(id, item);
            } else if (previous instanceof Multiple group) {
                insertInOrder(group.rows, item);
            } else {
                final var rows = new ArrayList<L1ItemInstance>(2);
                rows.add((L1ItemInstance) previous);
                insertInOrder(rows, item);
                buckets.put(id, new Multiple(rows));
            }
        }

        void remove(int id, L1ItemInstance item) {
            final Object previous = buckets.get(id);
            if (previous == item) {
                buckets.remove(id);
            } else if (previous instanceof Multiple group && group.rows.remove(item)
                    && group.rows.size() == 1) {
                buckets.put(id, group.rows.getFirst());
            }
        }

        static L1ItemInstance at(Object bucket, int index) {
            if (bucket instanceof Multiple group)
                return index < group.rows.size() ? group.rows.get(index) : null;
            return index == 0 ? (L1ItemInstance) bucket : null;
        }
    }

    private static void insertInOrder(ArrayList<L1ItemInstance> rows, L1ItemInstance item) {
        int i = rows.size();
        while (i != 0 && rows.get(i - 1).inventoryOrder > item.inventoryOrder) i--;
        rows.add(i, item);
    }

    private Object lookup(ItemKey key, int id) {
        ItemIndex index = indexes == null ? null : indexes[key.ordinal()];
        if (index == null) {
            if (size < INDEX_MIN_SIZE) return SMALL_LIST;
            index = new ItemIndex(key, size);
            for (L1ItemInstance item = first; item != null; item = item.inventoryNext)
                index.add(item);
            if (indexes == null) indexes = new ItemIndex[INDEX_COUNT];
            indexes[key.ordinal()] = index;
        }
        return index.buckets.get(id);
    }

    private L1ItemInstance candidate(Object rows, L1ItemInstance previous, int index) {
        return rows == SMALL_LIST
                ? (previous == null ? first : previous.inventoryNext) : ItemIndex.at(rows, index);
    }

    // The item setters take this monitor and recheck ownership before calling.
    void lookupKeyChanged(L1ItemInstance item, int previousClass, L1Item previousTemplate) {
        final int previousTemplateId = previousTemplate == null ? previousClass : previousTemplate.getItemId();
        final int previousDescription = previousTemplate == null ? 0 : previousTemplate.getItemDescId();
        updateIndex(ItemKey.CLASS, previousClass, item);
        updateIndex(ItemKey.TEMPLATE, previousTemplateId, item);
        updateIndex(ItemKey.DESCRIPTION, previousDescription, item);
        updateIndex(ItemKey.BLESS, previousTemplate == null ? -1 : previousTemplate.getBless(), item);
        updateIndex(ItemKey.TYPE, previousTemplate == null ? -1 : previousTemplate.getType(), item);
        updateIndex(ItemKey.KIND, previousTemplate == null ? -1 : previousTemplate.getType2(), item);
        distinctTemplateClasses |= ItemKey.CLASS.of(item) != ItemKey.TEMPLATE.of(item);
    }

    void objectIdChanged(L1ItemInstance item, int previousId) {
        updateIndex(ItemKey.OBJECT, previousId, item);
    }

    private void updateIndex(ItemKey key, int previous, L1ItemInstance item) {
        if (indexes == null || previous == key.of(item)) return;
        final ItemIndex index = indexes[key.ordinal()];
        if (index != null) {
            index.remove(previous, item);
            index.add(item);
        }
    }

    synchronized void invalidateLookupAfterDetachedChange() {
        indexes = null;
        distinctTemplateClasses = true;
        carryingWeight = -1L;
    }

    synchronized void invalidateObjectIdAfterDetachedChange() {
        if (indexes != null) indexes[ItemKey.OBJECT.ordinal()] = null;
    }

    synchronized void invalidateEquippedAfterDetachedChange() {
        equippedDirty = true;
    }

    void equippedChanged(L1ItemInstance item) {
        if (equippedDirty) return;
        if (item.isEquipped()) {
            if (equipped == null) equipped = new ArrayList<>(4);
            insertInOrder(equipped, item);
        } else if (equipped != null) {
            equipped.remove(item);
            if (equipped.isEmpty()) equipped = null;
        }
    }

    private ArrayList<L1ItemInstance> equippedRows() {
        if (equippedDirty) {
            equipped = null;
            equippedDirty = false;
            for (L1ItemInstance item = first; item != null; item = item.inventoryNext)
                if (item.isEquipped()) equippedChanged(item);
        }
        return equipped;
    }

    public synchronized L1ItemInstance[] GetEquippedItems() {
        final var rows = equippedRows();
        return rows == null ? NO_ITEMS : rows.toArray(L1ItemInstance[]::new);
    }

    public synchronized L1ItemInstance GetFirstEquippedByType(int type2, int type) {
        final var rows = equippedRows();
        if (rows == null) return null;
        for (int i = 0; i < rows.size(); i++) {
            final L1ItemInstance item = rows.get(i);
            if (item.getItem().getType2() == type2 && item.getItem().getType() == type) return item;
        }
        return null;
    }

    public synchronized L1ItemInstance[] GetEquippedByType(int type2, int type, int slots) {
        final L1ItemInstance[] result = new L1ItemInstance[slots];
        final var rows = equippedRows();
        if (rows == null) return result;
        int count = 0;
        for (int i = 0; i < rows.size() && count < slots; i++) {
            final L1ItemInstance item = rows.get(i);
            if (item.getItem().getType2() == type2 && item.getItem().getType() == type)
                result[count++] = item;
        }
        return result;
    }

    public synchronized int CountEquipped(int type2, int type, Integer grade) {
        final var rows = equippedRows();
        if (rows == null) return 0;
        int count = 0;
        for (int i = 0; i < rows.size(); i++) {
            final L1Item template = rows.get(i).getItem();
            if (template.getType2() == type2 && template.getType() == type
                    && (grade == null || template.getGrade() == grade)) count++;
        }
        return count;
    }

    public synchronized int CountEquippedByTemplate(int classId, int type2, int type, boolean matchType) {
        final var rows = equippedRows();
        if (rows == null) return 0;
        int count = 0;
        for (int i = 0; i < rows.size(); i++) {
            final L1Item template = rows.get(i).getItem();
            if (template.getItemId() == classId
                    && (!matchType || template.getType2() == type2 && template.getType() == type)) count++;
        }
        return count;
    }

    public synchronized boolean EnchantEquipped(int classId, int enchant) {
        final var rows = equippedRows();
        if (rows == null) return false;
        for (int i = 0; i < rows.size(); i++) {
            final L1ItemInstance item = rows.get(i);
            if (item.getItem().getItemId() == classId && item.getEnchantLevel() == enchant) return true;
        }
        return false;
    }

    public synchronized boolean HasAnyEquippedByClassID(int[] ids) {
        final var rows = equippedRows();
        if (rows == null) return false;
        for (int i = 0; i < rows.size(); i++) {
            final int classId = rows.get(i).getItemId();
            for (int id : ids) if (classId == id) return true;
        }
        return false;
    }

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
        // Rebase only on integer exhaustion, never on a preset request.
        if (nextInventoryOrder == Integer.MAX_VALUE) {
            nextInventoryOrder = 0;
            for (L1ItemInstance row = first; row != null; row = row.inventoryNext)
                row.inventoryOrder = nextInventoryOrder++;
        }
        item.inventoryOrder = nextInventoryOrder++;
        item.inventoryPrev = last;
        item.inventoryNext = null;
        if (last == null) first = item;
        else last.inventoryNext = item;
        last = item;
        size++;
        snapshot = null;
        carryingWeight = -1L;
        if (indexes != null)
            for (ItemIndex index : indexes)
                if (index != null) index.add(item);
        distinctTemplateClasses |= ItemKey.CLASS.of(item) != ItemKey.TEMPLATE.of(item);
        if (item.isEquipped()) equippedChanged(item);
        if (persistence != null) notePersistenceChange(item);
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
        if (indexes != null)
            for (ItemIndex index : indexes)
                if (index != null) index.remove(index.key.of(item), item);
        if (equipped != null && equipped.remove(item) && equipped.isEmpty()) equipped = null;
        if (size == 0) {
            indexes = null;
            distinctTemplateClasses = false;
        }
        if (persistence != null) {
            persistence.remove(item.getId(), item);
            persistence.revision++;
        }
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
        nextInventoryOrder = 0;
        snapshot = null;
        carryingWeight = -1L;
        indexes = null;
        distinctTemplateClasses = false;
        equipped = null;
        equippedDirty = false;
        if (persistence != null) {
            persistence.rows = null;
            persistence.revision++;
        }
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

    /** Registered online item only: no linked-list fallback on stale preset IDs. */
    public L1ItemInstance GetRegisteredItemByServerID(int objectId) {
        final L1Object object = CWorld.getInstance().findObject(objectId);
        return object instanceof L1ItemInstance item && item.getId() == objectId
                && Have(item) ? item : null;
    }

    /** Sort only the caller's selected rows, in the existing append/shuffle
     * order. The monitor protects membership and order, with no callbacks. */
    public synchronized boolean SortSelectedByInventoryOrder(L1ItemInstance[] selected, int count) {
        if (count < 0 || count > selected.length) return false;
        for (int i = 0; i < count; i++) {
            if (selected[i] == null || selected[i].inventoryLinkOwner != this) return false;
        }
        for (int i = 1; i < count; i++) {
            final L1ItemInstance item = selected[i];
            int j = i;
            while (j > 0 && selected[j - 1].inventoryOrder > item.inventoryOrder) {
                selected[j] = selected[j - 1];
                j--;
            }
            selected[j] = item;
        }
        return true;
    }

    public L1ItemInstance GetByServerID(int objectId) {
        // Published items already have a world registry index. Receipts and
        // detached loads may link their rows before publishing that registry.
        final L1ItemInstance registered = GetRegisteredItemByServerID(objectId);
        if (registered != null) return registered;
        synchronized (this) {
            return firstBy(ItemKey.OBJECT, objectId);
        }
    }

    private L1ItemInstance firstBy(ItemKey key, int id) {
        final Object rows = lookup(key, id);
        if (rows != SMALL_LIST) return ItemIndex.at(rows, 0);
        for (L1ItemInstance item = first; item != null; item = item.inventoryNext)
            if (key.of(item) == id) return item;
        return null;
    }

    public synchronized L1ItemInstance GetFirstItemByClassID(int classId) {
        return firstBy(distinctTemplateClasses ? ItemKey.TEMPLATE : ItemKey.CLASS, classId);
    }

    public synchronized L1ItemInstance GetFirstItemByClassIDs(int firstClassId, int secondClassId) {
        final L1ItemInstance a = firstBy(ItemKey.CLASS, firstClassId);
        final L1ItemInstance b = firstClassId == secondClassId ? a : firstBy(ItemKey.CLASS, secondClassId);
        if (a == null) return b;
        return b == null || a.inventoryOrder < b.inventoryOrder ? a : b;
    }

    public synchronized L1ItemInstance GetFirstItemByType(int type) {
        return firstBy(ItemKey.TYPE, type);
    }

    public synchronized L1ItemInstance GetFirstEtcByClassID(int classId) {
        final Object rows = lookup(ItemKey.CLASS, classId);
        int i = 0;
        for (L1ItemInstance item = candidate(rows, null, 0); item != null; item = candidate(rows, item, ++i))
            if (item.getItemId() == classId && item.getItem().getType2() == 0) return item;
        return null;
    }

    public synchronized int GetLastEnchantByClassID(int classId) {
        final Object rows = lookup(ItemKey.CLASS, classId);
        int enchant = 0, i = 0;
        for (L1ItemInstance item = candidate(rows, null, 0); item != null; item = candidate(rows, item, ++i))
            if (item.getItemId() == classId) enchant = item.getEnchantLevel();
        return enchant;
    }

    /** No snapshot or temporary collection for a compatible stack lookup. */
    public synchronized L1ItemInstance GetMergeableStack(L1ItemInstance incoming, boolean ground) {
        if (incoming == null) return null;
        final int classId = incoming.getItemId();
        final Object rows = lookup(ItemKey.CLASS, classId);
        int i = 0;
        for (L1ItemInstance item = candidate(rows, null, 0); item != null; item = candidate(rows, item, ++i)) {
            if (item != incoming && item.getItemId() == classId && item.getCount() > 0
                    && (!ground || !item.isGroundPickupPending()) && L1ItemInstance.CanMergeStack(item, incoming)
                    && (!ground || item.SameGroundLootRights(incoming))) return item;
        }
        return null;
    }

    /** Check the number of matching instances, not their accumulated quantity. */
    public synchronized boolean HasInstancesByClassID(int classId, int count) {
        if (count <= 0) return true;
        final Object rows = lookup(ItemKey.CLASS, classId);
        int i = 0;
        for (L1ItemInstance item = candidate(rows, null, 0); item != null; item = candidate(rows, item, ++i))
            if (item.getItemId() == classId && --count == 0) return true;
        return false;
    }

    /** Legacy non-stackable count: one per matching unequipped instance. */
    public synchronized int CountUnequippedInstancesByClassID(int classId) {
        final Object rows = lookup(ItemKey.CLASS, classId);
        int total = 0, i = 0;
        for (L1ItemInstance item = candidate(rows, null, 0); item != null; item = candidate(rows, item, ++i))
            if (item.getItemId() == classId && !item.isEquipped()) total++;
        return total;
    }

    public int GetItemCountByClassID(int classId) {
        return (int) Math.min(Integer.MAX_VALUE, GetItemAmount(classId));
    }

    /** OFF uses a 64-bit accumulator; preserve L1's quantity semantics. */
    public synchronized long GetItemAmount(int classId) {
        final Object rows = lookup(ItemKey.CLASS, classId);
        long total = 0;
        int i = 0;
        for (L1ItemInstance item = candidate(rows, null, 0); item != null; item = candidate(rows, item, ++i))
            if (item.getItemId() == classId) total += item.getCount();
        return total;
    }

    public boolean IsEquippedByClassID(int classId) {
        return GetFirstEquippedItemByClassID(classId) != null;
    }

    public synchronized L1ItemInstance GetFirstEquippedItemByClassID(int classId) {
        final Object rows = lookup(ItemKey.CLASS, classId);
        int i = 0;
        for (L1ItemInstance item = candidate(rows, null, 0); item != null; item = candidate(rows, item, ++i))
            if (item.getItemId() == classId && item.isEquipped()) return item;
        return null;
    }

    public synchronized L1ItemInstance GetFirstUnequippedItemByClassID(int classId) {
        final Object rows = lookup(ItemKey.CLASS, classId);
        int i = 0;
        for (L1ItemInstance item = candidate(rows, null, 0); item != null; item = candidate(rows, item, ++i))
            if (item.getItemId() == classId && !item.isEquipped() && item.getCount() > 0) return item;
        return null;
    }

    public synchronized L1ItemInstance GetFirstItemByDescID(int descId) {
        return firstBy(ItemKey.DESCRIPTION, descId);
    }

    public synchronized L1ItemInstance[] GetItemListByClassID(int classId) {
        return selectItems(ItemKey.CLASS, classId, false, 0, false);
    }

    public synchronized L1ItemInstance[] GetItemListByDescID(int descId) {
        return selectItems(ItemKey.DESCRIPTION, descId, false, 0, false);
    }

    public synchronized L1ItemInstance[] GetItemListByClassID(int classId, int descId) {
        return selectItems(ItemKey.CLASS, classId, true, descId, false);
    }

    public synchronized L1ItemInstance[] GetUnequippedItemListByClassID(int classId) {
        return selectItems(ItemKey.CLASS, classId, false, 0, true);
    }

    public synchronized L1ItemInstance[] GetUnequippedItemListByClassIDs(int[] classIds) {
        return selectKeys(ItemKey.CLASS, classIds, true);
    }

    public synchronized L1ItemInstance[] GetItemListByClassIDs(int[] classIds) {
        return selectKeys(ItemKey.CLASS, classIds, false);
    }

    public synchronized L1ItemInstance[] GetItemsByDescriptionIDs(int[] descIds) {
        return selectKeys(ItemKey.DESCRIPTION, descIds, false);
    }

    public synchronized L1ItemInstance[] GetItemsByObjectIDs(int[] objectIds) {
        return selectKeys(ItemKey.OBJECT, objectIds, false);
    }

    public synchronized L1ItemInstance[] GetItemsByType(int type) {
        return selectItems(ItemKey.TYPE, type, false, 0, false);
    }

    public synchronized int getLightRadius(int radius) {
        final int etcItemKind = 0;
        final int lightItemType = 2;
        final Object rows = lookup(ItemKey.TYPE, lightItemType);
        int index = 0;
        for (L1ItemInstance item = candidate(rows, null, 0); item != null; item = candidate(rows, item, ++index)) {
            final L1Item template = item.getItem();
            if (template == null || template.getType2() != etcItemKind
                    || template.getType() != lightItemType || !item.isNowLighting()) continue;
            final int itemRadius = template.getLightRange();
            if (itemRadius != 0) radius = Math.max(radius, itemRadius);
        }
        return radius;
    }

    public synchronized L1ItemInstance[] GetItemsByKinds(int... kinds) {
        return selectKeys(ItemKey.KIND, kinds, false);
    }

    public synchronized L1ItemInstance[] GetCursedTemplateItems() {
        return selectItems(ItemKey.BLESS, 2, false, 0, false);
    }

    private L1ItemInstance[] selectKeys(ItemKey key, int[] ids, boolean unequipped) {
        if (ids.length == 0 || size == 0) return NO_ITEMS;
        if (ids.length == 1) return selectItems(key, ids[0], false, 0, unequipped);
        final var seen = new TIntHashSet(ids.length);
        final var result = new ArrayList<L1ItemInstance>();
        for (int id : ids) {
            if (!seen.add(id)) continue;
            final Object rows = lookup(key, id);
            int i = 0;
            for (L1ItemInstance item = candidate(rows, null, 0); item != null; item = candidate(rows, item, ++i))
                if (key.of(item) == id && (!unequipped || !item.isEquipped())) result.add(item);
        }
        if (result.isEmpty()) return NO_ITEMS;
        result.sort(INVENTORY_ORDER);
        return result.toArray(L1ItemInstance[]::new);
    }

    /** Keep the original inventory order and evaluate mutable predicates once. */
    private L1ItemInstance[] selectItems(ItemKey key, int id, boolean matchDesc, int descId, boolean unequipped) {
        final Object rows = lookup(key, id);
        L1ItemInstance firstMatch = null;
        L1ItemInstance[] result = null;
        int count = 0, i = 0;
        for (L1ItemInstance item = candidate(rows, null, 0); item != null; item = candidate(rows, item, ++i)) {
            if (key.of(item) != id || (matchDesc && ItemKey.DESCRIPTION.of(item) != descId)
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
        final Object rows = lookup(ItemKey.CLASS, classId);
        long total = 0L;
        int i = 0;
        for (L1ItemInstance item = candidate(rows, null, 0); item != null; item = candidate(rows, item, ++i)) {
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
            final int j = GameRandom.nextInt(i + 1);
            final L1ItemInstance tmp = rows[i]; rows[i] = rows[j]; rows[j] = tmp;
        }
        for (int i = 0; i < rows.length; i++) {
            rows[i].inventoryOrder = i;
            rows[i].inventoryPrev = i == 0 ? null : rows[i - 1];
            rows[i].inventoryNext = i == rows.length - 1 ? null : rows[i + 1];
        }
        first = rows[0]; last = rows[rows.length - 1]; snapshot = null;
        nextInventoryOrder = rows.length;
        indexes = null;
        if (equipped != null) equipped.sort(INVENTORY_ORDER);
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
