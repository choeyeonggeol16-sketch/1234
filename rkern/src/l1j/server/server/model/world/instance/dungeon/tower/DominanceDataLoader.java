package l1j.server.server.model.world.instance.dungeon.tower;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import l1j.server.DatabaseFactory;
import l1j.server.server.utils.SQLUtil;

public class DominanceDataLoader {

	public static DominanceDataLoader _instance;

	public Map<Integer, DominanceBoss> _list = new HashMap<Integer, DominanceBoss>();

	static private List<DominanceBoss> list;

	public static DominanceDataLoader getInstance() {
		if (_instance == null) {
			_instance = new DominanceDataLoader();
		}
		return _instance;
	}

	public static void reload() {
		DominanceDataLoader oldInstance = _instance;
		_instance = new DominanceDataLoader();
		oldInstance._list.clear();
	}

	private DominanceDataLoader() {
		loadQuestMonster();
	}

	private void loadQuestMonster() {
		if (list == null)
			list = new ArrayList<DominanceBoss>();
		synchronized (list) {
			list.clear();
			Connection con = null;
			PreparedStatement pstm = null;
			ResultSet rs = null;
			try {
				con = DatabaseFactory.getInstance().getConnection();
				pstm = con.prepareStatement("SELECT * FROM tower_of_dominance");
				rs = pstm.executeQuery();
				while (rs.next()) {
					DominanceBoss DT = new DominanceBoss();
					String spawn_time = rs.getString("spawn_time");
					DT.setBossNum(rs.getInt("boss_num"));
					DT.setBossName(rs.getString("npc_name"));
					DT.setNpcId(rs.getInt("npc_id"));
					DT.setMapX(rs.getInt("map_x"));
					DT.setMapY(rs.getInt("map_y"));
					DT.setMapId(rs.getInt("map_id"));
					DT.setMentuse(rs.getBoolean("ment_use"));
					DT.setMent(rs.getString("ment"));
					DT.setAllEffect(rs.getBoolean("send_effect_use"));
					DT.setEffectNum(rs.getInt("effect_id"));

					if (spawn_time.length() > 0) {
						StringTokenizer stt = new StringTokenizer(spawn_time, ",");
						int[][] time = new int[stt.countTokens()][2];
						int idx = 0;
						while (stt.hasMoreTokens()) {
							String boss_time = stt.nextToken();
							String boss_h = boss_time.substring(0, boss_time.indexOf(":"));
							String boss_m = boss_time.substring(boss_h.length() + 1, boss_time.length());
							time[idx][0] = Integer.valueOf(boss_h);
							time[idx][1] = Integer.valueOf(boss_m);
							idx += 1;
						}
						DT.setBossTime(time);
					}

					list.add(DT);
				}
			} catch (SQLException e) {
				e.printStackTrace();
			} finally {
				SQLUtil.close(rs, pstm, con);
			}
		}
	}

	static public List<DominanceBoss> getList() {
		synchronized (list) {
			return new ArrayList<DominanceBoss>(list);
		}
	}
	
	static public int getSize() {
		return list.size();
	}

	static public DominanceBoss find(int bossnum) {
		synchronized (list) {
			for (DominanceBoss b : list) {
				if (b.getBossNum() == bossnum)
					return b;
			}
			return null;
		}
	}

}