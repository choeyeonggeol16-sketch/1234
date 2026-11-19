package l1j.server.MJCompanion.Basic.Stat;

public enum MJCompanionStatType {
	STR(0),
	CON(1),
	INTEL(2);
	
	private int m_val;
	MJCompanionStatType(int val){
		m_val = val;
	}
	
	public int to_int(){
		return m_val;
	}
	
	public static MJCompanionStatType from_int(int val){
		switch(val){
		case 0:
			return STR;
		case 1:
			return CON;
		case 2:
			return INTEL;
		}
		return null;
	}
	
	public static MJCompanionStatType from_string(String type){
		switch(type.toUpperCase()){
		case "STR":
			return STR;
		case "CON":
			return CON;
		case "INTEL":
			return INTEL;
		}
		
		throw new IllegalArgumentException(String.format("not found companion stat type...%s", type));
	}
}
