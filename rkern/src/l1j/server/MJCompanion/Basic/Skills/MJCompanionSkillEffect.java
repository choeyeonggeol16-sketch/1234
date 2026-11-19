package l1j.server.MJCompanion.Basic.Skills;

public class MJCompanionSkillEffect {
	public static MJCompanionSkillEffect newInstance(){
		return new MJCompanionSkillEffect();
	}
	private int m_melee_dmg;
	private int m_melee_hit;
	private int m_melee_cri_hit;
	private int m_ignore_reduction;
	private int m_blood_suck_hit;
	private int m_blood_suck_heal;
	private int m_regen_hp;
	private int m_ac;
	private int m_mr;
	private int m_potion_hp;
	private int m_dmg_reduction;
	private int m_max_hp;
	private int m_spell_dmg_multi;
	private int m_spell_hit;
	private double m_move_delay_reduce;
	private double m_attack_delay_reduce;
	private int m_fire_elemental_dmg;
	private int m_water_elemental_dmg;
	private int m_air_elemental_dmg;
	private int m_earth_elemental_dmg;
	private int m_light_elemental_dmg;
	private int m_combo_dmg_multi;
	private int m_spell_dmg_add;
	private int m_attacker_effect_id;
	private int m_target_effect_id;
	private MJCompanionSkillEffect(){	
	}
	public MJCompanionSkillEffect add_melee_dmg(int melee_dmg){
		m_melee_dmg += melee_dmg;
		return this;
	}
	public MJCompanionSkillEffect add_melee_hit(int melee_hit){
		m_melee_hit += melee_hit;
		return this;
	}
	public MJCompanionSkillEffect add_melee_cri_hit(int melee_cri_hit){
		m_melee_cri_hit += melee_cri_hit;
		return this;
	}
	public MJCompanionSkillEffect add_ignore_reduction(int ignore_reduction){
		m_ignore_reduction += ignore_reduction;
		return this;
	}
	public MJCompanionSkillEffect add_blood_suck_hit(double blood_suck_hit){
		m_blood_suck_hit += blood_suck_hit;
		return this;
	}
	public MJCompanionSkillEffect add_blood_suck_heal(int blood_suck_heal){
		m_blood_suck_heal += blood_suck_heal;
		return this;
	}
	public MJCompanionSkillEffect add_regen_hp(int regen_hp){
		m_regen_hp += regen_hp;
		return this;
	}
	public MJCompanionSkillEffect add_ac(int ac){
		m_ac += ac;
		return this;
	}
	public MJCompanionSkillEffect add_mr(int mr){
		m_mr += mr;
		return this;
	}
	public MJCompanionSkillEffect add_potion_hp(int potion_hp){
		m_potion_hp += potion_hp;
		return this;
	}
	public MJCompanionSkillEffect add_dmg_reduction(int dmg_reduction){
		m_dmg_reduction += dmg_reduction;
		return this;
	}
	public MJCompanionSkillEffect add_max_hp(int max_hp){
		m_max_hp += max_hp;
		return this;
	}
	public MJCompanionSkillEffect add_spell_dmg_multi(int spell_dmg_multi){
		m_spell_dmg_multi += spell_dmg_multi;
		return this;
	}
	public MJCompanionSkillEffect add_spell_hit(int spell_hit){
		m_spell_hit += spell_hit;
		return this;
	}
	public MJCompanionSkillEffect add_move_delay_reduce(double move_delay_reduce){
		m_move_delay_reduce += move_delay_reduce;
		return this;
	}
	public MJCompanionSkillEffect add_attack_delay_reduce(double attack_delay_reduce){
		m_attack_delay_reduce += attack_delay_reduce;
		return this;
	}
	public MJCompanionSkillEffect add_fire_elemental_dmg(int fire_elemental_dmg){
		m_fire_elemental_dmg += fire_elemental_dmg;
		return this;
	}
	public MJCompanionSkillEffect add_water_elemental_dmg(int water_elemental_dmg){
		m_water_elemental_dmg += water_elemental_dmg;
		return this;
	}
	public MJCompanionSkillEffect add_air_elemental_dmg(int air_elemental_dmg){
		m_air_elemental_dmg += air_elemental_dmg;
		return this;
	}
	public MJCompanionSkillEffect add_earth_elemental_dmg(int earth_elemental_dmg){
		m_earth_elemental_dmg += earth_elemental_dmg;
		return this;
	}
	public MJCompanionSkillEffect add_light_elemental_dmg(int light_elemental_dmg){
		m_light_elemental_dmg += light_elemental_dmg;
		return this;
	}
	public MJCompanionSkillEffect add_combo_dmg_multi(int combo_dmg_multi){
		m_combo_dmg_multi += combo_dmg_multi;
		return this;
	}
	public MJCompanionSkillEffect add_spell_dmg_add(int spell_dmg_add){
		m_spell_dmg_add += spell_dmg_add;
		return this;
	}
	public MJCompanionSkillEffect set_melee_dmg(int melee_dmg){
		m_melee_dmg = melee_dmg;
		return this;
	}
	public MJCompanionSkillEffect set_melee_hit(int melee_hit){
		m_melee_hit = melee_hit;
		return this;
	}
	public MJCompanionSkillEffect set_melee_cri_hit(int melee_cri_hit){
		m_melee_cri_hit = melee_cri_hit;
		return this;
	}
	public MJCompanionSkillEffect set_ignore_reduction(int ignore_reduction){
		m_ignore_reduction = ignore_reduction;
		return this;
	}
	public MJCompanionSkillEffect set_blood_suck_hit(int blood_suck_hit){
		m_blood_suck_hit = blood_suck_hit;
		return this;
	}
	public MJCompanionSkillEffect set_blood_suck_heal(int blood_suck_heal){
		m_blood_suck_heal = blood_suck_heal;
		return this;
	}
	public MJCompanionSkillEffect set_regen_hp(int regen_hp){
		m_regen_hp = regen_hp;
		return this;
	}
	public MJCompanionSkillEffect set_ac(int ac){
		m_ac = ac;
		return this;
	}
	public MJCompanionSkillEffect set_mr(int mr){
		m_mr = mr;
		return this;
	}
	public MJCompanionSkillEffect set_potion_hp(int potion_hp){
		m_potion_hp = potion_hp;
		return this;
	}
	public MJCompanionSkillEffect set_dmg_reduction(int dmg_reduction){
		m_dmg_reduction = dmg_reduction;
		return this;
	}
	public MJCompanionSkillEffect set_max_hp(int max_hp){
		m_max_hp = max_hp;
		return this;
	}
	public MJCompanionSkillEffect set_spell_dmg_multi(int spell_dmg_multi){
		m_spell_dmg_multi = spell_dmg_multi;
		return this;
	}
	public MJCompanionSkillEffect set_spell_hit(int spell_hit){
		m_spell_hit = spell_hit;
		return this;
	}
	public MJCompanionSkillEffect set_move_delay_reduce(double move_delay_reduce){
		m_move_delay_reduce = move_delay_reduce;
		return this;
	}
	public MJCompanionSkillEffect set_attack_delay_reduce(double attack_delay_reduce){
		m_attack_delay_reduce = attack_delay_reduce;
		return this;
	}
	public MJCompanionSkillEffect set_fire_elemental_dmg(int fire_elemental_dmg){
		m_fire_elemental_dmg = fire_elemental_dmg;
		return this;
	}
	public MJCompanionSkillEffect set_water_elemental_dmg(int water_elemental_dmg){
		m_water_elemental_dmg = water_elemental_dmg;
		return this;
	}
	public MJCompanionSkillEffect set_air_elemental_dmg(int air_elemental_dmg){
		m_air_elemental_dmg = air_elemental_dmg;
		return this;
	}
	public MJCompanionSkillEffect set_earth_elemental_dmg(int earth_elemental_dmg){
		m_earth_elemental_dmg = earth_elemental_dmg;
		return this;
	}
	public MJCompanionSkillEffect set_light_elemental_dmg(int light_elemental_dmg){
		m_light_elemental_dmg = light_elemental_dmg;
		return this;
	}
	public MJCompanionSkillEffect set_combo_dmg_multi(int combo_dmg_multi){
		m_combo_dmg_multi = combo_dmg_multi;
		return this;
	}
	public MJCompanionSkillEffect set_spell_dmg_add(int spell_dmg_add){
		m_spell_dmg_add = spell_dmg_add;
		return this;
	}
	public MJCompanionSkillEffect set_attacker_effect_id(int attacker_effect_id){
		m_attacker_effect_id = attacker_effect_id;
		return this;
	}
	public MJCompanionSkillEffect set_target_effect_id(int target_effect_id){
		m_target_effect_id = target_effect_id;
		return this;
	}
	public int get_melee_dmg(){
		return m_melee_dmg;
	}
	public int get_melee_hit(){
		return m_melee_hit;
	}
	public int get_melee_cri_hit(){
		return m_melee_cri_hit;
	}
	public int get_ignore_reduction(){
		return m_ignore_reduction;
	}
	public int get_blood_suck_hit(){
		return m_blood_suck_hit;
	}
	public int get_blood_suck_heal(){
		return m_blood_suck_heal;
	}
	public int get_regen_hp(){
		return m_regen_hp;
	}
	public int get_ac(){
		return m_ac;
	}
	public int get_mr(){
		return m_mr;
	}
	public int get_potion_hp(){
		return m_potion_hp;
	}
	public int get_dmg_reduction(){
		return m_dmg_reduction;
	}
	public int get_max_hp(){
		return m_max_hp;
	}
	public int get_spell_dmg_multi(){
		return m_spell_dmg_multi;
	}
	public int get_spell_hit(){
		return m_spell_hit;
	}
	public double get_move_delay_reduce(){
		return m_move_delay_reduce;
	}
	public double get_attack_delay_reduce(){
		return m_attack_delay_reduce;
	}
	public int get_fire_elemental_dmg(){
		return m_fire_elemental_dmg;
	}
	public int get_water_elemental_dmg(){
		return m_water_elemental_dmg;
	}
	public int get_air_elemental_dmg(){
		return m_air_elemental_dmg;
	}
	public int get_earth_elemental_dmg(){
		return m_earth_elemental_dmg;
	}
	public int get_light_elemental_dmg(){
		return m_light_elemental_dmg;
	}
	public int get_combo_dmg_multi(){
		return m_combo_dmg_multi;
	}
	public int get_spell_dmg_add(){
		return m_spell_dmg_add;
	}
	public int get_attacker_effect_id(){
		return m_attacker_effect_id;
	}
	public int get_target_effect_id(){
		return m_target_effect_id;
	}
}
