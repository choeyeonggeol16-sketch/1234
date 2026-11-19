package l1j.server.MJItemEnchantSystem;

import java.sql.ResultSet;
import java.util.HashMap;
import java.util.LinkedList;

import l1j.server.MJTemplate.MJSqlHelper.Executors.Selector;
import l1j.server.MJTemplate.MJSqlHelper.Handler.FullSelectorHandler;

public class MJItemEnchanteeLoader{
	private static MJItemEnchanteeLoader _instance;
	public static MJItemEnchanteeLoader getInstance(){
		if(_instance == null)
			_instance = new MJItemEnchanteeLoader();
		return _instance;
	}

	public static void release(){
		if(_instance != null){
			_instance.dispose();
			_instance = null;
		}
	}

	public static void reload(){
		MJItemEnchanteeLoader old = _instance;
		_instance = new MJItemEnchanteeLoader();
		if(old != null){
			old.dispose();
			old = null;
		}
	}

	private HashMap<Integer, LinkedList<MJItemEnchantee>> _enchanties;
	private MJItemEnchanteeLoader(){
		_enchanties = load();
	}
	
	private HashMap<Integer, LinkedList<MJItemEnchantee>> load(){
		HashMap<Integer, LinkedList<MJItemEnchantee>> enchanties = new HashMap<Integer, LinkedList<MJItemEnchantee>>(256);
		Selector.exec("select * from tb_enchanties", new FullSelectorHandler(){
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()){
					MJItemEnchantee enchantee = MJItemEnchantee.newInstance(rs);
					LinkedList<MJItemEnchantee> enchantee_array = enchanties.get(enchantee.get_enchanter_id());
					if(enchantee_array == null){
						enchantee_array = new LinkedList<MJItemEnchantee>();
						enchanties.put(enchantee.get_enchanter_id(), enchantee_array);
					}
					enchantee_array.add(enchantee);
				}
			}
		});
		return enchanties;
	}
	
	public MJItemEnchantee find_enchantee(int enchanter_id, int enchantee_item_id){
		LinkedList<MJItemEnchantee> enchantee_array = _enchanties.get(enchanter_id);
		if(enchantee_array != null){
			for(MJItemEnchantee enchantee : enchantee_array){
				if(enchantee.get_item_id() == enchantee_item_id)
					return enchantee;
			}
		}
		return null;
	}

	public void dispose(){
		if(_enchanties != null){
			for(LinkedList<MJItemEnchantee> enchantee_array : _enchanties.values())
				enchantee_array.clear();
			_enchanties.clear();
			_enchanties = null;
		}
	}
}