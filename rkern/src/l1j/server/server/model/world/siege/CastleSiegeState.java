package l1j.server.server.model.world.siege;

public enum CastleSiegeState {
	IDLE(0),
	READY(1),
	RUN(2),
	CLOSING(3);
	
	private int _value;
	CastleSiegeState(int i){
		_value = i;
	}
	
	public int toInt(){
		return _value;
	}
	
	public boolean equals(CastleSiegeState state){
		return _value == state.toInt();
	}
	
	public static CastleSiegeState fromInt(int i){
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
