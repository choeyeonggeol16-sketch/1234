package l1j.server.server.model;

import java.util.logging.Logger;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_DropItem;
import l1j.server.server.model.network.core.sc.packet.SC_RemoveObject;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.Warehouse.Warehouse;
import l1j.server.server.storage.CacheD;

public class L1GroundInventory extends CInventory {

    private static final long serialVersionUID = 1L;

    private static final boolean DEBUG_GROUND_ITEM_TIMER = false;

    private static final Logger _log =
            Logger.getLogger(L1GroundInventory.class.getName());

    public L1GroundInventory(int objectId, int x, int y, short map) {
        setId(objectId);
        setX(x);
        setY(y);
        setMap(map);

        L1World.getInstance().addVisibleObject(this);
    }

    /** A ground item's visible identity is the item, not this tile bucket. */
    public static boolean isVisibleGroundItem(L1PcInstance viewer, L1ItemInstance item) {
        if (viewer == null || item == null || item.getClusterMapId() < 0
                || !viewer.canSeeObject(item)) return false;
        final L1GroundInventory ground = L1World.getInstance().findGroundInventory(
                item.getX(), item.getY(), item.getMapId());
        return ground != null && ground._items.Have(item);
    }

    private static boolean ready(L1PcInstance viewer, int epoch) {
        return (epoch & 1) != 0 && !viewer.isLogoutStarted()
                && viewer.groundSightEpoch() == epoch;
    }

    /** CItem::SendItemSpawn equivalent; record KnownObject after SEND admission. */
    private static void showItem(L1PcInstance viewer, L1ItemInstance item,
            boolean changed, int epoch) {
        if (!ready(viewer, epoch) || (!changed && viewer.knownsObject(item))) return;
        final var client = viewer.getNetConnection();
        if (client == null || client.getActiveChar() != viewer) return;
        final boolean sent = SC_DropItem.send(client, item);
        if (ready(viewer, epoch) && viewer.getNetConnection() == client && client.isConnected()) {
            if (sent) viewer.addKnownObject(item);
            else viewer.markGroundSightRetry();
        }
    }

    /** User::OutOfSight equivalent, with stale object-ID replacement protection. */
    public static void forgetItem(L1PcInstance viewer, L1ItemInstance item) {
        final int epoch = viewer.groundSightEpoch();
        if (!ready(viewer, epoch) || !viewer.knownsObject(item)) return;
        final var client = viewer.getNetConnection();
        if (client == null || client.getActiveChar() != viewer) return;
        final L1Object current = L1World.getInstance().findObject(item.getId());
        if (current instanceof L1ItemInstance replacement && replacement != item
                && viewer.knownsObject(replacement) && isVisibleGroundItem(viewer, replacement)) {
            viewer.removeKnownObject(item);
            return;
        }
        final boolean sent = SC_RemoveObject.send(client, item);
        if (ready(viewer, epoch) && viewer.getNetConnection() == client && client.isConnected()) {
            if (sent) viewer.removeKnownObject(item);
            else viewer.markGroundSightRetry();
        }
    }

    public static void reconcileItem(L1PcInstance viewer, L1ItemInstance item, boolean changed) {
        final int epoch = viewer.groundSightEpoch();
        if (!ready(viewer, epoch)) return;
        if (isVisibleGroundItem(viewer, item)) showItem(viewer, item, changed, epoch);
        else forgetItem(viewer, item);
    }

    @Override
    public void insertItem(L1ItemInstance item) {
        publishGroundItem(item, false);
    }

    /** An already-owned ground item must survive a failed pickup even when
     * the wheel has stopped. A new drop must instead fail admission.
     * Called under this tile monitor before any empty-tile retirement.
     */
    void restoreAfterFailedTransfer(L1ItemInstance item) {
        assert Thread.holdsLock(this);
        _items.Push(item);
        try {
            publishGroundItem(item, true);
        } catch (RuntimeException failure) {
            _items.Pop(item);
            throw failure;
        }
    }

    private void publishGroundItem(L1ItemInstance item, boolean restoring) {
        if (item == null) return;
        if (DEBUG_GROUND_ITEM_TIMER) debug("SPAWN", item, "before OnSpawned");
        // Timer admission is part of insertion and shares its cleanup.
        try {
            try {
                item.OnSpawned();
            } catch (java.util.concurrent.RejectedExecutionException unavailable) {
                if (!restoring) throw unavailable;
                _log.log(java.util.logging.Level.SEVERE,
                        "Ground item restored without delete timer: " + item.getId(), unavailable);
            }
            L1World.getInstance().addVisibleObject(item);
        } catch (RuntimeException | Error failure) {
            try { L1World.getInstance().removeVisibleObject(item); }
            catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
            try { item.CancelTimer(L1ItemInstance.TIMER_GROUND_DELETE); }
            catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    @Override
    public void updateItem(L1ItemInstance item) {
        if (item == null) return;
        // Serialize the state snapshot against world leave on the same item
        // monitor already used by CWorld. This is not a controller event.
        synchronized (item) {
            if (item.getClusterMapId() < 0 || !_items.Have(item)) return;
            SC_DropItem.broadcast(item, true);
        }
    }

    @Override
    public synchronized void Pop(L1ItemInstance item) {
        if (item != null && (item.isGroundPickupPending() || item.isGroundDropUnsettled())) return;
        if (unlinkGroundItem(item)) finishGroundTransfer(item);
    }

    // A failed pickup must be able to restore the last item into the same tile.
    // Caller already holds the ground monitor for the entire transfer.
    boolean popForTransfer(L1ItemInstance item) {
        assert Thread.holdsLock(this);
        return unlinkGroundItem(item);
    }

    private boolean unlinkGroundItem(L1ItemInstance item) {
        if (item == null) return false;
        // Ground lock must precede item lock (pickup holds ground before transfer).
        synchronized (item) {
            if (!_items.Pop(item)) return false;
            item.CancelTimer(L1ItemInstance.TIMER_GROUND_DELETE);
            // World leave publishes the item itself to User::OutOfSight.
            L1World.getInstance().removeVisibleObject(item);
            return true;
        }
    }

    void finishGroundTransfer(L1ItemInstance item) {
        assert Thread.holdsLock(this);
        if (_items.isEmpty()) {
            final L1World world = L1World.getInstance();
            if (world.findGroundInventory(getX(), getY(), getMapId()) != this) return;
            world.removeVisibleObject(this);
            if (DEBUG_GROUND_ITEM_TIMER)
                debug("GROUND-EMPTY", item, "inventory removed from visible world");
        }
    }

    /** Destination cleanup after an accepted or rejected transfer.
     * The existing ordered inventory monitors are still held.
     * Cleanup must not replace the original transfer exception.
     */
    void retireEmptyTileAfterTransfer() {
        assert Thread.holdsLock(this);
        if (!_items.isEmpty()) return;
        try {
            finishGroundTransfer(null);
        } catch (RuntimeException failure) {
            _log.log(java.util.logging.Level.SEVERE, "Empty ground tile retirement failed: " + getId(), failure);
        }
    }

    /** Ordinary stackable direct pickups use the same durable owner receipt as
     * other economy operations. Preserve special charge/entity and party paths. */
    public static boolean supportsReceiptPickup(L1ItemInstance item) {
        if (item == null || item.getItem() == null || !item.isStackable()) return false;
        final int id = item.getItemId();
        return !Warehouse.chargeMergeItem(id) && id != 40314 && id != 40316
                && (id < 49016 || id > 49025) && (id < 41383 || id > 41400)
                && id != 700024 && item.get_bless_level() == 0;
    }

    /** OFF RequestPickItem -> DB reply: reserve, prepare, commit, then publish. */
    public synchronized boolean RequestPickItem(L1PcInstance owner, L1ItemInstance item,
            int amount, Runnable afterCommitted) {
        if (owner == null || !supportsReceiptPickup(item) || amount <= 0
                || !_items.Have(item) || item.isGroundPickupPending() || item.isGroundDropUnsettled()
                || item.is_Not_PickUp_Item() || amount > item.getCount()
                || L1World.getInstance().findGroundInventory(getX(), getY(), getMapId()) != this)
            return false;
        final L1World world = L1World.getInstance();
        final int before = item.getCount();
        final L1InventoryTransaction receipt;
        try {
            receipt = L1InventoryTransaction.begin(owner,
                    CacheD.accountOrderKey("ground-pick:", owner.getId()));
        } catch (IllegalStateException | java.util.concurrent.RejectedExecutionException busy) {
            return false;
        }
        boolean submitted = false;
        try (receipt) {
            if (!item.reserveGroundPickup(receipt)) return false;
            if (owner.getInventory().CheckAddableTransfer(item, amount) != OK) return false;
            final L1ItemInstance image = amount == before ? item
                    : Warehouse.allocateDetachedTransfer(item, amount);
            if (receipt.grantCopy(owner, image, amount) == null) return false;

            // Confirmed SQL rollback clears only the exact reservation. No
            // world work, blocking or new persistence command on a DB worker.
            receipt.onConfirmedRollback(() -> item.releaseGroundPickup(receipt));
            receipt.beforeInventoryReplay(() -> PickItem(item, receipt, before, amount));
            receipt.onTerminalFailure(() -> {
                // A lost session cannot resurrect an already committed ground
                // item. Complete its world removal on the existing CIO owner.
                // An unresolved COMMIT keeps the token fenced, not pickable.
                if (receipt.isDurablyCommitted() && item.hasGroundPickupReservation(receipt)) {
                    CIOThreadCallback.getInstance().completeWorldMutation(world,
                            () -> PickItem(item, receipt, before, amount),
                            failure -> {
                                if (failure != null) {
                                    item.freezeGroundPickup(receipt);
                                    _log.log(java.util.logging.Level.SEVERE,
                                            "Committed ground pickup replay failed: " + item.getId(), failure);
                                }
                            });
                } else if (receipt.isUncertainFailure()) {
                    // Keep the item unavailable without retaining the entire
                    // receipt, character, and session through its world object.
                    item.freezeGroundPickup(receipt);
                }
            });
            receipt.commitAfterInventoryRelease(afterCommitted);
            submitted = true;
            return true;
        } finally {
            if (!submitted) item.releaseGroundPickup(receipt);
        }
    }

    /** Commit reply, idempotent for the exact reservation even after owner logout. */
    private synchronized void PickItem(L1ItemInstance item, L1InventoryTransaction receipt,
            int before, int amount) {
        if (!item.hasGroundPickupReservation(receipt)) return;
        if (!receipt.isDurablyCommitted()) throw new IllegalStateException("Uncommitted pickup reply");
        final L1World world = L1World.getInstance();
        if (amount == before) {
            // Retire the original ground identity before the full-count
            // inventory image with that same ID is installed by the receipt.
            if (_items.Have(item)) popForTransfer(item);
            else {
                item.CancelTimer(L1ItemInstance.TIMER_GROUND_DELETE);
                world.removeVisibleObject(item);
            }
            world.removeObject(item);
            finishGroundTransfer(item);
        } else {
            final int remaining = before - amount;
            if (!_items.Have(item) || (item.getCount() != before && item.getCount() != remaining))
                throw new IllegalStateException("Reserved ground pickup changed: " + item.getId());
            item.setCount(remaining);
            try { updateItem(item); }
            catch (RuntimeException notice) {
                _log.log(java.util.logging.Level.WARNING, "Committed ground remainder notice failed", notice);
            }
        }
        item.setGiveItem(false);
        item.releaseGroundPickup(receipt);
    }

    @Override
    public synchronized void clearItems() {
        // A cleanup sweep cannot destroy a source reserved by a DB receipt.
        // Keep its tile published until the exact committed/rollback reply.
        for (L1ItemInstance item : getItems()) {
            if (item == null || item.isGroundPickupPending() || item.isGroundDropUnsettled()) continue;
            if (unlinkGroundItem(item)) L1World.getInstance().removeObject(item);
        }
        finishGroundTransfer(null);
    }

    private static void debug(String event, L1ItemInstance item, String extra) {
        if (!DEBUG_GROUND_ITEM_TIMER) {
            return;
        }

        final Thread thread = Thread.currentThread();

        _log.info(
                "[CITEM TIMER] "
                + event
                + " | oid=" + safeId(item)
                + " itemId=" + safeItemId(item)
                + " pos=" + safeX(item) + "," + safeY(item) + "," + safeMap(item)
                + " | thread=" + thread.getName()
                + " cioShard=" + CIOThreadCallback.currentShardIndex()
                + " | " + extra);
    }

    private static int safeId(L1ItemInstance item) {
        return item != null ? item.getId() : 0;
    }

    private static int safeItemId(L1ItemInstance item) {
        return item != null ? item.getItemId() : 0;
    }

    private static int safeX(L1ItemInstance item) {
        return item != null ? item.getX() : 0;
    }

    private static int safeY(L1ItemInstance item) {
        return item != null ? item.getY() : 0;
    }

    private static int safeMap(L1ItemInstance item) {
        return item != null ? item.getMapId() : 0;
    }
}
