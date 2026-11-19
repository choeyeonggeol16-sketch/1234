package l1j.server.server.datatables;

import java.sql.ResultSet;
import java.util.HashMap;

import l1j.server.MJTemplate.MJSqlHelper.Executors.Selector;
import l1j.server.MJTemplate.MJSqlHelper.Handler.FullSelectorHandler;

public class MonsterParalyzeDelay {
	private static MonsterParalyzeDelay _instance;
	public static MonsterParalyzeDelay getInstance(){
		if(_instance == null)
			_instance = new MonsterParalyzeDelay();
		return _instance;
	}
	
	public static void reload(){
		_instance = new MonsterParalyzeDelay();
	}
	
	private HashMap<Integer, MonsterParalyze> m_paralyzes;
	private MonsterParalyze m_default_paralyze;
	private MonsterParalyzeDelay(){
		m_paralyzes = new HashMap<Integer, MonsterParalyze>();
		Selector.exec("select * from monster_paralyze", new FullSelectorHandler(){
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()){
					MonsterParalyze p = new MonsterParalyze();
					p.skill_id = rs.getInt("skill_id");
					p.paralyze_delay = rs.getInt("paralyze_delay");
					p.paralyze_millis = rs.getInt("paralyze_millis");
					m_paralyzes.put(p.skill_id, p);
				}
			}
		});
		
		m_default_paralyze = new MonsterParalyze();
		m_default_paralyze.skill_id = 0;
		m_default_paralyze.paralyze_delay = 6000;
		m_default_paralyze.paralyze_millis = 10000;
	}
	
	public boolean contains_paralyze(int skill_id){
		return m_paralyzes.containsKey(skill_id);
	}
	
	public MonsterParalyze get_paralyze(int skill_id){
		MonsterParalyze paralyze = m_paralyzes.get(skill_id);
		if(paralyze == null){
			//System.out.println(String.format("몬스터 마비 정보가 누락되었습니다. 스킬아이디 : %d", skill_id)); //neige
			return m_default_paralyze;
		}
		return paralyze;
	}
	
	public static class MonsterParalyze{
		public int skill_id;
		public int paralyze_delay;
		public int paralyze_millis;
	}
}
