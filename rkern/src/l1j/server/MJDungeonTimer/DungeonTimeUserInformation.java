package l1j.server.MJDungeonTimer;

import java.util.HashMap;

import l1j.server.MJDungeonTimer.Loader.DungeonTimeProgressLoader;
import l1j.server.MJDungeonTimer.Progress.AccountTimeProgress;
import l1j.server.MJDungeonTimer.Progress.CharacterTimeProgress;
import l1j.server.MJDungeonTimer.Progress.DungeonTimeProgress;
import l1j.server.MJTemplate.ObServer.MJCopyMapObservable;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;

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
		if(_character_progresses == null) return;
		_character_progresses.put(timer_id, progress);
	}
	
	public void put_dungeon_progress(int timer_id, AccountTimeProgress progress){
		if(_account_progresses == null) return;
		_account_progresses.put(timer_id, progress);		
	}
	
	public DungeonTimeProgress<?> remove_dungeon_progress(int timer_id){
		DungeonTimeProgress<?> p = null;
		if(_character_progresses != null) {
			p = _character_progresses.remove(timer_id);
		}
		if(p == null && _account_progresses != null) {
			p = _account_progresses.remove(timer_id);
		}
		return p;
	}
	
	public HashMap<Integer, DungeonTimeProgress<?>> get_character_progresses(){
		return _character_progresses;
	}
	
	public HashMap<Integer, DungeonTimeProgress<?>> get_account_progresses(){
		return _account_progresses;
	}
	
	public DungeonTimeProgress<?> get_progress(DungeonTimeInformation dtInfo){
		if(dtInfo == null || _account_progresses == null || _character_progresses == null)
			return null;
			
		if(dtInfo.get_is_account_share()){
			return _account_progresses.get(dtInfo.get_timer_id());
		}
		return _character_progresses.get(dtInfo.get_timer_id());
	}
	
	public DungeonTimeProgress<?> get_progress(int timer_id){
		DungeonTimeProgress<?> progress = null;
		if(_account_progresses != null) {
			progress = _account_progresses.get(timer_id);
		}
		if(progress == null && _character_progresses != null) {
			progress = _character_progresses.get(timer_id);
		}
		return progress;
	}
	
	public void dec_dungeon_progress(L1PcInstance pc, DungeonTimeInformation dtInfo){
		// 널 검사 추가: 유저 정보나 던전 정보가 없으면 예외 없이 리턴
		if(pc == null || dtInfo == null || _account_progresses == null || _character_progresses == null)
			return;

		int timer_id = dtInfo.get_timer_id();
		HashMap<Integer, DungeonTimeProgress<?>> progresses = dtInfo.get_is_account_share() ? _account_progresses : _character_progresses;
		if(progresses == null)
			return;

		DungeonTimeProgress<?> progress = progresses.get(timer_id);
		if(progress == null){
			progress = dtInfo.to_progress(pc);
			if(progress == null)
				return;
				
			progresses.put(timer_id, progress);
			DungeonTimeProgressLoader.update(progress);
		}
		
		int remain = progress.dec_remain_seconds();
		if(remain <= 0){
			DungeonTimeProgressLoader.update(progress);
			pc.do_simple_teleport(MJCopyMapObservable.RESET_X, MJCopyMapObservable.RESET_Y, MJCopyMapObservable.RESET_MAPID);
			pc.sendPackets(String.format("%s 사용 시간이 만료되었습니다.", dtInfo.get_description()));
		}else if(remain % 60 == 0){
			DungeonTimeProgressLoader.update(progress);
		}
	}
	
	public void send_dungeon_progress(L1PcInstance pc, DungeonTimeInformation dtInfo){
		// 널 검사 추가
		if(pc == null || dtInfo == null || _account_progresses == null || _character_progresses == null)
			return;

		int timer_id = dtInfo.get_timer_id();
		HashMap<Integer, DungeonTimeProgress<?>> progresses = dtInfo.get_is_account_share() ? _account_progresses : _character_progresses;
		if(progresses == null)
			return;

		DungeonTimeProgress<?> progress = progresses.get(timer_id);
		if(progress == null){
			progress = dtInfo.to_progress(pc);
			if(progress == null)
				return;
				
			progresses.put(timer_id, progress);
			DungeonTimeProgressLoader.update(progress);
		}
	//	SC_EVENT_COUNTDOWN_NOTI_PACKET.send(pc, progress.get_remain_seconds(), dtInfo.get_description());
		SC_PacketBox.send(pc, SC_PacketBox.MAP_TIMER, progress.get_remain_seconds());
	}
	
	public void initialize(){
		if(_character_progresses != null) _character_progresses.clear();
		if(_account_progresses != null) _account_progresses.clear();
	}
}