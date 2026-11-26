package l1j.server.server.task.calendar.acceptor;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Calendar;

import l1j.server.Config;
import l1j.server.server.task.TaskLane;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.character.action.listener.ActionListener;
import l1j.server.server.model.character.action.listener.npc.NpcActionListener;
import l1j.server.server.model.character.action.listener.npc.TeleporterActionListener;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.shared.arrangement.ArrangeParseeFactory;
import l1j.server.server.shared.arrangement.ArrangeParser;

public class AcceptorToNpcListenerAdapter extends AbstractDateAcceptor implements ActionListener{
	public static int parse_remian_type(String s){
		switch(s){
		case "시":
			return Calendar.HOUR_OF_DAY;
		case "분":
			return Calendar.MINUTE;
		case "초":
			return Calendar.SECOND;
		default:
			throw new IllegalArgumentException(String.format("invalid remain type %s", s));
		}
	}
	
	private static Integer[] parse_opened_date(String s){
		return (Integer[])ArrangeParser.parsing(s, ",", ArrangeParseeFactory.createIntArrange()).result();
	}
	
	public static AcceptorToNpcListenerAdapter newInstance(NpcActionListener listener, ResultSet rs) throws SQLException{
		return (AcceptorToNpcListenerAdapter) newInstance()
				.set_opened_message(rs.getString("opened_message"))
				.set_closed_message(rs.getString("closed_message"))
				.setActionListener(listener)
				.set_is_opened(false)
				.add_include_day_week(parse_opened_date(rs.getString("opened_date")))
				.set_opened_time(rs.getInt("opened_hour"), rs.getInt("opened_minute"), rs.getInt("opened_second"))
				.set_remain_time(rs.getInt("remain_time"))
				.set_remain_type(parse_remian_type(rs.getString("remain_type")))
				.do_register();
	}
	
	public static AcceptorToNpcListenerAdapter newInstance(){
		return new AcceptorToNpcListenerAdapter();
	}
	
	private NpcActionListener _listener;
	private String	_opened_message;
	private String _closed_message;
	private AcceptorToNpcListenerAdapter(){
		
	}
	
	public AcceptorToNpcListenerAdapter set_opened_message(String opened_message){
		_opened_message = opened_message;
		return this;
	}
	
	public String get_opened_message(){
		return _opened_message;
	}
	
	public AcceptorToNpcListenerAdapter set_closed_message(String closed_message){
		_closed_message = closed_message;
		return this;
	}
	
	public String get_closed_message(){
		return _closed_message;
	}
	
	@Override
	public ActionListener deep_copy(ActionListener listener){
		return ((NpcActionListener) _listener.deep_copy(listener)).set_opened(false);
	}
	
	@Override
	public ActionListener deep_copy(){
		return ((NpcActionListener) _listener.deep_copy()).set_opened(false);
	}
	
	@Override
	public ActionListener drain(ActionListener listener) {
		return _listener.drain(listener);
	}
	
	public AcceptorToNpcListenerAdapter deep_copy(ResultSet rs) throws SQLException{
		return (AcceptorToNpcListenerAdapter) newInstance()
				.set_opened_message(rs.getString("opened_message"))
				.set_closed_message(rs.getString("closed_message"))
				.setActionListener((NpcActionListener) _listener.deep_copy())
				.set_is_opened(false)
				.add_include_day_week(parse_opened_date(rs.getString("opened_date")))
				.set_opened_time(rs.getInt("opened_hour"), rs.getInt("opened_minute"), rs.getInt("opened_second"))
				.set_remain_time(rs.getInt("remain_time"))
				.set_remain_type(parse_remian_type(rs.getString("remain_type")))
				.do_register();
	}
	
	protected AcceptorToNpcListenerAdapter setActionListener(NpcActionListener listener){
		_listener = listener;
		return this;
	}
	
	@Override
	public boolean eqauls_action(String action) {
		return _listener.eqauls_action(action);
	}

	@Override
	public String to_action(L1PcInstance pc, L1Object target) {
		return _listener.to_action(pc, target);
	}

	@Override
	public AbstractDateAcceptor do_open(Calendar cal) {
		if(_opened_message != null)
			l1j.server.server.model.network.core.sc.packet.SC_SystemMessage.broadcast(_opened_message);
		SC_PacketBox.broadcastGreen(_opened_message);
		if(_listener.get_npc_id() == 70086){Config.잊섬오픈 = 1;}
		if(_listener.get_npc_id() == 77103){Config.거무오픈 = 1;}
		if(_listener.get_npc_id() == 77108){Config.라스타오픈 = 1;}
		if(_listener.get_npc_id() == 7320148){Config.지배오픈 = 1;}
		if(_listener.get_npc_id() == 7320177){Config.풍둥오픈 = 1;}
		if(_listener.get_npc_id() == 7320179){Config.오설오픈 = 1;}
		if(_listener.get_npc_id() == 7320183){Config.화둥오픈 = 1;}
		if(_listener.get_npc_id() == 7320190){Config.용계오픈 = 1;}
		if(_listener.get_npc_id() == 50000020){Config.테베오픈 = 1;}
	
		return this;
	}

	@Override
	public AbstractDateAcceptor do_close(Calendar cal) {
		if(_listener instanceof TeleporterActionListener){
			//SC_SystemMessage message = new SC_SystemMessage(_closed_message == null ? String.format("%s이(가) 종료되었습니다.", ((TeleporterActionListener) _listener).get_destination_name()) : _closed_message);
			final String message = _closed_message == null ? String.format("%s이(가) 종료되었습니다.", ((TeleporterActionListener) _listener).get_destination_name()) : _closed_message;
			TeleporterActionListener listener = (TeleporterActionListener)_listener;
			short[] mapIds = listener.get_teleport_map_id();
			if(_listener.get_npc_id() == 70086){Config.잊섬오픈 = 0;}
			if(_listener.get_npc_id() == 77103){Config.거무오픈 = 0;}
			if(_listener.get_npc_id() == 77108){Config.라스타오픈 = 0;}
			if(_listener.get_npc_id() == 7320148){Config.지배오픈 = 0;}
			if(_listener.get_npc_id() == 7320177){Config.풍둥오픈 = 0;}
			if(_listener.get_npc_id() == 7320179){Config.오설오픈 = 0;}
			if(_listener.get_npc_id() == 7320183){Config.화둥오픈 = 0;}
			if(_listener.get_npc_id() == 7320190){Config.용계오픈 = 0;}
			if(_listener.get_npc_id() == 50000020){Config.테베오픈 = 0;}
			
			TaskLane.WORLD.execute(new Runnable(){
				@Override
				public void run(){
					try(var shared=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
      SC_PacketBox.nativeGreenWriter(message),Integer.MIN_VALUE,Integer.MAX_VALUE)) {
for(int i=0; i<2; ++i){
						for(L1PcInstance pc : L1World.getInstance().getAllPlayers()){
							if(pc == null)
								continue;
							
							if(i == 0)
								l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(shared,pc);
							
							boolean is_in = false;
							int mid = pc.getMapId();
							for(int mapId : mapIds){
								if(mid == mapId){
									is_in=true;
									break;
								}
							}
							if(is_in){
								//getback 베이스 마을로 귀환
								if(i == 0){
									pc.start_teleport(33432, 32806, 4, pc.getHeading(), 169, true, false);
									SC_SkillSound.send(pc, pc.getId(), 491);//
								}else{
									pc.do_simple_teleport(33436, 32812, 4);
									SC_SkillSound.send(pc, pc.getId(), 491);//
								}
							}
						}
					}
}
				//	message.clear();
					
				}
			});
		}
		return this;
	}
	
	@Override
	public boolean is_opened(){
		return _listener.is_opened();
	}
	
	@Override
	public boolean is_closed(){
		return !_listener.is_opened();
	}
	
	@Override
	public AbstractDateAcceptor set_is_opened(boolean is_opened){
		_listener.set_opened(is_opened);
		return this;
	}
	
	@Override
	public void dispose(){
		
		do_removed();
		if(is_opened())
			set_is_opened(false).do_close(null);
		set_opened_time(null).set_closed_cal(null);
		
		if(_listener != null){
			_listener.dispose();
			_listener = null;
		}
	}

	@Override
	public boolean is_action() {
		return _listener.is_action();
	}
}
