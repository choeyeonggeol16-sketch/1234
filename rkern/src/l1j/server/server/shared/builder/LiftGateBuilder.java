package l1j.server.server.shared.builder;

import l1j.server.server.IdFactory;
import l1j.server.server.model.L1World;
import l1j.server.server.model.world.instance.shared.L1LiftGateInstance;

public class LiftGateBuilder{
	private int 	_gfx;
	
	public LiftGateBuilder setGfx(int gfx){
		_gfx = gfx;
		return this;
	}
	
	public L1LiftGateInstance build(int x, int y, short mapid) {
		return build(x, y, mapid, true, 3);
	}
	
	public L1LiftGateInstance build(int x, int y, short mapid, boolean isWidth, int weight) {
		L1LiftGateInstance lift = new L1LiftGateInstance(IdFactory.getInstance().nextId(), _gfx, x, y, 2, mapid, isWidth, weight);
		L1World.getInstance().storeAndEnterWorld(lift);
		lift.up();
		return lift;
	}
	
	public L1LiftGateInstance build(int gfxId, int x, int y, short mapid) {
		return build(gfxId, x, y, mapid, true, 3);
	}
	
	public L1LiftGateInstance build(int gfxId, int x, int y, short mapid, boolean isWidth, int weight) {
		L1LiftGateInstance lift = new L1LiftGateInstance(IdFactory.getInstance().nextId(), gfxId, x, y, 2, mapid, isWidth, weight);
		L1World.getInstance().storeAndEnterWorld(lift);
		lift.up();
		return lift;
	}
}
