package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import l1j.server.L1DatabaseFactory;
import l1j.server.server.utils.SQLUtil;

public class CreateCharTeleportTable {
	public class BooksTeleportLoc {
		public int _getX;
		public int _getY;
		public int _getMapId;
	}

	private static CreateCharTeleportTable _instance;

	private Map<String, BooksTeleportLoc> _booksTeleport = new HashMap<String, BooksTeleportLoc>();

	public static CreateCharTeleportTable getInstance() {
		if (_instance == null) {
			_instance = new CreateCharTeleportTable();
		}
		return _instance;
	}

	public static void reload() {
		CreateCharTeleportTable oldInstance = _instance;
		_instance = new CreateCharTeleportTable();
		if (oldInstance != null) {
			oldInstance._booksTeleport.clear();
		}
	}

	private CreateCharTeleportTable() {
		selectBooksTeleport();
	}

	private void selectBooksTeleport() {
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("SELECT * FROM c_createchar");
			rs = pstm.executeQuery();
			BooksTeleportLoc bt = null;
			while (rs.next()) {
				bt = new BooksTeleportLoc();
				String bookId = rs.getString("name");
				bt._getX = rs.getInt("locX");
				bt._getY = rs.getInt("locY");
				bt._getMapId = rs.getInt("locMap");
				_booksTeleport.put(bookId, bt);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			SQLUtil.close(rs, pstm, con);
		}
	}

	public BooksTeleportLoc getTeleportLoc(String bookId) {
		BooksTeleportLoc btl = _booksTeleport.get(bookId);
		if (btl == null)
			return null;
		return btl;
	}
}