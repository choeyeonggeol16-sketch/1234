package l1j.server.MJItemSkillSystem.Model.Attack;

import java.util.ArrayList;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import java.util.Collection;
import java.util.List;

import l1j.server.MJItemSkillSystem.Model.MJItemSkillModel;
import l1j.server.server.ActionCodes;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.Instance.L1DollInstance;
import l1j.server.server.model.Instance.L1ItemInstance;

import l1j.server.server.utils.MJCommons;

public class Atk_TargetWideAttack extends MJItemSkillModel{

	@Override
	public double get(L1Character attacker, L1Character defender, L1ItemInstance t_item, double dwd) {
		if(!isPlay(attacker, t_item))
			return 0D;
		
		double result = 0D;
		Collection<L1Object> list = attacker.getKnownObjects();
		ArrayList<L1Character> tars = new ArrayList<L1Character>(list.size());
		tars.add(defender);
		for(L1Object obj : list){
			if(obj == null || !(obj instanceof L1Character) || obj instanceof L1DollInstance)
				continue;
			
			L1Character c = (L1Character)obj;
			if(c.getMap().isSafetyZone(c.getLocation()))
	            continue;
			if(c.isDead() || MJCommons.getDistance(defender.getX(), defender.getY(), c.getX(), c.getY()) > 3)
				continue;
			
			double dmg = MJCommons.getMagicDamage(attacker, c, min_val, max_val);
			dmg = calcEnchant(t_item, dmg);
			dmg = calcAttr(c, dmg);
			dmg = calcStat(attacker, dmg);
			if(dmg > max_val) 		dmg = max_val;
			else if(dmg < min_val) 	dmg = min_val;
			
			if(c.getId() == defender.getId()){
				if(!isCounterMagic(c) && !MJCommons.isUnbeatable(c))
					result = dmg;
				continue;
			}else{
				if(!isCounterMagic(c) && !MJCommons.isUnbeatable(c))
					c.receiveDamage(attacker, (int)dmg);
			}
			tars.add(c);
		}
		
//		if(result > 0)
//		else	
		
		
		if(eff_id > 0)//추가
			SC_SkillSound.sendAndBroadcast(defender, defender.getId(), eff_id);//추가
		if(MJCommons.isUnbeatable(defender))//추가
			return 0D;//추가
		return result;
	}
}
