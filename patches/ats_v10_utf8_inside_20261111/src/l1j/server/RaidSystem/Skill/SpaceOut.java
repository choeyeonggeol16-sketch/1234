package l1j.server.RaidSystem.Skill;

import java.util.ArrayList;

import l1j.server.RaidSystem.Dragon_Raid_Skill;
import l1j.server.server.model.Broadcaster;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_DoActionGFX;

public class SpaceOut extends Dragon_Raid_Skill {
	@Override
	public String toString() {
		return SpaceOut.class.getName();
	}


	@Override
	public void action(L1NpcInstance own, ArrayList<L1PcInstance> pcs) {
		if(own == null || pcs == null || pcs.size() <= 0 || own.isParalyzed()) {
			return;
		}
		try {
			synchronized(own.synchObject) {
				own.setParalyzed(true);
				if(_actid >= 0) {
					Broadcaster.broadcastPacket(own, S_DoActionGFX.get(own.getId(), _actid));
				}
				Thread.sleep(_rnd.nextInt(_dmgMax - _dmgMin) + _dmgMin);
			}
		} catch(Exception e){
			
		} finally {
			own.setParalyzed(false);
		}
		
	}
	
	@Override
	public void actionNoDelay(L1NpcInstance own, ArrayList<L1PcInstance> pcs) {
		if(own == null || pcs == null || pcs.size() <= 0) {
			return;
		}
		try {
			if(_actid >= 0) {
				Broadcaster.broadcastPacket(own, S_DoActionGFX.get(own.getId(), _actid));
			}
			Thread.sleep(_rnd.nextInt(_dmgMax - _dmgMin) + _dmgMin);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}