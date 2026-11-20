package l1j.server.MJTemplate.DateSchedulerModel.Acceptor;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Calendar;

import l1j.server.MJActionListener.ActionListener;
import l1j.server.MJActionListener.Npc.CombatActionListener;
import l1j.server.MJActionListener.Npc.NpcActionListener;
import l1j.server.MJCombatSystem.MJCombatEGameType;
import l1j.server.MJCombatSystem.MJCombatObserver;
import l1j.server.MJCombatSystem.Loader.MJCombatLoadManager;
import l1j.server.MJTemplate.Interface.MJSimpleListener;
import l1j.server.MJTemplate.MJArrangeHelper.MJArrangeParseeFactory;
import l1j.server.MJTemplate.MJArrangeHelper.MJArrangeParser;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.gametime.RealTimeClock;
import l1j.server.server.model.network.packet.CServerPacket;
import l1j.server.server.model.network.packet.IPacketWriter;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

public class AcceptorToCombatListenerAdapter extends AbstractDateAcceptor implements ActionListener{
	private static Integer[] parse_opened_date(String s){
		return (Integer[])MJArrangeParser.parsing(s, ",", MJArrangeParseeFactory.createIntArrange()).result();
	}
	
	public static AcceptorToCombatListenerAdapter newInstance(NpcActionListener listener, ResultSet rs) throws SQLException{
		return (AcceptorToCombatListenerAdapter) newInstance()
				.set_opened_message(rs.getString("opened_message"))
				.set_closed_message(rs.getString("closed_message"))
				.setActionListener(listener)
				.set_is_opened(false)
				.add_include_day_week(parse_opened_date(rs.getString("opened_date")))
				.set_opened_time(rs.getInt("opened_hour"), rs.getInt("opened_minute"), rs.getInt("opened_second"))
				.set_remain_time(rs.getInt("remain_time"))
				.set_remain_type(AcceptorToNpcListenerAdapter.parse_remian_type(rs.getString("remain_type")))
				.do_register();
	}
	
	public static AcceptorToCombatListenerAdapter newInstance(){
		return new AcceptorToCombatListenerAdapter();
	}
	
	private CombatActionListener _listener;
	private String _opened_message;
	private String _closed_message;
	private AcceptorToCombatListenerAdapter(){}
	
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
	
	public AcceptorToCombatListenerAdapter deep_copy(ResultSet rs) throws SQLException{
		return (AcceptorToCombatListenerAdapter) newInstance()
				.set_opened_message(rs.getString("opened_message"))
				.set_closed_message(rs.getString("closed_message"))
				.setActionListener((NpcActionListener) _listener.deep_copy())
				.set_is_opened(false)
				.add_include_day_week(parse_opened_date(rs.getString("opened_date")))
				.set_opened_time(rs.getInt("opened_hour"), rs.getInt("opened_minute"), rs.getInt("opened_second"))
				.set_remain_time(rs.getInt("remain_time"))
				.set_remain_type(AcceptorToNpcListenerAdapter.parse_remian_type(rs.getString("remain_type")))
				.do_register();
	}
	
	public AcceptorToCombatListenerAdapter set_opened_message(String opened_message){
		_opened_message = opened_message;
		return this;
	}
	
	public String get_opened_message(){
		return _opened_message;
	}
	
	protected AcceptorToCombatListenerAdapter setActionListener(NpcActionListener listener){
		_listener = (CombatActionListener) listener;
		return this;
	}
	
	public AcceptorToCombatListenerAdapter set_closed_message(String closed_message){
		_closed_message = closed_message;
		return this;
	}
	
	public String get_closed_message(){
		return _closed_message;
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
	public boolean is_action() {
		return _listener.is_action();
	}

	@Override
	public boolean is_opened(){
		return _listener != null && _listener.is_opened();
	}
	
	@Override
	public boolean is_closed(){
		return _listener == null || !_listener.is_opened();
	}
	
	@Override
	public AbstractDateAcceptor set_is_opened(boolean is_opened){
		_listener.set_opened(is_opened);
		return this;
	}
	
	@Override
	public AbstractDateAcceptor do_open(Calendar cal) {
		if(_listener.get_g_type().equals(MJCombatEGameType.NONE))
			return this;
		if(_opened_message != null){
			SC_PacketBox.broadcastGreen(_opened_message);
					SC_SystemMessage.broadcast(_opened_message);
			
		}
		
		try{
			MJCombatObserver observer = MJCombatLoadManager.getInstance().execute_observer(_listener.get_g_type());
			Calendar closed_cal = RealTimeClock.getInstance().getRealTimeCalendar();
			closed_cal.add(Calendar.SECOND, observer.to_total_remain_seconds());
			set_closed_cal(closed_cal);
			observer.add_quit_listener(new MJSimpleListener(){
				@Override
				public Object on(Object obj){
					if(is_opened()){
						set_is_opened(false);
						_listener.set_combat_observer(null);
						do_close(null);
					}
					return AcceptorToCombatListenerAdapter.this;
				}
			});
			_listener.set_combat_observer(observer);
		}catch(Exception e){
			e.printStackTrace();
		}
		return this;
	}

	@Override
	public AbstractDateAcceptor do_close(Calendar cal) {
		if(_closed_message != null)
			SC_PacketBox.broadcastGreen(_closed_message);
		return this;
	}
	
	@Override
	public void dispose() {
		do_removed();
		if(is_opened())
			set_is_opened(false).do_close(null);
		set_opened_time(null).set_closed_cal(null);
		
		if(_listener != null){
			_listener.dispose();
			_listener = null;
		}
	}

}
