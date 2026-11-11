package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

import l1j.server.L1DatabaseFactory;
import l1j.server.server.utils.SQLUtil;

public class wcanceladena {
	private static wcanceladena _instance;

	private Map<Integer, Integer> _list = new HashMap<Integer, Integer>();

	public static wcanceladena getInstance() {
		if (_instance == null) {
			_instance = new wcanceladena();
		}
		return _instance;
	}

	private wcanceladena() {
		load(_list);
	}

	public void reload() {
		Map<Integer, Integer> list = new HashMap<Integer, Integer>();
		load(list);
		_list = list;
	}

	private void load(Map<Integer, Integer> list) {
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("SELECT * FROM w_cancel_adena");
			rs = pstm.executeQuery();
			while (rs.next()) {
				int level = rs.getInt("pay_adena");
				int exp = rs.getInt("cance_adena");

				list.put(level, exp);
			}

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			SQLUtil.close(rs, pstm, con);
		}
	}

	public int getExpPotionValue(int level) {
		return _list.get(level);
	}
}
