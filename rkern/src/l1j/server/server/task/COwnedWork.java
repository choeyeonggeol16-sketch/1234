package l1j.server.server.task;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.network.thread.CServerTimer;

/** Bounded off-owner work owned by a single service. CIO owns its deadlines. */
public final class COwnedWork {
    private static final Logger LOG = Logger.getLogger(COwnedWork.class.getName());
    private static final ThreadLocal<COwnedWork> CURRENT = new ThreadLocal<>();
    private static final long IDLE_SECONDS = 10L;

    private final String name;
    private final ThreadPoolExecutor workers;
    private final int timerLimit;
    private final Semaphore timers;

    public COwnedWork(String threadName, int parallelism, int queueLimit, int timerLimit) {
        name = threadName;
        this.timerLimit = timerLimit;
        timers = new Semaphore(timerLimit);
        final ThreadFactory platform = Thread.ofPlatform().daemon(false).name(threadName, 1)
                .inheritInheritableThreadLocals(false).factory();
        workers = new ThreadPoolExecutor(parallelism, parallelism, IDLE_SECONDS, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueLimit), runner -> platform.newThread(() -> {
                    CURRENT.set(this);
                    try {
                        runner.run();
                    } finally {
                        CURRENT.remove();
                    }
                })) {
            @Override protected void afterExecute(Runnable task, Throwable failure) {
                if (failure != null) LOG.log(Level.SEVERE, name + " work failed", failure);
            }
        };
        workers.allowCoreThreadTimeOut(true);
    }

    public void execute(Runnable task) {
        workers.execute(task);
    }

    public boolean tryExecute(Runnable task) {
        try {
            workers.execute(task);
            return true;
        } catch (RejectedExecutionException unavailable) {
            return false;
        }
    }

    public CServerTimer schedule(Runnable action, long delayMs) {
        return CServerTimer.onceOnShard(this, Math.max(0, CIOThreadCallback.currentShardIndex()), action, delayMs);
    }

    public CServerTimer scheduleRequired(Runnable action, long delayMs) {
        final CServerTimer timer = schedule(action, delayMs);
        if (timer == null) throw new RejectedExecutionException(name + " timer unavailable");
        return timer;
    }

    public CServerTimer scheduleAtFixedRate(Runnable action, long delayMs, long intervalMs) {
        if (intervalMs <= 0L) return null;
        return CServerTimer.fixedRateOnShard(this,
                Math.max(0, CIOThreadCallback.currentShardIndex()),
                action, delayMs, intervalMs);
    }

    public boolean isClosed() { return workers.isShutdown(); }

    public boolean reserveTimer() {
        if (isClosed() || !timers.tryAcquire()) return false;
        if (!isClosed()) return true;
        timers.release();
        return false;
    }

    public void releaseTimer() { timers.release(); }

    public void stopAccepting() { workers.shutdown(); }

    public boolean awaitDrained(long timeout, TimeUnit unit) throws InterruptedException {
        if (isWorkerThread()) throw new IllegalStateException("A worker cannot await its own service");
        return workers.awaitTermination(timeout, unit);
    }

    public boolean isWorkerThread() { return CURRENT.get() == this; }
    public static boolean onServiceWorker() { return CURRENT.get() != null; }

    public int pendingWork() { return workers.getActiveCount() + workers.getQueue().size(); }
    public int pendingTimers() { return timerLimit - timers.availablePermits(); }
}
