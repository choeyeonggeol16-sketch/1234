package l1j.server.BonusDropSystem;

import java.sql.ResultSet;
import java.sql.SQLException;

import l1j.server.server.model.Instance.L1PcInstance;

public class BonusDropSystemInfo {
	static BonusDropSystemInfo newInstance(ResultSet rs) throws SQLException {
		BonusDropSystemInfo pInfo = newInstance();
		pInfo._npc_id = rs.getInt("npc_id");
		pInfo._npc_name = rs.getString("npc_name");
		pInfo._item_id = rs.getInt("item_id");
		pInfo._item_name = rs.getString("item_name");
		pInfo._item_enchant = rs.getInt("item_enchant");
		pInfo._min_item_count = rs.getInt("min_count");	
		pInfo._max_item_count = rs.getInt("max_count");	
		pInfo._min_probability = rs.getInt("min_probability");
		pInfo._max_probability = rs.getInt("max_probability");
		pInfo._is_loggin = rs.getBoolean("is_loggin");
		return pInfo;
	}
	
	private static BonusDropSystemInfo newInstance() {
		return new BonusDropSystemInfo();
	}
	
	private boolean _is_loggin;
	private int _npc_id;
	private String _npc_name;
	private int _item_id;
	private String _item_name;
	private int _item_enchant;
	private int _min_item_count;
	private int _max_item_count;
	private int _min_probability;
	private int _max_probability;
	
	public int get_npcid() {
		return _npc_id;
	}
	
	public String get_npc_name() {
		return _npc_name;
	}

	public int get_item_id() {
		return _item_id;
	}
	
	public String get_item_name() {
		return _item_name;
	}
	
	public int get_item_enchant() {
		return _item_enchant;
	}

	public int get_min_count() {
		return _min_item_count;
	}
	
	public int get_max_count() {
		return _max_item_count;
	}
	
	public int get_min_probability() {
		return _min_probability;
	}
	
	public int get_max_probability() {
		return _max_probability;
	}
	
	void print() {
	}
	
	public int calc_probability(L1PcInstance pc) {
		int probability = 0;
		if (_max_probability == _min_probability)
			probability 	= _max_probability;
		else
			probability = (int) (Math.random() * (_max_probability - _min_probability)) + _min_probability;
		
		if (_is_loggin) {
			if (pc.isGm()) {
				System.out.println(String.format("보너스 지금 몬스터 : %s , 아이템 : +%d %s , 드랍 확률:(%d)", _npc_name, _item_enchant, _item_name, probability));
			}
		}
		
		return probability;
	}
	
	public int calc_count(L1PcInstance pc) {
		int count 		= 0;
		if (_max_item_count == _min_item_count)
			count 	= _max_item_count;
		else
			count = (int) (Math.random() * (_max_item_count - _min_item_count)) + _min_item_count;
		
		if (_is_loggin) {
			if (pc.isGm()) {
				System.out.println(String.format("보너스 지금 몬스터 : %s , 아이템 : +%d %s , 드랍 갯수:(%d)", _npc_name, _item_enchant, _item_name, count));
			}
		}
		return count;
	}
}
