package l1j.server.MJCompanion.Basic.Potion;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;

import l1j.server.MJTemplate.MJRnd;
import l1j.server.MJTemplate.MJSqlHelper.Executors.Selector;
import l1j.server.MJTemplate.MJSqlHelper.Handler.FullSelectorHandler;
import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;

public class MJCompanionPotionInfo {
	private static HashMap<Integer, MJCompanionPotionInfo> m_potions_info;
	public static void do_load(){
		HashMap<Integer, MJCompanionPotionInfo> potions_info = new HashMap<Integer, MJCompanionPotionInfo>();
		Selector.exec("select * from companion_potion_info", new FullSelectorHandler(){
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()){
					MJCompanionPotionInfo pInfo = MJCompanionPotionInfo.newInstance(rs);
					potions_info.put(pInfo.get_potion_id(), pInfo);
				}
			}
		});
		m_potions_info = potions_info;
	}
	
	public static MJCompanionPotionInfo get_potion_info(int item_id){
		return m_potions_info.get(item_id);
	}
	
	private static MJCompanionPotionInfo newInstance(ResultSet rs) throws SQLException{
		return newInstance()
				.set_potion_id(rs.getInt("potion_id"))
				.set_potion_min_hp(rs.getInt("potion_min_hp"))
				.set_potion_max_hp(rs.getInt("potion_max_hp"))
				.set_potion_effect(rs.getInt("potion_effect"));
	}
	
	private static MJCompanionPotionInfo newInstance(){
		return new MJCompanionPotionInfo();
	}
	
	private int m_potion_id;
	private int m_potion_min_hp;
	private int m_potion_max_hp;
	private int m_potion_effect;
	private MJCompanionPotionInfo(){}
	
	public MJCompanionPotionInfo set_potion_id(int potion_id){
		m_potion_id = potion_id;
		return this;
	}
	public MJCompanionPotionInfo set_potion_min_hp(int potion_min_hp){
		m_potion_min_hp = potion_min_hp;
		return this;
	}
	public MJCompanionPotionInfo set_potion_max_hp(int potion_max_hp){
		m_potion_max_hp = potion_max_hp;
		return this;
	} 
	public MJCompanionPotionInfo set_potion_effect(int potion_effect){
		m_potion_effect = potion_effect;
		return this;
	}
	public int get_potion_id(){
		return m_potion_id;
	}
	public int get_potion_min_hp(){
		return m_potion_min_hp;
	}
	public int get_potion_max_hp(){
		return m_potion_max_hp;
	}
	public int get_potion_effect(){
		return m_potion_effect;
	}
	
	public void use_item(L1PcInstance pc, L1ItemInstance item){
		L1CompanionInstance companion = pc.get_companion();
		if(companion == null)
			return;
		
		if(!pc.getInventory().consumeItem(item, 1))
			return;
		
		companion.use_healing_potion(MJRnd.next(m_potion_min_hp, m_potion_max_hp));
		if(m_potion_effect > 0)
			companion.send_effect(m_potion_effect);
	}
}
