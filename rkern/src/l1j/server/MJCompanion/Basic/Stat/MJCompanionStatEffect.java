package l1j.server.MJCompanion.Basic.Stat;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;

import l1j.server.MJTemplate.MJSqlHelper.Executors.Selector;
import l1j.server.MJTemplate.MJSqlHelper.Handler.FullSelectorHandler;

public class MJCompanionStatEffect {
	private static HashMap<Integer, HashMap<Integer, MJCompanionStatEffect>> m_stat_effects;
	public static void do_load(){
		HashMap<Integer, HashMap<Integer, MJCompanionStatEffect>> stat_effects = new HashMap<Integer, HashMap<Integer, MJCompanionStatEffect>>();
		Selector.exec("select * from companion_stat_effects", new FullSelectorHandler(){
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()){
					MJCompanionStatEffect effect = newInstance(rs);
					HashMap<Integer, MJCompanionStatEffect> stats = stat_effects.get(effect.get_stat_type().to_int());
					if(stats == null){
						stats = new HashMap<Integer, MJCompanionStatEffect>();
						stat_effects.put(effect.get_stat_type().to_int(), stats);	
					}
					stats.put(effect.get_value(), effect);
				}
			}
		});
		m_stat_effects = stat_effects;
	}
	
	public static MJCompanionStatEffect createInstance(int str, int con, int intel){
		return createInstance(new MJCompanionStatEffect[]{
				m_stat_effects.get(MJCompanionStatType.STR.to_int()).get(str),
				m_stat_effects.get(MJCompanionStatType.CON.to_int()).get(con),
				m_stat_effects.get(MJCompanionStatType.INTEL.to_int()).get(intel),
		});
	}
	
	private static MJCompanionStatEffect createInstance(MJCompanionStatEffect[] effects){
		MJCompanionStatEffect new_effect = newInstance();
		for(MJCompanionStatEffect effect : effects){
			if(effect == null)
				continue;
			new_effect
			.add_melee_dmg(effect.get_melee_dmg())
			.add_melee_hit(effect.get_melee_hit())
			.add_melee_cri(effect.get_melee_cri())
			.add_add_min_hp(effect.get_add_min_hp())
			.add_add_max_hp(effect.get_add_max_hp())
			.add_hpr(effect.get_hpr())
			.add_ac(effect.get_ac())
			.add_mr(effect.get_mr())
			.add_reduction(effect.get_reduction())
			.add_skill_dmg(effect.get_skill_dmg())
			.add_skill_cri(effect.get_skill_cri())
			.add_skill_ratio(effect.get_skill_ratio());
		}
		return new_effect;
	}
	
	private static MJCompanionStatEffect newInstance(ResultSet rs) throws SQLException{
		return newInstance()
				.set_stat(rs.getString("stat_type"))
				.set_value(rs.getInt("value"))
				.set_melee_dmg(rs.getInt("melee_dmg"))
				.set_melee_hit(rs.getInt("melee_hit"))
				.set_melee_cri(rs.getInt("melee_cri"))
				.set_add_min_hp(rs.getInt("add_min_hp"))
				.set_add_max_hp(rs.getInt("add_max_hp"))
				.set_hpr(rs.getInt("hpr"))
				.set_ac(rs.getInt("ac"))
				.set_mr(rs.getInt("mr"))
				.set_reduction(rs.getInt("reduction"))
				.set_skill_dmg(rs.getInt("skill_dmg"))
				.set_skill_cri(rs.getInt("skill_cri"))
				.set_skill_ratio(rs.getInt("skill_ratio"));
	}
	
	private static MJCompanionStatEffect newInstance(){
		return new MJCompanionStatEffect();
	}

	private MJCompanionStatType m_stat_type;
	private int m_value;
	private int m_melee_dmg;
	private int m_melee_hit;
	private int m_melee_cri;
	private int m_add_min_hp;
	private int m_add_max_hp;
	private int m_hpr;
	private int m_ac;
	private int m_mr;
	private int m_reduction;
	private int m_skill_dmg;
	private int m_skill_cri;
	private int m_skill_ratio;
	private MJCompanionStatEffect(){}
	
	public MJCompanionStatEffect add_melee_dmg(int melee_dmg){
		m_melee_dmg += melee_dmg;
		return this;
	}
	public MJCompanionStatEffect add_melee_hit(int melee_hit){
		m_melee_hit += melee_hit;
		return this;
	}
	public MJCompanionStatEffect add_melee_cri(int melee_cri){
		m_melee_cri += melee_cri;
		return this;
	}
	public MJCompanionStatEffect add_add_min_hp(int add_min_hp){
		m_add_min_hp += add_min_hp;
		return this;
	}
	public MJCompanionStatEffect add_add_max_hp(int add_max_hp){
		m_add_max_hp += add_max_hp;
		return this;
	}
	public MJCompanionStatEffect add_hpr(int hpr){
		m_hpr += hpr;
		return this;
	}
	public MJCompanionStatEffect add_ac(int ac){
		m_ac += ac;
		return this;
	}
	public MJCompanionStatEffect add_mr(int mr){
		m_mr += mr;
		return this;
	}
	public MJCompanionStatEffect add_reduction(int reduction){
		m_reduction += reduction;	
		return this;
	}
	public MJCompanionStatEffect add_skill_dmg(int skill_dmg){
		m_skill_dmg += skill_dmg;
		return this;
	}
	public MJCompanionStatEffect add_skill_cri(int skill_cri){
		m_skill_cri += skill_cri;
		return this;
	}
	public MJCompanionStatEffect add_skill_ratio(int skill_ratio){
		m_skill_ratio += skill_ratio;
		return this;
	}
	public MJCompanionStatEffect set_stat(String stat_type){
		m_stat_type = MJCompanionStatType.from_string(stat_type);
		return this;
	}
	public MJCompanionStatEffect set_value(int value){
		m_value = value;
		return this;
	}
	public MJCompanionStatEffect set_melee_dmg(int melee_dmg){
		m_melee_dmg = melee_dmg;
		return this;
	}
	public MJCompanionStatEffect set_melee_hit(int melee_hit){
		m_melee_hit = melee_hit;
		return this;
	}
	public MJCompanionStatEffect set_melee_cri(int melee_cri){
		m_melee_cri = melee_cri;
		return this;
	}
	public MJCompanionStatEffect set_add_min_hp(int add_min_hp){
		m_add_min_hp = add_min_hp;
		return this;
	}
	public MJCompanionStatEffect set_add_max_hp(int add_max_hp){
		m_add_max_hp = add_max_hp;
		return this;
	}
	public MJCompanionStatEffect set_hpr(int hpr){
		m_hpr = hpr;
		return this;
	}
	public MJCompanionStatEffect set_ac(int ac){
		m_ac = ac;
		return this;
	}
	public MJCompanionStatEffect set_mr(int mr){
		m_mr = mr;
		return this;
	}
	public MJCompanionStatEffect set_reduction(int reduction){
		m_reduction = reduction;	
		return this;
	}
	public MJCompanionStatEffect set_skill_dmg(int skill_dmg){
		m_skill_dmg = skill_dmg;
		return this;
	}
	public MJCompanionStatEffect set_skill_cri(int skill_cri){
		m_skill_cri = skill_cri;
		return this;
	}
	public MJCompanionStatEffect set_skill_ratio(int skill_ratio){
		m_skill_ratio = skill_ratio;
		return this;
	}
	public MJCompanionStatType get_stat_type(){
		return m_stat_type;
	}
	public int get_value(){
		return m_value;
	}
	public int get_melee_dmg(){
		return m_melee_dmg;
	}
	public int get_melee_hit(){
		return m_melee_hit;
	}
	public int get_melee_cri(){
		return m_melee_cri;
	}
	public int get_add_min_hp(){
		return m_add_min_hp;
	}
	public int get_add_max_hp(){
		return m_add_max_hp;
	}
	public int get_hpr(){
		return m_hpr;
	}
	public int get_ac(){
		return m_ac;
	}
	public int get_mr(){
		return m_mr;
	}
	public int get_reduction(){
		return m_reduction;
	}
	public int get_skill_dmg(){
		return m_skill_dmg;
	}
	public int get_skill_cri(){
		return m_skill_cri;
	}
	public int get_skill_ratio(){
		return m_skill_ratio;
	}
}
