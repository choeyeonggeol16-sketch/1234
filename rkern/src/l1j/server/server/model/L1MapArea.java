package l1j.server.server.model;

import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1WorldMap;
import l1j.server.server.types.Rectangle;

public class L1MapArea extends Rectangle {

    // map 객체는 필요할 때만 resolve
    private volatile L1Map _map;     // lazy cache
    private short _mapId;

    public L1MapArea(int left, int top, int right, int bottom, int mapId) {
        super(left, top, right, bottom);
        _mapId = (short) mapId;
        _map = null; // lazy
    }

    public int getMapId() {
        return _mapId;
    }

    public void setMapId(int mapId) {
        _mapId = (short) mapId;
        _map = null; // invalidate cache
    }

    /** 정말 맵 객체가 필요한 경우만 resolve */
    public L1Map getMap() {
        L1Map m = _map;
        if (m == null || m.getId() != _mapId) { 
            m = L1WorldMap.getInstance().getMap(_mapId);
            _map = m;
        }
        return m;
    }

    /** 기존 호환: map 객체 직접 세팅 */
    public void setMap(L1Map map) {
        _map = map;
        _mapId = (map != null) ? (short) map.getId() : 0;
    }

    /** 가장 빠른 형태: L1Location 생성도 안 함 */
    public boolean contains(int x, int y, int mapId) {
        if ((short) mapId != _mapId) return false;
        return super.contains(x, y);
    }

    /** 기존 API 호환 */
    public boolean contains(L1Location loc) {
        if (loc == null) return false;
        if (loc.getMapId() != _mapId) return false;
        return super.contains(loc);
    }
}
