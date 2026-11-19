package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import javolution.util.FastTable;
import l1j.server.DatabaseFactory;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.templates.L1QuestMonsterInfo;
import l1j.server.server.utils.SQLUtil;

// 퀘스트 몬스터 시스템
public class CharacterQuestMonsterTable {
	private static CharacterQuestMonsterTable _instance;

	public static CharacterQuestMonsterTable getInstance() {
		if (_instance == null) {
			_instance = new CharacterQuestMonsterTable();
		}
		return _instance;
	}

	private static Logger _log = Logger.getLogger(CharacterQuestMonsterTable.class.getName());

	private FastTable<QuestTemp> QuestList = new FastTable<QuestTemp>();

	public static void reload() {
		CharacterQuestMonsterTable oldInstance = _instance;
		_instance = new CharacterQuestMonsterTable();
		oldInstance.QuestList.clear();
	}

	private CharacterQuestMonsterTable() {
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		try {
			con = DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("SELECT * FROM character_quest_monster");
			rs = pstm.executeQuery();

			QuestTemp mon = null;
			while (rs.next()) {
				mon = new QuestTemp();
				mon.name = rs.getString("name");
				mon.text = rs.getString("info");
				QuestList.add(mon);

			}
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}

	/**
	 * 로그인시 몬스터 정보를 케릭터에 업로드한다.
	 * 
	 * @param pc
	 */
	public void LoginQuestInfo(L1PcInstance pc) {
//		for (QuestTemp quest : QuestList) {
//			if (quest.name.equalsIgnoreCase(pc.getName())) {
//				StringTokenizer s = new StringTokenizer(quest.text, "\r\n");
//				String temp = null;
//				L1QuestMonsterInfo info = null;
//				while (s.hasMoreElements()) { // 엔터기준
//					temp = s.nextToken();
//					StringTokenizer mdata = new StringTokenizer(temp, ":");
//					info = new L1QuestMonsterInfo();
//					info.npc_id = Integer.parseInt(mdata.nextToken().trim());
//					info.npc_count = Integer.parseInt(mdata.nextToken().trim());
//					pc.monster_list.put(info.npc_id, info);
//				}
//				return;
//			}
//		}
//		QuestTemp quest = new QuestTemp();
//		quest.name = pc.getName();
//		quest.text = "";
//		createQuest(quest);
	}

	/**
	 * 로그아웃 정보업로드
	 * 
	 * @param pc
	 */
	public void LogOutQuest(L1PcInstance pc) {
//		synchronized (pc.syncTalkIsland) {
//			for (QuestTemp quest : QuestList) {
//				if (quest.name.equalsIgnoreCase(pc.getName())) {
//					StringBuffer NewText = new StringBuffer();
//					// String temp = null;]
//					for (L1QuestMonsterInfo info : pc.monster_list.values()) {
//						NewText.append(Integer.toString(info.npc_id) + ":");
//						NewText.append(Integer.toString(info.npc_count) + "\r\n");
//					}
//					quest.text = NewText.toString();
//					update(quest);
//					break;
//				}
//			}
//		}
	}

	public void update(QuestTemp quest) {
		Connection con = null;
		PreparedStatement pstm = null;
		try {
			con = DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("UPDATE character_quest_monster SET info=? WHERE name=?");
			pstm.setString(1, quest.text);
			pstm.setString(2, quest.name);
			pstm.execute();
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}

	public void delete(String name) {
		Connection con = null;
		PreparedStatement pstm = null;
		for (QuestTemp quest : QuestList) {
			if (quest.name.equalsIgnoreCase(name)) {
				QuestList.remove(quest);
				break;
			}
		}
		try {
			con = DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("DELETE FROM character_quest_monster WHERE name=?");
			pstm.setString(1, name);
			pstm.execute();
		} catch (SQLException e) {
			e.printStackTrace();
			e.printStackTrace();
		} finally {
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}

	/**
	 * 서버 종료시 전체 정보 업데이트
	 */
	public void updateAll() {
		Connection con = null;
		PreparedStatement pstm = null;
		try {
			con = DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("UPDATE character_quest_monster SET info=? WHERE name=?");

			for (QuestTemp QuestTemp : QuestList) {
				pstm.setString(1, QuestTemp.text);
				pstm.setString(2, QuestTemp.name);
				pstm.execute();
			}
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "MonsterBookTable[]Error1", e);
		} finally {
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}

	/**
	 * 케릭 로그인시 데이터 베이스에 생성
	 * 
	 * @param mon
	 */
	public void createQuest(QuestTemp mon) {
		Connection con = null;
		PreparedStatement pstm = null;
		try {
			con = DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("INSERT INTO character_quest_monster SET name=?, info=?");
			pstm.setString(1, mon.name);
			pstm.setString(2, mon.text);
			pstm.execute();

			QuestList.add(mon);
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "MonsterBookTable[]Error2", e);
		} finally {
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}

	public class QuestTemp {
		public String name;
		public String text;
	}
}