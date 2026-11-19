package l1j.server.MJItemSkillSystem.Model.Defence;

import l1j.server.MJItemSkillSystem.Model.MJItemSkillModel;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.character.skill.L1SkillId;

import l1j.server.server.utils.MJCommons;

public class Def_ProbReduction extends MJItemSkillModel{

	@Override
	protected boolean isPlay(L1Character c, L1ItemInstance t_item){
		int itemId = t_item.getItemId();
		int enchant = t_item.getEnchantLevel();
		if(itemId == 22226){
			if(enchant >= 8)
				return isInPercent(2);
			else if(enchant >= 7)
				return isInPercent(1);
			return false;
		}else if(itemId == 222332){
			if(enchant >= 8)
				return isInPercent(3);
			else if(enchant >= 7)
				return isInPercent(2);
			else if(enchant >= 6)
				return isInPercent(1);
			return false;
		}
		return super.isPlay(c, t_item);
	}
	
	@Override
	public double get(L1Character attacker, L1Character defender, L1ItemInstance t_item, double dwd) {
		if(MJCommons.isBlessed(defender) || MJCommons.isUnbeatable(defender) || !isPlay(defender, t_item))
			return 0D;
		
		double reduc = min_val;
		if(min_val != max_val){
			reduc		= calcEnchant(t_item, reduc);
			reduc		= calcAttr(defender, reduc);
			reduc		= calcStat(defender, reduc);
		}
		if(reduc > max_val)
			reduc = max_val;
		
		if(eff_id > 0)
			SC_SkillSound.sendAndBroadcast(defender, defender.getId(), eff_id);
		defender.setSkillEffect(L1SkillId.ARMOR_BLESSING, 500);
		
		return reduc;
	}
	
	@Override
	public boolean isAttack(){
		return false;
	}
}
