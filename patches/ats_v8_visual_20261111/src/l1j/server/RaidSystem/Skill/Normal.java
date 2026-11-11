package l1j.server.RaidSystem.Skill;

import java.util.ArrayList;
import l1j.server.RaidSystem.Dragon_Raid_Skill;
import l1j.server.server.model.Broadcaster;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_DoActionGFX;
import l1j.server.server.serverpackets.S_SkillSound;

public class Normal extends Dragon_Raid_Skill {
	
	@Override
	public String toString(){
		return Normal.class.getName();
	}
	
	@Override
	public void action(L1NpcInstance own, ArrayList<L1PcInstance> pcs) {
		if(own == null || pcs == null || pcs.size() <= 0 || own.isParalyzed()) {
			return;
		}
		L1PcInstance 	pc		= null;
		int 			size 	= pcs.size();
		try{
			synchronized(own.synchObject) {
				own.setParalyzed(true);
				if(_actid >= 0) {
					Broadcaster.broadcastPacket(own, S_DoActionGFX.get(own.getId(), _actid));
				}
				if((_type & BSRTYPE_ONCE) > 0) {
					pc = pcs.get(_rnd.nextInt(size));
					if(isNormalSkillSet(own, pc))
						processAction(own, pc);
				} else {
					for(int i=0; i<size; i++) {
						pc = pcs.get(i);
						
						if(!isNormalSkillSet(own, pc)) {
							continue;						
						}
						processAction(own, pc);
					}
				}
				sleepAction(own, _actid);
			}
		} catch(Exception e) {
			e.printStackTrace();
		} finally {
			own.setParalyzed(false);			
		}
		
	}

	@Override
	public void actionNoDelay(L1NpcInstance own, ArrayList<L1PcInstance> pcs) {
		// TODO 자동 생성된 메소드 스텁
		if(own == null || pcs == null || pcs.size() <= 0) {
			return;
		}	
		L1PcInstance 	pc		= null;
		int 			size 	= pcs.size();
		try{
			if(_actid >= 0) {
				Broadcaster.broadcastPacket(own, S_DoActionGFX.get(own.getId(), _actid));
			}
			if((_type & BSRTYPE_ONCE) > 0) {
				pc = pcs.get(_rnd.nextInt(size));
				if(isNormalSkillSet(own, pc)) {
					processAction(own, pc);
				}
			} else {
				for(int i=0; i<size; i++) {
					pc = pcs.get(i);
					if(!isNormalSkillSet(own, pc)) {
						continue;
					}
					processAction(own, pc);
				}
			}
			sleepAction(own, _actid);
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	private void processAction(L1NpcInstance own, L1PcInstance pc) {
		if(_effectId > 0){
			S_SkillSound oe = S_SkillSound.get(pc.getId(), _effectId);
			pc.sendPackets(oe, false);
			Broadcaster.broadcastPacket(pc, oe);
		}
		pc.receiveDamage(own, calcDamage(own, pc));
	}
}
