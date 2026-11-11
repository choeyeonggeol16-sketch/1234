package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.L1DatabaseFactory;
import l1j.server.Templates.L1BuffBead;
import l1j.server.server.utils.SQLUtil;

public class EtcItemBuffBeadTable {

	private final static Logger _log = Logger.getLogger(EtcItemBuffBeadTable.class.getName());

	public static EtcItemBuffBeadTable _instance;

	private List<L1BuffBead> _item = new ArrayList<L1BuffBead>();

	public static EtcItemBuffBeadTable getInstance() {
		if (_instance == null) {
			_instance = new EtcItemBuffBeadTable();
		}
		return _instance;
	}

	public static void reload() {
		EtcItemBuffBeadTable oldInstance = _instance;
		_instance = new EtcItemBuffBeadTable();
		oldInstance._item.clear();
	}

	private EtcItemBuffBeadTable() {
		selectBuffBeadItem();
	}

	public void selectBuffBeadItem() { // 로그인시 불러오는것
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("SELECT * FROM etcitem_buff_bead");
			rs = pstm.executeQuery();
			while (rs.next()) {
				L1BuffBead bead = new L1BuffBead();
				bead.setItemId(rs.getInt("아이템아이디"));
				bead.setDelete(rs.getString("아이템삭제여부").equalsIgnoreCase("true"));
				bead.setTimeUse(rs.getString("재사용대기여부").equalsIgnoreCase("true"));
				bead.setUseItemId(rs.getInt("사용시소비아이템"));
				bead.setUseItemCount(rs.getInt("사용시소비아이템갯수"));

				try {
					StringTokenizer stt = new StringTokenizer(rs.getString("버프스킬번호"), ",");
					int[] buffList = new int[stt.countTokens()];
					for (int i = 0; stt.hasMoreTokens(); ++i) {
						buffList[i] = Integer.valueOf(stt.nextToken());
					}
					bead.setBuffId(buffList);
				} catch (Exception e) {
					_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
				}
				_item.add(bead);
			}
		} catch (SQLException e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		} finally {
			SQLUtil.close(rs, pstm, con);
		}
	}

	public L1BuffBead getBuffBeadItem(int itemid) {
		for (L1BuffBead a : _item) {
			if (a.getItemId() == itemid) {
				return a;
			}
		}
		return null;
	}
}