package l1j.server.server.model.character.combat.arena.dogfight.skills;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import l1j.server.server.shared.GameRandom;
import l1j.server.server.storage.sql.executors.Selector;
import l1j.server.server.storage.sql.handler.FullSelectorHandler;

public class DogFightSkill {
	private static ArrayList<DogFightSkill> m_skills;
	public static void do_load(){
		final ArrayList<DogFightSkill> skills = new ArrayList<DogFightSkill>();
		Selector.exec("select * from dogfight_skills", new FullSelectorHandler(){
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()){
					DogFightSkill o = newInstance(rs);
					skills.add(o);
				}
			}
		});
		m_skills = skills;
	}
	
	public static DogFightSkill select_skill(){
		return m_skills.get(GameRandom.next(m_skills.size()));
	}

	private static DogFightSkill newInstance(ResultSet rs) throws SQLException{
		return newInstance()
				.set_skill_id(rs.getInt("skill_id"))
				.set_min_dmg(rs.getInt("min_dmg"))
				.set_max_dmg(rs.getInt("max_dmg"))
				.set_attacker_action(rs.getInt("attacker_action"))
				.set_attacker_effect(rs.getInt("attacker_effect"))
				.set_target_effect(rs.getInt("target_effect"));
	}

	private static DogFightSkill newInstance(){
		return new DogFightSkill();
	}

	private int m_skill_id;
	private int m_min_dmg;
	private int m_max_dmg;
	private int m_attacker_action;
	private int m_attacker_effect;
	private int m_target_effect;
	private DogFightSkill(){}

	public DogFightSkill set_skill_id(int skill_id){
		m_skill_id = skill_id;
		return this;
	}
	public DogFightSkill set_min_dmg(int min_dmg){
		m_min_dmg = min_dmg;
		return this;
	}
	public DogFightSkill set_max_dmg(int max_dmg){
		m_max_dmg = max_dmg;
		return this;
	}
	public DogFightSkill set_attacker_action(int attacker_action){
		m_attacker_action = attacker_action;
		return this;
	}
	public DogFightSkill set_attacker_effect(int attacker_effect){
		m_attacker_effect = attacker_effect;
		return this;
	}
	public DogFightSkill set_target_effect(int target_effect){
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
