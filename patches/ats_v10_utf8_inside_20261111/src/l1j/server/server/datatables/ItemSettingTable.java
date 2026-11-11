package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.L1DatabaseFactory;
import l1j.server.Templates.L1Item;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.utils.SQLUtil;

public class ItemSettingTable {
	private static Logger _log = Logger.getLogger(ItemSettingTable.class.getName());
	private static ItemSettingTable _instance;
	private static Map<Integer, EnchantSetting> _enchantSettings = new HashMap<Integer, EnchantSetting>();

	public static ItemSettingTable getInstance() {
		if (_instance == null) {
			_instance = new ItemSettingTable();
		}
		return _instance;
	}

	private ItemSettingTable() {
		loadEnchantSettings(_enchantSettings);
	}

	private EnchantSetting loadGeneralEnchantSetting(ResultSet rs) throws SQLException {
		EnchantSetting enchantSetting = new EnchantSetting();
		enchantSetting.max = rs.getInt("max");
		enchantSetting.chance_0 = rs.getInt("chance_0");
		enchantSetting.chance_1 = rs.getInt("chance_1");
		enchantSetting.chance_2 = rs.getInt("chance_2");
		enchantSetting.chance_3 = rs.getInt("chance_3");
		enchantSetting.chance_4 = rs.getInt("chance_4");
		enchantSetting.chance_5 = rs.getInt("chance_5");
		enchantSetting.chance_6 = rs.getInt("chance_6");
		enchantSetting.chance_7 = rs.getInt("chance_7");
		enchantSetting.chance_8 = rs.getInt("chance_8");
		enchantSetting.chance_9 = rs.getInt("chance_9");
		enchantSetting.chance_10 = rs.getInt("chance_10");
		enchantSetting.chance_11 = rs.getInt("chance_11");
		enchantSetting.chance_12 = rs.getInt("chance_12");
		enchantSetting.chance_13 = rs.getInt("chance_13");
		enchantSetting.chance_14 = rs.getInt("chance_14");
		enchantSetting.chance_15 = rs.getInt("chance_15");

		return enchantSetting;
	}

	private void loadEnchantSettings(Map<Integer, EnchantSetting> enchantSettings) {
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("SELECT * FROM setting_enchant_accessory");
			rs = pstm.executeQuery();
			while (rs.next()) {
				int item_id = rs.getInt("item_id");
				L1Item temp = ItemTable.getInstance().getTemplate(item_id);
				if (temp == null) {
					System.out.println("[ItemSettingTable] 존재하지 않는 아이템번호: " + item_id);
					continue;
				}
				EnchantSetting enchantSetting = loadGeneralEnchantSetting(rs);

				enchantSettings.put(Integer.valueOf(item_id), enchantSetting);
			}
			rs.close();
			pstm.close();

			pstm = con.prepareStatement("SELECT * FROM setting_enchant_armor");
			rs = pstm.executeQuery();
			while (rs.next()) {
				int item_id = rs.getInt("item_id");
				L1Item temp = ItemTable.getInstance().getTemplate(item_id);
				if (temp == null) {
					System.out.println("[ItemSettingTable] 존재하지 않는 아이템번호: " + item_id);
					continue;
				}
				EnchantSetting enchantSetting = loadGeneralEnchantSetting(rs);

				enchantSettings.put(Integer.valueOf(item_id), enchantSetting);
			}
			rs.close();
			pstm.close();

			pstm = con.prepareStatement("SELECT * FROM setting_enchant_weapon");
			rs = pstm.executeQuery();
			while (rs.next()) {
				int item_id = rs.getInt("item_id");
				L1Item temp = ItemTable.getInstance().getTemplate(item_id);
				if (temp == null) {
					System.out.println("[ItemSettingTable] 존재하지 않는 아이템번호: " + item_id);
					continue;
				}
				EnchantSetting enchantSetting = loadGeneralEnchantSetting(rs);

				enchantSettings.put(Integer.valueOf(item_id), enchantSetting);
			}
		} catch (SQLException e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		} finally {
			SQLUtil.close(rs, pstm, con);
		}
	}



	public void reload() {
		Map<Integer, EnchantSetting> enchantSettings = new HashMap<Integer, EnchantSetting>();
		loadEnchantSettings(enchantSettings);
		_enchantSettings = enchantSettings;
	}

	public int getEnchantMax(int item_id) {
		EnchantSetting enchantSetting = (EnchantSetting) _enchantSettings.get(Integer.valueOf(item_id));
		if (enchantSetting == null) {
			return -1;
		}
		return enchantSetting.max;
	}

	public double getEnchantChance(ItemInstance item) {
		EnchantSetting enchantSetting = (EnchantSetting) _enchantSettings.get(Integer.valueOf(item.getItemId()));
		if (enchantSetting == null) {
			return 0.0D;
		}
		double chance = 0.0D;
		switch (item.getEnchantLevel()) {
		case 0:
			chance = enchantSetting.chance_0;
			break;
		case 1:
			chance = enchantSetting.chance_1;
			break;
		case 2:
			chance = enchantSetting.chance_2;
			break;
		case 3:
			chance = enchantSetting.chance_3;
			break;
		case 4:
			chance = enchantSetting.chance_4;
			break;
		case 5:
			chance = enchantSetting.chance_5;
			break;
		case 6:
			chance = enchantSetting.chance_6;
			break;
		case 7:
			chance = enchantSetting.chance_7;
			break;
		case 8:
			chance = enchantSetting.chance_8;
			break;
		case 9:
			chance = enchantSetting.chance_9;
			break;
		case 10:
			chance = enchantSetting.chance_10;
			break;
		case 11:
			chance = enchantSetting.chance_11;
			break;
		case 12:
			chance = enchantSetting.chance_12;
			break;
		case 13:
			chance = enchantSetting.chance_13;
			break;
		case 14:
			chance = enchantSetting.chance_14;
			break;
		case 15:
			chance = enchantSetting.chance_15;
		}
		return chance;
	}

	private class EnchantSetting {
		int max = -1;
		double chance_0 = 0.0D;
		double chance_1 = 0.0D;
		double chance_2 = 0.0D;
		double chance_3 = 0.0D;
		double chance_4 = 0.0D;
		double chance_5 = 0.0D;
		double chance_6 = 0.0D;
		double chance_7 = 0.0D;
		double chance_8 = 0.0D;
		double chance_9 = 0.0D;
		double chance_10 = 0.0D;
		double chance_11 = 0.0D;
		double chance_12 = 0.0D;
		double chance_13 = 0.0D;
		double chance_14 = 0.0D;
		double chance_15 = 0.0D;

		private EnchantSetting() {
		}
	}
}