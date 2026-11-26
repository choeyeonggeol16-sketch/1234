package l1j.server.server.model.npc.dialog;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;

import l1j.server.DatabaseFactory;

public class NPCTalkDataTable {

	private static final Logger _log = Logger.getLogger(NPCTalkDataTable.class.getName());
	private static NPCTalkDataTable _instance;

	private final TIntObjectMap<L1NpcTalkData> _datatable = new TIntObjectHashMap<L1NpcTalkData>(512);

	public static NPCTalkDataTable getInstance() {
		if (_instance == null) {
			_instance = new NPCTalkDataTable();
		}
		return _instance;
	}

	public static void reload() {
		NPCTalkDataTable oldInstance = _instance;
		_instance = new NPCTalkDataTable();
		if (oldInstance != null) {
			oldInstance._datatable.clear();
		}
	}

	private NPCTalkDataTable() {
		parseList();
	}

	private void parseList() {
		String sql = "SELECT * FROM npcaction";

		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql);
			 ResultSet rs = pstm.executeQuery()) {

			while (rs.next()) {
				L1NpcTalkData l1npctalkdata = new L1NpcTalkData();
				int npcId = rs.getInt(1);
				l1npctalkdata.setNpcID(npcId);
				l1npctalkdata.setNormalAction(rs.getString(2));
				l1npctalkdata.setCaoticAction(rs.getString(3));
				l1npctalkdata.setTeleportURL(rs.getString(4));
				l1npctalkdata.setTeleportURLA(rs.getString(5));
				
				_datatable.put(npcId, l1npctalkdata);
			}
			_log.config("NPC 액션 리스트 " + _datatable.size() + "건 로드 완료");

		} catch (SQLException e) {
			_log.log(Level.SEVERE, "error while creating npc action table", e);
		}
	}

	public L1NpcTalkData getTemplate(int i) {
		return _datatable.get(i);
	}
}