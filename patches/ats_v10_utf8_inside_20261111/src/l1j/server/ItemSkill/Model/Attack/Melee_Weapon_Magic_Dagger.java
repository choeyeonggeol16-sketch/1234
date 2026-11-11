package l1j.server.ItemSkill.Model.Attack;

import l1j.server.commons;
import l1j.server.ItemSkill.Model.ItemSkillModel;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.MonsterInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.utils.SkillUtil;

public class Melee_Weapon_Magic_Dagger extends ItemSkillModel {
	public double get(L1Character attacker, L1Character defender, ItemInstance t_item, double dwd) {
		
		double dmg = 0.0D;
		
		// 스킬 상태 유무 
		if(commons.isLock(defender)) {
			return 0.0D;
		}		
		if (is_magic) {	
			// 타겟이 피시 인경우 
			if(defender instanceof L1PcInstance) {
				L1PcInstance targetpc = (L1PcInstance) defender;
				int targetMr = targetpc.getResistance().getMr();
				if(targetMr <= 100) {
					dmg = 1.0D;
				} else {
					dmg = 0;
				}		
			} // 타겟이 몬스터 일 경우 
			else if(defender instanceof MonsterInstance) {
				MonsterInstance targetnpc = (MonsterInstance) defender;
				int targetMr = targetnpc.getResistance().getMr();
				if(targetMr <= 50) {
					dmg = 1.0D;
				} else {
					dmg = 0;
				}		
			}
		}
		// 이레이즈 매직 판정 처리 
		if(is_erase) {
			SkillUtil.cancelEraseMagic(defender);
		}		
		// 버그 체크 
		if(defender.getCurrentMp() < dmg) {
			dmg = defender.getCurrentMp();
		}	
		// 데미지 전달
		defender.setCurrentMp((int) (defender.getCurrentMp() - dmg));
		attacker.setCurrentMp((int) (attacker.getCurrentMp() + dmg));
		return dmg;
	}
}
