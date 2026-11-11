package l1j.server.ItemSkill.Model.Attack;

import l1j.server.commons;
import l1j.server.ItemSkill.Model.ItemSkillModel;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.utils.CommonUtil;

public class Magic_Weapon_Hpdrain extends ItemSkillModel {
	public double get(L1Character attacker, L1Character defender, ItemInstance t_item, double dwd) {
		double dmg = 0.0D;
		
		// 스킬 확률 판별 , 스킬 상태 유무 
		if(!isProbability(attacker, t_item) || commons.isLock(defender)) {
			return 0.0D;
		}
		// 순수 검 데미지 구하기.
		int 추가데미지 =  attacker.getMeleeWeaponDmg(), 인챈트데미지 = t_item.getEnchantLevel(), 
				무기작은몹데미지 = t_item.getItem().getDmgSmall(), 무기데미지 = CommonUtil.randomRange(1, 무기작은몹데미지) + 인챈트데미지 + 추가데미지;		
		dmg = 무기데미지 / 8 + 1;
		// 데미지 전달
		attacker.setCurrentHp((int) (attacker.getCurrentHp() + dmg));
		return 0.0D;
	}
}
