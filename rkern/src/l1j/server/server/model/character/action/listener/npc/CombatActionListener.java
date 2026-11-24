package l1j.server.server.model.character.action.listener.npc;

import l1j.server.server.datatables.CObjectDB;
import java.sql.ResultSet;
import java.sql.SQLException;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.action.listener.ActionListener;
import l1j.server.server.model.character.combat.arena.CombatEGameType;
import l1j.server.server.model.character.combat.arena.CombatObserver;
import l1j.server.server.templates.L1Item;

public class CombatActionListener extends NpcActionListener{
	public static CombatActionListener newInstance(NpcActionListener listener, ResultSet rs) throws SQLException{
		return newInstance(listener).set_g_type(CombatEGameType.from_kr_desc(rs.getString("combat_type")));
	}
	
	public static CombatActionListener newInstance(NpcActionListener listener){
		return (CombatActionListener) newInstance().drain(listener);
	}
	
	public static CombatActionListener newInstance(){
		return new CombatActionListener();
	}
	
	private CombatObserver _combat_observer;
	private CombatEGameType _g_type;
	private CombatActionListener(){}
	
	@Override
	public ActionListener deep_copy(){
		return deep_copy(newInstance());
	}
	
	@Override
	public ActionListener drain(ActionListener listener){
		if(listener instanceof CombatActionListener){
			CombatActionListener c_listener = (CombatActionListener) listener;
			set_combat_observer(c_listener.get_combat_observer());
			set_g_type(c_listener.get_g_type());
		}
		return super.drain(listener);
	}
	
	@Override
	public void dispose(){
		super.dispose();
	}
	
	@Override
	public String result_un_opened(L1PcInstance pc){
		pc.sendPackets("지금은 게임 시간이 아닙니다.");
		return super.result_un_opened(pc);
	}
	
	public String result_already(L1PcInstance pc){
		pc.sendPackets("이미 게임이 시작되었습니다.");
		return super.result_un_opened(pc);
	}
	
	@Override
	public String result_short_level(L1PcInstance pc){
		pc.sendPackets(String.format("%d레벨 부터 입장이 가능합니다.", get_need_level())); 
		return super.result_short_level(pc);
	}
	
	@Override
	public String result_no_buff(L1PcInstance pc){
		pc.sendPackets("입장 조건을 충족하지 않습니다.");
		return super.result_no_buff(pc);
	}
	
	@Override
	public String result_no_pc_buff(L1PcInstance pc){
		pc.sendPackets("PC방 이용권을 사용중에만 사용 가능한 행동입니다.");
		return super.result_no_pc_buff(pc);
	}
	
	@Override
	public String result_short_item(L1PcInstance pc){
		L1Item item = CObjectDB.GetItem(get_need_item_id());
		pc.sendPackets(String.format("%s %d이(가) 부족합니다.", item.getName(), get_need_item_amount()));
		return super.result_short_item(pc);
	}
	
	@Override
	public String result_success(L1PcInstance pc){
		if(_combat_observer == null || _combat_observer.is_closed() || _g_type.equals(CombatEGameType.NONE))
			return result_un_opened(pc);
		if(_combat_observer.is_running() || _combat_observer.is_disposing())
			return result_already(pc);
		
		_combat_observer.enter(pc);
		return super.result_success(pc);
	}
	
	public CombatActionListener set_combat_observer(CombatObserver observer){
		_combat_observer = observer;
		return this;
	}
	
	public CombatObserver get_combat_observer(){
		return _combat_observer;
	}
	
	public CombatActionListener set_g_type(CombatEGameType g_type){
		_g_type = g_type;
		return this;
	}
	
	public CombatEGameType get_g_type(){
		return _g_type;
	}
}
