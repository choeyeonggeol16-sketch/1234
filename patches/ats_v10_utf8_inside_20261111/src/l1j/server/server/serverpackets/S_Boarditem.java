package l1j.server.server.serverpackets;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.L1DatabaseFactory;
import l1j.server.Templates.L1Item;
import l1j.server.server.Opcodes.Opcodes;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.utils.SQLUtil;

public class S_Boarditem extends ServerBasePacket {

	private static final String S_Boarditem = "[S] S_Boarditem";

	private static Logger _log = Logger.getLogger(S_Boarditem.class.getName());

	public S_Boarditem(L1NpcInstance board) {
		buildPacket(board, 0);
	}

	public S_Boarditem(L1NpcInstance board, int number) {
		buildPacket(board, number);
	}

	public S_Boarditem(int number) {
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
			pstm = con.prepareStatement("SELECT * FROM Board_item order by id desc");
			rs = pstm.executeQuery();
			while (rs.next() && count < 8) {
				if (rs.getInt(1) <= number || number == 0) {
					id[count] = rs.getInt(1);
					db[count][0] = rs.getString(2);
					db[count][1] = rs.getString(8);
					db[count][2] = rs.getString(5);
					// db[count][2] = Enchant(rs.getString(5));
					db[count][3] = rs.getString(4);
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
		writeC(0);
		writeD(board.getId());
		writeC(0xFF);
		writeC(0xFF);
		writeC(0xFF);
		writeC(0x7F);
		writeH(count);
		writeH(300);
		for (int i = 0; i < count; ++i) {
			writeD(id[i]);
			writeS(db[i][0]);
			writeS(db[i][1]);
			writeS("+ " + db[i][2] + " " + db[i][3]);
			// writeS(db[i][2] + " " + db[i][3]);
		}
	}

	public void buildPacket(int number) {
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("SELECT * FROM Board_item WHERE id=?");
			pstm.setInt(1, number);
			rs = pstm.executeQuery();
			while (rs.next()) {
				writeC(Opcodes.S_OPCODE_BOARDREAD);
				writeD(number);
				writeS(rs.getString(2));
				// writeS("+ " + rs.getString(5) + " " + rs.getString(4));
				writeS(Enchant(rs.getString(5)) + " " + rs.getString(4));
				writeS(rs.getString(8));
				writeS("\n" + "<판매 물품>" + "\n\n" +
				// Enchant(rs.getString(5))+ " " + rs.getString(4)+"\n"+"\n"+
						"+" + rs.getString(5) + " " + rs.getString(4) + "\n" + "\n" + "<판매 정보>" + "\n\n"
						+ "판매 금액 : " + count(rs.getString(6)) + "\n" + "\n" + "<판매 옵션 정보(무기/방어구)> " + "\n" + "속성 인첸트 : "
						+ UpClick(rs.getString(9)) + "\n\n"
						+ "축복부여:" + Blesslevel(rs.getString(11)) + "+"+rs.getString(12)+" \n\n"
						+ "판매자 : " + rs.getString(2) + "\n\n"
						
						
						
						//+ "축복부여 : " + Blesslevel(rs.getString(10)) + "\n" + "\n" + "<판매 옵션 정보 (인형)>  " + "\n"
						//+ "[잠재력 단계]" + "\n" + itemlevel(rs.getString(11))

				);
			}
		} catch (SQLException e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}

	private String count(String g) {
		int count = Integer.parseInt(g);

		String ggg = "";
		int 억 = count;
		int A = 억 / 100000000;// 억단위만 표시 1억
		int a = A * 100000000;
		int B = count - a; // 억금액에서 억단위뺀 천만단위금액 5151545151

		int b = B / 10000000; // 천단위만 // 5
		int bb = b * 10000000; // 5천만원 // 5000000

		int c = B - bb; // 15551554 백만단위
		int cc = c / 1000000;
		int ccc = cc * 1000000;

		if (count >= 100000000) {
			if (B == 0 || B == 0 && ccc == 0) {
				ggg = A + "억 ";
			} else if (ccc == 0) {
				ggg = A + "억 " + b + "천";
			} else {
				ggg = A + "억 " + b + "천 " + cc + "백";
			}
		} else {
			if (ccc == 0) {
				ggg = b + "천만원 ";
			} else {
				ggg = b + "천 " + cc + "백만원";
			}
		}

		return ggg;
	}
 

	private String UpClick(String z) {
		int aaaaa = Integer.parseInt(z);
		String ggg = "";

		switch (aaaaa) {
		case 1: ggg = "화령 1단계"; break;	// 화령 1단
		case 2: ggg = "화령 2단계"; break;	// 화령 2단
		case 3: ggg = "화령 3단계"; break;	// 화령 3단
		
		case 4: ggg = "수령 1단계"; break;	// 수령 1단
		case 5: ggg = "수령 2단계"; break;	// 수령 2단
		case 6: ggg = "수령 3단계"; break;	// 수령 3단
		
		case 7: ggg = "풍령 1단계"; break;	// 풍령 1단
		case 8: ggg = "풍령 2단계"; break;	// 풍령 2단
		case 9: ggg = "풍령 3단계"; break;	// 풍령 3단
		
		case 10: ggg = "지령 1단계"; break;	// 지령 1단
		case 11: ggg = "지령 2단계"; break;	// 지령 2단
		case 12: ggg = "지령 3단계"; break;	// 지령 3단 

		default:
			ggg = "없음";
			break;
		}
		return ggg;
	}

	private String Enchant(String z) {
		int aaaaa = Integer.parseInt(z);
		String ggg = "";
		L1Item temp = ItemTable.getInstance().getTemplate(aaaaa);
		switch (aaaaa) {

		case 1:
			if (temp.getType2() != 0) {
				ggg = "+" + aaaaa;
			} else {
				ggg = null;
			}
			break;
		default:
			if (aaaaa > 1) {
				if (temp.getType2() != 0) {
					ggg = "+" + aaaaa;
				} else {
					ggg = null;
				}
			}
			break;
		}

		return ggg;
	}

	private String Blesslevel(String z) {
		int aaaaa = Integer.parseInt(z);
		String ggg = "";                     
	 
		switch (aaaaa) {
		case 1:
			ggg = "방어증가";
			break;
		case 2:
			ggg = "힘증가";
			break;
		case 3:
			ggg = "콘증가";
			break;
		case 4:
			ggg = "덱스증가";
			break;
		case 5:
			ggg = "인트증가";
			break;
		case 6:
			ggg = "위즈증가";
			break;
		case 7:
			ggg = "카리증가";
			break;
		case 8:
			ggg = "HP증가";
			break;
		case 9:
			ggg = "MP증가";
			break;
		case 10:
			ggg = "피틱증가";
			break;
		case 11:
			ggg = "엠틱증가";
			break;
		case 12:
			ggg = "마방증가";
			break;
		case 13:
			ggg = "스펠증가";
			break;
		case 14:
			ggg = "근거리대미지증가";
			break;
		case 15:
			ggg = "원거리대미지증가";
			break;
		case 16:
			ggg = "근거리명중증가";
			break;
		case 17:
			ggg = "원거리명중증가";
			break;
		case 18:
			ggg = "대미지감소증가";
			break;
		case 19:
			ggg = "소지무게증가";
			break;
		case 20:
			ggg = "스턴내성증가";
			break;
		case 21:
			ggg = "석화내성증가";
			break;
		case 22:
			ggg = "수면내성증가";
			break;
		case 23:
			ggg = "동빙내성증가";
			break;
		case 24:
			ggg = "홀드내성증가";
			break; 	 
		case 31:
			ggg = "PVP추가대미지";
			break;
		case 32:
			ggg = "PVP추가대미지감소";
			break;
		case 33:
			ggg = "경험치증가";
			break;
			  

		default:
			ggg = "축복부여없음";
			break;
		}
		return ggg;
	}

	@Override
	public byte[] getContent() {
		return getBytes();
	}

	@Override
	public String getType() {
		return S_Boarditem;
	}
}
