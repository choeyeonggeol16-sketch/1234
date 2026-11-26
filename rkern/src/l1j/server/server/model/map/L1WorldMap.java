package l1j.server.server.model.map;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import l1j.server.MapReader;

public class L1WorldMap {
	private static final class Holder {
		private static final L1WorldMap INSTANCE = new L1WorldMap();
	}
	private final Map<Integer, L1Map> _maps = new ConcurrentHashMap<>();

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

	public L1Map getMap(short mapId) {
		L1Map map = _maps.get((int) mapId);
		if (map == null) { 
			map = L1Map.newNull(); 
		}
		return map;
	}
	
	public void cloneMap(int targetId, int newId){//레이드
		L1Map copymap = null;
		copymap = _maps.get(targetId).copyMap(newId);
		_maps.put(newId, copymap);
	}
	
	public L1Map cloneMapAndGet(int targetId, int newId){
		L1Map copymap = null;
		copymap = _maps.get(targetId).copyMap(newId);
		_maps.put(newId, copymap);
		return copymap;
	}
	
	// 오림 인던 관련
	public void addMap(L1Map map) {
		_maps.put(map.getId(), map);
	}

	public void removeMap(int mapId) {
		_maps.remove(mapId);
	}
}
