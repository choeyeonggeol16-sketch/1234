package l1j.server.server;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import l1j.server.DatabaseFactory;

public class IdFactory {
	private static final Logger _log = Logger.getLogger(IdFactory.class.getName());

	private int _curId;
	private final Object _monitor = new Object();

	private static final int FIRST_ID = 0x10000000;

	private static final IdFactory _instance = new IdFactory();

	private IdFactory() {
		loadState();
	}

	public static IdFactory getInstance() {
		return _instance;
	}

	public int nextId() {
		synchronized (_monitor) {
            if (_curId < FIRST_ID || _curId >= Integer.MAX_VALUE) {
                throw new IllegalStateException("Persistent object ID range exhausted or uninitialized");
            }
			return _curId++;
		}
	}

	private void loadState() {
		String sql = "SELECT max(id)+1 AS nextid FROM ("
				+ "SELECT id FROM character_items UNION ALL "
				+ "SELECT id FROM character_teleport UNION ALL "
				+ "SELECT id FROM character_warehouse UNION ALL "
				+ "SELECT id FROM character_elf_warehouse UNION ALL "
                + "SELECT id FROM character_special_warehouse UNION ALL "
                + "SELECT id FROM character_supplementary_service UNION ALL "
				+ "SELECT objid AS id FROM characters UNION ALL "
				+ "SELECT clan_id AS id FROM clan_data UNION ALL "
				+ "SELECT id FROM clan_warehouse UNION ALL "
				+ "SELECT object_id AS id FROM companion_instance"
				+ " UNION ALL SELECT item_object_id AS id FROM letter"
				+ ") t";

		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql);
			 ResultSet rs = pstm.executeQuery()) {

			long id = FIRST_ID;
            if (rs.next()) {
                long next = rs.getLong("nextid");
                if (!rs.wasNull() && next > id) id = next;
            }
            if (id >= Integer.MAX_VALUE)
                throw new IllegalStateException("Persistent object ID overflow: " + id);
			_curId = (int) id;
	//		_log.info("■ 오브젝트ID 초기화 완료: " + _curId);
		} catch (SQLException e) {
            // Generating an ID from a zero/uninitialized cursor is unsafe:
            // it may collide with a character or warehouse item after restart.
            throw new IllegalStateException("IdFactory cannot verify persisted object ID high-water mark", e);
		}
	}
}
