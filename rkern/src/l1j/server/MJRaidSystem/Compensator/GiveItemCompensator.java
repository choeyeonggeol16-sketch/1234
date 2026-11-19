package l1j.server.MJRaidSystem.Compensator;

import l1j.server.server.model.Instance.L1PcInstance;

public class GiveItemCompensator implements CompensatorElement{
	private int _itemid;
	private int _count;
	
	public GiveItemCompensator(int itemid, int count){
		_itemid = itemid;
		_count	= count;
	}
	
	@Override
	public void compensate(Object obj) {
		if(_itemid < 0 || _count < 0)
			return;
		
		L1PcInstance pc = (L1PcInstance)obj;
		pc.getInventory().storeItem(_itemid, _count, 0);
	}
}
