package l1j.server.MJCompanion.Basic.ClassInfo;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import l1j.server.MJCompanion.Basic.Category.MJCompanionCategoryInfo;
import l1j.server.MJCompanion.Basic.Elemental.MJCompanionElemental;
import l1j.server.MJTemplate.MJSqlHelper.Executors.Selector;
import l1j.server.MJTemplate.MJSqlHelper.Handler.FullSelectorHandler;

public class MJCompanionClassInfo {
	private static HashMap<String, MJCompanionClassInfo> m_companions;
	public static void do_load(){
		final HashMap<String, MJCompanionClassInfo> companions = new HashMap<String, MJCompanionClassInfo>();
		Selector.exec("select * from companion_class", new FullSelectorHandler(){

			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()){
					MJCompanionClassInfo cInfo = newInstance(rs);
					companions.put(cInfo.get_class_name(), cInfo);
				}
			}
			
		});
		m_companions = companions;
	}
	
	public static MJCompanionClassInfo from_class_name(String class_name){
		return m_companions.get(class_name);
	}
	
	public static ArrayList<MJCompanionClassInfo> get_classes(){
		return new ArrayList<MJCompanionClassInfo>(m_companions.values());
	}
	
	public static ArrayList<MJCompanionClassInfo> get_shuffles_classes(){
		ArrayList<MJCompanionClassInfo> classes = get_classes();
		Collections.shuffle(classes);
		return classes;
	}
	
	public static MJCompanionClassInfo from_npc_id(int npc_id){
		for(MJCompanionClassInfo cInfo : m_companions.values()){
			if(cInfo.get_npc_id() == npc_id)
				return cInfo;
		}
		return null;
	}
	
	public static MJCompanionClassInfo from_item_id(int item_id){
		for(MJCompanionClassInfo cInfo : m_companions.values()){
			if(cInfo.get_item_id() == item_id)
				return cInfo;
		}
		return null;
	}
	
	private static MJCompanionClassInfo newInstance(ResultSet rs) throws SQLException{
		return newInstance()
				.set_class_name(rs.getString("class_name"))
				.set_class_id(rs.getInt("class_id"))
				.set_class_npc_name(rs.getString("class_npc_name"))
				.set_class_npc_name_id(rs.getString("class_npc_name_id"))
				.set_npc_id(rs.getInt("npc_id"))
				.set_item_id(rs.getInt("item_id"))
				.set_category(MJCompanionCategoryInfo.get_category_info(rs.getString("category")))
				.set_elemental(MJCompanionElemental.get_elemental_info(rs.getString("elemental")))
				.set_db_id(rs.getInt("db_id"));
	}
	
	private static MJCompanionClassInfo newInstance(){
		return new MJCompanionClassInfo();
	}
	
	private String m_class_name;
	private int m_class_id;
	private String m_class_npc_name;
	private String m_class_npc_name_id;
	private int m_npc_id;
	private int m_item_id;
	private MJCompanionCategoryInfo m_category;
	private MJCompanionElemental m_elemental;
	private int m_db_id;
	private MJCompanionClassInfo(){}
	
	public MJCompanionClassInfo set_class_name(String class_name){
		m_class_name = class_name;
		return this;
	}
	public String get_class_name(){
		return m_class_name;
	}
	public MJCompanionClassInfo set_class_id(int class_id){
		m_class_id = class_id;
		return this;
	}
	public int get_class_id(){
		return m_class_id;
	}
	public MJCompanionClassInfo set_class_npc_name(String class_npc_name){
		m_class_npc_name = class_npc_name;
		return this;
	}
	public String get_class_npc_name(){
		return m_class_npc_name;
	}
	public MJCompanionClassInfo set_class_npc_name_id(String class_npc_name_id){
		m_class_npc_name_id = class_npc_name_id;
		return this;
	}
	public String get_class_npc_name_id(){
		return m_class_npc_name_id;
	}
	public MJCompanionClassInfo set_npc_id(int npc_id){
		m_npc_id = npc_id;
		return this;
	}
	public MJCompanionClassInfo set_db_id(int db_id){
		m_db_id = db_id;
		return this;
	}
	public int get_npc_id(){
		return m_npc_id;
	}
	public MJCompanionClassInfo set_category(MJCompanionCategoryInfo category){
		m_category = category;
		return this;
	}
	public MJCompanionClassInfo set_item_id(int item_id){
		m_item_id = item_id;
		return this;
	}
	public int get_item_id(){
		return m_item_id;
	}
	public MJCompanionCategoryInfo get_category(){
		return m_category;
	}
	public MJCompanionClassInfo set_elemental(MJCompanionElemental elemental){
		m_elemental = elemental;
		return this;
	}
	public MJCompanionElemental get_elemental(){
		return m_elemental;
	}
	public int get_db_id(){
		return m_db_id;
	}
}
