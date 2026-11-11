package l1j.server.RaidSystem.Loader;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;

import l1j.server.L1DatabaseFactory;
import l1j.server.RaidSystem.Dragon_Raid_Spawn;
import l1j.server.server.utils.SQLUtil;

public class Dragon_Raid_Spawn_Loader {
	private static Dragon_Raid_Spawn_Loader _instance;
	public static Dragon_Raid_Spawn_Loader getInstance(){
		if(_instance == null)
			_instance = new Dragon_Raid_Spawn_Loader();
		return _instance;
	}
	
	public static void release(){
		if(_instance != null){
			_instance.clear();
			_instance = null;
		}
	}
	
	public static void reload(){
		Dragon_Raid_Spawn_Loader tmp = null;
		tmp	= _instance;
		_instance = new Dragon_Raid_Spawn_Loader();
		tmp.clear();
		tmp = null;
	}
	
	private HashMap<Integer, ArrayList<Dragon_Raid_Spawn>> _spawns;
	private Dragon_Raid_Spawn_Loader(){
		load();
	}
	
	private void load(){
		Connection 			con			= null;
		PreparedStatement 	pstm 		= null;
		ResultSet 			rs 			= null;
		Dragon_Raid_Spawn		spawn		= null;
		StringBuilder		sbQry		= new StringBuilder(256);
		ArrayList<Dragon_Raid_Spawn> list	= null;
		_spawns							= new HashMap<Integer, ArrayList<Dragon_Raid_Spawn>>(4);
		
		try{
			sbQry.append("select * from ").append(Dragon_Raid_Spawn.NAME);
			con 	= L1DatabaseFactory.getInstance().getConnection();
			pstm 	= con.prepareStatement(sbQry.toString());
			rs		= pstm.executeQuery();
			while(rs.next()){
				spawn = new Dragon_Raid_Spawn();
				if(spawn.setInformation(rs)){
					list = _spawns.get(spawn.getId());
					if(list == null){
						list = new ArrayList<Dragon_Raid_Spawn>();
						_spawns.put(spawn.getId(), list);
					}
					
					list.add(spawn);
				}
			}
		}catch(Exception e){
			e.printStackTrace();
		}finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}
	
	public ArrayList<Dragon_Raid_Spawn> get(int i) {
		return _spawns.get(i);
	}
	
	public void clear(){
		if(_spawns != null){
			_spawns.clear();
			_spawns = null;
		}
	}
}
