package l1j.server.server.model.Instance;

import l1j.server.RaidSystem.Dragon_Raid_Object;
import l1j.server.Templates.L1Npc;


public class L1RaidDoorInstance extends L1NpcInstance {
	
	private static final long serialVersionUID = 1L;
	
	private Dragon_Raid_Object _raidObj;
	
	public L1RaidDoorInstance(L1Npc template) {
		super(template);
	}

	public void setRaidObject(Dragon_Raid_Object obj) {
		_raidObj = obj;
	}
	
	public Dragon_Raid_Object getRaidObject() {
		return _raidObj;
	}
	
	@Override
	public void onTalkAction(L1PcInstance pc) {
		if(_raidObj == null) {
			return;
		}
		_raidObj.doorMove(pc);
	}

}
