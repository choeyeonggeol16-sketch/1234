package l1j.server.server.model.world.instance.inn.loader;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import l1j.server.DatabaseFactory;
import l1j.server.server.model.world.instance.inn.InnMapInfo;
import l1j.server.server.utils.SQLUtil;

public class InnMapInfoLoader {
	public static InnMapInfo[] getMapInfo(int innId){
		InnMapInfo[] 		infos 	= new InnMapInfo[2];
		Connection 			con		= null;
		PreparedStatement 	pstm 	= null;
		ResultSet 			rs 		= null;
		try{
			con			= DatabaseFactory.getInstance().getConnection();
			pstm		= con.prepareStatement("select * from tb_inn_mapInfo where inn_id=?");
			pstm.setInt(1, innId);
			rs			= pstm.executeQuery();
			while(rs.next()){
				InnMapInfo mInfo = InnMapInfo.create(rs);
				infos[parseHall(mInfo.isHall)] = mInfo;
			}
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			SQLUtil.close(rs, pstm, con);
		}
		return infos;
	}
		
	private static int parseHall(boolean isHall){
		return isHall ? 1 : 0;
	}

	public static int isInInnArea(int x, int y, int mapId){
		switch(mapId){
		case 4:
			if(x == 33986 && y == 33312)
				return 7;
			else if(x == 33437 && y == 32787)
				return 4;
			else if(x == 34067 && y == 32254)
				return 6;
			else if(x == 32632 && y == 32761)
				return 1;
			else if(x == 33604 && y == 33275)
				return 5;
			else if(x == 33112 && y == 33376)
				return 3;
			else if(x == 32631 && y == 33165)
				return 2;
			break;
		case 0:
			if(x == 32600 && y == 32930)
				return 0;
			break;
		case 440:
			if(x == 32451 && y == 33046)
				return 8;
			break;
		}
		
		return -1;
	}
	
	public static boolean isInnArea(int x, int y, int mapId){
		return (x == 32600 && y == 32930 && mapId == 0)
		|| (x == 33986 && y == 33312 && mapId == 4)
		|| (x == 33437 && y == 32789 && mapId == 4)
		|| (x == 34066 && y == 32254 && mapId == 4)
		|| (x == 32632 && y == 32761 && mapId == 4)
		|| (x == 33605 && y == 33275 && mapId == 4)
		|| (x == 33116 && y == 33379 && mapId == 4)
		|| (x == 32628 && y == 33167 && mapId == 4)
		|| (x == 32451 && y == 33046 && mapId == 440);
	}
}
