package l1j.server.server.storage.cached;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntIterator;

/**
 * Item ownership/dirty indexes for the existing L1J inventories and warehouses.
 * These are CacheD persistence indexes, not replicated item or world objects.
 * All access is serialized by CacheD's existing lock.
 */
public final class CWareHouse {
    public final Int2IntOpenHashMap itemOwners = new Int2IntOpenHashMap();
    public final Int2ObjectOpenHashMap<IntOpenHashSet> pendingItemIds = new Int2ObjectOpenHashMap<>();
    public final IntOpenHashSet releasingOwnerIds = new IntOpenHashSet();
    private final Int2ObjectOpenHashMap<IntOpenHashSet> ownerItemIds = new Int2ObjectOpenHashMap<>();

    private static void addId(Int2ObjectOpenHashMap<IntOpenHashSet> index, int ownerId, int itemId) {
        IntOpenHashSet ids = index.get(ownerId);
        if (ids == null) {
            ids = new IntOpenHashSet();
            index.put(ownerId, ids);
        }
        ids.add(itemId);
    }

    public void addPendingId(int ownerId, int itemId) {
        addId(pendingItemIds, ownerId, itemId);
    }

    public void removePendingId(int ownerId, int itemId) {
        removeId(pendingItemIds, ownerId, itemId);
    }

    private static void removeId(Int2ObjectOpenHashMap<IntOpenHashSet> index, int ownerId, int itemId) {
        IntOpenHashSet ids = index.get(ownerId);
        if (ids == null) return;
        ids.remove(itemId);
        if (ids.isEmpty()) index.remove(ownerId);
    }

    public void trackItemOwner(int itemId, int ownerId) {
        int previous = itemOwners.get(itemId);
        if (previous == ownerId) return;
        if (previous > 0) removeId(ownerItemIds, previous, itemId);
        if (ownerId > 0) {
            itemOwners.put(itemId, ownerId);
            addId(ownerItemIds, ownerId, itemId);
        } else {
            itemOwners.remove(itemId);
        }
    }

    public void clearCharacterOwners(int characterId) {
        IntOpenHashSet ids = ownerItemIds.remove(characterId);
        if (ids == null) return;
        for (IntIterator it = ids.iterator(); it.hasNext();) {
            int id = it.nextInt();
            if (itemOwners.get(id) == characterId) itemOwners.remove(id);
        }
    }

    public void trackPendingItem(CItem item, int ownerId) {
        if (item.ownerId == ownerId) return;
        // Do not drop the OLD owner from pendingItemIds here: an older
        // snapshot may still be in flight, and a logout/SELECT for that owner
        // must not pass the durability barrier before the final SQL update.
        if (item.pendingOwners == null) {
            item.pendingOwners = new IntOpenHashSet();
            item.pendingOwners.add(item.ownerId);
        }
        item.pendingOwners.add(ownerId);
        item.ownerId = ownerId;
        addId(pendingItemIds, ownerId, item.id);
    }

    /** Only retire historical owner barriers after the last dirty version is persisted. */
    public void completePendingItem(CItem item) {
        IntOpenHashSet history = item.pendingOwners;
        if (history != null) {
            for (IntIterator it = history.iterator(); it.hasNext();) {
                removeId(pendingItemIds, it.nextInt(), item.id);
            }
            item.pendingOwners = null;
        } else {
            removeId(pendingItemIds, item.ownerId, item.id);
        }
    }

}
