package l1j.server.server.model.item.proc.model;

import java.util.Random;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.templates.L1Skills;
import l1j.server.server.utils.GameUtils;

public abstract class ItemSkillModel {
	protected static Random _rnd = new Random(System.nanoTime());
	
	public int 		itemId;			// item id.
	public int		condition;		// condition..
	public int 		d_prob;			// default probability.
	public int 		e_prob;			// enchant probability.
	public int 		s_prob;			// stat probability.
	public double 	s_weight;		// stat probability in weight.
	public int 		min_val;		// min value
	public int 		max_val;		// max value
	public int 		stat_val;		// stat value
	public double	stat_valweight;	// stat value weight.
	public int		e_val;			// enchant value.
	public double	e_valweight;	// enchant value weight.
	public int 		eff_id;			// effect id
	public int		attr_type;		// attribute type
	public boolean	is_magic;		// is magic skill?
	
	/**
	 * t_item is traget item instance.
	 * dwd is default weapon damage.
	 * **/
	public abstract double get(L1Character attacker, L1Character defender, L1ItemInstance t_item, double dwd);
	
	protected boolean isInPercent(int p){
		return (_rnd.nextInt(100) + 1) <= p;
	}
	
	protected int getProbability(L1Character c, L1ItemInstance t_item){
		int prob = d_prob;
		if(condition <= 0) 
			prob += (e_prob * t_item.getEnchantLevel());
		else if(e_prob > 0){
			if(t_item.getEnchantLevel() >= condition){
				int ptmp = (t_item.getEnchantLevel() - condition) + 1;
				prob += (ptmp * e_prob);
			}
		}
		if(s_prob != 0){
			int stat = 0;
			switch(s_prob){
			case 1:	stat = c.GetTotalStatStr(); break;
			case 2:	stat = c.GetTotalStatDex(); break;
			case 3:	stat = c.GetTotalStatCon(); break;
			case 4:	stat = c.GetTotalStatWis(); break;
			case 5:	stat = c.GetTotalStatInt(); break;
			case 6:	stat = c.GetTotalStatCha(); break;
			}
			
			prob += (prob * (stat * s_weight));
		}
		return prob;
	}
	
	protected boolean isPlay(L1Character c, L1ItemInstance t_item){
		return condition <= t_item.getEnchantLevel() && isInPercent(getProbability(c, t_item));
	}
	
	protected double calcStat(L1Character c, double pure_dmg){
		double dmg = pure_dmg;
		if(stat_val != 0){
			int stat = 0;
	
	
			switch(stat_val){
			case 1:	stat = c.GetTotalStatStr(); break;
			case 2:	stat = c.GetTotalStatDex(); break;
			case 3:	stat = c.GetTotalStatCon(); break;
			case 4:	stat = c.GetTotalStatWis(); break;
			case 5:	stat = c.GetTotalStatInt(); break;
			case 6:	stat = c.GetTotalStatCha(); break;

			}
			
			dmg += (dmg * (stat * stat_valweight));
			
		}
		
		return dmg;
	}
	
	protected double calcEnchant(L1ItemInstance item, double pure_dmg){
		double dmg = pure_dmg;
		if(e_val > 0){
			int ech = item.getEnchantLevel();
			if(ech > e_val){
				ech -= e_val + 1;
				dmg += (dmg * (ech * e_valweight));
			}
		}
		return dmg;
	}
	public static double calcMrDefense(int MagicResistance, double dmg) {
		double cc = 0;
		if (MagicResistance <= 19) {
			cc = 0.05;
		} else if (MagicResistance <= 29) {
			cc = 0.07;
		} else if (MagicResistance <= 39) {
			cc = 0.1;
		} else if (MagicResistance <= 49) {
			cc = 0.12;
		} else if (MagicResistance <= 59) {
			cc = 0.17;
		} else if (MagicResistance <= 69) {
			cc = 0.20;
		} else if (MagicResistance <= 79) {
			cc = 0.22;
		} else if (MagicResistance <= 89) {
			cc = 0.25;
		} else if (MagicResistance <= 99) {
			cc = 0.27;
		} else if (MagicResistance <= 110) {
			cc = 0.31;
		} else if (MagicResistance <= 120) {
			cc = 0.32;
		} else if (MagicResistance <= 130) {
			cc = 0.34;
		} else if (MagicResistance <= 140) {
			cc = 0.36;
		} else if (MagicResistance <= 150) {
			cc = 0.38;
		} else if (MagicResistance <= 160) {
			cc = 0.40;
		} else if (MagicResistance <= 170) {
			cc = 0.42;
		} else if (MagicResistance <= 180) {
			cc = 0.44;
		} else if (MagicResistance <= 190) {
			cc = 0.46;
		} else if (MagicResistance <= 200) {
			cc = 0.48;
		} else if (MagicResistance <= 220) {
			cc = 0.49;
		} else {
			cc = 0.51;
		}

		dmg -= dmg * cc;

		if (dmg < 0) {
			dmg = 0;
		}

		return dmg;
	}
	
	protected double calcAttr(L1Character defender, double pure_dmg){
		double dmg = pure_dmg;
		if(defender.IsResistanceActive()){
			switch(attr_type){
			case L1Skills.ATTR_EARTH:
				dmg = GameUtils.getAttrDamage(dmg, defender.GetEarthResist());
				break;
			case L1Skills.ATTR_FIRE:
				dmg = GameUtils.getAttrDamage(dmg, defender.GetFireResist());
				break;
			case L1Skills.ATTR_WATER:
				dmg = GameUtils.getAttrDamage(dmg, defender.GetWaterResist());
				break;
			case L1Skills.ATTR_WIND:
				dmg = GameUtils.getAttrDamage(dmg, defender.GetWindResist());
				break;
			}
		}
		
		return dmg;
	}
	
	
	
	
	
	protected boolean isCounterMagic(L1Character c){
		if(c.hasSkillEffect(L1SkillId.COUNTER_MAGIC)){
			c.removeSkillEffect(L1SkillId.COUNTER_MAGIC);
			SC_SkillSound.sendAndBroadcast(c, c.getId(), 10702);
			return true;
		}
		return false;
	}
	
	public boolean isAttack(){
		return true;
	}
}
