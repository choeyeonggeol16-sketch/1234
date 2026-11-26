package l1j.server.server.model.character.experience.amplifier;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;

import l1j.server.DatabaseFactory;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.utils.GameUtils;
import l1j.server.server.utils.SQLUtil;

public class ExpAmplifierLoader {
    private static volatile ExpAmplifierLoader _instance;
    public static ExpAmplifierLoader getInstance(){
        ExpAmplifierLoader current = _instance;
        if (current != null) return current;
        synchronized (ExpAmplifierLoader.class) {
            current = _instance;
            if (current == null) _instance = current = new ExpAmplifierLoader();
        }
        return current;
    }
	
	public static void release(){
		if(_instance != null){
			_instance.clear();
			_instance = null;
		}
	}
	
	public static void reload(){
		ExpAmplifierLoader tmp = _instance;
		_instance = new ExpAmplifierLoader();
		if(tmp != null){
			tmp.clear();
			tmp = null;
		}
	}
	
	private HashMap<Integer, ExpAmplifier> 	_amps;
	private ExpAmplifierLoader(){
		Connection 				con 		= null;
		PreparedStatement 	pstm 		= null;
		ResultSet 				rs 			= null;
		ExpAmplifier			amp		= null;
		ArrayList<Integer>		maps		= null;
		int							msgid		= 0;
		int							magnifier= 0;
		int							id			= 0;
		_amps									= new HashMap<Integer, ExpAmplifier>(32);
		try{
			con 	= DatabaseFactory.getInstance().getConnection();
			pstm 	= con.prepareStatement("select * from tb_mjexpampsystem");
			rs 		= pstm.executeQuery();
			while(rs.next()){
				String s 	= rs.getString("maps");
				msgid 		= rs.getInt("msgid");
				magnifier	= rs.getInt("magnifier");
				if(!rs.getBoolean("isUse"))
					continue;
				
				maps 		= GameUtils.parseToIntArray(s, ",");
				if(maps == null || maps.size() <= 0)
					continue;
				
				for(Integer i : maps){
					amp = new ExpAmplifier();
					amp.setId(id++);
					amp.setMapId(i);
					amp.setMessageId(msgid);
					amp.setMagnifier(magnifier);
					_amps.put(i, amp);
				}
			}
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			SQLUtil.close(rs, pstm, con);
		}
	}
	
	public void set(L1PcInstance pc){
		if(!pc.getMap().isNormalZone(pc.getLocation())){
			pc.setExpAmplifier(null);
			return;
		}
		
		ExpAmplifier amp = _amps.get(pc.getMap().getBaseMapId());
		if(amp == null){
			pc.setExpAmplifier(null);
			return;
		}
		
		if(amp.isIn(pc)){
			pc.setExpAmplifier(amp);
			return;
		}
		
		pc.setExpAmplifier(null);
	}
	
	public void clear(){
		if(_amps != null){
			_amps.clear();
			_amps = null;
		}
	}
}
