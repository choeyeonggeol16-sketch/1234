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

public class LevelUpPresentSystemCharacterTable {
	private static LevelUpPresentSystemCharacterTable _instance;

	private final Map<String, LevelUpPresentSystemCharacter> _present_items = new HashMap<String, LevelUpPresentSystemCharacter>();

	public static LevelUpPresentSystemCharacterTable getInstance() {
		if (_instance == null) {
			_instance = new LevelUpPresentSystemCharacterTable();
		}
		return _instance;
	}
	
	public static void reload() {
		Updator.truncate("character_present_items_character");
		LevelUpPresentSystemCharacterTable oldInstance = _instance;
		_instance = new LevelUpPresentSystemCharacterTable();
		if(oldInstance != null && oldInstance._present_items != null)
			oldInstance._present_items.clear();
	}

	private LevelUpPresentSystemCharacterTable() {
		Connection con = null;
		PreparedStatement ItemIdPS = null;
		ResultSet ItemIdRS = null;
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			ItemIdPS = con.prepareStatement("SELECT distinct(char_id) as char_id, present_level FROM character_present_items_character");
			ItemIdRS = ItemIdPS.executeQuery();
			PreparedStatement ItemsPS = null;
			ResultSet ItemsRS = null;
			while (ItemIdRS.next()) {
				try {
					ItemsPS = con.prepareStatement("SELECT item_id, present_level, give_time FROM character_present_items_character WHERE char_id=? and present_level=?");
					int charId = ItemIdRS.getInt("char_id");
					int present_level = ItemIdRS.getInt("present_level");
					ItemsPS.setInt(1, charId);
					ItemsPS.setInt(2, present_level);
					LevelUpPresentSystemCharacter present_item = new LevelUpPresentSystemCharacter(charId);
					ItemsRS = ItemsPS.executeQuery();
					while (ItemsRS.next()) {
						present_item.add(String.format("%d:%d", charId, present_level), ItemsRS.getInt("item_id"), ItemsRS.getTimestamp("give_time") != null ? ItemsRS.getTimestamp("give_time") : null);
					}
					_present_items.put(String.format("%d:%d", charId, present_level), present_item);
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
	
	public LevelUpPresentSystemCharacter getPresentTable(L1PcInstance pc, int level) {
		LevelUpPresentSystemCharacter present_item = _present_items.get(String.format("%s:%s", pc.getId(), level));
		if (_present_items.containsKey(String.format("%d:%d", pc.getId(), level))) {
			return present_item;
		}
		return present_item;
	}

	public void updatePresentItem(L1PcInstance pc, ItemInstance item, boolean timecheck) {
		LevelUpPresentSystemCharacter present_item = new LevelUpPresentSystemCharacter(pc.getId());
		Updator.exec("insert into character_present_items_character set char_id=?, char_name=?, item_id=?, item_name=?, present_level=?, give_time=?", new Handler(){
			@Override
			public void handle(PreparedStatement pstm) throws Exception {
				int idx = 0;
				pstm.setInt(++idx, pc.getId());
				pstm.setString(++idx, pc.getName());
				pstm.setInt(++idx, item.getItemId());
				pstm.setString(++idx, item.getName());
				pstm.setInt(++idx, pc.getLevel());
				pstm.setTimestamp(++idx, timecheck ? new Timestamp(System.currentTimeMillis()) : null);
			}
		});
		_present_items.put(String.format("%d:%d", pc.getId(), pc.getLevel()), present_item);
	}
}
