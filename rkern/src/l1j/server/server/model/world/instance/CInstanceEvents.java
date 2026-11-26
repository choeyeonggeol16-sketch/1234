package l1j.server.server.model.world.instance;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import l1j.server.server.model.network.thread.CServerTimer;

/** Lifetime of instance scripts which wait during their event loop. */
public final class CInstanceEvents {
    private static final Logger LOG = Logger.getLogger(CInstanceEvents.class.getName());
    private static final int MAX_EVENTS = 256;
    private static final Object LOCK = new Object();
    private static final Set<BlockingEvent> EVENTS = new HashSet<>();
    private static final ThreadLocal<Boolean> EVENT_THREAD = new ThreadLocal<>();
    private static volatile boolean closed;

    private CInstanceEvents() {
    }

    public static Future<?> start(Runnable task) {
        return schedule(task, 0L);
    }

    public static Future<?> schedule(Runnable task, long delayMs) {
        final BlockingEvent event = new BlockingEvent(Objects.requireNonNull(task));
        synchronized (LOCK) {
            if (closed || EVENTS.size() >= MAX_EVENTS)
                throw new RejectedExecutionException("Instance event capacity unavailable");
            EVENTS.add(event);
        }
        try {
            if (delayMs <= 0) {
                event.launch();
            } else {
                event.deadline = CServerTimer.onceSignal(event::launch, delayMs, () -> event.cancel(false));
                if (event.deadline == null) throw new RejectedExecutionException("Instance event timer unavailable");
                if (event.isCancelled()) event.deadline.cancel();
            }
            return event;
        } catch (RuntimeException | Error failure) {
            event.cancel(false);
            throw failure;
        }
    }

    public static void stop() {
        final List<BlockingEvent> pending;
        synchronized (LOCK) {
            closed = true;
            pending = new ArrayList<>(EVENTS);
        }
        for (BlockingEvent event : pending) event.cancel(true);
    }

    public static boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        if (isEventThread()) throw new IllegalStateException("An instance event cannot await itself");
        final long deadline = System.nanoTime() + unit.toNanos(timeout);
        synchronized (LOCK) {
            while (!EVENTS.isEmpty()) {
                final long remaining = deadline - System.nanoTime();
                if (remaining <= 0L) return false;
                TimeUnit.NANOSECONDS.timedWait(LOCK, remaining);
            }
        }
        return true;
    }

    public static boolean isStopping() {
        return closed;
    }

    public static boolean isEventThread() {
        return EVENT_THREAD.get() != null;
    }

    public static int pendingEvents() {
        synchronized (LOCK) {
            return EVENTS.size();
        }
    }

    private static final class BlockingEvent extends FutureTask<Void> {
        // Reserved, running, retired. Future cancellation does not retire running work.
        private final AtomicInteger state = new AtomicInteger();
        private volatile CServerTimer deadline;
        private volatile Thread eventThread;

        BlockingEvent(Runnable task) { super(task, null); }

        void launch() {
            if (closed || isCancelled()) {
                cancel(false);
                return;
            }
            try {
                Thread.ofVirtual().name("RKern-InstanceEvent").inheritInheritableThreadLocals(false).start(this);
            } catch (RuntimeException | Error failure) {
                cancel(false);
                throw failure;
            }
        }

        @Override public void run() {
            if (!state.compareAndSet(0, 1)) return;
            eventThread = Thread.currentThread();
            EVENT_THREAD.set(Boolean.TRUE);
            try {
                super.run();
                if (state() == Future.State.FAILED) LOG.log(Level.SEVERE, "Blocking event failed", exceptionNow());
            } finally {
                EVENT_THREAD.remove();
                eventThread = null;
                if (state.compareAndSet(1, 2)) retire();
            }
        }

        @Override protected void done() {
            if (state.compareAndSet(0, 2)) retire();
        }

        @Override public boolean cancel(boolean interrupt) {
            final boolean cancelled = super.cancel(interrupt);
            if (!cancelled && interrupt) {
                // A prior cancel(false) marks the Future cancelled while its
                // body can still be asleep. Shutdown must interrupt that body.
                final Thread running = eventThread;
                if (running != null) running.interrupt();
            }
            final CServerTimer timer = deadline;
            if (timer != null) timer.cancel();
            return cancelled;
        }

        private void retire() {
            synchronized (LOCK) {
                EVENTS.remove(this);
                LOCK.notifyAll();
            }
        }
    }

}
