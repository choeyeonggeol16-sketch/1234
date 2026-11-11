package l1j.server.InvenBonusItem;

import java.sql.ResultSet;
import java.util.HashMap;

import l1j.server.Template.SqlHelper.Executors.Selector;
import l1j.server.Template.SqlHelper.Handler.FullSelectorHandler;
//인벤 아이템<
public class InvenBonusItemLoader {
	private static InvenBonusItemLoader _instance;
	public static InvenBonusItemLoader getInstance() {
		if(_instance == null)
			_instance = new InvenBonusItemLoader();
		return _instance;
	}
	
	public static void reload() {
		if(_instance != null) {
			_instance = new InvenBonusItemLoader();
		}
	}
	
	private HashMap<Integer, InvenBonusItemInfo> _bonusitem;
	private InvenBonusItemLoader() {
		load();
	}
	
	private void load() {
		final HashMap<Integer, InvenBonusItemInfo> bonus = new HashMap<Integer, InvenBonusItemInfo>(256);
		Selector.exec("select * from inventory_bonus_items", new FullSelectorHandler() {
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()) {
					InvenBonusItemInfo Info = InvenBonusItemInfo.newInstance(rs);
					if(Info == null) {
						continue;
					}
					bonus.put(Info.get_item_id(), Info);
				}
			}
		});
		_bonusitem = bonus;
	}
	
	public InvenBonusItemInfo getInvenBonusItemInfo(int itemId) {
		if (_bonusitem.containsKey(itemId)) {
			return _bonusitem.get(itemId);
		}
		return null;
	}
}
