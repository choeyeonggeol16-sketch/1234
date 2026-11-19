package l1j.server.MJTemplate.Builder;

import l1j.server.MJTemplate.L1Instance.MJL1LiftGateInstance;
import l1j.server.server.IdFactory;
import l1j.server.server.model.L1World;

public class MJLiftGateBuilder{
	private int 	_gfx;
	
	public MJLiftGateBuilder setGfx(int gfx){
		_gfx = gfx;
		return this;
	}
	
	public MJL1LiftGateInstance build(int x, int y, short mapid) {
		return build(x, y, mapid, true, 3);
	}
	
	public MJL1LiftGateInstance build(int x, int y, short mapid, boolean isWidth, int weight) {
		MJL1LiftGateInstance lift = new MJL1LiftGateInstance(IdFactory.getInstance().nextId(), _gfx, x, y, 2, mapid, isWidth, weight);
		L1World.getInstance().storeAndEnterWorld(lift);
		lift.up();
		return lift;
	}
	
	public MJL1LiftGateInstance build(int gfxId, int x, int y, short mapid) {
		return build(gfxId, x, y, mapid, true, 3);
	}
	
	public MJL1LiftGateInstance build(int gfxId, int x, int y, short mapid, boolean isWidth, int weight) {
		MJL1LiftGateInstance lift = new MJL1LiftGateInstance(IdFactory.getInstance().nextId(), gfxId, x, y, 2, mapid, isWidth, weight);
		L1World.getInstance().storeAndEnterWorld(lift);
		lift.up();
		return lift;
	}
}
