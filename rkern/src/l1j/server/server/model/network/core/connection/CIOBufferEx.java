package l1j.server.server.model.network.core.connection;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import l1j.server.server.model.network.buffer.CPacketWriteBuffer;
/** L1 Java view of a pooled CIOBufferEx; JVM object layout is not the original C++ ABI. */
public final class CIOBufferEx {
    private static final AtomicIntegerFieldUpdater<CIOBufferEx> ACTIVE =
            AtomicIntegerFieldUpdater.newUpdater(CIOBufferEx.class, "_active");

    final CIOBufferPool.Bucket _bucket;
    private final int _slot;
    private volatile int _active;
    private int _generation;
    boolean _writePrepared;
    boolean _packedFrames;
    // Only the internal fixed 8-byte action encoder sets this metadata.
    boolean _actionOnly;
    volatile boolean _ioPending;
    private ByteBuffer _bytes;
    private CPacketWriteBuffer _writer;
    private CIOFrame _directFrame;
    int _wireLength;
    int _frameCount;
    int _reservedWireBytes;

    CIOBufferEx(CIOBufferPool.Bucket bucket, int slot) {
        _bucket = bucket;
        _slot = slot;
    }

    public ByteBuffer buffer() {
        if (_active == 0) throw new IllegalStateException("lease already released");
        return _bytes;
    }

    public CPacketWriteBuffer writer() {
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

    /** Re-arm the same pooled buffer for an existing IPacketWriter.writeData(). */
    CPacketWriteBuffer beginPacket(Class<?> packetClass) {
        if (_active != 1 || _writer == null || _writePrepared)
            throw new IllegalStateException("not a reserved SEND packet slot");
        _writer.releaseResources();
        _writer.armExternal(_bytes, 0, _reservedWireBytes);
        _writer.preparePacket(packetClass);
        return _writer;
    }

    void start(boolean send, int capacity) {
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
            _packedFrames = false;
            _actionOnly = false;
            _ioPending = false;
            if (send) {
                if (_writer == null) _writer = new CPacketWriteBuffer();
                _writer.armExternal(_bytes, 0, capacity);
                _writer.beginDirect();
                _writer.position(CIOBufferPool.HEADER_BYTES);
            }
        } catch (RuntimeException | Error failure) {
            try { if (_writer != null) _writer.releaseResources(); }
            catch (RuntimeException | Error cleanup) {
                if (cleanup != failure) failure.addSuppressed(cleanup);
            } finally {
                ACTIVE.set(this, 0);
            }
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

    void release() {
        final int prior = ACTIVE.getAndSet(this, 0);
        if (prior == 0)
            throw new IllegalStateException("double lease release");
        // Final Native ref release can throw (e.g. DLL/FFM shutdown). Retire
        // the physical slot even in that case, and preserve the first error.
        // Otherwise one failed cleanup permanently consumes a pool slot.
        Throwable failure = null;
        try {
            if (_writer != null) _writer.releaseResources();
            _wireLength = 0;
            _packedFrames = false;
            _actionOnly = false;
            _reservedWireBytes = 0;
            if (_bytes != null) _bytes.clear();
        } catch (RuntimeException | Error failed) { failure = failed; }
        try {
            _bucket._slots.releaseOwnedLease(_slot, _generation);
        } catch (RuntimeException | Error failed) {
            if (failure == null) failure = failed;
            else if (failure != failed) failure.addSuppressed(failed);
        }
        if (failure instanceof RuntimeException runtime) throw runtime;
        if (failure instanceof Error error) throw error;
    }

    /** Reusable direct SEND lease handle; owned by CIOBufferPool. */
    public static final class CIOFrame {
        private CUserSocket owner;
        private CIOBufferEx lease;
        private CPacketWriteBuffer buffer;
        private boolean active;
        private boolean optional;
        private int reservedBytes;
        private int reservedTicket = -1;
        private long reservedTicketId = -1L;

        CIOFrame() {}
        public CPacketWriteBuffer buffer() { return buffer; }
        void beginIO(CUserSocket owner, CIOBufferEx lease) {
            if (active) throw new IllegalStateException("outbound frame still reserved");
            this.owner = owner;
            this.lease = lease;
            this.buffer = lease.writer();
            optional = false;
            active = true;
        }
        void setReservation(int bytes, int ticket, long ticketId) {
            if (!active) throw new IllegalStateException("frame not active");
            reservedBytes = bytes;
            reservedTicket = ticket;
            reservedTicketId = ticketId;
        }
        int reservedBytes() { return reservedBytes; }
        int reservedTicket() { return reservedTicket; }
        long reservedTicketId() { return reservedTicketId; }
        void setOptional(boolean value) { optional = value; }
        boolean optional() { return optional; }
        CIOBufferEx ioLease(CUserSocket owner) {
            if (!active || this.owner != owner) throw new IllegalArgumentException("foreign frame");
            return lease;
        }
        void finishIO() {
            active = false;
            optional = false;
            reservedBytes = 0;
            reservedTicket = -1;
            reservedTicketId = -1L;
            owner = null;
            lease = null;
            buffer = null;
        }
    }
}
