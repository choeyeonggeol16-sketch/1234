package l1j.server.server.model.Instance;

import l1j.server.server.model.world.instance.raid.RaidObject;
import l1j.server.server.templates.L1Npc;

public class L1RaidDoorInstance extends L1NpcInstance{
	private RaidObject _raidObj;
	
	public L1RaidDoorInstance(L1Npc template) {
		super(template);
	}

	public void setRaidObject(RaidObject obj){
		_raidObj = obj;
	}
	
	public RaidObject getRaidObject(){
		return _raidObj;
	}
	
	@Override
	public void onTalkAction(L1PcInstance pc){
		if(_raidObj == null)
			return;
		
		_raidObj.doorMove(pc);
	}
}
