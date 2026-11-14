package LIN;




public enum AttendanceUserFlag{
	USER_FLAG_NORMAL(0),
	USER_FLAG_PC_CAFE(1);
	private int value;
	AttendanceUserFlag(int val){
		value = val;
	}
	public int toInt(){
		return value;
	}
	public boolean equals(LIN.AttendanceUserFlag v){
		return value == v.value;
	}
	public static LIN.AttendanceUserFlag fromInt(int i){
		switch(i){
		case 0:
			return USER_FLAG_NORMAL;
		case 1:
			return USER_FLAG_PC_CAFE;
		default:
			return null;
		}
	}
}
