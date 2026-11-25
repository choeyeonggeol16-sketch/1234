package l1j.server.server.model;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import gnu.trove.map.TLongObjectMap;
import gnu.trove.map.hash.TLongObjectHashMap;

import l1j.server.DatabaseFactory;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.gametime.L1GameTimeClock;

public class Dungeon {

	private static final Logger _log = Logger.getLogger(Dungeon.class.getName());

	private static Dungeon _instance = null;

	private TLongObjectMap<NewDungeon> _dungeonMap = new TLongObjectHashMap<NewDungeon>(1024);

	private enum DungeonType {
		NONE, SHIP_FOR_FI, SHIP_FOR_HEINE, SHIP_FOR_PI, SHIP_FOR_HIDDENDOCK, SHIP_FOR_GLUDIN, SHIP_FOR_TI
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
		if (oldInstance != null && oldInstance._dungeonMap != null) {
			oldInstance._dungeonMap.clear();
		}
	}

	/**
	 * mapId, locX, locY 좌표를 하나의 64비트 long 키로 비트 압축 
	 */
	private long makeKey(int mapId, int locX, int locY) {
		return ((long) mapId << 32) | ((long) (locX & 0xFFFF) << 16) | (locY & 0xFFFF);
	}

	private Dungeon() {
		String sql = "SELECT * FROM dungeon";

		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql);
			 ResultSet rs = pstm.executeQuery()) {

			while (rs.next()) {
				int srcMapId = rs.getInt("src_mapid");
				int srcX = rs.getInt("src_x");
				int srcY = rs.getInt("src_y");
				
				long key = makeKey(srcMapId, srcX, srcY);
				
				int newX = rs.getInt("new_x");
				int newY = rs.getInt("new_y");
				int newMapId = rs.getInt("new_mapid");
				int heading = rs.getInt("new_heading");
				
				DungeonType dungeonType = DungeonType.NONE;
				if ((srcX == 33423 || srcX == 33424 || srcX == 33425 || srcX == 33426) && srcY == 33502 && srcMapId == 4 // Heine선착장->FI행의 배
						|| (srcX == 32733 || srcX == 32734 || srcX == 32735 || srcX == 32736) && srcY == 32794 && srcMapId == 83) { // FI행의 배->Heine선착장
					dungeonType = DungeonType.SHIP_FOR_FI;
				} else if ((srcX == 32935 || srcX == 32936 || srcX == 32937) && srcY == 33058 && srcMapId == 70 // FI선착장->Heine행의 배
						|| (srcX == 32732 || srcX == 32733 || srcX == 32734 || srcX == 32735) && srcY == 32796 && srcMapId == 84) { // Heine행의 배->FI선착장
					dungeonType = DungeonType.SHIP_FOR_HEINE;
				} else if ((srcX == 32750 || srcX == 32751 || srcX == 32752) && srcY == 32874 && srcMapId == 445 // 숨겨진 선착장->해적섬행의 배
						|| (srcX == 32731 || srcX == 32732 || srcX == 32733) && srcY == 32796 && srcMapId == 447) { // 해적섬행의 배->숨겨진 선착장
					dungeonType = DungeonType.SHIP_FOR_PI;
				} else if ((srcX == 32296 || srcX == 32297 || srcX == 32298) && srcY == 33087 && srcMapId == 440 // 해적도선착장->숨겨진 선착장행의 배
						|| (srcX == 32735 || srcX == 32736 || srcX == 32737) && srcY == 32794 && srcMapId == 446) { // 숨겨진 선착장행의 배->해적도선착장
					dungeonType = DungeonType.SHIP_FOR_HIDDENDOCK;
				} else if ((srcX == 32630 || srcX == 32631 || srcX == 32632) && srcY == 32983 && srcMapId == 0 // TalkingIsland->TalkingIslandShiptoAdenMainland
						|| (srcX == 32733 || srcX == 32734 || srcX == 32735) && srcY == 32796 && srcMapId == 5) { // TalkingIslandShiptoAdenMainland->TalkingIsland
					dungeonType = DungeonType.SHIP_FOR_GLUDIN;
				} else if ((srcX == 32540 || srcX == 32542 || srcX == 32543 || srcX == 32544 || srcX == 32545) && srcY == 32728 && srcMapId == 4 // AdenMainland->AdenMainlandShiptoTalkingIsland
						|| (srcX == 32734 || srcX == 32735 || srcX == 32736 || srcX == 32737) && srcY == 32794 && srcMapId == 6) { // AdenMainlandShiptoTalkingIsland->AdenMainland
					dungeonType = DungeonType.SHIP_FOR_TI;
				}
				
				NewDungeon newDungeon = new NewDungeon(newX, newY, (short) newMapId, heading, dungeonType);
				if (_dungeonMap.containsKey(key)) {
					_log.log(Level.WARNING, "같은 키의 dungeon 데이터가 있습니다. map=" + srcMapId + ", x=" + srcX + ", y=" + srcY);
				}
				_dungeonMap.put(key, newDungeon);
			}
			_log.config("던전(Dungeon) 포탈 데이터 " + _dungeonMap.size() + "건 로드 완료");

		} catch (SQLException e) {
			_log.log(Level.SEVERE, "Dungeon load error", e);
		}
	}

	private static class NewDungeon {
		int _newX;
		int _newY;
		short _newMapId;
		int _heading;
		DungeonType _dungeonType;

		private NewDungeon(int newX, int newY, short newMapId, int heading, DungeonType dungeonType) {
			_newX = newX;
			_newY = newY;
			_newMapId = newMapId;
			_heading = heading;
			_dungeonType = dungeonType;
		}
	}

	public boolean dg(int locX, int locY, int mapId, L1PcInstance pc) {
		int servertime = L1GameTimeClock.getInstance().getGameTime().getSeconds();
		int nowtime = servertime % 86400;
		
		long key = makeKey(mapId, locX, locY);
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
			} else {
				if (nowtime >= 15 * 360
						&& nowtime < 25 * 360 // 1.30~2. 30
						|| nowtime >= 45 * 360
						&& nowtime < 55 * 360 // 4.30~5. 30
						|| nowtime >= 75 * 360
						&& nowtime < 85 * 360 // 7.30~8. 30
						|| nowtime >= 105 * 360
						&& nowtime < 115 * 360 // 10.30~11. 30
						|| nowtime >= 135 * 360 && nowtime < 145 * 360 || nowtime >= 165 * 360 && nowtime < 175 * 360 || nowtime >= 195 * 360 && nowtime < 205 * 360
						|| nowtime >= 225 * 360 && nowtime < 235 * 360) {
					if ((pc.getInventory().HaveItemAmount(40299, 1) && dungeonType == DungeonType.SHIP_FOR_GLUDIN) // TalkingIslandShiptoAdenMainland
							|| (pc.getInventory().HaveItemAmount(40301, 1) && dungeonType == DungeonType.SHIP_FOR_HEINE) // AdenMainlandShiptoForgottenIsland
							|| (pc.getInventory().HaveItemAmount(40302, 1) && dungeonType == DungeonType.SHIP_FOR_PI)) { // ShipPirateislandtoHiddendock
						teleportable = true;
					}
				} else if (nowtime >= 0 && nowtime < 360 || nowtime >= 30 * 360 && nowtime < 40 * 360 || nowtime >= 60 * 360 && nowtime < 70 * 360 || nowtime >= 90 * 360
						&& nowtime < 100 * 360 || nowtime >= 120 * 360 && nowtime < 130 * 360 || nowtime >= 150 * 360 && nowtime < 160 * 360 || nowtime >= 180 * 360
						&& nowtime < 190 * 360 || nowtime >= 210 * 360 && nowtime < 220 * 360) {
					if ((pc.getInventory().HaveItemAmount(40298, 1) && dungeonType == DungeonType.SHIP_FOR_TI) // AdenMainlandShiptoTalkingIsland
							|| (pc.getInventory().HaveItemAmount(40300, 1) && dungeonType == DungeonType.SHIP_FOR_FI) // ForgottenIslandShiptoAdenMainland
							|| (pc.getInventory().HaveItemAmount(40303, 1) && dungeonType == DungeonType.SHIP_FOR_HIDDENDOCK)) { // ShipHiddendocktoPirateisland
						teleportable = true;
					}
				}
			}

			if (teleportable) {
				pc.start_teleport(newX, newY, newMap, heading, 169, false, false);
				return true;
			}
		}
		return false;
	}
}