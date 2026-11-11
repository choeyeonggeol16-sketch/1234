package l1j.server.DogFightSystem;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import l1j.server.Template.SqlHelper.Executors.Selector;
import l1j.server.Template.SqlHelper.Handler.FullSelectorHandler;

public class DogFightCharacters {
	private static ArrayList<DogFightCharacters> m_character;
	public static void do_load(){
		final ArrayList<DogFightCharacters> skills = new ArrayList<DogFightCharacters>();
		Selector.exec("select * from gamble_dogfight_characters", new FullSelectorHandler(){
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()){
					DogFightCharacters o = newInstance(rs);
					skills.add(o);
				}
			}
		});
		m_character = skills;
	}
	
	public static ArrayList<DogFightCharacters> character_info(){
		return m_character;
	}

	private static DogFightCharacters newInstance(ResultSet rs) throws SQLException{
		return newInstance()
				.set_char_num(rs.getInt("num"))
				.set_char_gfx(rs.getInt("gfx"))
				.set_char_name(rs.getString("name"))
				.set_max_hp(rs.getInt("max_hp"))
				.set_min_dmg(rs.getInt("min_dmg"))
				.set_max_dmg(rs.getInt("max_dmg"))
				.set_skill_percent(rs.getInt("skill_percent"))
				.set_skill_min_dmg(rs.getInt("skill_min_dmg"))
				.set_skill_max_dmg(rs.getInt("skill_max_dmg"))
				.set_attacker_effect(rs.getInt("attacker_effect"))
				.set_win_effect(rs.getInt("win_effect"));
	}

	private static DogFightCharacters newInstance(){
		return new DogFightCharacters();
	}

	private int m_char_num;
	private int m_char_gfx;
	private String m_char_name;
	private int m_max_hp;
	private int m_min_dmg;
	private int m_max_dmg;
	private int m_skill_percent;
	private int m_skill_min_dmg;
	private int m_skill_max_dmg;
	private int m_attacker_effect;
	private int m_win_effect;
	private DogFightCharacters(){}
	
	public DogFightCharacters set_char_num(int num){
		m_char_num = num;
		return this;
	}
	public DogFightCharacters set_char_gfx(int gfx){
		m_char_gfx = gfx;
		return this;
	}
	public DogFightCharacters set_char_name(String name){
		m_char_name = name;
		return this;
	}
	public DogFightCharacters set_max_hp(int hp){
		m_max_hp = hp;
		return this;
	}
	public DogFightCharacters set_min_dmg(int min_dmg){
		m_min_dmg = min_dmg;
		return this;
	}
	public DogFightCharacters set_max_dmg(int max_dmg){
		m_max_dmg = max_dmg;
		return this;
	}
	public DogFightCharacters set_skill_percent(int skill_id){
		m_skill_percent = skill_id;
		return this;
	}
	public DogFightCharacters set_skill_min_dmg(int min_dmg){
		m_skill_min_dmg = min_dmg;
		return this;
	}
	public DogFightCharacters set_skill_max_dmg(int max_dmg){
		m_skill_max_dmg = max_dmg;
		return this;
	}
	public DogFightCharacters set_attacker_effect(int attacker_effect){
		m_attacker_effect = attacker_effect;
		return this;
	}
	public DogFightCharacters set_win_effect(int win_effect){
		m_win_effect = win_effect;
		return this;
	}
	public int get_char_num(){
		return m_char_num;
	}
	public int get_char_gfx(){
		return m_char_gfx;
	}
	public String get_char_name(){
		return m_char_name;
	}	
	public int get_max_hp(){
		return m_max_hp;
	}
	public int get_min_dmg(){
		return m_min_dmg;
	}
	public int get_max_dmg(){
		return m_max_dmg;
	}
	public int get_skill_percent(){
		return m_skill_percent;
	}
	public int get_skill_min_dmg(){
		return m_skill_min_dmg;
	}
	public int get_skill_max_dmg(){
		return m_skill_max_dmg;
	}
	public int get_attacker_effect(){
		return m_attacker_effect;
	}
	public int get_win_effect(){
		return m_win_effect;
	}
}
