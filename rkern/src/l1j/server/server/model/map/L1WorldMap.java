package l1j.server.server.model.map;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import l1j.server.MapReader;
import l1j.server.server.model.world.instance.CInstantZoneManager;

public class L1WorldMap {
	private static final class Holder {
		private static final L1WorldMap INSTANCE = new L1WorldMap();
	}
	private final Map<Integer, L1Map> _maps = new ConcurrentHashMap<>();
    // Present from reservation until retirement. Protects new zones even from
    // older callers whose removal API carries only a numeric map ID.
    private final Set<Integer> zoneIds = new HashSet<>();

	public static L1WorldMap getInstance() {
		return Holder.INSTANCE;
	}

	private L1WorldMap() {
		MapReader in = MapReader.getDefaultReader();

		try {
			final Map<Integer, L1Map> maps = in.read();
			if (maps == null) {
				throw new RuntimeException("MAP의 read에 실패");
			}
			_maps.putAll(maps);
		} catch (Exception e) {
			e.printStackTrace();

			System.exit(0);
		}
	}

    L1WorldMap(Map<Integer, L1Map> initialMaps) {
        _maps.putAll(initialMaps);
    }

    public L1Map getMap(short mapId) {
        final L1Map map = findMap(mapId);
        return map == null ? L1Map.newNull() : map;
    }

    public L1Map findMap(int mapId) {
        return _maps.get(mapId);
    }

    /** Reserve a free ID, then copy terrain outside the publication monitor. */
    public L1Map tryCloneMap(int templateId, int instanceId) {
        if (instanceId < 0 || instanceId > Short.MAX_VALUE)
            throw new IllegalArgumentException("Instance map ID outside protocol range: " + instanceId);
        final L1Map source = requireMap(templateId);
        synchronized (zoneIds) {
            if (_maps.containsKey(instanceId) || !zoneIds.add(instanceId)) return null;
        }

        boolean published = false;
        try {
            final L1Map clone = source.copyMap(instanceId);
            if (clone == null || clone == source || clone.getId() != instanceId)
                throw new IllegalStateException("Invalid copy of map " + templateId);
            synchronized (zoneIds) {
                _maps.put(instanceId, clone);
                published = true;
            }
            return clone;
        } finally {
            if (!published) {
                synchronized (zoneIds) { zoneIds.remove(instanceId); }
            }
        }
    }

    /** Retire only this generation of a zone created with tryCloneMap. */
    public boolean removeMap(L1Map expected) {
        if (expected == null) return false;
        final int id = expected.getId();
        synchronized (zoneIds) {
            if (!zoneIds.contains(id) || _maps.get(id) != expected) return false;
            _maps.remove(id);
            zoneIds.remove(id);
            return true;
        }
    }

    private L1Map requireMap(int mapId) {
        final L1Map map = findMap(mapId);
        if (map == null) throw new IllegalArgumentException("Unknown map template: " + mapId);
        return map;
    }

    public void cloneMap(int targetId, int newId) {
        cloneMapAndGet(targetId, newId);
    }

    public L1Map cloneMapAndGet(int targetId, int newId) {
        final L1Map copy = requireMap(targetId).copyMap(newId);
        addMap(copy);
        return copy;
    }

    public void addMap(L1Map map) {
        final int id = map.getId();
        synchronized (zoneIds) {
            if ((zoneIds.contains(id) || CInstantZoneManager.destination(id) != null) && _maps.get(id) != map)
                throw new IllegalStateException("Map ID belongs to an instance zone: " + id);
            _maps.put(id, map);
        }
    }

    public void removeMap(int mapId) {
        synchronized (zoneIds) {
            if (!zoneIds.contains(mapId) && CInstantZoneManager.destination(mapId) == null) _maps.remove(mapId);
        }
    }
}
