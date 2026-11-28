package l1j.server.server.model.item.proc.model.attack;

import l1j.server.server.model.character.transform.CTransformCombatData;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.item.proc.model.ItemSkillModel;
import l1j.server.server.model.network.core.sc.packet.SC_UseAttackSkill;
import l1j.server.server.shared.GameRandom;
import l1j.server.server.utils.GameUtils;

public class Atk_NormalRangeAttack extends ItemSkillModel{
	@Override
	public double get(L1Character attacker, L1Character defender, L1ItemInstance t_item, double dwd) {
		if(!isPlay(attacker, t_item))
			return 0D;

		double dmg = 0D;
		if(is_magic){	
			if(isCounterMagic(defender))
				return 0D;
			dmg = GameUtils.getMagicDamage(attacker, defender, min_val, max_val);
		}else {
			dmg = GameRandom.nextIntOrFixed(min_val, max_val);
		}

		dmg = calcEnchant(t_item, dmg);	
		dmg = calcAttr(defender, dmg);
		dmg = calcStat(attacker, dmg);
		dmg = calcMrDefense(defender.GetMDefendBySkill(), dmg);
		if(dmg > max_val) 		dmg = max_val;
		else if(dmg < min_val) 	dmg = min_val;

		if(eff_id > 0)
			if (t_item.getItemId() == 123003) {

				SC_UseAttackSkill.broadcast(
						attacker,
						attacker.getId(),
						eff_id,
						attacker.getX(),
						attacker.getY(),
						CTransformCombatData.weaponAction(attacker, t_item) + 1,
						false);
			} else {
				SC_UseAttackSkill.broadcast(
						attacker,
						defender.getId(),
						eff_id,
						defender.getX(),
						defender.getY(),
						CTransformCombatData.weaponAction(attacker, t_item) + 1,
						false);
			}
		if(GameUtils.isUnbeatable(defender))
			return 0D;
		return dmg;
	}
}
