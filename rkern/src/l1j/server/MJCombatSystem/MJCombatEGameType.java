package l1j.server.MJCombatSystem;

public enum MJCombatEGameType {
	NONE(0, "사용안함"),
	BATTLE_FIELD(1, "배틀필드"),
	BATTLE_TOWER(2, "배틀타워"),
	BATTLE_TOURNAMENT(3, "배틀토너먼트");
	
	private int _val;
	private String _kr_desc;
	MJCombatEGameType(int val, String kr_desc){
		_val = val;
		_kr_desc = kr_desc;
	}
	
	public int to_int(){
		return _val;
	}
	
	public String to_kr_desc(){
		return _kr_desc;
	}
	
	public boolean equals(MJCombatEGameType game_type){
		return to_int() == game_type.to_int();
	}
	
	public static MJCombatEGameType from_int(int val){
		switch(val){
		case 0:
			return NONE;
		case 1:
			return BATTLE_FIELD;
		case 2:
			return BATTLE_TOWER;
		}
		
		throw new IllegalArgumentException(String.format("invalid MJCombatEGameType %d", val));
	}
	
	public static MJCombatEGameType from_string(String s){
		MJCombatEGameType[] types = values();
		for(MJCombatEGameType type : types){
			if(type.name().equalsIgnoreCase(s))
				return type;
		}
		
		throw new IllegalArgumentException(String.format("invalid MJCombatEGameType %s", s));		
	}
	
	public static MJCombatEGameType from_kr_desc(String s){
		MJCombatEGameType[] types = values();
		for(MJCombatEGameType type : types){
			if(type.to_kr_desc().equalsIgnoreCase(s))
				return type;
		}
		
		throw new IllegalArgumentException(String.format("invalid MJCombatEGameType %s", s));				
	}
}
