package l1j.server.server.model.network.core;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/** One deferred close owns its captured session through final L1 cleanup. */
final class CUserSocketCloseTask implements Runnable {
    private final BooleanSupplier ownsSession;
    private final Runnable logout, cleanup;
    private final AtomicBoolean started = new AtomicBoolean();
    CUserSocketCloseTask(BooleanSupplier ownsSession, Runnable logout, Runnable cleanup) {
        this.ownsSession = Objects.requireNonNull(ownsSession);
        this.logout = Objects.requireNonNull(logout);
        this.cleanup = Objects.requireNonNull(cleanup);
    }
    @Override public void run() {
        if (!started.compareAndSet(false, true)) return;
        try { if (ownsSession.getAsBoolean()) logout.run(); }
        finally { cleanup.run(); }
    }
}
