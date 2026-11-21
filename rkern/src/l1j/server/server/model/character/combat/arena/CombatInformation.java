package l1j.server.server.model.character.combat.arena;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import l1j.server.server.packets.Protocol.MainServer.Proto.SC_BOX_ATTR_CHANGE_NOTI_PACKET.Box;
import l1j.server.server.shared.geometry.GameRectangle;
import l1j.server.server.storage.sql.executors.Selector;
import l1j.server.server.storage.sql.handler.SelectorHandler;

public class CombatInformation {
	public static CombatInformation newInstance(ResultSet rs) throws SQLException{
		return newInstance()
				.set_combat_id(rs.getInt("combat_id"))
				.set_game_type(CombatEGameType.from_kr_desc(rs.getString("combat_type")))
				.set_description(rs.getString("description"))
				.set_ready_seconds(rs.getLong("ready_seconds"))
				.set_play_seconds(rs.getLong("play_seconds"))
				.set_team_max_player(rs.getInt("team_max_player"))
				.set_rt_zone(GameRectangle.from_radius(rs.getInt("zone_x"), rs.getInt("zone_y"), rs.getInt("zone_width"), rs.getInt("zone_height"), rs.getShort("zone_map_id")))
				.create_team_informations();	
	}
	
	public static CombatInformation newInstance(){
		return new CombatInformation();
	}
	
	private int _combat_id;
	private CombatEGameType _game_type;
	private String _description;
	private long _ready_seconds;
	private long _play_seconds;
	private int _team_max_player;
	private GameRectangle _rt_zone;
	private ArrayList<CombatTeamInformation> _team_informations;
	
	
	public CombatInformation deep_copy(CombatInformation destination){
		return destination
			.set_combat_id(get_combat_id())
			.set_game_type(get_game_type())
			.set_description(get_description())
			.set_ready_seconds(get_ready_seconds())
			.set_play_seconds(get_play_seconds())
			.set_team_max_player(get_team_max_player())
			.set_rt_zone(get_rt_zone().deep_copy(GameRectangle.newInstance()))
			.set_team_informations(get_team_informations());
	}
	
	public CombatInformation set_combat_id(int combat_id){
		_combat_id = combat_id;
		return this;
	}
	
	public int get_combat_id(){
		return _combat_id;
	}
	
	public CombatInformation set_game_type(CombatEGameType game_type){
		_game_type = game_type;
		return this;
	}
	
	public CombatEGameType get_game_type(){
		return _game_type;
	}
	
	public CombatInformation set_description(String description){
		_description = description;
		return this;
	}
	
	public String get_description(){
		return _description;
	}
	
	public CombatInformation set_ready_seconds(long ready_seconds){
		_ready_seconds = ready_seconds;
		return this;
	}
	
	public long get_ready_seconds(){
		return _ready_seconds;
	}
	
	public CombatInformation set_play_seconds(long play_seconds){
		_play_seconds = play_seconds;
		return this;
	}
	
	public long get_play_seconds(){
		return _play_seconds;
	}
	
	public CombatInformation set_team_max_player(int team_max_player){
		_team_max_player = team_max_player;
		return this;
	}
	
	public int get_team_max_player(){
		return _team_max_player;
	}
	
	public CombatInformation set_rt_zone(GameRectangle rt_zone){
		_rt_zone = rt_zone;
		return this;
	}
	
	public GameRectangle get_rt_zone(){
		return _rt_zone;
	}
	
	public Box[] create_line_box(){
		return _rt_zone.to_line_box();
	}
	
	public Box create_box(){
		return _rt_zone.to_box();
	}
	
	public CombatInformation create_team_informations(){
		if(_team_informations != null){
			_team_informations.clear();
			_team_informations = null;
		}
		
		Selector.exec("select * from tb_combat_team_informations where combat_id=?", new SelectorHandler(){
			@Override
			public void handle(PreparedStatement pstm) throws Exception {
				pstm.setInt(1, get_combat_id());
			}
			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next())
					add_team_informations(CombatTeamInformation.newInstance(rs));
			}
		});
		return this;
	}
	
	public CombatInformation add_team_informations(CombatTeamInformation team_informations){
		if(_team_informations == null)
			_team_informations = new ArrayList<CombatTeamInformation>(4);
		_team_informations.add(team_informations);
		return this;
	}
	
	public ArrayList<CombatTeamInformation> get_team_informations(){
		return _team_informations;
	}
	
	public CombatInformation set_team_informations(ArrayList<CombatTeamInformation> team_informations){
		_team_informations = team_informations;
		return this;
	}
	
	public CombatTeamInformation get_team_informations(int team_index){
		return _team_informations == null ? null : _team_informations.get(team_index);
	}
	
	public int team_informations_size(){
		return _team_informations == null ? 0 : _team_informations.size();
	}
}
