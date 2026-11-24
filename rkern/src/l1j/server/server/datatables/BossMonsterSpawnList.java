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

import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import l1j.server.DatabaseFactory;
import l1j.server.server.templates.L1Boss;
import l1j.server.server.templates.L1Npc;

public final class BossMonsterSpawnList {

	private static final Logger _log = Logger.getLogger(BossMonsterSpawnList.class.getName());

	private static final List<L1Boss> list = new ArrayList<>();
	
	private static final TIntObjectMap<L1Boss> _bossMap = new TIntObjectHashMap<>();

	public static void init() {
		synchronized (list) {
			// Construct the replacement in isolation. A failed SQL reload must not
			// clear the last successfully published spawn schedule.
			final List<L1Boss> loaded = new ArrayList<>();
			final TIntObjectMap<L1Boss> loadedMap = new TIntObjectHashMap<>();

			String sql = "SELECT * FROM spawnlist_boss_hot";
			try (Connection con = DatabaseFactory.getInstance().getConnection();
				 PreparedStatement st = con.prepareStatement(sql);
				 ResultSet rs = st.executeQuery()) {

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
					int display_effect = rs.getInt("display_effect");
					String spawn_time = rs.getString("spawn_time");
					String[] spawn_yoil = new String[] { "전체" }; 
					String spawn_message = rs.getString("ment");
					String yn_spawn_message = rs.getString("yn_ment");
					int nonespawntime = rs.getInt("랜덤시간부여");

					try {
						String yoilStr = rs.getString("spawn_yoil");
						if (yoilStr != null && !yoilStr.isEmpty()) {
							StringTokenizer stt = new StringTokenizer(yoilStr, ",");
							spawn_yoil = new String[stt.countTokens()];
							for (int i = 0; stt.hasMoreTokens(); ++i) {
								spawn_yoil[i] = stt.nextToken();
							}
						}
					} catch (Exception e) {
						// 요일 파싱 예외 무시
					}

					L1Npc npc = CObjectDB.GetNPC(npcid);

					if (npc != null) {
						L1Boss b = new L1Boss();
						b.setMonName(mobname);
						b.setMapName(mapname);
						b.setNpcId(npcid);
						b.setX(spawn_x);
						b.setY(spawn_y);
						b.setYoil(spawn_yoil);
						b.setMap(spawn_map);
						b.setMent(isment == 1);
						b.setYn(isyn == 1);
						b.set_display_effect(display_effect);
						b.setMentMessage(spawn_message);
						b.setYnMessage(yn_spawn_message);
						b.setRndLoc(rnd_xy);
						b.setnonespawntime(nonespawntime);

						// Malformed tokens must not silently become 00:00 boss spawns.
						if (spawn_time != null && !spawn_time.isEmpty()) {
							StringTokenizer stt = new StringTokenizer(spawn_time, ",");
							final int[][] parsed = new int[stt.countTokens()][2];
							int valid = 0;
							while (stt.hasMoreTokens()) {
								final String token = stt.nextToken().trim();
								final int colon = token.indexOf(':');
								if (colon < 1 || colon == token.length() - 1) {
									_log.warning("Invalid boss spawn time npcId=" + npcid + " value=" + token);
									continue;
								}
								try {
									final int hour = Integer.parseInt(token.substring(0, colon).trim());
									final int minute = Integer.parseInt(token.substring(colon + 1).trim());
									if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
										_log.warning("Invalid boss spawn time npcId=" + npcid + " value=" + token);
										continue;
									}
									parsed[valid][0] = hour;
									parsed[valid++][1] = minute;
								} catch (NumberFormatException invalid) {
									_log.warning("Invalid boss spawn time npcId=" + npcid + " value=" + token);
								}
							}
							if (valid == parsed.length) {
								b.setTime(parsed);
							} else {
								final int[][] times = new int[valid][2];
								System.arraycopy(parsed, 0, times, 0, valid);
								b.setTime(times);
							}
						}
						

						loaded.add(b);
						loadedMap.put(npcid, b); 
					}
				}
			} catch (SQLException e) {
				_log.log(Level.SEVERE, "BossMonsterSpawnList init error", e);
				throw new IllegalStateException("Boss spawn schedule DB load failed", e);
			}
			// Both indices must change together under the existing publication lock.
			list.clear();
			list.addAll(loaded);
			_bossMap.clear();
			_bossMap.putAll(loadedMap);
		}
	}

	public static List<L1Boss> getList() {
		synchronized (list) {
			return new ArrayList<>(list);
		}
	}

	public static int getSize() {
		synchronized (list) {
			return list.size();
		}
	}

	public static L1Boss find(int npcid) {
		synchronized (list) {
			return _bossMap.get(npcid);
		}
	}
}