package l1j.server.ItemSkill.Model.Attack;

import l1j.server.commons;
import l1j.server.ItemSkill.Model.ItemSkillModel;
import l1j.server.configure.Config;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.skill.SkillId;
import l1j.server.server.serverpackets.S_ServerMessage;
import l1j.server.server.serverpackets.S_SkillSound;
import l1j.server.server.utils.EffectUtil;

public class Melee_Weapon_DiceDagger extends ItemSkillModel {
	public double get(L1Character attacker, L1Character defender, ItemInstance t_item, double dwd) {
		
		// 스킬 확률 판별 , 스킬 상태 유무 
		if(!isProbability(attacker, t_item) || commons.isLock(defender)) {
			return 0.0D;
		}
		int hp = defender.getCurrentHp();
		if (hp <= 0) {
			return 0.0D;
		}
		double dmg = hp;
		// 마법 판별
		if(is_magic) {		
			dmg /= 2;	
			remove(attacker, t_item);
		}
		// 아머 브레이크 데미지 처리 
		if(defender instanceof L1PcInstance) {
			L1PcInstance targetPc = (L1PcInstance) defender;
			if (targetPc != null && targetPc.hasSkillEffect(SkillId.아머_브레이크)) {
				dmg *= Config.아머_브레이크;
			}
		}	
		// 카운터 매직 처리 
		if(is_counter) {
			if(defender.hasSkillEffect(SkillId.카운터_매직)) {
				defender.removeSkillEffect(SkillId.카운터_매직);
				S_SkillSound s = S_SkillSound.get(defender.getId(), EffectUtil.카운터_매직);
				defender.sendPackets(s, false);
				defender.broadcastPacket(s);
				dmg = 0;
				remove(attacker, t_item);
			}
		}	
		// 타겟의 데미지 리덕션 처리 
		if(defender instanceof L1PcInstance) {
			L1PcInstance targetPc = (L1PcInstance) defender;
			
			if(targetPc.hasSkillEffect(SkillId.리덕션_아머)) {
				int targetLevel = targetPc.getLevel() < 50 ? 50 : targetPc.getLevel();
				dmg -= (targetLevel - 50) / 5 + 1;
			}		
		    dmg -= targetPc.getDamageReduction();
		    
			if (targetPc != null && targetPc.hasSkillEffect(SkillId.이뮨_투함)) {
				dmg *= Config.IMMNUE_TO_HARM_RATE;
			}		
		}
		return dmg;
	}

	private void remove(L1Character c, ItemInstance item) {
		c.sendPackets(new S_ServerMessage(158, item.getLogName()));
		c.getInventory().removeItem(item, 1);
	}
}
