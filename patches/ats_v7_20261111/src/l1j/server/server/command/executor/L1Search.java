package l1j.server.server.command.executor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.StringTokenizer;
import java.util.logging.Logger;

import l1j.server.L1DatabaseFactory;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_SystemMessage;
import l1j.server.server.utils.SQLUtil;

public class L1Search implements L1CommandExecutor {
	@SuppressWarnings("unused")
	private static Logger _log = Logger.getLogger(L1Search.class.getName());

	private L1Search() {
	}

	public static L1CommandExecutor getInstance() {
		return new L1Search();
	}

	@Override
	public void execute(L1PcInstance pc, String cmdName, String arg) {
		try {
			StringTokenizer st = new StringTokenizer(arg);
			String type = "";
			String real_name_id_view = "";
			String add = "";
			boolean simpleS = true;
			int itCount = 0;
			while (st.hasMoreTokens()) {
				if (itCount == 1) {
					add = "%";
				}
				String tempVar = st.nextToken();
				if (itCount == 0
						&& (tempVar.equals("갑옷") || tempVar.equals("무기")
								|| tempVar.equals("etc")
								|| tempVar.equals("변신") || tempVar
									.equals("NPC"))) {
					simpleS = false;
					type = tempVar;
				} else {
					real_name_id_view = real_name_id_view + add + tempVar;
				}
				itCount++;
			}
			if (simpleS == false) {
				find_object(pc, type, real_name_id_view);
			} else {
				find_object(pc, real_name_id_view);
			}
		} catch (Exception e) {
			pc.sendPackets(new S_SystemMessage(
					".find [갑옷,무기,etc,변신,NPC] 를 입력 해주세요"));
		}
	}

	private void find_object(L1PcInstance pc, String type, String real_name_id_view) {
		String str1 = null;
		String str2 = null;
		int bless = 0;
		int count = 0;
		Connection con = null;
		PreparedStatement statement = null;
		ResultSet rs = null;
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			boolean error = false;

			pc.sendPackets(new S_SystemMessage(" "));

			if (type.equals("갑옷")) {
				statement = con.prepareStatement("SELECT item_id,real_name_id_view,bless FROM armor WHERE real_name_id_view Like '%" + real_name_id_view + "%'");
			} else if (type.equals("무기")) {
				statement = con.prepareStatement("SELECT item_id,real_name_id_view,bless FROM weapon WHERE real_name_id_view Like '%" + real_name_id_view + "%'");
			} else if (type.equals("etc")) {
				statement = con.prepareStatement("SELECT item_id,real_name_id_view,bless FROM etcitem WHERE real_name_id_view Like '%" + real_name_id_view + "%'");
			} else if (type.equals("변신")) {
				statement = con.prepareStatement("SELECT polyid,real_name_id_view FROM polymorphs WHERE real_name_id_view Like '%" + real_name_id_view + "%'");
			} else if (type.equals("NPC")) {
				statement = con.prepareStatement("SELECT npcid,real_name_id_view FROM npc WHERE real_name_id_view Like '%" + real_name_id_view + "%'");
			} else {
				error = true;
				pc.sendPackets(new S_SystemMessage(".find [갑옷,무기,etc,변신,NPC] 를 입력 해주세요."));
			}
			String blessed = null;
			if (error == false) {
				rs = statement.executeQuery();
				pc.sendPackets(new S_SystemMessage("라인에 검색 하는 '" + real_name_id_view.replace("%", " ") + " ' 는" + type + "이름..."));
				while (rs.next()) {
					str1 = rs.getString(1);
					str2 = rs.getString(2);
					if (type.equals("갑옷") || type.equals("무기")
							|| type.equals("etc")) {
						bless = rs.getInt(3);
						if (bless == 1) {
							blessed = "";
						} else if (bless == 0) {
							blessed = "\\fR";
						} else {
							blessed = "\\fY";
						}
						pc.sendPackets(new S_SystemMessage(blessed + "번호: "
								+ str1 + ", " + str2));
					} else {
						pc.sendPackets(new S_SystemMessage("번호: " + str1 + ", "
								+ str2));
					}
					count++;
				}
				pc.sendPackets(new S_SystemMessage("찾기 " + count + " 라인에 항목"
						+ type + "타입。"));
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			SQLUtil.close(rs, statement, con);
		}
	}

	private void find_object(L1PcInstance pc, String real_name_id_view) {
		String str1 = null;
		String str2 = null;
		int bless = 0;
		String blessed = null;
		Connection con = null;
		PreparedStatement statement = null;
		ResultSet rs = null;
		int count1 = 0;
		int count2 = 0;
		int count3 = 0;
		int count4 = 0;
		int count5 = 0;
		try {
			con = L1DatabaseFactory.getInstance().getConnection();

			pc.sendPackets(new S_SystemMessage(" "));
			pc.sendPackets(new S_SystemMessage("라인에서 검색 한 개체 이름: '" + real_name_id_view.replace("%", " ") + "'"));

			statement = con.prepareStatement("SELECT item_id,real_name_id_view,bless FROM armor WHERE real_name_id_view Like '%" + real_name_id_view + "%'");
			rs = statement.executeQuery();
			while (rs.next()) {
				if (count1 == 0) {
					pc.sendPackets(new S_SystemMessage("갑옷:"));
				}
				str1 = rs.getString(1);
				str2 = rs.getString(2);
				bless = rs.getInt(3);
				if (bless == 1) {
					blessed = "";
				} else if (bless == 0) {
					blessed = "\\fR";
				} else {
					blessed = "\\fY";
				}
				pc.sendPackets(new S_SystemMessage(blessed + "번호: " + str1
						+ ", " + str2));
				count1++;
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(statement);
			SQLUtil.close(con);
		}
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			statement = con.prepareStatement("SELECT item_id,real_name_id_view,bless FROM weapon WHERE real_name_id_view Like '%" + real_name_id_view + "%'");
			rs = statement.executeQuery();
			while (rs.next()) {
				if (count2 == 0) {
					pc.sendPackets(new S_SystemMessage("무기:"));
				}
				str1 = rs.getString(1);
				str2 = rs.getString(2);
				bless = rs.getInt(3);
				if (bless == 1) {
					blessed = "";
				} else if (bless == 0) {
					blessed = "\\fR";
				} else {
					blessed = "\\fY";
				}
				pc.sendPackets(new S_SystemMessage(blessed + "번호: " + str1
						+ ", " + str2));
				count2++;
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(statement);
			SQLUtil.close(con);
		}
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			statement = con.prepareStatement("SELECT item_id,real_name_id_view,bless FROM etcitem WHERE real_name_id_view Like '%" + real_name_id_view + "%'");
			rs = statement.executeQuery();
			while (rs.next()) {
				if (count3 == 0) {
					pc.sendPackets(new S_SystemMessage("etc:"));
				}
				str1 = rs.getString(1);
				str2 = rs.getString(2);
				bless = rs.getInt(3);
				if (bless == 1) {
					blessed = "";
				} else if (bless == 0) {
					blessed = "\\fR";
				} else {
					blessed = "\\fY";
				}
				pc.sendPackets(new S_SystemMessage(blessed + "번호: " + str1
						+ ", " + str2));
				count3++;
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(statement);
			SQLUtil.close(con);
		}
		try {

			con = L1DatabaseFactory.getInstance().getConnection();
			statement = con.prepareStatement("SELECT polyid,name FROM polymorphs WHERE name Like '%" + real_name_id_view + "%'");
			rs = statement.executeQuery();
			while (rs.next()) {
				if (count4 == 0) {
					pc.sendPackets(new S_SystemMessage("변신:"));
				}
				str1 = rs.getString(1);
				str2 = rs.getString(2);
				pc.sendPackets(new S_SystemMessage("번호: " + str1 + ", " + str2));
				count4++;
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(statement);
			SQLUtil.close(con);
		}
		try {

			con = L1DatabaseFactory.getInstance().getConnection();
			statement = con.prepareStatement("SELECT npcid, desc_view FROM npc WHERE desc_view Like '%" + real_name_id_view + "%'");
			rs = statement.executeQuery();
			while (rs.next()) {
				if (count5 == 0) {
					pc.sendPackets(new S_SystemMessage("NPC:"));
				}
				str1 = rs.getString(1);
				str2 = rs.getString(2);
				pc.sendPackets(new S_SystemMessage("번호: " + str1 + ", " + str2));
				count5++;
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			SQLUtil.close(rs, statement, con);
		}

		try {
			pc.sendPackets(new S_SystemMessage("검색 결과:"));
			String found = "";
			if (count1 > 0) {
				found += "갑옷: " + count1 + "、";
			}
			if (count2 > 0) {
				found += "무기: " + count2 + "、";
			}
			if (count3 > 0) {
				found += "etc: " + count3 + "、";
			}
			if (count4 > 0) {
				found += "변신: " + count4 + "、";
			}
			if (count5 > 0) {
				found += "NPC: " + count5 + "。";
			}
			if (found.length() > 0) {
				found = found.substring(0, found.length() - 1) + "。";
			} else {
				found = " 0 개의 항목을 발견";
			}
			pc.sendPackets(new S_SystemMessage(found));
		} catch (Exception e) {
		}
	}
}
