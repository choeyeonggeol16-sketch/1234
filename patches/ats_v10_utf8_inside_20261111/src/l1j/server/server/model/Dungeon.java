/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 *
 * http://www.gnu.org/copyleft/gpl.html
 */
package l1j.server.server.model;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.L1DatabaseFactory;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.gametime.GameTimeClock;
import l1j.server.server.model.skill.SkillId;
import l1j.server.server.utils.SQLUtil;

public class Dungeon {

	private static Logger _log = Logger.getLogger(Dungeon.class.getName());

	private static Dungeon _instance = null;
 
	private Map<String, NewDungeon> _dungeonMap = new HashMap<String, NewDungeon>();

	private enum DungeonType {
		NONE, SHIP_FOR_FI, SHIP_FOR_HEINE, SHIP_FOR_PI, SHIP_FOR_HIDDENDOCK, SHIP_FOR_GLUDIO, SHIP_FOR_SI //## A39 글루디오행, 말하는 섬행 배 추가 
	};

	public static Dungeon getInstance() {
		if (_instance == null) {
			_instance = new Dungeon();
		}
		return _instance;
	}
	
	public static void reload() {
		Dungeon oldInstance = _instance;
		_instance = new Dungeon();
		oldInstance._dungeonMap.clear();
	}

	private Dungeon() {
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;

		try {
			con = L1DatabaseFactory.getInstance().getConnection();

			pstm = con.prepareStatement("SELECT * FROM dungeon");
			rs = pstm.executeQuery();
			NewDungeon newDungeon = null;
			while (rs.next()) {
				int srcMapId = rs.getInt("src_mapid");
				int srcX = rs.getInt("src_x");
				int srcY = rs.getInt("src_y");
				String key = new StringBuilder().append(srcMapId).append(srcX).append(srcY).toString();
				int newX = rs.getInt("new_x");
				int newY = rs.getInt("new_y");
				int newMapId = rs.getInt("new_mapid");
				int heading = rs.getInt("new_heading");
				DungeonType dungeonType = DungeonType.NONE;
				if (srcX == 33423 && srcY == 33502 && srcMapId == 4 // 하이네 선착장 -> 잊혀진 섬행의 배
						|| srcX == 32735 && srcY == 32794 && srcMapId == 83) { // 잊혀진 섬행의 배 -> 하이네 선착장
					dungeonType = DungeonType.SHIP_FOR_FI;
				} else if (srcX == 32936 && srcY == 33058 && srcMapId == 70 // 잊혀진 섬 선착장 -> 하이네행의 배
						|| srcX == 32732 && srcY == 32796 && srcMapId == 84) { // 하이네행의 배 -> 잊혀진 섬 선착장
					dungeonType = DungeonType.SHIP_FOR_HEINE;
				} else if (srcX == 32750 && srcY == 32874 && srcMapId == 445 // 숨겨진 선착장 -> 해적섬행의 배
						|| srcX == 32732 && srcY == 32796 && srcMapId == 447) { // 해적섬행의 배 -> 숨겨진 선착장
					dungeonType = DungeonType.SHIP_FOR_PI;
				} else if (srcX == 32297 && srcY == 33087 && srcMapId == 440 // 해적섬 선착장 -> 숨겨진 선착장행의 배
						|| srcX == 32735 && srcY == 32794 && srcMapId == 446) { // 숨겨진 선착장행의 배 -> 해적섬 선착장
					dungeonType = DungeonType.SHIP_FOR_HIDDENDOCK;
				} else if (srcX == 32543 && srcY == 32728 && srcMapId == 4 // 글루디오 선착장 -> 말하는 섬행의 배
						|| srcX == 32735 && srcY == 32794 && srcMapId == 6) { // 말하는 섬행의 배 -> 글루디오 선착장
					dungeonType = DungeonType.SHIP_FOR_SI;				
				} else if (srcX == 32631 && srcY == 32983 && srcMapId == 0 // 말하는 섬 선착장 -> 글루디오행의 배
						|| srcX == 32733 && srcY == 32796 && srcMapId == 5) { // 글루디오행의 배 -> 말하는 섬 선착장
					dungeonType = DungeonType.SHIP_FOR_GLUDIO;
				}
				newDungeon = new NewDungeon(newX, newY, (short) newMapId, heading, dungeonType);
				if (_dungeonMap.containsKey(key)) {
					_log.log(Level.WARNING, "같은 키의 dungeon 데이터가 있습니다.key=" + key);
				}
				_dungeonMap.put(key, newDungeon);
			}
		} catch (SQLException e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}

	private static class NewDungeon {
		int _newX;
		int _newY;
		short _newMapId;
		int _heading;
		DungeonType _dungeonType;

		private NewDungeon(int newX, int newY, short newMapId, int heading,
				DungeonType dungeonType) {
			_newX = newX;
			_newY = newY;
			_newMapId = newMapId;
			_heading = heading;
			_dungeonType = dungeonType;

		}
	}

	public boolean dg(int locX, int locY, int mapId, L1PcInstance pc) {
		int servertime = GameTimeClock.getInstance().getGameTime().getSeconds();
		int nowtime = servertime % 86400;
		String key = new StringBuilder().append(mapId).append(locX).append(locY).toString();
		if (_dungeonMap.containsKey(key)) {
			NewDungeon newDungeon = _dungeonMap.get(key);
			short newMap = newDungeon._newMapId;
			int newX = newDungeon._newX;
			int newY = newDungeon._newY;
			int heading = newDungeon._heading;
			DungeonType dungeonType = newDungeon._dungeonType;
			boolean teleportable = false;

			if (dungeonType == DungeonType.NONE) {
				teleportable = true;
			} else if (dungeonType == DungeonType.SHIP_FOR_FI) { // 하이네 선착장 -> 잊혀진 섬행의 배
																	// /
																	// 잊혀진 섬행의 배 -> 하이네 선착장
/*				if ((nowtime >= 5 * 3600 && nowtime < 6 * 3600
						|| nowtime >= 9 * 3600 && nowtime < 10 * 3600
						|| nowtime >= 13 * 3600 && nowtime < 14 * 3600 || nowtime >= 17 * 3600
						&& nowtime < 18 * 3600)	*/
//## [A153] 배 시간 수정
				if ((nowtime >= 0 * 3600 && nowtime < 1 * 3600
						|| nowtime >= 3 * 3600 && nowtime < 4 * 3600
						|| nowtime >= 6 * 3600 && nowtime < 7 * 3600
						|| nowtime >= 9 * 3600 && nowtime < 10 * 3600
						|| nowtime >= 12 * 3600 && nowtime < 13 * 3600
						|| nowtime >= 15 * 3600 && nowtime < 16 * 3600
						|| nowtime >= 18 * 3600 && nowtime < 19 * 3600
						|| nowtime >= 21 * 3600 && nowtime < 22 * 3600)
//## [A153] 배 시간 수정
						&& pc.getInventory().checkItem(40300, 1)) { // 잊혀진 섬행 배표
					teleportable = true;
				}
			} else if (dungeonType == DungeonType.SHIP_FOR_HEINE) { // 잊혀진 섬 선착장 -> 하이네행의 배
																	// /
																	// 하이네행의 배 -> 잊혀진 섬 선착장
/*				if ((nowtime >= 7 * 3600 && nowtime < 8 * 3600
						|| nowtime >= 11 * 3600 && nowtime < 12 * 3600
						|| nowtime >= 15 * 3600 && nowtime < 16 * 3600 || nowtime >= 19 * 3600
						&& nowtime < 20 * 3600)	*/
				if ((nowtime >= 1.5 * 3600 && nowtime < 2.5 * 3600
						|| nowtime >= 4.5 * 3600 && nowtime < 5.5 * 3600
						|| nowtime >= 7.5 * 3600 && nowtime < 8.5 * 3600
						|| nowtime >= 10.5 * 3600 && nowtime < 11.5 * 3600
						|| nowtime >= 13.5 * 3600 && nowtime < 14.5 * 3600
						|| nowtime >= 16.5 * 3600 && nowtime < 17.5 * 3600
						|| nowtime >= 19.5 * 3600 && nowtime < 20.5 * 3600
						|| nowtime >= 22.5 * 3600 && nowtime < 23.5 * 3600)
						&& pc.getInventory().checkItem(40301, 1)) { // 하이네행 배표
					teleportable = true;
				}
			} else if (dungeonType == DungeonType.SHIP_FOR_PI) {// 숨겨진 선착장 -> 해적섬행의 배
																// /
																// 해적섬행의 배 -> 숨겨진 선착장
				if ((nowtime >= 1.5 * 3600 && nowtime < 2.5 * 3600
						|| nowtime >= 4.5 * 3600 && nowtime < 5.5 * 3600
						|| nowtime >= 7.5 * 3600 && nowtime < 8.5 * 3600
						|| nowtime >= 10.5 * 3600 && nowtime < 11.5 * 3600
						|| nowtime >= 13.5 * 3600 && nowtime < 14.5 * 3600
						|| nowtime >= 16.5 * 3600 && nowtime < 17.5 * 3600
						|| nowtime >= 19.5 * 3600 && nowtime < 20.5 * 3600
						|| nowtime >= 22.5 * 3600 && nowtime < 23.5 * 3600)
						&& pc.getInventory().checkItem(40302, 1)) { // 해적섬행 배표
					teleportable = true;
				}
			} else if (dungeonType == DungeonType.SHIP_FOR_HIDDENDOCK) { // 해적섬 선착장 -> 숨겨진 선착장행의 배
																			// /
																			// 숨겨진 선착장행의 배 -> 해적섬 선착장
				if ((nowtime >= 0 * 3600 && nowtime < 1 * 3600
						|| nowtime >= 3 * 3600 && nowtime < 4 * 3600
						|| nowtime >= 6 * 3600 && nowtime < 7 * 3600
						|| nowtime >= 9 * 3600 && nowtime < 10 * 3600
						|| nowtime >= 12 * 3600 && nowtime < 13 * 3600
						|| nowtime >= 15 * 3600 && nowtime < 16 * 3600
						|| nowtime >= 18 * 3600 && nowtime < 19 * 3600
						|| nowtime >= 21 * 3600 && nowtime < 22 * 3600)
						&& pc.getInventory().checkItem(40303, 1)) { // 숨겨진 선착장행 배표
					teleportable = true;
				}
			} else if (dungeonType == DungeonType.SHIP_FOR_SI) { // 글루디오 선착장 -> 말하는 섬행의 배
																	// /
																	// 말하는 섬행의 배 -> 글루디오 선착장
				if ((nowtime >= 0 * 3600 && nowtime < 1 * 3600
						|| nowtime >= 3 * 3600 && nowtime < 4 * 3600
						|| nowtime >= 6 * 3600 && nowtime < 7 * 3600
						|| nowtime >= 9 * 3600 && nowtime < 10 * 3600
						|| nowtime >= 12 * 3600 && nowtime < 13 * 3600
						|| nowtime >= 15 * 3600 && nowtime < 16 * 3600
						|| nowtime >= 18 * 3600 && nowtime < 19 * 3600
						|| nowtime >= 21 * 3600 && nowtime < 22 * 3600)
						&& pc.getInventory().checkItem(40298, 1)) { // 말하는 섬행 배표
					teleportable = true;
				}
			} else if (dungeonType == DungeonType.SHIP_FOR_GLUDIO) { // 말하는 섬 선착장 -> 글루디오행의 배
																		// /
																		// 글루디오행의 배 -> 말하는 섬 선착장
				if ((nowtime >= 1.5 * 3600 && nowtime < 2.5 * 3600
						|| nowtime >= 4.5 * 3600 && nowtime < 5.5 * 3600
						|| nowtime >= 7.5 * 3600 && nowtime < 8.5 * 3600
						|| nowtime >= 10.5 * 3600 && nowtime < 11.5 * 3600
						|| nowtime >= 13.5 * 3600 && nowtime < 14.5 * 3600
						|| nowtime >= 16.5 * 3600 && nowtime < 17.5 * 3600
						|| nowtime >= 19.5 * 3600 && nowtime < 20.5 * 3600
						|| nowtime >= 22.5 * 3600 && nowtime < 23.5 * 3600)
						&& pc.getInventory().checkItem(40299, 1)) { // 글루디오 영지행 배표
					teleportable = true;
				}
			}
			if (teleportable) {
				// 2초간은 무적(앱솔루트 배리어 상태)으로 한다.
				pc.setSkillEffect(SkillId.앱솔루트_배리어, 2000);
				pc.teleport(newX, newY, newMap, heading, 169, false, false); 
				return true;
			}
			
		}
		return false;
	}
}