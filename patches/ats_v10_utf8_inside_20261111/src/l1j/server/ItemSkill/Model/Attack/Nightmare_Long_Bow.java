package l1j.server.ItemSkill.Model.Attack;

import l1j.server.commons;
import l1j.server.ItemSkill.Model.ItemSkillModel;
import l1j.server.configure.Config;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.skill.SkillId;
import l1j.server.server.serverpackets.S_SkillSound;
import l1j.server.server.utils.CommonUtil;
import l1j.server.server.utils.EffectUtil;

public class Nightmare_Long_Bow extends ItemSkillModel {
	public double get(L1Character attacker, L1Character defender, ItemInstance t_item, double dwd) {
		
		double dmg = 0.0D;
		// 인챈트 체크 
		int 인챈트레벨 = t_item.getEnchantLevel(), law = attacker.getLawful();
		
		if (law <= -20010) {
			dmg += 5;
		} else if (law <= -10035) {
			dmg += 3;
		} else if (law <= -136) {
			dmg += 2;
		
	    }
		if(인챈트레벨 > 9) {
			if(!isProbability(attacker, t_item) || commons.isLock(defender)) {
				return 0.0D;
			} 
			if(is_magic) {		
				dmg = commons.getMagicDamage(attacker, defender, is_cri, is_erase, is_counter, attr_type, min_dmg, max_dmg, in_dmg);				
				double alignmentRevision = 1.0;
				if (attacker.getLawful() > 0) {
					alignmentRevision += (attacker.getLawful() / 32768.0);
				}
				dmg *= alignmentRevision;
				
			} else {
				// 마법검이 아닐 경우 
				dmg +=  CommonUtil.random(max_dmg) + min_dmg;

				if(is_counter) {
					if(defender.hasSkillEffect(SkillId.카운터_매직)) {
						defender.removeSkillEffect(SkillId.카운터_매직);
						defender.sendAll(EffectUtil.카운터_매직);
						dmg = 0;
					}
				}	
				// 타겟의 데미지 리덕션 처리 
				if(defender instanceof L1PcInstance) {
					L1PcInstance targetPc = (L1PcInstance) defender;

					if(targetPc.hasSkillEffect(SkillId.리덕션_아머)) {
						int targetLevel = targetPc.getLevel() < 50 ? 50 : targetPc.getLevel();
						dmg -= (targetLevel - 50) / 5 + 1;
					}		
					dmg -= targetPc.getDamageReduction();

					if (targetPc != null && targetPc.hasSkillEffect(SkillId.이뮨_투함)) {
						dmg *= Config.IMMNUE_TO_HARM_RATE;
					}		
				}
			}
			// 이팩트 처리
			if (eff_id > 0) {
				broadcast(defender, S_SkillSound.get(defender.getId(), eff_id));		
			}
		}	
		return dmg;	
	}
}
