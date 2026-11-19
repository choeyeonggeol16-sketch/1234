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
    public void onPerceive(L1PcInstance perceivedFrom) {
        if (perceivedFrom == null || _items.isEmpty()) {
            return;
        }

        for (L1ItemInstance item : getItems()) {
	        if (item == null) {
	            continue;
	        }

	        if (!perceivedFrom.knownsObject(item)) {
	            perceivedFrom.addKnownObject(item);
	        }

	        SC_DropItem.send(
	                perceivedFrom.getNetConnection(),
	                item);
	    }
    }

    @Override
    public void insertItem(L1ItemInstance item) {
        if (item == null) {
            return;
        }

        if (DEBUG_GROUND_ITEM_TIMER) {
            debug("SPAWN", item, "before OnSpawned");
        }

        item.OnSpawned();

        final int viewers = SC_DropItem.broadcast(item, true);

        if (DEBUG_GROUND_ITEM_TIMER) {
            debug("TIMER-ARM", item, "viewers=" + viewers);
        }
    }

    @Override
    public void updateItem(L1ItemInstance item) {
        if (item == null || !_items.contains(item)) {
            return;
        }
        SC_DropItem.broadcast(item);
    }

    @Override
    public void deleteItem(L1ItemInstance item) {
        if (item == null) {
            return;
        }

        if (DEBUG_GROUND_ITEM_TIMER) {
            debug("DELETE-BEGIN", item, "contained=" + _items.contains(item));
        }

        final boolean removed = _items.remove(item);
        if (!removed) {
            return;
        }

        item.CancelTimer(L1ItemInstance.TIMER_GROUND_DELETE);

        if (DEBUG_GROUND_ITEM_TIMER) {
            debug("TIMER-CANCEL", item, "generation invalidated");
        }

        try(var shared=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                SC_RemoveObject.nativeWriter(item.getId()),Integer.MIN_VALUE,Integer.MAX_VALUE)) {
            for(L1PcInstance pc:L1World.getInstance().getVisiblePlayerFast(item)) {
                final var client=pc.getNetConnection();
                if(client!=null && client.isConnected()) {
                    final var socket=client.getConnection();
                    if(socket!=null && socket.isClientConnected())
                        shared.send(socket,pc.getId());
                }
                pc.removeKnownObject(item);
            }
        }

        if (DEBUG_GROUND_ITEM_TIMER) {
            debug("DELETE-END", item,
                    "removed=" + removed + " remaining=" + _items.size());
        }

        if (_items.isEmpty()) {
            L1World.getInstance().removeVisibleObject(this);

            if (DEBUG_GROUND_ITEM_TIMER) {
                debug("GROUND-EMPTY", item, "inventory removed from visible world");
            }
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
