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
import l1j.server.server.model.L1Buddy;

public class BuddyTable {

	private static final Logger _log = Logger.getLogger(BuddyTable.class.getName());
	private static BuddyTable _instance;

	private final TIntObjectMap<L1Buddy> _buddys = new TIntObjectHashMap<L1Buddy>(512);

	public static BuddyTable getInstance() {
		if (_instance == null) {
			_instance = new BuddyTable();
		}
		return _instance;
	}

	private BuddyTable() {
		loadBuddyList();
	}

	private void loadBuddyList() {
		String sql = "SELECT char_id, buddy_id, buddy_name FROM character_buddys";

		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql);
			 ResultSet rs = pstm.executeQuery()) {

			while (rs.next()) {
				int charId = rs.getInt("char_id");
				int buddyId = rs.getInt("buddy_id");
				String buddyName = rs.getString("buddy_name");

				L1Buddy buddy = _buddys.get(charId);
				if (buddy == null) {
					buddy = new L1Buddy(charId);
					_buddys.put(charId, buddy);
				}
				buddy.add(buddyId, buddyName);
			}
			_log.config("loaded " + _buddys.size() + " character's buddylists");

		} catch (SQLException e) {
			_log.log(Level.SEVERE, "BuddyTable load error", e);
		}
	}

	public L1Buddy getBuddyTable(int charId) {
	    L1Buddy buddy = _buddys.get(charId);
	    if (buddy == null) {
	        synchronized (this) {   
	            buddy = _buddys.get(charId);
	            if (buddy == null) {
	                buddy = new L1Buddy(charId);
	                _buddys.put(charId, buddy);
	            }
	        }
	    }
	    return buddy;
	}

	public void addBuddy(int charId, int objId, String name) {
		String sql = "INSERT INTO character_buddys SET char_id = ?, buddy_id = ?, buddy_name = ?";

		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			
			pstm.setInt(1, charId);
			pstm.setInt(2, objId);
			pstm.setString(3, name);
			pstm.execute();
			
			// 메모리 맵에도 실시간 반영
			L1Buddy buddy = getBuddyTable(charId);
			buddy.add(objId, name);

		} catch (SQLException e) {
			_log.log(Level.SEVERE, "BuddyTable addBuddy error", e);
		}
	}

	public void removeBuddy(int charId, String buddyName) {
		L1Buddy buddy = getBuddyTable(charId);
		if (!buddy.containsName(buddyName)) {
			return;
		}

		String sql = "DELETE FROM character_buddys WHERE char_id = ? AND buddy_name = ?";

		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			
			pstm.setInt(1, charId);
			pstm.setString(2, buddyName);
			pstm.execute();

			buddy.remove(buddyName);

		} catch (SQLException e) {
			_log.log(Level.SEVERE, "BuddyTable removeBuddy error", e);
		}
	}
}