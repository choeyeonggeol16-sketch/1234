package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;
import l1j.server.DatabaseFactory;

public class NoTradable {

	private static final Logger _log = Logger.getLogger(NoTradable.class.getName());

	private static volatile NoTradable _instance;

	private final TIntSet _idlist = new TIntHashSet();

	public static NoTradable getInstance() {
		if (_instance == null) {
			synchronized (NoTradable.class) {
				if (_instance == null) {
					_instance = new NoTradable();
				}
			}
		}
		return _instance;
	}

	private NoTradable() {
		loadIdList();
	}

	private void loadIdList() {
		String sql = "SELECT item_id FROM NoTradable";
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql);
			 ResultSet rs = pstm.executeQuery()) {
			
			synchronized (_idlist) {
				_idlist.clear();
				while (rs.next()) {
					_idlist.add(rs.getInt("item_id"));
				}
			}
			_log.config("거래 불가 아이템 목록 " + _idlist.size() + "건 로드 완료");
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "loadIdList error", e);
		}
	}
	
	public void storeId(int itemid) {
		synchronized (_idlist) {
			if (_idlist.contains(itemid)) {
				return;
			}
		}

		String sql = "INSERT INTO NoTradable SET item_id=?";
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			
			pstm.setInt(1, itemid);
			pstm.execute();
			
			synchronized (_idlist) {
				_idlist.add(itemid);
			}
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "storeId error [ItemID: " + itemid + "]", e);
		}
	}

	public void deleteId(int itemid) {
		synchronized (_idlist) {
			if (!_idlist.contains(itemid)) {
				return;
			}
		}

		String sql = "DELETE FROM NoTradable WHERE item_id=?";
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			
			pstm.setInt(1, itemid);
			pstm.execute();
			
			synchronized (_idlist) {
				_idlist.remove(itemid);
			}
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "deleteId error [ItemID: " + itemid + "]", e);
		}
	}

	public void reload() {
		loadIdList();
	}

	public int[] getIdList() {
		synchronized (_idlist) {
			return _idlist.toArray();
		}
	}

	public boolean isNoTradable(int itemId) {
		synchronized (_idlist) {
			return _idlist.contains(itemId);
		}
	}
}