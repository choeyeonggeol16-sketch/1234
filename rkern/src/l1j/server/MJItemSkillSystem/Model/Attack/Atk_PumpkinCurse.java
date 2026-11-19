package l1j.server.MJItemSkillSystem.Model.Attack;

import l1j.server.MJItemSkillSystem.Model.MJItemSkillModel;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.character.skill.L1SkillId;

import l1j.server.server.utils.MJCommons;
import l1j.server.server.model.network.core.sc.packet.SC_SkillIconWindShackle;

public class Atk_PumpkinCurse extends MJItemSkillModel{

	@Override
	public double get(L1Character attacker, L1Character defender, L1ItemInstance t_item, double dwd) {
		if(!isPlay(attacker, t_item))
			return 0D;
		
		double dmg = 0D;
		if(is_magic){	
			if(isCounterMagic(defender))
				return 0D;
			dmg = MJCommons.getMagicDamage(attacker, defender, min_val, max_val);
		}else {
			dmg = _rnd.nextInt(max_val - min_val) + min_val;
		}
		
		dmg = calcEnchant(t_item, dmg);
		dmg = calcAttr(defender, dmg);
		dmg = calcStat(attacker, dmg);
		if(dmg > max_val) 		dmg = max_val;
		else if(dmg < min_val) 	dmg = min_val;
		
		if(eff_id > 0)
			SC_SkillSound.sendAndBroadcast(defender, defender.getId(), eff_id);
		
		if(MJCommons.isUnbeatable(defender))
			return 0D;
		
		if(!defender.hasSkillEffect(L1SkillId.WIND_SHACKLE)){
			int time = _rnd.nextInt(5);
			if(time > 0){
				defender.setSkillEffect(L1SkillId.WIND_SHACKLE, time * 1000);
				SC_SkillIconWindShackle.send(defender, defender.getId(), time);
			}
		}
		return dmg;
	}
}
