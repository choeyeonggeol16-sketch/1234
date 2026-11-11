package l1j.server.ServerMacroSystem;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;

import l1j.server.L1DatabaseFactory;
import l1j.server.server.GeneralThreadPool;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.utils.SQLUtil;

public class ServerMacroLoader implements Runnable{
	private static ServerMacroLoader _instance;
	public static ServerMacroLoader getInstance(){
		if(_instance == null)
			_instance = new ServerMacroLoader();
		return _instance;
	}
	
	public static void reload(){
		ServerMacroLoader tmp = _instance;
		_instance = new ServerMacroLoader();
		_instance.start();
		if(tmp != null){
			tmp.clear();
			tmp = null;
		}
	}
	
	public static void release(){
		if(_instance != null){
			_instance.clear();
			_instance = null;
		}
	}
	
	private boolean 							_isrun;
	private ArrayList<ServerMacroObject> 		_workList;
	private ServerMacroLoader(){
		_isrun	= false;
	}
	
	public void start(){
		_isrun						= true;
		Connection 			con 	= null;
		PreparedStatement 	pstm 	= null;
		ResultSet 			rs		= null;
		ServerMacroObject obj		= null;
		HashMap<Integer, ServerMacroObject> maps = new HashMap<Integer, ServerMacroObject>(128);
		try{
			con 		= L1DatabaseFactory.getInstance().getConnection();
			pstm 		= con.prepareStatement("SELECT * FROM server_macro");
			rs			= pstm.executeQuery();
			int rows	= SQLUtil.calcRows(rs);
			if(rows <= 0) {
				return;
			}
			_workList	= new ArrayList<ServerMacroObject>(SQLUtil.calcRows(rs));
			long cur	= System.currentTimeMillis();
			while(rs.next()){
				int 	id	= rs.getInt("id");
				long 	d	= rs.getInt("delay") * 1000;
				obj			= maps.get(id);
				if(obj == null){
					obj = new ServerMacroObject(id, d, cur);
					maps.put(id, obj);
				}
				
				byte[] b = rs.getBytes("message");
				obj.add(new String(b, "MS949"), rs.getString("type"));
			}
			
			for(ServerMacroObject o : maps.values())
				_workList.add(o);

			_isrun = true;
			Collections.sort(_workList);
			GeneralThreadPool.getInstance().execute(this);
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			SQLUtil.close(rs, pstm, con);
		}
	}
	
	public void clear(){
		_isrun = false;
		if(_workList != null){
			_workList.clear();
			_workList = null;
		}
	}
	
	@Override
	public void run() {
		long 				cur = System.currentTimeMillis();
		ServerMacroObject obj = null;
		 
		if(!_isrun)
			return;
		
		try{
			Collection<L1PcInstance> col = L1World.getInstance().getAllPlayers();
			int size = _workList.size();			
			for(int i=0; i<size; i++){
				obj = _workList.get(i);
				if(obj.nextMs > cur)
					break;
				
				for(L1PcInstance p : col)
					p.sendPackets(obj.get(), false);
				
				obj.nextMs = cur + obj.delay;
			}
		}catch(Exception e){
			if(_isrun)	e.printStackTrace();
		}finally{
			if(_isrun && _workList != null && _workList.size() > 0){
				Collections.sort(_workList);
				obj 	= _workList.get(0);
				long t 	= obj.nextMs - cur;
				if(t > 0)	GeneralThreadPool.getInstance().schedule(this, t);
				else		GeneralThreadPool.getInstance().execute(this);
			}
		}
	}
}
