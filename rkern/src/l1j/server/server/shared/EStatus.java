package l1j.server.server.shared;

public enum EStatus {
	CLOSED(0),
	READY(1),
	RUNNING(2),
	DISPOSING(3),
	COMPLETED(4);
	private int _val;
	EStatus(int val){
		_val = val;
	}
	
	public int toInt(){
		return _val;
	}
	
	public boolean equals(EStatus status){
		return _val == status.toInt();
	}
	
	public static EStatus fromInt(int val){
		switch(val){
		case 0:
			return CLOSED;
		case 1:
			return READY;
		case 2:
			return RUNNING;
		case 3:
			return DISPOSING;
		default:
			return null;
		}
	}
}
