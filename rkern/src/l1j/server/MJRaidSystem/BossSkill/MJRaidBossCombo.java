package l1j.server.MJRaidSystem.BossSkill;

import java.util.ArrayList;

import l1j.server.MJRaidSystem.Loader.MJRaidLoadManager;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_NpcChatPacket;

public class MJRaidBossCombo {
	private String 						_nameId;
	private ArrayList<MJRaidBossSkill> 	_combos;
	private int							_phase;
	public MJRaidBossCombo(){
		_combos = new ArrayList<MJRaidBossSkill>(2);
	}
	
	public void add(MJRaidBossSkill skill){
		_combos.add(skill);
	}
	
	public void setPhase(int i){
		_phase = i;
	}
	
	public int getPhase(){
		return _phase;
	}
	
	public void setNameId(String s){
		_nameId = s;
	}
	public String getNameId(){
		return _nameId;
	}
	
	public void action(L1NpcInstance own, ArrayList<L1PcInstance> pcs){
		if(_combos == null)
			return;
		
		int size = _combos.size();
		MJRaidBossSkill skill = null;
		own.setParalyzed(true);
		if(_nameId != null)
			SC_NpcChatPacket.broadcastNpcFast(own, own, _nameId, 0);
		try{
			for(int i=0; i<size; i++){
				skill = _combos.get(i);
				if(skill == null)
					continue;
				
				skill.actionNoDelay(own, pcs);
				Thread.sleep(MJRaidLoadManager.MRS_CB_DELAY);
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
