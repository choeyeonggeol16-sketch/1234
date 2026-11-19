package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;

import l1j.server.DatabaseFactory;
import l1j.server.server.templates.L1GetBackRestart;

public class GetBackRestartTable {
	private static final Logger _log = Logger.getLogger(GetBackRestartTable.class.getName());
	private static GetBackRestartTable _instance;

	private final TIntObjectMap<L1GetBackRestart> _getbackrestart = new TIntObjectHashMap<L1GetBackRestart>(64);

	public static GetBackRestartTable getInstance() {
		if (_instance == null) {
			_instance = new GetBackRestartTable();
		}
		return _instance;
	}

	public static void reload() {
		GetBackRestartTable oldInstance = _instance;
		_instance = new GetBackRestartTable();
		if (oldInstance != null) {
			oldInstance._getbackrestart.clear();
		}
	}

	private GetBackRestartTable() {
		load();
	}

	private void load() {
		_getbackrestart.clear();
		String sql = "SELECT * FROM getback_restart";

		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql);
			 ResultSet rs = pstm.executeQuery()) {

			while (rs.next()) {
				L1GetBackRestart gbr = new L1GetBackRestart();
				int area = rs.getInt("area");
				gbr.setArea(area);
				gbr.setLocX(rs.getInt("locx"));
				gbr.setLocY(rs.getInt("locy"));
				gbr.setMapId(rs.getShort("mapid"));
				
				_getbackrestart.put(area, gbr);
			}
			_log.config("귀환/리스폰 정보 " + _getbackrestart.size() + "건 로드 완료");

		} catch (SQLException e) {
			_log.log(Level.SEVERE, "GetBackRestartTable load error", e);
		}
	}

	public L1GetBackRestart getRestartInfo(int mapId) {
		return _getbackrestart.get(mapId);
	}
}