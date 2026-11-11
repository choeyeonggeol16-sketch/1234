package l1j.server.RaidSystem.Loader;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;

import l1j.server.L1DatabaseFactory;
import l1j.server.RaidSystem.Dragon_Raid_Creator;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.utils.SQLUtil;

public class Dragon_Raid_Creator_Loader {
	private static Dragon_Raid_Creator_Loader _instance;
	public static Dragon_Raid_Creator_Loader getInstance(){
		if(_instance == null)
			_instance = new Dragon_Raid_Creator_Loader();
		return _instance;
	}
	
	public static void release(){
		if(_instance != null){
			_instance.clear();
			_instance = null;
		}
	}
	
	public static void reload(){
		Dragon_Raid_Creator_Loader tmp = _instance;
		_instance 				= new Dragon_Raid_Creator_Loader();
		tmp.clear();
		tmp = null;
	}
	
	private HashMap<Integer, Dragon_Raid_Creator> _creatorMaps;
	private Dragon_Raid_Creator_Loader() {
		load();
	}
	
	private void load(){
		Connection 			con		= null;
		PreparedStatement 	pstm 	= null;
		ResultSet 			rs 		= null;
		Dragon_Raid_Creator		creator	= null;
		StringBuilder 		sbQry	= new StringBuilder(256);
		_creatorMaps 				= new HashMap<Integer, Dragon_Raid_Creator>(4);

		try{
			sbQry.append("select * from ").append(Dragon_Raid_Creator.NAME);
			con 	= L1DatabaseFactory.getInstance().getConnection();
			pstm 	= con.prepareStatement(sbQry.toString());
			rs		= pstm.executeQuery();
			while(rs.next()){
				creator = new Dragon_Raid_Creator();
				if(creator.setInformation(rs))
					_creatorMaps.put(creator.getNeedItemId(), creator);
			}
		}catch(Exception e){
			e.printStackTrace();
		}finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}
	
	public void clear(){
		if(_creatorMaps != null){
			_creatorMaps.clear();
			_creatorMaps = null;
		}
	}
	
	public boolean createRaid(L1PcInstance pc, ItemInstance item){
		Dragon_Raid_Creator creator = _creatorMaps.get(item.getItemId());
		if(creator != null){
			int castle_id1 = L1CastleLocation.getCastleIdByArea(pc);
			if (castle_id1 != 0) {
				pc.sendPackets("공성지역은 소환이 불가능합니다.");
				return true;
			}
			if (pc.getZoneType() != 1) {// 세이프존
				pc.sendPackets("세이프티존 에서만 사용이 가능합니다.");
				return true;
			}	
			creator.create(pc, item);
			return true;
		}
		return false;
	}
}
