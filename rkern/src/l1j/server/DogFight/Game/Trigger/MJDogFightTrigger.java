package l1j.server.DogFight.Game.Trigger;

import java.sql.ResultSet;
import java.sql.SQLException;

public class MJDogFightTrigger {
	public static MJDogFightTrigger newInstance(ResultSet rs) throws SQLException{
		return newInstance()
				.set_trigger_type(MJDogFightTiggerType.from_string(rs.getString("trigger_type")))
				.set_game_id(rs.getInt("game_id"))
				.set_corner_id(rs.getInt("corner_id"))
				.set_member_id(rs.getInt("member_id"))
				.set_move_x(rs.getInt("move_x"))
				.set_move_y(rs.getInt("move_y"))
				.set_is_move_ended_send(rs.getInt("is_move_ended_send") != 0)
				.set_send_action(rs.getInt("send_action"))
				.set_send_effect(rs.getInt("send_effect"))
				.set_send_message(rs.getString("send_message"));
	}

	private static MJDogFightTrigger newInstance(){
		return new MJDogFightTrigger();
	}

	private MJDogFightTiggerType m_trigger_type;
	private int m_game_id;
	private int m_corner_id;
	private int m_member_id;
	private int m_move_x;
	private int m_move_y;
	private boolean m_is_move_ended_send;
	private int m_send_action;
	private int m_send_effect;
	private String m_send_message;
	private MJDogFightTrigger(){}

	public MJDogFightTrigger set_trigger_type(MJDogFightTiggerType trigger_type){
		m_trigger_type = trigger_type;
		return this;
	}
	public MJDogFightTrigger set_game_id(int game_id){
		m_game_id = game_id;
		return this;
	}
	public MJDogFightTrigger set_corner_id(int corner_id){
		m_corner_id = corner_id;
		return this;
	}
	public MJDogFightTrigger set_member_id(int member_id){
		m_member_id = member_id;
		return this;
	}
	public MJDogFightTrigger set_move_x(int move_x){
		m_move_x = move_x;
		return this;
	}
	public MJDogFightTrigger set_move_y(int move_y){
		m_move_y = move_y;
		return this;
	}
	public MJDogFightTrigger set_is_move_ended_send(boolean is_move_ended_send){
		m_is_move_ended_send = is_move_ended_send;
		return this;
	}
	public MJDogFightTrigger set_send_action(int send_action){
		m_send_action = send_action;
		return this;
	}
	public MJDogFightTrigger set_send_effect(int send_effect){
		m_send_effect = send_effect;
		return this;
	}
	public MJDogFightTrigger set_send_message(String send_message){
		m_send_message = send_message;
		return this;
	}
	public MJDogFightTiggerType get_trigger_type(){
		return m_trigger_type;
	}
	public int get_game_id(){
		return m_game_id;
	}
	public int get_corner_id(){
		return m_corner_id;
	}
	public int get_member_id(){
		return m_member_id;
	}
	public int get_move_x(){
		return m_move_x;
	}
	public int get_move_y(){
		return m_move_y;
	}
	public boolean get_is_move_ended_send(){
		return m_is_move_ended_send;
	}
	public int get_send_action(){
		return m_send_action;
	}
	public int get_send_effect(){
		return m_send_effect;
	}
	public String get_send_message(){
		return m_send_message;
	}

}
