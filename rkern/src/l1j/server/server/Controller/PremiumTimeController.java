package l1j.server.server.Controller;

import l1j.server.server.datatables.CObjectDB;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import l1j.server.Config;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

public final class PremiumTimeController implements Runnable {

	private static final Logger LOG = Logger.getLogger(PremiumTimeController.class.getName());
    private static final AtomicLong LAST_OVERLOAD_LOG_MS = new AtomicLong();
	private static volatile PremiumTimeController _instance;

	public static PremiumTimeController getInstance() {
		PremiumTimeController v = _instance;
		if (v == null) {
			synchronized (PremiumTimeController.class) {
				v = _instance;
				if (v == null) {
					_instance = (v = new PremiumTimeController());
				}
			}
		}
		return v;
	}

	// ======= 설정 및 튜닝 =======
	private final long rewardIntervalMs = (long) Config.FEATHERTIME * 60000L; // 지급 주기 (기본 10분)
	private static final int WORKERS = 2;
	private static final int QUEUE_CAP = 5000;
	// ===========================

	private final DelayQueue<RewardTask> q = new DelayQueue<>();
	private final Int2ObjectMap<Holder> holders = 
			Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<Holder>(8192));

	private final AtomicBoolean running = new AtomicBoolean(false);
	private Thread dispatcher;
	private volatile ThreadPoolExecutor workerPool;

	private PremiumTimeController() {}

	@Override
	public void run() {
		start();
	}

	public void start() {
		if (!running.compareAndSet(false, true)) return;

		workerPool = new ThreadPoolExecutor(
				WORKERS,
				WORKERS,
				30L,
				TimeUnit.SECONDS,
				new ArrayBlockingQueue<>(QUEUE_CAP),
				r -> {
					Thread t = new Thread(r, "PremiumRewardWorker");
					t.setDaemon(true);
					t.setUncaughtExceptionHandler((th, ex) ->
							LOG.log(Level.SEVERE, "[PremiumTimeController] worker error", ex));
					return t;
				},
				new ThreadPoolExecutor.AbortPolicy()
		);
		workerPool.prestartAllCoreThreads();

		dispatcher = new Thread(this::dispatchLoop, "PremiumRewardDispatcher");
		dispatcher.setDaemon(true);
		dispatcher.setUncaughtExceptionHandler((th, ex) ->
				LOG.log(Level.SEVERE, "[PremiumTimeController] dispatcher error", ex));
		dispatcher.start();

	//	System.out.println("[PremiumTimeController] 유저 개별 딜레이큐 기반 깃털 지급 시스템 가동.");
	}

	public void stop() {
		if (!running.compareAndSet(true, false)) return;

		if (dispatcher != null) {
			dispatcher.interrupt();
			dispatcher = null;
		}

		synchronized (holders) {
			for (Holder h : holders.values()) {
				h.cancelled.set(true);
				h.pc = null;
				if (h.lastTask != null) h.lastTask.cancelled = true;
			}
			holders.clear();
		}

		if (workerPool != null) {
			workerPool.shutdownNow();
			workerPool = null;
		}
		q.clear();
	}

	/**
	 * 유저 접속 시 등록 (로그인 시 호출)
	 */
	public void register(L1PcInstance pc) {
		if (pc == null) return;
		final int id = pc.getId();
		final long now = System.currentTimeMillis();

        synchronized (holders) {
            if (!running.get()) return;
            Holder h = holders.get(id);
            if (h == null) {
                h = new Holder(id);
                holders.put(id, h);
            }
            h.pc = pc;
            h.cancelled.set(false);
            h.bumpVer();
            final RewardTask stale = h.lastTask;
            if (stale != null) {
                stale.cancelled = true;
                q.remove(stale);
            }
            h.lastTask = null;
            if (rewardIntervalMs > 0L)
                scheduleIfEarlier(h, now + rewardIntervalMs, h.ver.get());
        }
	}

	/**
	 * 유저 등록 해제 (로그아웃 시 호출)
	 */
	public void unregister(L1PcInstance pc) {
		if (pc == null) return;
        synchronized (holders) {
            final Holder h = holders.get(pc.getId());
            // A stale logout cannot unregister its replacement connection.
            if (h == null || h.pc != pc) return;
            holders.remove(pc.getId());
            h.cancelled.set(true);
            h.bumpVer();
            h.pc = null;
            final RewardTask stale = h.lastTask;
            h.lastTask = null;
            if (stale != null) {
                stale.cancelled = true;
                q.remove(stale);
            }
        }
    }

	private void dispatchLoop() {
		while (running.get() && !Thread.currentThread().isInterrupted()) {
			try {
				final RewardTask task = q.take();
				if (task.cancelled) continue;

				final Holder h = holders.get(task.objectId);
				if (h == null || h.cancelled.get() || !h.isCurrent(task)) continue;

                try {
                    // Saturation must not execute an inventory reward on
                    // the dispatcher thread (or silently lose its next tick).
                    ThreadPoolExecutor pool = workerPool;
                    if (pool == null || !running.get()) break;
                    pool.execute(() -> runTask(h, task));
                } catch (RejectedExecutionException refused) {
                    if (running.get() && !h.cancelled.get() && h.isCurrent(task)
                            && h.nextDueMs.compareAndSet(task.dueTimeMs, 0L)) {
                        scheduleIfEarlier(h, System.currentTimeMillis() + 1000L, task.ver);
                    }
                    final long now = System.currentTimeMillis();
                    final long previous = LAST_OVERLOAD_LOG_MS.get();
                    if (now - previous >= 30_000L
                            && LAST_OVERLOAD_LOG_MS.compareAndSet(previous, now)) {
                        LOG.log(Level.WARNING, "Premium reward delayed: worker pool full", refused);
                    }
                }
			} catch (InterruptedException ie) {
				Thread.currentThread().interrupt();
				break;
			} catch (Throwable t) {
				LOG.log(Level.SEVERE, "[PremiumTimeController] dispatch loop error", t);
			}
		}
	}

	private void runTask(Holder h, RewardTask task) {
		boolean gotLock = false;
		try {
			h.lock.lockInterruptibly();
			gotLock = true;

			if (h.cancelled.get() || !h.isCurrent(task) || task.cancelled) return;
			final L1PcInstance pc = h.pc;
			if (pc == null) return;

			if (!isPcCk(pc)) {
				giveReward(pc);
			}

		} catch (Exception e) {
			// 예외 방어
		} finally {
			if (gotLock) h.lock.unlock();
			
			// 다음 주기 예약
            if (running.get() && !h.cancelled.get() && h.isCurrent(task)
                    && rewardIntervalMs > 0L) {
				h.bumpVer();
				scheduleIfEarlier(h, System.currentTimeMillis() + rewardIntervalMs, h.ver.get());
			}
		}
	}

	private void scheduleIfEarlier(Holder h, long due, long ver) {
		while (true) {
			long cur = h.nextDueMs.get();
			if (cur != 0L && cur <= due) return;
			if (h.nextDueMs.compareAndSet(cur, due)) break;
		}
		RewardTask t = new RewardTask(h.objectId, ver, due);
		h.lastTask = t;
		q.offer(t);
	}

	private boolean isPcCk(L1PcInstance pc) {
		if (pc == null || pc.isDead() || pc.getNetConnection() == null || 
			pc.getCurrentHp() == 0 || pc.isPrivateShop() || pc.noPlayerCK || pc.noPlayerck2) {
			return true;
		}
		return false;
	}

	private void giveReward(L1PcInstance pc) {
		try {
			int fn = Config.FEATHER_NUM;
			int cln = Config.FEATHER_NUM1;
			int can = Config.FEATHER_NUM2;

			L1ItemInstance sucitem = CObjectDB.LoadItem().createItem(Config.깃털번호);
			if (sucitem == null) return;

			int addCount = fn;
			String msgType = String.valueOf(fn);

			L1Clan clan = L1World.getInstance().getClan(pc.getClanid());

			if (clan != null) {
				if (clan.getCastleId() != 0) { // 성혈
					addCount = can + fn;
					msgType = fn + "+" + can;
				} else if (pc.getClanid() != 0) { // 일반 혈맹
					addCount = cln + fn;
					msgType = fn + "+" + cln;
				}
			}

			if (addCount > 0) {
				sucitem.setCount(addCount);
				pc.getInventory().CreateOrAddItem(sucitem);
				SC_SystemMessage.send(pc, sucitem.getName() + "(" + msgType + ")개를 얻었습니다.");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static final class Holder {
		final int objectId;
		volatile L1PcInstance pc;
		final AtomicBoolean cancelled = new AtomicBoolean(false);
		final AtomicLong ver = new AtomicLong(0L);
		final AtomicLong nextDueMs = new AtomicLong(0L);
		final ReentrantLock lock = new ReentrantLock();
		volatile RewardTask lastTask;

		Holder(int objectId) { this.objectId = objectId; }
		void bumpVer() { ver.incrementAndGet(); nextDueMs.set(0L); }
		boolean isCurrent(RewardTask t) { return t.ver == ver.get(); }
	}

	private static final class RewardTask implements Delayed {
		final int objectId;
		final long ver;
		final long dueTimeMs;
		volatile boolean cancelled = false;

		private RewardTask(int objectId, long ver, long dueTimeMs) {
			this.objectId = objectId;
			this.ver = ver;
			this.dueTimeMs = dueTimeMs;
		}

		@Override
		public long getDelay(TimeUnit unit) {
			return unit.convert(dueTimeMs - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
		}

		@Override
		public int compareTo(Delayed o) {
			if (this == o) return 0;
			return Long.compare(this.dueTimeMs, ((RewardTask) o).dueTimeMs);
		}
	}
}