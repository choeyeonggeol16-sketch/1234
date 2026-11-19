package l1j.server.MJExpRevision;

public enum MJEFishingType {
	GROWN_UP(0, "성장낚시"),
	HIGH_GROWN_UP(1, "고급성장낚시"),
	ACIENT_SILVER(2, "고대은빛낚시"),
	ACIENT_GOLD(3, "고대금빛낚시");
	
	int m_val;
	String m_name;
	MJEFishingType(int val, String name){
		m_val = val;
		m_name = name;
	}
	
	public int to_val(){
		return m_val;
	}
	public String to_name(){
		return m_name;
	}
	public static MJEFishingType from_name(String name){
		for(MJEFishingType f_type : values()){
			if(f_type.to_name().equals(name))
				return f_type;
		}
		return null;
	}
	public static MJEFishingType from_int(int val){
		for(MJEFishingType f_type : values()){
			if(f_type.to_val() == val)
				return f_type;
		}
		return null;
	}
}
