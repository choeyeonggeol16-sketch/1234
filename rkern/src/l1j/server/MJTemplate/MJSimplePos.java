package l1j.server.MJTemplate;

import java.util.Random;

public class MJSimplePos {
	private static final Random _rnd = new Random(System.nanoTime());
	
	public int 		x;
	public int 		y;
	public short 	mapId;
	
	public MJSimplePos setX(int x){
		this.x = x;
		return this;
	}
	public MJSimplePos setY(int y){
		this.y = y;
		return this;
	}
	public MJSimplePos setMapId(short mapId){
		this.mapId = mapId;
		return this;
	}
	
	public MJSimplePos clone(int range){
		int cx = x;
		int cy = y;
		if(range != 0){
			cx += _rnd.nextBoolean() ? -_rnd.nextInt(range) : _rnd.nextInt(range);
			cy += _rnd.nextBoolean() ? -_rnd.nextInt(range) : _rnd.nextInt(range);
		}
		return new MJSimplePos().setX(cx).setY(cy).setMapId(mapId);
	}
}
