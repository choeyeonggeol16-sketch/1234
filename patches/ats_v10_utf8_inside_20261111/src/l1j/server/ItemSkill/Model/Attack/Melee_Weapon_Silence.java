package l1j.server.ItemSkill.Model.Attack;

import l1j.server.commons;
import l1j.server.ItemSkill.Model.ItemSkillModel;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.skill.SkillId;
import l1j.server.server.serverpackets.S_SkillSound;
import l1j.server.server.utils.SkillUtil;

public class Melee_Weapon_Silence extends ItemSkillModel {
	public double get(L1Character attacker, L1Character defender, ItemInstance t_item, double dwd) {
	
		// 스킬 확률 판별 , 스킬 상태 유무 
		if(!isProbability(attacker, t_item) || defender.hasSkillEffect(SkillId.사일런스) || commons.isLock(defender)) {
			return 0.0D;
		}	
		// 카운터 매직 처리
		if(is_counter) {
			if(SkillUtil.isCounterMagic(defender)) {
				return 0.0D;
			}
		}	
		if(is_magic) {
			// 중복 스킬 체크 
			int time = 16000;
			defender.setSkillEffect(SkillId.사일런스, time);	
				    
		}
		if (eff_id > 0) {
			broadcast(defender, S_SkillSound.get(defender.getId(), eff_id));
		}
	  
		
		return 0.0D;
	}	
}


