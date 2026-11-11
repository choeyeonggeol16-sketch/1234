package l1j.server.RaidSystem;

import java.util.ArrayList;

import l1j.server.RaidSystem.Loader.Dragon_Raid_Load_Manager;
import l1j.server.server.model.Broadcaster;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_NpcChatPacket;

public class Dragon_Raid_Skill_Combo {
	
	private String 						_nameId;
	private ArrayList<Dragon_Raid_Skill> 	_combos;
	private int							_phase;
	public Dragon_Raid_Skill_Combo(){
		_combos = new ArrayList<Dragon_Raid_Skill>(2);
	}
	public void add(Dragon_Raid_Skill skill) {
		_combos.add(skill);
	}
	
	public void setPhase(int i){
		_phase = i;
	}
	
	public int getPhase(){
		return _phase;
	}
	
	public void setNameId(String s) {
		_nameId = s;
	}
	public String getNameId(){
		return _nameId;
	}
	
	public void action(L1NpcInstance own, ArrayList<L1PcInstance> pcs) {
		if(_combos == null)
			return;
		
		int size = _combos.size();
		Dragon_Raid_Skill skill = null;
		own.setParalyzed(true);
		if(_nameId != null)
			Broadcaster.broadcastPacket(own, new S_NpcChatPacket(own, _nameId, 0), true);
		try{
			for(int i=0; i<size; i++) {
				skill = _combos.get(i);
				if(skill == null)
					continue;
				
				skill.actionNoDelay(own, pcs);
				Thread.sleep(Dragon_Raid_Load_Manager.MRS_CB_DELAY);
			}
		}catch(Exception e){
			e.printStackTrace();
		}
		own.setParalyzed(false);
	}
	
	@Override
	public String toString(){
		StringBuilder sb = new StringBuilder(64);
		sb.append("name id : ").append(_nameId).append("\n");
		sb.append("combos size : ").append(_combos.size()).append("\n");
		sb.append("phase : ").append(_phase).append("\n");
		return sb.toString();
	}

}
