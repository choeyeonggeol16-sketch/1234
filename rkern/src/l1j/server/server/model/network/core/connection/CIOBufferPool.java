package l1j.server.server.model.network.core.connection;

import java.util.concurrent.atomic.AtomicLong;

/** Bounded, lazy direct-buffer slots shared by async I/O connections. */
public final class CIOBufferPool {
    public static final int HEADER_BYTES = 2;
    public static final int MAX_WIRE_BYTES = 0xffff;
    public static final int RECV_BYTES = 4096;
    public static final int SMALL_SEND_BYTES = 16 * 1024;
    public static final int LARGE_BYTES = 65536;
    private static final int MAX_SMALL_SHARED_SOURCES = 64;
    private static final int MAX_LARGE_SHARED_SOURCES = 16;

    private final Bucket _recv;
    private final Bucket _largeRecv;
    private final Bucket _send;
    private final Bucket _largeSend;
    private final Bucket _sharedSource;
    private final Bucket _largeSharedSource;
    private final int _receiveShards;
    private final AtomicLong _sharedSourceRefusals = new AtomicLong();
    private final AtomicLong _optionalBroadcastRefusals = new AtomicLong();

    public CIOBufferPool(int maxConnections, int sendSlots, int largeRecvSlots,
                           int largeSendSlots) {
        this(maxConnections, sendSlots, largeRecvSlots, largeSendSlots, 0);
    }

    public CIOBufferPool(int maxConnections, int sendSlots, int largeRecvSlots,
                           int largeSendSlots, int receiveShards) {
        if (maxConnections <= 0 || sendSlots <= 0 || largeRecvSlots <= 0 || largeSendSlots <= 0) {
            throw new IllegalArgumentException("all pool capacities must be positive");
        }
        if (receiveShards < 0) throw new IllegalArgumentException("receiveShards < 0");
        _receiveShards = receiveShards;
        _recv = new Bucket(RECV_BYTES, maxConnections);
        _largeRecv = new Bucket(LARGE_BYTES, largeRecvSlots);
        _send = new Bucket(SMALL_SEND_BYTES, sendSlots);
        _largeSend = new Bucket(LARGE_BYTES, largeSendSlots);
        // A frozen plaintext must be able to coexist with the last recipient
        // SEND lease. These lazy slabs add at most 2 MiB per listener.
        _sharedSource = new Bucket(SMALL_SEND_BYTES, Math.min(sendSlots, MAX_SMALL_SHARED_SOURCES));
        _largeSharedSource = new Bucket(LARGE_BYTES, Math.min(largeSendSlots, MAX_LARGE_SHARED_SOURCES));
    }

    public CIOBufferEx acquireRecv() {
        final CIOBufferEx lease = _recv.acquire(false, 0);

        return lease;
    }

    CIOBufferEx acquireRecv(int shard) {
        return _recv.acquireReceive(shard, _receiveShards);
    }

    public CIOBufferEx acquireLargeRecv() {
        final CIOBufferEx lease = _largeRecv.acquire(false, 0);

        return lease;
    }

    CIOBufferEx acquireLargeRecv(int shard) {
        return _largeRecv.acquireReceive(shard, _receiveShards);
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

    CIOBufferEx acquireSharedSource(int capacity) {
        if (capacity <= HEADER_BYTES || capacity > MAX_WIRE_BYTES) return null;
        final Bucket bucket = capacity > SMALL_SEND_BYTES ? _largeSharedSource : _sharedSource;
        final CIOBufferEx lease = bucket.acquire(false, 0);
        if (lease == null) _sharedSourceRefusals.incrementAndGet();
        return lease;
    }

    boolean ownsSend(CIOBufferEx lease) {
        return lease != null && (lease._bucket == _send || lease._bucket == _largeSend);
    }

    public boolean owns(CIOBufferEx lease) {
        return lease != null && (lease._bucket == _recv || lease._bucket == _largeRecv
                || lease._bucket == _send || lease._bucket == _largeSend
                || lease._bucket == _sharedSource || lease._bucket == _largeSharedSource);
    }

    public long allocatedDirectBytes() {
        return _recv._allocated.get() + _largeRecv._allocated.get()
                + _send._allocated.get() + _largeSend._allocated.get()
                + _sharedSource._allocated.get() + _largeSharedSource._allocated.get();
    }

    public long maxDirectBytes() {
        return (long) _recv._bytes * _recv._slots.capacity()
                + (long) _largeRecv._bytes * _largeRecv._slots.capacity()
                + (long) _send._bytes * _send._slots.capacity()
                + (long) _largeSend._bytes * _largeSend._slots.capacity()
                + (long) _sharedSource._bytes * _sharedSource._slots.capacity()
                + (long) _largeSharedSource._bytes * _largeSharedSource._slots.capacity();
    }

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

    public int usedSharedSourceSlots() { return _sharedSource.used() + _largeSharedSource.used(); }
    public long sharedSourceRefusals() { return _sharedSourceRefusals.get(); }
    public long optionalBroadcastRefusals() { return _optionalBroadcastRefusals.get(); }
    void recordOptionalBroadcastRefusal() { _optionalBroadcastRefusals.incrementAndGet(); }

    /** On-demand pool state: slotBytes, capacity, used, allocatedDirectBytes. */
    public long[][] snapshot() {
        final Bucket[] buckets = {_recv, _largeRecv, _send, _largeSend, _sharedSource, _largeSharedSource};
        final long[][] rows = new long[buckets.length][];
        for (int i = 0; i < buckets.length; i++) {
            final Bucket bucket = buckets[i];
            rows[i] = new long[] {bucket._bytes, bucket._slots.capacity(), bucket.used(), bucket._allocated.get()};
        }
        return rows;
    }

    /** Each slot and its Assemble are reused; no per-packet CIOBufferEx is allocated. */
    static final class Bucket {
        final int _bytes;
        final CIOSlotPool _slots;
        private final CIOBufferEx[] _leases;
        final AtomicLong _allocated = new AtomicLong();

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
            return acquireSlot(_slots.acquire(), send, requested);
        }

        private CIOBufferEx acquireReceive(int shard, int shards) {
            return acquireSlot(_slots.acquireNearShard(shard, shards), false, 0);
        }

        private CIOBufferEx acquireSlot(int slot, boolean send, int requested) {
            if (slot < 0) {
                return null;
            }
            try {
                CIOBufferEx lease = _leases[slot];
                if (lease == null) {
                    lease = new CIOBufferEx(this, slot);
                    _leases[slot] = lease;
                }
                lease.start(send, requested);
                return lease;
            } catch (Throwable failure) {
                _slots.release(slot);
                throw failure;
            }
        }

        private int used() {
            return _slots.usedCount();
        }
    }

    public void release(CIOBufferEx lease) {
        if (lease == null) return;
        if (!owns(lease)) throw new IllegalArgumentException("foreign buffer lease");
        lease.release();
    }
}
