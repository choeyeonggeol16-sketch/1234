package l1j.server.MJActionListener;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;

import l1j.server.MJActionListener.Npc.CombatActionListener;
import l1j.server.MJActionListener.Npc.DungeonTimeActionListener;
import l1j.server.MJActionListener.Npc.NpcActionListener;
import l1j.server.MJActionListener.Npc.TeleporterActionListener;
import l1j.server.MJTemplate.DateSchedulerModel.Acceptor.AcceptorToCombatListenerAdapter;
import l1j.server.MJTemplate.DateSchedulerModel.Acceptor.AcceptorToNpcListenerAdapter;
import l1j.server.MJTemplate.MJSqlHelper.Executors.Selector;
import l1j.server.MJTemplate.MJSqlHelper.Handler.FullSelectorHandler;

public class ActionListenerLoader {
	private static ActionListenerLoader _instance;
	public static ActionListenerLoader getInstance(){
		if(_instance == null)
			_instance = new ActionListenerLoader();
		return _instance;
	}
	
	private HashMap<Integer, HashMap<String, ArrayList<ActionListener>>> _npc_listeners;
	// Immutable published snapshot of tb_act_listener_npc_teleporter tower rows.
	// Four primitive values per floor: X, Y, map ID, range. No SQL on CIO.
	private volatile int[] _towerTeleport = new int[12 * 4];

	/** Copy the main action (1..11) destination from the already loaded NPC table. */
	public boolean copyTowerTeleport(int floor, int[] destination) {
		if (floor < 1 || floor > 11 || destination == null || destination.length < 4)
			return false;
		final int[] snapshot = _towerTeleport;
		final int offset = floor << 2;
		final int map = snapshot[offset + 2];
		if (map != 12851 + floor) return false;
		destination[0] = snapshot[offset];
		destination[1] = snapshot[offset + 1];
		destination[2] = map;
		destination[3] = snapshot[offset + 3];
		return true;
	}
	private ActionListenerLoader(){
		updateNpcActionListener();
	}
	
	public void updateNpcActionListener(){
		HashMap<Integer, HashMap<String, ArrayList<ActionListener>>> old_listeners = _npc_listeners;
		final int[] towerTeleport = new int[12 * 4];
		_npc_listeners = 
				loadDungeonTimeActionListener(
				loadTimeCombatActionListener(
				loadCombatActionListener(
				loadTimeTeleporterActionListener(
				loadTeleporterActionListener(
				loadNpcActionListener(
						new HashMap<Integer, HashMap<String, ArrayList<ActionListener>>>(256)
				), towerTeleport)))));
		_towerTeleport = towerTeleport;
		dispose(old_listeners);
	}
	
	private HashMap<Integer, HashMap<String, ArrayList<ActionListener>>> loadNpcActionListener(HashMap<Integer, HashMap<String, ArrayList<ActionListener>>> listeners_2d){
		Selector.exec("select * from tb_act_listener_npc", new FullSelectorHandler(){
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()){
					NpcActionListener listener = NpcActionListener.newInstance(rs);
					int npc_id = listener.get_npc_id();

					HashMap<String, ArrayList<ActionListener>> listeners = listeners_2d.get(npc_id);
					if(listeners == null){
						listeners = new HashMap<String, ArrayList<ActionListener>>(4);
						listeners_2d.put(npc_id, listeners);
					}
					
					ArrayList<ActionListener> listener_array = listeners.get(listener.get_action_name());
					if(listener_array == null){
						listener_array = new ArrayList<ActionListener>(4);
						listeners.put(listener.get_action_name(), listener_array);
					}
					listener_array.add(listener);
				}
			}
		});
		return listeners_2d;
	}
	
	private HashMap<Integer, HashMap<String, ArrayList<ActionListener>>> loadTeleporterActionListener(HashMap<Integer, HashMap<String, ArrayList<ActionListener>>> listeners_2d, int[] towerTeleport){
		Selector.exec("select * from tb_act_listener_npc_teleporter", new FullSelectorHandler(){
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()){
					int npc_id = rs.getInt("npc_id");
					String action_name	= rs.getString("action_name");
					
					HashMap<String, ArrayList<ActionListener>> listeners = listeners_2d.get(npc_id);
					if(listeners == null){
						to_invalid_listener("텔레포터", npc_id, action_name);
						continue;
					}
					
					ArrayList<ActionListener> listener_array = listeners.get(action_name);
					if(listener_array == null){
						to_invalid_listener("텔레포터", npc_id, action_name);
						continue;
					}
					
					ActionListener a_listener = listener_array.get(0);
					TeleporterActionListener teleporter = TeleporterActionListener.newInstance(
							(NpcActionListener) a_listener.deep_copy(), rs);
					if(a_listener instanceof NpcActionListener){
						listener_array.set(0, teleporter);
					}else{
						listener_array.add(teleporter);
					}
					// Only numeric main actions select a floor. Event 'a' rows remain
					// available to their existing NPC action flow, not GM test routing.
					final int floor = npc_id - 8500027;
					if (floor >= 1 && floor <= 11 && action_name.equals(Integer.toString(floor))) {
						final short[] maps = teleporter.get_teleport_map_id();
						if (maps != null && maps.length != 0 && maps[0] == 12851 + floor) {
							final int o = floor << 2;
							towerTeleport[o] = teleporter.get_teleport_x();
							towerTeleport[o + 1] = teleporter.get_teleport_y();
							towerTeleport[o + 2] = maps[0];
							towerTeleport[o + 3] = Math.max(0, teleporter.get_range());
						}
					}
				}
			}
		});
		return listeners_2d;
	}
	
	private HashMap<Integer, HashMap<String, ArrayList<ActionListener>>> loadTimeTeleporterActionListener(HashMap<Integer, HashMap<String, ArrayList<ActionListener>>> listeners_2d){
		Selector.exec("select * from tb_act_listener_npc_time_teleporter", new FullSelectorHandler(){
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()){
					int npc_id = rs.getInt("npc_id");
					String action_name	= rs.getString("action_name");
					
					HashMap<String, ArrayList<ActionListener>> listeners = listeners_2d.get(npc_id);
					if(listeners == null){
						to_invalid_listener("타임 텔레포터", npc_id, action_name);
						continue;
					}
					
					ArrayList<ActionListener> listener_array = listeners.get(action_name);
					if(listener_array == null){
						to_invalid_listener("타임 텔레포터", npc_id, action_name);
						continue;
					}
					
					ActionListener a_listener = listener_array.get(0);
					if(a_listener instanceof TeleporterActionListener){
						listener_array.set(0, AcceptorToNpcListenerAdapter.newInstance((TeleporterActionListener)a_listener, rs));
					}else{
						AcceptorToNpcListenerAdapter adapter = (AcceptorToNpcListenerAdapter)a_listener;
						listener_array.add(adapter.deep_copy(rs));
					}
				}
			}
		});
		return listeners_2d;
	}
	
	private HashMap<Integer, HashMap<String, ArrayList<ActionListener>>> loadCombatActionListener(HashMap<Integer, HashMap<String, ArrayList<ActionListener>>> listeners_2d){
		Selector.exec("select * from tb_act_listener_npc_combat", new FullSelectorHandler(){
			public void result(ResultSet rs) throws Exception {
				while(rs.next()){
					int npc_id = rs.getInt("npc_id");
					String action_name	= rs.getString("action_name");
					
					HashMap<String, ArrayList<ActionListener>> listeners = listeners_2d.get(npc_id);
					if(listeners == null){
						to_invalid_listener("배틀필드", npc_id, action_name);
						continue;
					}
					
					ArrayList<ActionListener> listener_array = listeners.get(action_name);
					if(listener_array == null){
						to_invalid_listener("배틀필드", npc_id, action_name);
						continue;
					}
					
					ActionListener a_listener = listener_array.get(0);
					if(a_listener instanceof NpcActionListener){
						listener_array.set(0, CombatActionListener.newInstance((NpcActionListener)a_listener.deep_copy(), rs));
					}else{
						listener_array.add(CombatActionListener.newInstance((NpcActionListener)a_listener.deep_copy(), rs));
					}
				}
			}
		});
		return listeners_2d;
	}
	
	private HashMap<Integer, HashMap<String, ArrayList<ActionListener>>> loadTimeCombatActionListener(HashMap<Integer, HashMap<String, ArrayList<ActionListener>>> listeners_2d){
		Selector.exec("select * from tb_act_listener_npc_time_combat", new FullSelectorHandler(){
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()){
					int npc_id = rs.getInt("npc_id");
					String action_name	= rs.getString("action_name");
					
					HashMap<String, ArrayList<ActionListener>> listeners = listeners_2d.get(npc_id);
					if(listeners == null){
						to_invalid_listener("타임 배틀필드", npc_id, action_name);
						continue;
					}
					
					ArrayList<ActionListener> listener_array = listeners.get(action_name);
					if(listener_array == null){
						to_invalid_listener("타임 배틀필드", npc_id, action_name);
						continue;
					}
					
					ActionListener a_listener = listener_array.get(0);
					if(a_listener instanceof CombatActionListener){
						listener_array.set(0, AcceptorToCombatListenerAdapter.newInstance((CombatActionListener)a_listener, rs));
					}else{
						AcceptorToCombatListenerAdapter adapter = (AcceptorToCombatListenerAdapter)a_listener;
						listener_array.add(adapter.deep_copy(rs));
					}
				}
			}
		});
		return listeners_2d;
	}
	
	private HashMap<Integer, HashMap<String, ArrayList<ActionListener>>> loadDungeonTimeActionListener(HashMap<Integer, HashMap<String, ArrayList<ActionListener>>> listeners_2d){
		Selector.exec("select * from tb_act_listener_npc_dungeon_telepeorter", new FullSelectorHandler(){
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()){
					int npc_id = rs.getInt("npc_id");
					String action_name	= rs.getString("action_name");
					
					HashMap<String, ArrayList<ActionListener>> listeners = listeners_2d.get(npc_id);
					if(listeners == null){
						to_invalid_listener("던전타이머 텔레포터", npc_id, action_name);
						continue;
					}
					
					ArrayList<ActionListener> listener_array = listeners.get(action_name);
					if(listener_array == null){
						to_invalid_listener("던전타이머 텔레포터", npc_id, action_name);
						continue;
					}
					
					ActionListener a_listener = listener_array.get(0);

					if (!(a_listener instanceof NpcActionListener)) {
					//    System.out.println(String.format("[에러] 던전타이머 리스너 타입 불일치. NPC ID: %d, 클래스: %s", npc_id, a_listener.getClass().getName()));
					    continue; 
					}

					NpcActionListener npcListener = (NpcActionListener) a_listener;

					if(a_listener instanceof TeleporterActionListener){
					    listener_array.set(0, DungeonTimeActionListener.newInstance(npcListener, rs));
					} else {
					    listener_array.add(DungeonTimeActionListener.newInstance(npcListener, rs));
					}
				}
			}
		});
		return listeners_2d;
	}
		
	public ActionListener findListener(int npc_id, String action){
		
		HashMap<String, ArrayList<ActionListener>> listeners = _npc_listeners.get(npc_id);
		if(listeners == null)
			return null;
		
		ArrayList<ActionListener> listener_array = listeners.get(action);
		if(listener_array == null){
			return null;
		}
		
		if(listener_array.size() > 1){
			for(ActionListener listener : listener_array){
				if(listener.is_action())
					return listener;
			}
		}
		return listener_array.get(0);
	}
	
	private void to_invalid_listener(String owner, int id, String action_name){
		System.out.println(String.format("[ActionListenerLoader] %s의 액션 리스너를 찾을 수 없습니다. 아이디 : %d, 액션 : %s",owner, id, action_name));
	}

	private void dispose(HashMap<Integer, HashMap<String, ArrayList<ActionListener>>> listeners_2d){
		if(listeners_2d != null){
			for(HashMap<String, ArrayList<ActionListener>> listeners : listeners_2d.values()){
				for(ArrayList<ActionListener> listener_array : listeners.values()){
					for(ActionListener listener : listener_array)
						listener.dispose();
					listener_array.clear();
				}
				listeners.clear();
			}
			listeners_2d.clear();
			listeners_2d = null;
		}
	}
	
	public void dispose(){
		dispose(_npc_listeners);
	}
}
