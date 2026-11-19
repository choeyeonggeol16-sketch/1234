package l1j.server.MJRaidSystem.Loader;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;

import l1j.server.DatabaseFactory;
import l1j.server.MJRaidSystem.Compensator.CompensatorElement;
import l1j.server.MJRaidSystem.Compensator.ExpCompensator;
import l1j.server.MJRaidSystem.Compensator.GiveItemCompensator;
import l1j.server.MJRaidSystem.Compensator.ItemCompensator;
import l1j.server.server.utils.SQLUtil;

public class MJRaidCompensatorLoader {
	private static MJRaidCompensatorLoader _instance;
	public static MJRaidCompensatorLoader getInstance(){
		if(_instance == null)
			_instance = new MJRaidCompensatorLoader();
		return _instance;
	}
	
	public static void release(){
		if(_instance != null){
			_instance.clear();
			_instance = null;
		}
	}
	
	public static void reload(){
		MJRaidCompensatorLoader tmp = _instance;
		_instance = new MJRaidCompensatorLoader();
		tmp.clear();
		tmp = null;
	}
	
	private HashMap<Integer, ArrayList<CompensatorElement>> _compensators;
	private MJRaidCompensatorLoader(){
		Connection con						= null;
		PreparedStatement pstm 				= null;
		ResultSet rs 						= null;
		CompensatorElement element 			= null;
		String column						= null;
		String type							= null;
		int rid								= 0;
		int col1							= 0;
		int col2							= 0;
		int col3							= 0;
		ArrayList<CompensatorElement> list	= null;
		_compensators						= new HashMap<Integer, ArrayList<CompensatorElement>>();
		try{
			con 	= DatabaseFactory.getInstance().getConnection();
			pstm 	= con.prepareStatement("select * from tb_mjraid_compensators");
			rs		= pstm.executeQuery();
			while(rs.next()){
				column	= "raidId";
				rid		= rs.getInt(column);
				
				column	= "type";
				type	= rs.getString(column);
	
				column	= "col1";
				col1	= rs.getInt(column);
				
				column	= "col2";
				col2	= rs.getInt(column);
				
				column	= "col3";
				col3	= rs.getInt(column);
				if(type.equalsIgnoreCase("exp"))
					element = new ExpCompensator(col1);
				else if(type.equalsIgnoreCase("item"))
					element = new ItemCompensator(col1, col2, col3);
				else if(type.equalsIgnoreCase("commonItem"))
					element = new GiveItemCompensator(col1, col2);
				else
					continue;
				
				list = _compensators.get(rid);
				if(list == null){
					list = new ArrayList<CompensatorElement>();
					_compensators.put(rid, list);
				}
				list.add(element);
			}
			
		}catch(Exception e){
			e.printStackTrace();
		}finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}
	
	public ArrayList<CompensatorElement> get(int i){
		return _compensators.get(i);
	}
	
	public void clear(){
		if(_compensators != null){
			_compensators.clear();
			_compensators = null;
		}
	}
}
