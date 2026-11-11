package l1j.server.DungeonTimer;

import java.util.HashMap;

import l1j.server.DungeonTimer.Loader.DungeonTimeProgressLoader;
import l1j.server.DungeonTimer.Progress.AccountTimeProgress;
import l1j.server.DungeonTimer.Progress.CharacterTimeProgress;
import l1j.server.DungeonTimer.Progress.DungeonTimeProgress;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.gametime.RealTimeClock;
import l1j.server.server.serverpackets.S_ServerMessage;

public class DungeonTimeUserInformation {
	public static DungeonTimeUserInformation newInstance(){
		return new DungeonTimeUserInformation();
	}
	
	private HashMap<Integer, DungeonTimeProgress<?>> _character_progresses;
	private HashMap<Integer, DungeonTimeProgress<?>> _account_progresses;
	private DungeonTimeUserInformation(){
		_character_progresses = new HashMap<Integer, DungeonTimeProgress<?>>(8);
		_account_progresses = new HashMap<Integer, DungeonTimeProgress<?>>(8);
	}
	
	public void put_dungeon_progress(int timer_id, CharacterTimeProgress progress){
		_character_progresses.put(timer_id, progress);
	}
	
	public void put_dungeon_progress(int timer_id, AccountTimeProgress progress){
		_account_progresses.put(timer_id, progress);		
	}
	
	public DungeonTimeProgress<?> remove_dungeon_progress(int timer_id){
		DungeonTimeProgress<?> p = _character_progresses.remove(timer_id);
		if(p == null)
			p = _account_progresses.remove(timer_id);
		return p;
	}
	
	public HashMap<Integer, DungeonTimeProgress<?>> get_character_progresses(){
		return _character_progresses;
	}
	
	public HashMap<Integer, DungeonTimeProgress<?>> get_account_progresses(){
		return _account_progresses;
	}
	
	public DungeonTimeProgress<?> get_progress(DungeonTimeInformation dtInfo){
		if(dtInfo.get_is_account_share()){
			return _account_progresses.get(dtInfo.get_timer_id());
		}
		return _character_progresses.get(dtInfo.get_timer_id());
	}
	
	public DungeonTimeProgress<?> get_progress(int timer_id){
		DungeonTimeProgress<?> progress =  _account_progresses.get(timer_id);
		if(progress == null)
			progress =  _character_progresses.get(timer_id);
		return progress;
	}
	
	public void dec_dungeon_progress(L1PcInstance pc, DungeonTimeInformation dtInfo){
		int timer_id = dtInfo.get_timer_id();		
		HashMap<Integer, DungeonTimeProgress<?>> progresses = dtInfo.get_is_account_share() ? _account_progresses : _character_progresses;
		DungeonTimeProgress<?> progress = progresses.get(timer_id);
		if(progress == null){
			progress = dtInfo.to_progress(pc);
			progresses.put(timer_id, progress);
			DungeonTimeProgressLoader.update(progress);
		}
		
		if(progress.dec_remain_seconds() <= 0){
			pc.SimpleTeleport(33437, 32813, 4);
			pc.sendPackets(String.format("%s 던전 이용시간이 초과되었습니다.", dtInfo.get_description()));
		}
	}

	
	public void send_dungeon_progress(L1PcInstance pc, DungeonTimeInformation dtInfo){
		int timer_id = dtInfo.get_timer_id();
		HashMap<Integer, DungeonTimeProgress<?>> progresses = dtInfo.get_is_account_share() ? _account_progresses : _character_progresses;
		DungeonTimeProgress<?> progress = progresses.get(timer_id);
		if(progress == null){
			progress = dtInfo.to_progress(pc);
			progresses.put(timer_id, progress);
			DungeonTimeProgressLoader.update(progress);
		}

		int remain_seconds = progress.get_remain_seconds();
		int remain_hours = (remain_seconds / RealTimeClock.HOUR_SECONDS);
		remain_seconds -= (remain_hours * RealTimeClock.HOUR_SECONDS);
		int remain_minute = (remain_seconds / RealTimeClock.MINUTE_SECONDS);
		remain_seconds -= (remain_minute * RealTimeClock.MINUTE_SECONDS);
//
		if (remain_hours > 0) {
			pc.sendPackets(new S_ServerMessage(1525, String.valueOf(remain_hours), String.valueOf(remain_minute)));
		} else if (remain_minute > 0) {
			pc.sendPackets(new S_ServerMessage(1527, String.valueOf(remain_minute)));
		} else {
			if (remain_minute < 0)
				remain_seconds = 0;
			pc.sendPackets(new S_ServerMessage(1528, String.valueOf(remain_seconds)));
		}
		
//		if (pc.getMapId() == 54) {
//			if (GameTimeClock.getInstance().is_night())
//				pc.sendPackets(new S_PacketBox(S_PacketBox.GREEN_MESSAGE, "밤시간 동안 무작위 텔레포트를 할 수 없는 지역입니다."));
//		}
		
	}

	
	public void initialize(){
		_character_progresses.clear();
		_account_progresses.clear();
	}
}
