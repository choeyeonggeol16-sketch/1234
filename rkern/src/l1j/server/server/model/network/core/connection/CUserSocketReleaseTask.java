package l1j.server.server.model.network.core.connection;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.BooleanSupplier;

/** One deferred close owns its captured session through final L1 cleanup. */
public final class CUserSocketReleaseTask implements Runnable {
    private final BooleanSupplier ownsSession;
    private final Runnable logout, cleanup;
    private static final AtomicIntegerFieldUpdater<CUserSocketReleaseTask> STARTED =
            AtomicIntegerFieldUpdater.newUpdater(CUserSocketReleaseTask.class, "started");
    private volatile int started;
    public CUserSocketReleaseTask(BooleanSupplier ownsSession, Runnable logout, Runnable cleanup) {
        this.ownsSession = Objects.requireNonNull(ownsSession);
        this.logout = Objects.requireNonNull(logout);
        this.cleanup = Objects.requireNonNull(cleanup);
    }
    @Override public void run() {
        if (!STARTED.compareAndSet(this, 0, 1)) return;
        try { if (ownsSession.getAsBoolean()) logout.run(); }
        finally { cleanup.run(); }
    }
}
