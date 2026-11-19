package l1j.server.MJDungeonTimer.Progress;

import java.sql.ResultSet;
import java.sql.SQLException;

public class CharacterTimeProgress extends DungeonTimeProgress<Integer>{
	public static CharacterTimeProgress newInstance(){
		return new CharacterTimeProgress();
	}
	
	public static CharacterTimeProgress newInstance(ResultSet rs) throws SQLException{
		return (CharacterTimeProgress) newInstance()
				.set_owner_info(rs.getInt("owner_info"))
				.set_timer_id(rs.getInt("timer_id"))
				.set_remain_seconds(rs.getInt("remain_seconds"));
	}
	
	protected CharacterTimeProgress(){}
}
