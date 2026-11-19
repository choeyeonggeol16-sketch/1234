package l1j.server.server.model.character.scheduler;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import l1j.server.Config;
import l1j.server.server.model.Instance.L1PcInstance;

public final class AutoSaveChaQuitCheckScheduler {

    private static final Logger LOG = Logger.getLogger(AutoSaveChaQuitCheckScheduler.class.getName());
    private static volatile AutoSaveChaQuitCheckScheduler INSTANCE;

    public static AutoSaveChaQuitCheckScheduler getInstance() {
        AutoSaveChaQuitCheckScheduler v = INSTANCE;
        if (v == null) {
            synchronized (AutoSaveChaQuitCheckScheduler.class) {
                v = INSTANCE;
                if (v == null) {
                    INSTANCE = (v = new AutoSaveChaQuitCheckScheduler());
                }
            }
        }
        return v;
    }

    // ======= 설정 및 튜닝 =======
    private final long saveCharMs = (long) Config.AUTOSAVE_INTERVAL * 1000L;
    private final long saveInvMs  = (long) Config.AUTOSAVE_INTERVAL_INVENTORY * 1000L;
    private final long healthCheckMs = 60_000L; // 좀비 캐릭터 체크 주기 (1분)

    private long jitterMs(long base) {
        if (base <= 0) return 0;
        return ThreadLocalRandom.current().nextLong(0L, Math.max(1000L, base / 10L));
    }

    private static final int SAVE_WORKERS = 4; 
    private static final long RETRY_MIN_MS = 2_000L;
    private static final long RETRY_MAX_MS = 60_000L;
    private static final int WORKER_QUEUE_CAP = 5000;

    // ===========================

    private final DelayQueue<Task> q = new DelayQueue<Task>();
    
    private final Int2ObjectMap<Holder> holders = 
    		Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<Holder>(8192));

    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread dispatcher;
    private volatile ThreadPoolExecutor workerPool;

    // 통계용 변수
    private final LongAdder executedTasks = new LongAdder();
    private final LongAdder failedTasks   = new LongAdder();
    private final LongAdder zombieKicks   = new LongAdder();
    private final LongAdder staleTasks    = new LongAdder();

    private AutoSaveChaQuitCheckScheduler() {}

    /**
     * 스케줄러 시작
     */
    public void start() {
        if (!running.compareAndSet(false, true)) return;

        workerPool = new ThreadPoolExecutor(
                SAVE_WORKERS,
                SAVE_WORKERS,
                30L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<Runnable>(WORKER_QUEUE_CAP),
                r -> {
                    Thread t = new Thread(r, "AutoSaveWorker");
                    t.setDaemon(true);
                    t.setUncaughtExceptionHandler((th, ex) ->
                            LOG.log(Level.SEVERE, "[AutoSaveScheduler] worker error", ex));
                    return t;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );

        workerPool.prestartAllCoreThreads();

        dispatcher = new Thread(this::dispatchLoop, "AutoSaveDispatcher");
        dispatcher.setDaemon(true);
        dispatcher.setUncaughtExceptionHandler((th, ex) ->
                LOG.log(Level.SEVERE, "[AutoSaveScheduler] dispatcher error", ex));
        dispatcher.start();

     //   System.out.println("[AutoSaveScheduler] 통합 모듈 가동 시작. (저장/좀비체크)");
    }

    /**
     * 스케줄러 중단 (서버 종료 시 호출)
     */
    public void stop() {
        if (!running.compareAndSet(true, false)) return;

     //   System.out.println("[AutoSaveScheduler] 중단 요청됨. 자원 정리를 시작합니다.");

        if (dispatcher != null) {
            dispatcher.interrupt();
            dispatcher = null;
        }

        synchronized (holders) {
            for (Holder h : holders.values()) {
                h.cancelled.set(true);
                h.pc = null;
                if (h.lastCharTask != null) h.lastCharTask.cancelled = true;
                if (h.lastInvTask != null) h.lastInvTask.cancelled = true;
                if (h.lastCheckTask != null) h.lastCheckTask.cancelled = true;
            }
            holders.clear();
        }

        if (workerPool != null) {
            workerPool.shutdownNow();
            workerPool = null;
        }

        q.clear();
    //    System.out.println("[AutoSaveScheduler] 모든 작업이 중단되었습니다.");
    }

    /**
     * 유저 등록 (접속 시 호출)
     */
    public void register(L1PcInstance pc) {
        if (pc == null) return;
        final int id = pc.getId();
        final long now = System.currentTimeMillis();
        for (;;) {
            Holder h = holders.get(id);
            if (h == null) {
                Holder candidate = new Holder(id);
                Holder existing = holders.putIfAbsent(id, candidate);
                h = existing == null ? candidate : existing;
            }
            h.pcLock.lock();
            try {
                // An old logout can remove the holder while this login waits.
                if (holders.get(id) != h) continue;
                h.pc = pc;
                h.cancelled.set(false);
                h.bumpAllVer();
                if (saveCharMs > 0L)
                    scheduleCharIfEarlier(h, now + saveCharMs + jitterMs(saveCharMs), h.charVer.get());
                if (saveInvMs > 0L)
                    scheduleInvIfEarlier(h, now + saveInvMs + jitterMs(saveInvMs), h.invVer.get());
                scheduleCheckIfEarlier(h, now + healthCheckMs + jitterMs(5000L), h.checkVer.get());
                return;
            } finally { h.pcLock.unlock(); }
        }
    }

    public void unregister(L1PcInstance pc) {
        if (pc == null) return;
        final Holder h = holders.get(pc.getId());
        if (h == null) return;
        h.pcLock.lock();
        try {
            if (h.pc != pc || holders.get(pc.getId()) != h) return;
            synchronized (holders) {
                if (holders.get(pc.getId()) != h) return;
                holders.remove(pc.getId());
            }
            h.cancelled.set(true);
            h.bumpAllVer();
            h.pc = null;
            if (h.lastCharTask != null) h.lastCharTask.cancelled = true;
            if (h.lastInvTask != null) h.lastInvTask.cancelled = true;
            if (h.lastCheckTask != null) h.lastCheckTask.cancelled = true;
        } finally { h.pcLock.unlock(); }
    }

    private void dispatchLoop() {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                final Task task = q.take(); // 지연 시간이 다 된 작업 추출
                if (task.cancelled) {
                    staleTasks.increment();
                    continue;
                }

                final Holder h = holders.get(task.objectId);
                if (h == null || h.cancelled.get() || !h.isCurrent(task)) {
                    staleTasks.increment();
                    continue;
                }

                try {
                    workerPool.execute(() -> runTask(h, task));
                } catch (RejectedExecutionException rex) {
                    staleTasks.increment(); 
                    final Task retry = task.requeue(5_000L);
                    if (h.replaceRejected(task, retry)) q.offer(retry);
                }

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (Throwable t) {
                LOG.log(Level.SEVERE, "[AutoSaveScheduler] dispatch loop error", t);
            }
        }
    }

    private void runTask(Holder h, Task task) {
        boolean gotLock = false;
        try {
            h.pcLock.lockInterruptibly();
            gotLock = true;

            if (h.cancelled.get() || !h.claim(task) || task.cancelled) return;
            final L1PcInstance pc = h.pc;
            if (pc == null) return;

            final long now = System.currentTimeMillis();
            executedTasks.increment();

            // 작업 종류에 따른 실행
            if (task.kind == Kind.HEALTH_CHECK) {
                handleHealthCheck(h, pc, now);
            } else if (task.kind == Kind.CHAR) {
                handleSaveChar(h, pc, now);
            } else {
                handleSaveInv(h, pc, now);
            }

        } catch (Exception e) {
            failedTasks.increment();
        } finally {
            if (gotLock) h.pcLock.unlock();
        }
    }

    private void handleHealthCheck(Holder h, L1PcInstance pc, long now) {
        try {
            // 좀비 캐릭터 판별 로직
            if (pc.getNetConnection() == null || pc.getNetConnection().isClosed()) {
                if (!pc.isPrivateShop() && !pc.isAutoClanjoin() && !pc.noPlayerCK) {
                    LOG.warning("[좀비캐릭터제거] " + pc.getName() + " (연결 종료됨)");
                    zombieKicks.increment();
                    pc.logout();
                    unregister(pc);
                    return;
                }
            }
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, "Health check error: " + pc.getName(), t);
        }

        if (!h.cancelled.get()) {
            h.bumpCheckVer();
            scheduleCheckIfEarlier(h, now + healthCheckMs, h.checkVer.get());
        }
    }

    private void handleSaveChar(Holder h, L1PcInstance pc, long now) {
        try {
            pc.save();
            h.charFailCount.set(0);
        } catch (Throwable t) {
            int fc = h.charFailCount.incrementAndGet();
            LOG.log(Level.WARNING, "Char save failed: " + pc.getName(), t);
            scheduleCharIfEarlier(h, now + backoffMs(fc), h.charVer.get());
            return;
        }
        h.bumpCharVer();
        if (!h.cancelled.get() && saveCharMs > 0L) {
            scheduleCharIfEarlier(h, now + saveCharMs + jitterMs(saveCharMs), h.charVer.get());
        }
    }

    private void handleSaveInv(Holder h, L1PcInstance pc, long now) {
        try {
            pc.saveInventory();
            h.invFailCount.set(0);
        } catch (Throwable t) {
            int fc = h.invFailCount.incrementAndGet();
            LOG.log(Level.WARNING, "Inv save failed: " + pc.getName(), t);
            scheduleInvIfEarlier(h, now + backoffMs(fc), h.invVer.get());
            return;
        }
        h.bumpInvVer();
        if (!h.cancelled.get() && saveInvMs > 0L) {
            scheduleInvIfEarlier(h, now + saveInvMs + jitterMs(saveInvMs), h.invVer.get());
        }
    }

    private void scheduleCharIfEarlier(Holder h, long due, long ver) {
        while (true) {
            long cur = h.nextCharDueMs.get();
            if (cur != 0L && cur <= due) return;
            if (h.nextCharDueMs.compareAndSet(cur, due)) break;
        }
        Task t = Task.charTask(h.objectId, ver, due);
        h.lastCharTask = t;
        q.offer(t);
    }

    private void scheduleInvIfEarlier(Holder h, long due, long ver) {
        while (true) {
            long cur = h.nextInvDueMs.get();
            if (cur != 0L && cur <= due) return;
            if (h.nextInvDueMs.compareAndSet(cur, due)) break;
        }
        Task t = Task.invTask(h.objectId, ver, due);
        h.lastInvTask = t;
        q.offer(t);
    }

    private void scheduleCheckIfEarlier(Holder h, long due, long ver) {
        while (true) {
            long cur = h.nextCheckDueMs.get();
            if (cur != 0L && cur <= due) return;
            if (h.nextCheckDueMs.compareAndSet(cur, due)) break;
        }
        Task t = Task.checkTask(h.objectId, ver, due);
        h.lastCheckTask = t;
        q.offer(t);
    }

    private long backoffMs(int failCount) {
        return Math.min(RETRY_MAX_MS, RETRY_MIN_MS * (1L << Math.min(10, Math.max(0, failCount - 1))));
    }

    // 통계용 메서드들
    public long getExecutedCount() { return executedTasks.sum(); }
    public long getZombieKickCount() { return zombieKicks.sum(); }

    private enum Kind { CHAR, INV, HEALTH_CHECK }

    private static final class Holder {
        final int objectId;
        volatile L1PcInstance pc;
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        final AtomicLong charVer = new AtomicLong(0L);
        final AtomicLong invVer  = new AtomicLong(0L);
        final AtomicLong checkVer = new AtomicLong(0L);
        final AtomicInteger charFailCount = new AtomicInteger(0);
        final AtomicInteger invFailCount  = new AtomicInteger(0);
        final AtomicLong nextCharDueMs = new AtomicLong(0L);
        final AtomicLong nextInvDueMs  = new AtomicLong(0L);
        final AtomicLong nextCheckDueMs = new AtomicLong(0L);
        final ReentrantLock pcLock = new ReentrantLock();
        volatile Task lastCharTask, lastInvTask, lastCheckTask;

        Holder(int objectId) { this.objectId = objectId; }
        void bumpCharVer() { charVer.incrementAndGet(); nextCharDueMs.set(0L); }
        void bumpInvVer() { invVer.incrementAndGet(); nextInvDueMs.set(0L); }
        void bumpCheckVer() { checkVer.incrementAndGet(); nextCheckDueMs.set(0L); }
        void bumpAllVer() { bumpCharVer(); bumpInvVer(); bumpCheckVer(); }
        boolean claim(Task t) {
            if (!isCurrent(t)) return false;
            if (t.kind == Kind.CHAR) return lastCharTask == t
                    && nextCharDueMs.compareAndSet(t.dueTimeMs, 0L);
            if (t.kind == Kind.INV) return lastInvTask == t
                    && nextInvDueMs.compareAndSet(t.dueTimeMs, 0L);
            return lastCheckTask == t
                    && nextCheckDueMs.compareAndSet(t.dueTimeMs, 0L);
        }

        boolean replaceRejected(Task oldTask, Task retry) {
            if (!isCurrent(oldTask) || cancelled.get()) return false;
            if (oldTask.kind == Kind.CHAR && lastCharTask == oldTask
                    && nextCharDueMs.compareAndSet(oldTask.dueTimeMs, retry.dueTimeMs)) {
                lastCharTask = retry; return true;
            }
            if (oldTask.kind == Kind.INV && lastInvTask == oldTask
                    && nextInvDueMs.compareAndSet(oldTask.dueTimeMs, retry.dueTimeMs)) {
                lastInvTask = retry; return true;
            }
            if (oldTask.kind == Kind.HEALTH_CHECK && lastCheckTask == oldTask
                    && nextCheckDueMs.compareAndSet(oldTask.dueTimeMs, retry.dueTimeMs)) {
                lastCheckTask = retry; return true;
            }
            return false;
        }
        boolean isCurrent(Task t) { 
            if(t.kind == Kind.CHAR) return t.ver == charVer.get();
            if(t.kind == Kind.INV) return t.ver == invVer.get();
            return t.ver == checkVer.get();
        }
    }

    private static final class Task implements Delayed {
        final int objectId;
        final Kind kind;
        final long ver;
        final long dueTimeMs;
        volatile boolean cancelled = false;
        
        Task requeue(long delayMs) {
            return new Task(this.objectId, this.kind, this.ver, System.currentTimeMillis() + delayMs);
        }

        private Task(int objectId, Kind kind, long ver, long dueTimeMs) {
            this.objectId = objectId; this.kind = kind; this.ver = ver; this.dueTimeMs = dueTimeMs;
        }
        static Task charTask(int id, long ver, long due) { return new Task(id, Kind.CHAR, ver, due); }
        static Task invTask(int id, long ver, long due) { return new Task(id, Kind.INV, ver, due); }
        static Task checkTask(int id, long ver, long due) { return new Task(id, Kind.HEALTH_CHECK, ver, due); }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(dueTimeMs - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
        }
        @Override
        public int compareTo(Delayed o) {
            if (this == o) return 0;
            return Long.compare(this.dueTimeMs, ((Task) o).dueTimeMs);
        }
    }
}