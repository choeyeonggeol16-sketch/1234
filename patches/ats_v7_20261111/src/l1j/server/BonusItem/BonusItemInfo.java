package l1j.server.BonusItem;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Random;

import l1j.server.Template.ArrangeHelper.MJArrangeParseeFactory;
import l1j.server.Template.ArrangeHelper.MJArrangeParser;
import l1j.server.server.model.Instance.L1PcInstance;

public class BonusItemInfo {
	static BonusItemInfo newInstance(ResultSet rs) throws SQLException {
		BonusItemInfo pInfo = newInstance(); 
		pInfo._item_id = rs.getInt("item_id");
		pInfo._item_name = rs.getString("item_name");
		pInfo._item_enchant = rs.getInt("item_enchant");
		pInfo._bonus_item_id = rs.getInt("bonus_id");
		pInfo._bonus_name = rs.getString("bonus_name");
		pInfo._bonus_min_item_count = rs.getInt("bonus_min_count");	
		pInfo._bonus_max_item_count = rs.getInt("bonus_max_count");	
		pInfo._min_probability = rs.getInt("min_probability");
		pInfo._max_probability = rs.getInt("max_probability");
		pInfo._equip_check = rs.getBoolean("isEquipType");
		pInfo._mapids = rs.getString("mapids");
		//pInfo._is_loggin = rs.getInt("is_loggin");
		return pInfo;
	}
	
	private static BonusItemInfo newInstance() {
		return new BonusItemInfo();
	}
	
	private int _is_loggin;
	private int _item_id;
	private String _item_name;
	private int _item_enchant;
	private int _bonus_item_id;
	private String _bonus_name;
	private int _bonus_min_item_count;
	private int _bonus_max_item_count;
	private int _min_probability;
	private int _max_probability;
	private boolean _equip_check;
	private String _mapids;
	
	private BonusItemInfo() {
	}

	public int get_item_id() {
		return _item_id;
	}
	
	String get_item_name() {
		return _item_name;
	}
	
	public int get_item_enchant() {
		return _item_enchant;
	}
	
	public int get_bonus_id() {
		return _bonus_item_id;
	}
	
	public String get_bonus_name() {
		return _bonus_name;
	}
	
	public int get_bonus_min_count() {
		return _bonus_min_item_count;
	}
	
	public int get_bonus_max_count() {
		return _bonus_max_item_count;
	}
	
	public int get_min_probability() {
		return _min_probability;
	}
	
	public int get_max_probability() {
		return _max_probability;
	}
	
	public String get_mapids() {
		return _mapids;
	}
	
	public boolean isEquipType() {
		return _equip_check;
	}
	
	public int getLogger() {
		return _is_loggin;
	}

	public void setLogger(int i) {
		_is_loggin = i;
	}
	
	void print() {
	}
	
	public int calc_probability(L1PcInstance pc) {
		Random random 	= new Random(System.nanoTime());
		int probability = 0;
		if (_max_probability == _min_probability)
			probability 	= _max_probability;
		else
			probability 	= random.nextInt(_max_probability - _min_probability) + _min_probability;
		
		return probability;
	}
	
	public int calc_count(L1PcInstance pc) {
		Random random 	= new Random(System.nanoTime());
		int count 		= 0;
		if (_bonus_max_item_count == _bonus_min_item_count)
			count 	= _bonus_max_item_count;
		else
			count 	= random.nextInt(_bonus_max_item_count - _bonus_min_item_count) + _bonus_min_item_count;
		
		return count;
	}
	
	
	public boolean isMap(L1PcInstance pc) {
		String[] bonusmap = (String[])MJArrangeParser.parsing(get_mapids(), ",", MJArrangeParseeFactory.createStringArrange()).result();
		for (String map : bonusmap) {
			int mapId = Integer.parseInt(map);
			if (pc.getMapId() == mapId) {
				return true;
			}
		}
		return false;
	}
}
