package l1j.server.server.model.network.thread;

import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import l1j.server.server.ThreadPool;

/** A server-owned deadline on the existing CIO wheel. World work runs on the shared workers. */
public final class CServerTimer implements CIOThreadCallback.WheelTask {
    private static final long RETRY_MS = 50L;
    private static final long MAX_DELAY_MS = TimeUnit.DAYS.toMillis(36_500L);

    private volatile Runnable action;
    private final ThreadPool workers;
    private final Runnable worker;
    private final long intervalMs;
    private final boolean fixedRate;
    private long deadlineMs;

    // Accessed only under the server wheel monitor.
    int distantIndex = -1;

    private CServerTimer(Runnable action, long delayMs, long intervalMs,
            boolean fixedRate, boolean signalOnly) {
        this.action = Objects.requireNonNull(action);
        this.intervalMs = intervalMs;
        this.fixedRate = fixedRate;
        this.workers = signalOnly ? null : ThreadPool.getInstance();
        this.worker = signalOnly ? null : this::TimerExpired;
        this.deadlineMs = nowMs() + delayMs;
    }

    public static CServerTimer once(Runnable action, long delayMs) {
        return register(action, delayMs, 0L, false, false);
    }

    public static CServerTimer onceRequired(Runnable action, long delayMs) {
        final CServerTimer timer = once(action, delayMs);
        if (timer == null) throw new RejectedExecutionException("Server timer admission rejected");
        return timer;
    }

    public static CServerTimer fixedRate(Runnable action, long delayMs, long intervalMs) {
        return register(action, delayMs, intervalMs, true, false);
    }

    public static CServerTimer fixedDelay(Runnable action, long delayMs, long intervalMs) {
        return register(action, delayMs, intervalMs, false, false);
    }

    /** Only for bounded, nonblocking work publication. The signal must not run SQL or world callbacks. */
    public static CServerTimer fixedRateSignal(Runnable signal, long delayMs, long intervalMs) {
        return register(signal, delayMs, intervalMs, true, true);
    }

    /** Only for bounded, nonblocking work publication. */
    public static CServerTimer fixedDelaySignal(Runnable signal, long delayMs, long intervalMs) {
        return register(signal, delayMs, intervalMs, false, true);
    }

    private static CServerTimer register(Runnable action, long delayMs, long intervalMs,
            boolean fixedRate, boolean signalOnly) {
        final long delay = Math.clamp(delayMs, 1L, MAX_DELAY_MS);
        final long interval = Math.clamp(intervalMs, 0L, MAX_DELAY_MS);
        final CServerTimer timer = new CServerTimer(action, delay, interval, fixedRate, signalOnly);
        return timer.AddTimer(delay) ? timer : null;
    }

    private boolean AddTimer(long delayMs) {
        if (CIOThreadCallback.getInstance().scheduleServerTimer(this, delayMs)) return true;
        action = null;
        return false;
    }

    public void cancel() {
        action = null;
        CIOThreadCallback.getInstance().cancelDistantServerTimer(this);
    }

    void discard() { action = null; }

    @Override public boolean isCancelled() { return action == null; }

    @Override public void run() {
        if (isCancelled()) return;
        if (worker == null) {
            TimerExpired();
            return;
        }
        try {
            workers.execute(worker);
        } catch (RejectedExecutionException rejected) {
            if (workers.isShutdown()) cancel();
            else AddTimer(RETRY_MS);
        }
    }

    private void TimerExpired() {
        final Runnable current = action;
        if (current == null) return;
        try {
            current.run();
        } catch (RuntimeException | Error failure) {
            cancel();
            throw failure;
        }
        if (intervalMs == 0L) {
            action = null;
            return;
        }
        final long now = nowMs();
        final long delay = fixedRate
                ? intervalMs - Math.max(0L, now - deadlineMs) % intervalMs
                : intervalMs;
        deadlineMs = now + delay;
        AddTimer(delay);
    }

    private static long nowMs() { return System.nanoTime() / 1_000_000L; }
}
