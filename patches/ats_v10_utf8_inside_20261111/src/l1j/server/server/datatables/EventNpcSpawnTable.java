package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import l1j.server.L1DatabaseFactory;
import l1j.server.Templates.L1EventNpcSpawn;
import l1j.server.server.utils.SQLUtil;

public class EventNpcSpawnTable
{
	public static EventNpcSpawnTable _instance;
	public static List<L1EventNpcSpawn> _list = new ArrayList<L1EventNpcSpawn>();

	public static EventNpcSpawnTable getInstance() {
		if (_instance == null) {
			_instance = new EventNpcSpawnTable();
		}
		return _instance;
	}

	public void loadEventNpc() {
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		try {
			if (_list != null) {
				_list.clear();
			}

			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("SELECT * FROM 특화사냥터");
			rs = pstm.executeQuery();
			while (rs.next()) {
				L1EventNpcSpawn event = new L1EventNpcSpawn();
				String spawn_time = rs.getString("스폰시간");
				String[] spawn_yoil = { "전체" };
				event.setNpcId(rs.getInt("엔피씨번호"));
				event.setMent(rs.getString("스폰멘트"));
				event.setKeepTime(rs.getInt("유지시간"));
				event.setEventName(rs.getString("이벤트이름"));
				event.setJoinLevel(rs.getInt("입장가능레벨"));
				event.setJoinMaxLevel(rs.getInt("입장최대레벨")); 
				try
				{
					StringTokenizer stt = new StringTokenizer(rs.getString("스폰좌표"), ",");
					int[] craftlist = new int[stt.countTokens()];
					for (int i = 0; stt.hasMoreTokens(); i++) {
						craftlist[i] = Integer.valueOf(stt.nextToken()).intValue();
					}
					event.setSpawnLoc(craftlist);
				} catch (Exception e) {
					e.printStackTrace();
				}
				try
				{
					StringTokenizer stt = new StringTokenizer(rs.getString("입장좌표"), "\n\r");
					String[] craftlist = new String[stt.countTokens()];
					for (int i = 0; stt.hasMoreTokens(); i++) {
						craftlist[i] = stt.nextToken();
					}

					event.setJoinLoc(craftlist);
				} catch (Exception e) {
					e.printStackTrace();
				}
				try
				{
					StringTokenizer stt = new StringTokenizer(rs.getString("종료텔좌표"), ",");
					int[] craftlist = new int[stt.countTokens()];
					for (int i = 0; stt.hasMoreTokens(); i++) {
						craftlist[i] = Integer.valueOf(stt.nextToken()).intValue();
					}
					event.setExitLoc(craftlist);
				} catch (Exception e) {
					e.printStackTrace();
				}
				try
				{
					StringTokenizer stt = new StringTokenizer(rs.getString("강제추방적용맵"), ",");
					int[] craftlist = new int[stt.countTokens()];
					for (int i = 0; stt.hasMoreTokens(); i++) {
						craftlist[i] = Integer.valueOf(stt.nextToken()).intValue();
					}
					event.setExitMap(craftlist);
				} catch (Exception e) {
					e.printStackTrace();
				}
				try
				{
					StringTokenizer stt = new StringTokenizer(rs.getString("스폰요일"), ",");
					spawn_yoil = new String[stt.countTokens()];
					for (int i = 0; stt.hasMoreTokens(); i++) {
						spawn_yoil[i] = stt.nextToken();
					}
					event.setYoil(spawn_yoil);
				} catch (Exception e) {
					e.printStackTrace();
				}

				if (spawn_time.length() > 0) {
					StringTokenizer stt = new StringTokenizer(spawn_time, ",");
					int[][] time = new int[stt.countTokens()][2];
					int idx = 0;
					while (stt.hasMoreTokens()) {
						String boss_time = stt.nextToken();
						String boss_h = boss_time.substring(0, boss_time.indexOf(":"));
						String boss_m = boss_time.substring(boss_h.length() + 1, boss_time.length());
						time[idx][0] = Integer.valueOf(boss_h).intValue();
						time[idx][1] = Integer.valueOf(boss_m).intValue();
						idx++;
					}
					event.setTime(time);
				}

				_list.add(event);
			}
		} catch (Exception e) {
			e.getStackTrace();
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}

	public List<L1EventNpcSpawn> getList() {
		return _list;
	}

	public boolean isEventNpc(int npcid) {
		for (L1EventNpcSpawn event : _list) {
			if (event.getNpcId() == npcid) {
				return true;
			}
		}
		return false;
	}

	public L1EventNpcSpawn getEventMapCheck(int mapid) {
		for (L1EventNpcSpawn event : _list) {
			for (int i = 0; i < event.getExitMap().length; i++) {
				if (event.getExitMap()[i] == mapid) {
					return event;
				}
			}
		}
		return null;
	}

	public L1EventNpcSpawn getEventMapLevelUpCheck(int mapid) {
		for (L1EventNpcSpawn event : _list) {
			String[] loc = event.getJoinLoc()[0].split(",");
			if (Integer.valueOf(loc[2]).intValue() == mapid) {
				return event;
			}
		}
		return null;
	}

	public L1EventNpcSpawn getEventSpawn(int npcid) {
		for (L1EventNpcSpawn event : _list) {
			if (event.getNpcId() == npcid) {
				return event;
			}
		}
		return null;
	}
}