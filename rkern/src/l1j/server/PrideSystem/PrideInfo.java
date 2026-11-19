package l1j.server.PrideSystem;

import java.sql.ResultSet;
import java.sql.SQLException;

public class PrideInfo {
	public static PrideInfo newInstance(ResultSet rs) throws SQLException{
		PrideInfo pInfo = newInstance();
		pInfo.object_id = rs.getInt("object_id");
		pInfo.target_object_id = rs.getInt("target_object_id");
		pInfo.target_name = rs.getString("target_name");
		return pInfo;
	}
	public static PrideInfo newInstance(int object_id, int target_object_id, String target_name){
		PrideInfo pInfo = newInstance();
		pInfo.object_id = object_id;
		pInfo.target_object_id = target_object_id;
		pInfo.target_name = target_name;
		return pInfo;
	}
	
	private static PrideInfo newInstance(){
		return new PrideInfo();
	}
	
	private int object_id;
	private int target_object_id;
	private String target_name;
	
	private PrideInfo(){}
	
	public int get_object_id(){
		return object_id;
	}
	public int get_target_object_id(){
		return target_object_id;
	}
	public String get_target_name(){
		return target_name;
	}
}
