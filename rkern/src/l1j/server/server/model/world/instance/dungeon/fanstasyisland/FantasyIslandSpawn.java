package l1j.server.server.model.world.instance.dungeon.fanstasyisland;

import l1j.server.server.datatables.CObjectDB;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import l1j.server.DatabaseFactory;
import l1j.server.server.IdFactory;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.templates.L1Npc;
import l1j.server.server.utils.SQLUtil;


public class FantasyIslandSpawn {
	private static FantasyIslandSpawn _instance;

	public static FantasyIslandSpawn getInstance() {
		if (_instance == null) {
			_instance = new FantasyIslandSpawn();
		}
		return _instance;
	}

	private FantasyIslandSpawn() {}

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
			pstm = con.prepareStatement("SELECT * FROM spawnlist_fantasyisland");
			rs = pstm.executeQuery();
			int count = 0;
			while(rs.next()){
				if (type != rs.getInt("type")) continue;
				if(type >= 2 && type <= 5)
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
						field.setMap((short) mapid);
						field.setHomeX(field.getX());
						field.setHomeY(field.getY());
						field.setHeading(rs.getInt("heading"));
						field.setLightSize(l1npc.getLightSize());
						field.refreshLight();
						field.setSpawnLocation(rs.getString("location"));
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
}
