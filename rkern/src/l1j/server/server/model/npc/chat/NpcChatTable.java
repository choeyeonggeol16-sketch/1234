package l1j.server.server.model.npc.chat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import l1j.server.DatabaseFactory;
import l1j.server.server.model.Instance.L1NpcInstance;

public class NpcChatTable {

	private static final Logger _log = Logger.getLogger(NpcChatTable.class.getName());
	private static NpcChatTable _instance;

	private final TIntObjectMap<L1NpcChat> _npcChatAppearance = new TIntObjectHashMap<L1NpcChat>(64);
	private final TIntObjectMap<L1NpcChat> _npcChatDead = new TIntObjectHashMap<L1NpcChat>(64);
	private final TIntObjectMap<L1NpcChat> _npcChatHide = new TIntObjectHashMap<L1NpcChat>(64);
	private final TIntObjectMap<L1NpcChat> _npcChatGameTime = new TIntObjectHashMap<L1NpcChat>(64);

	public static NpcChatTable getInstance() {
		if (_instance == null) {
			_instance = new NpcChatTable();
		}
		return _instance;
	}

	private NpcChatTable() {
		loadChatTable();
	}
	
	public static void reload() {
		NpcChatTable oldInstance = _instance;
		_instance = new NpcChatTable();
		if (oldInstance != null) {
			oldInstance._npcChatAppearance.clear();
			oldInstance._npcChatDead.clear();
			oldInstance._npcChatHide.clear();
			oldInstance._npcChatGameTime.clear();
		}
	}

	private void loadChatTable() {
		String sql = "SELECT * FROM npcchat";

		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql);
			 ResultSet rs = pstm.executeQuery()) {

			while (rs.next()) {
				L1NpcChat npcChat = new L1NpcChat();
				int npcId = rs.getInt("npc_id");
				npcChat.setNpcId(npcId);
				npcChat.setChatTiming(rs.getInt("chat_timing"));
				npcChat.setStartDelayTime(rs.getInt("start_delay_time"));
				npcChat.setChatId1(rs.getString("chat_id1"));
				npcChat.setChatId2(rs.getString("chat_id2"));
				npcChat.setChatId3(rs.getString("chat_id3"));
				npcChat.setChatId4(rs.getString("chat_id4"));
				npcChat.setChatId5(rs.getString("chat_id5"));
				npcChat.setChatInterval(rs.getInt("chat_interval"));
				npcChat.setShout(rs.getBoolean("is_shout"));
				npcChat.setWorldChat(rs.getBoolean("is_world_chat"));
				npcChat.setRepeat(rs.getBoolean("is_repeat"));
				npcChat.setRepeatInterval(rs.getInt("repeat_interval"));
				npcChat.setGameTime(rs.getInt("game_time"));

				int timing = npcChat.getChatTiming();
				if (timing == L1NpcInstance.CHAT_TIMING_APPEARANCE) {
					_npcChatAppearance.put(npcId, npcChat);
				} else if (timing == L1NpcInstance.CHAT_TIMING_DEAD) {
					_npcChatDead.put(npcId, npcChat);
				} else if (timing == L1NpcInstance.CHAT_TIMING_HIDE) {
					_npcChatHide.put(npcId, npcChat);
				} else if (timing == L1NpcInstance.CHAT_TIMING_GAME_TIME) {
					_npcChatGameTime.put(npcId, npcChat);
				}
			}
			
			_log.config("NpcChat 설정 로드 완료 (Appearance: " + _npcChatAppearance.size() 
					+ ", Dead: " + _npcChatDead.size() 
					+ ", Hide: " + _npcChatHide.size() 
					+ ", GameTime: " + _npcChatGameTime.size() + ")");

		} catch (SQLException e) {
			_log.log(Level.SEVERE, "NpcChatTable loadChatTable error", e);
		}
	}

	public L1NpcChat getTemplateAppearance(int i) {
		return _npcChatAppearance.get(i);
	}

	public L1NpcChat getTemplateDead(int i) {
		return _npcChatDead.get(i);
	}

	public L1NpcChat getTemplateHide(int i) {
		return _npcChatHide.get(i);
	}

	public L1NpcChat getTemplateGameTime(int i) {
		return _npcChatGameTime.get(i);
	}

	public L1NpcChat[] getAllGameTime() {
		return _npcChatGameTime.values(new L1NpcChat[_npcChatGameTime.size()]);
	}
}