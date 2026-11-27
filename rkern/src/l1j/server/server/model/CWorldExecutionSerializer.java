package l1j.server.server.model;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.concurrent.locks.LockSupport;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.model.map.L1Map;

/**
 * Per-world CExecutionSerializer with a 2^22 ticket ring and eight slots of admission headroom.
 * The first submitting thread drains the FIFO. Reentrant submissions execute on that thread.
 * Pages hold typed fields for admitted operations. Empty drains can retire the backing pages.
 */
final class CWorldExecutionSerializer {
    private final CWorld world;

    CWorldExecutionSerializer(CWorld world) {
        this.world = world;
    }

    private static final Logger EXEC_LOG = Logger.getLogger(CWorld.class.getName());
    private static final int EXEC_SLOTS = 1 << 22;
    private static final int EXEC_MASK = EXEC_SLOTS - 1;
    private static final int EXEC_LIMIT = EXEC_SLOTS - 8;
    private static final int EXEC_PAGE_BITS = 10;
    private static final int EXEC_PAGE_SIZE = 1 << EXEC_PAGE_BITS;
    private static final int EXEC_PAGE_MASK = EXEC_PAGE_SIZE - 1;
    private static final int EXEC_PAGE_COUNT = EXEC_SLOTS >>> EXEC_PAGE_BITS;
    // Preserve the full admission ring; only cold, empty backing pages are reclaimed.
    private static final int EXEC_TRIM_MIN_PAGES = 64;
    private static final long EXEC_TRIM_INTERVAL_NS = 60_000_000_000L;
    private static final long EXEC_TRIM_GATE = Long.MIN_VALUE;

    private static final int EXEC_CANCEL = 8;

    private static final VarHandle EXEC_TAIL;
    private static final VarHandle EXEC_PENDING;
    private static final VarHandle EXEC_PAGE =
            MethodHandles.arrayElementVarHandle(ExecutionPage[].class);
    private static final VarHandle EXEC_READY =
            MethodHandles.arrayElementVarHandle(long[].class);

    static {
        try {
            final MethodHandles.Lookup lookup = MethodHandles.lookup();
            EXEC_TAIL = lookup.findVarHandle(CWorldExecutionSerializer.class, "executionTail", long.class);
            EXEC_PENDING = lookup.findVarHandle(CWorldExecutionSerializer.class, "executionPending", int.class);
        } catch (ReflectiveOperationException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    private static final class ExecutionPage {
        final L1Object[] objects = new L1Object[EXEC_PAGE_SIZE];
        final L1Map[] destinations = new L1Map[EXEC_PAGE_SIZE];
        final long[] coordinates = new long[EXEC_PAGE_SIZE];
        final long[] identities = new long[EXEC_PAGE_SIZE];
        final long[] ready = new long[EXEC_PAGE_SIZE];
    }

    private final ExecutionPage[] executionPages = new ExecutionPage[EXEC_PAGE_COUNT];

    private volatile long executionTail;
    private volatile long executionHead;
    private volatile int executionPending;
    private volatile Thread executionThread;
    private volatile Thread executionWaiter;
    private int executionDepth;
    // Mutated only under executionPages; the consumer reads it after a drain.
    private volatile int executionAllocatedPages;
    private volatile long executionLastTrimNs;

    int backlog() {
        final long outstanding = executionTail - executionHead;
        return (int) Math.max(0L, Math.min(EXEC_SLOTS, outstanding));
    }

    private ExecutionPage ensureExecutionPage(int index) {
        ExecutionPage page = (ExecutionPage) EXEC_PAGE.getAcquire(executionPages, index);
        if (page != null) return page;
        synchronized (executionPages) {
            page = executionPages[index];
            if (page == null) {
                page = new ExecutionPage();
                EXEC_PAGE.setRelease(executionPages, index, page);
                ++executionAllocatedPages;
            }
            return page;
        }
    }

    /**
     * Retire unused serializer pages only after the last admitted ticket drained.
     * A CAS gate on the tail prevents a new reservation while arrays are retired.
     * Advancing the empty ring by one full revolution also invalidates any
     * producer that loaded the old tail before the gate was acquired: it must
     * retry page initialization instead of publishing into a retired page.
     * No per-event locks, ticket-capacity reduction, or extra worker.
     */
    private void trimIdleExecutionPages() {
        if (executionAllocatedPages < EXEC_TRIM_MIN_PAGES) return;
        final long now = System.nanoTime();
        final long last = executionLastTrimNs;
        // A second large burst can be the last traffic for hours. The cooldown only
        // applies to the smaller, routinely reused working set.
        if (executionAllocatedPages < EXEC_TRIM_MIN_PAGES * 4
                && last != 0L && now - last < EXEC_TRIM_INTERVAL_NS) return;
        final long emptyTicket = executionHead;
        if (emptyTicket < 0L || !EXEC_TAIL.compareAndSet(this,
                emptyTicket, emptyTicket | EXEC_TRIM_GATE)) return;
        final long nextTicket = emptyTicket + EXEC_SLOTS;
        try {
            synchronized (executionPages) {
                Arrays.fill(executionPages, null);
                executionAllocatedPages = 0;
                executionLastTrimNs = now;
            }
            executionHead = nextTicket;
        } finally {
            // Publish the new generation only after all retired pages and
            // head position are visible. Waiting producers then retry.
            EXEC_TAIL.setRelease(this, nextTicket);
        }
    }

    boolean submit(int operation, L1Object object,
            int x, int y, L1Map destination, long identity) {
        if (Thread.currentThread() == executionThread) {
            run(operation, object, x, y, destination, identity);
            return true;
        }
        long ticket;
        int slot;
        ExecutionPage page;
        for (;;) {
            ticket = executionTail;
            if (ticket < 0L) {
                Thread.onSpinWait();
                continue;
            }
            if (ticket - executionHead >= EXEC_LIMIT) return false;
            slot = (int) ticket & EXEC_MASK;
            page = ensureExecutionPage(slot >>> EXEC_PAGE_BITS);
            if (EXEC_TAIL.compareAndSet(this, ticket, ticket + 1L)) break;
        }
        final int entry = slot & EXEC_PAGE_MASK;
        int publishedOperation = operation;
        try {
            page.objects[entry] = object;
            page.coordinates[entry] =
                    ((long) x << 32) | (y & 0xffffffffL);
            page.destinations[entry] = destination;
            page.identities[entry] = identity;
        } catch (Throwable failure) {
            // A reserved ticket must always be consumable by the drainer.
            publishedOperation = EXEC_CANCEL;
            EXEC_READY.setRelease(page.ready, entry,
                    ((ticket + 1L) << 4) | publishedOperation);
            signalWorld();
            throw failure;
        }
        EXEC_READY.setRelease(page.ready, entry,
                ((ticket + 1L) << 4) | publishedOperation);
        signalWorld();
        return true;
    }

    private void signalWorld() {
        final Thread waiter = executionWaiter;
        if (waiter != null) LockSupport.unpark(waiter);
        if ((int) EXEC_PENDING.getAndAdd(this, 1) == 0) {
            drainWorldExecutables();
        }
    }

    private void drainWorldExecutables() {
        final Thread runner = Thread.currentThread();
        executionThread = runner;
        for (;;) {
            final long ticket = executionHead;
            final int slot = (int) ticket & EXEC_MASK;
            final ExecutionPage page = (ExecutionPage) EXEC_PAGE.getAcquire(executionPages, slot >>> EXEC_PAGE_BITS);
            final int entry = slot & EXEC_PAGE_MASK;
            final long[] ready = page.ready;
            final long expected = (ticket + 1L) << 4;
            long stamp;
            int spins = 0;
            while (((stamp = (long) EXEC_READY.getAcquire(ready, entry)) & ~15L)
                    != expected) {
                if (++spins < 1000) {
                    Thread.onSpinWait();
                } else {
                    executionWaiter = runner;
                    if (((long) EXEC_READY.getAcquire(ready, entry) & ~15L)
                            != expected) {
                        LockSupport.parkNanos(this, 10_000_000L);
                    }
                    executionWaiter = null;
                    spins = 0;
                }
            }
            try {
                final int operation = (int) (stamp & 15L);
                if (operation != EXEC_CANCEL) {
                    final long position = page.coordinates[entry];
                    run(operation, page.objects[entry],
                            (int) (position >>> 32), (int) position,
                            page.destinations[entry],
                            page.identities[entry]);
                }
            } catch (Throwable failure) {
                EXEC_LOG.log(Level.SEVERE, "CWorld executable failed", failure);
            } finally {
                page.objects[entry] = null;
                page.destinations[entry] = null;
                executionHead = ticket + 1L;
            }
            executionThread = null;
            if ((int) EXEC_PENDING.getAndAdd(this, -1) == 1) {
                trimIdleExecutionPages();
                return;
            }
            executionThread = runner;
        }
    }

    private void run(int operation, L1Object object, int x, int y, L1Map destination, long identity) {
        if (++executionDepth > 4) {
            EXEC_LOG.warning("CWorld nested executable depth=" + executionDepth);
        }
        try {
            world.executeWorld(operation, object, x, y, destination, identity);
        } finally {
            --executionDepth;
        }
    }
}
