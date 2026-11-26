package l1j.server.server.model.network.thread;

import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import l1j.server.server.task.COwnedWork;

/** A server deadline on the existing CIO wheel. */
public final class CServerTimer implements CIOThreadCallback.WheelTask {
    private static final long RETRY_MS = 50L;
    private static final Semaphore OWNER_SIGNALS = new Semaphore(65_536);
    private static volatile boolean ownerSignalsClosed;
    private static final long MAX_DELAY_MS = TimeUnit.DAYS.toMillis(36_500L);
    private static final AtomicReferenceFieldUpdater<CServerTimer, Runnable> ACTION =
            AtomicReferenceFieldUpdater.newUpdater(CServerTimer.class, Runnable.class, "action");

    private volatile Runnable action;
    private final COwnedWork service;
    private final int shard;
    private final Runnable worker;
    private Runnable onDiscard;
    private final long intervalMs;
    private final boolean fixedRate;
    private long deadlineMs;

    // Accessed only under the server wheel monitor.
    int distantIndex = -1;

    private CServerTimer(COwnedWork service, int shard, Runnable action, long delayMs, long intervalMs,
            boolean fixedRate, Runnable onDiscard) {
        this.action = action;
        this.intervalMs = intervalMs;
        this.fixedRate = fixedRate;
        this.service = service;
        this.shard = shard;
        this.worker = service == null ? null : this::TimerExpired;
        this.onDiscard = onDiscard;
        this.deadlineMs = nowMs() + delayMs;
    }

    public static CServerTimer onceOnShard(COwnedWork service, int shard, Runnable action, long delayMs) {
        return register(Objects.requireNonNull(service), Math.max(0, shard), action, delayMs, 0L, false, null);
    }

    /** Runs a bounded, nonblocking signal on an existing CIO owner. */
    public static CServerTimer onceSignal(Runnable signal, long delayMs, Runnable onDiscard) {
        return onceSignalOnShard(Math.max(0, CIOThreadCallback.currentShardIndex()), signal, delayMs, onDiscard);
    }

    public static CServerTimer onceSignalOnShard(int shard, Runnable signal, long delayMs, Runnable onDiscard) {
        return register(null, shard, signal, delayMs, 0L, false, onDiscard);
    }

    public static void stopOwnerSignals() {
        ownerSignalsClosed = true;
    }

    public static CServerTimer fixedRateOnShard(COwnedWork service, int shard, Runnable action,
            long delayMs, long intervalMs) {
        return register(Objects.requireNonNull(service), shard, action, delayMs, intervalMs, true, null);
    }

    public static CServerTimer fixedDelayOnShard(COwnedWork service, int shard, Runnable action,
            long delayMs, long intervalMs) {
        return register(Objects.requireNonNull(service), shard, action, delayMs, intervalMs, false, null);
    }

    /** Only for bounded, nonblocking work publication. The signal must not run SQL or world callbacks. */
    public static CServerTimer fixedRateSignal(Runnable signal, long delayMs, long intervalMs) {
        return fixedRateSignalOnShard(0, signal, delayMs, intervalMs);
    }

    public static CServerTimer fixedRateSignalOnShard(int shard, Runnable signal, long delayMs,
            long intervalMs) {
        return register(null, shard, signal, delayMs, intervalMs, true, null);
    }

    /** Only for bounded, nonblocking work publication. */
    public static CServerTimer fixedDelaySignal(Runnable signal, long delayMs, long intervalMs) {
        return register(null, 0, signal, delayMs, intervalMs, false, null);
    }

    private static CServerTimer register(COwnedWork service, int shard, Runnable action, long delayMs, long intervalMs,
            boolean fixedRate, Runnable onDiscard) {
        Objects.requireNonNull(action);
        final long delay = Math.clamp(delayMs, 1L, MAX_DELAY_MS);
        final long interval = Math.clamp(intervalMs, 0L, MAX_DELAY_MS);
        if (shard < 0 || shard >= CIOThreadCallback.getInstance().shardCount() || !reserveTimer(service)) return null;
        CServerTimer timer = null;
        try {
            timer = new CServerTimer(service, shard, action, delay, interval, fixedRate, onDiscard);
            return timer.AddTimer(delay) ? timer : null;
        } catch (RuntimeException | Error failure) {
            if (timer == null) releaseTimer(service);
            else timer.discard();
            throw failure;
        }
    }

    private static boolean reserveTimer(COwnedWork service) {
        if (service != null) return service.reserveTimer();
        if (ownerSignalsClosed || !OWNER_SIGNALS.tryAcquire()) return false;
        if (!ownerSignalsClosed) return true;
        OWNER_SIGNALS.release();
        return false;
    }

    private static void releaseTimer(COwnedWork service) {
        if (service == null) OWNER_SIGNALS.release();
        else service.releaseTimer();
    }

    private boolean isExecutionClosed() {
        return service == null ? ownerSignalsClosed : service.isClosed();
    }

    private boolean AddTimer(long delayMs) {
        if (!isExecutionClosed() && CIOThreadCallback.getInstance().scheduleServerTimer(shard, this, delayMs)) return true;
        discard();
        return false;
    }

    public boolean cancel() {
        if (!releaseAction()) return false;
        onDiscard = null;
        CIOThreadCallback.getInstance().cancelDistantServerTimer(shard, this);
        return true;
    }

    private boolean releaseAction() {
        if (ACTION.getAndSet(this, null) == null) return false;
        releaseTimer(service);
        return true;
    }

    void discard() {
        if (!releaseAction()) return;
        final Runnable cleanup = onDiscard;
        onDiscard = null;
        if (cleanup != null) cleanup.run();
    }

    @Override public boolean isCancelled() { return action == null; }

    @Override public void run() {
        if (isCancelled()) return;
        if (isExecutionClosed()) {
            discard();
            return;
        }
        if (worker == null) {
            TimerExpired();
            return;
        }
        try {
            if (!service.tryExecute(worker)) AddTimer(RETRY_MS);
        } catch (RuntimeException | Error failure) {
            discard();
            throw failure;
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
            if (releaseAction()) onDiscard = null;
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
