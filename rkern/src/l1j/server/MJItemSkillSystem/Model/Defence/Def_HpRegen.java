package l1j.server.MJItemSkillSystem.Model.Defence;

import l1j.server.MJItemSkillSystem.Model.MJItemSkillModel;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.character.skill.L1SkillId;

import l1j.server.server.utils.MJCommons;

public class Def_HpRegen extends MJItemSkillModel{

	@Override
	public double get(L1Character attacker, L1Character defender, L1ItemInstance t_item, double dwd) {
		if(MJCommons.isBlessed(defender) || MJCommons.isUnbeatable(defender) || !isPlay(defender, t_item))
			return 0D;
		
		double heal = min_val;
		if(min_val != max_val){
			heal		= _rnd.nextInt(max_val - min_val) + min_val;
			heal		= calcEnchant(t_item, heal);
			heal		= calcAttr(defender, heal);
			heal		= calcStat(defender, heal);
		}
		heal		= MJCommons.calcHealing(defender, heal);
		/*if(heal > max_val)
			heal = max_val;
		else if(heal < min_val)
			heal = min_val;
		*/
		defender.setCurrentHp(defender.getCurrentHp() + (int)heal);
		if(eff_id > 0)
			SC_SkillSound.sendAndBroadcast(defender, defender.getId(), eff_id);
		defender.setSkillEffect(L1SkillId.ARMOR_BLESSING, 500);
		return 0D;
	}
	
	@Override
	public boolean isAttack(){
		return false;
	}
}
