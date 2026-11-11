package l1j.server.SetItems;

import java.sql.ResultSet;
import java.sql.SQLException;

public class SetOptionInfo {
	public static SetOptionInfo newInstance(ResultSet rs) throws SQLException{
		SetOptionInfo Info = newInstance();
		Info._id = rs.getInt("set_id");
		Info._name = rs.getString("set_name");
		Info._equip_size = rs.getInt("equip_size");
		Info._option = rs.getString("option");
		Info._option_value = rs.getInt("option_value");
		return Info;
	}
	
	public static SetOptionInfo newInstance(){
		return new SetOptionInfo();
	}

	private int _id;
	private String _name;
	private int _equip_size;
	private String _option;
	private int _option_value;
	
	public SetOptionInfo(){
	}
	
	public SetOptionInfo setId(int val){
		_id = val;
		return this;
	}
	public int getId(){
		return _id;
	}
	
	public SetOptionInfo set_name(String val){
		_name = val;
		return this;
	}
	public String get_name(){
		return _name;
	}
	
	public SetOptionInfo set_equip_size(int val){
		_equip_size = val;
		return this;
	}
	public int get_equip_size(){
		return _equip_size;
	}
	
	public SetOptionInfo set_option(String val){
		_option = val;
		return this;
	}
	public String get_option(){
		return _option;
	}

	public SetOptionInfo set_option_value(int val){
		_option_value = val;
		return this;
	}
	public int get_option_value(){
		return _option_value;
	}
}
