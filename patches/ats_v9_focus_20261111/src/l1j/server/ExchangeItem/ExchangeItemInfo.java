package l1j.server.ExchangeItem;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Random;

import l1j.server.server.model.Instance.L1PcInstance;

public class ExchangeItemInfo {
	static ExchangeItemInfo newInstance(ResultSet rs) throws SQLException {
		ExchangeItemInfo ExInfo = newInstance();
		ExInfo._item_id = rs.getInt("item_id");
		ExInfo._item_name = rs.getString("item_name");
		ExInfo._use_count = rs.getInt("use_count");
		ExInfo._success_item_id = rs.getInt("success_item_id");
		ExInfo._success_item_count = rs.getInt("success_item_count");
		ExInfo._success_item_name = rs.getString("success_item_name");
		ExInfo._min_probability = rs.getInt("min_probability");
		ExInfo._max_probability = rs.getInt("max_probability");
		return ExInfo;
	}
	
	private static ExchangeItemInfo newInstance() {
		return new ExchangeItemInfo();
	}
	
	private int _item_id;
	private String _item_name;
	private int _use_count;
	private int _success_item_id;
	private int _success_item_count;
	private String _success_item_name;
	private int _min_probability;
	private int _max_probability;
	
	private ExchangeItemInfo() {
	}

	public int get_item_id() {
		return _item_id;
	}
	
	public String get_item_name() {
		return _item_name;
	}
	
	public int get_use_count() {
		return _use_count;
	}
	
	public int get_success_item_id() {
		return _success_item_id;
	}	
	
	public int get_success_item_count() {
		return _success_item_count;
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
