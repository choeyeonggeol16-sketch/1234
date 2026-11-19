package l1j.server.MJWarSystem;

public enum MJCastleWarEState {
	IDLE(0),
	READY(1),
	RUN(2),
	CLOSING(3);
	
	private int _value;
	MJCastleWarEState(int i){
		_value = i;
	}
	
	public int toInt(){
		return _value;
	}
	
	public boolean equals(MJCastleWarEState state){
		return _value == state.toInt();
	}
	
	public static MJCastleWarEState fromInt(int i){
		switch(i){
		case 0:
			return IDLE;
		case 1:
			return READY;
		case 2:
			return RUN;
		case 3:
			return CLOSING;
		}
		
		throw new IllegalArgumentException();
	}
}
