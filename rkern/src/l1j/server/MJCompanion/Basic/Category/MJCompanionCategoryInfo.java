package l1j.server.MJCompanion.Basic.Category;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;

import l1j.server.MJTemplate.MJSqlHelper.Executors.Selector;
import l1j.server.MJTemplate.MJSqlHelper.Handler.FullSelectorHandler;

public class MJCompanionCategoryInfo {
	private static HashMap<String, MJCompanionCategoryInfo> m_categories;
	public static void do_load(){
		final HashMap<String, MJCompanionCategoryInfo> categories = new HashMap<String, MJCompanionCategoryInfo>();
		Selector.exec("select * from companion_category", new FullSelectorHandler(){

			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()){
					MJCompanionCategoryInfo cInfo = MJCompanionCategoryInfo.newInstance(rs);
					categories.put(cInfo.get_category_name(), cInfo);
				}
			}
		});
		
		m_categories = categories;
	}

	public static MJCompanionCategoryInfo get_category_info(String category_name){
		return m_categories.get(category_name);
	}
	
	private static MJCompanionCategoryInfo newInstance(ResultSet rs) throws SQLException{
		return newInstance()
				.set_category_name(rs.getString("category_name"))
				.set_category_value(rs.getInt("category_value"));
	}
	
	private static MJCompanionCategoryInfo newInstance(){
		return new MJCompanionCategoryInfo();
	}
	
	private String m_category_name;
	private int m_category_value;
	private MJCompanionCategoryInfo(){}
	
	public MJCompanionCategoryInfo set_category_name(String category_name){
		m_category_name = category_name;
		return this;
	}
	public String get_category_name(){
		return m_category_name;
	}
	public MJCompanionCategoryInfo set_category_value(int category_value){
		m_category_value = category_value;
		return this;
	}
	public int get_category_value(){
		return m_category_value;
	}
}
