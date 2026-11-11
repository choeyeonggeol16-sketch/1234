package I1j.server.server.model.Thread;

import l1j.server.server.GeneralThreadPool;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;

public class EquipmentTimer implements Runnable {
	private long _scheduleTime;
	private boolean _active;
	
	public EquipmentTimer(L1PcInstance pc, ItemInstance item, long scheduleTime ) {
		_pc = pc;
		_item = item;
		_scheduleTime = scheduleTime;
		_active = true;
	}

	@Override
	public void run() {
		if (!_active) {
			return;
		} 

		if ((_item.getRemainingTime() - 1) > 0) {
			if (_pc.getOnlineStatus() == 0) {
				_item.stopEquipmentTimer(_pc);
			}
			_item.setRemainingTime(_item.getRemainingTime() - 1);
			GeneralThreadPool.getInstance().schedule(this, _scheduleTime);
		} else {
			_pc.getInventory().removeItem(_item, 1);
		}
	}

	
	public void cancel() {
		_active = false;
	}
	private final L1PcInstance _pc;
	private final ItemInstance _item;
}
