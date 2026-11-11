package l1j.server.RaidSystem.Skill;

import java.util.ArrayList;

import l1j.server.RaidSystem.Dragon_Raid_Skill;
import l1j.server.RaidSystem.Loader.Dragon_Raid_Load_Manager;
import l1j.server.RaidSystem.Util.PoisonCludeSpawn;
import l1j.server.server.GeneralThreadPool;
import l1j.server.server.model.Broadcaster;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_DoActionGFX;

public class PoisonCloud extends Dragon_Raid_Skill {
	
	@Override
	public String toString(){
		return PoisonCloud.class.getName();
	}
	
	@Override
	public void action(L1NpcInstance own, ArrayList<L1PcInstance> pcs) {
		if(own == null || pcs == null || pcs.size() <= 0 || own.isParalyzed()) {
			return;
		}
		L1PcInstance 	pc		= null;
		int 			size 	= pcs.size();
		int				pending	= 0;
		try{
			synchronized(own.synchObject){
				own.setParalyzed(true);
				if(_actid >= 0)
					Broadcaster.broadcastPacket(own, S_DoActionGFX.get(own.getId(), _actid));
				
				while(true){
					pc = pcs.get(_rnd.nextInt(size));
					if(pending++ > 10) {
						break;
					}
					if(isPoisonCloudSkillSet(own, pc)) {
						break;
					}
				}
				
				if(pending <= 10)
					GeneralThreadPool.getInstance().execute(new PoisonCludeSpawn(
							own, pc.getX(), pc.getY(), Dragon_Raid_Load_Manager.MRS_SP_POISONCLOUD_ID, Dragon_Raid_Load_Manager.MRS_SP_POISONCLOUD_TIME));
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
		if(own == null || pcs == null || pcs.size() <= 0)
			return;
		
		L1PcInstance 	pc		= null;
		int 			size 	= pcs.size();
		int				pending	= 0;
		try{
			if(_actid >= 0)
				Broadcaster.broadcastPacket(own, S_DoActionGFX.get(own.getId(), _actid));
			
			while(true){
				pc = pcs.get(_rnd.nextInt(size));
				if(pending++ > 10) {
					break;
				}
				
				if(isPoisonCloudSkillSet(own, pc)) {
					break;
				}
			}
			if(pending <= 10)
				GeneralThreadPool.getInstance().execute(new PoisonCludeSpawn(
						own, pc.getX(), pc.getY(), Dragon_Raid_Load_Manager.MRS_SP_POISONCLOUD_ID, Dragon_Raid_Load_Manager.MRS_SP_POISONCLOUD_TIME));
			
			sleepAction(own, _actid);
		}catch(Exception e){
			e.printStackTrace();
		}
	}
}
