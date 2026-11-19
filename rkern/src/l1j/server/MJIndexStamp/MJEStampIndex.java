package l1j.server.MJIndexStamp;

public enum MJEStampIndex {
	SIDX_DUNGEON_TIME_TRUNCATE(1);
	
	private int _val;
	MJEStampIndex(int val){
		_val = val;
	}
	
	public int to_int(){
		return _val;
	}
	
	public boolean equals(MJEStampIndex stamp){
		return to_int() == stamp.to_int();
	}
	
	public static MJEStampIndex from_int(int i){
		MJEStampIndex[] indices = MJEStampIndex.values();
		for(MJEStampIndex stamp : indices){
			if(stamp.to_int() == i)
				return stamp;
		}
		return null;
	}
}
