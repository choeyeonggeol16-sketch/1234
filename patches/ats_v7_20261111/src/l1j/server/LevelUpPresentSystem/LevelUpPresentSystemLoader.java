package l1j.server.LevelUpPresentSystem;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import l1j.server.L1DatabaseFactory;
import l1j.server.server.utils.SQLUtil;

public class LevelUpPresentSystemLoader {
	private static LevelUpPresentSystemLoader _instance;
	private Map<Integer, L1PresentItems> _list = new HashMap<Integer, L1PresentItems>();

	public static LevelUpPresentSystemLoader getInstance() {
		if (_instance == null) {
			_instance = new LevelUpPresentSystemLoader();
		}
		return _instance;
	}

	public LevelUpPresentSystemLoader() {
		loadLevelUpPresentSystem(_list);
	}

	public void reload() {
		Map<Integer, L1PresentItems> list = new HashMap<Integer, L1PresentItems>();
		loadLevelUpPresentSystem(list);
		_list = list;
	}

	public void loadLevelUpPresentSystem(Map<Integer, L1PresentItems> list) {
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		L1PresentItems present_items = null;
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("SELECT * FROM level_present_items");
			rs = pstm.executeQuery();
			while (rs.next()) {
				present_items = new L1PresentItems();
				present_items.setItemId(rs.getInt("item_id"));
				present_items.setItemCount(rs.getInt("item_count"));
			//	present_items.setItemBless(BlessType(rs.getString("item_bless")));
				present_items.setItemEnchantLevel(rs.getInt("item_enchant_level"));
				present_items.setItemAttrLevel(AttrType(rs.getString("item_attr_level")));
				present_items.setLevel(rs.getInt("give_level"));
				present_items.setPresentType(PresentType(rs.getString("type")));
				list.put(present_items.getLevel(), present_items);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}
	
	public int BlessType(String type) {
		if(type.equalsIgnoreCase("Bless"))
			return 0;
		else if(type.equalsIgnoreCase("Normal"))
			return 1;
		else if(type.equalsIgnoreCase("Curse"))
			return 2;
		return 1;
	}
	
	public int AttrType(String type) {
		if(type.equalsIgnoreCase("화령:1단"))
			return 1;
		else if(type.equalsIgnoreCase("화령:2단"))
			return 2;
		else if(type.equalsIgnoreCase("화령:3단"))
			return 3;
		else if(type.equalsIgnoreCase("화령:4단"))
			return 4;
		else if(type.equalsIgnoreCase("화령:5단"))
			return 5;
		else if(type.equalsIgnoreCase("수령:1단"))
			return 6;
		else if(type.equalsIgnoreCase("수령:2단"))
			return 7;
		else if(type.equalsIgnoreCase("수령:3단"))
			return 8;
		else if(type.equalsIgnoreCase("수령:4단"))
			return 9;
		else if(type.equalsIgnoreCase("수령:5단"))
			return 10;
		else if(type.equalsIgnoreCase("풍령:1단"))
			return 11;
		else if(type.equalsIgnoreCase("풍령:2단"))
			return 12;
		else if(type.equalsIgnoreCase("풍령:3단"))
			return 13;
		else if(type.equalsIgnoreCase("풍령:4단"))
			return 14;
		else if(type.equalsIgnoreCase("풍령:5단"))
			return 15;
		else if(type.equalsIgnoreCase("지령:1단"))
			return 16;
		else if(type.equalsIgnoreCase("지령:2단"))
			return 17;
		else if(type.equalsIgnoreCase("지령:3단"))
			return 18;
		else if(type.equalsIgnoreCase("지령:4단"))
			return 19;
		else if(type.equalsIgnoreCase("지령:5단"))
			return 20;
		return 0;
	}
	
	public int PresentType(String type) {
		if (type.equalsIgnoreCase("Account-Present"))
			return 1;
		else if (type.equalsIgnoreCase("Character-Present"))
			return 2;
		return 0;
	}

	public L1PresentItems getPresentItem(int level) {
		return (L1PresentItems) _list.get(level);
	}

	public class L1PresentItems {
		private int _item_id;
		private int _item_count;
		private int _item_bless;
		private int _item_enchant_lvl;
		private int _item_attr_lvl;
		private int _present_level;
		private int _present_type;

		public L1PresentItems() {
		}
		
		public int getItemId() {
			return _item_id;
		}

		public void setItemId(int i) {
			_item_id = i;
		}

		public int getItemCount() {
			return _item_count;
		}

		public void setItemCount(int i) {
			_item_count = i;
		}
		
		public int getItemBless() {
			return _item_bless;
		}

		public void setItemBless(int i) {
			_item_bless = i;
		}
		
		public int getItemEnchantLevel() {
			return _item_enchant_lvl;
		}

		public void setItemEnchantLevel(int i) {
			_item_enchant_lvl = i;
		}
		
		public int getItemAttrLevel() {
			return _item_attr_lvl;
		}

		public void setItemAttrLevel(int i) {
			_item_attr_lvl = i;
		}

		public int getLevel() {
			return _present_level;
		}

		public void setLevel(int i) {
			_present_level = i;
		}
		
		public int isPresentType() {
			return _present_type;
		}
		
		public void setPresentType(int i) {
			_present_type = i;
		}
	}
}