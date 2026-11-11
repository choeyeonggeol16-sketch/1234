package l1j.server.server.serverpackets;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.L1DatabaseFactory;
import l1j.server.server.Opcodes.Opcodes;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.datatables.NpcTable;
import l1j.server.server.utils.SQLUtil;

public class S_Serchdrop2 extends ServerBasePacket {

	private static final String S_Serchdrop2 = "[C] S_Serchdrop2";

	private static Logger _log = Logger.getLogger(S_Serchdrop2.class.getName());

	private byte[] _byte = null;

	int itemid[] = new int[30];

	String itemname[] = new String[30];

	public S_Serchdrop2(int itemid) {
		buildPacket(itemid);
	}

	private void buildPacket(int npcid) {
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;

		Connection con1 = null;
		PreparedStatement pstm1 = null;
		ResultSet rs1 = null;

		int i = 0;
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con
					.prepareStatement("SELECT `itemId` FROM `droplist` WHERE mobId=? ORDER BY `itemId` DESC LIMIT 30");
			pstm.setInt(1, npcid);
			rs = pstm.executeQuery();

			while (rs.next()) {
				itemid[i] = rs.getInt(1);
				itemname[i] = ItemTable.getInstance().getTemplate(itemid[i]).getName();
				i++;
			}
			
			StringBuilder sb = new StringBuilder();
			for (int a = 1; a < 30; a++) {
				String t = itemname[a];
				if (t != null)
					sb.append(",  " + itemname[a]);
			}

			writeC(Opcodes.S_OPCODE_BOARDREAD);
			writeD(0);// 넘버
			writeS("몬스터이름");// 글쓴이?
			writeS(" : " + NpcTable.getInstance().getTemplate(npcid).get_name());
			writeS("");
			writeS("\r ****  드랍하는 아이템  ****" + "\n\r" + itemname[0]
					+ sb.toString());

		} catch (SQLException e) {
			_log.log(Level.SEVERE, "S_Serchdrop2[]Error", e);
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(con);
			SQLUtil.close(rs1);
			SQLUtil.close(pstm1);
			SQLUtil.close(con1);
		}
	}

	@Override
	public byte[] getContent() {
		if (_byte == null) {
			_byte = getBytes();
		}
		return _byte;
	}

	public String getType() {
		return S_Serchdrop2;
	}
}
