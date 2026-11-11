package l1j.server.DungeonTimer;

import java.sql.ResultSet;
import java.sql.SQLException;

import l1j.server.DungeonTimer.Progress.AccountTimeProgress;
import l1j.server.DungeonTimer.Progress.CharacterTimeProgress;
import l1j.server.DungeonTimer.Progress.DungeonTimeProgress;
import l1j.server.server.model.Instance.L1PcInstance;

public class DungeonTimeInformation{
	
	public static DungeonTimeInformation newInstance(ResultSet rs) throws SQLException{
		return newInstance()
			.set_timer_id(rs.getInt("timer_id"))
			.set_is_account_share(rs.getBoolean("is_account_share"))
			.set_is_presentation(rs.getBoolean("is_presentation"))
			.set_serial_id(rs.getInt("serial_id"))
			.set_description(rs.getString("description"))
			.set_amount_seconds(rs.getInt("amount_seconds"));
	}
	
	public static DungeonTimeInformation newInstance(){
		return new DungeonTimeInformation();
	}

	private int _timer_id;
	private boolean _is_account_share;
	private boolean _is_presentation;
	private int _serial_id;
	private String _description;
	private int _amount_seconds;
	private DungeonTimeInformation(){
	}

	public DungeonTimeInformation set_timer_id(int val){
		_timer_id = val;
		return this;
	}
	public int get_timer_id(){
		return _timer_id;
	}

	public DungeonTimeInformation set_is_account_share(boolean val){
		_is_account_share = val;
		return this;
	}
	public boolean get_is_account_share(){
		return _is_account_share;
	}

	public DungeonTimeInformation set_is_presentation(boolean val){
		_is_presentation = val;
		return this;
	}
	public boolean get_is_presentation(){
		return _is_presentation;
	}

	public DungeonTimeInformation set_serial_id(int val){
		_serial_id = val;
		return this;
	}
	
	public int get_serial_id(){
		return _serial_id;
	}
	
	public DungeonTimeInformation set_description(String val){
		_description = val;
		return this;
	}
	public String get_description(){
		return _description;
	}

	public DungeonTimeInformation set_amount_seconds(int val){
		_amount_seconds = val;
		return this;
	}
	public int get_amount_seconds(){
		return _amount_seconds;
	}
	
	public DungeonTimeProgress<?> to_progress(L1PcInstance pc){
		DungeonTimeProgress<?> progress = null;
		if (get_is_account_share()) {
			progress = AccountTimeProgress.newInstance().set_owner_info(pc.getAccountName());
		} else {
			progress = CharacterTimeProgress.newInstance().set_owner_info(pc.getId());
		}
		
		int remain = get_amount_seconds();
		int t_id = get_timer_id();
//		if(pc.getLevel() >= 90){
//			if(t_id == 1){
//				remain = 10800;
//			}else if(t_id == 7){
//				remain = 5400;
//			}
//		}
		
		// Æú(³¬½ÃÅÍ)
//		if(t_id == DungeonTimeInformationLoader.FISH_TIMER_ID) { 
//			if(pc.is_apply_tam()) {
//				remain = (RealTimeClock.DAYS_SECONDS);	
//			}
//		}
		return progress.set_timer_id(t_id).set_remain_seconds(remain);	
	}

}