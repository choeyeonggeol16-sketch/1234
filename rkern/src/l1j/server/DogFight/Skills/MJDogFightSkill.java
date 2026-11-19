package l1j.server.DogFight.Skills;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import l1j.server.MJTemplate.MJRnd;
import l1j.server.MJTemplate.MJSqlHelper.Executors.Selector;
import l1j.server.MJTemplate.MJSqlHelper.Handler.FullSelectorHandler;

public class MJDogFightSkill {
	private static ArrayList<MJDogFightSkill> m_skills;
	public static void do_load(){
		final ArrayList<MJDogFightSkill> skills = new ArrayList<MJDogFightSkill>();
		Selector.exec("select * from dogfight_skills", new FullSelectorHandler(){
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()){
					MJDogFightSkill o = newInstance(rs);
					skills.add(o);
				}
			}
		});
		m_skills = skills;
	}
	
	public static MJDogFightSkill select_skill(){
		return m_skills.get(MJRnd.next(m_skills.size()));
	}

	private static MJDogFightSkill newInstance(ResultSet rs) throws SQLException{
		return newInstance()
				.set_skill_id(rs.getInt("skill_id"))
				.set_min_dmg(rs.getInt("min_dmg"))
				.set_max_dmg(rs.getInt("max_dmg"))
				.set_attacker_action(rs.getInt("attacker_action"))
				.set_attacker_effect(rs.getInt("attacker_effect"))
				.set_target_effect(rs.getInt("target_effect"));
	}

	private static MJDogFightSkill newInstance(){
		return new MJDogFightSkill();
	}

	private int m_skill_id;
	private int m_min_dmg;
	private int m_max_dmg;
	private int m_attacker_action;
	private int m_attacker_effect;
	private int m_target_effect;
	private MJDogFightSkill(){}

	public MJDogFightSkill set_skill_id(int skill_id){
		m_skill_id = skill_id;
		return this;
	}
	public MJDogFightSkill set_min_dmg(int min_dmg){
		m_min_dmg = min_dmg;
		return this;
	}
	public MJDogFightSkill set_max_dmg(int max_dmg){
		m_max_dmg = max_dmg;
		return this;
	}
	public MJDogFightSkill set_attacker_action(int attacker_action){
		m_attacker_action = attacker_action;
		return this;
	}
	public MJDogFightSkill set_attacker_effect(int attacker_effect){
		m_attacker_effect = attacker_effect;
		return this;
	}
	public MJDogFightSkill set_target_effect(int target_effect){
		m_target_effect = target_effect;
		return this;
	}
	public int get_skill_id(){
		return m_skill_id;
	}
	public int get_min_dmg(){
		return m_min_dmg;
	}
	public int get_max_dmg(){
		return m_max_dmg;
	}
	public int get_attacker_action(){
		return m_attacker_action;
	}
	public int get_attacker_effect(){
		return m_attacker_effect;
	}
	public int get_target_effect(){
		return m_target_effect;
	}

}
