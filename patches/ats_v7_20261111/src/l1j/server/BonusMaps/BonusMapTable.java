package l1j.server.BonusMaps;

import java.sql.ResultSet;
import java.util.HashMap;

import l1j.server.Template.SqlHelper.Executors.Selector;
import l1j.server.Template.SqlHelper.Handler.FullSelectorHandler;

public class BonusMapTable {
	private static BonusMapTable _instance;
	public static BonusMapTable getInstance() {
		if(_instance == null)
			_instance = new BonusMapTable();
		return _instance;
	}
	public static void reload() {
		if(_instance != null) {
			_instance = new BonusMapTable();
		}
	}
	
	
	private HashMap<Integer, BonusMapInfo> _bonusitem;
	private BonusMapTable() {
		load();
	}
	
	private void load() {
		final HashMap<Integer, BonusMapInfo> bonus = new HashMap<Integer, BonusMapInfo>(256);
		Selector.exec("select * from bonus_map", new FullSelectorHandler() {
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()) {
					BonusMapInfo pInfo = BonusMapInfo.newInstance(rs);
					if(pInfo == null) {
						continue;
					}
					bonus.put(pInfo.get_map_id(), pInfo);
				}
			}
		});
		_bonusitem = bonus;
	}
	
	public BonusMapInfo getBonusMapInfo(int MapId) {
		if (_bonusitem.containsKey(MapId)) {
			return _bonusitem.get(MapId);
		}
		return null;
	}
}
