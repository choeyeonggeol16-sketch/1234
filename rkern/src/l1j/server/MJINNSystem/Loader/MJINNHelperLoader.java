package l1j.server.MJINNSystem.Loader;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;

import l1j.server.DatabaseFactory;
import l1j.server.MJINNSystem.MJINNHelper;
import l1j.server.MJINNSystem.MJINNMapInfo;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.utils.SQLUtil;

public class MJINNHelperLoader {
	private static MJINNHelperLoader _instance;
	public static MJINNHelperLoader getInstance(){
		if(_instance == null)
			_instance = new MJINNHelperLoader();
		return _instance;
	}
	
	private final HashMap<Integer, MJINNHelper> _helpers = new HashMap<>(32);
	private MJINNHelperLoader(){
		Connection 			con		= null;
		PreparedStatement 	pstm 	= null;
		ResultSet 			rs 		= null;
		try{
			con			= DatabaseFactory.getInstance().getConnection();
			pstm		= con.prepareStatement("select * from tb_inn_helper");
			rs			= pstm.executeQuery();
			HashMap<Integer, MJINNMapInfo[]> maps = new HashMap<>();
			while(rs.next()){
				MJINNHelper helper = MJINNHelper.create();
				helper.setHelperId(rs.getInt("helperId"));
				helper.setInnId(rs.getInt("innId"));
				helper.setMapInfo(maps.computeIfAbsent(helper.getInnId(), MJINNMapInfoLoader::getMapInfo));
				_helpers.put(helper.getHelperId(), helper);
			}
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			SQLUtil.close(rs, pstm, con);
		}
	}
	
	public MJINNHelper get(int helperId){
		return _helpers.get(helperId);
	}
	
	public boolean onResult(L1NpcInstance npc, L1PcInstance pc, String action, int amount){
		MJINNHelper helper = get(npc.getNpcId());
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
		MJINNHelper helper = get(npc.getNpcId());
		if(helper != null){
			helper.onTalk(npc, pc);
			return true;
		}
		return false;
	}
	
	public boolean onAction(L1NpcInstance npc, L1PcInstance pc, String action){
		MJINNHelper helper = get(npc.getNpcId());
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
