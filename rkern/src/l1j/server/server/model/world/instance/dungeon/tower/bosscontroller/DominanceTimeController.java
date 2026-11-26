package l1j.server.server.model.world.instance.dungeon.tower.bosscontroller;

import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.model.world.instance.dungeon.tower.DominanceBoss;
import l1j.server.server.model.world.instance.dungeon.tower.DominanceDataLoader;
import l1j.server.server.model.world.instance.CInstanceWork;
import l1j.server.server.model.network.thread.CServerTimer;

public class DominanceTimeController implements Runnable {
	private static volatile DominanceTimeController instance;
	private static final Logger LOG = Logger.getLogger(DominanceTimeController.class.getName());
	private final AtomicBoolean tickPending = new AtomicBoolean();
    private final java.util.concurrent.atomic.AtomicLong pendingSpawnMinute =
            new java.util.concurrent.atomic.AtomicLong(-1L);
    private volatile long lastProcessedMinute = -1L;
    private long observedMinute = System.currentTimeMillis() / 60_000L;
    private volatile CServerTimer timer;
	private static final ConcurrentHashMap<Integer, Integer> _bossnum = new ConcurrentHashMap<Integer, Integer>(256);

	public static DominanceTimeController getInstance() {
        DominanceTimeController current = instance;
        if (current != null) return current;
        synchronized (DominanceTimeController.class) {
            if (instance == null) {
                DominanceTimeController created = new DominanceTimeController();
                // The scheduler only signals work; boss/game mutations stay on
                // the existing bounded instant pool, never on a timer thread.
                CServerTimer future = CServerTimer.fixedDelaySignal(
                        created::signalTick, 0L, 1000L);
                if (future == null) {
                    throw new IllegalStateException("Dominance spawn timer admission failed");
                }
                created.timer = future;
                instance = created;
            }
            return instance;
        }
    }

    public static void stop() {
        final DominanceTimeController current = instance;
        if (current != null) current.timer.cancel();
    }

    private void signalTick() {
        final long currentSeconds = System.currentTimeMillis() / 1000L;
        final long minute = currentSeconds / 60L;
        if (minute > observedMinute || currentSeconds % 60L == 0L) {
            if (minute > lastProcessedMinute) pendingSpawnMinute.set(minute);
        }
        observedMinute = minute;
        // Keep a missed spawn-minute while an instant worker is saturated.
        // When it recovers, the boss logic receives the original due minute.
        if (pendingSpawnMinute.get() <= lastProcessedMinute
                || !tickPending.compareAndSet(false, true)) return;
        try {
            CInstanceWork.execute(this);
        } catch (RejectedExecutionException refused) {
            tickPending.set(false); // Next scheduled tick retries admission.
            LOG.log(Level.WARNING, "Dominance spawn tick admission refused", refused);
        }
    }

    private void isSpawnTime(long scheduledMinute) {
        try {
            List<DominanceBoss> list = DominanceDataLoader.getList();
            if (list.size() > 0) {
                Calendar oCalendar = Calendar.getInstance();
                oCalendar.setTimeInMillis(scheduledMinute * 60_000L);
				int hour = oCalendar.get(Calendar.HOUR_OF_DAY);
				int minute = oCalendar.get(Calendar.MINUTE);
				for (DominanceBoss b : list) {
					if (b.isSpawnTime(hour, minute, oCalendar)) {
						//Integer id = _bossnum.get(b.getBossNum());
						//if (id != null) {
							if (b.getBossNum() == 1) {
								DominanceFloorLv1 zenis = new DominanceFloorLv1(b.getNpcId(), b.getMapX(), b.getMapY(),
										b.getMapId(), b.isMentuse(), b.getMent(), b.isAllEffect(), b.getEffectNum());
								zenis.Start();
							} else if (b.getBossNum() == 2) {
								DominanceFloorLv2 sier = new DominanceFloorLv2(b.getNpcId(), b.getMapX(), b.getMapY(),
										b.getMapId(), b.isMentuse(), b.getMent(), b.isAllEffect(), b.getEffectNum());
								sier.Start();
							} else if (b.getBossNum() == 3) {
								DominanceFloorLv3 vampire = new DominanceFloorLv3(b.getNpcId(), b.getMapX(),
										b.getMapY(), b.getMapId(), b.isMentuse(), b.getMent(), b.isAllEffect(),
										b.getEffectNum());
								vampire.Start();
							} else if (b.getBossNum() == 4) {
								DominanceFloorLv4 zombie = new DominanceFloorLv4(b.getNpcId(), b.getMapX(), b.getMapY(),
										b.getMapId(), b.isMentuse(), b.getMent(), b.isAllEffect(), b.getEffectNum());
								zombie.Start();
							} else if (b.getBossNum() == 5) {
								DominanceFloorLv5 kuger = new DominanceFloorLv5(b.getNpcId(), b.getMapX(), b.getMapY(),
										b.getMapId(), b.isMentuse(), b.getMent(), b.isAllEffect(), b.getEffectNum());
								kuger.Start();
							} else if (b.getBossNum() == 6) {
								DominanceFloorLv6 mummy = new DominanceFloorLv6(b.getNpcId(), b.getMapX(), b.getMapY(),
										b.getMapId(), b.isMentuse(), b.getMent(), b.isAllEffect(), b.getEffectNum());
								mummy.Start();
							} else if (b.getBossNum() == 7) {
								DominanceFloorLv7 iris = new DominanceFloorLv7(b.getNpcId(), b.getMapX(), b.getMapY(),
										b.getMapId(), b.isMentuse(), b.getMent(), b.isAllEffect(), b.getEffectNum());
								iris.Start();
							} else if (b.getBossNum() == 8) {
								DominanceFloorLv8 bald = new DominanceFloorLv8(b.getNpcId(), b.getMapX(), b.getMapY(),
										b.getMapId(), b.isMentuse(), b.getMent(), b.isAllEffect(), b.getEffectNum());
								bald.Start();
							} else if (b.getBossNum() == 9) {
								DominanceFloorLv9 rich = new DominanceFloorLv9(b.getNpcId(), b.getMapX(), b.getMapY(),
										b.getMapId(), b.isMentuse(), b.getMent(), b.isAllEffect(), b.getEffectNum());
								rich.Start();
							} else if (b.getBossNum() == 10) {
								DominanceFloorLv10 ugnus = new DominanceFloorLv10(b.getNpcId(), b.getMapX(),
										b.getMapY(), b.getMapId(), b.isMentuse(), b.getMent(), b.isAllEffect(),
										b.getEffectNum());
								ugnus.Start();
							} else if (b.getBossNum() == 11) {
								DominanceFloorLv11 riper = new DominanceFloorLv11(b.getNpcId(), b.getMapX(),
										b.getMapY(), b.getMapId(), b.isMentuse(), b.getMent(), b.isAllEffect(),
										b.getEffectNum());
								riper.Start();
							}
						//}
						//_bossnum.put(b.getBossNum(), b.getNpcId());
					}

				}
			}
		} catch (Exception e) {
		//	e.printStackTrace();
		}
	}

	@Override
    public void run() {
        final long dueMinute = pendingSpawnMinute.get();
        try {
            if (dueMinute > lastProcessedMinute) {
                lastProcessedMinute = dueMinute;
                isSpawnTime(dueMinute);
            }
        } catch (Throwable failure) {
            LOG.log(Level.SEVERE, "Dominance spawn tick failed", failure);
        } finally {
            pendingSpawnMinute.compareAndSet(dueMinute, -1L);
            tickPending.set(false);
        }
    }
}
