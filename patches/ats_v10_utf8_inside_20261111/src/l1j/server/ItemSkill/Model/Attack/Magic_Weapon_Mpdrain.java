package l1j.server.ItemSkill.Model.Attack;

import l1j.server.commons;
import l1j.server.ItemSkill.Model.ItemSkillModel;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.utils.CommonUtil;
import l1j.server.server.utils.SkillUtil;

public class Magic_Weapon_Mpdrain extends ItemSkillModel {
	public double get(L1Character attacker, L1Character defender, ItemInstance t_item, double dwd) {
		
		double dmg = 0.0D;	
		
		// 스킬 상태 유무 
		if(commons.isLock(defender)) {
			return 0.0D;
		}		
		// 타겟이 mr 체크
		if (is_magic) {	
			dmg = CommonUtil.randomRange(1, 9);		
			
			int targetMr = defender.getResistance().getMr();
		    if(targetMr >= 100) {
		    	dmg /= 2;
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
	
		return 0.0D;
	}
	

}
