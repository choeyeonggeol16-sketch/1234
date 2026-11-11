package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import l1j.server.L1DatabaseFactory;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.utils.SQLUtil;

public class CharacterSlotItemTable {

	public static CharacterSlotItemTable _instance;

	public static CharacterSlotItemTable getInstance() {
		if (_instance == null) {
			_instance = new CharacterSlotItemTable();
		}
		return _instance;
	}

	public void selectCharSlot(L1PcInstance pc, int slotNum) { // 로그인시 불러오는것
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("SELECT * FROM character_slot_items WHERE char_id=? AND slot_number=?");
			pstm.setInt(1, pc.getId());
			pstm.setInt(2, slotNum);
			rs = pstm.executeQuery();
			while (rs.next()) {
				pc.addSlotItem(slotNum, rs.getInt("item_objid"), false);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			SQLUtil.close(rs, pstm, con);
		}
	}

	public void updateCharSlotItems(L1PcInstance pc) {
		Connection con = null;
		PreparedStatement pstm = null;
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("DELETE FROM character_slot_items WHERE char_id=?");
			pstm.setInt(1, pc.getId());
			pstm.executeUpdate();
			pstm.close();

			if (pc.getSlotItems(0) != null) {
				for (int i = 0; i < pc.getSlotItems(0).size(); i++) {
					pstm = con.prepareStatement(
							"INSERT INTO character_slot_items SET item_objid=?, char_id=? ,slot_number=?");
					pstm.setInt(1, pc.getSlotItems(0).get(i));
					pstm.setInt(2, pc.getId());
					pstm.setInt(3, 0);
					pstm.execute();
					pstm.close();
				}
			}

			if (pc.getSlotItems(1) != null) {
				for (int i = 0; i < pc.getSlotItems(1).size(); i++) {
					pstm = con.prepareStatement(
							"INSERT INTO character_slot_items SET item_objid=?, char_id=? ,slot_number=?");
					pstm.setInt(1, pc.getSlotItems(1).get(i));
					pstm.setInt(2, pc.getId());
					pstm.setInt(3, 1);
					pstm.execute();
					pstm.close();
				}
			}

		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}
}
