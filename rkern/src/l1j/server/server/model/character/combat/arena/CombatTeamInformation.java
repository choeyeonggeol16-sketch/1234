package l1j.server.server.model.character.combat.arena;

import java.sql.ResultSet;
import java.sql.SQLException;

import l1j.server.server.packets.Protocol.MainServer.Proto.SC_BOX_ATTR_CHANGE_NOTI_PACKET.Box;
import l1j.server.server.shared.geometry.GameRectangle;

public class CombatTeamInformation {
	public static CombatTeamInformation newInstance(ResultSet rs) throws SQLException{
		return newInstance()
			.set_combat_id(rs.getInt("combat_id"))
			.set_team_id(rs.getInt("team_id"))
			.set_mark_id(rs.getInt("mark_id"))
			.set_rt_position(GameRectangle.from_radius(rs.getInt("x"), rs.getInt("y"), rs.getInt("w_radius"), rs.getInt("h_radius"), rs.getShort("map_id")))
			.set_is_use(rs.getBoolean("is_use"));
	}
	
	public static CombatTeamInformation newInstance(){
		return new CombatTeamInformation();
	}
	
	private int _combat_id;
	private int _team_id;
	private int _mark_id;
	private GameRectangle _rt_position;
	private boolean _is_use;
	
	private CombatTeamInformation(){}
	
	public CombatTeamInformation set_combat_id(int combat_id){
		_combat_id = combat_id;
		return this;
	}
	
	public int get_combat_id(){
		return _combat_id;
	}
	
	public CombatTeamInformation set_team_id(int team_id){
		_team_id = team_id;
		return this;
	}
	
	public int get_team_id(){
		return _team_id;
	}
	
	public CombatTeamInformation set_mark_id(int mark_id){
		_mark_id = mark_id;
		return this;
	}
	
	public int get_mark_id(){
		return _mark_id;
	}
	
	public CombatTeamInformation set_rt_position(GameRectangle rt_position){
		_rt_position = rt_position;
		return this;
	}
	
	public GameRectangle get_rt_position(){
		return _rt_position;
	}
	
	public Box[] create_line_boxes(){
		return _rt_position.to_line_box();
	}
	
	public Box create_box(){
		return _rt_position.to_box();
	}
	
	public CombatTeamInformation set_is_use(boolean is_use){
		_is_use = is_use;
		return this;
	}
	
	public boolean is_use(){
		return _is_use;
	}
}
