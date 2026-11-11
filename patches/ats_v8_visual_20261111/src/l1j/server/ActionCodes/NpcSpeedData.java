package l1j.server.ActionCodes;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;

import l1j.server.Template.SqlHelper.Executors.Selector;
import l1j.server.Template.SqlHelper.Handler.FullSelectorHandler;
import l1j.server.server.model.Instance.L1NpcInstance;

public class NpcSpeedData {
	private static HashMap<Integer, NpcSpeedData> m_npc_speed_data;
	public static void do_load(){
		HashMap<Integer, NpcSpeedData> npc_speed_data = new HashMap<Integer, NpcSpeedData>();
		Selector.exec("select * from npc_speed_data", new FullSelectorHandler(){
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()){
					NpcSpeedData o = newInstance(rs);
					npc_speed_data.put(o.get_monster_id(), o);
				}
			}
		});
		m_npc_speed_data = npc_speed_data;
	}
	
	public static void install_npc(L1NpcInstance npc){
		NpcSpeedData data = m_npc_speed_data.get(npc.getNpcId());
		if(data == null)
			return;
		
		npc.setMoveSpeed(data.get_speed());
		npc.setBraveSpeed(data.get_brave());
	}
	
	private static NpcSpeedData newInstance(ResultSet rs) throws SQLException{
		return newInstance()
				.set_monster_id(rs.getInt("monster_id"))
				.set_speed(rs.getInt("speed"))
				.set_brave(rs.getInt("brave"));
	}

	private static NpcSpeedData newInstance(){
		return new NpcSpeedData();
	}

	private int m_monster_id;
	private int m_speed;
	private int m_brave;
	private NpcSpeedData(){}

	public NpcSpeedData set_monster_id(int monster_id){
		m_monster_id = monster_id;
		return this;
	}
	public NpcSpeedData set_speed(int speed){
		m_speed = speed;
		return this;
	}
	public NpcSpeedData set_brave(int brave){
		m_brave = brave;
		return this;
	}
	public int get_monster_id(){
		return m_monster_id;
	}
	public int get_speed(){
		return m_speed;
	}
	public int get_brave(){
		return m_brave;
	}

}

