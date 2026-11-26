package l1j.server.server.model.world;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import l1j.server.server.model.network.thread.CServerTimer;
import l1j.server.server.task.COwnedWork;

/** World-wide work such as rebuilds, multi-actor settlement and blocking administrative work. */
public final class CWorldWork {
    private static final COwnedWork WORK = new COwnedWork("RKern-World-",
            Math.clamp(Runtime.getRuntime().availableProcessors(), 4, 16), 32_768, 65_536);

    private CWorldWork() {
    }

    public static void execute(Runnable action) { WORK.execute(action); }
    public static boolean tryExecute(Runnable action) { return WORK.tryExecute(action); }

    public static CServerTimer schedule(Runnable action, long delayMs) {
        return WORK.schedule(action, delayMs);
    }

    public static CServerTimer scheduleRequired(Runnable action, long delayMs) {
        return WORK.scheduleRequired(action, delayMs);
    }

    private static CServerTimer scheduleOnWorldClock(Runnable action, long delayMs) {
        return CServerTimer.onceOnShard(WORK, 0, action, delayMs);
    }

    public static CServerTimer scheduleRequiredOnWorldClock(Runnable action, long delayMs) {
        final CServerTimer timer = scheduleOnWorldClock(action, delayMs);
        if (timer == null) throw new RejectedExecutionException("World timer unavailable");
        return timer;
    }

    public static CServerTimer scheduleAtFixedRate(Runnable action, long delayMs, long intervalMs) {
        return CServerTimer.fixedRateOnShard(WORK, 0, action, delayMs, intervalMs);
    }

    public static CServerTimer scheduleWithFixedDelay(Runnable action, long delayMs, long intervalMs) {
        return CServerTimer.fixedDelayOnShard(WORK, 0, action, delayMs, intervalMs);
    }

    public static void stopAccepting() { WORK.stopAccepting(); }

    public static boolean awaitDrained(long timeout, TimeUnit unit) throws InterruptedException {
        return WORK.awaitDrained(timeout, unit);
    }
}
