package l1j.server.server.datatables;

import java.lang.reflect.Constructor;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.DatabaseFactory;
import l1j.server.server.IdFactory;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1FieldObjectInstance;
import l1j.server.server.templates.L1Npc;

public class LightSpawnTable {
	private static final Logger _log = Logger.getLogger(LightSpawnTable.class.getName());

	private static volatile LightSpawnTable _instance;

	public static LightSpawnTable getInstance() {
		if (_instance == null) {
			synchronized (LightSpawnTable.class) {
				if (_instance == null) {
					_instance = new LightSpawnTable();
				}
			}
		}
		return _instance;
	}

	private LightSpawnTable() {
		fillLightSpawnTable();
	}

	private void fillLightSpawnTable() {
		String sql = "SELECT * FROM spawnlist_light";
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql);
			 ResultSet rs = pstm.executeQuery()) {
			
			while (rs.next()) {
				int npcId = rs.getInt(2);
				L1Npc l1npc = CObjectDB.GetNPC(npcId);
				if (l1npc != null) {
					try {
						String s = l1npc.getImpl();
						Class<?> clazz = Class.forName("l1j.server.server.model.Instance." + s + "Instance");
						Constructor<?> constructor = clazz.getConstructors()[0];
						Object[] parameters = { l1npc };
						
						L1FieldObjectInstance field = (L1FieldObjectInstance) constructor.newInstance(parameters);
						field.setId(IdFactory.getInstance().nextId());
						field.setX(rs.getInt("locx"));
						field.setY(rs.getInt("locy"));
						field.setMap((short) rs.getInt("mapid"));
						field.setHomeX(field.getX());
						field.setHomeY(field.getY());
						field.setHeading(0);
						field.setLightSize(l1npc.getLightSize());

						L1World.getInstance().storeAndEnterWorld(field);
					} catch (Exception e) {
						_log.log(Level.SEVERE, "Light instantiation error for npcId: " + npcId, e);
					}
				}
			}
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "fillLightSpawnTable SQL error", e);
		}
	}
}