package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.DatabaseFactory;
import l1j.server.server.IdFactory;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.Instance.L1FurnitureInstance;
import l1j.server.server.templates.L1Npc;

public class FurnitureSpawnTable {
	private static final Logger _log = Logger.getLogger(FurnitureSpawnTable.class.getName());

	private static FurnitureSpawnTable _instance;

	public static FurnitureSpawnTable getInstance() {
		if (_instance == null) {
			_instance = new FurnitureSpawnTable();
		}
		return _instance;
	}

	private FurnitureSpawnTable() {
		FillFurnitureSpawnTable();
	}

	private void FillFurnitureSpawnTable() {
		String sql = "SELECT * FROM spawnlist_furniture";
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql);
			 ResultSet rs = pstm.executeQuery()) {
			
			while (rs.next()) {
				int npcId = rs.getInt(2);
				L1Npc l1npc = CObjectDB.GetNPC(npcId);
				if (l1npc != null) {
					try {
						L1FurnitureInstance furniture = (L1FurnitureInstance) CObjectDB.CreateNPC(l1npc);
						furniture.setId(IdFactory.getInstance().nextId());

						furniture.setItemObjId(rs.getInt(1));
						furniture.setX(rs.getInt(3));
						furniture.setY(rs.getInt(4));
						furniture.setMap((short) rs.getInt(5));
						furniture.setHomeX(furniture.getX());
						furniture.setHomeY(furniture.getY());
						furniture.setHeading(0);

						CWorld.getInstance().storeAndEnterWorld(furniture);
					} catch (Exception e) {
						_log.log(Level.SEVERE, "Furniture instantiation error for npcId: " + npcId, e);
					}
				}
			}
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "FillFurnitureSpawnTable SQL error", e);
		}
	}

	public void insertFurniture(L1FurnitureInstance furniture) {
		String sql = "INSERT INTO spawnlist_furniture SET item_obj_id=?, npcid=?, locx=?, locy=?, mapid=?";
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			
			pstm.setInt(1, furniture.getItemObjId());
			pstm.setInt(2, furniture.getNpcTemplate().get_npcId());
			pstm.setInt(3, furniture.getX());
			pstm.setInt(4, furniture.getY());
			pstm.setInt(5, furniture.getMapId());
			pstm.execute();
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "insertFurniture error", e);
		}
	}

	public void deleteFurniture(L1FurnitureInstance furniture) {
		String sql = "DELETE FROM spawnlist_furniture WHERE item_obj_id=?";
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			
			pstm.setInt(1, furniture.getItemObjId());
			pstm.execute();
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "deleteFurniture error", e);
		}
	}
}