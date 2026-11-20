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

import java.util.Random;
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
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.gametime.L1GameTimeClock;
import l1j.server.server.model.network.packet.CServerPacket;
import l1j.server.server.model.network.core.sc.packet.SC_DisplayEffect;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;

import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.utils.L1SpawnUtil;

public class ShipTimeController implements Runnable {
	private static ShipTimeController _instance;

	private static final Logger LOG = Logger.getLogger(ShipTimeController.class.getName());
    private final AtomicBoolean pending = new AtomicBoolean();
    private volatile ScheduledFuture<?> ticker;
    private volatile int generation;
    private Random _random = new Random(System.nanoTime());
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
			if(Config.포세이돈 == 0){
				for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) {
					if (pc.getMapId() == 447) {
						//					L1Teleport.teleport(pc, 32631, 32983, (short) 0, 0, false);
						pc.start_teleport(32297, 33086, 440, pc.getHeading(), 169, false, false);
						pc.getInventory().consumeItem(40303, 1); // 숨겨진 섬 선착장행 배표
					}
				}
				}else{
					if(Config.시그널1 == 0) {
						final int eff1 = 2;
						String box = "갑자기 파도가 거세게 휘몰아칩니다.";
						try(var shared=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                SC_PacketBox.nativeGreenWriter(box),Integer.MIN_VALUE,Integer.MAX_VALUE)){
            try(var effectShared=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
    SC_DisplayEffect.nativeWriter(eff1),Integer.MIN_VALUE,Integer.MAX_VALUE)) {
for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) {
							if (pc.getMapId() == 447){
									l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(shared,pc);
									l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(effectShared,pc);
									SC_SkillSound.send(pc, pc.getId(), 20394);
									pc.getInventory().consumeItem(40303, 1); // 숨겨진 섬 선착장행 배표
								}
							}
}
        }
						Config.시그널1 = 1;
					}else{
						if(Config.시그널2 == 0){
							final int eff1 = 2;
							try(var effectShared=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
    SC_DisplayEffect.nativeWriter(eff1),Integer.MIN_VALUE,Integer.MAX_VALUE)) {
for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) {
								if (pc.getMapId() == 447){
									l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(effectShared,pc);
									SC_SkillSound.send(pc, pc.getId(), 20394);
								}
							}
}
							Config.시그널2 = 1;
						}else{
							if(Config.시그널3 == 0){
								String box = "선박이 심하게 흔들리고 있습니다.";
								final int eff1 = 2;
								try(var shared=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                SC_PacketBox.nativeGreenWriter(box),Integer.MIN_VALUE,Integer.MAX_VALUE)){
            try(var effectShared=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
    SC_DisplayEffect.nativeWriter(eff1),Integer.MIN_VALUE,Integer.MAX_VALUE)) {
for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) {
									if (pc.getMapId() == 447){
									l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(shared,pc);
									l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(effectShared,pc);
									SC_SkillSound.send(pc, pc.getId(), 20394);
									}
								}
}
        }
							Config.시그널3 = 1;
							}else{
								if(Config.시그널3 == 1){
									final int eff1 = 2;
									try(var effectShared=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
    SC_DisplayEffect.nativeWriter(eff1),Integer.MIN_VALUE,Integer.MAX_VALUE)) {
for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) {
										if (pc.getMapId() == 447){
										l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(effectShared,pc);
										SC_SkillSound.send(pc, pc.getId(), 20394);
										}
									}
}
									Config.시그널3 = 2;
								}else{
									if(Config.시그널3 == 2){
										final int eff1 = 23;
										try(var effectShared=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
    SC_DisplayEffect.nativeWriter(eff1),Integer.MIN_VALUE,Integer.MAX_VALUE)) {
for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) {
											if (pc.getMapId() == 447){
											SC_SkillSound.send(pc, pc.getId(), 20394);
											l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(effectShared,pc);
											pc.start_teleport(32749, 32690, 63, pc.getHeading(), 169, false, false);
											}
										}
}
										SC_SystemMessage.broadcast("동쪽 해역에서 항해중이던 선박이 침몰하였습니다.");
										SC_PacketBox.broadcastGreen("동쪽 해역에서 항해중이던 선박이 침몰하였습니다.");
										Config.시그널3 = 3;
										Config.포세이돈 = 0;
									}
								}
							}
						}
					}
				}
			}
		if(Config.시그널3 == 3){
            // The bounded scheduler can refuse this delayed spawn. Keep the
            // pending state and retry on the next recurring ship tick.
            final ScheduledFuture<?> spawn = ThreadPool.getInstance().schedule(new Runnable() {
				@Override
				public void run() {
					L1SpawnUtil.spawn2(32746, 32697, (short) 63, 460000365, 0, 3600 * 1000, 0);
					//L1SpawnUtil.spawnfieldboss(32744, 32698, (short) 63, 460000365, 0, 0, 0);
				}
			}, 5000L);
            if (spawn != null) Config.시그널3 = 4;
		}
	
	}
}
