package l1j.server.ItemInvisibleOptions;

import java.sql.ResultSet;
import java.util.HashMap;

import l1j.server.Template.SqlHelper.Executors.Selector;
import l1j.server.Template.SqlHelper.Handler.FullSelectorHandler;

public class ItemInvisibleOptionsLoader {
	private static ItemInvisibleOptionsLoader _instance;
	public static ItemInvisibleOptionsLoader getInstance() {
		if(_instance == null)
			_instance = new ItemInvisibleOptionsLoader();
		return _instance;
	}
	public static void reload() {
		if(_instance != null) {
			_instance = new ItemInvisibleOptionsLoader();
		}
	}
	
	
	private HashMap<Integer, ItemInvisibleOptionsInfo> _item;
	private ItemInvisibleOptionsLoader() {
		load();
	}
	
	private void load() {
		final HashMap<Integer, ItemInvisibleOptionsInfo> Exchange = new HashMap<Integer, ItemInvisibleOptionsInfo>(256);
		Selector.exec("select * from item_invisible_options", new FullSelectorHandler() {
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()) {
					ItemInvisibleOptionsInfo bonus_option = ItemInvisibleOptionsInfo.newInstance(rs);
					if(bonus_option == null)
						continue;

					Exchange.put(bonus_option.get_item_id(), bonus_option);
				}
			}
		});
		_item = Exchange;
	}
	
	public ItemInvisibleOptionsInfo getItemInvisibleOptionsInfo(int itemId) {
		if (_item.containsKey(itemId)) {
			return _item.get(itemId);
		}
		return null;
	}
}
