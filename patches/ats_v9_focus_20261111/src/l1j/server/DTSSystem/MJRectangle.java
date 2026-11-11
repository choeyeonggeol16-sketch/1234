package l1j.server.DTSSystem;

import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1WorldMap;

public class MJRectangle extends MJPoint{
	public static MJRectangle from_lefttop(int left, int top, int width, int height, short mapId){
		return newInstance(left, top, left + width, top + height);
	}
	
	public static MJRectangle from_radius(int x, int y, int w_radius, int h_radius, short mapId){
		return newInstance(x - w_radius, y - h_radius, x + w_radius, y + h_radius, mapId);
	}
	
	public static MJRectangle newInstance(int left, int top, int right, int bottom, short mapId){
		MJRectangle rect = newInstance(left, top, right, bottom);
		rect.mapId = mapId;
		return rect;
	}
	
	public static MJRectangle newInstance(int left, int top, int right, int bottom){
		MJRectangle rect = newInstance();
		rect.left = left;
		rect.top = top;
		rect.right = right;
		rect.bottom = bottom;
		rect.w_radius = (right - left) / 2;
		rect.h_radius = (bottom - top) / 2;
		rect.x = rect.w_radius + left;
		rect.y = rect.h_radius + top;
		return rect;
	}
	
	public static MJRectangle newInstance(){
		return new MJRectangle();
	}
	
	public int left;
	public int top;
	public int right;
	public int bottom;
	public int w_radius;
	public int h_radius;
	
	private MJRectangle(){}
	
	public MJPoint toRandPoint(int limit_try){
		int current_try = 0, cx = 0, cy = 0;
		
		L1Map m = L1WorldMap.getInstance().getMap(mapId);
		do{
			cx = to_random_x();
			cy = to_random_y();
		}while(++current_try < limit_try && !isValidPosition(m, cx, cy));
		
		if(current_try >= limit_try)
			return clone();
		
		return MJPoint.newInstance().setX(cx).setY(cy).setMapId(mapId);
	}
	
	public void reduce(){
		++left;
		++top;
		--right;
		--bottom;
	}
	
	public int width(){
		return right - left;
	}
	
	public int height(){
		return bottom - top;
	}
	
	public int to_random_x(){
		return to_random_x(w_radius);
	}
	
	public int to_random_y(){
		return to_random_y(h_radius);
	}
	 
	
	public MJRectangle deep_copy(MJRectangle rt){
		rt.left = left;
		rt.top = top;
		rt.right = right;
		rt.bottom = bottom;
		rt.w_radius = rt.w_radius;
		rt.h_radius = rt.h_radius;
		return (MJRectangle) super.deep_copy(rt);
	}
}
