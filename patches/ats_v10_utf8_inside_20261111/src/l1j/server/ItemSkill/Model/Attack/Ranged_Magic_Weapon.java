package l1j.server.ItemSkill.Model.Attack;

import l1j.server.commons;
import l1j.server.ItemSkill.Model.ItemSkillModel;
import l1j.server.configure.Config;
import l1j.server.server.ActionCodes;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.skill.SkillId;
import l1j.server.server.serverpackets.S_SkillSound;
import l1j.server.server.serverpackets.S_UseAttackSkill;
import l1j.server.server.utils.CommonUtil;
import l1j.server.server.utils.EffectUtil;

public class Ranged_Magic_Weapon extends ItemSkillModel {
	public double get(L1Character attacker, L1Character defender, ItemInstance t_item, double dwd) {
		
		double dmg = 0.0D;
		
		// 스킬 확률 판별 , 스킬 상태 유무 
		if(!isProbability(attacker, t_item) || commons.isLock(defender)) {
			return 0.0D;
		}
		// 마법데미지 인지 판별 	
		if(is_magic) {		
			dmg = commons.getMagicDamage(attacker, defender, is_cri, is_erase, is_counter, attr_type, min_dmg, max_dmg, in_dmg);			
		} else { // 고정 데미지 무기 취급
			dmg += CommonUtil.random(max_dmg) + min_dmg;
			
			if(is_counter) {
				if(defender.hasSkillEffect(SkillId.카운터_매직)) {
					defender.removeSkillEffect(SkillId.카운터_매직);
					S_SkillSound s = S_SkillSound.get(defender.getId(), EffectUtil.카운터_매직);
					defender.sendPackets(s);
					defender.broadcastPacket(s);
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
			if(effectType == 1) {
				broadcast(defender, S_SkillSound.get(defender.getId(), eff_id));
			} else {
				broadcast(attacker, S_UseAttackSkill.MagicArrow(attacker, defender.getId(), eff_id, defender.getX(), defender.getY(), ActionCodes.ACTION_Attack));
			}
		}	
		
		return dmg;
	}
}
