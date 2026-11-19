package l1j.server.MJItemEnchantSystem;

import java.sql.ResultSet;
import java.sql.SQLException;

import l1j.server.server.datatables.ItemTable;
import l1j.server.server.templates.L1Item;

public class MJItemEnchantee {
	public static MJItemEnchantee newInstance(ResultSet rs) throws SQLException{
		return newInstance()
			.set_enchanter_id(rs.getInt("enchanter_id"))
			.set_item_id(rs.getInt("item_id"))
			.set_item_amount(rs.getInt("item_amount"))
			.set_reward_item_id(rs.getInt("reward_item_id"))
			.set_reward_item_amount(rs.getInt("reward_item_amount"))
			.set_success_effect_id(rs.getInt("success_effect_id"))
			.set_failure_effect_id(rs.getInt("failure_effect_id"))
			.set_enchant_probability_by_millions(rs.getInt("enchant_probability_by_millions"))
			.set_is_broadcast(rs.getBoolean("is_broadcast"));
	}
	
	public static MJItemEnchantee newInstance(){
		return new MJItemEnchantee();
	}
	
	private int _enchanter_id;
	private int _item_id;
	private int _item_amount;
	private int _reward_item_id;
	private int _reward_item_amount;
	private int _success_effect_id;
	private int _failure_effect_id;
	private int _enchant_probability_by_millions;
	private boolean _is_broadcast;
	
	private MJItemEnchantee(){}
	
	public MJItemEnchantee set_enchanter_id(int enchanter_id){
		_enchanter_id = enchanter_id;
		return this;
	}
	
	public int get_enchanter_id(){
		return _enchanter_id;
	}
	
	public MJItemEnchantee set_item_id(int item_id){
		_item_id = item_id;
		return this;
	}
	
	public int get_item_id(){
		return _item_id;
	}
	
	public MJItemEnchantee set_item_amount(int item_amount){
		_item_amount = item_amount;
		return this;
	}
	
	public int get_item_amount(){
		return _item_amount;
	}
	
	public MJItemEnchantee set_reward_item_id(int reward_item_id){
		_reward_item_id = reward_item_id;
		return this;
	}
	
	public int get_reward_item_id(){
		return _reward_item_id;
	}
	
	public MJItemEnchantee set_reward_item_amount(int reward_item_amount){
		_reward_item_amount = reward_item_amount;
		return this;
	}
	
	public int get_reward_item_amount(){
		return _reward_item_amount;
	}
	
	public MJItemEnchantee set_success_effect_id(int success_effect_id){
		_success_effect_id = success_effect_id;
		return this;
	}
	
	public int get_success_effect_id(){
		return _success_effect_id;
	}
	
	public MJItemEnchantee set_failure_effect_id(int failure_effect_id){
		_failure_effect_id = failure_effect_id;
		return this;
	}
	
	public int get_failure_effect_id(){
		return _failure_effect_id;
	}
	
	public MJItemEnchantee set_is_broadcast(boolean is_broadcast){
		_is_broadcast = is_broadcast;
		return this;
	}
	
	public boolean is_broadcast(){
		return _is_broadcast;
	}
	
	public MJItemEnchantee set_enchant_probability_by_millions(int enchant_probability_by_millions){
		_enchant_probability_by_millions =enchant_probability_by_millions;
		return this;
	}
	
	public int get_enchant_probability_by_millions(){
		return _enchant_probability_by_millions;
	}
	
	public L1Item to_item_temaplte(){
		return ItemTable.getInstance().getTemplate(get_item_id());
	}
	
	public L1Item to_reward_item_template(){
		return ItemTable.getInstance().getTemplate(get_reward_item_id());
	}
}
