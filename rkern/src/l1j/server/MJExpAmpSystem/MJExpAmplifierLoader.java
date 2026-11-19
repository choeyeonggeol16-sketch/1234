package l1j.server.MJExpAmpSystem;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;

import l1j.server.DatabaseFactory;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.utils.MJCommons;
import l1j.server.server.utils.SQLUtil;

public class MJExpAmplifierLoader {
    private static volatile MJExpAmplifierLoader _instance;
    public static MJExpAmplifierLoader getInstance(){
        MJExpAmplifierLoader current = _instance;
        if (current != null) return current;
        synchronized (MJExpAmplifierLoader.class) {
            current = _instance;
            if (current == null) _instance = current = new MJExpAmplifierLoader();
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
		MJExpAmplifierLoader tmp = _instance;
		_instance = new MJExpAmplifierLoader();
		if(tmp != null){
			tmp.clear();
			tmp = null;
		}
	}
	
	private HashMap<Integer, MJExpAmplifier> 	_amps;
	private MJExpAmplifierLoader(){
		Connection 				con 		= null;
		PreparedStatement 	pstm 		= null;
		ResultSet 				rs 			= null;
		MJExpAmplifier			amp		= null;
		ArrayList<Integer>		maps		= null;
		int							msgid		= 0;
		int							magnifier= 0;
		int							id			= 0;
		_amps									= new HashMap<Integer, MJExpAmplifier>(32);
		try{
			con 	= DatabaseFactory.getInstance().getConnection();
			pstm 	= con.prepareStatement("select * from tb_mjExpAmpSystem");
			rs 		= pstm.executeQuery();
			while(rs.next()){
				String s 	= rs.getString("maps");
				msgid 		= rs.getInt("msgid");
				magnifier	= rs.getInt("magnifier");
				if(!rs.getBoolean("isUse"))
					continue;
				
				maps 		= MJCommons.parseToIntArray(s, ",");
				if(maps == null || maps.size() <= 0)
					continue;
				
				for(Integer i : maps){
					amp = new MJExpAmplifier();
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
		
		MJExpAmplifier amp = _amps.get(pc.getMap().getBaseMapId());
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
