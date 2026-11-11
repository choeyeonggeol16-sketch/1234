package l1j.server.ItemSkill.Model.Attack;

import l1j.server.commons;
import l1j.server.ItemSkill.Model.ItemSkillModel;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.skill.SkillId;
import l1j.server.server.serverpackets.S_SkillIconWindShackle;
import l1j.server.server.serverpackets.S_SkillSound;
import l1j.server.server.utils.CommonUtil;
import l1j.server.server.utils.SkillUtil;

public class Magic_Weapon_Pumpkin_Curse extends ItemSkillModel {
	public double get(L1Character attacker, L1Character defender, ItemInstance t_item, double dwd) {
		
		double dmg = 0.0D;
		
		// 스킬 확률, 스킬 발동, 스킬 상태 유무 
		if(!isProbability(attacker, t_item) || defender.hasSkillEffect(SkillId.윈드_셰클) || commons.isLock(defender)) {
			return 0.0D;
		}
		// 카매 상태  체크 
		if(is_counter) {
			if(SkillUtil.isCounterMagic(defender)) {
				return 0.0D;
			}
		}
		// 마법 체크
		if (is_magic) {	
			int time =  CommonUtil.random(min_dmg) + 1;
			if (time > 0) {				
				defender.setSkillEffect(SkillId.윈드_셰클, time);
				defender.sendPackets(new S_SkillIconWindShackle(defender.getId(), time), true);
			}		
		}
		if (eff_id > 0) {
			broadcast(defender, S_SkillSound.get(defender.getId(), eff_id)); 
		}
		return dmg;
	}
}
