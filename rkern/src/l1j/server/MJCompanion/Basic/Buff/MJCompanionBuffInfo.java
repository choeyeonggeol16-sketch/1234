package l1j.server.MJCompanion.Basic.Buff;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;

import l1j.server.MJTemplate.MJSqlHelper.Executors.Selector;
import l1j.server.MJTemplate.MJSqlHelper.Handler.FullSelectorHandler;
import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.server.utils.MJCommons;

public class MJCompanionBuffInfo {
	private static HashMap<Integer, MJCompanionBuffInfo> m_buffs;
	public static void do_load(){
		HashMap<Integer, MJCompanionBuffInfo> buffs = new HashMap<Integer, MJCompanionBuffInfo>();
		Selector.exec("select * from companion_buff", new FullSelectorHandler(){
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()){
					MJCompanionBuffInfo bInfo = newInstance(rs);
					buffs.put(bInfo.get_item_id(), bInfo);
				}
			}
		});
		m_buffs = buffs;
	}
	
	public static MJCompanionBuffInfo from_item_id(int item_id){
		return m_buffs.get(item_id);
	}
	public static MJCompanionBuffInfo from_spell_id(int spell_id){
		for(MJCompanionBuffInfo bInfo : m_buffs.values()){
			if(bInfo.get_spell_id() == spell_id)
				return bInfo;
		}
		return null;
	}
	
	public static double get_exp_bonus_percent(L1CompanionInstance companion){
		double total_exp = 0D;
		for(MJCompanionBuffInfo bInfo : m_buffs.values()){
			double exp = bInfo.get_exp_added_percent();
			if(exp <= 0)
				continue;
			
			if(companion.hasSkillEffect(bInfo.get_spell_id()))
				total_exp += exp;
		}
		return total_exp;
	}
	public static double get_friend_ship_bonus_percent(L1CompanionInstance companion){
		double total_friend_ship = 0D;
		for(MJCompanionBuffInfo bInfo : m_buffs.values()){
			double friend_ship = bInfo.get_friend_ship_guage_percent();
			if(friend_ship <= 0)
				continue;
			
			if(companion.hasSkillEffect(bInfo.get_spell_id()))
				total_friend_ship += friend_ship;
		}
		return total_friend_ship;
	}
	public static int get_pvp_melee_offense_bonus(L1CompanionInstance companion){
		int total_pvp_melee_offense = 0;
		for(MJCompanionBuffInfo bInfo : m_buffs.values()){
			double pvp_melee_offense = bInfo.get_pvp_offense();
			if(pvp_melee_offense <= 0)
				continue;
			
			if(companion.hasSkillEffect(bInfo.get_spell_id()))
				total_pvp_melee_offense += pvp_melee_offense;
		}
		return total_pvp_melee_offense;
	}
	public static int get_pvp_melee_defense_bonus(L1CompanionInstance companion){
		int total_pvp_melee_defense = 0;
		for(MJCompanionBuffInfo bInfo : m_buffs.values()){
			double pvp_melee_defense = bInfo.get_pvp_defense();
			if(pvp_melee_defense <= 0)
				continue;
			
			if(companion.hasSkillEffect(bInfo.get_spell_id()))
				total_pvp_melee_defense += pvp_melee_defense;
		}
		return total_pvp_melee_defense;
	}
	
	private static MJCompanionBuffInfo newInstance(ResultSet rs) throws SQLException{
		return newInstance()
				.set_item_id(rs.getInt("item_id"))
				.set_spell_id(rs.getInt("spell_id"))
				.set_duration(rs.getInt("duration"))
				.set_deduplications_spell_id(MJCommons.parseToIntArrange(rs.getString("deduplications_spell_id"), "\\,"))
				.set_exp_added_percent((double)rs.getInt("exp_added_percent") * 0.01)
				.set_friend_ship_guage_percent((double)rs.getInt("friend_ship_guage_percent") * 0.01)
				.set_pvp_offense(rs.getInt("pvp_melee_offense"))
				.set_pvp_defense(rs.getInt("pvp_melee_defense"))
				.set_effect_id(rs.getInt("effect_id"))
				.set_trigger_effect_id(rs.getInt("trigger_effect_id"));
	}
	
	private static MJCompanionBuffInfo newInstance(){
		return new MJCompanionBuffInfo();
	}
	
	private int m_item_id;
	private int m_spell_id;
	private int m_duration;
	private int[] m_deduplications_spell_id;
	private double m_exp_added_percent;
	private double m_friend_ship_guage_percent;
	private int m_pvp_offense;
	private int m_pvp_defense;
	private int m_effect_id;
	private int m_trigger_effect_id;
	
	private MJCompanionBuffInfo(){}
	
	public MJCompanionBuffInfo set_item_id(int item_id){
		m_item_id = item_id;
		return this;
	}
	public MJCompanionBuffInfo set_spell_id(int spell_id){
		m_spell_id = spell_id;
		return this;
	}
	public MJCompanionBuffInfo set_duration(int duration){
		m_duration = duration;
		return this;
	}
	public MJCompanionBuffInfo set_deduplications_spell_id(int[] deduplications_spell_id){
		m_deduplications_spell_id = deduplications_spell_id;
		return this;
	}
	public MJCompanionBuffInfo set_exp_added_percent(double exp_added_percent){
		m_exp_added_percent = exp_added_percent;
		return this;
	}
	public MJCompanionBuffInfo set_friend_ship_guage_percent(double friend_ship_guage_percent){
		m_friend_ship_guage_percent = friend_ship_guage_percent;
		return this;
	}
	public MJCompanionBuffInfo set_pvp_offense(int pvp_offense){
		m_pvp_offense = pvp_offense;
		return this;
	}
	public MJCompanionBuffInfo set_pvp_defense(int pvp_defense){
		m_pvp_defense = pvp_defense;
		return this;
	}
	public MJCompanionBuffInfo set_effect_id(int effect_id){
		m_effect_id = effect_id;
		return this;
	}
	public MJCompanionBuffInfo set_trigger_effect_id(int trigger_effect_id){
		m_trigger_effect_id = trigger_effect_id;
		return this;
	}
	public int get_item_id(){
		return m_item_id;
	}
	public int get_spell_id(){
		return m_spell_id;
	}
	public int get_duration(){
		return m_duration;
	}
	public int[] get_deduplications_spell_id(){
		return m_deduplications_spell_id;
	}
	public double get_exp_added_percent(){
		return m_exp_added_percent;
	}
	public double get_friend_ship_guage_percent(){
		return m_friend_ship_guage_percent;
	}
	public int get_pvp_offense(){
		return m_pvp_offense;
	}
	public int get_pvp_defense(){
		return m_pvp_defense;
	}
	public int get_effect_id(){
		return m_effect_id;
	}
	public int get_trigger_effect_id(){
		return m_trigger_effect_id;
	}
}
