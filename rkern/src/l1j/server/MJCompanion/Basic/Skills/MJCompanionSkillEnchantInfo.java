package l1j.server.MJCompanion.Basic.Skills;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;

import l1j.server.MJTemplate.MJSqlHelper.Executors.Selector;
import l1j.server.MJTemplate.MJSqlHelper.Handler.FullSelectorHandler;
import l1j.server.server.utils.IntRange;

public class MJCompanionSkillEnchantInfo {
	private static HashMap<Integer, MJCompanionSkillEnchantInfo[]> m_enchant_info;
	public static void do_load(){
		final HashMap<Integer, MJCompanionSkillEnchantInfo[]> enchant_info = new HashMap<Integer, MJCompanionSkillEnchantInfo[]>();
		Selector.exec("select * from companion_skills_enchant", new FullSelectorHandler(){
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()){
					MJCompanionSkillEnchantInfo eInfo = newInstance(rs);
					MJCompanionSkillEnchantInfo[] array = enchant_info.get(eInfo.get_tier());
					if(array == null){
						array = new MJCompanionSkillEnchantInfo[10];
						enchant_info.put(eInfo.get_tier(), array);
					}
					array[eInfo.get_enchant() - 1] = eInfo;
				}
			}
		});
		m_enchant_info = enchant_info;
	}
	
	public static MJCompanionSkillEnchantInfo get_enchant_info(int tier, int enchant){
		if(!IntRange.includes(enchant, 1, 10))
			return null;
		
		MJCompanionSkillEnchantInfo[] array = m_enchant_info.get(tier);
		return array == null ? null : array[enchant - 1];
	}
	
	
	private static MJCompanionSkillEnchantInfo newInstance(ResultSet rs) throws SQLException{
		return newInstance()
				.set_tier(rs.getInt("tier"))
				.set_enchant(rs.getInt("enchant"))
				.set_enchant_cost_item_id(rs.getInt("enchant_cost_item_id"))
				.set_enchant_cost_friend_ship(rs.getInt("enchant_cost_friend_ship"))
				.set_probability(rs.getInt("probability"));
	}
	
	private static MJCompanionSkillEnchantInfo newInstance(){
		return new MJCompanionSkillEnchantInfo();
	}
	
	private int m_tier;
	private int m_enchant;
	private int m_enchant_cost_item_id;
	private int m_enchant_cost_friend_ship;
	private int m_probability;
	
	private MJCompanionSkillEnchantInfo(){}
	
	public MJCompanionSkillEnchantInfo set_tier(int tier){
		m_tier = tier;
		return this;
	}
	public MJCompanionSkillEnchantInfo set_enchant(int enchant){
		m_enchant = enchant;
		return this;
	}
	public MJCompanionSkillEnchantInfo set_enchant_cost_item_id(int enchant_cost_item_id){
		m_enchant_cost_item_id = enchant_cost_item_id;
		return this;
	}
	public MJCompanionSkillEnchantInfo set_enchant_cost_friend_ship(int enchant_cost_friend_ship){
		m_enchant_cost_friend_ship = enchant_cost_friend_ship;
		return this;
	}
	public MJCompanionSkillEnchantInfo set_probability(int probability){
		m_probability = probability;
		return this;
	}
	
	public int get_tier(){
		return m_tier;
	}
	public int get_enchant(){
		return m_enchant;
	}
	public int get_enchant_cost_item_id(){
		return m_enchant_cost_item_id;
	}
	public int get_enchant_cost_friend_ship(){
		return m_enchant_cost_friend_ship;
	}
	public int get_probability(){
		return m_probability;
	}
	
	
}
