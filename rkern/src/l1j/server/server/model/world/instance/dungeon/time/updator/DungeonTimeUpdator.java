package l1j.server.server.model.world.instance.dungeon.time.updator;

import java.util.Calendar;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.Config;
import l1j.server.server.model.world.instance.CInstanceWork;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.gametime.L2DateTime;
import l1j.server.server.model.gametime.CL2Time;
import l1j.server.server.model.gametime.L2TimeListener;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.world.instance.dungeon.time.DungeonTimeInformation;
import l1j.server.server.model.world.instance.dungeon.time.loader.DungeonTimeInformationLoader;
import l1j.server.server.model.world.instance.dungeon.time.loader.DungeonTimeLoadManager;

public class DungeonTimeUpdator implements L2TimeListener{
	private static final Logger LOG = Logger.getLogger(DungeonTimeUpdator.class.getName());
	private static DungeonTimeUpdator _instance;
	public static DungeonTimeUpdator getInstance(){
		if(_instance == null)
			_instance = new DungeonTimeUpdator();
		return _instance;
	}

	public static void release(){
		if(_instance != null){
			_instance.dispose();
			_instance = null;
		}
	}

	public static void reload(){
		DungeonTimeUpdator old = _instance;
		final boolean running = old != null && old.is_running();
		if (old != null) old.dispose();
		_instance = new DungeonTimeUpdator();
		if (running) _instance.run();
	}
	
	public static boolean is_possible_update_character(L1PcInstance pc){
		return pc != null && pc.getNetConnection() != null;
	}

	private DungeonTimeConsumer _consumer;
	private Calendar _pendingScheduledReset;
	private DungeonTimeUpdator() { }

	public synchronized void run() {
		if (_consumer != null) return;
		_consumer = new DungeonTimeConsumer();
		CL2Time.addRealListener(this, Calendar.SECOND);
	}

	public synchronized void dispose() {
		CL2Time.removeRealListener(this, Calendar.SECOND);
		// In-flight work checks its consumer generation before applying a tick.
		_consumer = null;
	}

	public synchronized boolean is_running(){
		return _consumer != null;
	}
	




	public void do_initialize(Calendar cal) {
		do_initialize(cal, false);
	}

	private boolean do_initialize(Calendar cal, boolean scheduled) {
		dispose();
		try {
		CInstanceWork.execute(new Runnable(){
			@Override
			public void run(){
				String message = "전체 던전 시간이 초기화되었습니다.";
				String message_en = "[NOTICE] All dungeon timers have been reset."; 
				try{
					CWorld.getInstance()
					.getAllPlayerStream()
					.filter((L1PcInstance pc)->{
						return is_possible_update_character(pc);
					})
					.forEach((L1PcInstance pc)->{
						pc.initialize_dungeon_progress();
						SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, message);
						SC_SystemMessage.send(pc, message_en);
					});
				}catch(Exception e){
					e.printStackTrace();
				} finally {
					try {
						if (scheduled) teleportAfterReset();
					} finally {
						try { DungeonTimeLoadManager.getInstance().do_truncate(cal); }
						finally { DungeonTimeUpdator.this.run(); }
					}
				}
			}
		});
		return true;
		} catch (RejectedExecutionException rejected) {
			// Reset never started. Restore the old clock subscription.
			run();
			LOG.log(Level.WARNING, "Dungeon reset admission rejected", rejected);
			return false;
		}
	}
	
	@Override
	public void onSecondChanged(L2DateTime time) {
		/** 던전초기화 시간 **/
		int nowDay = time.get(Calendar.DAY_OF_WEEK);
		// [던전초기화 시각 외부화] config/altsettings.properties 의 DungeonResetHour/DungeonResetMinute
		final Calendar pendingReset;
		synchronized (this) { pendingReset = _pendingScheduledReset; }
		if (pendingReset != null) {
			// A refused daily reset must not be skipped for an entire day.
			if (do_initialize(pendingReset, true)) {
				synchronized (this) {
					if (_pendingScheduledReset == pendingReset) _pendingScheduledReset = null;
				}
				Config.네쥬던전초기화 = pendingReset.get(Calendar.DAY_OF_WEEK);
			}
		} else if(time.matches(Config.DungeonResetHour, Config.DungeonResetMinute, 0)) {
			final Calendar resetTime = time.getCalendar();
			if (do_initialize(resetTime, true)) {
				Config.네쥬던전초기화 = nowDay;
			} else {
				synchronized (this) { _pendingScheduledReset = resetTime; }
			}
		} else {
			queueTick();
		}
	}

	private void teleportAfterReset() {
		final String message = "던전 초기화 시간으로, 더 이용하시기 위해서는 재입장 해주십시오.";
		try(var shared=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                SC_PacketBox.nativeGreenWriter(message),
                Integer.MIN_VALUE,Integer.MAX_VALUE)){
            for (int i = 0; i < 2; ++i) {
			for (L1PcInstance pc : CWorld.getInstance().getAllPlayers()) {
				if (pc == null) continue;
				if(i==0)l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(shared,pc);
				if (pc.getMapId() == 53 || pc.getMapId() == 54) {
					if (i == 0) {
						pc.start_teleport(33430, 32823, 4, pc.getHeading(), 169, true, false);
						SC_SkillSound.send(pc, pc.getId(), 491);
					} else {
						pc.do_simple_teleport(33436, 32812, 4);
						SC_SkillSound.send(pc, pc.getId(), 491);
					}
				}
			}
		}
	    }
}
	
	// A maximum of two pending seconds matches the old two-slot signal
	// backlog. No dedicated instant worker waits for a clock notification.
	private void queueTick() {
		final DungeonTimeConsumer consumer;
		synchronized (this) {
			consumer = _consumer;
			if (consumer == null) return;
			if (consumer.pendingTicks < 2) consumer.pendingTicks++;
			if (consumer.queued) return;
			consumer.queued = true;
		}
		submitTick(consumer);
	}

	private void submitTick(DungeonTimeConsumer consumer) {
		try {
			CInstanceWork.execute(consumer);
		} catch (RejectedExecutionException refused) {
			synchronized (this) { consumer.queued = false; }
			// Preserve the pending ticks. The next clock event retries admission.
			LOG.log(Level.WARNING, "Dungeon time tick admission rejected", refused);
		}
	}

	private final class DungeonTimeConsumer implements Runnable {
		private int pendingTicks;
		private boolean queued;

		@Override
		public void run() {
			final int ticks;
			synchronized (DungeonTimeUpdator.this) {
				if (_consumer != this) { queued = false; return; }
				ticks = pendingTicks;
				pendingTicks = 0;
			}
			try {
				for (int i = 0; i < ticks; i++) {
					synchronized (DungeonTimeUpdator.this) {
						if (_consumer != this) break;
					}
					do_update();
				}
			} finally {
				boolean resubmit = false;
				synchronized (DungeonTimeUpdator.this) {
					queued = false;
					if (_consumer == this && pendingTicks > 0) {
						queued = true;
						resubmit = true;
					}
				}
				if (resubmit) submitTick(this);
			}
		}

		private void do_update() {
			try {
				DungeonTimeInformationLoader loader = DungeonTimeInformationLoader.getInstance();
				for (L1PcInstance pc : CWorld.getInstance().getAllPlayers()) {
					if (!is_possible_update_character(pc)) continue;
					DungeonTimeInformation dtInfo = loader.from_map_id(pc.getMapId());
					if (dtInfo != null) pc.dec_dungeon_progress(dtInfo);
				}
			} catch (Exception error) {
				LOG.log(Level.WARNING, "Dungeon time update failed", error);
			}
		}
	}
}
