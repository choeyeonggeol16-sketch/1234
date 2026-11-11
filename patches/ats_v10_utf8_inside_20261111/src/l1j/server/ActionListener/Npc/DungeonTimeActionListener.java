package l1j.server.ActionListener.Npc;

import java.sql.ResultSet;
import java.sql.SQLException;

import l1j.server.ActionListener.ActionListener;
import l1j.server.DungeonTimer.DungeonTimeInformation;
import l1j.server.DungeonTimer.Loader.DungeonTimeInformationLoader;
import l1j.server.DungeonTimer.Progress.DungeonTimeProgress;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_ServerMessage;

public class DungeonTimeActionListener extends TeleporterActionListener{
	public static DungeonTimeActionListener newInstance(NpcActionListener listener, ResultSet rs) throws SQLException{
		return newInstance(listener)
				.set_timer_id(rs.getInt("timer_id"));
	}
	
	public static DungeonTimeActionListener newInstance(NpcActionListener listener){
		return (DungeonTimeActionListener) newInstance().drain(listener);
	}
	
	public static DungeonTimeActionListener newInstance(){
		return new DungeonTimeActionListener();
	}
	
	private int _timer_id;
	protected DungeonTimeActionListener(){}
	
	@Override
	public ActionListener deep_copy(){
		return deep_copy(newInstance());
	}
	
	@Override
	public ActionListener drain(ActionListener listener){
		if(listener instanceof DungeonTimeActionListener){
			DungeonTimeActionListener d_listener = (DungeonTimeActionListener) listener;
			set_timer_id(d_listener.get_timer_id());
		}
		return super.drain(listener);
	}
	
	public DungeonTimeActionListener set_timer_id(int val){
		_timer_id = val;
		return this;
	}
	public int get_timer_id(){
		return _timer_id;
	}
	
	@Override

	public String result_success(L1PcInstance pc) {
		DungeonTimeInformation dtInfo = DungeonTimeInformationLoader.getInstance().from_timer_id(get_timer_id());

		if (dtInfo == null) {
			return result_un_opened(pc);
		}
		DungeonTimeProgress<?> progress = pc.get_progress(dtInfo);
		if ((progress == null) || (progress.get_remain_seconds() > 0)) {
			return super.result_success(pc);
		}
		//주어진 시간을 전부 사용해 입장하실 수 없습니다.
		pc.sendPackets(new S_ServerMessage(3338));
		
		return "";
	}
	
	@Override
	public boolean simple_teleport(L1PcInstance pc){
		DungeonTimeInformation dtInfo = DungeonTimeInformationLoader.getInstance().from_timer_id(get_timer_id());
		if(dtInfo == null){
			return false;
		}
		
		DungeonTimeProgress<?> progress = pc.get_progress(dtInfo);
		if(progress == null || progress.get_remain_seconds() > 0){
			return super.simple_teleport(pc);
		}else{
			//주어진 시간을 전부 사용해 입장하실 수 없습니다.
			pc.sendPackets(new S_ServerMessage(3338));
		}
		
		return false;
	}
}
