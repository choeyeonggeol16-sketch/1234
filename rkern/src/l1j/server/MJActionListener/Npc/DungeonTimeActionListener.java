package l1j.server.MJActionListener.Npc;

import java.sql.ResultSet;
import java.sql.SQLException;

import l1j.server.MJActionListener.ActionListener;
import l1j.server.MJDungeonTimer.DungeonTimeInformation;
import l1j.server.MJDungeonTimer.Loader.DungeonTimeInformationLoader;
import l1j.server.MJDungeonTimer.Progress.DungeonTimeProgress;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

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
	public String result_success(L1PcInstance pc){
		DungeonTimeInformation dtInfo = DungeonTimeInformationLoader.getInstance().from_timer_id(get_timer_id());
		if(dtInfo == null){
			return result_un_opened(pc);
		}
		
		DungeonTimeProgress<?> progress = pc.get_progress(dtInfo);
		if(progress == null || progress.get_remain_seconds() > 0){
			return super.result_success(pc);
		}
		
		SC_ServerMessage.send(pc, 3338);
//		pc.sendPackets("던전 이용 시간이 부족합니다.");
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
			SC_ServerMessage.send(pc, 3338);
//			pc.sendPackets("던전 이용 시간이 부족합니다.");
		}
		
		return false;
	}
}
