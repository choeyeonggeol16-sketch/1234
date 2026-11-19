package l1j.server.MJRaidSystem.Loader;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;

import l1j.server.DatabaseFactory;
import l1j.server.MJRaidSystem.Spawn.MJRaidNpcSpawn;
import l1j.server.server.utils.SQLUtil;

public class MJRaidSpawnLoader {
	private static MJRaidSpawnLoader _instance;
	public static MJRaidSpawnLoader getInstance(){
		if(_instance == null)
			_instance = new MJRaidSpawnLoader();
		return _instance;
	}
	
	public static void release(){
		if(_instance != null){
			_instance.clear();
			_instance = null;
		}
	}
	
	public static void reload(){
		MJRaidSpawnLoader tmp = null;
		tmp	= _instance;
		_instance = new MJRaidSpawnLoader();
		tmp.clear();
		tmp = null;
	}
	
	private HashMap<Integer, ArrayList<MJRaidNpcSpawn>> _spawns;
	private MJRaidSpawnLoader(){
		load();
	}
	
	private void load(){
		Connection 			con			= null;
		PreparedStatement 	pstm 		= null;
		ResultSet 			rs 			= null;
		MJRaidNpcSpawn		spawn		= null;
		StringBuilder		sbQry		= new StringBuilder(256);
		ArrayList<MJRaidNpcSpawn> list	= null;
		_spawns							= new HashMap<Integer, ArrayList<MJRaidNpcSpawn>>(4);
		
		try{
			sbQry.append("select * from ").append(MJRaidNpcSpawn.TB_NAME);
			con 	= DatabaseFactory.getInstance().getConnection();
			pstm 	= con.prepareStatement(sbQry.toString());
			rs		= pstm.executeQuery();
			while(rs.next()){
				spawn = new MJRaidNpcSpawn();
				if(spawn.setInformation(rs)){
					list = _spawns.get(spawn.getId());
					if(list == null){
						list = new ArrayList<MJRaidNpcSpawn>();
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
	
	public ArrayList<MJRaidNpcSpawn> get(int i){
		return _spawns.get(i);
	}
	
	public void clear(){
		if(_spawns != null){
			_spawns.clear();
			_spawns = null;
		}
	}
}
