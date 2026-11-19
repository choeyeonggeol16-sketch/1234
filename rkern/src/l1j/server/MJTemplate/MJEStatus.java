package l1j.server.MJTemplate;

public enum MJEStatus {
	CLOSED(0),
	READY(1),
	RUNNING(2),
	DISPOSING(3),
	COMPLETED(4);
	private int _val;
	MJEStatus(int val){
		_val = val;
	}
	
	public int toInt(){
		return _val;
	}
	
	public boolean equals(MJEStatus status){
		return _val == status.toInt();
	}
	
	public static MJEStatus fromInt(int val){
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
