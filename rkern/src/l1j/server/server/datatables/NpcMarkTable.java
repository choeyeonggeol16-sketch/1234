package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import gnu.trove.map.TIntIntMap;
import gnu.trove.map.hash.TIntIntHashMap;

import l1j.server.DatabaseFactory;

public class NpcMarkTable {
	private static final Logger _log = Logger.getLogger(NpcMarkTable.class.getName());
	
	private static volatile NpcMarkTable _instance;
	
	private final TIntIntMap _marks = new TIntIntHashMap();

	public static NpcMarkTable getInstance() {
		if (_instance == null) {
			synchronized (NpcMarkTable.class) {
				if (_instance == null) {
					_instance = new NpcMarkTable();
				}
			}
		}
		return _instance;
	}
	
	public static void reload() {
		NpcMarkTable oldInstance = _instance;
		_instance = new NpcMarkTable();
		if (oldInstance != null) {
			oldInstance.clear();
		}
	}
	
	private NpcMarkTable() {
		load();
	}
	
	private void load() {
		String sql = "SELECT npcid, markid FROM tb_npc_mark";
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql);
			 ResultSet rs = pstm.executeQuery()) {
			
			synchronized (_marks) {
				_marks.clear();
				while (rs.next()) {
					_marks.put(rs.getInt("npcid"), rs.getInt("markid"));
				}
			}
			_log.config("NPC 마크 설정 데이터 " + _marks.size() + "건 로드 완료");
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "load error for tb_npc_mark", e);
		}
	}
	
	public int get(int npcId) {
		synchronized (_marks) {
			if (!_marks.containsKey(npcId)) {
				return 0;
			}
			return _marks.get(npcId);
		}
	}
	
	public void clear() {
		synchronized (_marks) {
			_marks.clear();
		}
	}
}