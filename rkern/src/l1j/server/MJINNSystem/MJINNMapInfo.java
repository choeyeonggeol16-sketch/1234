package l1j.server.MJINNSystem;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

public class MJINNMapInfo {
	public static MJINNMapInfo create(ResultSet rs) throws SQLException{
		MJINNMapInfo mInfo = new MJINNMapInfo();
		mInfo.innId 	= rs.getInt("inn_id");
		mInfo.town		= rs.getString("desc");
		mInfo.isHall	= rs.getString("type").equalsIgnoreCase("hall");
		mInfo.mapId	= rs.getShort("mapid");
		mInfo.x		= rs.getInt("x");
		mInfo.y		= rs.getInt("y");
		return mInfo;
	}
	
	public int 				innId;
	public String			town;
	public boolean 			isHall;
	public short 			mapId;
	public int				x;
	public int				y;
	public AtomicInteger 	usageCount;
	private MJINNMapInfo(){
		usageCount = new AtomicInteger(0);
	}
	
	@Override
	public String toString(){
		StringBuilder sb = new StringBuilder(64);
		sb.append("InnId : ").append(innId).append("\r\n");
		sb.append("Town : ").append(town).append("\r\n");
		sb.append("IsHall : ").append(isHall).append("\r\n");
		sb.append("MapId : ").append(mapId).append("\r\n");
		sb.append("X : ").append(x).append("\r\n");
		sb.append("Y : ").append(y);
		return sb.toString();
	}
}
