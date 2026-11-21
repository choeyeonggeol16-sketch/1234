package l1j.server.server.model.world.index;

public enum StampIndex {
	SIDX_DUNGEON_TIME_TRUNCATE(1);
	
	private int _val;
	StampIndex(int val){
		_val = val;
	}
	
	public int to_int(){
		return _val;
	}
	
	public boolean equals(StampIndex stamp){
		return to_int() == stamp.to_int();
	}
	
	public static StampIndex from_int(int i){
		StampIndex[] indices = StampIndex.values();
		for(StampIndex stamp : indices){
			if(stamp.to_int() == i)
				return stamp;
		}
		return null;
	}
}
