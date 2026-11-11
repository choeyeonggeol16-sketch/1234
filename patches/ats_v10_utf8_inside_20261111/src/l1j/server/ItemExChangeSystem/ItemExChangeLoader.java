package l1j.server.ItemExChangeSystem;

import java.sql.ResultSet;
import java.util.HashMap;

import l1j.server.Template.SqlHelper.Executors.Selector;
import l1j.server.Template.SqlHelper.Handler.FullSelectorHandler;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;

public class ItemExChangeLoader{
	private static ItemExChangeLoader _instance;
	public static ItemExChangeLoader getInstance(){
		if(_instance == null)
			_instance = new ItemExChangeLoader();
		return _instance;
	}

	public static void release(){
		if(_instance != null){
			_instance.dispose();
			_instance = null;
		}
	}

	public static void reload(){
		ItemExChangeLoader old = _instance;
		_instance = new ItemExChangeLoader();
		if(old != null){
			old.dispose();
			old = null;
		}
	}

	private HashMap<Integer, ItemExChangeInformation> _exchanges;
	private ItemExChangeLoader(){
		_exchanges = load();
	}
	
	private HashMap<Integer, ItemExChangeInformation> load(){
		HashMap<Integer, ItemExChangeInformation> exchanges = new HashMap<Integer, ItemExChangeInformation>(32);
		Selector.exec("select * from tb_item_exchange_key_info", new FullSelectorHandler(){
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()){
					ItemExChangeInformation exchange = ItemExChangeInformation.newInstance(rs).load_rewards();
					exchanges.put(exchange.get_key_item_id(), exchange);
				}
			}
		});
		return exchanges;
	}
	
	public boolean use_item(L1PcInstance pc, ItemInstance key, int target_id){
		ItemExChangeInformation exInfo = _exchanges.get(key.getItemId());
		if(exInfo == null) {
			return false;
		}
		ItemInstance target = pc.getInventory().getItem(target_id);
		if(target == null) {
			return false;
		}
		exInfo.use_item(pc, key, target);
		return true;
	}

	public void dispose(){
		if(_exchanges != null){
			_exchanges.clear();
			_exchanges = null;
		}
	}
}