package l1j.server.ItemSkill.Model.Attack;

import l1j.server.ItemSkill.Model.ItemSkillModel;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.ItemInstance;

public class Chaotic_Magic_Weapon extends ItemSkillModel {
	public double get(L1Character attacker, L1Character defender, ItemInstance t_item, double dwd) {
		
		int law = attacker.getLawful();
	
		double dmg = 0;

		if (law <= -20010) {
			dmg += 5;
		} else if (law <= -10035) {
			dmg += 3;
		} else if (law <= -136) {
			dmg += 2;
		
	    }
		return dmg;
	}
}
