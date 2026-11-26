package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;

import l1j.server.DatabaseFactory;
import l1j.server.server.IdFactory;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.Instance.L1DoorInstance;
import l1j.server.server.templates.L1Npc;

public class DoorSpawnTable {
	private static final Logger _log = Logger.getLogger(DoorSpawnTable.class.getName());
	private static DoorSpawnTable _instance;

	private final List<L1DoorInstance> _doorList = new ArrayList<L1DoorInstance>(256);
	
	private final TIntObjectMap<L1DoorInstance> _doorMap = new TIntObjectHashMap<L1DoorInstance>(256);
	
	public static DoorSpawnTable getInstance() {
		if (_instance == null) {
			_instance = new DoorSpawnTable();
		}
		return _instance;
	}

	public static void reload() {
		DoorSpawnTable oldInstance = _instance;
		_instance = new DoorSpawnTable();
		oldInstance._doorMap.clear();
		oldInstance._doorList.clear();
	}

	private DoorSpawnTable() {
		FillDoorSpawnTable();
	}
	
	private void FillDoorSpawnTable() {
		String sql = "SELECT * FROM spawnlist_door";

		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql);
			 ResultSet rs = pstm.executeQuery()) {

			L1Npc l1npc = CObjectDB.GetNPC(81158);
			if (l1npc == null) {
				_log.warning("Door base NPC template (81158) not found.");
				return;
			}

			while (rs.next()) {
				L1DoorInstance door = new L1DoorInstance(l1npc);
				door.setId(IdFactory.getInstance().nextId());
				door.setDoorId(rs.getInt(1));
				door.setCurrentSprite(rs.getInt(3));
				door.setX(rs.getInt(4));
				door.setY(rs.getInt(5));
				door.setMap((short) rs.getInt(6));
				door.setHomeX(rs.getInt(4));
				door.setHomeY(rs.getInt(5));
				door.setDirection(rs.getInt(7));
				door.setLeftEdgeLocation(rs.getInt(8));
				door.setRightEdgeLocation(rs.getInt(9));
				door.setMaxHp(rs.getInt(10));
				door.setCurrentHp(rs.getInt(10));
				door.setKeeperId(rs.getInt(11));

				CWorld.getInstance().storeAndEnterWorld(door);

				_doorList.add(door);
				_doorMap.put(door.getDoorId(), door);
			}
		} catch (Exception e) {
			_log.log(Level.SEVERE, "DoorSpawnTable load error", e);
		}

		buildDoorCacheOnce();
		
	}
	
	public List<L1DoorInstance> getDoorList() {
		return _doorList;
	}
	
	public L1DoorInstance getDoor(int doorId) {
		return _doorMap.get(doorId);
	}
	
	public void buildDoorCacheOnce() {
		for (L1DoorInstance door : _doorList) {
			if (door != null) {
				door.refreshDoorCacheByStatus();
			}
		}
	}
	
	public L1DoorInstance get(int i) {
		return _doorMap.get(i);
	}
}