package l1j.server.server.datatables;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;

import l1j.server.MJTemplate.MJSqlHelper.Executors.Selector;
import l1j.server.MJTemplate.MJSqlHelper.Handler.SelectorHandler;

public class IncreaseEinhasadMap{
	private static IncreaseEinhasadMap _instance;
	public static IncreaseEinhasadMap getInstance(){
		if(_instance == null)
			_instance = new IncreaseEinhasadMap();
		return _instance;
	}

	public static void release(){
		if(_instance != null){
			_instance.dispose();
			_instance = null;
		}
	}

	public static void reload(){
		IncreaseEinhasadMap old = _instance;
		_instance = new IncreaseEinhasadMap();
		if(old != null){
			old.dispose();
			old = null;
		}
	}

	private HashMap<Integer, Double> m_increaseInfo;
	private IncreaseEinhasadMap(){
		load();
	}
	
	private void load(){
		m_increaseInfo = new HashMap<Integer, Double>(32);
		Selector.exec("select * from tb_increase_einhasad_map", new SelectorHandler(){
			@Override
			public void handle(PreparedStatement pstm) throws Exception {
			}

			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()){
					int ratio = rs.getInt("ratio");
					
					m_increaseInfo.put(rs.getInt("target_map_id"), new Double(ratio * 0.01));
				}
			}
		});
	}

	public void dispose(){
		if(m_increaseInfo != null){
			m_increaseInfo.clear();
			m_increaseInfo = null;
		}
	}
	
	public double increaseEinhasadValue(int mapId, double val){
		if(!m_increaseInfo.containsKey(mapId))
			return val;
		
		double increase = m_increaseInfo.get(mapId);
		return val * increase;
	}
}
