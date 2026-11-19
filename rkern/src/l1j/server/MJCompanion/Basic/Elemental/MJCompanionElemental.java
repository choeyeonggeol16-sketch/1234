package l1j.server.MJCompanion.Basic.Elemental;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import l1j.server.MJTemplate.MJSqlHelper.Executors.Selector;
import l1j.server.MJTemplate.MJSqlHelper.Handler.FullSelectorHandler;

public class MJCompanionElemental {
	private static HashMap<String, MJCompanionElemental> m_elementals;
	public static void do_load(){
		final HashMap<String, MJCompanionElemental> elementals = new HashMap<String, MJCompanionElemental>();
		Selector.exec("select * from companion_elemental", new FullSelectorHandler(){

			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()){
					MJCompanionElemental eInfo = MJCompanionElemental.newInstance(rs);
					elementals.put(eInfo.get_element_name(), eInfo);
				}
			}
		});
		
		m_elementals = elementals;
	}

	public static MJCompanionElemental get_elemental_info(String category_name){
		return m_elementals.get(category_name);
	}
	
	
	private static MJCompanionElemental newInstance(ResultSet rs) throws SQLException{
		return newInstance()
				.set_element_name(rs.getString("element_name"))
				.set_element_value(rs.getInt("element_value"));
	}
	
	private static MJCompanionElemental newInstance(){
		return new MJCompanionElemental();
	}
	
	private String m_element_name;
	private int m_element_value;
	private MJCompanionElemental(){}
	
	public MJCompanionElemental set_element_name(String element_name){
		m_element_name = element_name;
		return this;
	}
	public String get_element_name(){
		return m_element_name;
	}
	public MJCompanionElemental set_element_value(int element_value){
		m_element_value = element_value;
		return this;
	}
	public int get_element_value(){
		return m_element_value;
	}
}
