package l1j.server.MJItemSkillSystem.Model.Attack;

import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.MJItemSkillSystem.MJItemSkillModelLoader;
import l1j.server.MJItemSkillSystem.Model.MJItemSkillModel;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.character.skill.L1SkillId;

import l1j.server.server.utils.MJCommons;

public class Atk_NormalAttack extends MJItemSkillModel{

	@Override
	public double get(L1Character attacker, L1Character defender, L1ItemInstance t_item, double dwd) {
		if(!isPlay(attacker, t_item)){
			return 0D;
		}
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
		dmg = calcMrDefense(defender.getResistance()
				.getEffectedMrBySkill(), dmg);
		if(dmg > max_val) 		dmg = max_val;
		else if(dmg < min_val) 	dmg = min_val;
		
		if(eff_id > 0)
			if (t_item.getItemId() == 123001 || t_item.getItemId() == 123002 || t_item.getItemId() == 123004
					 || t_item.getItemId() == 123005 || t_item.getItemId() == 123006 || t_item.getItemId() == 123007 || t_item.getItemId() == 123008) {
				SC_SkillSound.sendAndBroadcast(attacker, attacker.getId(), eff_id);
				
			} else {
			SC_SkillSound.sendAndBroadcast(defender, defender.getId(), eff_id);
			}
		if(MJCommons.isUnbeatable(defender))
			return 0D;
		return dmg;
	}

	
	private static Atk_NormalAttack _instance;
	public static Atk_NormalAttack getInstance(){
		if(_instance == null)
			_instance = new Atk_NormalAttack();
		return _instance;
	}
	

}
