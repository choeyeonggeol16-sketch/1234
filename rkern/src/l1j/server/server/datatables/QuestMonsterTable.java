package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import l1j.server.DatabaseFactory;
import l1j.server.server.templates.L1QuestMonster;
import l1j.server.server.utils.SQLUtil;

// 퀘스트 몬스터 시스템
public class QuestMonsterTable {
	
	public static QuestMonsterTable _instance;
	
	public Map<Integer, L1QuestMonster> _list = new HashMap<Integer, L1QuestMonster>();
	
	public static QuestMonsterTable getInstance() {
		if (_instance == null) {
			_instance = new QuestMonsterTable();
		}
		return _instance;
	}
	
	public static void reload() {
		QuestMonsterTable oldInstance = _instance;
		_instance = new QuestMonsterTable();
		oldInstance._list.clear();
	}
	
	private QuestMonsterTable(){
		loadQuestMonster();
	}
	
	private void loadQuestMonster(){
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		try {
			con = DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("SELECT * FROM quest_monster");
			rs = pstm.executeQuery();
			while(rs.next()) {
				L1QuestMonster CM = new L1QuestMonster();
				int npcid = rs.getInt("npc_id");
				CM.setNpcId(rs.getInt("npc_id"));
				CM.setNpcName(rs.getString("npc_name"));
				CM.setNpcCount(rs.getInt("npc_count"));
				CM.setMentuse(rs.getBoolean("ment_use"));
				CM.setMent(rs.getString("ment"));
				CM.setGiveItem(rs.getBoolean("item_compansate_use"));
				CM.setItemId(rs.getInt("item_id"));
				CM.setItemCount(rs.getInt("item_count"));
				CM.setEffectNum(rs.getInt("effect_id"));
				CM.setAllEffect(rs.getBoolean("send_effect_use"));
				_list.put(npcid, CM);
			}
		}catch(SQLException e) {
			e.printStackTrace();
		}finally {
			SQLUtil.close(rs, pstm, con);
		}
	}
	
	public L1QuestMonster getQuestMonster(int npcid){
		return _list.get(npcid);
	}
	
	public boolean isQuestMonster(int npcid){
		Set<Integer> keys = _list.keySet();
		int questmon;
		boolean OK = false;
		for (Iterator<Integer> iterator = keys.iterator(); iterator.hasNext();) {
			questmon = iterator.next();
			if(questmon == npcid){
				OK = true;
				break;
			}
		}
		return OK;
	}
}