package l1j.server.server.model.character.companion.basic.classinfo;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;

import l1j.server.server.model.character.companion.basic.category.CompanionCategoryInfo;
import l1j.server.server.model.character.companion.basic.elemental.CompanionElemental;
import l1j.server.server.shared.GameRandom;
import l1j.server.server.storage.sql.executors.Selector;
import l1j.server.server.storage.sql.handler.FullSelectorHandler;

public class CompanionClassInfo {
	private static HashMap<String, CompanionClassInfo> m_companions;
	public static void do_load(){
		final HashMap<String, CompanionClassInfo> companions = new HashMap<String, CompanionClassInfo>();
		Selector.exec("select * from companion_class", new FullSelectorHandler(){

			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()){
					CompanionClassInfo cInfo = newInstance(rs);
					companions.put(cInfo.get_class_name(), cInfo);
				}
			}
			
		});
		m_companions = companions;
	}
	
	public static CompanionClassInfo from_class_name(String class_name){
		return m_companions.get(class_name);
	}
	
	public static ArrayList<CompanionClassInfo> get_classes(){
		return new ArrayList<CompanionClassInfo>(m_companions.values());
	}
	
	public static ArrayList<CompanionClassInfo> get_shuffles_classes(){
		ArrayList<CompanionClassInfo> classes = get_classes();
		GameRandom.shuffle(classes);
		return classes;
	}
	
	public static CompanionClassInfo from_npc_id(int npc_id){
		for(CompanionClassInfo cInfo : m_companions.values()){
			if(cInfo.get_npc_id() == npc_id)
				return cInfo;
		}
		return null;
	}
	
	public static CompanionClassInfo from_item_id(int item_id){
		for(CompanionClassInfo cInfo : m_companions.values()){
			if(cInfo.get_item_id() == item_id)
				return cInfo;
		}
		return null;
	}
	
	private static CompanionClassInfo newInstance(ResultSet rs) throws SQLException{
		return newInstance()
				.set_class_name(rs.getString("class_name"))
				.set_class_id(rs.getInt("class_id"))
				.set_class_npc_name(rs.getString("class_npc_name"))
				.set_class_npc_name_id(rs.getString("class_npc_name_id"))
				.set_npc_id(rs.getInt("npc_id"))
				.set_item_id(rs.getInt("item_id"))
				.set_category(CompanionCategoryInfo.get_category_info(rs.getString("category")))
				.set_elemental(CompanionElemental.get_elemental_info(rs.getString("elemental")))
				.set_db_id(rs.getInt("db_id"));
	}
	
	private static CompanionClassInfo newInstance(){
		return new CompanionClassInfo();
	}
	
	private String m_class_name;
	private int m_class_id;
	private String m_class_npc_name;
	private String m_class_npc_name_id;
	private int m_npc_id;
	private int m_item_id;
	private CompanionCategoryInfo m_category;
	private CompanionElemental m_elemental;
	private int m_db_id;
	private CompanionClassInfo(){}
	
	public CompanionClassInfo set_class_name(String class_name){
		m_class_name = class_name;
		return this;
	}
	public String get_class_name(){
		return m_class_name;
	}
	public CompanionClassInfo set_class_id(int class_id){
		m_class_id = class_id;
		return this;
	}
	public int get_class_id(){
		return m_class_id;
	}
	public CompanionClassInfo set_class_npc_name(String class_npc_name){
		m_class_npc_name = class_npc_name;
		return this;
	}
	public String get_class_npc_name(){
		return m_class_npc_name;
	}
	public CompanionClassInfo set_class_npc_name_id(String class_npc_name_id){
		m_class_npc_name_id = class_npc_name_id;
		return this;
	}
	public String get_class_npc_name_id(){
		return m_class_npc_name_id;
	}
	public CompanionClassInfo set_npc_id(int npc_id){
		m_npc_id = npc_id;
		return this;
	}
	public CompanionClassInfo set_db_id(int db_id){
		m_db_id = db_id;
		return this;
	}
	public int get_npc_id(){
		return m_npc_id;
	}
	public CompanionClassInfo set_category(CompanionCategoryInfo category){
		m_category = category;
		return this;
	}
	public CompanionClassInfo set_item_id(int item_id){
		m_item_id = item_id;
		return this;
	}
	public int get_item_id(){
		return m_item_id;
	}
	public CompanionCategoryInfo get_category(){
		return m_category;
	}
	public CompanionClassInfo set_elemental(CompanionElemental elemental){
		m_elemental = elemental;
		return this;
	}
	public CompanionElemental get_elemental(){
		return m_elemental;
	}
	public int get_db_id(){
		return m_db_id;
	}
}
