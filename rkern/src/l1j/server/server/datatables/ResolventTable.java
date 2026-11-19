package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import gnu.trove.map.TIntIntMap;
import gnu.trove.map.hash.TIntIntHashMap;

import l1j.server.DatabaseFactory;

public final class ResolventTable {
	private static final Logger _log = Logger.getLogger(ResolventTable.class.getName());

	private static ResolventTable _instance;

	private final TIntIntMap _resolvent = new TIntIntHashMap(512);

	public static ResolventTable getInstance() {
		if (_instance == null) {
			_instance = new ResolventTable();
		}
		return _instance;
	}

	private ResolventTable() {
		loadMapsFromDatabase();
	}

	public static void reload() {
		ResolventTable oldInstance = _instance;
		_instance = new ResolventTable();
		if (oldInstance != null) {
			oldInstance._resolvent.clear();
		}
	}
	
	private void loadMapsFromDatabase() {
		String sql = "SELECT * FROM resolvent";

		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql);
			 ResultSet rs = pstm.executeQuery()) {

			while (rs.next()) {
				int itemId = rs.getInt("item_id");
				int crystalCount = rs.getInt("crystal_count");

				_resolvent.put(itemId, crystalCount);
			}

			_log.config("resolvent 설정 " + _resolvent.size() + "건 로드 완료");

		} catch (SQLException e) {
			_log.log(Level.SEVERE, "ResolventTable load error", e);
		}
	}

	public int getCrystalCount(int itemId) {
		return _resolvent.get(itemId);
	}
}