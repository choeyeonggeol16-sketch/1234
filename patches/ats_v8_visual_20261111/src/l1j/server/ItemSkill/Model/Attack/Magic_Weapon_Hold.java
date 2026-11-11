package l1j.server.ItemSkill.Model.Attack;

import l1j.server.commons;
import l1j.server.ItemSkill.Model.ItemSkillModel;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.skill.SkillId;
import l1j.server.server.serverpackets.S_SkillSound;
import l1j.server.server.utils.CommonUtil;
import l1j.server.server.utils.SkillUtil;

public class Magic_Weapon_Hold extends ItemSkillModel {
	public double get(L1Character attacker, L1Character defender, ItemInstance t_item, double dwd) {
		// 스킬 확률 판별 , 스킬 상태 유무 
		if(!isProbability(attacker, t_item) || defender.hasSkillEffect(SkillId.상태_동결) || commons.isLock(defender)) {
			return 0.0D;
		}	

		// 카운터 매직 처리
		if(is_counter) {
			if(SkillUtil.isCounterMagic(defender)) {
				return 0.0D;
			}
		}	
		int time = CommonUtil.random(min_dmg) + 1;
		// 마법인지 체크 
		if(is_magic) {
			defender.setSkillEffect(SkillId.상태_동결, time);	
		}
		// 이팩트 처리 
		if (eff_id > 0) {
			broadcast(defender, S_SkillSound.get(defender.getId(), eff_id));	
		}
		defender.sendPackets(commons.on_paralysis, false);
		return 0.0D;
	}
}
