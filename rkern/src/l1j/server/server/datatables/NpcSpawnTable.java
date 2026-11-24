package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;

import l1j.server.Config;
import l1j.server.DatabaseFactory;
import l1j.server.server.model.L1Spawn;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.templates.L1Npc;

public class NpcSpawnTable {

	private static final Logger _log = Logger.getLogger(NpcSpawnTable.class.getName());
	private static NpcSpawnTable _instance;

	private final TIntObjectMap<L1Spawn> _spawntable = new TIntObjectHashMap<L1Spawn>(2048);

	private int _highestId;

	public static NpcSpawnTable getInstance() {
		if (_instance == null) {
			_instance = new NpcSpawnTable("all");
		}
		return _instance;
	}

	public static void reload() {
		NpcSpawnTable oldInstance = _instance;
		_instance = new NpcSpawnTable("L1Merchant");
		if (oldInstance != null) {
			oldInstance._spawntable.clear();
		}
	}

	private NpcSpawnTable(String type) {
		fillNpcSpawnTable(type);
	}

	private void fillNpcSpawnTable(String type) {
		int spawnCount = 0;
		String sql = "SELECT * FROM spawnlist_npc";

		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql);
			 ResultSet rs = pstm.executeQuery()) {

			while (rs.next()) {
				if (!Config.ALT_GMSHOP) {
					int npcid = rs.getInt(1);
					if (npcid >= Config.ALT_GMSHOP_MIN_ID && npcid <= Config.ALT_GMSHOP_MAX_ID) {
						continue;
					}
				}
				if (!Config.ALT_BASETOWN) {
					int npcid = rs.getInt(1);
					if (npcid >= Config.ALT_BASETOWN_MIN_ID && npcid <= Config.ALT_BASETOWN_MAX_ID) {
						continue;
					}
				}

				int npcTemplateid = rs.getInt("npc_templateid");
				L1Npc l1npc = CObjectDB.GetNPC(npcTemplateid);
				
				if (!type.equalsIgnoreCase("all")) {
					if (l1npc != null) {
						if (!type.equalsIgnoreCase(l1npc.getImpl())) {
							continue;
						}
					}
				}
				
				if (!Config.ALT_FISHEVENT || Config.하이네낚시 == 1) {
					if ((npcTemplateid >= 73341 && npcTemplateid <= 73348) || npcTemplateid == 1010555 || npcTemplateid == 1010556) {
						continue;
					}
				}

				L1Spawn l1spawn;
				if (l1npc == null) {
					_log.warning("mob data for id:" + npcTemplateid + " missing in npc table");
					l1spawn = null;
				} else {
					if (rs.getInt("count") == 0) {
						continue;
					}
					l1spawn = new L1Spawn(l1npc);
					int spawnId = rs.getInt("id");
					l1spawn.setId(spawnId);
					l1spawn.setAmount(rs.getInt("count"));
					l1spawn.setLocX(rs.getInt("locx"));
					l1spawn.setLocY(rs.getInt("locy"));
					l1spawn.setRandomx(rs.getInt("randomx"));
					l1spawn.setRandomy(rs.getInt("randomy"));
					l1spawn.setLocX1(0);
					l1spawn.setLocY1(0);
					l1spawn.setLocX2(0);
					l1spawn.setLocY2(0);
					l1spawn.setHeading(rs.getInt("heading"));
					l1spawn.setMinRespawnDelay(rs.getInt("respawn_delay"));
					l1spawn.setMapId(rs.getShort("mapid"));
					l1spawn.setMovementDistance(rs.getInt("movement_distance"));
					l1spawn.setName(l1npc.get_name());
					l1spawn.init();
					spawnCount += l1spawn.getAmount();

					_spawntable.put(spawnId, l1spawn);
					if (spawnId > _highestId) {
						_highestId = spawnId;
					}
				}
			}
		} catch (Exception e) {
			_log.log(Level.SEVERE, "NpcSpawnTable fillNpcSpawnTable error", e);
		}

		_log.config("NPC 배치 리스트 " + _spawntable.size() + "건 로드");
		_log.fine("총 NPC 수 " + spawnCount + "건");
	}
	
	public void storeSpawn(L1PcInstance pc, L1Npc npc) {
		String sql = "INSERT INTO spawnlist_npc SET location=?, count=?, npc_templateid=?, locx=?, locy=?, heading=?, mapid=?";

		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			
			pstm.setString(1, npc.get_name());
			pstm.setInt(2, 1);
			pstm.setInt(3, npc.get_npcId());
			pstm.setInt(4, pc.getX());
			pstm.setInt(5, pc.getY());
			pstm.setInt(6, pc.getHeading());
			pstm.setInt(7, pc.getMapId());
			pstm.execute();
		} catch (Exception e) {
			_log.log(Level.SEVERE, "NpcSpawnTable storeSpawn error", e);
		}
	}

	public void removeSpawn(L1NpcInstance paramL1NpcInstance) {
		String selectSql = "SELECT id FROM spawnlist_npc WHERE npc_templateid=? AND mapid=? AND locx=? AND locy=?";
		String deleteSql = "DELETE FROM spawnlist_npc WHERE npc_templateid=? AND mapid=? AND locx=? AND locy=?";

		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement selectPstm = con.prepareStatement(selectSql)) {
			
			selectPstm.setInt(1, paramL1NpcInstance.getNpcId());
			selectPstm.setInt(2, paramL1NpcInstance.getMapId());
			selectPstm.setInt(3, paramL1NpcInstance.getX());
			selectPstm.setInt(4, paramL1NpcInstance.getY());
			
			try (ResultSet rs = selectPstm.executeQuery()) {
				if (rs.next()) {
					int spawnId = rs.getInt("id");
					_spawntable.remove(spawnId);
				}
			}

			try (PreparedStatement deletePstm = con.prepareStatement(deleteSql)) {
				deletePstm.setInt(1, paramL1NpcInstance.getNpcId());
				deletePstm.setInt(2, paramL1NpcInstance.getMapId());
				deletePstm.setInt(3, paramL1NpcInstance.getX());
				deletePstm.setInt(4, paramL1NpcInstance.getY());
				deletePstm.execute();
			}
		} catch (Exception e) {
			_log.log(Level.SEVERE, "NpcSpawnTable removeSpawn error", e);
		}
	}

	public L1Spawn getTemplate(int i) {
		return _spawntable.get(i);
	}
}