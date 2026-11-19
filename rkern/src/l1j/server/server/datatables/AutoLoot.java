package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;

import l1j.server.DatabaseFactory;

public class AutoLoot {

	private static final Logger _log = Logger.getLogger(AutoLoot.class.getName());
	private static AutoLoot _instance;

	private final TIntSet _idlist = new TIntHashSet();

	public static AutoLoot getInstance() {
		if (_instance == null) {
			_instance = new AutoLoot();
		}
		return _instance;
	}

	private AutoLoot() {
		loadIdList();
	}

	public static void reload() {
		AutoLoot oldInstance = _instance;
		_instance = new AutoLoot();
		if (oldInstance != null) {
			oldInstance._idlist.clear();
		}
	}
	
	private void loadIdList() {
		String sql = "SELECT item_id FROM autoloot";

		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql);
			 ResultSet rs = pstm.executeQuery()) {

			_idlist.clear();
			while (rs.next()) {
				_idlist.add(rs.getInt("item_id"));
			}
			_log.config("AutoLoot 설정 " + _idlist.size() + "건 로드 완료");

		} catch (SQLException e) {
			_log.log(Level.SEVERE, "AutoLoot loadIdList Error", e);
		}
	}

	public void storeId(int itemid) {
		if (_idlist.contains(itemid)) {
			return;
		}

		String sql = "INSERT INTO autoloot SET item_id = ?";
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			
			pstm.setInt(1, itemid);
			pstm.execute();
			_idlist.add(itemid);
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "AutoLoot storeId Error", e);
		}
	}

	public void deleteId(int itemid) {
		if (!_idlist.contains(itemid)) {
			return;
		}

		String sql = "DELETE FROM autoloot WHERE item_id = ?";
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			
			pstm.setInt(1, itemid);
			pstm.execute();
			_idlist.remove(itemid);
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "AutoLoot deleteId Error", e);
		}
	}

	public void reload1() {
		loadIdList();
	}

	/**
	 * 기존 ArrayList 반환 호환성을 위해 필요시 배열로 변환해서 제공
	 */
	public ArrayList<Integer> getIdList() {
		ArrayList<Integer> list = new ArrayList<Integer>();
		int[] values = _idlist.toArray();
		for (int i = 0; i < values.length; i++) {
			list.add(values[i]);
		}
		return list;
	}

	public boolean isAutoLoot(int itemId) {
		return _idlist.contains(itemId);
	}
}