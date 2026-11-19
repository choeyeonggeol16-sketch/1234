package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.DatabaseFactory;

public final class MapsTable {

    private static final Logger _log = Logger.getLogger(MapsTable.class.getName());

    // 인스턴스는 volatile로 가시성 확보
    private static volatile MapsTable _instance;

    // 맵 데이터는 불변 객체로
    private static final class MapData {
        final String mapName;
        final int startX, endX, startY, endY;
        final double monster_amount;
        final double dropRate;
        final boolean isUnderwater;
        final boolean markable;
        final boolean teleportable;
        final boolean escapable;
        final boolean isUseResurrection;
        final boolean isUsePainwand;
        final boolean isEnabledDeathPenalty;
        final boolean isTakePets;
        final boolean isRecallPets;
        final boolean isUsableItem;
        final boolean isUsableSkill;
        final boolean is_ruler;
		public long monster_respawn_seconds = 40000L;

        MapData(ResultSet rs) throws SQLException {
            this.mapName = rs.getString("locationname");
            this.startX = rs.getInt("startX");
            this.endX = rs.getInt("endX");
            this.startY = rs.getInt("startY");
            this.endY = rs.getInt("endY");
            this.monster_amount = rs.getDouble("monster_amount");
            this.dropRate = rs.getDouble("drop_rate");
            this.isUnderwater = rs.getBoolean("underwater");
            this.markable = rs.getBoolean("markable");
            this.teleportable = rs.getBoolean("teleportable");
            this.escapable = rs.getBoolean("escapable");
            this.isUseResurrection = rs.getBoolean("resurrection");
            this.isUsePainwand = rs.getBoolean("painwand");
            this.isEnabledDeathPenalty = rs.getBoolean("penalty");
            this.isTakePets = rs.getBoolean("take_pets");
            this.isRecallPets = rs.getBoolean("recall_pets");
            this.isUsableItem = rs.getBoolean("usable_item");
            this.isUsableSkill = rs.getBoolean("usable_skill");
            this.is_ruler = rs.getBoolean("is_ruler");
        }
    }

    private volatile Map<Integer, MapData> _maps = new HashMap<>();

    private MapsTable() {
        loadMapsFromDatabase();
    }

    public static MapsTable getInstance() {
        MapsTable inst = _instance;
        if (inst == null) {
            synchronized (MapsTable.class) {
                inst = _instance;
                if (inst == null) {
                    inst = new MapsTable();
                    _instance = inst;
                }
            }
        }
        return inst;
    }

    private void loadMapsFromDatabase() {
        final Map<Integer, MapData> tmp = new HashMap<>(512); // 대충 넉넉히

        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement pstm = con.prepareStatement("SELECT * FROM mapids");
             ResultSet rs = pstm.executeQuery()) {

            while (rs.next()) {
                final int mapId = rs.getInt("mapid");
                final MapData data = new MapData(rs);
                tmp.put(Integer.valueOf(mapId), data);
            }

            // 한 방에 교체해서 부분 로딩 상태가 보이지 않게
            _maps = tmp;

            _log.config("Maps " + tmp.size());

        } catch (SQLException e) {
            _log.log(Level.SEVERE, "MapsTable loadMapsFromDatabase() failed", e);
            // 실패 시 기존 _maps 유지(운영 안정성)
        }
    }

	public Map<Integer, MapData> getMaps() {
		return _maps;
	}

    public int getStartX(int mapId) {
        MapData map = _maps.get(mapId);
        return (map == null) ? 0 : map.startX;
    }

    public int getEndX(int mapId) {
        MapData map = _maps.get(mapId);
        return (map == null) ? 0 : map.endX;
    }

    public int getStartY(int mapId) {
        MapData map = _maps.get(mapId);
        return (map == null) ? 0 : map.startY;
    }

    public int getEndY(int mapId) {
        MapData map = _maps.get(mapId);
        return (map == null) ? 0 : map.endY;
    }

    public double getMonsterAmount(int mapId) {
        MapData map = _maps.get(mapId);
        return (map == null) ? 0 : map.monster_amount;
    }

    public double getDropRate(int mapId) {
        MapData map = _maps.get(mapId);
        return (map == null) ? 0 : map.dropRate;
    }

    public boolean isUnderwater(int mapId) {
        MapData map = _maps.get(mapId);
        return map != null && map.isUnderwater;
    }

    public boolean isMarkable(int mapId) {
        MapData map = _maps.get(mapId);
        return map != null && map.markable;
    }

    public boolean isTeleportable(int mapId) {
        MapData map = _maps.get(mapId);
        return map != null && map.teleportable;
    }

    public boolean isEscapable(int mapId) {
        MapData map = _maps.get(mapId);
        return map != null && map.escapable;
    }

    public boolean isUseResurrection(int mapId) {
        MapData map = _maps.get(mapId);
        return map != null && map.isUseResurrection;
    }

    public boolean isUsePainwand(int mapId) {
        MapData map = _maps.get(mapId);
        return map != null && map.isUsePainwand;
    }

    public boolean isEnabledDeathPenalty(int mapId) {
        MapData map = _maps.get(mapId);
        return map != null && map.isEnabledDeathPenalty;
    }

    public boolean isTakePets(int mapId) {
        MapData map = _maps.get(mapId);
        return map != null && map.isTakePets;
    }

    public boolean isRecallPets(int mapId) {
        MapData map = _maps.get(mapId);
        return map != null && map.isRecallPets;
    }

    public boolean isUsableItem(int mapId) {
        MapData map = _maps.get(mapId);
        return map != null && map.isUsableItem;
    }

    public boolean isUsableSkill(int mapId) {
        MapData map = _maps.get(mapId);
        return map != null && map.isUsableSkill;
    }

    public String getMapName(int mapId) {
        MapData map = _maps.get(mapId);
        return (map == null) ? null : map.mapName;
    }

    public boolean isRuler(int mapId) {
        MapData map = _maps.get(mapId);
        return map != null && map.is_ruler;
    }
	public long get_monster_respawn_seconds(int mapId){
		MapData map = _maps.get(mapId);
		return map == null ? 40000L : map.monster_respawn_seconds;
	}

}
