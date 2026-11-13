package l1j.server.server.model.network.core;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import l1j.server.server.model.network.buffer.WriteBuffer;

/** Bounded, lazy direct-buffer slots shared by async I/O connections. */
public final class CIOBufferPool {
    public static final int HEADER_BYTES = 2;
    public static final int MAX_WIRE_BYTES = 0xffff;
    public static final int RECV_BYTES = 4096;
    public static final int SMALL_SEND_BYTES = 16 * 1024;
    public static final int LARGE_BYTES = 65536;

    private final Bucket _recv;
    private final Bucket _largeRecv;
    private final Bucket _send;
    private final Bucket _largeSend;

    public CIOBufferPool(int maxConnections, int sendSlots, int largeRecvSlots,
                           int largeSendSlots) {
        if (maxConnections <= 0 || sendSlots <= 0
                || largeRecvSlots <= 0 || largeSendSlots <= 0) {
            throw new IllegalArgumentException("all pool capacities must be positive");
        }
        _recv = new Bucket(RECV_BYTES, maxConnections);
        _largeRecv = new Bucket(LARGE_BYTES, largeRecvSlots);
        _send = new Bucket(SMALL_SEND_BYTES, sendSlots);
        _largeSend = new Bucket(LARGE_BYTES, largeSendSlots);
    }

    public CIOBufferEx acquireRecv() {
        final CIOBufferEx lease = _recv.acquire(false, 0);

        return lease;
    }

    public CIOBufferEx acquireLargeRecv() {
        final CIOBufferEx lease = _largeRecv.acquire(false, 0);

        return lease;
    }

    /** Returns null when bounded backing capacity is exhausted (never allocates unbounded memory). */
    public CIOBufferEx acquireSend(int maxWireBytes) {
        if (maxWireBytes <= HEADER_BYTES || maxWireBytes > MAX_WIRE_BYTES) {
            return null;
        }
        final CIOBufferEx lease = maxWireBytes > SMALL_SEND_BYTES
                ? _largeSend.acquire(true, maxWireBytes)
                : _send.acquire(true, maxWireBytes);

        return lease;
    }

    public boolean owns(CIOBufferEx lease) {
        return lease != null && (lease._bucket == _recv || lease._bucket == _largeRecv
                || lease._bucket == _send || lease._bucket == _largeSend);
    }
    CIOBufferEx acquireShared(CAsyncSendConstBuffer.Source source) {
        return _send.acquireShared(source);
    }

    public long allocatedDirectBytes() {
        return _recv._allocated.get() + _largeRecv._allocated.get()
                + _send._allocated.get() + _largeSend._allocated.get();
    }

    public long maxDirectBytes() {
        return (long) _recv._bytes * _recv._slots.capacity()
                + (long) _largeRecv._bytes * _largeRecv._slots.capacity()
                + (long) _send._bytes * _send._slots.capacity()
                + (long) _largeSend._bytes * _largeSend._slots.capacity();
    }

    /** Snapshot counters: increments only when a bounded SEND bucket is exhausted. */
    public long smallSendExhaustions() { return _send._exhaustions.get(); }
    public long largeSendExhaustions() { return _largeSend._exhaustions.get(); }
    public int smallSendSlots() { return _send._slots.capacity(); }
    public int largeSendSlots() { return _largeSend._slots.capacity(); }
    public int usedSmallSendSlots() { return _send.used(); }
    public int usedLargeSendSlots() { return _largeSend.used(); }

    public int usedSendSlots() {
        return _send.used() + _largeSend.used();
    }

    public int usedRecvSlots() {
        return _recv.used() + _largeRecv.used();
    }

    /** Rows: slotBytes,capacity,used,successfulBorrows,finalReturns,allocatedDirectBytes,exhaustions.
     * Concurrent snapshots can briefly straddle an acquire/return publication.
     */
    public long[][] snapshot() {
        Bucket[] buckets={_recv,_largeRecv,_send,_largeSend};long[][] rows=new long[4][];
        for(int i=0;i<4;i++){Bucket b=buckets[i];rows[i]=new long[]{b._bytes,b._slots.capacity(),b.used(),
                b._borrows.sum(),b._returns.sum(),b._allocated.get(),b._exhaustions.get()};}
        return rows;
    }

    /** Each slot and its WriteBuffer are reused; no per-packet CIOBufferEx is allocated. */
    static final class Bucket {
        final int _bytes;
        final CIOSlotPool _slots;
        private final CIOBufferEx[] _leases;
        final AtomicLong _allocated = new AtomicLong();
        // Failure-only counter: no additional atomic operations on the SEND hot path.
        private final AtomicLong _exhaustions = new AtomicLong();
        final LongAdder _borrows = new LongAdder(), _returns = new LongAdder();

        private Bucket(int bytes, int capacity) {
            _bytes = bytes;
            _slots = new CIOSlotPool(bytes, capacity, 0);
            // The slot bitmap is the ownership gate. Only its current holder
            // can initialize _leases[slot]; release/acquire CAS publishes the
            // reference to the next holder. No extra lock or allocation after
            // a slot's first use.
            _leases = new CIOBufferEx[capacity];
        }

        private CIOBufferEx acquire(boolean send, int requested) {
            final int slot = _slots.acquire();
            if (slot < 0) {
                _exhaustions.incrementAndGet();
                return null;
            }
            CIOBufferEx lease = _leases[slot];
            if (lease == null) {
                lease = new CIOBufferEx(this, slot);
                _leases[slot] = lease;
            }
            try {
                lease.start(send, requested);
                _borrows.increment();
                return lease;
            } catch (Throwable failure) {
                _slots.release(slot);
                throw failure;
            }
        }

        private int used() {
            return _slots.usedCount();
        }
        private CIOBufferEx acquireShared(CAsyncSendConstBuffer.Source source) {
            int slot = _slots.acquire();
            if (slot < 0) { _exhaustions.incrementAndGet(); return null; }
            try {
                CIOBufferEx lease = _leases[slot];
                if (lease == null) { lease = new CIOBufferEx(this, slot); _leases[slot] = lease; }
                lease.startShared(source); _borrows.increment(); return lease;
            }
            catch (Throwable failure) { _slots.release(slot); throw failure; }
        }
    }

    public void release(CIOBufferEx lease) {
        if (lease == null) return;
        if (!owns(lease)) throw new IllegalArgumentException("foreign buffer lease");
        lease.release();
    }
}
