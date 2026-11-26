package l1j.server.server.model.world.instance;

import java.util.concurrent.TimeUnit;

import l1j.server.server.model.network.thread.CServerTimer;
import l1j.server.server.task.COwnedWork;

/** Finite instance actions that sweep a room, settle rewards, or access storage. */
public final class CInstanceWork {
    private static final COwnedWork WORK = new COwnedWork(
            "RKern-Instance-", Math.clamp(Runtime.getRuntime().availableProcessors(), 4, 16),
            16_384, 32_768);

    private CInstanceWork() {
    }

    public static void execute(Runnable task) {
        WORK.execute(task);
    }

    public static boolean tryExecute(Runnable task) {
        return WORK.tryExecute(task);
    }

    public static CServerTimer schedule(Runnable task, long delayMs) {
        return WORK.schedule(task, delayMs);
    }

    public static CServerTimer scheduleRequired(Runnable task, long delayMs) {
        return WORK.scheduleRequired(task, delayMs);
    }

    public static boolean isClosed() {
        return WORK.isClosed();
    }

    public static void stopAccepting() {
        WORK.stopAccepting();
    }

    public static boolean awaitDrained(long timeout, TimeUnit unit) throws InterruptedException {
        return WORK.awaitDrained(timeout, unit);
    }

    public static boolean isWorkerThread() {
        return WORK.isWorkerThread();
    }
}
