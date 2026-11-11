package l1j.server.RaidSystem.Loader;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;

import l1j.server.L1DatabaseFactory;
import l1j.server.RaidSystem.Dragon_Raid_Type;
import l1j.server.server.utils.SQLUtil;

public class Dragon_Raid_Types_Loader {
	private static Dragon_Raid_Types_Loader _instance;
	public static Dragon_Raid_Types_Loader getInstance(){
		if(_instance == null)
			_instance = new Dragon_Raid_Types_Loader();
		return _instance;
	}
	
	public static void release(){
		if(_instance != null){
			_instance.clear();
			_instance = null;
		}
	}
	
	public static void reload(){
		Dragon_Raid_Types_Loader tmp = null;
		tmp 		= _instance;
		_instance 	= new Dragon_Raid_Types_Loader();	
		tmp.clear();
		tmp = null;
	}
	
	private HashMap<Integer, Dragon_Raid_Type> _raidTypes;
	private Dragon_Raid_Types_Loader() {
		load();
	}
	
	private void load(){
		Connection 			con		= null;
		PreparedStatement 	pstm 	= null;
		ResultSet 			rs 		= null;
		Dragon_Raid_Type	type	= null;
		StringBuilder 		sbQry	= new StringBuilder(256);
		_raidTypes 					= new HashMap<Integer, Dragon_Raid_Type>(2);
		
		try{
			sbQry.append("select * from ").append(Dragon_Raid_Type.NAME);
			con 	= L1DatabaseFactory.getInstance().getConnection();
			pstm 	= con.prepareStatement(sbQry.toString());
			rs		= pstm.executeQuery();
			while(rs.next()){
				type = new Dragon_Raid_Type();
				if(type.setInformation(rs))
					_raidTypes.put(type.getId(), type);
			}
		}catch(Exception e){
			e.printStackTrace();
		}finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}
	
	public void clear(){
		if(_raidTypes != null){
			_raidTypes.clear();
			_raidTypes = null;
		}
	}
	
	public Dragon_Raid_Type get(int i){
		return _raidTypes.get(i);
	}
}