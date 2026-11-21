package l1j.server.server.model.world.instance.inn.loader;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;

import l1j.server.DatabaseFactory;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.world.instance.inn.InnHelper;
import l1j.server.server.model.world.instance.inn.InnMapInfo;
import l1j.server.server.utils.SQLUtil;

public class InnHelperLoader {
	private static InnHelperLoader _instance;
	public static InnHelperLoader getInstance(){
		if(_instance == null)
			_instance = new InnHelperLoader();
		return _instance;
	}
	
	private final HashMap<Integer, InnHelper> _helpers = new HashMap<>(32);
	private InnHelperLoader(){
		Connection 			con		= null;
		PreparedStatement 	pstm 	= null;
		ResultSet 			rs 		= null;
		try{
			con			= DatabaseFactory.getInstance().getConnection();
			pstm		= con.prepareStatement("select * from tb_inn_helper");
			rs			= pstm.executeQuery();
			HashMap<Integer, InnMapInfo[]> maps = new HashMap<>();
			while(rs.next()){
				InnHelper helper = InnHelper.create();
				helper.setHelperId(rs.getInt("helperId"));
				helper.setInnId(rs.getInt("innId"));
				helper.setMapInfo(maps.computeIfAbsent(helper.getInnId(), InnMapInfoLoader::getMapInfo));
				_helpers.put(helper.getHelperId(), helper);
			}
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			SQLUtil.close(rs, pstm, con);
		}
	}
	
	public InnHelper get(int helperId){
		return _helpers.get(helperId);
	}
	
	public boolean onResult(L1NpcInstance npc, L1PcInstance pc, String action, int amount){
		InnHelper helper = get(npc.getNpcId());
		if(helper != null){
			try {
				helper.onResult(npc, pc, action, amount);
			} catch (IOException e) {
				e.printStackTrace();
			}
			return true;
		}
		return false;
	}
	
	public boolean onTalk(L1NpcInstance npc, L1PcInstance pc){
		InnHelper helper = get(npc.getNpcId());
		if(helper != null){
			helper.onTalk(npc, pc);
			return true;
		}
		return false;
	}
	
	public boolean onAction(L1NpcInstance npc, L1PcInstance pc, String action){
		InnHelper helper = get(npc.getNpcId());
		if(helper != null){
			try {
				helper.onAction(npc, pc, action);
			} catch (IOException e) {
				e.printStackTrace();
			}
			return true;
		}
		return false;
	}
}
