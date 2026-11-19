package l1j.server.MJTemplate;

public enum MJEPcStatus {
	NONE(-1),
	WORLD(0),
	COMBAT_FIELD(1);
	
	private int _val;
	MJEPcStatus(int val){
		_val = val;
	}
	
	public int toInt(){
		return _val;
	}
	
	public boolean equals(MJEPcStatus status){
		return _val == status.toInt();
	}
	
	public static MJEPcStatus fromInt(int val){
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
