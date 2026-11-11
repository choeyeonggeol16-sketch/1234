package l1j.server.RaidSystem.Skill;

import java.util.ArrayList;

import l1j.server.RaidSystem.Dragon_Raid_Skill;
import l1j.server.server.model.Broadcaster;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_DoActionGFX;
import l1j.server.server.utils.CommonUtil;

public class ReCall extends Dragon_Raid_Skill {
	@Override
	public String toString(){
		return ReCall.class.getName();
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
					if(isReCallSkillSet(own, pc))
						processAction(own, pc);
				} else {
					for(int i=0; i<size; i++) {
						pc = pcs.get(i);

						if(!isReCallSkillSet(own, pc)) {
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
				if(isReCallSkillSet(own, pc))
					processAction(own, pc);
			} else {
				for(int i=0; i<size; i++) {
					pc = pcs.get(i);

					if(!isReCallSkillSet(own, pc)) {
						continue;
					}
					processAction(own, pc);
				}
			}
			sleepAction(own, _actid);
		}catch(Exception e){
			e.printStackTrace();
		}
	}

	private void processAction(L1NpcInstance own, L1PcInstance pc) {
		if (own.getLocation().getTileLineDistance(pc.getLocation()) > 4) {
			int loc_x = own.getX() + CommonUtil.randomRange(-1, 1);
			int loc_y = own.getY() + CommonUtil.randomRange(-1, 1);
			if (pc.glanceCheck(loc_x, loc_y)) {
				pc.teleport(loc_x, loc_y, pc.getMapId(), pc.getHeading(), 169, true, false);
			}
		}
	}
}
