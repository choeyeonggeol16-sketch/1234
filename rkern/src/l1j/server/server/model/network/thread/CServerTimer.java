package l1j.server.server.model.network.thread;

import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import l1j.server.server.task.TaskLane;

/** A server deadline on the existing CIO wheel, with off-CIO execution by its owner. */
public final class CServerTimer implements CIOThreadCallback.WheelTask {
    private static final long RETRY_MS = 50L;
    private static final long MAX_DELAY_MS = TimeUnit.DAYS.toMillis(36_500L);
    private static final AtomicReferenceFieldUpdater<CServerTimer, Runnable> ACTION =
            AtomicReferenceFieldUpdater.newUpdater(CServerTimer.class, Runnable.class, "action");

    private volatile Runnable action;
    private final TaskLane lane;
    private final int shard;
    private final Runnable worker;
    private final Runnable onDiscard;
    private final long intervalMs;
    private final boolean fixedRate;
    private long deadlineMs;

    // Accessed only under the server wheel monitor.
    int distantIndex = -1;

    private CServerTimer(TaskLane lane, int shard, Runnable action, long delayMs, long intervalMs,
            boolean fixedRate, boolean signalOnly, Runnable onDiscard) {
        this.action = action;
        this.intervalMs = intervalMs;
        this.fixedRate = fixedRate;
        this.lane = lane;
        this.shard = shard;
        this.worker = signalOnly ? null : this::TimerExpired;
        this.onDiscard = onDiscard;
        this.deadlineMs = nowMs() + delayMs;
    }

    public static CServerTimer once(Runnable action, long delayMs) {
        return register(TaskLane.WORLD, 0, action, delayMs, 0L, false, false, null);
    }

    public static CServerTimer once(TaskLane lane, Runnable action, long delayMs) {
        return onceOnShard(lane, CIOThreadCallback.currentShardIndex(), action, delayMs);
    }

    public static CServerTimer onceOnShard(TaskLane lane, int shard, Runnable action, long delayMs) {
        return register(lane, Math.max(0, shard), action, delayMs, 0L, false, false, null);
    }

    /** Only for nonblocking publication of an interruptible instance event. */
    public static CServerTimer onceSignal(TaskLane lane, Runnable signal, long delayMs, Runnable onDiscard) {
        return register(lane, Math.max(0, CIOThreadCallback.currentShardIndex()), signal, delayMs, 0L, false, true, onDiscard);
    }

    public static CServerTimer onceRequired(Runnable action, long delayMs) {
        final CServerTimer timer = once(action, delayMs);
        if (timer == null) throw new RejectedExecutionException("Server timer admission rejected");
        return timer;
    }

    public static CServerTimer fixedRate(Runnable action, long delayMs, long intervalMs) {
        return register(TaskLane.WORLD, 0, action, delayMs, intervalMs, true, false, null);
    }

    public static CServerTimer fixedRate(TaskLane lane, Runnable action, long delayMs, long intervalMs) {
        return register(lane, Math.max(0, CIOThreadCallback.currentShardIndex()), action,
                delayMs, intervalMs, true, false, null);
    }

    public static CServerTimer fixedDelay(Runnable action, long delayMs, long intervalMs) {
        return register(TaskLane.WORLD, 0, action, delayMs, intervalMs, false, false, null);
    }

    /** Only for bounded, nonblocking work publication. The signal must not run SQL or world callbacks. */
    public static CServerTimer fixedRateSignal(Runnable signal, long delayMs, long intervalMs) {
        return register(TaskLane.WORLD, 0, signal, delayMs, intervalMs, true, true, null);
    }

    /** Only for bounded, nonblocking work publication. */
    public static CServerTimer fixedDelaySignal(Runnable signal, long delayMs, long intervalMs) {
        return register(TaskLane.WORLD, 0, signal, delayMs, intervalMs, false, true, null);
    }

    private static CServerTimer register(TaskLane lane, int shard, Runnable action, long delayMs, long intervalMs,
            boolean fixedRate, boolean signalOnly, Runnable onDiscard) {
        Objects.requireNonNull(action);
        final long delay = Math.clamp(delayMs, 1L, MAX_DELAY_MS);
        final long interval = Math.clamp(intervalMs, 0L, MAX_DELAY_MS);
        if (lane.isClosed() || shard >= CIOThreadCallback.getInstance().shardCount() || !lane.reserveTimer()) return null;
        CServerTimer timer = null;
        try {
            timer = new CServerTimer(lane, shard, action, delay, interval, fixedRate, signalOnly, onDiscard);
            return timer.AddTimer(delay) ? timer : null;
        } catch (RuntimeException | Error failure) {
            if (timer == null) lane.releaseTimer();
            else timer.discard();
            throw failure;
        }
    }

    private boolean AddTimer(long delayMs) {
        if (!lane.isClosed() && CIOThreadCallback.getInstance().scheduleServerTimer(shard, this, delayMs)) return true;
        discard();
        return false;
    }

    public boolean cancel() {
        if (!releaseAction()) return false;
        CIOThreadCallback.getInstance().cancelDistantServerTimer(shard, this);
        return true;
    }

    private boolean releaseAction() {
        if (ACTION.getAndSet(this, null) == null) return false;
        lane.releaseTimer();
        return true;
    }

    void discard() {
        if (releaseAction() && onDiscard != null) onDiscard.run();
    }

    @Override public boolean isCancelled() { return action == null; }

    @Override public void run() {
        if (isCancelled()) return;
        if (lane.isClosed()) {
            discard();
            return;
        }
        if (worker == null) {
            TimerExpired();
            return;
        }
        try {
            if (!lane.tryExecute(worker)) AddTimer(RETRY_MS);
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
            releaseAction();
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
