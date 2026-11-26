package l1j.server.server.task;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.model.network.thread.CServerTimer;

/** Owns off-CIO work. Threads are created for ready work and exit when it drains. */
public enum TaskLane {
    WORLD("World", parallelism(4, 16), 32768, 65536),
    INSTANCE("Instance", parallelism(4, 16), 16384, 32768),
    SESSION("Session", parallelism(2, 8), 8192, 16384),
    PERSISTENCE("Persistence", parallelism(2, 8), 8192, 16384);

    private static final Logger LOG = Logger.getLogger(TaskLane.class.getName());
    private static final ThreadLocal<TaskLane> CURRENT = new ThreadLocal<>();

    private final ThreadFactory threads;
    private final int parallelism;
    private final int queueLimit;
    private final int timerLimit;
    private final Semaphore timers;
    private final ArrayDeque<Runnable> ready = new ArrayDeque<>();
    private int active;
    private volatile boolean closed;

    TaskLane(String name, int parallelism, int queueLimit, int timerLimit) {
        // These legacy callbacks can do JDBC under Java monitors. Platform
        // threads keep that code off the CIO and off Java 21 virtual carriers.
        this.threads = Thread.ofPlatform().daemon(false).name("RKern-" + name + "-", 1)
                .inheritInheritableThreadLocals(false).factory();
        this.parallelism = parallelism;
        this.queueLimit = queueLimit;
        this.timerLimit = timerLimit;
        this.timers = new Semaphore(timerLimit);
    }

    private static int parallelism(int min, int max) {
        return Math.clamp(Runtime.getRuntime().availableProcessors(), min, max);
    }

    public void execute(Runnable task) {
        if (!tryExecute(task)) throw new RejectedExecutionException(name() + " work queue unavailable");
    }

    public synchronized boolean tryExecute(Runnable task) {
        Objects.requireNonNull(task);
        if (closed) return false;
        if (active < parallelism) {
            ++active;
            try { threads.newThread(() -> drain(task)).start(); }
            catch (RuntimeException | Error failure) {
                --active;
                throw failure;
            }
        } else {
            if (ready.size() >= queueLimit) return false;
            ready.addLast(task);
        }
        return true;
    }

    private void drain(Runnable task) {
        CURRENT.set(this);
        try {
            for (;;) {
                try { task.run(); }
                catch (Throwable failure) {
                    try { LOG.log(Level.SEVERE, name() + " work failed", failure); }
                    catch (Throwable ignored) { /* preserve accepted work */ }
                }
                // One callback's interrupted status must not reach its successor.
                Thread.interrupted();
                synchronized (this) {
                    task = ready.pollFirst();
                    if (task == null) {
                        --active;
                        notifyAll();
                        return;
                    }
                }
            }
        } finally {
            CURRENT.remove();
        }
    }

    public CServerTimer schedule(Runnable work, long delayMs) {
        return CServerTimer.once(this, work, delayMs);
    }

    public CServerTimer scheduleRequired(Runnable work, long delayMs) {
        final CServerTimer timer = schedule(work, delayMs);
        if (timer == null) throw new RejectedExecutionException(name() + " timer unavailable");
        return timer;
    }

    public CServerTimer scheduleAtFixedRate(Runnable work, long delayMs, long periodMs) {
        if (periodMs <= 0) return null;
        return CServerTimer.fixedRate(this, work, delayMs, periodMs);
    }

    public boolean isClosed() { return closed; }

    public boolean reserveTimer() {
        if (closed || !timers.tryAcquire()) return false;
        if (!closed) return true;
        timers.release();
        return false;
    }

    public void releaseTimer() { timers.release(); }

    public static void stopAll() {
        for (TaskLane lane : values()) {
            synchronized (lane) {
                lane.closed = true;
            }
        }
    }

    public static boolean awaitAll(long timeout, TimeUnit unit) throws InterruptedException {
        if (isWorkerThread()) throw new IllegalStateException("Work cannot await its own lane");
        final long start = System.nanoTime();
        final long budget = unit.toNanos(timeout);
        for (TaskLane lane : values()) {
            synchronized (lane) {
                while (lane.active != 0) {
                    final long remaining = budget - (System.nanoTime() - start);
                    if (remaining <= 0) return false;
                    TimeUnit.NANOSECONDS.timedWait(lane, remaining);
                }
            }
        }
        return true;
    }

    public static boolean isWorkerThread() { return CURRENT.get() != null; }

    public synchronized int pendingWork() { return active + ready.size(); }
    public int pendingTimers() { return timerLimit - timers.availablePermits(); }
}
