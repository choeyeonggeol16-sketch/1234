package l1j.server.ExchangeItem;

import java.sql.ResultSet;
import java.util.HashMap;

import l1j.server.Template.SqlHelper.Executors.Selector;
import l1j.server.Template.SqlHelper.Handler.FullSelectorHandler;

public class ExchangeItemLoader {
	private static ExchangeItemLoader _instance;
	public static ExchangeItemLoader getInstance() {
		if(_instance == null)
			_instance = new ExchangeItemLoader();
		return _instance;
	}
	public static void reload() {
		if(_instance != null) {
			_instance = new ExchangeItemLoader();
		}
	}
	
	
	private HashMap<Integer, ExchangeItemInfo> _exchange_item;
	private ExchangeItemLoader() {
		load();
	}
	
	private void load() {
		final HashMap<Integer, ExchangeItemInfo> Exchange = new HashMap<Integer, ExchangeItemInfo>(256);
		Selector.exec("select * from exchange_items", new FullSelectorHandler() {
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()) {
					ExchangeItemInfo ExInfo = ExchangeItemInfo.newInstance(rs);
					if(ExInfo == null) {
						continue;
					}
					Exchange.put(ExInfo.get_item_id(), ExInfo);
				}
			}
		});
		_exchange_item = Exchange;
	}
	
	public ExchangeItemInfo getExchangeItemInfo(int itemId) {
		if (_exchange_item.containsKey(itemId)) {
			return _exchange_item.get(itemId);
		}
		return null;
	}
}
