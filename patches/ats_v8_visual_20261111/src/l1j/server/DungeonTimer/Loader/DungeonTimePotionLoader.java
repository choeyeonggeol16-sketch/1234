package l1j.server.DungeonTimer.Loader;

import java.sql.ResultSet;
import java.util.HashMap;

import l1j.server.DungeonTimer.DungeonTimePotion;
import l1j.server.Template.SqlHelper.Executors.Selector;
import l1j.server.Template.SqlHelper.Handler.FullSelectorHandler;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;

public class DungeonTimePotionLoader{
	private static DungeonTimePotionLoader _instance;
	public static DungeonTimePotionLoader getInstance(){
		if(_instance == null) {
			_instance = new DungeonTimePotionLoader();
		}
		return _instance;
	}

	public static void release(){
		if(_instance != null){
			_instance.dispose();
			_instance = null;
		}
	}

	public static void reload(){
		DungeonTimePotionLoader old = _instance;
		_instance = new DungeonTimePotionLoader();
		if(old != null){
			old.dispose();
			old = null;
		}
	}

	private HashMap<Integer, DungeonTimePotion> _potions;
	private DungeonTimePotionLoader(){
		_potions = load();
	}
	
	private HashMap<Integer, DungeonTimePotion> load(){
		HashMap<Integer, DungeonTimePotion> potions = new HashMap<Integer, DungeonTimePotion>(16);
		Selector.exec("select * from Dungeon_time_potion", new FullSelectorHandler() {
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()){
					DungeonTimePotion potion = DungeonTimePotion.newInstance(rs);
					potions.put(potion.get_item_id(), potion);
				}
			}
		});
		
		return potions;
	}

	public boolean use_potion(L1PcInstance pc, ItemInstance item){
		DungeonTimePotion potion = _potions.get(item.getItemId());
		if(potion != null){
			potion.use_potion(pc, item);
			return true;
		}
		return false;
	}
	
	public void dispose(){
		if(_potions != null){
			_potions.clear();
			_potions = null;
		}
	}
}
