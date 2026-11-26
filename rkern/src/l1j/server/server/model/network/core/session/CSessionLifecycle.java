package l1j.server.server.model.network.core.session;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import l1j.server.server.task.COwnedWork;

/** Owns blocking session transitions after the transport or character owner has published them. */
public final class CSessionLifecycle {
    private static final COwnedWork RELEASES = new COwnedWork("RKern-SessionRelease-",
            Math.clamp(Runtime.getRuntime().availableProcessors(), 2, 8), 8192, 0);
    // A transfer waits for CacheD, so it cannot occupy the workers needed to
    // release the previous session or the CacheD workers needed by the barrier.
    private static final COwnedWork HANDOFFS = new COwnedWork("RKern-SessionHandoff-", 2, 1024, 0);

    private CSessionLifecycle() { }

    /** Socket release is mandatory. Its caller retains the terminal recovery path. */
    public static void release(Runnable task) throws RejectedExecutionException {
        RELEASES.execute(task);
    }

    /** Retire the published private store or retire the character for selection. */
    public static void retireCharacter(Runnable task) throws RejectedExecutionException {
        RELEASES.execute(task);
    }

    /** Wait for a retired session's database barriers before attaching its replacement. */
    public static void finishHandoff(Runnable task) throws RejectedExecutionException {
        HANDOFFS.execute(task);
    }

    public static void stopAccepting() {
        HANDOFFS.stopAccepting();
        RELEASES.stopAccepting();
    }

    public static boolean awaitDrained(long timeout, TimeUnit unit) throws InterruptedException {
        final long nanos = unit.toNanos(timeout);
        final long start = System.nanoTime();
        return HANDOFFS.awaitDrained(nanos, TimeUnit.NANOSECONDS)
                && RELEASES.awaitDrained(Math.max(0L, nanos - (System.nanoTime() - start)), TimeUnit.NANOSECONDS);
    }
}
