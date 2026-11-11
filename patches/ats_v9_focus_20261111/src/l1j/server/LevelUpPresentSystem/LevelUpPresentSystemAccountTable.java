package l1j.server.LevelUpPresentSystem;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;

import l1j.server.L1DatabaseFactory;
import l1j.server.Template.SqlHelper.Executors.Updator;
import l1j.server.Template.SqlHelper.Handler.Handler;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.utils.SQLUtil;

public class LevelUpPresentSystemAccountTable {
	private static LevelUpPresentSystemAccountTable _instance;

	private final Map<String, LevelUpPresentSystemAccount> _present_items = new HashMap<String, LevelUpPresentSystemAccount>();

	public static LevelUpPresentSystemAccountTable getInstance() {
		if (_instance == null) {
			_instance = new LevelUpPresentSystemAccountTable();
		}
		return _instance;
	}
	
	public static void reload() {
		Updator.truncate("character_present_items_account");
		LevelUpPresentSystemAccountTable oldInstance = _instance;
		_instance = new LevelUpPresentSystemAccountTable();
		if(oldInstance != null && oldInstance._present_items != null)
			oldInstance._present_items.clear();
	}

	private LevelUpPresentSystemAccountTable() {
		Connection con = null;
		PreparedStatement ItemIdPS = null;
		ResultSet ItemIdRS = null;
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			ItemIdPS = con.prepareStatement("SELECT distinct(account) as account, present_level FROM character_present_items_account");
			ItemIdRS = ItemIdPS.executeQuery();
			PreparedStatement ItemsPS = null;
			ResultSet ItemsRS = null;
			while (ItemIdRS.next()) {
				try {
					ItemsPS = con.prepareStatement("SELECT item_id, present_level, give_time FROM character_present_items_account WHERE account=? and present_level=?");
					String account = ItemIdRS.getString("account");
					int present_level = ItemIdRS.getInt("present_level");
					ItemsPS.setString(1, account);
					ItemsPS.setInt(2, present_level);
					ItemsRS = ItemsPS.executeQuery();
					LevelUpPresentSystemAccount present_item = new LevelUpPresentSystemAccount(account);
					while (ItemsRS.next()) {
						present_item.add(String.format("%s:%s", account, present_level), ItemsRS.getInt("item_id"), ItemsRS.getTimestamp("give_time") != null ? ItemsRS.getTimestamp("give_time") : null);
					}
					_present_items.put(String.format("%s:%s", account, present_level), present_item);
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					SQLUtil.close(ItemsRS);
					SQLUtil.close(ItemsPS);
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			SQLUtil.close(ItemIdRS);
			SQLUtil.close(ItemIdPS);
			SQLUtil.close(con);
		}
	}

	public LevelUpPresentSystemAccount getPresentTable(String account, int level) {
		LevelUpPresentSystemAccount present_item = _present_items.get(String.format("%s:%d", account, level));
		if (_present_items.containsKey(String.format("%s:%d", account, level))) {
			return present_item;
		}
		return present_item;
	}
	
	public void updatePresentItem(L1PcInstance pc, ItemInstance item, int present_level, boolean timecheck) {
		LevelUpPresentSystemAccount present_item = new LevelUpPresentSystemAccount(pc.getAccountName());
		Updator.exec("insert into character_present_items_account set account=?, item_id=?, item_name=?, present_level=?, give_time=?", new Handler(){
			@Override
			public void handle(PreparedStatement pstm) throws Exception {
				int idx = 0;
				pstm.setString(++idx, pc.getAccountName());
				pstm.setInt(++idx, item.getItemId());
				pstm.setString(++idx, item.getName());
				pstm.setInt(++idx, present_level);
				pstm.setTimestamp(++idx, timecheck ? new Timestamp(System.currentTimeMillis()) : null);
			}
		});
		_present_items.put(String.format("%s:%d", pc.getAccountName(), pc.getLevel()), present_item);
	}
}
