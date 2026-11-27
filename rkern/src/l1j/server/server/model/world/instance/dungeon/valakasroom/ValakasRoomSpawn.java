package l1j.server.server.model.world.instance.dungeon.valakasroom;

import l1j.server.server.datatables.CObjectDB;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import l1j.server.DatabaseFactory;
import l1j.server.server.IdFactory;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.world.cleanup.MapCleaner;
import l1j.server.server.model.world.instance.CInstantZone;
import l1j.server.server.model.world.instance.CInstantZoneManager;
import java.util.logging.Level;
import java.util.logging.Logger;
import l1j.server.server.model.Instance.L1DoorInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.templates.L1Npc;
import l1j.server.server.utils.SQLUtil;


public class ValakasRoomSpawn {
    private static final Logger LOG = Logger.getLogger(ValakasRoomSpawn.class.getName());
	private static final ValakasRoomSpawn INSTANCE = new ValakasRoomSpawn();

	public static ValakasRoomSpawn getInstance() {
		return INSTANCE;
	}

	private ValakasRoomSpawn() {}

    static boolean hasPlayers(CInstantZone<?> zone) {
        for (L1Object obj : CWorld.getInstance().getVisibleObjects(zone.mapId()).values()) {
            if (obj instanceof L1PcInstance && obj.getMap() == zone.map()) return true;
        }
        return false;
    }

    static void clearRoom(CInstantZone<?> zone) {
        CWorld world = CWorld.getInstance();
        for (L1Object obj : world.snapshotZoneOccupants(zone.map())) {
            if (obj.getMap() != zone.map() || CInstantZoneManager.isPlayerCompanion(obj)) continue;
            try {
                if (obj instanceof L1NpcInstance npc) {
                    npc.deleteMe();
                } else if (obj instanceof L1PcInstance pc) {
                    CIOThreadCallback.getInstance().completeSession(pc.getNetConnection(), () -> {
                        if (zone.ownsMap() && pc.getMap() == zone.map() && !pc.get_teleport())
                            pc.start_teleport(33705, 32504, 4, 5, 169, true, false);
                    });
                }
            } catch (RuntimeException failure) {
                LOG.log(Level.WARNING, "Valakas room cleanup failed for object " + obj.getId(), failure);
                if (obj instanceof L1NpcInstance npc && npc.isDestroyed() && world.findObject(npc.getId()) == npc)
                    world.leaveAndRemoveObject(npc);
            }
        }
        MapCleaner.removeGroundItems(zone.map());
    }

	public void fillSpawnTable(int mapid, int type) {
		fillSpawnTable(mapid, type, false);
	}

	public ArrayList<L1NpcInstance> fillSpawnTable(int mapid, int type, boolean RT) {
		return fillSpawnTable(mapid, type, RT, false);
	}

	ArrayList<L1NpcInstance> fillRequiredSpawnTable(int mapid, int type) {
		return fillSpawnTable(mapid, type, true, true);
	}

	private ArrayList<L1NpcInstance> fillSpawnTable(int mapid, int type, boolean RT, boolean required) {
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
			pstm = con.prepareStatement("SELECT npc_id,locx,locy,heading FROM spawnlist_valakas_room WHERE type=?");
			pstm.setInt(1, type);
			rs = pstm.executeQuery();
			while(rs.next()){

				l1npc = CObjectDB.GetNPC(rs.getInt("npc_id"));
				if (l1npc != null) {
					try {
						field = CObjectDB.CreateNPC(rs.getInt("npc_id"));
						field.setId(IdFactory.getInstance().nextId());
						field.setX(rs.getInt("locx"));
						field.setY(rs.getInt("locy"));
						field.setMap((short) mapid);
						if(field instanceof L1DoorInstance){
							L1DoorInstance door = (L1DoorInstance) field;
							door.setDoorId(rs.getInt("npc_id"));
							if(door.getDoorId() == 7210013){
								door.setDirection(1);
								door.setLeftEdgeLocation(32999);
								door.setRightEdgeLocation(33001);
							}else if(door.getDoorId() == 7210014){
								door.setDirection(1);
								door.setLeftEdgeLocation(33051);
								door.setRightEdgeLocation(33053);
							}else if(door.getDoorId() == 7210015){
								door.setDirection(0);
								door.setLeftEdgeLocation(32734);
								door.setRightEdgeLocation(32738);
							}
							door.isPassibleDoor(false);
						}
						field.setHomeX(field.getX());
						field.setHomeY(field.getY());
						field.setHeading(rs.getInt("heading"));
						field.setLightSize(l1npc.getLightSize());
						field.refreshLight();
						CWorld.getInstance().storeAndEnterWorld(field);
						if(RT)
							list.add(field);
					} catch (Exception e) {
						if (required) throw new IllegalStateException("Valakas spawn failed for type " + type, e);
						e.printStackTrace();
					}
				} else if (required) {
					throw new IllegalStateException("Missing Valakas NPC template " + rs.getInt("npc_id"));
				}
				l1npc = null;
			}
		} catch (SQLException e) {
			if (required) throw new IllegalStateException("Valakas spawn table unavailable", e);
			e.printStackTrace();
		} catch (SecurityException e) {
			if (required) throw new IllegalStateException("Valakas spawn table unavailable", e);
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			if (required) throw new IllegalStateException("Valakas spawn table unavailable", e);
			e.printStackTrace();
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}

		if (required && list.isEmpty()) throw new IllegalStateException("Missing Valakas spawn type " + type);
		return list;
	}
}
