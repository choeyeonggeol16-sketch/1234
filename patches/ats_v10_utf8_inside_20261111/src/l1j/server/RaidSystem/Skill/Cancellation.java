package l1j.server.RaidSystem.Skill;

import java.util.ArrayList;

import l1j.server.RaidSystem.Dragon_Raid_Skill;
import l1j.server.server.model.Broadcaster;
import l1j.server.server.model.L1PolyMorph;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.skill.SkillId;
import l1j.server.server.serverpackets.S_CharVisualUpdate;
import l1j.server.server.serverpackets.S_DoActionGFX;
import l1j.server.server.serverpackets.S_Invis;
import l1j.server.server.serverpackets.S_RemoveObject;
import l1j.server.server.serverpackets.S_SkillHaste;
import l1j.server.server.serverpackets.S_SkillIconBlessOfEva;
import l1j.server.server.serverpackets.S_Sound;
import l1j.server.server.utils.SkillUtil;

public class Cancellation extends Dragon_Raid_Skill {
	@Override
	public String toString(){
		return Cancellation.class.getName();
	}
	
	@Override
	public void action(L1NpcInstance own, ArrayList<L1PcInstance> pcs) {
		if(own == null || pcs == null || pcs.size() <= 0 || own.isParalyzed())
			return;

		L1PcInstance 	pc		= null;
		int 			size 	= pcs.size();
		try{
			synchronized(own.synchObject){
				own.setParalyzed(true);
				if(_actid >= 0)
					Broadcaster.broadcastPacket(own, S_DoActionGFX.get(own.getId(), _actid));
				
//				if((_type & BSRTYPE_ONCE) > 0){
//					pc = pcs.get(_rnd.nextInt(size));
//					if(isSkillSet(own, pc))
//						processAction(own, pc);
//				}else{
//					for(int i=0; i<size; i++){
//						pc = pcs.get(i);
//						if(!isSkillSet(own, pc))
//							continue;
//
//						processAction(own, pc);
//					}
//				}
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
		try{
			if(_actid >= 0) {
				Broadcaster.broadcastPacket(own, S_DoActionGFX.get(own.getId(), _actid));
			}
//			if((_type & BSRTYPE_ONCE) > 0) {
//				pc = pcs.get(_rnd.nextInt(size));
//				if(isSkillSet(own, pc))
//					processAction(own, pc);
//			}else{
//				for(int i=0; i<size; i++){
//					pc = pcs.get(i);
//					
//					if(!isSkillSet(own, pc))
//						continue;
//					
//					processAction(own, pc);
//				}
//			}
			sleepAction(own, _actid);
		}catch(Exception e){
			e.printStackTrace();
		}
	}
	
	private void processAction(L1NpcInstance own, L1PcInstance pc) {
		if(_effectId >= 0) {
//			S_OnlyEffect oe = new S_OnlyEffect(pc.getId(), _effectId);
//			pc.sendPackets(oe, false);
//			Broadcaster.broadcastPacket(pc, oe, true);
		}
		pc.delInvis();
		// 독 관련 해제
		pc.curePoison();
		pc.cureParalaysis();
		// 스킬의 해제
		for(int skillNum = SkillId.스킬_시작; skillNum <= SkillId.스킬_끝; skillNum++) {
			if (SkillUtil.isNotCancelable(skillNum)) {
				continue;
			}
			pc.removeSkillEffect(skillNum);
		}
		for(int skillNum = SkillId.상태_시작; skillNum <= SkillId.상태_끝; skillNum++) {
			if (skillNum == SkillId.상태_채팅_금지) { // 채팅 금지는 해제하지 않는다
				continue;
			}
			pc.removeSkillEffect(skillNum);
		}
		// 요리 효과  해제
		for(int skillNum = SkillId.요리_시작; skillNum <= SkillId.요리_끝; skillNum++) {
			pc.removeSkillEffect(skillNum);
		}
	}
}

