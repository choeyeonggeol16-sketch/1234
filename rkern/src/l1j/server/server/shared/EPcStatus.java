package l1j.server.server.shared;

public enum EPcStatus {
	NONE(-1),
	WORLD(0),
	COMBAT_FIELD(1);
	
	private int _val;
	EPcStatus(int val){
		_val = val;
	}
	
	public int toInt(){
		return _val;
	}
	
	public boolean equals(EPcStatus status){
		return _val == status.toInt();
	}
	
	public static EPcStatus fromInt(int val){
		switch(val){
		case 0:
			return WORLD;
		case 1:
			return COMBAT_FIELD;
		default:
			return NONE;
		}
	}
}
