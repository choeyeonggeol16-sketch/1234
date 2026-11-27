package l1j.server.server.model.world.instance.raid.loader;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;

import l1j.server.DatabaseFactory;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.world.instance.raid.creator.RaidCreator;
import l1j.server.server.utils.SQLUtil;

/** 레이드를 시작 RaidCreator 객체를 로드하고 로드하고 관리하는 클래스 **/
public class RaidCreatorLoader {
    private static volatile RaidCreatorLoader _instance;

    public static RaidCreatorLoader getInstance() {
        RaidCreatorLoader current = _instance;
        if (current != null) return current;
        synchronized (RaidCreatorLoader.class) {
            if (_instance == null) _instance = new RaidCreatorLoader();
            return _instance;
        }
    }

    public static synchronized void release() {
        _instance = null;
    }

    public static synchronized void reload() {
        _instance = new RaidCreatorLoader();
    }

	private HashMap<Integer, RaidCreator> _creatorMaps;
	private RaidCreatorLoader(){
		load();
	}
	
	private void load(){
		Connection 			con		= null;
		PreparedStatement 	pstm 	= null;
		ResultSet 			rs 		= null;
		RaidCreator		creator	= null;
		StringBuilder 		sbQry	= new StringBuilder(256);
		_creatorMaps 				= new HashMap<Integer, RaidCreator>(4);

		try{
			sbQry.append("select * from ").append(RaidCreator.TB_NAME);
			con 	= DatabaseFactory.getInstance().getConnection();
			pstm 	= con.prepareStatement(sbQry.toString());
			rs		= pstm.executeQuery();
			while(rs.next()){
				creator = new RaidCreator();
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
	

	
	public boolean createRaid(L1PcInstance pc, L1ItemInstance item){
		RaidCreator creator = _creatorMaps.get(item.getItemId());
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
