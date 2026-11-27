package l1j.server.server.model;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.inventory.CInventory;
import l1j.server.server.model.inventory.L1GroundInventory;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1WorldMap;
import l1j.server.server.model.world.instance.CInstantZone;
import l1j.server.server.model.world.instance.CInstantZoneManager;

import static l1j.server.server.model.CWorld.PICK_GROUND;
import static l1j.server.server.model.CWorld.PICK_INVENTORY;
import static l1j.server.server.model.CWorld.PICK_REJECTED;

/** Settles loot into inventories and owns ground tile lookup for this world. */
final class CWorldGroundItems {
    private final CWorld world;
    private final CWorldMapObjects mapObjects;

    CWorldGroundItems(CWorld world, CWorldMapObjects mapObjects) {
        this.world = world;
        this.mapObjects = mapObjects;
    }

    /** Validate ownership before a party route; tradeItem revalidates under both inventory monitors. */
    boolean ownsStack(CInventory source, L1ItemInstance item, int count) {
        return source != null && item != null && count > 0
                && source.GetByServerID(item.getId()) == item && count <= item.getCount();
    }

    /** Existing ordered inventory monitors remain authoritative for each transfer. */
    int pickItem(L1Location origin, CInventory source, L1ItemInstance item, int count,
            L1PcInstance recipient) {
        if (origin == null || !ownsStack(source, item, count)) return PICK_REJECTED;
        if (recipient != null) {
            final CInstantZone<?> zone = CInstantZoneManager.destination(origin.getMapId());
            if (origin instanceof CWorld.DropOrigin saved && !saved.belongsTo(zone)) return PICK_REJECTED;
            final boolean awarded = zone == null
                    ? tryAwardDrop(origin, source, item, count, recipient)
                    : zone.trySettle(origin.getMap(), () -> tryAwardDrop(origin, source, item, count, recipient));
            if (awarded) return PICK_INVENTORY;
            int targetX = 0, targetY = 0;
            final boolean live;
            synchronized (recipient.autoSaveLifecycleLock()) {
                live = isDropRecipient(origin, recipient);
                if (live) {
                    targetX = recipient.getX();
                    targetY = recipient.getY();
                }
            }
            if (live && dropAtTile(source, item, count, targetX, targetY, origin)) return PICK_GROUND;
        }
        return dropAtTile(source, item, count, origin.getX(), origin.getY(), origin)
                ? PICK_GROUND : PICK_REJECTED;
    }

    private boolean tryAwardDrop(L1Location origin, CInventory source, L1ItemInstance item, int count,
            L1PcInstance recipient) {
        synchronized (recipient.autoSaveLifecycleLock()) {
            return isDropRecipient(origin, recipient)
                    && addToInventory(source, item, count, recipient);
        }
    }

    private static boolean isDropRecipient(L1Location origin, L1PcInstance recipient) {
        final var session = recipient.getNetConnection();
        return recipient.getMap() == origin.getMap() && !recipient.isLogoutStarted()
                && session != null && session.isConnected() && session.getActiveChar() == recipient;
    }

    /**
     * Delivery phase of DoAsyncPickItem, executed in the already admitted world
     * job (or its mandatory synchronous fallback). Do not enqueue a second
     * ticket: completion belongs to the outer NPC settlement.
     * Caller holds the recipient's existing logout/save lifecycle monitor.
     */
    private boolean addToInventory(CInventory source, L1ItemInstance item, int count,
            L1PcInstance recipient) {
        final CInventory dest = recipient.getInventory();
        final L1ItemInstance adena = item.getItemId() == l1j.server.server.model.item.L1ItemId.ADENA
                ? dest.GetFirstItemByClassID(item.getItemId()) : null;
        return (adena == null || (long) adena.getCount() + count <= 2_000_000_000L)
                && source.tradeItem(item, count, dest) != null;
    }

    private boolean dropAtTile(CInventory source, L1ItemInstance item, int count,
            int x, int y, L1Location origin) {
        if (!ownsStack(source, item, count)) return false;
        final CInstantZone<?> zone = CInstantZoneManager.destination(origin.getMapId());
        if (origin instanceof CWorld.DropOrigin saved && !saved.belongsTo(zone)) return false;
        final L1Map expected = origin.getMap();
        return zone == null ? deliverToGround(source, item, count, x, y, expected)
                : zone.trySettle(expected, () -> deliverToGround(source, item, count, x, y, expected));
    }

    private boolean deliverToGround(CInventory source, L1ItemInstance item, int count,
            int x, int y, L1Map expected) {
        final short map = (short) expected.getId();
        final L1GroundInventory ground = tryGroundInventory(x, y, expected);
        if (ground == null) return false;
        // Pickup may have removed an empty bucket between lookup and transfer.
        // Preserve the existing ground -> source monitor order.
        synchronized (ground) {
            if (ground.getMap() != expected || findGroundInventory(x, y, map) != ground) return false;
            return source.tradeItem(item, count, ground) != null;
        }
    }

    // Legacy nearest-passable order, precomputed instead of allocating a BFS per item.
    private static final byte[] DROP_OFFSETS = { 0, 0, 0, -1, 1, -1, 1, 0, 1, 1, 0, 1, -1, 1, -1, 0, -1, -1, 0, -2, 1, -2, -1, -2, 2, -2, 2, -1, 2, 0, 2, 1, 2, 2, 1, 2, 0, 2, -1, 2, -2, 2, -2, 1, -2, 0, -2, -1, -2, -2, 0, -3, 1, -3, -1, -3, 2, -3, -2, -3, 3, -3, 3, -2, 3, -1, 3, 0, 3, 1, 3, 2, 3, 3, 2, 3, 1, 3, 0, 3, -1, 3, -2, 3, -3, 3, -3, 2, -3, 1, -3, 0, -3, -1, -3, -2, -3, -3 };

    boolean putItem(L1Location origin, CInventory source, L1ItemInstance item) {
        if (origin == null || item == null || !ownsStack(source, item, item.getCount())) return false;
        final int x = origin.getX(), y = origin.getY();
        for (int i = 0; i < DROP_OFFSETS.length; i += 2) {
            final int tx = x + DROP_OFFSETS[i], ty = y + DROP_OFFSETS[i + 1];
            if (origin.getMap().isPassable(tx, ty))
                return dropAtTile(source, item, item.getCount(), tx, ty, origin);
        }
        return dropAtTile(source, item, item.getCount(), x, y, origin);
    }

    /** Try the corpse tile and nearby passable tiles, without changing stack caps. */
    boolean recoverRemainder(L1Location origin, CInventory source, L1ItemInstance item) {
        if (origin == null || source == null || item == null) return false;
        if (source.GetByServerID(item.getId()) != item || item.getCount() <= 0) return true;
        final int x = origin.getX(), y = origin.getY();
        if (dropAtTile(source, item, item.getCount(), x, y, origin)) return true;
        for (int radius = 1; radius <= 3; radius++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != radius
                            || !origin.getMap().isPassable(x + dx, y + dy)) continue;
                    if (dropAtTile(source, item, item.getCount(), x + dx, y + dy, origin)) return true;
                }
            }
        }
        return source.GetByServerID(item.getId()) != item;
    }

    private static int groundKey(int x, int y) {
        return -((x - 30000) * 10000 + (y - 30000));
    }

    boolean deleteGroundItem(L1ItemInstance item, L1Map expected) {
        if (item == null || expected == null || LocalWorldMap.publishedTerrain(item) != expected) return false;
        final short mapId = (short) expected.getId();
        final L1GroundInventory tile = findGroundInventory(item.getX(), item.getY(), mapId);
        if (tile == null) return false;
        synchronized (tile) {
            if (tile.getMap() != expected || item.getMap() != expected
                    || world.findObject(item.getId()) != item
                    || findGroundInventory(tile.getX(), tile.getY(), mapId) != tile) return false;
            // Checks exact inventory ownership and both receipt states before deleting any auxiliary rows.
            return tile.DeleteItemAmount(item) > 0;
        }
    }

    /** A numeric map ID can be reused after an instance closes. */
    L1GroundInventory findGroundInventory(int x, int y, short mapId) {
        final var map = mapObjects.registry(mapId, false);
        if (map == null || !(map.get(groundKey(x, y)) instanceof L1GroundInventory ground)) return null;
        return ground.getX() == x && ground.getY() == y
                && L1WorldMap.getInstance().findMap(mapId) == ground.getMap() ? ground : null;
    }

    L1GroundInventory getGroundInventory(int x, int y, short mapId) {
        final L1Map expected = L1WorldMap.getInstance().findMap(mapId);
        final L1GroundInventory ground = tryGroundInventory(x, y, expected);
        if (ground == null) throw new IllegalStateException("Ground tile unavailable: " + mapId + "/" + x + "/" + y);
        return ground;
    }

    L1GroundInventory tryGroundInventory(int x, int y, L1Map expected) {
        if (expected == null || !LocalWorldMap.isSanePosition(expected.getId(), x, y)) return null;
        final int mapId = expected.getId();
        final var terrain = L1WorldMap.getInstance();
        if (terrain.findMap(mapId) != expected) return null;
        final int key = groundKey(x, y);
        final var registry = mapObjects.registry(mapId, true);
        if (registry == null) return null;
        for (;;) {
            final L1GroundInventory retired;
            synchronized (registry.lockForKey(key)) {
                if (terrain.findMap(mapId) != expected) return null;
                final L1Object current = registry.get(key);
                if (current == null) {
                    // Only the fresh, unpublished tile's monitor is acquired under this stripe.
                    final var created = new L1GroundInventory(key, x, y, expected);
                    synchronized (created) {
                        mapObjects.enter(created, false);
                        world.getClusterGrid().enterSilently(created);
                    }
                    return terrain.findMap(mapId) == expected ? created : null;
                }
                if (!(current instanceof L1GroundInventory ground) || current.getX() != x || current.getY() != y)
                    return null;
                if (ground.getMap() == expected) return ground;
                retired = ground;
            }
            // An outstanding pickup owns the item and its original tile. Never overwrite it.
            synchronized (retired) {
                if (registry.get(key) != retired) continue;
                if (retired.getSize() != 0) return null;
                world.removeVisibleObject(retired);
            }
        }
    }
}
