package l1j.server.ExchangeListItem;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Random;

import l1j.server.server.model.Instance.L1PcInstance;

public class ExchangeListItemInfo {
	static ExchangeListItemInfo newInstance(ResultSet rs) throws SQLException {
		ExchangeListItemInfo ExInfo = newInstance();
		ExInfo._main_item_id = rs.getInt("main_item_id");
		ExInfo._main_item_name = rs.getString("main_item_name");
		ExInfo._material_item_id = rs.getString("material_item_id");
		ExInfo._material_item_count = rs.getString("material_item_count");
		ExInfo._material_item_enchant = rs.getString("material_item_enchant");
		ExInfo._success_item_id = rs.getInt("success_item_id");
		ExInfo._success_item_count = rs.getInt("success_item_count");
		ExInfo._success_item_enchant = rs.getInt("success_item_enchant");
		ExInfo._success_item_name = rs.getString("success_item_name");
		ExInfo._min_probability = rs.getInt("min_probability");
		ExInfo._max_probability = rs.getInt("max_probability");
		ExInfo._is_broadcast = rs.getBoolean("is_broadcast");
		return ExInfo;
	}
	
	private static ExchangeListItemInfo newInstance() {
		return new ExchangeListItemInfo();
	}
	
	private int _main_item_id;
	private String _main_item_name;
	private String _material_item_id;
	private String _material_item_count;
	private String _material_item_enchant;
	private int _success_item_id;
	private int _success_item_count;
	private int _success_item_enchant;
	private String _success_item_name;
	private int _min_probability;
	private int _max_probability;
	private boolean _is_broadcast;
	
	private ExchangeListItemInfo() {
	}

	public int get_main_item_id() {
		return _main_item_id;
	}
	
	public String get_main_item_name() {
		return _main_item_name;
	}
	
	public String get_meterial_item_id() {
		return _material_item_id;
	}
	
	public String get_meterial_item_count() {
		return _material_item_count;
	}
	
	public String get_meterial_item_enchant() {
		return _material_item_enchant;
	}
	
	public int get_success_item_id() {
		return _success_item_id;
	}	
	
	public int get_success_item_count() {
		return _success_item_count;
	}
	public int get_success_item_enchant() {
		return _success_item_enchant;
	}
	
	public String get_success_item_name() {
		return _success_item_name;
	}
	
	public int get_min_probability() {
		return _min_probability;
	}
	
	public int get_max_probability() {
		return _max_probability;
	}
	 
	public boolean is_broadcast(){
		return _is_broadcast;
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
}
