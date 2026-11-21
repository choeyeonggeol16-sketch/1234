package l1j.server.server.model.world.instance.dungeon.time.progress;

import java.sql.ResultSet;
import java.sql.SQLException;

public class AccountTimeProgress extends DungeonTimeProgress<String>{
	public static AccountTimeProgress newInstance(){
		return new AccountTimeProgress();
	}
	
	public static AccountTimeProgress newInstance(ResultSet rs) throws SQLException{
		return (AccountTimeProgress) newInstance()
				.set_owner_info(rs.getString("owner_info"))
				.set_timer_id(rs.getInt("timer_id"))
				.set_remain_seconds(rs.getInt("remain_seconds"));
	}
	
	protected AccountTimeProgress(){}
}
