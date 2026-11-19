package l1j.server.MJCompanion.Basic.Skills;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;

import l1j.server.MJTemplate.MJSqlHelper.Executors.Selector;
import l1j.server.MJTemplate.MJSqlHelper.Handler.FullSelectorHandler;
import l1j.server.server.utils.MJCommons;

public class MJCompanionClassSkillInfo {
	private static HashMap<String, HashMap<Integer, MJCompanionClassSkillInfo>> m_companion_class_skills;
	private static HashMap<Integer, Integer> m_companion_id_to_tier;
	public static void do_load(){
		final HashMap<String, HashMap<Integer, MJCompanionClassSkillInfo>> companion_class_skills = new HashMap<String, HashMap<Integer, MJCompanionClassSkillInfo>>();
		final HashMap<Integer, Integer> companion_id_to_tier = new HashMap<Integer, Integer>();
		Selector.exec("select * from companion_class_skills order by class_name,tier asc", new FullSelectorHandler(){

			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()){
					MJCompanionClassSkillInfo sInfo = newInstance(rs);
					HashMap<Integer, MJCompanionClassSkillInfo> skills = companion_class_skills.get(sInfo.get_class_name());
					if(skills == null){
						skills = new HashMap<Integer, MJCompanionClassSkillInfo>();
						companion_class_skills.put(sInfo.get_class_name(), skills);	
					}
					
					int tier = sInfo.get_tier();
					skills.put(tier, sInfo);
					for(int id : sInfo.get_skills_id())
						companion_id_to_tier.put(id, tier);
				}
			}
			
		});
		m_companion_class_skills = companion_class_skills;
		m_companion_id_to_tier = companion_id_to_tier;
	}
	
	public static MJCompanionClassSkillInfo get_companion_skills(String class_name, int tier){
		HashMap<Integer, MJCompanionClassSkillInfo> skills = m_companion_class_skills.get(class_name);
		return skills == null ? null : skills.get(tier);
	}
	
	public static int id_to_tier(int skill_id){
		return m_companion_id_to_tier.containsKey(skill_id) ? m_companion_id_to_tier.get(skill_id) : -1;
	}
	
	private static MJCompanionClassSkillInfo newInstance(ResultSet rs) throws SQLException{
		return newInstance()
				.set_class_name(rs.getString("class_name"))
				.set_tier(rs.getInt("tier"))
				.set_skills_id(MJCommons.parseToIntArrange(rs.getString("skill_id"), "\\,"));
	}
	
	private static MJCompanionClassSkillInfo newInstance(){
		return new MJCompanionClassSkillInfo();
	}
	
	
	private String m_class_name;
	private int m_tier;
	private int[] m_skills_id;
	private MJCompanionClassSkillInfo(){}
	
	public MJCompanionClassSkillInfo set_class_name(String class_name){
		m_class_name = class_name;
		return this;
	}
	public MJCompanionClassSkillInfo set_tier(int tier){
		m_tier = tier;
		return this;
	}
	public MJCompanionClassSkillInfo set_skills_id(int[] skills_id){
		ArrayList<Integer> temporary = new ArrayList<Integer>(skills_id.length);
		for(int id : skills_id){
			if(id == 0)
				continue;
			temporary.add(id);
		}
		
		int size = temporary.size();
		m_skills_id = new int[size];
		for(int i=0; i<size; ++i)
			m_skills_id[i] = temporary.get(i);
		return this;
	}
	
	public String get_class_name(){
		return m_class_name;
	}
	public int get_tier(){
		return m_tier;
	}
	public int[] get_skills_id(){
		return m_skills_id;
	}
}
