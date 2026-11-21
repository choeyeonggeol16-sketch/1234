package l1j.server.server.model.item.enchant;

import java.sql.ResultSet;
import java.util.HashMap;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.storage.sql.executors.Selector;
import l1j.server.server.storage.sql.handler.FullSelectorHandler;

public class ItemEnchanterLoader{
	private static ItemEnchanterLoader _instance;
	public static ItemEnchanterLoader getInstance(){
		if(_instance == null)
			_instance = new ItemEnchanterLoader();
		return _instance;
	}

	public static void release(){
		if(_instance != null){
			_instance.dispose();
			_instance = null;
		}
	}

	public static void reload(){
		ItemEnchanterLoader old = _instance;
		_instance = new ItemEnchanterLoader();
		if(old != null){
			old.dispose();
			old = null;
		}
	}

	private HashMap<Integer, ItemEnchanter> _enchanters;
	private ItemEnchanterLoader(){
		_enchanters = load();
	}
	
	private HashMap<Integer, ItemEnchanter> load(){
		HashMap<Integer, ItemEnchanter> enchanters = new HashMap<Integer, ItemEnchanter>(4);
		Selector.exec("select * from tb_enchanters", new FullSelectorHandler(){
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()){
					ItemEnchanter enchanter = ItemEnchanter.newInstance(rs);
					enchanters.put(enchanter.get_item_id(), enchanter);
				}
			}
		});
		return enchanters;
	}
	
	public boolean do_enchant(L1PcInstance pc, L1ItemInstance enchanter_item, L1ItemInstance enchantee_item){
		if(enchanter_item == null)
			return false;

		if(enchantee_item == null)
			return false;

		if(!pc.getInventory().checkItem(enchanter_item.getItemId()))
			return false;

		if(!pc.getInventory().checkItem(enchantee_item.getItemId()))
			return false;

		ItemEnchanter enchanter = _enchanters.get(enchanter_item.getItemId());
		if(enchanter == null)
			return false;

		ItemEnchantee enchantee = ItemEnchanteeLoader.getInstance().find_enchantee(enchanter.get_enchanter_id(), enchantee_item.getItemId());
		if(enchantee == null){
			pc.sendPackets(String.format("%s은(는) 강화할 수 없는 아이템입니다.", enchantee_item.getName()));
			return true;
		}
		
		if(enchanter_item.getCount() < enchanter.get_item_amount()){
			pc.sendPackets(String.format("%s 사용에는 %d개가 필요합니다.", enchanter_item.getName(), enchanter.get_item_amount()));
			return true;
		}
		
		enchanter.do_enchant(pc, enchanter_item, enchantee_item, enchantee);
		return true;
	}

	public void dispose(){
		if(_enchanters != null){
			_enchanters.clear();
			_enchanters = null;
		}
	}
}