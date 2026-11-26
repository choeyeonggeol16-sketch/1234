package l1j.server.server.storage.cached;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.storage.mysql.MySqlCharactersItemStorage.ItemSnapshot;
import l1j.server.server.storage.mysql.MySqlCharactersItemStorage;

/** One item's pending write image and dirty bits; no duplicate game item allocation. */
public final class CItem extends CPendingState {
    public final int id;
    public ItemSnapshot latest;
    public long dirty;
    public int ownerId;
    // Only allocated when one pending dirty item changes ownership. Preserve
    // every former owner's flush boundary until its last write succeeds.
    public IntOpenHashSet pendingOwners;

    public CItem(int id) { this.id = id; }

    /** Called under the CacheD lock. */
    public void capture(ItemSnapshot snapshot, long mask) {
        latest = snapshot;
        dirty |= mask;
        version++;
    }
    /** Keep the pending FIFO position and wakeup deadline when both indexed owners stay the same. */
    public boolean mergeQueued(L1ItemInstance item, int owner, long mask) {
        if (!queued || owner != ownerId || latest.ownerId != owner) return false;
        if (latest.matchesSaveSource(item, owner)) {
            if ((mask & ~dirty) != 0) capture(latest, mask);
        } else {
            capture(ItemSnapshot.capture(item, owner), mask);
        }
        return true;
    }

    public static void persist(ItemSnapshot snapshot, long dirtyMask) throws java.sql.SQLException {
        MySqlCharactersItemStorage.updateSnapshot(snapshot, dirtyMask);
    }
}
