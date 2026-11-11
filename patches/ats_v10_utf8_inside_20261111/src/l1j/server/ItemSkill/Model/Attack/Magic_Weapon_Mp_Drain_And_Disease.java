package l1j.server.ItemSkill.Model.Attack;

import l1j.server.commons;
import l1j.server.ItemSkill.Model.ItemSkillModel;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.skill.SkillId;
import l1j.server.server.serverpackets.S_SkillSound;
import l1j.server.server.utils.CommonUtil;
import l1j.server.server.utils.SkillUtil;

public class Magic_Weapon_Mp_Drain_And_Disease extends ItemSkillModel {
	public double get(L1Character attacker, L1Character defender, ItemInstance t_item, double dwd) {
		double dmg = 0.0D;	
		// 스킬 상태 유무 
		if(commons.isLock(defender)) {
			return 0.0D;
		}
		// 타겟이 mr 체크
		if (is_magic) {	
			dmg = CommonUtil.randomRange(1, 9);		
			
			int targetMr = defender.getResistance().getMr();
		    if(targetMr >= 100) {
		    	dmg /= 2;
		    }
		}
		// 이레이즈 매직 판정 처리 
		if(is_erase) {
			SkillUtil.cancelEraseMagic(defender);
		}		
		// 버그 체크 
		if(defender.getCurrentMp() < dmg) {
			dmg = defender.getCurrentMp();
		}	
		// 데미지 전달
		defender.setCurrentMp((int) (defender.getCurrentMp() - dmg));
		attacker.setCurrentMp((int) (attacker.getCurrentMp() + dmg));
		
		if(!isProbability(attacker, t_item) || defender.hasSkillEffect(SkillId.디지즈)) {
			return 0.0D;
		}
		// 카운터 매직 유무
		if(is_counter) {
			if(SkillUtil.isCounterMagic(defender)) {
				return 0.0D;
			}
		}	
		// 마방 제한
		int targetMr = defender.getResistance().getMr();
	    if(targetMr >= 100) {
	    	return 0.0D;
	    }
		
		int t = min_dmg;
		defender.addMeleeHit(-6);
		defender.getAC().addAc(12);
		defender.setSkillEffect(SkillId.디지즈, t);
		// 이팩트 처리
		if (eff_id > 0) {
			broadcast(defender, S_SkillSound.get(defender.getId(), eff_id)); 
		}
		return 0.0D;
	}
}

