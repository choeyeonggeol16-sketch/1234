package l1j.server.ActionListener;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;

import l1j.server.ActionListener.Npc.DungeonTimeActionListener;
import l1j.server.ActionListener.Npc.NpcActionListener;
import l1j.server.ActionListener.Npc.TeleporterActionListener;
import l1j.server.Template.DateSchedulerModel.Acceptor.AcceptorToNpcListenerAdapter;
import l1j.server.Template.SqlHelper.Executors.Selector;
import l1j.server.Template.SqlHelper.Handler.FullSelectorHandler;

public class ActionListenerLoader {
	private static ActionListenerLoader _instance;
	public static ActionListenerLoader getInstance(){
		if(_instance == null)
			_instance = new ActionListenerLoader();
		return _instance;
	}
	
	private HashMap<Integer, HashMap<String, ArrayList<ActionListener>>> _npc_listeners;
	private ActionListenerLoader(){
		updateNpcActionListener();
	}
	
	public void updateNpcActionListener(){
		HashMap<Integer, HashMap<String, ArrayList<ActionListener>>> old_listeners = _npc_listeners;
		_npc_listeners = 
				loadDungeonTimeActionListener(
				loadTimeTeleporterActionListener(
				loadTeleporterActionListener(
				loadNpcActionListener(
						new HashMap<Integer, HashMap<String, ArrayList<ActionListener>>>(256)
	))));
		dispose(old_listeners);
	}
	
	private HashMap<Integer, HashMap<String, ArrayList<ActionListener>>> loadNpcActionListener(HashMap<Integer, HashMap<String, ArrayList<ActionListener>>> listeners_2d){
		Selector.exec("select * from Act_listener_npc", new FullSelectorHandler(){
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
	
	private HashMap<Integer, HashMap<String, ArrayList<ActionListener>>> loadTeleporterActionListener(HashMap<Integer, HashMap<String, ArrayList<ActionListener>>> listeners_2d){
		Selector.exec("select * from Act_listener_npc_teleporter", new FullSelectorHandler(){
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
					if(a_listener instanceof NpcActionListener) {					
						listener_array.set(0, TeleporterActionListener.newInstance((NpcActionListener)a_listener.deep_copy(), rs));
					} else {
						listener_array.add(TeleporterActionListener.newInstance((NpcActionListener)a_listener.deep_copy(), rs));
					}
				}
			}
		});
		return listeners_2d;
	}
	
	private HashMap<Integer, HashMap<String, ArrayList<ActionListener>>> loadTimeTeleporterActionListener(HashMap<Integer, HashMap<String, ArrayList<ActionListener>>> listeners_2d){
		Selector.exec("select * from Act_listener_npc_time_teleporter", new FullSelectorHandler(){
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
	
	
	private HashMap<Integer, HashMap<String, ArrayList<ActionListener>>> loadDungeonTimeActionListener(HashMap<Integer, HashMap<String, ArrayList<ActionListener>>> listeners_2d){
		Selector.exec("select * from Act_listener_npc_dungeon_telepeorter", new FullSelectorHandler(){
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
					if(a_listener instanceof TeleporterActionListener){
						listener_array.set(0, DungeonTimeActionListener.newInstance((NpcActionListener) a_listener, rs));
					}else{
						listener_array.add(DungeonTimeActionListener.newInstance((NpcActionListener) a_listener, rs));
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
