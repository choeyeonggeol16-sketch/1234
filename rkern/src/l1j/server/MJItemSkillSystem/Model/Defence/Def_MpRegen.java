package l1j.server.MJItemSkillSystem.Model.Defence;

import l1j.server.MJItemSkillSystem.Model.MJItemSkillModel;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;

import l1j.server.server.utils.MJCommons;

public class Def_MpRegen extends MJItemSkillModel{
	@Override
	public double get(L1Character attacker, L1Character defender, L1ItemInstance t_item, double dwd) {
		if(MJCommons.isBlessed(defender) || MJCommons.isUnbeatable(defender) || !isPlay(defender, t_item))
			return 0D;
		
		if(!(defender instanceof L1PcInstance))
			return 0D;
		
		L1PcInstance def = (L1PcInstance)defender;
		double mp = 0;
		
		try{
			mp = MJCommons.getClassLindBlessing(def.getType());
		}catch(Exception e){
			e.printStackTrace();
		}
		
		
		if(mp < min_val)		
			mp = min_val;
		
		mp		= calcEnchant(t_item, mp);
		mp		= calcAttr(defender, mp);
		mp		= calcStat(defender, mp);
		if(mp > max_val)	
			mp = max_val;
		else if(mp < min_val)
			mp = min_val;
		
		defender.setCurrentMp(defender.getCurrentMp() + (int)mp);
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
