package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.StringTokenizer;

import l1j.server.L1DatabaseFactory;
import l1j.server.Templates.L1Boss;
import l1j.server.Templates.L1Npc;
import l1j.server.server.utils.SQLUtil;

public final class BossMonsterSpawnList {

	static private List<L1Boss> list;
	private static Random _random = new Random(System.nanoTime());

	static public void init() {

		if (list == null)
			list = new ArrayList<L1Boss>();
		synchronized (list) {
			list.clear();
			Connection con = null;
			PreparedStatement st = null;
			ResultSet rs = null;
			try {
				con = L1DatabaseFactory.getInstance().getConnection();
				st = con.prepareStatement("SELECT * FROM spawnlist_boss");
				rs = st.executeQuery();
				while (rs.next()) {
					int npcid = rs.getInt("npcid");
					String mobname = rs.getString("monname");
					String mapname = rs.getString("mapname");
					int spawn_x = rs.getInt("spawn_x");
					int spawn_y = rs.getInt("spawn_y");
					int spawn_map = rs.getInt("spawn_map");
					int rnd_xy = rs.getInt("rnd_loc");
					int isment = rs.getInt("isment");
					int isyn = rs.getInt("isyn");
					int spwan_rate = rs.getInt("spawn_rate") * 10000;
					int rnd_time_min = rs.getInt("rnd_time_min");
					String spawn_time = rs.getString("spawn_time");
					String[] spawn_yoil = new String[] { "전체 "};
					String spawn_message = rs.getString("ment");
					String yn_spawn_message = rs.getString("yn_ment");
					try {
						StringTokenizer stt = new StringTokenizer(rs.getString("spawn_yoil"), ",");
						spawn_yoil = new String[stt.countTokens()];
						for (int i = 0; stt.hasMoreTokens(); ++i)
							spawn_yoil[i] = stt.nextToken();
					} catch (Exception e) {
					}

					L1Npc npc = NpcTable.getInstance().getTemplate(npcid);
					if (npc != null) {
						L1Boss b = new L1Boss();
						b.setMonName(mobname);
						b.setMapName(mapname);
						b.setNpcId(npcid);
						b.setX(spawn_x);
						b.setY(spawn_y);
						b.setYoil(spawn_yoil);
						b.setMap(spawn_map);
						b.setMent(isment == 1 ? true : false);
						b.setYn(isyn == 1 ? true : false);
						b.setMentMessage(spawn_message);
						b.setYnMessage(yn_spawn_message);
						b.setRndLoc(rnd_xy);
						b.setRndMinuteTime(rnd_time_min);
						b.setSpwanRate(spwan_rate);
						// 스폰시간 구분 추출.
						try {
			            if (spawn_time.length() > 0) {
			              String[] spawn = spawn_time.split(",");
			              int[][] time = new int[spawn.length][2];
			              int[][] real_time = new int[spawn.length][2];

			              for (int i = 0; i < spawn.length; i++) {
			                String boss_time = spawn[i];
			                String[] boss_result = boss_time.split(":");
			                int boss_h = Integer.valueOf(boss_result[0]).intValue();
			                int boss_m = Integer.valueOf(boss_result[1]).intValue();
			                int rnd_spawn_minute = rnd_time_min == 0 ? 0 : _random.nextInt(rnd_time_min);
			                real_time[i][0] = boss_h;
			                real_time[i][1] = boss_m;
			                time[i][0] = boss_h;
			                time[i][1] = (boss_m + rnd_spawn_minute);
			                while (time[i][1] >= 60) {
			                  time[i][1] -= 60;
			                  time[i][0] += 1;
			                }
			                if (time[i][0] >= 24) {
			                  time[i][0] -= 24;
			                }
			              }
			              b.setTime(time);
			              b.setRealTime(real_time);
			            }
						list.add(b);
						} catch (Exception e) {
							System.out.println("보스몹시간에러 : " + npcid);
						}
					}
				}
			} catch (Exception e) {
				e.getStackTrace();
			} finally {
				SQLUtil.close(rs);
				SQLUtil.close(st);
				SQLUtil.close(con);
			}
		}
	}

	static public List<L1Boss> getList() {
		synchronized (list) {
			return new ArrayList<L1Boss>(list);
		}
	}

	static public int getSize() {
		return list.size();
	}

	static public L1Boss find(int npcid) {
		synchronized (list) {
			for (L1Boss b : list) {
				if (b.getNpcId() == npcid)
					return b;
			}
			return null;
		}
	}
}
