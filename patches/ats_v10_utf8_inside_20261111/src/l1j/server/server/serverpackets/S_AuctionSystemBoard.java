package l1j.server.server.serverpackets;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.L1DatabaseFactory;
import l1j.server.server.Opcodes.Opcodes;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.utils.SQLUtil;

//TODO 중개 거래 게시판
public class S_AuctionSystemBoard extends ServerBasePacket {

	private static final String S_AuctionSystemBoard = "[S] S_AuctionSystemBoard";

	private static Logger _log = Logger.getLogger(S_AuctionSystemBoard.class.getName());

	private byte[] _byte = null;

	public S_AuctionSystemBoard(L1NpcInstance board) {
		buildPacket(board, 0);
	}

	public S_AuctionSystemBoard(L1NpcInstance board, int number) {
		buildPacket(board, number);
	}

	public S_AuctionSystemBoard(int number) {
		buildPacket(number);
	}

	private void buildPacket(L1NpcInstance board, int number) {
		int count = 0;
		String[][] db = null;
		int[] id = null;
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		try {
			db = new String[8][4];
			id = new int[8];
			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("SELECT * FROM Auction order by id desc");
			rs = pstm.executeQuery();
			while (rs.next() && count < 8) {
				if (rs.getInt(1) <= number || number == 0) {
					id[count] = rs.getInt(1); 		//게시판번호
					db[count][0] = rs.getString(2);	//name
					db[count][1] = rs.getString(8);	//셀타입
					db[count][2] = "";
					db[count][3] = makeMoneyType(rs.getString(5)) + "억 : " + makeMoneyType(rs.getString(6)) + "원";
					count++;
					
				}
			}
		} catch (SQLException e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
		writeC(Opcodes.S_OPCODE_BOARD);
		writeC(0);// type
		writeD(board.getId());
		writeC(0xFF); // ?
		writeC(0xFF); // ?
		writeC(0xFF); // ?
		writeC(0x7F); // ?
		writeH(count);
		writeH(300);
		for (int i = 0; i < count; ++i) {
			writeD(id[i]);
			writeS(db[i][0]);
			writeS(db[i][1]);
			writeS("" + db[i][2] + " " + db[i][3]);
		}
	}

	public void buildPacket(int number) {
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null; 
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("SELECT * FROM Auction WHERE id=?");
			pstm.setInt(1, number);
			rs = pstm.executeQuery();
			while (rs.next()) {
				writeC(Opcodes.S_OPCODE_BOARDREAD);
				
				writeD(number);
				writeS(rs.getString(2));
				writeS("" + rs.getString(5) + " " + rs.getString(4));
				writeS(rs.getString(8));
				writeS("\n\n"
						+ "현재상태 : " + rs.getString(8) + "\n"
						+ "게시번호 : " + rs.getString(1) + ""
						+ "\n\n" + rs.getString(4) + " : " + makeMoneyType(rs.getString(5)) + ""
						+ "\n판매금액 : " + makeMoneyType(rs.getString(6)) + " 원" + "\n\n"
						+ "판매자 : " + rs.getString(2) + "\n\n"
						+ "구매자 : " + rs.getString(13) + ""
						+ "\n\n.구매신청 (게시번호) 입력\n\n");
				//System.out.println(rs.getString(5)); 
				//String str = makeMoneyType(rs.getString(5)); 
				//System.out.println(makeMoneyType(rs.getString(5)));
			}
		} catch (SQLException e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}
	
	public static String makeMoneyType(String string) {
		String moneyString = Double.valueOf(string).toString();

		String format = "###,###";
		DecimalFormat df = new DecimalFormat(format);
		DecimalFormatSymbols dfs = new DecimalFormatSymbols();

		dfs.setGroupingSeparator(',');// 구분자를 ,로
		df.setGroupingSize(3);// 3자리 단위마다 구분자처리 한다.
		df.setDecimalFormatSymbols(dfs);

		return (df.format(Double.parseDouble(moneyString))).toString();
	}

	@Override
	public byte[] getContent() {
		if (_byte == null) {
			_byte = getBytes();
		}
		return _byte;
	}

	@Override
	public String getType() {
		return S_AuctionSystemBoard;
	}

}