package l1j.server.server.shared.geometry;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1WorldMap;
import l1j.server.server.shared.GameRandom;

public class GamePoint {
	public static boolean isValidPosition(L1Map m, int x, int y){
		return m.isInMap(x, y) && m.isPassable(x, y);
	}
	
	public static GamePoint newInstance(int x, int y, int r, short mapid){
		return newInstance(x, y, r, mapid, 50);
	}
	
	public static GamePoint newInstance(int x, int y, int r, short mapid, int limit_try){
		if(r <= 0)
			return new GamePoint().setX(x).setY(y).setMapId(mapid);
		
		int cx = 0, cy = 0, current_try = 0;
		L1Map m	= L1WorldMap.getInstance().getMap(mapid);
		do{
			cx = x + (GameRandom.isBoolean() ? GameRandom.next(r) : -GameRandom.next(r));
			cy = y + (GameRandom.isBoolean() ? GameRandom.next(r) : -GameRandom.next(r));
		}while(++current_try < limit_try && !isValidPosition(m, cx, cy));
		
		if(current_try >= limit_try){
			cx = x;
			cy = y;
		}
		
		return newInstance().setX(cx).setY(cy).setMapId(mapid);
	}
	
	public static GamePoint newInstance(){
		return new GamePoint();
	}
	
	public int x;
	public int y;
	public short mapId;
	
	public GamePoint setX(int x){
		this.x = x;
		return this;
	}
	public GamePoint setY(int y){
		this.y = y;
		return this;
	}
	public GamePoint setMapId(short mapId){
		this.mapId = mapId;
		return this;
	}
	
	public GamePoint clone(int range, int maxTry){
		return newInstance(x, y, range, mapId, maxTry);
	}
	
	public GamePoint clone(int range){
		return newInstance(x, y, range, mapId);
	}
	
	public GamePoint clone(){
		return GamePoint.newInstance().setX(x).setY(y).setMapId(mapId);
	}
	
	public void do_teleport(L1PcInstance pc){
		do_teleport(pc, mapId);
	}
	
	public void do_teleport(L1PcInstance pc, short mId){
		pc.do_simple_teleport(x, y, mId);
	}
	
	public int to_random_x(int radius){
		return x + (GameRandom.isBoolean() ? GameRandom.next(radius) : -GameRandom.next(radius));
	}
	
	public int to_random_y(int radius){
		return y + (GameRandom.isBoolean() ? GameRandom.next(radius) : -GameRandom.next(radius));
	}
	
	public GamePoint deep_copy(GamePoint pt){
		pt.x = x;
		pt.y = y;
		pt.mapId = mapId;
		return pt;
	}
}

