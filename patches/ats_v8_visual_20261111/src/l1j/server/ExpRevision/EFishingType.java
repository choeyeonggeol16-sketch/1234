package l1j.server.ExpRevision;

public enum EFishingType {
	GROWN_UP(0, "º∫¿Â≥¨Ω√"),
	HIGH_GROWN_UP(1, "∞Ì±ﬁº∫¿Â≥¨Ω√");
	
	int m_val;
	String m_name;
	EFishingType(int val, String name){
		m_val = val;
		m_name = name;
	}
	
	public int to_val(){
		return m_val;
	}
	public String to_name(){
		return m_name;
	}
	public static EFishingType from_name(String name){
		for(EFishingType f_type : values()){
			if(f_type.to_name().equals(name))
				return f_type;
		}
		return null;
	}
	public static EFishingType from_int(int val){
		for(EFishingType f_type : values()){
			if(f_type.to_val() == val)
				return f_type;
		}
		return null;
	}
}
