package l1j.server.server.model.network.core.transport.selector;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Bounded direct receive storage owned by one Selector thread. */
public final class SelectorReceivePool {
    static final int COMPACT_BYTES = 4 * 1024;
    static final int LARGE_BYTES = 64 * 1024;

    public record Stats(int inUse, long borrows, long returns, long allocatedDirectBytes, long maxDirectBytes) { }

    private final Thread owner = Thread.currentThread();
    private final Bucket compact;
    private final Bucket large;

    public SelectorReceivePool(int compactSlots, int largeSlots) {
        if (compactSlots < 1 || largeSlots < 1) throw new IllegalArgumentException("Invalid receive pool size");
        compact = new Bucket(COMPACT_BYTES, compactSlots);
        large = new Bucket(LARGE_BYTES, largeSlots);
    }

    Lease acquireCompact() {
        requireOwner();
        return compact.acquire();
    }

    Lease acquireLarge() {
        requireOwner();
        return large.acquire();
    }

    void release(Lease lease) {
        requireOwner();
        if (lease == null) return;
        if (lease.bucket != compact && lease.bucket != large) throw new IllegalArgumentException("Foreign lease");
        lease.bucket.release(lease);
    }

    public Stats stats() {
        requireOwner();
        return new Stats(compact.used() + large.used(), compact.borrows + large.borrows,
                compact.returns + large.returns, compact.allocated + large.allocated,
                (long) COMPACT_BYTES * compact.free.length + (long) LARGE_BYTES * large.free.length);
    }

    private void requireOwner() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Wrong receive pool owner");
    }

    static final class Lease {
        private final Bucket bucket;
        final ByteBuffer buffer;
        private boolean active;

        private Lease(Bucket bucket) {
            this.bucket = bucket;
            buffer = ByteBuffer.allocateDirect(bucket.bytes).order(ByteOrder.LITTLE_ENDIAN);
        }
    }

    private static final class Bucket {
        private final int bytes;
        private final Lease[] free;
        private int available;
        private long allocated;
        private long borrows;
        private long returns;

        Bucket(int bytes, int capacity) {
            this.bytes = bytes;
            free = new Lease[capacity];
            available = capacity;
        }

        Lease acquire() {
            if (available == 0) return null;
            int index = available - 1;
            Lease lease = free[index];
            if (lease == null) {
                lease = new Lease(this);
                allocated += bytes;
            }
            available = index;
            free[index] = null;
            lease.active = true;
            borrows++;
            return lease;
        }

        void release(Lease lease) {
            if (!lease.active) throw new IllegalStateException("Double lease release");
            lease.active = false;
            lease.buffer.clear();
            free[available++] = lease;
            returns++;
        }

        int used() { return free.length - available; }
    }
}
