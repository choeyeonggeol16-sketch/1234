package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import l1j.server.DatabaseFactory;
import l1j.server.server.model.CNpcMaker;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.templates.L1Npc;
import l1j.server.server.utils.NumberUtil;

public class CNpcMakerDB {
	private static final Logger _log = Logger.getLogger(CNpcMakerDB.class.getName());

	private final TIntObjectMap<CNpcMaker> _spawntable = new TIntObjectHashMap<CNpcMaker>(10240);

	private int _highestId;

	private static class SingletonHolder {
		private static CNpcMakerDB INSTANCE = new CNpcMakerDB();
	}

	public static CNpcMakerDB getInstance() {
		return SingletonHolder.INSTANCE;
	}

	private CNpcMakerDB() {
		fillSpawnTable();
	}

	public static void reload() {
		SingletonHolder.INSTANCE = new CNpcMakerDB();
	}

    public static void reloadMap(int mapId) {
        despawnForReload(CWorld.getInstance().getMapObjects(mapId), mapId);
        getInstance().reload(mapId);
    }

    public static void despawnForReload(Iterable<L1Object> members, int mapId) {
        for (L1Object member : members) {
            if (member instanceof L1MonsterInstance monster && monster.getMapId() == mapId
                    && monster.getHiddenStatus() == 0 && !monster.isDead()) {
                monster.re();
            }
        }
    }

	public void reload(int mpaid) {
		String sql = "SELECT * FROM spawnlist WHERE mapid = ?";

		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {

			pstm.setInt(1, mpaid);
			try (ResultSet rs = pstm.executeQuery()) {
				while (rs.next()) {
					int spawnid = rs.getInt("id");

					if (_spawntable.containsKey(spawnid)) {
						_spawntable.remove(spawnid);
					}

					int npcTemplateId = rs.getInt("npc_templateid");
					L1Npc template1 = CObjectDB.GetNPC(npcTemplateId);

					if (template1 == null) {
						_log.warning("mob data for id:" + npcTemplateId + " missing in npc table");
						continue;
					}
					
					if (rs.getInt("count") == 0) {
						continue;
					}

					double amount_rate = MapsTable.getInstance().getMonsterAmount(rs.getShort("mapid"));
					int count = calcCount(template1, rs.getInt("count"), amount_rate);

					if (count == 0) {
						continue;
					}

					CNpcMaker spawnDat = new CNpcMaker(template1);
					spawnDat.setId(spawnid);
					spawnDat.setAmount(count);
					spawnDat.setGroupId(rs.getInt("group_id"));
					spawnDat.setLocX(rs.getInt("locx"));
					spawnDat.setLocY(rs.getInt("locy"));
					spawnDat.setRandomx(rs.getInt("randomx"));
					spawnDat.setRandomy(rs.getInt("randomy"));
					spawnDat.setLocX1(rs.getInt("locx1"));
					spawnDat.setLocY1(rs.getInt("locy1"));
					spawnDat.setLocX2(rs.getInt("locx2"));
					spawnDat.setLocY2(rs.getInt("locy2"));
					spawnDat.setHeading(rs.getInt("heading"));
					spawnDat.setMinRespawnDelay(rs.getInt("min_respawn_delay"));
					spawnDat.setMaxRespawnDelay(rs.getInt("max_respawn_delay"));
					spawnDat.setMapId(rs.getShort("mapid"));
					spawnDat.setRespawnScreen(rs.getBoolean("respawn_screen"));
					spawnDat.setMovementDistance(rs.getInt("movement_distance"));
					spawnDat.setRest(rs.getBoolean("rest"));
					spawnDat.setName(template1.get_name());

					if (count > 1 && spawnDat.getLocX1() == 0) {
						int range = Math.min(count * 6, 30);
						spawnDat.setLocX1(spawnDat.getLocX() - range);
						spawnDat.setLocY1(spawnDat.getLocY() - range);
						spawnDat.setLocX2(spawnDat.getLocX() + range);
						spawnDat.setLocY2(spawnDat.getLocY() + range);
					}

					spawnDat.init();

					_spawntable.put(spawnDat.getId(), spawnDat);
					if (spawnDat.getId() > _highestId) {
						_highestId = spawnDat.getId();
					}
				}
			}
		} catch (Exception e) {
			_log.log(Level.SEVERE, "CNpcMakerDB reload(mapid) error", e);
		}
	}

	private void fillSpawnTable() {
		int spawnCount = 0;
		String sql = "SELECT * FROM spawnlist";

		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql);
			 ResultSet rs = pstm.executeQuery()) {

			while (rs.next()) {
				int npcTemplateId = rs.getInt("npc_templateid");
				L1Npc template1 = CObjectDB.GetNPC(npcTemplateId);

				if (template1 == null) {
					_log.warning("mob data for id:" + npcTemplateId + " missing in npc table");
					continue;
				}
				
				if (rs.getInt("count") == 0) {
					continue;
				}

				double amount_rate = MapsTable.getInstance().getMonsterAmount(rs.getShort("mapid"));
				int count = calcCount(template1, rs.getInt("count"), amount_rate);
				if (count == 0) {
					continue;
				}

				int spawnid = rs.getInt("id");
				CNpcMaker spawnDat = new CNpcMaker(template1);
				spawnDat.setId(spawnid);
				spawnDat.setAmount(count);
				spawnDat.setGroupId(rs.getInt("group_id"));
				spawnDat.setLocX(rs.getInt("locx"));
				spawnDat.setLocY(rs.getInt("locy"));
				spawnDat.setRandomx(rs.getInt("randomx"));
				spawnDat.setRandomy(rs.getInt("randomy"));
				spawnDat.setLocX1(rs.getInt("locx1"));
				spawnDat.setLocY1(rs.getInt("locy1"));
				spawnDat.setLocX2(rs.getInt("locx2"));
				spawnDat.setLocY2(rs.getInt("locy2"));
				spawnDat.setHeading(rs.getInt("heading"));
				spawnDat.setMinRespawnDelay(rs.getInt("min_respawn_delay"));
				spawnDat.setMaxRespawnDelay(rs.getInt("max_respawn_delay"));
				spawnDat.setMapId(rs.getShort("mapid"));
				spawnDat.setRespawnScreen(rs.getBoolean("respawn_screen"));
				spawnDat.setMovementDistance(rs.getInt("movement_distance"));
				spawnDat.setRest(rs.getBoolean("rest"));
				spawnDat.setName(template1.get_name());

				if (count > 1 && spawnDat.getLocX1() == 0) {
					int range = Math.min(count * 6, 30);
					spawnDat.setLocX1(spawnDat.getLocX() - range);
					spawnDat.setLocY1(spawnDat.getLocY() - range);
					spawnDat.setLocX2(spawnDat.getLocX() + range);
					spawnDat.setLocY2(spawnDat.getLocY() + range);
				}

				spawnDat.init();
				spawnCount += spawnDat.getAmount();

				_spawntable.put(spawnDat.getId(), spawnDat);
				if (spawnDat.getId() > _highestId) {
					_highestId = spawnDat.getId();
				}
			}

		} catch (Exception e) {
			_log.log(Level.SEVERE, "CNpcMakerDB fillSpawnTable error", e);
		}
		_log.config("총 monster 수: " + spawnCount + "마리 로드 완료");
	}

	public CNpcMaker getTemplate(int id) {
		return _spawntable.get(id);
	}

	public void delspawntable(int id) {
		_spawntable.remove(id);
	}

	public void addNewSpawn(CNpcMaker spawn) {
		_highestId++;
		spawn.setId(_highestId);
		_spawntable.put(spawn.getId(), spawn);
	}

	public static void storeSpawn(L1PcInstance pc, L1Npc npc) {
		int count = 1;
		int randomXY = 12;
		int minRespawnDelay = 60;
		int maxRespawnDelay = 120;
		String note = npc.get_name();

		String sql = "INSERT INTO spawnlist SET location=?, count=?, npc_templateid=?, group_id=?, locx=?, locy=?, randomx=?, randomy=?, heading=?, min_respawn_delay=?, max_respawn_delay=?, mapid=?";

		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {

			pstm.setString(1, note);
			pstm.setInt(2, count);
			pstm.setInt(3, npc.get_npcId());
			pstm.setInt(4, 0);
			pstm.setInt(5, pc.getX());
			pstm.setInt(6, pc.getY());
			pstm.setInt(7, randomXY);
			pstm.setInt(8, randomXY);
			pstm.setInt(9, pc.getHeading());
			pstm.setInt(10, minRespawnDelay);
			pstm.setInt(11, maxRespawnDelay);
			pstm.setInt(12, pc.getMapId());
			pstm.execute();

		} catch (Exception e) {
			_log.log(Level.SEVERE, "CNpcMakerDB storeSpawn error", e);
		}
	}

	private static int calcCount(L1Npc npc, int count, double rate) {
		if (rate == 0) {
			return 0;
		}
		if (rate == 1 || npc.isAmountFixed()) {
			return count;
		} else {
			return NumberUtil.randomRound((count * rate));
		}
	}
}