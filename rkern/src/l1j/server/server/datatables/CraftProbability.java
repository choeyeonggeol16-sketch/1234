package l1j.server.server.datatables;

import java.sql.ResultSet;
import java.util.HashMap;

import l1j.server.MJTemplate.MJSqlHelper.Executors.Selector;
import l1j.server.MJTemplate.MJSqlHelper.Handler.FullSelectorHandler;

public class CraftProbability {
	private static CraftProbability _instance;
	public static CraftProbability getInstance(){
		if(_instance == null)
			_instance = new CraftProbability();
		return _instance;
	}
	
	public static void reload(){
		CraftProbability old = _instance;
		_instance = new CraftProbability();
		if(old != null){
			old.dispose();
			old = null;
		}
	}
	
	public static void release(){
		if(_instance != null){
			_instance.dispose();
			_instance = null;
		}
	}
	
	private HashMap<Integer, Integer> _craft_probabilities;
	private CraftProbability(){
		_craft_probabilities = load_probabilities();
	}
	
	private HashMap<Integer, Integer> load_probabilities(){
		HashMap<Integer, Integer> probs = new HashMap<Integer, Integer>(256);
		Selector.exec("select * from craft_probability", new FullSelectorHandler(){
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next())
					probs.put(rs.getInt("craft_id"), rs.getInt("prob_by_million"));
			}
		});
		return probs;
	}
	
	public Integer get(Integer craft_id){
		return _craft_probabilities.get(craft_id);
	}
	
	public void dispose(){
		if(_craft_probabilities != null){
			_craft_probabilities.clear();
			_craft_probabilities = null;
		}
	}
}
