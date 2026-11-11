package l1j.server.RaidSystem.Skill;

import java.util.ArrayList;

import l1j.server.RaidSystem.Dragon_Raid_Skill;
import l1j.server.RaidSystem.Loader.Dragon_Raid_Load_Manager;
import l1j.server.server.model.Broadcaster;
import l1j.server.server.model.L1EffectSpawn;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.skill.SkillId;
import l1j.server.server.serverpackets.S_DoActionGFX;
import l1j.server.server.serverpackets.S_Paralysis;
import l1j.server.server.serverpackets.S_SkillSound;

public class RangeStun extends Dragon_Raid_Skill {

private static final S_Paralysis s_paral = new S_Paralysis(S_Paralysis.TYPE_STUN, true);
	
	@Override
	public String toString(){
		return RangeStun.class.getName();
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
					if(isMagicSkillSet(own, pc))
						processAction(own, pc);
				}else{
					for(int i=0; i<size; i++) {
						pc = pcs.get(i);
						
						if(!isMagicSkillSet(own, pc)) {
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
				if(isMagicSkillSet(own, pc))
					processAction(own, pc);
			} else {
				for(int i=0; i<size; i++) {
					pc = pcs.get(i);
					
					if(!isMagicSkillSet(own, pc)) {
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
	
	private void processAction(L1NpcInstance own, L1PcInstance pc){
		if(_effectId > 0) {
			S_SkillSound oe = S_SkillSound.get(pc.getId(), _effectId);
			pc.sendPackets(oe, false);
			Broadcaster.broadcastPacket(pc, oe);
		}
		int time = calcStunTime();
		pc.setSkillEffect(SkillId.ºÓ≈©_Ω∫≈œ, time);
		L1EffectSpawn.getInstance().spawnEffect(Dragon_Raid_Load_Manager.MRS_BS_STUN_EFFECTID, time, pc.getX(), pc.getY(), pc.getMapId());
		pc.sendPackets(s_paral, false);
	}
	
	private int calcStunTime(){
		return _rnd.nextInt(Dragon_Raid_Load_Manager.MRS_BS_STUN_MAX - Dragon_Raid_Load_Manager.MRS_BS_STUN_MIN) + Dragon_Raid_Load_Manager.MRS_BS_STUN_MIN;
	}
}
