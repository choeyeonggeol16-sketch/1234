package l1j.server.SetItems;

import java.sql.ResultSet;
import java.sql.SQLException;

public class SetItemsInfo {
	
	public static SetItemsInfo newInstance(ResultSet rs) throws SQLException{
		SetItemsInfo Info = newInstance();
		Info._id = rs.getInt("id");
		Info._name = rs.getString("name");
		Info._set_items = rs.getString("set_items_id");
		Info._poly_id = rs.getInt("poly_id");
		Info._poly_desc = rs.getString("poly_desc");
		Info._high_poly_level = rs.getInt("high_poly_level");
		Info._high_poly_id = rs.getInt("high_poly_id");
		Info._high_poly_desc = rs.getString("high_poly_desc");
		Info._option_size = rs.getString("option_size");
		return Info;
	}
	
	public static SetItemsInfo newInstance(){
		return new SetItemsInfo();
	}

	private int _id;
	private String _name;
	private String _set_items;
	private int _poly_id;
	private String _poly_desc;
	private int _high_poly_level;
	private int _high_poly_id;
	private String _high_poly_desc;
	private String _option_size;
	
	public SetItemsInfo(){
	}
	
	public void set_id(int val){
		_id = val;
	}
	public int get_id(){
		return _id;
	}
	
	public void set_name(String val){
		_name = val;
	}
	public String get_name(){
		return _name;
	}
	
	public void set_Items(String val){
		_set_items = val;
	}
	public String get_Items(){
		return _set_items;
	}
	
	public void set_poly_id(int val){
		_poly_id = val;
	}
	public int get_poly_id(){
		return _poly_id;
	}
	
	public void set_poly_desc(String val){
		_poly_desc = val;
	}
	public String get_poly_desc(){
		return _poly_desc;
	}
	
	public void set_high_poly_level(int val){
		_high_poly_level = val;
	}
	public int get_high_poly_level(){
		return _high_poly_level;
	}
	
	public void set_high_poly_id(int val){
		_high_poly_id = val;
	}
	public int get_high_poly_id(){
		return _high_poly_id;
	}
	
	public void set_high_poly_desc(String val){
		_high_poly_desc = val;
	}
	public String get_high_poly_desc(){
		return _high_poly_desc;
	}
	
	public void set_option_size(String val){
		_option_size = val;
	}
	public String get_option_size(){
		return _option_size;
	}
}
