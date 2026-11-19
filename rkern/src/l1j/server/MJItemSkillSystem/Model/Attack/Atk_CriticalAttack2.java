package l1j.server.MJItemSkillSystem.Model.Attack;

import l1j.server.MJItemSkillSystem.Model.MJItemSkillModel;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.character.skill.L1SkillId;

import l1j.server.server.utils.MJCommons;

public class Atk_CriticalAttack2 extends MJItemSkillModel{
	@Override
	public double get(L1Character attacker, L1Character defender, L1ItemInstance t_item, double dwd) {
		if(MJCommons.isBlessed(attacker) || !isPlay(attacker, t_item))
			return 0D;
		
		double dmg = min_val;
		if(min_val != max_val){
			dmg = calcEnchant(t_item, dmg);
			dmg = calcAttr(defender, dmg);
			dmg = calcStat(defender, dmg);
		}
		
		if(dmg > max_val)
			dmg = max_val;
		
		if(eff_id > 0)
			SC_SkillSound.sendAndBroadcast(attacker, attacker.getId(), eff_id);
		attacker.setSkillEffect(L1SkillId.ARMOR_BLESSING, 500);
		return dmg;
	}

}
