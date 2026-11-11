package l1j.server.RaidSystem.Skill;

import java.util.ArrayList;

import l1j.server.RaidSystem.Dragon_Raid_Skill;
import l1j.server.server.model.Broadcaster;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_DoActionGFX;
import l1j.server.server.utils.SystemUtil;

public class Summon extends Dragon_Raid_Skill {
	@Override
	public String toString(){
		return Summon.class.getName();
	}
	
	
	@Override
	public void action(L1NpcInstance own, ArrayList<L1PcInstance> pcs) {
		if(own == null || own.isParalyzed()) {
			return;
		}
		try{
			synchronized(own.synchObject){
				own.setParalyzed(true);
				if(_actid >= 0) {
					Broadcaster.broadcastPacket(own, S_DoActionGFX.get(own.getId(), _actid));
				}
				int num = 0;
				if(_dmgMin == _dmgMax) {
					num = _dmgMin;
				}
				else {
					num = _rnd.nextInt(_dmgMax - _dmgMin) + _dmgMin;
				}
				for(int i=0; i<num; i++)
					SystemUtil.spawn(own.getX(), own.getY(), own.getMapId(), _effectId, _range, 0);
						
				sleepAction(own, _actid);
			}
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			own.setParalyzed(false);			
		}
	}

	@Override
	public void actionNoDelay(L1NpcInstance own, ArrayList<L1PcInstance> pcs) {
		if(own == null) {
			return;
		}
		try{
			if(_actid >= 0) {
				Broadcaster.broadcastPacket(own, S_DoActionGFX.get(own.getId(), _actid));
			}
			int num = 0;
			if(_dmgMin == _dmgMax) {
				num = _dmgMin;
			}
			else {
				num = _rnd.nextInt(_dmgMax - _dmgMin) + _dmgMin;
			}
			for(int i=0; i<num; i++) {
				SystemUtil.spawn(own.getX(), own.getY(), own.getMapId(), _effectId, _range, 0);	
			}
				
			sleepAction(own, _actid);
		}catch(Exception e){
			e.printStackTrace();
		}
	}
}

