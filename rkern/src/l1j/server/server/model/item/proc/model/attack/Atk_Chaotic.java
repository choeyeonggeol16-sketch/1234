package l1j.server.server.model.item.proc.model.attack;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.item.proc.model.ItemSkillModel;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.utils.GameUtils;

public class Atk_Chaotic extends ItemSkillModel{	
	@Override
	public double get(L1Character attacker, L1Character defender, L1ItemInstance t_item, double dwd) {
		int law = attacker.getLawful();
		double dmg 	= GameUtils.getChaoticDamage(law);
		if(dmg <= 0D)
			return 0D;
		
		dmg 		= calcEnchant(t_item, dmg);
		dmg 		= calcAttr(defender, dmg);
		dmg 		= calcStat(attacker, dmg);
		if(dmg > max_val) 		dmg = max_val;
		else if(dmg < min_val) 	dmg = min_val;
		
		if(eff_id > 0)
			SC_SkillSound.sendAndBroadcast(defender, defender.getId(), eff_id);
		
		return dmg;
	}
}
