package l1j.server.ItemSkill.Model.Attack;

import l1j.server.commons;
import l1j.server.ItemSkill.Model.ItemSkillModel;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.MonsterInstance;
import l1j.server.server.serverpackets.S_SkillSound;

public class Melee_Magic_Weapon_Tun_Undead extends ItemSkillModel {
	public double get(L1Character attacker, L1Character defender, ItemInstance t_item, double dwd) {
		
		// 스킬 확률 판별 , 스킬 상태 유무 , 몬스터 체크 
		if(!isProbability(attacker, t_item) || commons.isLock(defender) || (!(defender instanceof MonsterInstance))) {
			return 0.0D;
		}
		// 언데드 타입인지 체크 
		MonsterInstance mob = (MonsterInstance) defender;
		int undead = mob.getNpcTemplate().getUndead();
		if ((undead != 1)) {
			return 0.0D;
		}
		//이팩트 처리 
		if (eff_id > 0) {
			broadcast(defender, S_SkillSound.get(defender.getId(), eff_id));
		}
		// 데미지 전달
		mob.receiveDamage(attacker, mob.getCurrentHp());
		return 0.0D;
	}
	
}
