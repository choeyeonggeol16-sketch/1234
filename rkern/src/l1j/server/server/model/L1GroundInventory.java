package l1j.server.server.model;

import java.util.logging.Logger;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_DropItem;
import l1j.server.server.model.network.core.sc.packet.SC_RemoveObject;
import l1j.server.server.model.network.thread.CIOThreadCallback;

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
        return ground != null && ground._items.contains(item);
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
        if (item == null) return;
        if (DEBUG_GROUND_ITEM_TIMER) debug("SPAWN", item, "before OnSpawned");
        item.OnSpawned();
        // World entry publishes the item itself to User::InSight.
        try {
            L1World.getInstance().addVisibleObject(item);
        } catch (RuntimeException | Error failure) {
            try { L1World.getInstance().removeVisibleObject(item); }
            catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
            item.CancelTimer(L1ItemInstance.TIMER_GROUND_DELETE);
            throw failure;
        }
    }

    @Override
    public void updateItem(L1ItemInstance item) {
        if (item == null) return;
        // Serialize the state snapshot against world leave on the same item
        // monitor already used by CWorld. This is not a controller event.
        synchronized (item) {
            if (item.getClusterMapId() < 0 || !_items.contains(item)) return;
            SC_DropItem.broadcast(item, true);
        }
    }

    @Override
    public synchronized void deleteItem(L1ItemInstance item) {
        if (item == null) return;
        // Ground lock must precede item lock (pickup holds ground before transfer).
        synchronized (item) {
            if (!_items.remove(item)) return;
            item.CancelTimer(L1ItemInstance.TIMER_GROUND_DELETE);
            // World leave publishes the item itself to User::OutOfSight.
            L1World.getInstance().removeVisibleObject(item);
            if (_items.isEmpty()) {
                L1World.getInstance().removeVisibleObject(this);
                if (DEBUG_GROUND_ITEM_TIMER)
                    debug("GROUND-EMPTY", item, "inventory removed from visible world");
            }
        }
    }

    @Override
    public synchronized void clearItems() {
        // Base clearItems only unregisters object IDs. Ground objects also own
        // world visibility and their expiry timers.
        for (L1ItemInstance item : getItems()) {
            if (item == null) continue;
            synchronized (item) {
                item.CancelTimer(L1ItemInstance.TIMER_GROUND_DELETE);
                L1World.getInstance().removeVisibleObject(item);
            }
        }
        super.clearItems();
        L1World.getInstance().removeVisibleObject(this);
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
