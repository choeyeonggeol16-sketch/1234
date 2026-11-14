package LIN;




// TODO : 자동으로 생성된 PROTO 코드입니다. made by MJSoft.
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
	public boolean equals(LIN.UserAttendanceState v){
		return value == v.value;
	}
	public static LIN.UserAttendanceState fromInt(int i){
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
