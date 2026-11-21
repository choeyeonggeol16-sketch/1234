package l1j.server.server.model.character.companion.basic.category;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;

import l1j.server.server.storage.sql.executors.Selector;
import l1j.server.server.storage.sql.handler.FullSelectorHandler;

public class CompanionCategoryInfo {
	private static HashMap<String, CompanionCategoryInfo> m_categories;
	public static void do_load(){
		final HashMap<String, CompanionCategoryInfo> categories = new HashMap<String, CompanionCategoryInfo>();
		Selector.exec("select * from companion_category", new FullSelectorHandler(){

			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()){
					CompanionCategoryInfo cInfo = CompanionCategoryInfo.newInstance(rs);
					categories.put(cInfo.get_category_name(), cInfo);
				}
			}
		});
		
		m_categories = categories;
	}

	public static CompanionCategoryInfo get_category_info(String category_name){
		return m_categories.get(category_name);
	}
	
	private static CompanionCategoryInfo newInstance(ResultSet rs) throws SQLException{
		return newInstance()
				.set_category_name(rs.getString("category_name"))
				.set_category_value(rs.getInt("category_value"));
	}
	
	private static CompanionCategoryInfo newInstance(){
		return new CompanionCategoryInfo();
	}
	
	private String m_category_name;
	private int m_category_value;
	private CompanionCategoryInfo(){}
	
	public CompanionCategoryInfo set_category_name(String category_name){
		m_category_name = category_name;
		return this;
	}
	public String get_category_name(){
		return m_category_name;
	}
	public CompanionCategoryInfo set_category_value(int category_value){
		m_category_value = category_value;
		return this;
	}
	public int get_category_value(){
		return m_category_value;
	}
}
