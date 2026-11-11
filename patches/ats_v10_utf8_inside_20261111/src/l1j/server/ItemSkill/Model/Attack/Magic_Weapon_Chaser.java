package l1j.server.ItemSkill.Model.Attack;

import I1j.server.server.model.Thread.ChaserTimer;
import l1j.server.commons;
import l1j.server.ItemSkill.Model.ItemSkillModel;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.skill.SkillId;

public class Magic_Weapon_Chaser extends ItemSkillModel {
	public double get(L1Character attacker, L1Character defender, ItemInstance t_item, double dwd) {

		double dmg = 0.0D;
		// 스킬 확률 , 스킬 효과, 중복 방지
		if(!isProbability(attacker, t_item) || commons.isLock(defender)) {
			return 0.0D;
		}
		// 마법데미지 인지 판별 	
		if(is_magic) {		
			dmg = commons.getMagicDamage(attacker, defender, is_cri, is_erase, is_counter, attr_type, min_dmg, max_dmg, in_dmg);
		}
		if(!defender.hasSkillEffect(SkillId.체이서_딜레이)) {
			ChaserTimer chaser = new ChaserTimer((L1PcInstance) attacker, defender, dmg, effectType);
			chaser.begin();
			defender.setSkillEffect(SkillId.체이서_딜레이, 5000);
		}
		return 0.0D;

	}

}
