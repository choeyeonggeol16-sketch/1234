package l1j.server.server.model.character.companion.basic.joke;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;

import l1j.server.server.storage.sql.executors.Selector;
import l1j.server.server.storage.sql.handler.FullSelectorHandler;
import l1j.server.server.utils.IntRange;

public class CompanionJokeInfo {
	private static HashMap<String, ArrayList<CompanionJokeInfo>> m_joke_info;
	public static void do_load(){
		final HashMap<String, ArrayList<CompanionJokeInfo>> joke_info = new HashMap<String, ArrayList<CompanionJokeInfo>>();
		Selector.exec("select * from companion_joke_effect", new FullSelectorHandler(){
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()){
					CompanionJokeInfo jInfo = newInstance(rs);
					ArrayList<CompanionJokeInfo> jokes = joke_info.get(jInfo.get_class_name());
					if(jokes == null){
						jokes = new ArrayList<CompanionJokeInfo>();
						joke_info.put(jInfo.get_class_name(), jokes);
					}
					jokes.add(jInfo);
				}
			}
		});
		m_joke_info = joke_info;
	}
	
	public static CompanionJokeInfo find_joke_info(String class_name, int level){
		ArrayList<CompanionJokeInfo> jokes = m_joke_info.get(class_name);
		if(jokes == null)
			return null;
		
		for(CompanionJokeInfo jInfo : jokes){
			if(IntRange.includes(level, jInfo.get_min_level(), jInfo.get_max_level()))
				return jInfo;
		}
		return null;
	}
	
	
	private static CompanionJokeInfo newInstance(ResultSet rs) throws SQLException{
		return newInstance()
				.set_class_name(rs.getString("class_name"))
				.set_min_level(rs.getInt("min_level"))
				.set_max_level(rs.getInt("max_level"))
				.set_joke_effect(rs.getInt("joke_effect"));
	}
	
	private static CompanionJokeInfo newInstance(){
		return new CompanionJokeInfo();
	}
	
	private String m_class_name;
	private int m_min_level;
	private int m_max_level;
	private int m_joke_effect;
	
	private CompanionJokeInfo(){}
	
	public CompanionJokeInfo set_class_name(String class_name){
		m_class_name = class_name;
		return this;
	}
	public CompanionJokeInfo set_min_level(int min_level){
		m_min_level = min_level;
		return this;
	}
	public CompanionJokeInfo set_max_level(int max_level){
		m_max_level = max_level;
		return this;
	}
	public CompanionJokeInfo set_joke_effect(int joke_effect){
		m_joke_effect = joke_effect;
		return this;
	}
	
	public String get_class_name(){
		return m_class_name;
	}
	public int get_min_level(){
		return m_min_level;
	}
	public int get_max_level(){
		return m_max_level;
	}
	public int get_joke_effect(){
		return m_joke_effect;
	}
}	
