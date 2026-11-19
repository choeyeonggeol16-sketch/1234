package l1j.server.MJCompanion.Basic.Skills;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;

import l1j.server.MJTemplate.MJSqlHelper.Executors.Selector;
import l1j.server.MJTemplate.MJSqlHelper.Handler.FullSelectorHandler;


public class MJCompanionSkillInfo {
	private static HashMap<Integer, HashMap<Integer, MJCompanionSkillInfo>> m_companion_skills;
	private static HashMap<Integer, Byte> m_exercise_skills; 
	public static void do_load(){
		final HashMap<Integer, HashMap<Integer, MJCompanionSkillInfo>> companion_skills = new HashMap<Integer, HashMap<Integer, MJCompanionSkillInfo>>();
		final HashMap<Integer, Byte> exercise_skills = new HashMap<Integer, Byte>();
		Selector.exec("select * from companion_skills order by skill_id,enchant asc", new FullSelectorHandler(){

			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()){
					MJCompanionSkillInfo sInfo = newInstance(rs);
					HashMap<Integer, MJCompanionSkillInfo> skills = companion_skills.get(sInfo.get_skill_id());
					if(skills == null){
						skills = new HashMap<Integer, MJCompanionSkillInfo>();
						companion_skills.put(sInfo.get_skill_id(), skills);	
					}
					skills.put(sInfo.get_enchant(), sInfo);
					if(sInfo.get_is_exercise())
						exercise_skills.put(sInfo.get_skill_id(), Byte.MIN_VALUE);
				}
			}
			
		});
		m_companion_skills = companion_skills;
		m_exercise_skills = exercise_skills;
	}
	
	public static MJCompanionSkillInfo get_companion_skills(int skill_id, int enchant_level){
		HashMap<Integer, MJCompanionSkillInfo> skills = m_companion_skills.get(skill_id);
		return skills == null ? null : skills.get(enchant_level);
	}
	
	public static boolean is_exercise_skill(int skill_id){
		return m_exercise_skills.containsKey(skill_id);
	}
	
	private static MJCompanionSkillInfo newInstance(ResultSet rs) throws SQLException{
		return newInstance()
				.set_skill_id(rs.getInt("skill_id"))
				.set_desc_num(rs.getInt("desc_num"))
				.set_desc_view(rs.getString("desc_num_view"))
				.set_is_exercise(rs.getInt("exercise") != 0)
				.set_attacker_effect_id(rs.getInt("attacker_effect_id"))
				.set_target_effect_id(rs.getInt("target_effect_id"))
				.set_enchant(rs.getInt("enchant"))
				.set_melee_dmg(rs.getInt("melee_dmg"))
				.set_melee_hit(rs.getInt("melee_hit"))
				.set_melee_cri_hit(rs.getInt("melee_cri_hit"))
				.set_ignore_reduction(rs.getInt("ignore_reduction"))
				.set_blood_suck_hit(rs.getInt("blood_suck_hit"))
				.set_blood_suck_heal(rs.getInt("blood_suck_heal"))
				.set_regen_hp(rs.getInt("regen_hp"))
				.set_ac(rs.getInt("ac"))
				.set_mr(rs.getInt("mr"))
				.set_potion_hp(rs.getInt("potion_hp"))
				.set_dmg_reduction(rs.getInt("dmg_reduction"))
				.set_max_hp(rs.getInt("max_hp"))
				.set_spell_dmg_multi(rs.getInt("spell_dmg_multi"))
				.set_spell_hit(rs.getInt("spell_hit"))
				.set_move_delay_reduce(Double.parseDouble(rs.getString("move_delay_reduce")))
				.set_attack_delay_reduce(Double.parseDouble(rs.getString("attack_delay_reduce")))
				.set_fire_elemental_dmg(rs.getInt("fire_elemental_dmg"))
				.set_water_elemental_dmg(rs.getInt("water_elemental_dmg"))
				.set_air_elemental_dmg(rs.getInt("air_elemental_dmg"))
				.set_earth_elemental_dmg(rs.getInt("earth_elemental_dmg"))
				.set_light_elemental_dmg(rs.getInt("light_elemental_dmg"))
				.set_combo_dmg_multi(rs.getInt("combo_dmg_multi"))
				.set_spell_dmg_add(rs.getInt("spell_dmg_add"));	
	}
	
	private static MJCompanionSkillInfo newInstance(){
		return new MJCompanionSkillInfo();
	}
	
	private int m_skill_id;
	private int m_desc_num;
	private String m_desc_view;
	private boolean m_is_exercise;
	private int m_attacker_effect_id;
	private int m_target_effect_id;
	private int m_enchant;
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
	
	private MJCompanionSkillInfo(){	
	}
	public MJCompanionSkillInfo set_skill_id(int skill_id){
		m_skill_id = skill_id;
		return this;
	}
	public MJCompanionSkillInfo set_desc_num(int desc_num){
		m_desc_num = desc_num;
		return this;
	}
	public MJCompanionSkillInfo set_desc_view(String desc_view){
		m_desc_view = desc_view;
		return this;
	}
	public MJCompanionSkillInfo set_is_exercise(boolean is_exercise){
		m_is_exercise = is_exercise;
		return this;
	}
	public MJCompanionSkillInfo set_attacker_effect_id(int attacker_effect_id){
		m_attacker_effect_id = attacker_effect_id;
		return this;
	}
	public MJCompanionSkillInfo set_target_effect_id(int target_effect_id){
		m_target_effect_id = target_effect_id;
		return this;
	}
	public MJCompanionSkillInfo set_enchant(int enchant){
		m_enchant = enchant;
		return this;
	}
	public MJCompanionSkillInfo set_melee_dmg(int melee_dmg){
		m_melee_dmg = melee_dmg;
		return this;
	}
	public MJCompanionSkillInfo set_melee_hit(int melee_hit){
		m_melee_hit = melee_hit;
		return this;
	}
	public MJCompanionSkillInfo set_melee_cri_hit(int melee_cri_hit){
		m_melee_cri_hit = melee_cri_hit;
		return this;
	}
	public MJCompanionSkillInfo set_ignore_reduction(int ignore_reduction){
		m_ignore_reduction = ignore_reduction;
		return this;
	}
	public MJCompanionSkillInfo set_blood_suck_hit(int blood_suck_hit){
		m_blood_suck_hit = blood_suck_hit;
		return this;
	}
	public MJCompanionSkillInfo set_blood_suck_heal(int blood_suck_heal){
		m_blood_suck_heal = blood_suck_heal;
		return this;
	}
	public MJCompanionSkillInfo set_regen_hp(int regen_hp){
		m_regen_hp = regen_hp;
		return this;
	}
	public MJCompanionSkillInfo set_ac(int ac){
		m_ac = ac;
		return this;
	}
	public MJCompanionSkillInfo set_mr(int mr){
		m_mr = mr;
		return this;
	}
	public MJCompanionSkillInfo set_potion_hp(int potion_hp){
		m_potion_hp = potion_hp;
		return this;
	}
	public MJCompanionSkillInfo set_dmg_reduction(int dmg_reduction){
		m_dmg_reduction = dmg_reduction;
		return this;
	}
	public MJCompanionSkillInfo set_max_hp(int max_hp){
		m_max_hp = max_hp;
		return this;
	}
	public MJCompanionSkillInfo set_spell_dmg_multi(int spell_dmg_multi){
		m_spell_dmg_multi = spell_dmg_multi;
		return this;
	}
	public MJCompanionSkillInfo set_spell_hit(int spell_hit){
		m_spell_hit = spell_hit;
		return this;
	}
	public MJCompanionSkillInfo set_move_delay_reduce(double move_delay_reduce){
		m_move_delay_reduce = move_delay_reduce;
		return this;
	}
	public MJCompanionSkillInfo set_attack_delay_reduce(double attack_delay_reduce){
		m_attack_delay_reduce = attack_delay_reduce;
		return this;
	}
	public MJCompanionSkillInfo set_fire_elemental_dmg(int fire_elemental_dmg){
		m_fire_elemental_dmg = fire_elemental_dmg;
		return this;
	}
	public MJCompanionSkillInfo set_water_elemental_dmg(int water_elemental_dmg){
		m_water_elemental_dmg = water_elemental_dmg;
		return this;
	}
	public MJCompanionSkillInfo set_air_elemental_dmg(int air_elemental_dmg){
		m_air_elemental_dmg = air_elemental_dmg;
		return this;
	}
	public MJCompanionSkillInfo set_earth_elemental_dmg(int earth_elemental_dmg){
		m_earth_elemental_dmg = earth_elemental_dmg;
		return this;
	}
	public MJCompanionSkillInfo set_light_elemental_dmg(int light_elemental_dmg){
		m_light_elemental_dmg = light_elemental_dmg;
		return this;
	}
	public MJCompanionSkillInfo set_combo_dmg_multi(int combo_dmg_multi){
		m_combo_dmg_multi = combo_dmg_multi;
		return this;
	}
	public MJCompanionSkillInfo set_spell_dmg_add(int spell_dmg_add){
		m_spell_dmg_add = spell_dmg_add;
		return this;
	}
	public int get_skill_id(){
		return m_skill_id;
	}
	public int get_desc_num(){
		return m_desc_num;
	}
	public String get_desc_view(){
		return m_desc_view;
	}
	public boolean get_is_exercise(){
		return m_is_exercise;
	}
	public int get_attacker_effect_id(){
		return m_attacker_effect_id;
	}
	public int get_target_effect_id(){
		return m_target_effect_id;
	}
	public int get_enchant(){
		return m_enchant;
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
}
