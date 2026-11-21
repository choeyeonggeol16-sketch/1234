package l1j.server.server.model.character.combat.arena.dogfight.game;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

import l1j.server.server.model.character.combat.arena.dogfight.instance.DogFightInstance;
import l1j.server.server.model.character.companion.basic.classinfo.CompanionClassInfo;
import l1j.server.server.storage.sql.executors.Selector;
import l1j.server.server.storage.sql.handler.FullSelectorHandler;

public class DogFightGameInfo {
	private volatile static HashMap<Integer, DogFightGameInfo> m_games_info;
	public static void do_load(){
		HashMap<Integer, DogFightGameInfo> games_info = new HashMap<Integer, DogFightGameInfo>();
		Selector.exec("select * from dogfight_game_info", new FullSelectorHandler(){
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()){
					DogFightGameInfo gInfo = newInstance(rs);
					games_info.put(gInfo.get_game_id(), gInfo);
				}
			}
		});
		m_games_info = games_info;
	}
	
	public static DogFightGameInfo get_game_info(int game_id){
		return m_games_info.get(game_id);
	}
	
	private static DogFightGameInfo newInstance(ResultSet rs) throws SQLException{
		return newInstance()
				.set_game_id(rs.getInt("game_id"))
				.set_game_name(rs.getString("game_name"))
				.set_ready_seconds(rs.getInt("ready_seconds"))
				.set_ready_remain_message_cutline(rs.getInt("ready_remain_message_cutline"))
				.load_fighters_info();
	}

	private static DogFightGameInfo newInstance(){
		return new DogFightGameInfo();
	}

	private int m_game_id;
	private String m_game_name;
	private int m_ready_seconds;
	private int m_ready_remain_message_cutline;
	private HashMap<Integer, ArrayList<DogFightFighterInfo>> m_fighters_info;
	private DogFightGameInfo(){}

	public DogFightGameInfo set_game_id(int game_id){
		m_game_id = game_id;
		return this;
	}
	public DogFightGameInfo set_game_name(String game_name){
		m_game_name = game_name;
		return this;
	}
	public DogFightGameInfo set_ready_seconds(int ready_seconds){
		m_ready_seconds = ready_seconds;
		return this;
	}
	public DogFightGameInfo set_ready_remain_message_cutline(int ready_remain_message_cutline){
		m_ready_remain_message_cutline = ready_remain_message_cutline;
		return this;
	}
	public int get_game_id(){
		return m_game_id;
	}
	public String get_game_name(){
		return m_game_name;
	}
	public int get_ready_seconds(){
		return m_ready_seconds;
	}
	public int get_ready_remain_message_cutline(){
		return m_ready_remain_message_cutline;
	}
	public HashMap<Integer, ArrayList<DogFightFighterInfo>> get_fighters_info(){
		return m_fighters_info;
	}
	public Set<Integer> get_fighters_keys(){
		return m_fighters_info.keySet();
	}
	public DogFightGameInfo load_fighters_info(){
		m_fighters_info = DogFightFighterInfo.do_load(get_game_id());
		return this;
	}
	public String get_corner_name(int corner_id){
		return m_fighters_info.get(corner_id).get(0).get_corner_name();
	}
	public ArrayList<DogFightInstance> create_fighters(ArrayList<CompanionClassInfo> classes_info){
		int classes_index = 0;
		int classes_size = classes_info.size();
		ArrayList<DogFightInstance> fighters = new ArrayList<DogFightInstance>();
		for(ArrayList<DogFightFighterInfo> fighters_info : m_fighters_info.values()){
			for(DogFightFighterInfo fInfo : fighters_info)
				fighters.add(fInfo.create_fight_instance(classes_info.get(++classes_index % classes_size)));
		}
		return fighters;
	}
}

