package l1j.server.server.model.world.instance.raid.loader;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;

import l1j.server.DatabaseFactory;
import l1j.server.server.model.world.instance.raid.spawn.RaidNpcSpawn;
import l1j.server.server.utils.SQLUtil;

public class RaidSpawnLoader {
    private static volatile RaidSpawnLoader _instance;

    public static RaidSpawnLoader getInstance() {
        RaidSpawnLoader current = _instance;
        if (current != null) return current;
        synchronized (RaidSpawnLoader.class) {
            if (_instance == null) _instance = new RaidSpawnLoader();
            return _instance;
        }
    }

    public static synchronized void release() {
        _instance = null;
    }

    public static synchronized void reload() {
        _instance = new RaidSpawnLoader();
    }

	private HashMap<Integer, ArrayList<RaidNpcSpawn>> _spawns;
	private RaidSpawnLoader(){
		load();
	}
	
	private void load(){
		Connection 			con			= null;
		PreparedStatement 	pstm 		= null;
		ResultSet 			rs 			= null;
		RaidNpcSpawn		spawn		= null;
		StringBuilder		sbQry		= new StringBuilder(256);
		ArrayList<RaidNpcSpawn> list	= null;
		_spawns							= new HashMap<Integer, ArrayList<RaidNpcSpawn>>(4);
		
		try{
			sbQry.append("select * from ").append(RaidNpcSpawn.TB_NAME);
			con 	= DatabaseFactory.getInstance().getConnection();
			pstm 	= con.prepareStatement(sbQry.toString());
			rs		= pstm.executeQuery();
			while(rs.next()){
				spawn = new RaidNpcSpawn();
				if(spawn.setInformation(rs)){
					list = _spawns.get(spawn.getId());
					if(list == null){
						list = new ArrayList<RaidNpcSpawn>();
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
	
	public ArrayList<RaidNpcSpawn> get(int i){
		return _spawns.get(i);
	}
	

}
