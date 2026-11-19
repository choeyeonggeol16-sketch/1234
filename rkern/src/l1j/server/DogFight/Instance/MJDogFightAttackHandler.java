package l1j.server.DogFight.Instance;

import l1j.server.DogFight.MJDogFightSettings;
import l1j.server.DogFight.Skills.MJDogFightSkill;
import l1j.server.MJTemplate.MJRnd;
import l1j.server.server.model.character.action.CAttackAction.AttackTargetData;
import l1j.server.server.model.L1Character;

public final class MJDogFightAttackHandler {

	public static int do_calculate_hit(AttackTargetData attack_object, L1Character attacker, L1Character target) {
		if(attacker instanceof MJDogFightInstance && target instanceof MJDogFightInstance){
			return ((MJDogFightInstance) attacker).get_next_hit();
		}
		return 0;
	}

	public static void on_hit_notify(AttackTargetData attack_object, L1Character attacker, L1Character target, boolean is_hit) {
		
	}

	public static int do_calculate_damage(AttackTargetData attack_object, L1Character attacker, L1Character target) {
		if(attacker instanceof MJDogFightInstance && target instanceof MJDogFightInstance){
			int added_percent = 100 - (int)attacker.getCurrentHpPercent();
			
			if(attacker instanceof MJDogFightInstance && target instanceof MJDogFightInstance){
				MJDogFightInstance npc = (MJDogFightInstance) attacker;
				MJDogFightInstance target_npc = (MJDogFightInstance) target;
				//System.out.println("공격자 : " + npc.getTotalPrice() + " || 타겟 : " + target_npc.getTotalPrice());
				if(npc.getTotalPrice() > target_npc.getTotalPrice()) {
					added_percent -= MJDogFightSettings.FAKE_SKILL_RATE;
				}
			}
			
			if(MJRnd.isWinning(100, MJDogFightSettings.ON_SKILL_PERCENT + added_percent)){
				MJDogFightSkill skill = MJDogFightSkill.select_skill();
				attack_object.set_is_critical(true);
				if(skill.get_attacker_action() > 0)
					attack_object.setActId(skill.get_attacker_action());
				if(skill.get_attacker_effect() > 0)
					attacker.send_effect(skill.get_attacker_effect());
				if(skill.get_target_effect() > 0)
					target.send_effect(skill.get_target_effect());
				return MJRnd.next(skill.get_min_dmg(), skill.get_max_dmg());
			}

			return ((MJDogFightInstance) attacker).get_next_damage();
		}
		return 0;
	}
}
