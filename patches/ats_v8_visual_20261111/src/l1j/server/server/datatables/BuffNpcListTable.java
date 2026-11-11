package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import l1j.server.L1DatabaseFactory;
import l1j.server.server.utils.SQLUtil;

public class BuffNpcListTable {
	private static BuffNpcListTable _instance;

	public static BuffNpcListTable getInstance() {
		if (_instance == null) {
			_instance = new BuffNpcListTable();
		}
		return _instance;
	}

	private Map<Integer, BuffNpc> _list = new HashMap<Integer, BuffNpc>();

	private BuffNpcListTable() {
		load(_list);
	}

	public void reload() {
		Map<Integer, BuffNpc> list = new HashMap<Integer, BuffNpc>();
		load(list);
		_list = list;
	}

	private void load(Map<Integer, BuffNpc> list) {
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		BuffNpc bn = null;
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("select * from npc_buff_list");
			rs = pstm.executeQuery();
			while (rs.next()) {
				bn = new BuffNpc();
				int npcid = rs.getInt("npc_id");
				bn._Level = rs.getInt("level");
				bn._itemid = rs.getInt("item_id");
				bn._itemcount = rs.getInt("item_count");
				bn._skill_ids = rs.getString("skill_ids");

				list.put(npcid, bn);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}

	public BuffNpc getBuffNpc(int npcid) {
		return _list.get(npcid);
	}

	public class BuffNpc {
		public int _itemid;
		public int _itemcount;
		public int _Level;
		public String _skill_ids;
	}
}
