package l1j.server.BonusMaps;

import java.sql.ResultSet;
import java.sql.SQLException;

import l1j.server.Template.ArrangeHelper.MJArrangeParseeFactory;
import l1j.server.Template.ArrangeHelper.MJArrangeParser;
import l1j.server.server.model.Instance.L1PcInstance;

public class BonusMapInfo {
	static BonusMapInfo newInstance(ResultSet rs) throws SQLException {
		BonusMapInfo pInfo = newInstance();
		pInfo._map_id = rs.getInt("map_id");
		pInfo._map_name = rs.getString("map_name");
		pInfo._item_id = rs.getString("item_id");
		pInfo._item_name = rs.getString("item_name");
		pInfo._item_min_count = rs.getString("item_min_count");	
		pInfo._item_max_count = rs.getString("item_max_count");	
		pInfo._min_probability = rs.getString("min_drop_chance");
		pInfo._max_probability = rs.getString("max_drop_chance");
		return pInfo;
	}
	
	private static BonusMapInfo newInstance() {
		return new BonusMapInfo();
	}
	
	private int _map_id;
	private String _map_name;
	private String _item_id;
	private String _item_name;
	private String _item_min_count;
	private String _item_max_count;
	private String _min_probability;
	private String _max_probability;
	
	private BonusMapInfo() {
	}
	
	public int get_map_id() {
		return _map_id;
	}
	
	public String get_map_name() {
		return _map_name;
	}

	public String get_item_id() {
		return _item_id;
	}
	
	public String get_item_name() {
		return _item_name;
	}
	
	public String get_bonus_min_count() {
		return _item_min_count;
	}
	
	public String get_bonus_max_count() {
		return _item_max_count;
	}
	
	public String get_min_probability() {
		return _min_probability;
	}
	
	public String get_max_probability() {
		return _max_probability;
	}
	
	public int calc_probability(int i) {
		int probability = 0;
		String[] drop_min_chance = (String[]) MJArrangeParser.parsing(_min_probability, ",", MJArrangeParseeFactory.createStringArrange()).result();
		String[] drop_max_chance = (String[]) MJArrangeParser.parsing(_max_probability, ",", MJArrangeParseeFactory.createStringArrange()).result();
		int min_chance = Integer.parseInt(drop_min_chance[i]);
		int max_chance = Integer.parseInt(drop_max_chance[i]);
		if (min_chance == max_chance) {
			probability = Integer.parseInt(drop_max_chance[i]);
		}else
			probability = (int) (Math.random() * (max_chance - min_chance)) + min_chance;
		
		return probability;
	}
	
	public int calc_count(int i) {
		String[] drop_min_count = (String[]) MJArrangeParser.parsing(_item_min_count, ",", MJArrangeParseeFactory.createStringArrange()).result();
		String[] drop_max_count = (String[]) MJArrangeParser.parsing(_item_max_count, ",", MJArrangeParseeFactory.createStringArrange()).result();
		int min_count = Integer.parseInt(drop_min_count[i]);
		int max_count = Integer.parseInt(drop_max_count[i]);
		
		int count 		= 0;
		if (min_count == max_count)
			count 	= max_count;
		else {
			count = (int) (Math.random() * (max_count - min_count)) + min_count;
		}
		
		return count;
	}
	
	public boolean isMap(L1PcInstance pc) {
		int mapId = get_map_id();
		if (pc.getMapId() == mapId) {
			return true;
		}
		return false;
	}
}
