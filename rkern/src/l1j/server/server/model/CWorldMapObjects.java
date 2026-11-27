package l1j.server.server.model;

import java.util.BitSet;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicReferenceArray;
import l1j.server.server.model.inventory.L1GroundInventory;

/** Map-wide membership. The cell index independently serves bounded spatial queries. */
final class CWorldMapObjects {
    private static final int PAGE_BITS = 8;
    private static final int PAGE_SIZE = 1 << PAGE_BITS;
    private final AtomicReferenceArray<AtomicReferenceArray<L1IntRegistry<L1Object>>> pages =
            new AtomicReferenceArray<>((CWorld.MAX_MAP_ID >>> PAGE_BITS) + 1);
    // Some traps and items enter before receiving a factory registration; their IDs
    // must still retire across maps on replacement. Tile inventories are local to a map.
    private final L1IntRegistry<L1Object> mapOnly = new L1IntRegistry<>();
    private final BitSet configured = new BitSet();

    CWorldMapObjects(Iterable<Integer> configuredMaps) {
        for (int id : configuredMaps) {
            if (valid(id)) configured.set(id);
        }
    }

    private static boolean valid(int id) {
        return id >= 0 && id <= CWorld.MAX_MAP_ID;
    }

    L1IntRegistry<L1Object> registry(int mapId, boolean create) {
        if (!valid(mapId)) return null;
        int pageIndex = mapId >>> PAGE_BITS;
        var page = pages.get(pageIndex);
        if (page == null) {
            if (!create) return null;
            var candidate = new AtomicReferenceArray<L1IntRegistry<L1Object>>(PAGE_SIZE);
            page = pages.compareAndExchange(pageIndex, null, candidate);
            if (page == null) page = candidate;
        }
        int slot = mapId & (PAGE_SIZE - 1);
        var registry = page.get(slot);
        if (registry == null && create) {
            var candidate = new L1IntRegistry<L1Object>();
            registry = page.compareAndExchange(slot, null, candidate);
            if (registry == null) registry = candidate;
        }
        return registry;
    }

    L1IntRegistry<L1Object> legacyRegistry(int mapId) {
        return registry(mapId, valid(mapId) && configured.get(mapId));
    }

    Collection<L1Object> members(int mapId) {
        var map = registry(mapId, false);
        return map == null ? List.of() : map.values();
    }

    boolean remove(L1Object object, int mapId) {
        var map = registry(mapId, false);
        return map != null && map.remove(object.getId(), object);
    }

    L1Object mapOnlyOwner(int id) {
        return mapOnly.get(id);
    }

    /** All map-only owner changes are made under the same factory page as registration. */
    void rememberMapOnly(L1Object object) {
        mapOnly.put(object.getId(), object);
    }

    void forgetMapOnly(L1Object object) {
        if (mapOnly.get(object.getId()) == object) mapOnly.remove(object.getId(), object);
    }

    /** Caller holds the actor monitor; identity-registered actors also hold their factory page. */
    boolean enter(L1Object object, boolean canonical) {
        int mapId = object.getMapId();
        if (!LocalWorldMap.isSanePosition(mapId, object.getX(), object.getY())) return false;
        return publish(object, mapId, canonical);
    }

    private boolean publish(L1Object object, int mapId, boolean canonical) {
        int previousMap = object.getClusterMapId();
        var map = registry(mapId, true);
        if (map == null) return false;
        int id = object.getId();
        synchronized (map.lockForKey(id)) {
            var previous = map.get(id);
            if (previous != null && previous != object && (!canonical || previous instanceof L1GroundInventory))
                return false;
            if (previous != object) map.put(id, object);
        }
        if (previousMap != mapId) remove(object, previousMap);
        return true;
    }

    /** Source membership follows the last published position, even after the actor location has changed. */
    boolean move(L1Object object, int mapId, int x, int y) {
        int previousMap = object.getClusterMapId();
        return valid(mapId) && LocalWorldMap.isSanePosition(mapId, x, y)
                && (previousMap == mapId || publish(object, mapId, false));
    }
}
