package l1j.server.server.model.network.core;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import l1j.server.server.model.network.buffer.WriteBuffer;
/** L1 Java view of a pooled CIOBufferEx; JVM object layout is not the original C++ ABI. */
public final class CIOBufferEx {
    private static final AtomicIntegerFieldUpdater<CIOBufferEx> ACTIVE =
            AtomicIntegerFieldUpdater.newUpdater(CIOBufferEx.class, "_active");

    final CIOBufferPool.Bucket _bucket;
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

    CIOBufferEx(CIOBufferPool.Bucket bucket, int slot) {
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
            _nativeSendFlags = 0;
            _packedFrames = false;
            _actionOnly = false;
            _ioPending = false;
            if (send) {
                if (_writer == null) _writer = new WriteBuffer();
                _writer.armExternal(_bytes, 0, capacity);
                _writer.beginDirect();
                _writer.position(CIOBufferPool.HEADER_BYTES);
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

    void release() {
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
