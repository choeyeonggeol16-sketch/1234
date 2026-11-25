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

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

import l1j.server.server.ThreadPool;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.thread.CIOThreadCallback;

public final class L1EquipmentTimer implements Runnable {
    private static final long TICK_MILLIS = 1_000L;

    private final L1PcInstance pc;
    private final L1ItemInstance item;
    private final int sessionStamp;
    private final Runnable ownerTick = this::tickOnOwner;
    private final Consumer<Throwable> ownerCompletion = this::onOwnerCompletion;

    private volatile boolean active = true;
    private ScheduledFuture<?> pendingTick;
    private CIOThreadCallback.WheelTask wheelFallback;
    private boolean wheelPending;

    public L1EquipmentTimer(L1PcInstance pc, L1ItemInstance item) {
        this.pc = pc;
        this.item = item;
        this.sessionStamp = pc.getSessionStamp();
    }

    public boolean isActive() {
        return active;
    }

    /** Prefer the bounded scheduler. During saturation, use the existing
     * character-owner CIO wheel so a worn timed item cannot lose its tick.
     */
    public void scheduleNext() {
        try {
            synchronized (this) {
                if (!active || pendingTick != null || wheelPending) return;
                try {
                    pendingTick = ThreadPool.getInstance().scheduleRequired(this, TICK_MILLIS);
                    return;
                } catch (RuntimeException refused) {
                    if (wheelFallback == null) {
                        wheelFallback = new CIOThreadCallback.WheelTask() {
                            @Override public void run() {
                                synchronized (L1EquipmentTimer.this) {
                                    wheelPending = false;
                                    if (!active) return;
                                }
                                tickOnOwner();
                            }
                            @Override public boolean isCancelled() { return !active; }
                        };
                    }
                    try {
                        if (CIOThreadCallback.getInstance().scheduleEquipmentTick(
                                pc, sessionStamp, wheelFallback, TICK_MILLIS)) {
                            wheelPending = true;
                            return;
                        }
                    } catch (RuntimeException fallbackFailure) {
                        refused.addSuppressed(fallbackFailure);
                    }
                    throw new RejectedExecutionException("Timed equipment tick admission failed", refused);
                }
            }
        } catch (RuntimeException rejected) {
            cancel();
            throw rejected;
        }
    }

    @Override
    public void run() {
        synchronized (this) {
            pendingTick = null;
            if (!active) return;
        }
        CIOThreadCallback.getInstance().completeWarehouseMove(pc, sessionStamp, ownerTick, ownerCompletion);
    }

    private void onOwnerCompletion(Throwable failure) {
        if (failure != null) cancel();
    }

    private void tickOnOwner() {
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
                        || inventory.getItem(item.getId()) != item) {
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
                        // Stop timed bonuses if both schedulers are unavailable.
                        // Preserve the remaining seconds for a later re-equip.
                        pc.UnequipItem(item);
                    }
                } else {
                    inventory.removeItem(item, 1);
                    cancel();
                }
            } finally {
                inventory.endEquipmentChange();
            }
        }
    }

    public void cancel() {
        final ScheduledFuture<?> pending;
        synchronized (this) {
            active = false;
            pending = pendingTick;
            pendingTick = null;
        }
        if (pending != null) pending.cancel(false);
        item.clearEquipmentTimer(this);
    }
}
