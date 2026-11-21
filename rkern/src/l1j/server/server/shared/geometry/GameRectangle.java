package l1j.server.server.shared.geometry;

import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1WorldMap;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_BOX_ATTR_CHANGE_NOTI_PACKET;

public class GameRectangle extends GamePoint{
	public static GameRectangle from_lefttop(int left, int top, int width, int height, short mapId){
		return newInstance(left, top, left + width, top + height);
	}
	
	public static GameRectangle from_radius(int x, int y, int w_radius, int h_radius, short mapId){
		return newInstance(x - w_radius, y - h_radius, x + w_radius, y + h_radius, mapId);
	}
	
	public static GameRectangle newInstance(int left, int top, int right, int bottom, short mapId){
		GameRectangle rect = newInstance(left, top, right, bottom);
		rect.mapId = mapId;
		return rect;
	}
	
	public static GameRectangle newInstance(int left, int top, int right, int bottom){
		GameRectangle rect = newInstance();
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
	
	public static GameRectangle newInstance(){
		return new GameRectangle();
	}
	
	public int left;
	public int top;
	public int right;
	public int bottom;
	public int w_radius;
	public int h_radius;
	
	private GameRectangle(){}
	
	public GamePoint toRandPoint(int limit_try){
		int current_try = 0, cx = 0, cy = 0;
		
		L1Map m = L1WorldMap.getInstance().getMap(mapId);
		do{
			cx = to_random_x();
			cy = to_random_y();
		}while(++current_try < limit_try && !isValidPosition(m, cx, cy));
		
		if(current_try >= limit_try)
			return clone();
		
		return GamePoint.newInstance().setX(cx).setY(cy).setMapId(mapId);
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
	
	public SC_BOX_ATTR_CHANGE_NOTI_PACKET.Box[] to_line_box(){
	    SC_BOX_ATTR_CHANGE_NOTI_PACKET.Box[] boxes = new SC_BOX_ATTR_CHANGE_NOTI_PACKET.Box[4];
	    SC_BOX_ATTR_CHANGE_NOTI_PACKET.Box box = SC_BOX_ATTR_CHANGE_NOTI_PACKET.Box.of(left, top, right, top + 1);
	    boxes[0] = box;

	    box = SC_BOX_ATTR_CHANGE_NOTI_PACKET.Box.of(right - 1, top, right, bottom);
	    boxes[1] = box;

	    box = SC_BOX_ATTR_CHANGE_NOTI_PACKET.Box.of(left, bottom - 1, right, bottom);
	    boxes[2] = box;

	    box = SC_BOX_ATTR_CHANGE_NOTI_PACKET.Box.of(left, top, left + 1, bottom);
	    boxes[3] = box;
	    return boxes;
	}

	public SC_BOX_ATTR_CHANGE_NOTI_PACKET.Box to_box(){
	    return SC_BOX_ATTR_CHANGE_NOTI_PACKET.Box.of(left, top, right, bottom);
	}
	
	public GameRectangle deep_copy(GameRectangle rt){
		rt.left = left;
		rt.top = top;
		rt.right = right;
		rt.bottom = bottom;
		rt.w_radius = rt.w_radius;
		rt.h_radius = rt.h_radius;
		return (GameRectangle) super.deep_copy(rt);
	}
}
