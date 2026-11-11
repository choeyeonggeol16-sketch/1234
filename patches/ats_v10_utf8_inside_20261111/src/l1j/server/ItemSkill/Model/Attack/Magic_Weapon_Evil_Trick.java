package l1j.server.ItemSkill.Model.Attack;

import I1j.server.server.model.Thread.EvilTrickTimer;
import l1j.server.commons;
import l1j.server.ItemSkill.Model.ItemSkillModel;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.skill.SkillId;
import l1j.server.server.serverpackets.S_SkillSound;
import l1j.server.server.utils.EffectUtil;
import l1j.server.server.utils.SkillUtil;

public class Magic_Weapon_Evil_Trick extends ItemSkillModel {
	public double get(L1Character attacker, L1Character defender, ItemInstance t_item, double dwd) {
		
		// 스킬 확률 , 스킬 효과, 중복 방지
		if(!isProbability(attacker, t_item) || commons.isLock(defender)) {
			return 0.0D;
		}
		if(!defender.hasSkillEffect(SkillId.이블_트릭_딜레이)) { 
			EvilTrickTimer chaser = new EvilTrickTimer((L1PcInstance) attacker, defender);
			chaser.begin();
			defender.setSkillEffect(SkillId.이블_트릭_딜레이, 5000);
		}
		// 이레이즈 매직 판정
		if(is_erase) {
			SkillUtil.cancelEraseMagic(defender);
		}
		// 카운터 매직 판정
		if(is_counter) {
			if(defender.hasSkillEffect(SkillId.카운터_매직)) {
				defender.removeSkillEffect(SkillId.카운터_매직);
				S_SkillSound s = S_SkillSound.get(defender.getId(), EffectUtil.카운터_매직);
				defender.broadcastPacket(s);
				if (defender instanceof L1PcInstance) {
					L1PcInstance pc = (L1PcInstance) defender;
					pc.sendPackets(s); 
					return 0.0D;
				}
			}
		}
		return 0.0D;
		
	}

}
