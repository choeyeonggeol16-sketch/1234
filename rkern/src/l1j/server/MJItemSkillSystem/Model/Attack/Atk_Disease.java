package l1j.server.MJItemSkillSystem.Model.Attack;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharAttrDef;

import l1j.server.MJItemSkillSystem.Model.MJItemSkillModel;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;

import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus;

import l1j.server.server.utils.MJCommons;

public class Atk_Disease extends MJItemSkillModel{
	@Override
	public double get(L1Character attacker, L1Character defender, L1ItemInstance t_item, double dwd) {
		//버프가 상대편에게 들어가있으면 리턴 0
		/*if(defender.hasSkillEffect(L1SkillId.DISEASE))
			return 0D;*/
		
		if(MJCommons.isUnbeatable(defender) || !isPlay(attacker, t_item))
			return 0D;
		
		int pure = getProbability(attacker, t_item);
		if(is_magic){
			if(!MJCommons.isMagicSuccess(attacker, defender, pure))
				return 0D;
			
			if(isCounterMagic(defender))
				return 0D;
		}else{
			if(!isInPercent(pure))
				return 0D;
		}
		double time =  _rnd.nextInt(max_val - min_val) + min_val;
		time 		= calcEnchant(t_item, time);
		time		= calcAttr(defender, time);
		time		= calcStat(attacker, time);
		int t		= (int)time;
		
		if(defender.hasSkillEffect(L1SkillId.DISEASE))
			defender.removeSkillEffect(L1SkillId.DISEASE);
		
		defender.addHitup(-6);
		defender.getAC().addAc(12);
		if(defender instanceof L1PcInstance)
			SC_OwnCharAttrDef.send((L1PcInstance)defender);
		defender.setSkillEffect(L1SkillId.DISEASE, 64000);
		if(eff_id > 0)
			SC_SkillSound.sendAndBroadcast(defender, defender.getId(), eff_id);
		return 0D;
	}
}
