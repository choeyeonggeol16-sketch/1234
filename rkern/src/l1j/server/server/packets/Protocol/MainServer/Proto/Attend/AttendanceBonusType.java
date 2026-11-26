package l1j.server.server.packets.Protocol.MainServer.Proto.Attend;


public enum AttendanceBonusType{
	UseItem(1),
	GiveItem(2);
	private int value;
	AttendanceBonusType(int val){
		value = val;
	}
	public int toInt(){
		return value;
	}
	public boolean equals(AttendanceBonusType v){
		return value == v.value;
	}
	public static AttendanceBonusType fromInt(int i){
		switch(i){
		case 1:
			return UseItem;
		case 2:
			return GiveItem;
		default:
			return null;
		}
	}
}
