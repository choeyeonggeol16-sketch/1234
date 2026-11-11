package l1j.server.FishingRodInfo;

import java.sql.ResultSet;
import java.sql.SQLException;

public class FishingRodInfo {
	public static FishingRodInfo newInstance(ResultSet rs) throws SQLException{
		FishingRodInfo Info = newInstance();
		Info._fishing_rod_item = rs.getInt("fishing_rod_item");
		Info._fishing_rod_item_name = rs.getString("fishing_rod_item_name");
		Info._fishing_item_id = rs.getInt("fishing_itemid");
		Info._fishing_item_name = rs.getString("fishing_item_name");
		Info._probability_min = rs.getInt("probability_min");
		Info._probability_max = rs.getInt("probability_max");
		Info._effect_id = rs.getInt("effect_id");
		Info._ment = rs.getString("ment");
		Info._isbroadcast = rs.getBoolean("is_broad_cast");
		return Info;
	}
	
	public static FishingRodInfo newInstance(){
		return new FishingRodInfo();
	}

	private int _fishing_rod_item;
	private String _fishing_rod_item_name;
	private int _fishing_item_id;
	private String _fishing_item_name;
	private int _probability_min;
	private int _probability_max;
	private int _effect_id;
	private String _ment;
	private boolean _isbroadcast;
	
	public FishingRodInfo(){
	}
	
	public int get_fishing_rod_item(){
		return _fishing_rod_item;
	}
	
	public FishingRodInfo set_fishing_rod_item(int val){
		_fishing_rod_item = val;
		return this;
	}
	
	public String get_fishing_rod_item_name(){
		return _fishing_rod_item_name;
	}
	
	public FishingRodInfo set_fishing_rod_item_name(String val){
		_fishing_rod_item_name = val;
		return this;
	}
	
	public int get_fishing_item_id(){
		return _fishing_item_id;
	}
	
	public FishingRodInfo set_fishing_item_id(int val){
		_fishing_item_id = val;
		return this;
	}
	
	public FishingRodInfo set_fishing_item_name(String val){
		_fishing_item_name = val;
		return this;
	}
	public String get_fishing_item_name(){
		return _fishing_item_name;
	}
	
	public FishingRodInfo set_probability_min(int val){
		_probability_min = val;
		return this;
	}
	public int get_probability_min(){
		return _probability_min;
	}
	
	public FishingRodInfo set_probability_max(int val){
		_probability_max = val;
		return this;
	}
	public int get_probability_max(){
		return _probability_max;
	}
	
	public FishingRodInfo set_effect_id(int val){
		_effect_id = val;
		return this;
	}
	public int get_effect_id(){
		return _effect_id;
	}
	
	public FishingRodInfo set_ment(String val){
		_ment = val;
		return this;
	}
	public String get_ment(){
		return _ment;
	}
	
	public FishingRodInfo setBroadCast(boolean val){
		_isbroadcast = val;
		return this;
	}
	public boolean isBroadCast(){
		return _isbroadcast;
	}
}
