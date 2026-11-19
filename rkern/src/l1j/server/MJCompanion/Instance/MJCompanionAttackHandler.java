package l1j.server.MJCompanion.Instance;

import static l1j.server.server.model.character.skill.L1SkillId.ABSOLUTE_BARRIER;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import static l1j.server.server.model.character.skill.L1SkillId.DRAGON_SKIN;
import static l1j.server.server.model.character.skill.L1SkillId.EARTH_BIND;
import static l1j.server.server.model.character.skill.L1SkillId.ICE_LANCE;
import static l1j.server.server.model.character.skill.L1SkillId.IllUSION_AVATAR;
import static l1j.server.server.model.character.skill.L1SkillId.PHANTASM;
import static l1j.server.server.model.character.skill.L1SkillId.REDUCTION_ARMOR;

import l1j.server.Config;
import l1j.server.MJ3SEx.EActionCodes;
import l1j.server.MJCompanion.CompanionSettings;
import l1j.server.MJCompanion.Basic.Skills.MJCompanionSkillEffect;
import l1j.server.MJCompanion.Basic.Skills.MJCompanionSkillInfo;
import l1j.server.MJPassiveSkill.MJPassiveID;
import l1j.server.MJTemplate.MJRnd;
import l1j.server.server.model.character.action.CAttackAction.AttackTargetData;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;

import l1j.server.server.utils.IntRange;

public final class MJCompanionAttackHandler {

	public static int do_calculate_hit(AttackTargetData attack_object, L1Character attacker, L1Character target) {
		if(attacker instanceof L1CompanionInstance){
			return do_calculate_attacker_hit(attack_object, (L1CompanionInstance)attacker, target);
		}
		return 0;
	}


	public static void on_hit_notify(AttackTargetData attack_object, L1Character attacker, L1Character target, boolean is_hit) {
		if(attacker instanceof L1CompanionInstance){
			L1CompanionInstance companion = (L1CompanionInstance)attacker;
			if(is_hit && companion.get_is_combo_time()){
				companion.increase_combo_count();
			}else{
				companion.set_combo_count(0);
			}
		}
	}

	public static int do_calculate_damage(AttackTargetData attack_object, L1Character attacker, L1Character target) {
		int damage = 0;
		if(attacker instanceof L1CompanionInstance){
			L1CompanionInstance companion = (L1CompanionInstance)attacker;
			damage = do_calculate_attacker_damage(attack_object, companion, target);
			companion.on_attacked();
		}
		return damage;
	}
	
	private static int do_calculate_attacker_hit(AttackTargetData attack_object, L1CompanionInstance attacker, L1Character target){
		if(!(target instanceof L1MonsterInstance) && target.getZoneType() == -1)
			return 0;
		
		double hit = attacker.getLevel() * CompanionSettings.HIT_BY_LEVEL;
		hit += ((attacker.getLevel() - target.getLevel()) * CompanionSettings.HIT_BY_LEVEL_DIFF);
		hit += attacker.get_melee_hit();

		if(target instanceof L1PcInstance){
			hit += (target.getAC().getAc() * 0.01);	
		}
		
		double target_dodge = target.getDg();
		if(target_dodge > 0){
			target_dodge = (target_dodge * (Config.PER_DODGE * 0.02D));
			hit -= (hit * target_dodge * 0.01D);
		}
		if(target.hasSkillEffect(L1SkillId.ANTA_MAAN) || target.hasSkillEffect(L1SkillId.BIRTH_MAAN) || target.hasSkillEffect(L1SkillId.SHAPE_MAAN)){
			if(MJRnd.isWinning(100, 15))
				hit -= 5;
		}

		return IntRange.ensure((int)Math.round(hit), CompanionSettings.MINIMUM_HIT, CompanionSettings.MAXIMUM_HIT);
	}
	
	private static int do_calculate_attacker_damage(AttackTargetData attack_object, L1CompanionInstance attacker, L1Character target){
		if (target.hasSkillEffect(ABSOLUTE_BARRIER) ||
				target.hasSkillEffect(ICE_LANCE) || 
				target.hasSkillEffect(EARTH_BIND)) 
			return 0;

		double dmg = attacker.getLevel() * CompanionSettings.DMG_BY_LEVEL;
		dmg += ((attacker.getLevel() - target.getLevel()) * CompanionSettings.DMG_BY_LEVEL_DIFF);
		
		MJCompanionSkillEffect sInfo = get_damage_effect(attack_object, attacker, target);
		int melee = sInfo.get_melee_dmg();
		if(melee > 0 && sInfo.get_melee_cri_hit() > 0 && MJRnd.isWinning(100, sInfo.get_melee_cri_hit())){
			attack_object.set_is_critical(true);
			attack_object.setActId(EActionCodes.alt_attack.toInt());
			dmg += (melee * CompanionSettings.MAGNIFICATION_BY_CRITICAL);
		}else{
			dmg += melee;
		}
		if(attacker.get_is_combo_time()){
			if(attacker.get_combo_count() >= 3){
				attack_object.set_is_critical(true);
				attack_object.setActId(EActionCodes.alt_attack.toInt());
				dmg += (melee * CompanionSettings.MAGNIFICATION_BY_COMBO) + Math.max(1, sInfo.get_combo_dmg_multi() > 0 ? MJRnd.next(sInfo.get_combo_dmg_multi()) : 0);
				target.send_effect(17326);
				attacker.set_combo_count(0);
			}
		}else{
			attacker.set_combo_count(0);
		}
		if(melee > 0 && sInfo.get_blood_suck_hit() > 0 && sInfo.get_blood_suck_heal() > 0 && MJRnd.isWinning(100, sInfo.get_blood_suck_hit())){
			int heal = Math.max(MJRnd.next(attacker.get_blood_suck_heal()), 1);
			attacker.setCurrentHp(attacker.getCurrentHp() + heal);
			dmg += heal;
		}
		if(sInfo.get_spell_dmg_add() > 0){
			double mr_reduction = ((double)target.getResistance().getMrAfterEraseRemove() * CompanionSettings.REDUCTION_BY_MR);
			dmg += (Math.max(1, sInfo.get_spell_dmg_add() - mr_reduction));
		}
		if(sInfo.get_spell_dmg_multi() > 0){
			double mr_reduction = ((double)target.getResistance().getMrAfterEraseRemove() * CompanionSettings.REDUCTION_BY_MR);
			dmg += (Math.max(1, sInfo.get_spell_dmg_add() - mr_reduction));			
		}
		if(sInfo.get_fire_elemental_dmg() > 0){
			double elemental_reduction = ((double)target.getResistance().getFire() * CompanionSettings.REDUCTION_BY_ELEMENTAL);
			dmg += (Math.max(1, sInfo.get_fire_elemental_dmg() - elemental_reduction));						
		}
		if(sInfo.get_water_elemental_dmg() > 0){
			double elemental_reduction = ((double)target.getResistance().getWater() * CompanionSettings.REDUCTION_BY_ELEMENTAL);
			dmg += (Math.max(1, sInfo.get_water_elemental_dmg() - elemental_reduction));									
		}
		if(sInfo.get_air_elemental_dmg() > 0){
			double elemental_reduction = ((double)target.getResistance().getWind() * CompanionSettings.REDUCTION_BY_ELEMENTAL);
			dmg += (Math.max(1, sInfo.get_air_elemental_dmg() - elemental_reduction));									
		}
		if(sInfo.get_earth_elemental_dmg() > 0){
			double elemental_reduction = ((double)target.getResistance().getEarth() * CompanionSettings.REDUCTION_BY_ELEMENTAL);
			dmg += (Math.max(1, sInfo.get_earth_elemental_dmg() - elemental_reduction));									
		}
		if(sInfo.get_light_elemental_dmg() > 0){
			double elemental_reduction = ((double)target.getResistance().getWind() * CompanionSettings.REDUCTION_BY_ELEMENTAL);
			dmg += (Math.max(1, sInfo.get_light_elemental_dmg() - elemental_reduction));									
		}

		double reduction = 0;
		if(target instanceof L1PcInstance){
			reduction = Math.max(do_calculate_damage_reduction(attack_object, (L1PcInstance)target, dmg) - sInfo.get_ignore_reduction(), 0);
			dmg -= reduction;
			dmg = dmg * CompanionSettings.MAGNIFICATION_BY_PET_TO_PC;
			dmg += attacker.get_pvp_dmg_ratio();
		}
		
		if(sInfo.get_attacker_effect_id() > 0)
			SC_SkillSound.broadcast(attacker, attacker.getId(), sInfo.get_attacker_effect_id());
		if(sInfo.get_target_effect_id() > 0)
			SC_SkillSound.broadcast(target, target.getId(), sInfo.get_target_effect_id());
			
		int r = MJRnd.next(5);
		return IntRange.ensure((int)Math.round(dmg), r + CompanionSettings.MINIMUM_DMG, (MJRnd.isBoolean() ? r : -r) + CompanionSettings.MAXIMUM_HIT);
	}
	
	private static MJCompanionSkillEffect get_damage_effect(AttackTargetData attack_object, L1CompanionInstance attacker, L1Character target){
		MJCompanionSkillInfo sInfo = MJRnd.isWinning(100, attacker.get_spell_hit()) ? attacker.select_exercise_skills() : null;
		if(sInfo == null){
			return MJCompanionSkillEffect.newInstance()
					.set_melee_dmg(attacker.get_melee_dmg())
					.set_melee_cri_hit(attacker.get_melee_cri_hit())
					.set_ignore_reduction(attacker.get_ignore_reduction())
					.set_blood_suck_hit(attacker.get_blood_suck_hit())
					.set_blood_suck_heal(attacker.get_blood_suck_heal())
					.set_spell_dmg_multi(attacker.get_spell_dmg_multi())
					.set_fire_elemental_dmg(attacker.get_fire_elemental_dmg())
					.set_water_elemental_dmg(attacker.get_water_elemental_dmg())
					.set_air_elemental_dmg(attacker.get_air_elemental_dmg())
					.set_earth_elemental_dmg(attacker.get_earth_elemental_dmg())
					.set_light_elemental_dmg(attacker.get_light_elemental_dmg())
					.set_combo_dmg_multi(attacker.get_combo_dmg())
					.set_attacker_effect_id(0)
					.set_target_effect_id(0);
		}else{
			return MJCompanionSkillEffect.newInstance()
					.set_melee_dmg(attacker.get_melee_dmg() + sInfo.get_melee_dmg())
					.set_melee_cri_hit(attacker.get_melee_cri_hit() + sInfo.get_melee_cri_hit())
					.set_ignore_reduction(attacker.get_ignore_reduction() + sInfo.get_ignore_reduction())
					.set_blood_suck_hit(attacker.get_blood_suck_hit() + sInfo.get_blood_suck_hit())
					.set_blood_suck_heal(attacker.get_blood_suck_heal() + sInfo.get_blood_suck_heal())
					.set_spell_dmg_multi(attacker.get_spell_dmg_multi() + sInfo.get_spell_dmg_multi())
					.set_fire_elemental_dmg(attacker.get_fire_elemental_dmg() + sInfo.get_fire_elemental_dmg())
					.set_water_elemental_dmg(attacker.get_water_elemental_dmg() + sInfo.get_water_elemental_dmg())
					.set_air_elemental_dmg(attacker.get_air_elemental_dmg() + sInfo.get_air_elemental_dmg())
					.set_earth_elemental_dmg(attacker.get_earth_elemental_dmg() + sInfo.get_earth_elemental_dmg())
					.set_light_elemental_dmg(attacker.get_light_elemental_dmg() + sInfo.get_light_elemental_dmg())
					.set_combo_dmg_multi(attacker.get_combo_dmg() + sInfo.get_combo_dmg_multi())
					.set_attacker_effect_id(sInfo.get_attacker_effect_id())
					.set_target_effect_id(sInfo.get_target_effect_id());
		}
	}
	
	private static double do_calculate_damage_reduction(AttackTargetData attack_object, L1PcInstance target, double source_damage){
		int reduction = 0;

		reduction += target.getDamageReductionByArmor();
		reduction += target.getDamageReduction();
		if (target.hasSkillEffect(L1SkillId.메티스축복주문서)) {
			reduction += 3;
		}
		// 전사스킬 : 아머가드 - 캐릭의 AC/10의 데미지감소 효과를 얻는다.
		if (target.getPassive(MJPassiveID.ARMOR_GUARD.toInt()) != null) {
			reduction += (target.getAC().getAc() / 10);
		}

		if (target.hasSkillEffect(REDUCTION_ARMOR)) {
			int lvl = Math.max(50, target.getLevel());
			reduction += (lvl - 50) / 5 + 1;
		}

		if (target.hasSkillEffect(L1SkillId.MAJESTY)) {
			/*원본
			int lvl = Math.max(60, target.getLevel());
			if(lvl < 60){
				lvl = 60;
			}
			int r = (lvl - 60) / 2 + 1;
			reduction += r;
			*/
			int lvl = Math.max(50, target.getLevel());
			reduction += (lvl - 50) / 5 + 1;
		}
		/*수호성 아툰 추가하기
		if(target.getAccount().getTree_cri() == 1){
			
		}
		*/
		if (target.hasSkillEffect(DRAGON_SKIN)) {
			if (target.getLevel() >= 80) {
				reduction += (5 + ((target.getLevel() - 78) / 2));
			} else {
				reduction += 5;
			}
		}

		if (target.hasSkillEffect(IllUSION_AVATAR)) {
			reduction += (source_damage / 5);
		}
		if (target.hasSkillEffect(PHANTASM)) {
			target.removeSkillEffect(PHANTASM);
		}
		return reduction;
	}

}
