package l1j.server.server.model.character.companion.basic.skills;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;

import l1j.server.server.storage.sql.executors.Selector;
import l1j.server.server.storage.sql.handler.FullSelectorHandler;

public class CompanionSkillTierOpenInfo {
	private static HashMap<Integer, CompanionSkillTierOpenInfo> m_tiers_open_info;
	public static void do_load(){
		HashMap<Integer, CompanionSkillTierOpenInfo> tiers_open_info = new HashMap<Integer, CompanionSkillTierOpenInfo>();
		Selector.exec("select * from companion_skills_open", new FullSelectorHandler(){
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()){
					CompanionSkillTierOpenInfo oInfo = newInstance(rs);
					tiers_open_info.put(oInfo.get_tier(), oInfo);
				}
			}
		});
		m_tiers_open_info = tiers_open_info;
	}
	
	public static CompanionSkillTierOpenInfo get_tier_open_info(int tier){
		return m_tiers_open_info.get(tier);
	}
	
	private static CompanionSkillTierOpenInfo newInstance(ResultSet rs) throws SQLException{
		return newInstance()
				.set_tier(rs.getInt("tier"))
				.set_open_cost_level(rs.getInt("open_cost_level"))
				.set_open_cost_min_enchant(rs.getInt("open_cost_min_enchant"))
				.set_open_cost_adena(rs.getInt("open_cost_adena"));
	}
	
	private static CompanionSkillTierOpenInfo newInstance(){
		return new CompanionSkillTierOpenInfo();
	}
	
	private int m_tier;
	private int m_open_cost_level;
	private int m_open_cost_min_enchant;
	private int m_open_cost_adena;
	
	private CompanionSkillTierOpenInfo(){}
	
	public CompanionSkillTierOpenInfo set_tier(int tier){
		m_tier = tier;
		return this;
	}
	public CompanionSkillTierOpenInfo set_open_cost_level(int open_cost_level){
		m_open_cost_level = open_cost_level;
		return this;
	}
	public CompanionSkillTierOpenInfo set_open_cost_min_enchant(int open_cost_min_enchant){
		m_open_cost_min_enchant = open_cost_min_enchant;
		return this;
	}
	public CompanionSkillTierOpenInfo set_open_cost_adena(int open_cost_adena){
		m_open_cost_adena = open_cost_adena;
		return this;
	}
	
	public int get_tier(){
		return m_tier;
	}
	public int get_open_cost_level(){
		return m_open_cost_level;
	}
	public int get_open_cost_min_enchant(){
		return m_open_cost_min_enchant;
	}
	public int get_open_cost_adena(){
		return m_open_cost_adena;
	}
}
