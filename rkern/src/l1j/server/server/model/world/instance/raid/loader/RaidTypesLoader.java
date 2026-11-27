package l1j.server.server.model.world.instance.raid.loader;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;

import l1j.server.DatabaseFactory;
import l1j.server.server.model.world.instance.raid.RaidType;
import l1j.server.server.utils.SQLUtil;

/** 레이드 인스턴스의 기본이 되는 레이드 템플릿(RaidType 클래스)를 로드하고 관리하는 클래스 **/
public class RaidTypesLoader {
    private static volatile RaidTypesLoader _instance;

    public static RaidTypesLoader getInstance() {
        RaidTypesLoader current = _instance;
        if (current != null) return current;
        synchronized (RaidTypesLoader.class) {
            if (_instance == null) _instance = new RaidTypesLoader();
            return _instance;
        }
    }

    public static synchronized void release() {
        _instance = null;
    }

    public static synchronized void reload() {
        _instance = new RaidTypesLoader();
    }

	private HashMap<Integer, RaidType> _raidTypes;
	private RaidTypesLoader(){
		load();
	}
	
	private void load(){
		Connection 			con		= null;
		PreparedStatement 	pstm 	= null;
		ResultSet 			rs 		= null;
		RaidType			type	= null;
		StringBuilder 		sbQry	= new StringBuilder(256);
		_raidTypes 					= new HashMap<Integer, RaidType>(4);
		
		try{
			sbQry.append("select * from ").append(RaidType.TB_NAME);
			con 	= DatabaseFactory.getInstance().getConnection();
			pstm 	= con.prepareStatement(sbQry.toString());
			rs		= pstm.executeQuery();
			while(rs.next()){
				type = new RaidType();
				if(type.setInformation(rs))
					_raidTypes.put(type.getId(), type);
			}
		}catch(Exception e){
			e.printStackTrace();
		}finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}
	

	
	public RaidType get(int i){
		return _raidTypes.get(i);
	}
}
