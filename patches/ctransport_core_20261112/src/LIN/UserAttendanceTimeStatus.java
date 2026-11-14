package LIN;




// TODO : 자동으로 생성된 PROTO 코드입니다. made by MJSoft.
public enum UserAttendanceTimeStatus{
	ATTENDANCE_NORMAL(0),
	ATTENDANCE_NO_MORE_TIME(1),
	ATTENDANCE_NO_MORE_REWARD(2),
	ATTENDANCE_CANT_BE_ACHIEVE_TIME(3),
	ATTENDANCE_ALL_CLEAR(4),
	ATTENDANCE_ALL_COMPLETE(5);
	private int value;
	UserAttendanceTimeStatus(int val){
		value = val;
	}
	public int toInt(){
		return value;
	}
	public boolean equals(LIN.UserAttendanceTimeStatus v){
		return value == v.value;
	}
	public static LIN.UserAttendanceTimeStatus fromInt(int i){
		switch(i){
		case 0:
			return ATTENDANCE_NORMAL;
		case 1:
			return ATTENDANCE_NO_MORE_TIME;
		case 2:
			return ATTENDANCE_NO_MORE_REWARD;
		case 3:
			return ATTENDANCE_CANT_BE_ACHIEVE_TIME;
		case 4:
			return ATTENDANCE_ALL_CLEAR;
		case 5:
			return ATTENDANCE_ALL_COMPLETE;
		default:
			return null;
		}
	}
}
