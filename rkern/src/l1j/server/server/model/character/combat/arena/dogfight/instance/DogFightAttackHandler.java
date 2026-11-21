package l1j.server.server.model.character.combat.arena.dogfight.instance;

import l1j.server.server.model.character.combat.arena.dogfight.DogFightSettings;
import l1j.server.server.model.character.combat.arena.dogfight.skills.DogFightSkill;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.character.action.CAttackAction.AttackTargetData;
import l1j.server.server.shared.GameRandom;

public final class DogFightAttackHandler {

	public static int do_calculate_hit(AttackTargetData attack_object, L1Character attacker, L1Character target) {
		if(attacker instanceof DogFightInstance && target instanceof DogFightInstance){
			return ((DogFightInstance) attacker).get_next_hit();
		}
		return 0;
	}

	public static void on_hit_notify(AttackTargetData attack_object, L1Character attacker, L1Character target, boolean is_hit) {
		
	}

	public static int do_calculate_damage(AttackTargetData attack_object, L1Character attacker, L1Character target) {
		if(attacker instanceof DogFightInstance && target instanceof DogFightInstance){
			int added_percent = 100 - (int)attacker.getCurrentHpPercent();
			
			if(attacker instanceof DogFightInstance && target instanceof DogFightInstance){
				DogFightInstance npc = (DogFightInstance) attacker;
				DogFightInstance target_npc = (DogFightInstance) target;
				//System.out.println("공격자 : " + npc.getTotalPrice() + " || 타겟 : " + target_npc.getTotalPrice());
				if(npc.getTotalPrice() > target_npc.getTotalPrice()) {
					added_percent -= DogFightSettings.FAKE_SKILL_RATE;
				}
			}
			
			if(GameRandom.isWinning(100, DogFightSettings.ON_SKILL_PERCENT + added_percent)){
				DogFightSkill skill = DogFightSkill.select_skill();
				attack_object.set_is_critical(true);
				if(skill.get_attacker_action() > 0)
					attack_object.setActId(skill.get_attacker_action());
				if(skill.get_attacker_effect() > 0)
					attacker.send_effect(skill.get_attacker_effect());
				if(skill.get_target_effect() > 0)
					target.send_effect(skill.get_target_effect());
				return GameRandom.next(skill.get_min_dmg(), skill.get_max_dmg());
			}

			return ((DogFightInstance) attacker).get_next_damage();
		}
		return 0;
	}
}
