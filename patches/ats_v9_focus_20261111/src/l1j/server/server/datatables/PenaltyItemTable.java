package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

import l1j.server.L1DatabaseFactory;
import l1j.server.server.utils.SQLUtil;

public class PenaltyItemTable {
	private static PenaltyItemTable _instance;

	public static PenaltyItemTable getInstance() {
		if (_instance == null) {
			_instance = new PenaltyItemTable();
		}
		return _instance;
	}

	private Map<Integer, L1PenaltyItem> _list = new HashMap<Integer, L1PenaltyItem>();

	private PenaltyItemTable() {
		//TimeLine.start("loading PenaltyItemTable...");

		load(_list);

		//TimeLine.end();
	}

	public void reload() {
		//TimeLine.start("reloading PenaltyItemTable...");

		Map<Integer, L1PenaltyItem> list = new HashMap<Integer, L1PenaltyItem>();
		load(list);
		_list = list;

		//TimeLine.end();
	}

	private void load(Map<Integer, L1PenaltyItem> list) {
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		L1PenaltyItem pi = null;
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("SELECT * FROM penalty_item_info");
			rs = pstm.executeQuery();
			while (rs.next()) {
				pi = new L1PenaltyItem();

				int itemid = rs.getInt("아이템번호");
				int checktype = rs.getString("체크방식").equalsIgnoreCase("인벤체크") ? 1 : 0;

				pi.setDropItemId(rs.getInt("드랍아이템"));
				pi.setDropItemCount(rs.getInt("드랍아이템갯수"));
				pi.setEquipDelete(rs.getString("착용아이템삭제여부").equalsIgnoreCase("true"));
				pi.setExpPenalty(rs.getString("경험치패널티여부").equalsIgnoreCase("true"));
				pi.setItemPenalty(rs.getString("아이템패널티여부").equalsIgnoreCase("true"));
				pi.setPcPenaltyChance(rs.getInt("PcToPc패널티확률"));
				pi.setNpcPenaltyChance(rs.getInt("NpcToPc패널티확률"));
				pi.setItemCheckType(checktype);

				list.put(itemid, pi);
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			SQLUtil.close(rs, pstm, con);
		}
	}

	public L1PenaltyItem getPenaltyItem(int itemid) {
		return _list.get(itemid);
	}

	public class L1PenaltyItem {
		private boolean _isdelete;

		public boolean isEquipDelete() {
			return _isdelete;
		}

		public void setEquipDelete(boolean flag) {
			_isdelete = flag;
		}

		private int _dropitemid;

		public int getDropItemId() {
			return _dropitemid;
		}

		public void setDropItemId(int i) {
			_dropitemid = i;
		}

		private int _dropitemcount;

		public int getDropItemCount() {
			return _dropitemcount;
		}

		public void setDropItemCount(int i) {
			_dropitemcount = i;
		}

		private boolean _isItemPenalty;

		public boolean isItemPenalty() {
			return _isItemPenalty;
		}

		public void setItemPenalty(boolean flag) {
			_isItemPenalty = flag;
		}

		private boolean _isExpPenalty;

		public boolean isExpPenalty() {
			return _isExpPenalty;
		}

		public void setExpPenalty(boolean flag) {
			_isExpPenalty = flag;
		}

		private int _pcPenaltyChance;

		public int getPcPenaltyChance() {
			return _pcPenaltyChance;
		}

		public void setPcPenaltyChance(int i) {
			_pcPenaltyChance = i;
		}

		private int _npcPenaltyChance;

		public int getNpcPenaltyChance() {
			return _npcPenaltyChance;
		}

		public void setNpcPenaltyChance(int i) {
			_npcPenaltyChance = i;
		}

		private int _checkType;

		public int getItemCheckType() {
			return _checkType;
		}

		public void setItemCheckType(int i) {
			_checkType = i;
		}
	}
}
