package l1j.server.server.model.item.drop;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;

import l1j.server.DatabaseFactory;

public final class DropItemTable {
	
	public static final class DropItemData {
		public double dropRate = 1;
		public double dropAmount = 1;
	}

	private static final Logger _log = Logger.getLogger(DropItemTable.class.getName());
	private static DropItemTable _instance;

	private final TIntObjectMap<DropItemData> _dropItem = new TIntObjectHashMap<DropItemData>(1024);

	public static DropItemTable getInstance() {
		if (_instance == null) {
			_instance = new DropItemTable();
		}
		return _instance;
	}

	private DropItemTable() {
		loadMapsFromDatabase();
	}

	public static void reload() {
		DropItemTable oldInstance = _instance;
		_instance = new DropItemTable();
		if (oldInstance != null) {
			oldInstance._dropItem.clear();
		}
	}

	private void loadMapsFromDatabase() {
		String sql = "SELECT * FROM drop_item";

		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql);
			 ResultSet rs = pstm.executeQuery()) {

			while (rs.next()) {
				int itemId = rs.getInt("item_id");
				DropItemData data = new DropItemData();
				data.dropRate = rs.getDouble("drop_rate");
				data.dropAmount = rs.getDouble("drop_amount");

				_dropItem.put(itemId, data);
			}

			_log.config("drop_item 설정 " + _dropItem.size() + "건 로드 완료");

		} catch (SQLException e) {
			_log.log(Level.SEVERE, "DropItemTable load error", e);
		}
	}

	public double getDropRate(int itemId) {
		DropItemData data = _dropItem.get(itemId);
		if (data == null) {
			return 1;
		}
		return data.dropRate;
	}

	public double getDropAmount(int itemId) {
		DropItemData data = _dropItem.get(itemId);
		if (data == null) {
			return 1;
		}
		return data.dropAmount;
	}
}