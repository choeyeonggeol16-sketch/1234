package l1j.server.server.model.world.instance.dungeon.hadin;

import l1j.server.server.datatables.CObjectDB;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;

import l1j.server.DatabaseFactory;
import l1j.server.server.IdFactory;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.Instance.L1DoorInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.templates.L1Npc;
import l1j.server.server.utils.SQLUtil;

public class HadinSpawn {
	
	private static HadinSpawn _instance;

	public static HadinSpawn getInstance() {
		if (_instance == null) {
			_instance = new HadinSpawn();
		}
		return _instance;
	}

	private HadinSpawn() {}

	public void fillSpawnTable(int mapid, int type) {
		fillSpawnTable(mapid, type, false);
	}

	public ArrayList<L1NpcInstance> fillSpawnTable(int mapid, int type, boolean RT) {
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		L1Npc l1npc = null;
		L1NpcInstance field = null;
		ArrayList<L1NpcInstance> list = null;
		if(RT)
			list = new ArrayList<L1NpcInstance>();

		try {
			con = DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("SELECT * FROM spawnlist_hadin");
			rs = pstm.executeQuery();
			int count = 0;
			while(rs.next()){
				if (type != rs.getInt("type")) continue;
				if(type >= 1 && type <= 3)
					count = 1;
				else
					count = 0;
				for(int i = 0 ; i <= count; i++){

				l1npc = CObjectDB.GetNPC(rs.getInt("npc_id"));
				if (l1npc != null) {
					try {
						field = CObjectDB.CreateNPC(rs.getInt("npc_id"));
						field.setId(IdFactory.getInstance().nextId());
						field.setX(rs.getInt("locx"));
						field.setY(rs.getInt("locy"));
					//	if(type != 0){


					//	}
						field.setMap((short) mapid);
						field.setHomeX(field.getX());
						field.setHomeY(field.getY());
						field.setHeading(rs.getInt("heading"));
						field.setLightSize(l1npc.getLightSize());
						field.refreshLight();
						field.setSpawnLocation(rs.getString("location"));
						if(field instanceof L1DoorInstance){
							L1DoorInstance fi = (L1DoorInstance) field;
							fi.setDoorId(rs.getInt("npc_id"));
							if(fi.getDoorId() == 900151 || fi.getDoorId() == 900153){//11시
								fi.setDirection(0);
								fi.setLeftEdgeLocation(fi.getX());
								fi.setRightEdgeLocation(fi.getX());
							} else if(fi.getDoorId() == 900152 || fi.getDoorId() == 900154){//1시
								fi.setDirection(1);
								fi.setLeftEdgeLocation(fi.getY());
								fi.setRightEdgeLocation(fi.getY());
							}
							fi.isPassibleDoor(false);
							fi.setPassable(1);
						}

						CWorld.getInstance().storeAndEnterWorld(field);
						if(RT)
							list.add(field);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				l1npc = null;
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}

		return list;
	}

	public ArrayList<L1NpcInstance> fillSpawnTable(int x, int y, int mapid, int type) {
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		L1Npc l1npc = null;
		L1NpcInstance field = null;
		ArrayList<L1NpcInstance> list = null;
		list = new ArrayList<L1NpcInstance>();
		try {
			con = DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("SELECT * FROM spawnlist_hadin");
			rs = pstm.executeQuery();
			while(rs.next()){
				if (type != rs.getInt("type")) continue;
				for(int i = 0 ; i <= 1; i++){

				l1npc = CObjectDB.GetNPC(rs.getInt("npc_id"));
				if (l1npc != null) {
					try {
						field = CObjectDB.CreateNPC(rs.getInt("npc_id"));
						field.setId(IdFactory.getInstance().nextId());
						field.setX(x);
						field.setY(y);
						field.setMap((short) mapid);
						field.setHomeX(field.getX());
						field.setHomeY(field.getY());
						field.setHeading(rs.getInt("heading"));
						field.setLightSize(l1npc.getLightSize());
						field.refreshLight();
						field.setSpawnLocation(rs.getString("location"));
						CWorld.getInstance().storeAndEnterWorld(field);
						list.add(field);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				l1npc = null;
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
		return list;
	}

	public HashMap<String, L1NpcInstance> fillSpawnTable2(int mapid, int type) {
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		L1Npc l1npc = null;
		L1NpcInstance field = null;
		HashMap<String, L1NpcInstance> list = null;
		list = new HashMap<String, L1NpcInstance>();
		try {
			con = DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("SELECT * FROM spawnlist_hadin");
			rs = pstm.executeQuery();
			//int count = 0;
			while(rs.next()){
				if (type != rs.getInt("type")) continue;
				l1npc = CObjectDB.GetNPC(rs.getInt("npc_id"));
				if (l1npc != null) {
					try {
						field = CObjectDB.CreateNPC(rs.getInt("npc_id"));
						field.setId(IdFactory.getInstance().nextId());
						field.setX(rs.getInt("locx"));
						field.setY(rs.getInt("locy"));
						field.setMap((short) mapid);
						field.setHomeX(field.getX());
						field.setHomeY(field.getY());
						field.setHeading(rs.getInt("heading"));
						field.setLightSize(l1npc.getLightSize());
						field.refreshLight();
						field.setSpawnLocation(rs.getString("location"));
						if(field instanceof L1DoorInstance){
							L1DoorInstance fi = (L1DoorInstance) field;
							fi.setDoorId(rs.getInt("npc_id"));
							fi.setDirection(0);
							fi.setLeftEdgeLocation(fi.getX());
							fi.setRightEdgeLocation(fi.getX());
							fi.isPassibleDoor(false);
							fi.setPassable(1);
						}

						CWorld.getInstance().storeAndEnterWorld(field);
						list.put(field.getSpawnLocation(), field);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				l1npc = null;
			}
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}

		return list;
	}

}
