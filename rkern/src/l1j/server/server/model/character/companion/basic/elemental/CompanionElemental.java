package l1j.server.server.model.character.companion.basic.elemental;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;

import l1j.server.server.storage.sql.executors.Selector;
import l1j.server.server.storage.sql.handler.FullSelectorHandler;

public class CompanionElemental {
	private static HashMap<String, CompanionElemental> m_elementals;
	public static void do_load(){
		final HashMap<String, CompanionElemental> elementals = new HashMap<String, CompanionElemental>();
		Selector.exec("select * from companion_elemental", new FullSelectorHandler(){

			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()){
					CompanionElemental eInfo = CompanionElemental.newInstance(rs);
					elementals.put(eInfo.get_element_name(), eInfo);
				}
			}
		});
		
		m_elementals = elementals;
	}

	public static CompanionElemental get_elemental_info(String category_name){
		return m_elementals.get(category_name);
	}
	
	
	private static CompanionElemental newInstance(ResultSet rs) throws SQLException{
		return newInstance()
				.set_element_name(rs.getString("element_name"))
				.set_element_value(rs.getInt("element_value"));
	}
	
	private static CompanionElemental newInstance(){
		return new CompanionElemental();
	}
	
	private String m_element_name;
	private int m_element_value;
	private CompanionElemental(){}
	
	public CompanionElemental set_element_name(String element_name){
		m_element_name = element_name;
		return this;
	}
	public String get_element_name(){
		return m_element_name;
	}
	public CompanionElemental set_element_value(int element_value){
		m_element_value = element_value;
		return this;
	}
	public int get_element_value(){
		return m_element_value;
	}
}
