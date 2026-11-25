package l1j.server.server.model.character.action.listener;

import java.sql.ResultSet;
import java.sql.SQLException;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.action.listener.npc.TeleporterActionListener;

public class ActionListenerLinkage{
	public static ActionListenerLinkage newInstance(ResultSet rs) throws SQLException{
		return newInstance()
				.set_npc_id(rs.getInt("npc_id"))
				.set_action(rs.getString("action_name"))
				.set_item_id(rs.getInt("item_id"));
	}
	
	public static ActionListenerLinkage newInstance(){
		return new ActionListenerLinkage();
	}

	private int _npc_id;
	private String _action;
	private int _item_id;
	private ActionListenerLinkage(){
	}

	public ActionListenerLinkage set_npc_id(int val){
		_npc_id = val;
		return this;
	}
	public int get_npc_id(){
		return _npc_id;
	}

	public ActionListenerLinkage set_action(String val){
		_action = val;
		return this;
	}
	public String get_action(){
		return _action;
	}

	public ActionListenerLinkage set_item_id(int val){
		_item_id = val;
		return this;
	}
	public int get_item_id(){
		return _item_id;
	}
	
	public void use_item(L1PcInstance pc, L1ItemInstance item){
		ActionListener listener = ActionListenerLoader.getInstance().findListener(get_npc_id(), get_action());
		if(listener == null){
			pc.sendPackets("현재 사용할 수 없는 상태입니다.");
			return;
		}
		
		if(listener instanceof TeleporterActionListener){
			TeleporterActionListener t_listener = (TeleporterActionListener)listener;
			if(!t_listener.is_opened()){
				t_listener.result_un_opened(pc);
				return;
			}
			if(pc.getLevel() < t_listener.get_need_level()){
				t_listener.result_short_level(pc);
				return;
			}
			int need_buff = t_listener.get_need_buff();
			if(need_buff > 0 && !pc.hasSkillEffect(need_buff)){
				t_listener.result_no_buff(pc);
				return;
			}
			if(t_listener.is_need_pc_buff() && !pc.PC방_버프){
				t_listener.result_no_pc_buff(pc);
				return;
			}
			
			if(!pc.getMap().isEscapable()){
				pc.sendPackets("해당 맵에서는 사용할 수 없습니다.");
				return;
			}
			
			if(t_listener.simple_teleport(pc)){
				pc.getInventory().DeleteItemAmount(item, 1);				
			}
			return;
		}
		pc.sendPackets("현재 사용할 수 없는 상태입니다.");
	}

}