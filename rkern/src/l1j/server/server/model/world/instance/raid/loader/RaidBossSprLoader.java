package l1j.server.server.model.world.instance.raid.loader;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Iterator;

import l1j.server.DatabaseFactory;
import l1j.server.server.utils.SQLUtil;

public class RaidBossSprLoader {
	private static class SprAction{
		private HashMap<Integer, Long> actions = new HashMap<Integer, Long>(30);
	}
	
	private static RaidBossSprLoader _instance;
	
	public static RaidBossSprLoader getInstance(){
		if(_instance == null)
			_instance = new RaidBossSprLoader();
		return _instance;
	}
	
	public static void release(){
		if(_instance != null){
			_instance.clear();
			_instance = null;
		}
	}
	
	public static void reload(){
		RaidBossSprLoader tmp = _instance;
		_instance				= new RaidBossSprLoader();
		tmp.clear();
		tmp	= null;
	}
	
	private HashMap<Integer, SprAction> _sprs;
	private RaidBossSprLoader(){
		Connection con 			= null;
		PreparedStatement pstm 	= null;
		ResultSet rs 			= null;
		String column			= null;
		SprAction spr			= null;
		_sprs					= new HashMap<Integer, SprAction>(4);
		int sprid				= 0;
		int actid				= 0;
		int count				= 0;
		int rate				= 0;
		
		try{
			con = DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("SELECT * FROM spr_action");
			rs = pstm.executeQuery();
			while(rs.next()){
				column	= "spr_id";
				sprid 	= rs.getInt(column);
				
				spr		= _sprs.get(sprid);
				if(spr == null){
					spr = new SprAction();
					_sprs.put(sprid, spr);
				}
				
				column 	= "act_id";
				actid	= rs.getInt(column);
				
				column	= "framecount";
				count	= rs.getInt(column);
				
				column	= "framerate";
				rate	= rs.getInt(column);
				
				spr.actions.put(actid, calcSpeed(count, rate));
			}
			
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}
	
	public long get(int sprid, int actid){
		SprAction spr = _sprs.get(sprid);
		if(spr == null)
			return 640L;
		
		Long l = spr.actions.get(actid);
		if(l == null)
			return 640L;
		
		return l;
	}
	
	private long calcSpeed(int count, int rate){
		return (long)(count * 40L * (24D/rate));
	}
	
	public void clear(){
		if(_sprs == null)
			return;
		
		Iterator<SprAction> it = _sprs.values().iterator();
		SprAction spr = null;
		while(it.hasNext()){
			spr = it.next();
			if(spr != null)
				spr.actions.clear();
			
			spr = null;
		}
		
		_sprs.clear();
		_sprs = null;
	}
}
