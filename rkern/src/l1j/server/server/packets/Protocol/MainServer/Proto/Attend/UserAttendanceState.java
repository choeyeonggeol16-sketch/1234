package l1j.server.server.packets.Protocol.MainServer.Proto.Attend;


public enum UserAttendanceState{
	INIT(0),
	COMPLETE(1),
	CLEAR(2);
	private int value;
	UserAttendanceState(int val){
		value = val;
	}
	public int toInt(){
		return value;
	}
	public boolean equals(UserAttendanceState v){
		return value == v.value;
	}
	public static UserAttendanceState fromInt(int i){
		switch(i){
		case 0:
			return INIT;
		case 1:
			return COMPLETE;
		case 2:
			return CLEAR;
		default:
			return null;
		}
	}
}
