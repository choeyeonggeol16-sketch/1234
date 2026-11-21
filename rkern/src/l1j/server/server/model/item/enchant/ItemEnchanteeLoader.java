package l1j.server.server.model.item.enchant;

import java.sql.ResultSet;
import java.util.HashMap;
import java.util.LinkedList;

import l1j.server.server.storage.sql.executors.Selector;
import l1j.server.server.storage.sql.handler.FullSelectorHandler;

public class ItemEnchanteeLoader{
	private static ItemEnchanteeLoader _instance;
	public static ItemEnchanteeLoader getInstance(){
		if(_instance == null)
			_instance = new ItemEnchanteeLoader();
		return _instance;
	}

	public static void release(){
		if(_instance != null){
			_instance.dispose();
			_instance = null;
		}
	}

	public static void reload(){
		ItemEnchanteeLoader old = _instance;
		_instance = new ItemEnchanteeLoader();
		if(old != null){
			old.dispose();
			old = null;
		}
	}

	private HashMap<Integer, LinkedList<ItemEnchantee>> _enchanties;
	private ItemEnchanteeLoader(){
		_enchanties = load();
	}
	
	private HashMap<Integer, LinkedList<ItemEnchantee>> load(){
		HashMap<Integer, LinkedList<ItemEnchantee>> enchanties = new HashMap<Integer, LinkedList<ItemEnchantee>>(256);
		Selector.exec("select * from tb_enchanties", new FullSelectorHandler(){
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()){
					ItemEnchantee enchantee = ItemEnchantee.newInstance(rs);
					LinkedList<ItemEnchantee> enchantee_array = enchanties.get(enchantee.get_enchanter_id());
					if(enchantee_array == null){
						enchantee_array = new LinkedList<ItemEnchantee>();
						enchanties.put(enchantee.get_enchanter_id(), enchantee_array);
					}
					enchantee_array.add(enchantee);
				}
			}
		});
		return enchanties;
	}
	
	public ItemEnchantee find_enchantee(int enchanter_id, int enchantee_item_id){
		LinkedList<ItemEnchantee> enchantee_array = _enchanties.get(enchanter_id);
		if(enchantee_array != null){
			for(ItemEnchantee enchantee : enchantee_array){
				if(enchantee.get_item_id() == enchantee_item_id)
					return enchantee;
			}
		}
		return null;
	}

	public void dispose(){
		if(_enchanties != null){
			for(LinkedList<ItemEnchantee> enchantee_array : _enchanties.values())
				enchantee_array.clear();
			_enchanties.clear();
			_enchanties = null;
		}
	}
}