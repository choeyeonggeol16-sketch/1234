package l1j.server.server.model.world.instance.dungeon.time.loader;

import java.sql.ResultSet;
import java.util.Collection;
import java.util.HashMap;

import l1j.server.server.model.world.instance.dungeon.time.DungeonTimeInformation;
import l1j.server.server.shared.arrangement.ArrangeParseeFactory;
import l1j.server.server.shared.arrangement.ArrangeParser;
import l1j.server.server.storage.sql.executors.Selector;
import l1j.server.server.storage.sql.handler.FullSelectorHandler;

public class DungeonTimeInformationLoader{
	private static DungeonTimeInformationLoader _instance;
	public static DungeonTimeInformationLoader getInstance(){
		if(_instance == null)
			_instance = new DungeonTimeInformationLoader();
		return _instance;
	}

	public static void release(){
		if(_instance != null){
			_instance.dispose();
			_instance = null;
		}
	}

	public static void reload(){
		DungeonTimeInformationLoader old = _instance;
		_instance = new DungeonTimeInformationLoader();
		if(old != null){
			old.dispose();
			old = null;
		}
	}

	private HashMap<Integer, DungeonTimeInformation> _timerid_to_informations;
	private HashMap<Integer, DungeonTimeInformation> _map_to_informations;	
	private DungeonTimeInformationLoader(){
		load();
	}
	
	private void load(){
		_map_to_informations = new HashMap<Integer, DungeonTimeInformation>(256);
		_timerid_to_informations = new HashMap<Integer, DungeonTimeInformation>(16);
		Selector.exec("select * from tb_dungeon_time_information", new FullSelectorHandler(){
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()){
					DungeonTimeInformation dtInfo = DungeonTimeInformation.newInstance(rs);
					_timerid_to_informations.put(dtInfo.get_timer_id(), dtInfo);
					Integer[] mapids = (Integer[])ArrangeParser.parsing(rs.getString("map_ids"), ",", ArrangeParseeFactory.createIntArrange()).result();
					for(Integer i : mapids)
						_map_to_informations.put(i, dtInfo);
				}
			}
		});
	}
	
	public DungeonTimeInformation from_timer_id(int timer_id){
		return _timerid_to_informations.get(timer_id);
	}
	
	public DungeonTimeInformation from_map_id(int map_id){
		return _map_to_informations.get(map_id);
	}
	
	public Collection<DungeonTimeInformation> get_enumerator(){
		return _timerid_to_informations.values();
	}
	
	public void dispose(){
		if(_timerid_to_informations != null){
			_timerid_to_informations.clear();
			_timerid_to_informations = null;
		}
		if(_map_to_informations != null){
			_map_to_informations.clear();
			_map_to_informations = null;
		}
	}
}
