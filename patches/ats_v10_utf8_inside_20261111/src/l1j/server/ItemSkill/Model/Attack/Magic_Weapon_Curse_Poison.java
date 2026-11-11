package l1j.server.ItemSkill.Model.Attack;

import l1j.server.commons;
import l1j.server.ItemSkill.Model.ItemSkillModel;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.poison.DamagePoison;
import l1j.server.server.serverpackets.S_SkillSound;

public class Magic_Weapon_Curse_Poison extends ItemSkillModel {
	public double get(L1Character attacker, L1Character defender, ItemInstance t_item, double dwd) {
		
		// 스킬 확률 판별 , 스킬 상태 유무
		if(!isProbability(attacker, t_item) || commons.isLock(defender)) {
			return 0.0D;
		}		
		// 데미지 전달
		DamagePoison.doInfection(attacker, defender, 3000, 5);
		// 이팩트 처리 
		if (eff_id > 0) {
			broadcast(defender, S_SkillSound.get(defender.getId(), eff_id)); 
		}
		return 0.0D;
	}
}
