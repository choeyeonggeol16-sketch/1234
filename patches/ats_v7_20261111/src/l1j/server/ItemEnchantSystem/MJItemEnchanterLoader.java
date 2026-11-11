package l1j.server.ItemEnchantSystem;

import java.sql.ResultSet;
import java.util.HashMap;

import l1j.server.Template.SqlHelper.Executors.Selector;
import l1j.server.Template.SqlHelper.Handler.FullSelectorHandler;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;

public class MJItemEnchanterLoader{
	private static MJItemEnchanterLoader _instance;
	public static MJItemEnchanterLoader getInstance(){
		if(_instance == null)
			_instance = new MJItemEnchanterLoader();
		return _instance;
	}

	public static void release(){
		if(_instance != null){
			_instance.dispose();
			_instance = null;
		}
	}

	public static void reload(){
		MJItemEnchanterLoader old = _instance;
		_instance = new MJItemEnchanterLoader();
		if(old != null){
			old.dispose();
			old = null;
		}
	}

	private HashMap<Integer, MJItemEnchanter> _enchanters;
	private MJItemEnchanterLoader(){
		_enchanters = load();
	}
	
	private HashMap<Integer, MJItemEnchanter> load(){
		HashMap<Integer, MJItemEnchanter> enchanters = new HashMap<Integer, MJItemEnchanter>(4);
		Selector.exec("select * from tb_enchanters", new FullSelectorHandler(){
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()){
					MJItemEnchanter enchanter = MJItemEnchanter.newInstance(rs);
					enchanters.put(enchanter.get_item_id(), enchanter);
				}
			}
		});
		return enchanters;
	}
	
	public boolean do_enchant(L1PcInstance pc, ItemInstance enchanter_item, ItemInstance enchantee_item){
		if(enchanter_item == null)
			return false;

		if(enchantee_item == null)
			return false;

		if(!pc.getInventory().checkItem(enchanter_item.getItemId()))
			return false;

		if(!pc.getInventory().checkItem(enchantee_item.getItemId()))
			return false;

		MJItemEnchanter enchanter = _enchanters.get(enchanter_item.getItemId());
		if(enchanter == null)
			return false;

		MJItemEnchantee enchantee = MJItemEnchanteeLoader.getInstance().find_enchantee(enchanter.get_enchanter_id(), enchantee_item.getItemId());
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