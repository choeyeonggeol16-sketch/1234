package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import l1j.server.L1DatabaseFactory;
import l1j.server.server.utils.SQLUtil;

public class ItemMessageTable {

	public static ItemMessageTable _instance;

	public Map<Integer, L1ItemMessage> _drop_list = new HashMap<Integer, L1ItemMessage>();
	public Map<Integer, L1ItemMessage> _enchant_list = new HashMap<Integer, L1ItemMessage>();
	public Map<Integer, L1ItemMessage> _craft_list = new HashMap<Integer, L1ItemMessage>();

	public static ItemMessageTable getInstance() {
		if (_instance == null) {
			_instance = new ItemMessageTable();
		}
		return _instance;
	}

	public static void reload() {
		ItemMessageTable oldInstance = _instance;
		_instance = new ItemMessageTable();
		oldInstance._drop_list.clear();
		oldInstance._enchant_list.clear();
		oldInstance._craft_list.clear();
	}

	private ItemMessageTable() {
		loadItemMessage();
	}

	private void loadItemMessage() {
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("SELECT * FROM item_message");
			rs = pstm.executeQuery();
			while (rs.next()) {
				L1ItemMessage CM = new L1ItemMessage();
				String type = rs.getString("type");
				int itemid = rs.getInt("itemid");
				CM.setItemId(rs.getInt("itemid"));
				CM.setOption(rs.getInt("option"));
				CM.setItemName(rs.getString("itemname"));
				CM.setMentuse(rs.getBoolean("ment_use"));
				CM.setMent(rs.getString("ment"));
				if (type.equalsIgnoreCase("Drop")) {
					CM.setType(1);
					_drop_list.put(itemid, CM);
				} else if (type.equalsIgnoreCase("Enchant")) {
					CM.setType(2);
					_enchant_list.put(itemid, CM);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			SQLUtil.close(rs, pstm, con);
		}
	}

	public L1ItemMessage getPickUpMessage(int itemid) {
		return _drop_list.get(itemid);
	}

	public boolean isPickUpMessage(int itemid) {
		Set<Integer> keys = _drop_list.keySet();
		int itemmsg;
		boolean OK = false;
		for (Iterator<Integer> iterator = keys.iterator(); iterator.hasNext();) {
			itemmsg = iterator.next();
			if (itemmsg == itemid) {
				OK = true;
				break;
			}
		}
		return OK;
	}

	public L1ItemMessage getEnchantMessage(int itemid) {
		return _enchant_list.get(itemid);
	}

	public boolean isEnchantMessage(int itemid) {
		Set<Integer> keys = _enchant_list.keySet();
		int itemmsg;
		boolean OK = false;
		for (Iterator<Integer> iterator = keys.iterator(); iterator.hasNext();) {
			itemmsg = iterator.next();
			if (itemmsg == itemid) {
				OK = true;
				break;
			}
		}
		return OK;
	}

	public L1ItemMessage getCraftMessage(int itemid) {
		return _craft_list.get(itemid);
	}

	public boolean isCraftMessage(int itemid) {
		Set<Integer> keys = _craft_list.keySet();
		int itemmsg;
		boolean OK = false;
		for (Iterator<Integer> iterator = keys.iterator(); iterator.hasNext();) {
			itemmsg = iterator.next();
			if (itemmsg == itemid) {
				OK = true;
				break;
			}
		}
		return OK;
	}

	public class L1ItemMessage {
		private int _itemid;
		private String _itemName;
		private int _option;
		private int _type;
		private boolean _isment;
		private String _ment;

		public int getItemId() {
			return _itemid;
		}

		public void setItemId(int id) {
			_itemid = id;
		}

		public String getItemName() {
			return _itemName;
		}

		public void setItemName(String name) {
			_itemName = name;
		}

		public int getOption() {
			return _option;
		}

		public void setOption(int i) {
			_option = i;
		}

		public int getType() {
			return _type;
		}

		public void setType(int i) {
			_type = i;
		}

		public boolean isMentuse() {
			return _isment;
		}

		public L1ItemMessage setMentuse(boolean flag) {
			_isment = flag;
			return this;
		}

		public String getMent() {
			return _ment;
		}

		public void setMent(String ment) {
			_ment = ment;
		}

	}
}