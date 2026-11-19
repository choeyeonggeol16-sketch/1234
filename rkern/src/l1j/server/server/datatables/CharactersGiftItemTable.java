package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import l1j.server.DatabaseFactory;

public class CharactersGiftItemTable {

	private static final Logger _log = Logger.getLogger(CharactersGiftItemTable.class.getName());

	private static volatile CharactersGiftItemTable _instance;
	
	private final TIntObjectMap<List<Item>> _itemList = new TIntObjectHashMap<>();

	public static CharactersGiftItemTable getInstance() {
		if (_instance == null) {
			synchronized (CharactersGiftItemTable.class) {
				if (_instance == null) {
					_instance = new CharactersGiftItemTable();
				}
			}
		}
		return _instance;
	}

	public static void reload() {
		CharactersGiftItemTable oldInstance = _instance;
		_instance = new CharactersGiftItemTable();
		if (oldInstance != null) {
			synchronized (oldInstance._itemList) {
				oldInstance._itemList.clear();
			}
		}
	}

	private CharactersGiftItemTable() {
		loadItems();
	}

	private void loadItems() {
		String sql = "SELECT level, type, item_name, item_id, count, enchant, attrlevel, bless FROM levelup_quests_item ORDER BY level";
		
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql);
			 ResultSet rs = pstm.executeQuery()) {
			
			synchronized (_itemList) {
				_itemList.clear();
				while (rs.next()) {
					int level = rs.getInt("level");
					
					Item item = new Item();
					item.setType(rs.getInt("type"));
					item.setItemName(rs.getString("item_name"));
					item.setItemId(rs.getInt("item_id"));
					item.setCount(rs.getInt("count"));
					item.setEnchant(rs.getInt("enchant"));
					item.setAttrLevel(rs.getInt("attrlevel"));
					item.setBless(rs.getInt("bless"));
					
					List<Item> list = _itemList.get(level);
					if (list == null) {
						list = new ArrayList<>();
						_itemList.put(level, list);
					}
					list.add(item);
				}
			}
			_log.config("캐릭터 레벨별 지급 아이템 설정 " + _itemList.size() + "개 레벨 구간 로드 완료");
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "loadItems error", e);
		}
	}

	public Item[] getItems(int level) {
		synchronized (_itemList) {
			List<Item> result = _itemList.get(level);
			if (result == null || result.isEmpty()) {
				return null;
			}
			return result.toArray(new Item[result.size()]);
		}
	}

	public static class Item {
		private int type;
		private String itemName;
		private int itemId;
		private int count;
		private int enchant;
		private int attrLevel;
		private int bless;

		public int getType() {
			return type;
		}
		public void setType(int type) {
			this.type = type;
		}
		public String getItemName() {
			return itemName;
		}
		public void setItemName(String itemName) {
			this.itemName = itemName;
		}
		public int getItemId() {
			return itemId;
		}
		public void setItemId(int itemId) {
			this.itemId = itemId;
		}
		public int getCount() {
			return count;
		}
		public void setCount(int count) {
			this.count = count;
		}
		public int getEnchant() {
			return enchant;
		}
		public void setEnchant(int enchant) {
			this.enchant = enchant;
		}
		public int getAttrLevel() {
			return attrLevel;
		}
		public void setAttrLevel(int attrLevel) {
			this.attrLevel = attrLevel;
		}
		public int getBless() {
			return bless;
		}
		public void setBless(int bless) {
			this.bless = bless;
		}
	}
}