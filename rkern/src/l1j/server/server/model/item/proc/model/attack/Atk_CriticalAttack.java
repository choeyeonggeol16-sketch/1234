package l1j.server.server.model.item.proc.model.attack;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.item.proc.model.ItemSkillModel;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.utils.GameUtils;

public class Atk_CriticalAttack extends ItemSkillModel{
	@Override
	public double get(L1Character attacker, L1Character defender, L1ItemInstance t_item, double dwd) {
		int itemId = t_item.getItemId();
		if(GameUtils.isBlessed(attacker) || !isPlay(attacker, t_item))
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
			if (itemId >= 22208 && itemId <= 22211) {
				SC_SkillSound.sendAndBroadcast(attacker, attacker.getId(), eff_id);
				attacker.setSkillEffect(L1SkillId.ARMOR_BLESSING, 500);
			} else {
			SC_SkillSound.sendAndBroadcast(attacker, defender.getId(), eff_id);
		attacker.setSkillEffect(L1SkillId.ARMOR_BLESSING, 500);
			}
		return dmg;
	}
}
