package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

import l1j.server.L1DatabaseFactory;
import l1j.server.server.utils.SQLUtil;

public class MapReturnTable {
	private static MapReturnTable _instance;
	private Map<Integer, Integer> _list = new HashMap<Integer, Integer>();

	public static MapReturnTable getInstance() {
		if (_instance == null) {
			_instance = new MapReturnTable();
		}
		return _instance;
	}

	private MapReturnTable() {
		loadMapChange();
	}

	public static void reload() {
		MapReturnTable oldInstance = _instance;
		_instance = new MapReturnTable();
		oldInstance._list.clear();
	}

	private void loadMapChange() {
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("SELECT * FROM MemoryPossible");
			rs = pstm.executeQuery();

			while (rs.next()) {
				int mapId = rs.getInt("MapId");
				int mapType = 0;
				_list.put(mapId, mapType);
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			SQLUtil.close(rs, pstm, con);
		}
	}

	public boolean isMapZoneChange(int id) {
		return this._list.containsKey(id);
	}

	public int getMapZoneType(int id) {
		return ((Integer) _list.get(Integer.valueOf(id))).intValue();
	}
}