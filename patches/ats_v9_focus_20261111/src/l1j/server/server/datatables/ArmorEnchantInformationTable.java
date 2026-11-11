package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import l1j.server.L1DatabaseFactory;
import l1j.server.server.utils.SQLUtil;

public class ArmorEnchantInformationTable {
	private static ArmorEnchantInformationTable _instance;

	private final Map<Integer, HashMap<Integer, Integer>> _list = new HashMap<Integer, HashMap<Integer, Integer>>();

	public static ArmorEnchantInformationTable getInstance() {
		if (_instance == null) {
			_instance = new ArmorEnchantInformationTable();
		}
		return _instance;
	}

	private ArmorEnchantInformationTable() {
		loadInformation();	
	}

	public static void reload() {
		ArmorEnchantInformationTable oldInstance = _instance;
		_instance = new ArmorEnchantInformationTable();
		oldInstance._list.clear();
	}

	private void loadInformation() {
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("SELECT * FROM setting_armor_enchant_list WHERE item_id = ?");
			for (int items : enumItemIds()) {
				HashMap<Integer, Integer> _optionMap = new HashMap<Integer, Integer>();
				pstm.setInt(1, items);
				rs = pstm.executeQuery();
				while (rs.next()) {
					_optionMap.put(rs.getInt("enchant_lvl"), rs.getInt("chance"));
				}
				_list.put(items, _optionMap);
				rs.close();
			}
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}

	private ArrayList<Integer> enumItemIds() {
		ArrayList<Integer> ids = new ArrayList<Integer>();

		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("SELECT DISTINCT item_id FROM setting_armor_enchant_list");
			rs = pstm.executeQuery();
			while (rs.next()) {
				ids.add(rs.getInt("item_id"));
			}
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			SQLUtil.close(rs, pstm, con);
		}
		return ids;
	}

	public int getChance(int itemId, int enchant) {
		if (_list.get(itemId) == null || _list.get(itemId).get(enchant) == null || _list.get(itemId).isEmpty()) {
			return 0;
		}
		return _list.get(itemId).get(enchant);
	}
}