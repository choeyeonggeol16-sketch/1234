package l1j.server.DogFight.Game.Trigger;

public enum MJDogFightTiggerType {
	_3SECONDS_BEFORE_THE_START(0, "3SECONDS_BEFORE_THE_START"),
	ON_GAME_START(1, "ON_GAME_START"),
	ON_GAME_WIN(2, "ON_GAME_WIN"),
	ON_GAME_LOSE(3, "ON_GAME_LOSE");
	
	private int m_val;
	private String m_name;
	MJDogFightTiggerType(int val, String name){
		m_val = val;
		m_name = name;
	}
	
	public int to_int(){
		return m_val;
	}
	public String to_string(){
		return m_name;
	}
	public static final int TRIGGER_INT_3SECONDS_BEFORE_THE_START = 0;
	public static final int TRIGGER_INT_ON_GAME_START = 1;
	public static final int TRIGGER_INT_ON_GAME_WIN = 2;
	public static final int TRIGGER_INT_ON_GAME_LOSE = 3;
			
	
	public static MJDogFightTiggerType from_string(String name){
		for(MJDogFightTiggerType t : values()){
			if(t.to_string().equalsIgnoreCase(name))
				return t;
		}
		throw new IllegalArgumentException(String.format("invalid MJDogFightTiggerType::from_string... %s", name));
	}
	public static MJDogFightTiggerType from_int(int val){
		for(MJDogFightTiggerType t : values()){
			if(t.to_int() == val)
				return t;
		}
		throw new IllegalArgumentException(String.format("invalid MJDogFightTiggerType::from_int... %d", val));
	}
}
