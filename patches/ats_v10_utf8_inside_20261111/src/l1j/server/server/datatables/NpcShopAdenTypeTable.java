package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import l1j.server.L1DatabaseFactory;
import l1j.server.server.utils.SQLUtil;

public class NpcShopAdenTypeTable {

	private static NpcShopAdenTypeTable _instance;

	public static NpcShopAdenTypeTable getInstance() {
		if (_instance == null) {
			_instance = new NpcShopAdenTypeTable();
		}
		return _instance;
	}

	private Map<Integer, L1AdenType> _list = new HashMap<Integer, L1AdenType>();

	public NpcShopAdenTypeTable() {
		loadAdenType(_list);
	}

	public void reload() {
		Map<Integer, L1AdenType> list = new HashMap<Integer, L1AdenType>();
		loadAdenType(list);
		_list = list;
	}

	public void loadAdenType(Map<Integer, L1AdenType> list) {
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		L1AdenType at = null;
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("SELECT * FROM npc_shop_aden_type");
			rs = pstm.executeQuery();
			while (rs.next()) {
				at = new L1AdenType();
				int npcid = rs.getInt("NPC_ID");
				at.setAdenType(rs.getInt("ADEN_TYPE"));
				at.setAdenName(rs.getString("ADEN_NAME"));
				at.setAdenDesc(rs.getInt("ADEN_DESC"));
				at.setMaxUse(rs.getInt("MAX_USE"));
				at.setPackage(rs.getString("PACKAGE").equalsIgnoreCase("true"));

				list.put(npcid, at);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}

	public L1AdenType getNpcAdenType(int npcid) {
		return _list.get(npcid);
	}

	public class L1AdenType {
		private int _type;
		private String _typename;
		private int _desc;
		private int _maxuse;
		private boolean _package;

		public int getAdenType() {
			return _type;
		}

		public void setAdenType(int i) {
			_type = i;
		}

		public String getAdenName() {
			return _typename;
		}

		public void setAdenName(String name) {
			_typename = name;
		}

		public int getAdenDesc() {
			return _desc;
		}

		public void setAdenDesc(int i) {
			_desc = i;
		}

		public int getMaxUse() {
			return _maxuse;
		}

		public void setMaxUse(int i) {
			_maxuse = i;
		}

		public boolean isPackage() {
			return _package;
		}

		public void setPackage(boolean flag) {
			_package = flag;
		}
	}
}
