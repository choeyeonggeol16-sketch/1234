/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 *
 * http://www.gnu.org/copyleft/gpl.html
 */
package l1j.server.server.model;

import l1j.server.server.ThreadPool;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.thread.CIOThreadCallback;

public class L1EquipmentTimer implements Runnable {
	private final long _scheduleTime;
	private volatile boolean _active;
	private final int _sessionStamp;
	
	public L1EquipmentTimer(L1PcInstance pc, L1ItemInstance item, long scheduleTime ) {
		_pc = pc;
		_item = item;
		_scheduleTime = scheduleTime;
		_sessionStamp = pc.getSessionStamp();
		_active = true;
	}

	@Override
	public void run() {
		if (!_active) return;
        // Keep the existing scheduler, but never mutate a cached item on its
        // foreign ThreadPool thread or on a later character session.
        CIOThreadCallback.getInstance().completeWarehouseMove(_pc, _sessionStamp,
                this::tickOnOwner, failure -> { if (failure != null) cancel(); });
	}

    private void tickOnOwner() {
        final CIOClient session = _pc.getNetConnection();
        if (session == null) { cancel(); return; }
        synchronized (session) {
            if (!_active || !session.isConnected() || _pc.getSessionStamp() != _sessionStamp
                    || _pc.getInventory().getItem(_item.getId()) != _item) {
                cancel();
                return;
            }
            if (_pc.isItemTransferBusy()) {
                // Pause this same timer, preserving the remaining time and
                // registration instead of throwing after changing the item.
                ThreadPool.getInstance().scheduleRequired(this, _scheduleTime);
                return;
            }
            if (_item.getRemainingTime() > 1) {
                _item.setRemainingTime(_item.getRemainingTime() - 1);
                _pc.getInventory().updateItem(_item, L1PcInventory.COL_REMAINING_TIME);
                ThreadPool.getInstance().scheduleRequired(this, _scheduleTime);
            } else {
                _pc.getInventory().removeItem(_item, 1);
                cancel();
            }
        }
    }
	
	public void cancel()
	{
		_active = false;
	}

	private final L1PcInstance _pc;
	private final L1ItemInstance _item;
}
