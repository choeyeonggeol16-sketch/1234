package l1j.server.server.model;

import java.util.logging.Logger;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_DropItem;
import l1j.server.server.model.network.core.sc.packet.SC_RemoveObject;
import l1j.server.server.model.network.thread.CIOThreadCallback;

public class L1GroundInventory extends L1Inventory {

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

    @Override
    public void onPerceive(L1PcInstance viewer) {
        if (viewer != null) viewer.getCreatureController().ReconcileSight(this, false);
    }

    private static boolean ready(L1PcInstance viewer, int epoch) {
        return (epoch & 1) != 0 && !viewer.isLogoutStarted()
                && viewer.groundSightEpoch() == epoch;
    }

    /** Resolve membership, rather than treating carried items as ground objects. */
    public static boolean isVisibleGroundItem(L1PcInstance viewer, L1ItemInstance item) {
        if (item == null || !viewer.canSeeObject(item)) return false;
        final L1GroundInventory ground = L1World.getInstance().findGroundInventory(
                item.getX(), item.getY(), item.getMapId());
        return ground != null && ground.getClusterMapId() >= 0 && ground._items.contains(item);
    }

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

    public static void forgetItem(L1PcInstance viewer, L1ItemInstance item) {
        final int epoch = viewer.groundSightEpoch();
        if (!ready(viewer, epoch) || !viewer.knownsObject(item)) return;
        final var client = viewer.getNetConnection();
        if (client == null || client.getActiveChar() != viewer) return;
        final L1Object current = L1World.getInstance().findObject(item.getId());
        // A queued old deletion must not erase a newly registered known sprite
        // using the same object ID.
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

    /** Executed by the viewer's existing controller, including delayed events. */
    public static void reconcileItem(L1PcInstance viewer, L1ItemInstance item, boolean changed) {
        final int epoch = viewer.groundSightEpoch();
        if (!ready(viewer, epoch)) return;
        if (isVisibleGroundItem(viewer, item)) showItem(viewer, item, changed, epoch);
        else forgetItem(viewer, item);
    }

    public void reconcileSight(L1PcInstance viewer, boolean changed) {
        final int epoch = viewer.groundSightEpoch();
        if (!ready(viewer, epoch)) return;
        if (getClusterMapId() < 0 || !viewer.canSeeObject(this)) {
            leaveSight(viewer);
            return;
        }
        for (L1ItemInstance item : getItems()) {
            if (!ready(viewer, epoch)) return;
            if (!changed && viewer.knownsObject(item)) continue;
            if (_items.contains(item) && viewer.canSeeObject(item))
                showItem(viewer, item, changed, epoch);
        }
    }

    public void leaveSight(L1PcInstance viewer) {
        if (!ready(viewer, viewer.groundSightEpoch())) return;
        for (L1ItemInstance item : getItems()) {
            if (viewer.knownsObject(item)) reconcileItem(viewer, item, false);
        }
    }

    /** Reuse existing AOI scratch and controller tickets; no new worker or queue. */
    private void notifyGroundChange(L1ItemInstance item, boolean changed) {
        final L1World world = L1World.getInstance();
        final int range = l1j.server.Config.PC_RECOGNIZE_RANGE;
        // L1's screen test is directional. Collect a superset and use the
        // viewer's actual canSeeObject test when the ticket executes.
        final var viewers = world.borrowVisiblePlayerFast(this, range < 0 ? 22 : range);
        try {
            for (int i = 0, n = viewers.size(); i < n; i++) {
                final L1PcInstance viewer = viewers.get(i);
                if (viewer != null)
                    viewer.getCreatureController().GroundItemChanged(item, changed);
            }
        } finally {
            world.releaseVisiblePlayerFast(viewers);
        }
    }

    @Override
    public void insertItem(L1ItemInstance item) {
        if (item == null) return;
        if (DEBUG_GROUND_ITEM_TIMER) debug("SPAWN", item, "before OnSpawned");
        item.OnSpawned();
        notifyGroundChange(item, true);
    }

    @Override
    public void updateItem(L1ItemInstance item) {
        if (item == null || !_items.contains(item)) return;
        notifyGroundChange(item, true);
    }

    @Override
    public void deleteItem(L1ItemInstance item) {
        if (item == null || !_items.remove(item)) return;
        item.CancelTimer(L1ItemInstance.TIMER_GROUND_DELETE);
        notifyGroundChange(item, false);
        if (_items.isEmpty()) {
            L1World.getInstance().removeVisibleObject(this);
            if (DEBUG_GROUND_ITEM_TIMER)
                debug("GROUND-EMPTY", item, "inventory removed from visible world");
        }
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
