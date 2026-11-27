package l1j.server.server.model;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicReferenceArray;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.inventory.L1GroundInventory;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1WorldMap;

/** Map-wide membership. The cell index independently serves bounded spatial queries. */
final class CWorldMapObjects {
    private static final int PAGE_BITS = 8;
    private static final int PAGE_SIZE = 1 << PAGE_BITS;
    private final AtomicReferenceArray<AtomicReferenceArray<MapMembers>> pages =
            new AtomicReferenceArray<>((CWorld.MAX_MAP_ID >>> PAGE_BITS) + 1);
    // Some traps and items enter before receiving a factory registration; their IDs
    // must still retire across maps on replacement. Tile inventories are local to a map.
    private final L1IntRegistry<L1Object> mapOnly = new L1IntRegistry<>();
    private final BitSet configured = new BitSet();
    private final L1ObjectFactory identities;

    private static final class MapMembers {
        final L1IntRegistry<L1Object> objects = new L1IntRegistry<>();
        volatile L1IntRegistry<L1PcInstance> players;

        // The actor's factory ID stripe serializes changes to this identity across both
        // registries. Never nest their shared radix write stripes.
        void addPlayer(L1PcInstance pc) {
            var roster = players;
            if (roster == null) {
                synchronized (this) {
                    roster = players;
                    if (roster == null) players = roster = new L1IntRegistry<>();
                }
            }
            roster.put(pc.getId(), pc);
        }

        void removePlayer(L1PcInstance pc) {
            var roster = players;
            if (roster != null) roster.remove(pc.getId(), pc);
        }
    }

    private boolean containsPlayer(MapMembers members, L1PcInstance pc, L1Map terrain) {
        int id = pc.getId();
        // The factory installs a replacement before it can lock and retire the old actor.
        return LocalWorldMap.publishedTerrain(pc) == terrain && members.objects.get(id) == pc
                && identities.find(id) == pc;
    }

    CWorldMapObjects(Iterable<Integer> configuredMaps, L1ObjectFactory identities) {
        this.identities = identities;
        for (int id : configuredMaps) {
            if (valid(id)) configured.set(id);
        }
    }

    private static boolean valid(int id) {
        return id >= 0 && id <= CWorld.MAX_MAP_ID;
    }

    private MapMembers slot(int mapId, boolean create) {
        if (!valid(mapId)) return null;
        int pageIndex = mapId >>> PAGE_BITS;
        var page = pages.get(pageIndex);
        if (page == null) {
            if (!create) return null;
            var candidate = new AtomicReferenceArray<MapMembers>(PAGE_SIZE);
            page = pages.compareAndExchange(pageIndex, null, candidate);
            if (page == null) page = candidate;
        }
        int slot = mapId & (PAGE_SIZE - 1);
        var members = page.get(slot);
        if (members == null && create) {
            var candidate = new MapMembers();
            members = page.compareAndExchange(slot, null, candidate);
            if (members == null) members = candidate;
        }
        return members;
    }

    L1IntRegistry<L1Object> registry(int mapId, boolean create) {
        final var members = slot(mapId, create);
        return members == null ? null : members.objects;
    }

    List<L1PcInstance> players(int mapId, L1Map terrain) {
        final var members = slot(mapId, false);
        final var roster = members == null ? null : members.players;
        if (roster == null || roster.isEmpty()) return new ArrayList<>();
        if (terrain == null) terrain = L1WorldMap.getInstance().findMap(mapId);
        var result = new ArrayList<L1PcInstance>(roster.size());
        if (terrain != null) {
            for (L1PcInstance pc : roster.values()) {
                if (containsPlayer(members, pc, terrain)) result.add(pc);
            }
        }
        return result;
    }

    boolean hasOtherPlayer(L1Map terrain, L1PcInstance except) {
        final var members = slot(terrain.getId(), false);
        final var roster = members == null ? null : members.players;
        if (roster != null && !roster.isEmpty()) {
            for (L1PcInstance pc : roster.values()) {
                if (pc != except && containsPlayer(members, pc, terrain)) return true;
            }
        }
        return false;
    }

    L1IntRegistry<L1Object> legacyRegistry(int mapId) {
        return registry(mapId, valid(mapId) && configured.get(mapId));
    }

    Collection<L1Object> members(int mapId) {
        var map = registry(mapId, false);
        return map == null ? List.of() : map.values();
    }

    boolean remove(L1Object object, int mapId) {
        final var members = slot(mapId, false);
        if (members == null || !members.objects.remove(object.getId(), object)) return false;
        if (object instanceof L1PcInstance pc) members.removePlayer(pc);
        return true;
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
        final var members = slot(mapId, true);
        if (members == null) return false;
        final var map = members.objects;
        int id = object.getId();
        final L1Object previous;
        synchronized (map.lockForKey(id)) {
            previous = map.get(id);
            if (previous != null && previous != object && (!canonical || previous instanceof L1GroundInventory))
                return false;
            if (previous != object) map.put(id, object);
        }
        if (previous != object) {
            if (object instanceof L1PcInstance pc) members.addPlayer(pc);
            else if (previous instanceof L1PcInstance old) members.removePlayer(old);
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
