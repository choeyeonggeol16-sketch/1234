package l1j.server.server.model.network.core;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLong;

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

    public Lease acquireRecv() {
        final Lease lease = _recv.acquire(false, 0);

        return lease;
    }

    public Lease acquireLargeRecv() {
        final Lease lease = _largeRecv.acquire(false, 0);

        return lease;
    }

    /** Returns null when bounded backing capacity is exhausted (never allocates unbounded memory). */
    public Lease acquireSend(int maxWireBytes) {
        if (maxWireBytes <= HEADER_BYTES || maxWireBytes > MAX_WIRE_BYTES) {
            return null;
        }
        final Lease lease = maxWireBytes > SMALL_SEND_BYTES
                ? _largeSend.acquire(true, maxWireBytes)
                : _send.acquire(true, maxWireBytes);

        return lease;
    }

    public boolean owns(Lease lease) {
        return lease != null && (lease._bucket == _recv || lease._bucket == _largeRecv
                || lease._bucket == _send || lease._bucket == _largeSend);
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

    /** Each slot and its WriteBuffer are reused; no per-packet Lease is allocated. */
    public static final class Lease {
        private static final AtomicIntegerFieldUpdater<Lease> ACTIVE =
                AtomicIntegerFieldUpdater.newUpdater(Lease.class, "_active");

        private final Bucket _bucket;
        private final int _slot;
        private volatile int _active;
        private int _generation;
        boolean _writePrepared;
        int _nativeSendFlags;
        boolean _packedFrames;
        // Only the internal fixed 8-byte action encoder sets this metadata.
        boolean _actionOnly;
        volatile boolean _ioPending;
        private ByteBuffer _bytes;
        private WriteBuffer _writer;
        private CIOFrame _directFrame;
        int _wireLength;
        int _frameCount;
        int _reservedWireBytes;

        private Lease(Bucket bucket, int slot) {
            _bucket = bucket;
            _slot = slot;
        }

        public ByteBuffer buffer() {
            if (_active == 0) throw new IllegalStateException("lease already released");
            return _bytes;
        }

        public WriteBuffer writer() {
            if (_active == 0 || _writer == null)
                throw new IllegalStateException("not an active SEND lease");
            return _writer;
        }

        public int wireLength() {
            return _wireLength;
        }
        CIOFrame beginDirectFrame(CUserSocket connection) {
            if (_active != 1 || _writer == null)
                throw new IllegalStateException("not a reserved SEND slot");
            if (_directFrame == null) _directFrame = new CIOFrame();
            _directFrame.beginIO(connection, this);
            return _directFrame;
        }

        /** Re-arm the same pooled buffer for an existing WritablePacket.writeData(). */
        WriteBuffer beginPacket(Class<?> packetClass) {
            if (_active != 1 || _writer == null || _writePrepared)
                throw new IllegalStateException("not a reserved SEND packet slot");
            _writer.releaseResources();
            _writer.armExternal(_bytes, 0, _reservedWireBytes);
            _writer.preparePacket(packetClass);
            return _writer;
        }

        private void start(boolean send, int capacity) {
            // The slot bitmap CAS has already granted exclusive ownership.
            // Avoid an unnecessary second CAS on the lease state.
            if (_active != 0) throw new IllegalStateException("lease acquired twice");
            _active = 1;
            try {
                _generation = _bucket._slots.generation(_slot);
                if (_bytes == null) {
                    _bytes = ByteBuffer.allocateDirect(_bucket._bytes)
                            .order(ByteOrder.LITTLE_ENDIAN);
                    _bucket._allocated.addAndGet(_bucket._bytes);
                }
                _bytes.clear();
                _wireLength = 0;
                _frameCount = 0;
                _reservedWireBytes = send ? capacity : 0;
                _writePrepared = false;
                _nativeSendFlags = 0;
                _packedFrames = false;
                _actionOnly = false;
                _ioPending = false;
                if (send) {
                    if (_writer == null) _writer = new WriteBuffer();
                    _writer.armExternal(_bytes, 0, capacity);
                    _writer.beginDirect();
                    _writer.position(HEADER_BYTES);
                }
            } catch (RuntimeException | Error failure) {
                if (_writer != null) _writer.releaseResources();
                ACTIVE.set(this, 0);
                throw failure;
            }
        }

        void publish() {
            if (!ACTIVE.compareAndSet(this, 1, 2))
                throw new IllegalStateException("frame already published or released");
        }

        void unpublish() {
            if (!ACTIVE.compareAndSet(this, 2, 1))
                throw new IllegalStateException("frame publication state invalid");
        }

        boolean reserved() { return _active == 1; }

        private void release() {
            final int prior = ACTIVE.getAndSet(this, 0);
            if (prior == 0)
                throw new IllegalStateException("double lease release");
            if (_writer != null) _writer.releaseResources();
            _wireLength = 0;
            _packedFrames = false;
            _actionOnly = false;
            _reservedWireBytes = 0;
            _bytes.clear();
            // ACTIVE.getAndSet above already guarantees a single lease releaser.
            _bucket._slots.releaseOwnedLease(_slot, _generation);
        }
    }

    private static final class Bucket {
        private final int _bytes;
        private final CIOSlotPool _slots;
        private final Lease[] _leases;
        private final AtomicLong _allocated = new AtomicLong();
        // Failure-only counter: no additional atomic operations on the SEND hot path.
        private final AtomicLong _exhaustions = new AtomicLong();

        private Bucket(int bytes, int capacity) {
            _bytes = bytes;
            _slots = new CIOSlotPool(bytes, capacity, 0);
            // The slot bitmap is the ownership gate. Only its current holder
            // can initialize _leases[slot]; release/acquire CAS publishes the
            // reference to the next holder. No extra lock or allocation after
            // a slot's first use.
            _leases = new Lease[capacity];
        }

        private Lease acquire(boolean send, int requested) {
            final int slot = _slots.acquire();
            if (slot < 0) {
                if (send) _exhaustions.incrementAndGet();
                return null;
            }
            Lease lease = _leases[slot];
            if (lease == null) {
                lease = new Lease(this, slot);
                _leases[slot] = lease;
            }
            try {
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

    public void release(Lease lease) {
        if (lease == null) return;
        if (!owns(lease)) throw new IllegalArgumentException("foreign buffer lease");
        lease.release();
    }
}
