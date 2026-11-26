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
package l1j.server.server.model.inventory;

import java.util.concurrent.RejectedExecutionException;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.thread.CIOThreadCallback;

public final class CEquipmentTimer implements CIOThreadCallback.WheelTask {
    private static final long TICK_MILLIS = 1_000L;

    private final L1PcInstance pc;
    private final L1ItemInstance item;
    private final int sessionStamp;
    private volatile boolean active = true;
    private boolean wheelPending;

    public CEquipmentTimer(L1PcInstance pc, L1ItemInstance item) {
        this.pc = pc;
        this.item = item;
        this.sessionStamp = pc.getSessionStamp();
    }

    public boolean isActive() {
        return active;
    }

    @Override
    public boolean isCancelled() {
        return !active;
    }

    /** One deadline on the existing owner wheel; no ScheduledFuture or handoff. */
    public void scheduleNext() {
        try {
            synchronized (this) {
                if (!active || wheelPending) return;
                if (!CIOThreadCallback.getInstance().scheduleEquipmentTick(
                        pc, sessionStamp, this, TICK_MILLIS)) {
                    throw new RejectedExecutionException("Timed equipment tick admission failed");
                }
                wheelPending = true;
            }
        } catch (RuntimeException rejected) {
            cancel();
            throw rejected;
        }
    }

    @Override
    public void run() {
        synchronized (this) {
            if (!active || !wheelPending) return;
            wheelPending = false;
        }
        // Never hold the timer monitor while acquiring the session monitor.
        TimerExpired();
    }

    private void TimerExpired() {
        final CUserSocket session = pc.getNetConnection();
        if (session == null) {
            cancel();
            return;
        }
        synchronized (session) {
            final CUserInventory inventory = pc.getInventory();
            if (!active || !session.isConnected() || pc.isLogoutStarted() || pc.getSessionStamp() != sessionStamp) {
                cancel();
                return;
            }
            if (!inventory.tryBeginEquipmentChange()) {
                scheduleNext();
                return;
            }
            try {
                if (!active || !item.ownsEquipmentTimer(this) || !item.isEquipped()
                        || inventory.GetByServerID(item.getId()) != item) {
                    cancel();
                    return;
                }
                final int remaining = item.getRemainingTime();
                if (remaining > 1) {
                    item.setRemainingTime(remaining - 1);
                    inventory.updateItem(item, CUserInventory.COL_REMAINING_TIME);
                    try {
                        scheduleNext();
                    } catch (RejectedExecutionException unavailable) {
                        // Stop timed bonuses if the owner wheel is unavailable.
                        // Preserve the remaining seconds for a later re-equip.
                        pc.UnequipItem(item);
                    }
                } else {
                    inventory.DeleteItemAmount(item, 1);
                    cancel();
                }
            } finally {
                inventory.endEquipmentChange();
            }
        }
    }

    public void cancel() {
        synchronized (this) {
            active = false;
            wheelPending = false;
        }
        item.clearEquipmentTimer(this);
    }
}
