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
package l1j.server.server.Controller;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;

import l1j.server.Config;
import l1j.server.server.ThreadPool;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.gametime.L1GameTimeClock;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;


public class ShipTimeController implements Runnable {
	private static ShipTimeController _instance;

	private static final Logger LOG = Logger.getLogger(ShipTimeController.class.getName());
    private final AtomicBoolean pending = new AtomicBoolean();
    private volatile ScheduledFuture<?> ticker;
    private volatile int generation;
	public static ShipTimeController getInstance() {
		if (_instance == null) {
			_instance = new ShipTimeController();
		}
		return _instance;
	}

    public synchronized void start() {
        if (ticker != null) return;
        final int epoch = ++generation;
        final ScheduledFuture<?> future = ThreadPool.getInstance().scheduleWithFixedDelay(() -> {
            if (generation != epoch || !pending.compareAndSet(false, true)) return;
            try {
                ThreadPool.getInstance().execute(() -> {
                    try {
                        if (generation == epoch) run();
                    } finally {
                        // stop/restart must not admit a new tick while an old
                        // tick is still executing. Only the executing tick
                        // releases this shared gate on completion.
                        pending.set(false);
                    }
                });
            } catch (RejectedExecutionException rejected) {
                pending.set(false);
                LOG.log(Level.WARNING, "Ship tick admission rejected", rejected);
            }
        }, 0L, 5000L);
        if (future == null) {
            ++generation;
            throw new RejectedExecutionException("Ship recurring tick admission rejected");
        }
        ticker = future;
    }

    public synchronized void stop() {
        ++generation;
        final ScheduledFuture<?> future = ticker;
        ticker = null;
        // Do not clear pending here: an old in-flight tick may still be
        // changing world state. A restarted ticker waits for it to finish.
        if (future != null) future.cancel(false);
    }

    @Override
    public void run() {
        try {
            checkShipTime();
        } catch (Throwable failure) {
            LOG.log(Level.SEVERE, "Ship tick failed", failure);
        }
    }

	private void checkShipTime() {
		int servertime = L1GameTimeClock.getInstance().getGameTime().getSeconds();
        // Legacy ship windows are expressed as negative seconds within a day
        // (-86400..-1), while the game clock can be either positive or negative
        // after its 32-bit wrap. Normalize both directions before comparisons.
        int nowtime = Math.floorMod(servertime, 86400) - 86400;
		
		if (nowtime >= -62370 && nowtime < -62200 //13시 8분 ~ 13시 33분 배
				|| nowtime >= -56982 && nowtime < -56800
				|| nowtime >= -51576 && nowtime < -51400
				|| nowtime >= -46176 && nowtime < -46000
				|| nowtime >= -40776 && nowtime < -40600
				|| nowtime >= -35376 && nowtime < -35200
				|| nowtime >= -29976 && nowtime < -29800
				|| nowtime >= -24576 && nowtime < -24400
				|| nowtime >= -19176 && nowtime < -19000
				|| nowtime >= -13776 && nowtime < -13600
				|| nowtime >= -8376 && nowtime < -8200
				|| nowtime >= -2976 && nowtime < -2800
				|| nowtime >= -83970 && nowtime < -83800
				|| nowtime >= -78570 && nowtime < -78400
				|| nowtime >= -73170 && nowtime < -73000
				|| nowtime >= -67770 && nowtime < -67600
				) {
			if(Config.배알림 == 0){
				
				
			try(var docked=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                SC_PacketBox.nativeGreenWriter("해적섬 행 배가 선착장에 정박했습니다."),
                Integer.MIN_VALUE,Integer.MAX_VALUE);
            var boarding=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                SC_PacketBox.nativeGreenWriter("서둘러 배에 승선해주십시오."),
                Integer.MIN_VALUE,Integer.MAX_VALUE)){
            for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) {
				if (pc.getMapId() == 445) {
					SC_SkillSound.send(pc, pc.getId(), 631);
					l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(docked,pc);
					l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(boarding,pc);
				}
			}
        }
			Config.배알림 = 1;
			}
		}
		
		if ((nowtime >= -60370 && nowtime < -60100) //13시 8분 ~ 13시 33분 배
				|| (nowtime >= -54982 && nowtime < -54100)
				|| (nowtime >= -49576 && nowtime < -49100)
				|| (nowtime >= -44176 && nowtime < -43100)
				|| (nowtime >= -38776 && nowtime < -38100)
				|| (nowtime >= -33376 && nowtime < -32600)
				|| (nowtime >= -27976 && nowtime < -27100)
				|| (nowtime >= -22576 && nowtime < -22100)
				|| (nowtime >= -17176 && nowtime < -16600)
				|| (nowtime >= -11776 && nowtime < -11100)
				|| (nowtime >= -6376 && nowtime < -6100)
				|| (nowtime >= -976 && nowtime < 0)
				|| (nowtime >= -81970 && nowtime < -81600)
				|| (nowtime >= -75670 && nowtime < -75100)
				|| (nowtime >= -70270 && nowtime < -69600)
				|| (nowtime >= -65770 && nowtime < -65100)
				) {
			for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) {
				if (pc.getMapId() == 447) {
					pc.start_teleport(32297, 33086, 440, pc.getHeading(), 169, false, false);
					pc.getInventory().DeleteItemInInventory(40303, 1); // 숨겨진 섬 선착장행 배표
				}
			}
		}

	}
}
