package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import l1j.server.DatabaseFactory;

public class CharacterConfigTable {

	private static final Logger _log = Logger.getLogger(CharacterConfigTable.class.getName());

	private static class SingletonHolder {
		private static final CharacterConfigTable INSTANCE = new CharacterConfigTable();
	}

	public static CharacterConfigTable getInstance() {
		return SingletonHolder.INSTANCE;
	}

	private CharacterConfigTable() {
	}

	/**
	 * 캐릭터 설정을 저장하거나 업데이트 
	 */
	public void storeCharacterConfig(int objectId, int length, byte[] data) throws SQLException {
		String sql = "INSERT INTO character_config (object_id, length, data) VALUES (?, ?, ?) "
				   + "ON DUPLICATE KEY UPDATE length = VALUES(length), data = VALUES(data)";

		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {

			pstm.setInt(1, objectId);
			pstm.setInt(2, length);
			pstm.setBytes(3, data);
			pstm.executeUpdate();

		}
	}

	/**
	 * 캐릭터 설정 삭제
	 */
	public void deleteCharacterConfig(int objectId) {
		String sql = "DELETE FROM character_config WHERE object_id = ?";

		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {

			pstm.setInt(1, objectId);
			pstm.executeUpdate();

		} catch (SQLException e) {
			_log.log(Level.SEVERE, "CharacterConfigTable delete error - objectId: " + objectId, e);
		}
	}
}