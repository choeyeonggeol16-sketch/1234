package l1j.server.MJTemplate.Lineage2D;

import l1j.server.MJTemplate.MJRnd;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1WorldMap;

public class MJPoint {
	public static boolean isValidPosition(L1Map m, int x, int y){
		return m.isInMap(x, y) && m.isPassable(x, y);
	}
	
	public static MJPoint newInstance(int x, int y, int r, short mapid){
		return newInstance(x, y, r, mapid, 50);
	}
	
	public static MJPoint newInstance(int x, int y, int r, short mapid, int limit_try){
		if(r <= 0)
			return new MJPoint().setX(x).setY(y).setMapId(mapid);
		
		int cx = 0, cy = 0, current_try = 0;
		L1Map m	= L1WorldMap.getInstance().getMap(mapid);
		do{
			cx = x + (MJRnd.isBoolean() ? MJRnd.next(r) : -MJRnd.next(r));
			cy = y + (MJRnd.isBoolean() ? MJRnd.next(r) : -MJRnd.next(r));
		}while(++current_try < limit_try && !isValidPosition(m, cx, cy));
		
		if(current_try >= limit_try){
			cx = x;
			cy = y;
		}
		
		return newInstance().setX(cx).setY(cy).setMapId(mapid);
	}
	
	public static MJPoint newInstance(){
		return new MJPoint();
	}
	
	public int x;
	public int y;
	public short mapId;
	
	public MJPoint setX(int x){
		this.x = x;
		return this;
	}
	public MJPoint setY(int y){
		this.y = y;
		return this;
	}
	public MJPoint setMapId(short mapId){
		this.mapId = mapId;
		return this;
	}
	
	public MJPoint clone(int range, int maxTry){
		return newInstance(x, y, range, mapId, maxTry);
	}
	
	public MJPoint clone(int range){
		return newInstance(x, y, range, mapId);
	}
	
	public MJPoint clone(){
		return MJPoint.newInstance().setX(x).setY(y).setMapId(mapId);
	}
	
	public void do_teleport(L1PcInstance pc){
		do_teleport(pc, mapId);
	}
	
	public void do_teleport(L1PcInstance pc, short mId){
		pc.do_simple_teleport(x, y, mId);
	}
	
	public int to_random_x(int radius){
		return x + (MJRnd.isBoolean() ? MJRnd.next(radius) : -MJRnd.next(radius));
	}
	
	public int to_random_y(int radius){
		return y + (MJRnd.isBoolean() ? MJRnd.next(radius) : -MJRnd.next(radius));
	}
	
	public MJPoint deep_copy(MJPoint pt){
		pt.x = x;
		pt.y = y;
		pt.mapId = mapId;
		return pt;
	}
}

