package l1j.server.MJTemplate.Kda;

public class UserKda extends TeamKda{
	public static UserKda newInstance(int team_id, int character_id, String character_name){
		return (UserKda) newInstance()
				.set_character_id(character_id)
				.set_character_name(character_name)
				.set_team_id(team_id); 
	}
	
	public static UserKda newInstance(){
		return new UserKda(); 
	}
	
	private int _character_id;
	private String _character_name;
	protected UserKda(){}
	
	public UserKda set_character_id(int character_id){
		_character_id = character_id;
		return this;
	}
	
	public int get_character_id(){
		return _character_id;
	}
	
	public UserKda set_character_name(String character_name){
		_character_name = character_name;
		return this;
	}
	
	public String get_character_name(){
		return _character_name;
	}
}
