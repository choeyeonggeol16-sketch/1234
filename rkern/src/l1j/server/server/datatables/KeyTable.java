package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.DatabaseFactory;
import l1j.server.server.model.Instance.L1ItemInstance;

public class KeyTable {

	private static final Logger _log = Logger.getLogger(KeyTable.class.getName());

	private KeyTable() {
	}

	public static void StoreKey(L1ItemInstance item) {
		String sql = "INSERT INTO item_key_boss SET item_obj_id=?, key_id=?";
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			pstm.setInt(1, item.getId());
			pstm.setInt(2, item.getKeyId());
			pstm.execute();
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "StoreKey error for item id: " + item.getId(), e);
		}
	}

    public static void storeTrainingKeys(Connection con, int mapId, int[] keyIds) throws SQLException {
        try (PreparedStatement insert = con.prepareStatement(
                "INSERT INTO item_key_boss (item_obj_id,key_id) VALUES (?,?)")) {
            insert.setInt(2, mapId);
            for (int keyId : keyIds) {
                insert.setInt(1, keyId);
                insert.addBatch();
            }
            for (int rows : insert.executeBatch()) {
                if (rows != 1 && rows != java.sql.Statement.SUCCESS_NO_INFO)
                    throw new SQLException("Training room key insert failed: " + mapId);
            }
        }
    }

	public static void DeleteKey(L1ItemInstance item) {
		String sql = "DELETE FROM item_key_boss WHERE item_obj_id=?";
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			pstm.setInt(1, item.getId());
			pstm.execute();
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "DeleteKey error for item id: " + item.getId(), e);
		}
	}
	
	public static void DeleteKeyId(int keyId) {
		String sql = "DELETE FROM item_key_boss WHERE key_id=?";
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			pstm.setInt(1, keyId);
			pstm.execute();
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "DeleteKeyId error for keyId: " + keyId, e);
		}
	}
	
	public static void initBossKey() {
		String[] queries = {
			"TRUNCATE TABLE `item_key_boss`",
			"DELETE FROM character_items WHERE item_id = 80500",
			"DELETE FROM character_elf_warehouse WHERE item_id = 80500",
			"DELETE FROM clan_warehouse WHERE item_id = 80500",
			"DELETE FROM character_warehouse WHERE item_id = 80500"
		};

		try (Connection con = DatabaseFactory.getInstance().getConnection()) {
			for (String sql : queries) {
				try (PreparedStatement pstm = con.prepareStatement(sql)) {
					pstm.execute();
				}
			}
			_log.config("보스 키 및 80500 아이템 데이터 초기화 완료");
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "initBossKey error", e);
		}
	}

    /** Resolve all loaded boss keys on the inventory connection. A legacy object can
     * have multiple mappings; its first key in composite primary-key order wins. */
    public static void loadInventoryKeys(Connection con, int ownerId, Map<Integer, L1ItemInstance> keys) throws SQLException {
        String sql = "SELECT k.item_obj_id, MIN(k.key_id) FROM character_items i "
                + "JOIN item_key_boss k ON k.item_obj_id=i.id "
                + "WHERE i.char_id=? AND i.item_id=80500 GROUP BY k.item_obj_id";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, ownerId);
            try (ResultSet rows = ps.executeQuery()) {
                while (rows.next()) {
                    L1ItemInstance key = keys.get(rows.getInt(1));
                    if (key != null) key.setKeyId(rows.getInt(2));
                }
            }
        }
    }

	public static boolean checkey(L1ItemInstance item) {
		String sql = "SELECT key_id FROM item_key_boss WHERE item_obj_id=?";
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			
			pstm.setInt(1, item.getId());
			
			try (ResultSet rs = pstm.executeQuery()) {
				if (rs.next()) {
					item.setKeyId(rs.getInt("key_id"));
					return true;
				}
			}
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "checkey error for item id: " + item.getId(), e);
		}
		return false;
	}
}