package l1j.server.ItemSkill.Model.Attack;

import java.util.ArrayList;
import java.util.List;

import l1j.server.commons;
import l1j.server.ItemSkill.Model.ItemSkillModel;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.PetInstance;
import l1j.server.server.model.Instance.SummonInstance;
import l1j.server.server.serverpackets.S_AreaAttackEffect;
import l1j.server.server.utils.SkillUtil;

public class Wide_Area_Magic_Weapon extends ItemSkillModel {
	public double get(L1Character attacker, L1Character defender, ItemInstance t_item, double dwd) {

		// 스킬 확률 판별 , 스킬 상태 유무 
		if(!isProbability(attacker, t_item) || commons.isLock(defender)) {
			return 0.0D;
		}
		double result = 0.0D;
		List<L1Object> list = attacker.getKnownObjects();
		ArrayList<L1Character> tars = new ArrayList<L1Character>(list.size());
		tars.add(defender);
		for (L1Object obj : list) {
			if ((obj != null) && ((obj instanceof L1Character))) {
				L1Character c = (L1Character) obj;
				if ((!c.getMap().isSafetyZone(c.getLocation())) && (!c.isDead())
						&& (commons.getDistance(defender.getX(), defender.getY(), c.getX(), c.getY()) <= skill_range)) {
					if ((obj instanceof PetInstance) || (obj instanceof SummonInstance)) {
						continue;
					}
					double dmg = commons.getMagicDamage(attacker, c, is_cri, is_erase, is_counter, attr_type, min_dmg, max_dmg, in_dmg);	
	
					if (c.getId() == defender.getId()) {
						if ((!SkillUtil.isCounterMagic(c))) {
							result = dmg;
						}
					} else {
						if ((!SkillUtil.isCounterMagic(c))) {
							c.receiveDamage(attacker, (int) dmg);
						}
						tars.add(c);
					}
				}
			}
		}
		if (result > 0.0D) {
			broadcast(attacker, S_AreaAttackEffect.getNoDir(attacker, tars, eff_id, t_item.getItem().getType1() + 1)); 
		} 

		return result;
	}
}
