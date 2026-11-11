package l1j.server.BonusDropSystem;

import java.sql.ResultSet;
import java.util.HashMap;

import l1j.server.Template.SqlHelper.Executors.Selector;
import l1j.server.Template.SqlHelper.Handler.FullSelectorHandler;

public class BonusDropSystemLoader {
	private static BonusDropSystemLoader _instance;
	public static BonusDropSystemLoader getInstance() {
		if(_instance == null)
			_instance = new BonusDropSystemLoader();
		return _instance;
	}
	public static void reload() {
		if(_instance != null) {
			_instance = new BonusDropSystemLoader();
		}
	}
	
	
	private HashMap<Integer, BonusDropSystemInfo> _bonusitem;
	private BonusDropSystemLoader() {
		load();
	}
	
	private void load() {
		final HashMap<Integer, BonusDropSystemInfo> bonus = new HashMap<Integer, BonusDropSystemInfo>(256);
		Selector.exec("select * from bonus_drop_system", new FullSelectorHandler() {
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()) {
					BonusDropSystemInfo pInfo = BonusDropSystemInfo.newInstance(rs);
					if(pInfo == null) {
						continue;
					}
					bonus.put(pInfo.get_npcid(), pInfo);
				}
			}
		});
		_bonusitem = bonus;
	}
	
	public BonusDropSystemInfo getBonusDropSystemInfo(int npcid) {
		if (_bonusitem.containsKey(npcid)) {
			return _bonusitem.get(npcid);
		}
		return null;
	}
}
