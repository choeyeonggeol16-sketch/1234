package l1j.server.server.model.network.core.broadcast;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicInteger;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.network.core.connection.CIOBufferEx;
import l1j.server.server.model.network.core.connection.CIOBufferPool;
import l1j.server.server.model.network.core.connection.CUserSocket;

/** One pooled plaintext frame shared by ordered per-socket sends. */
public class CAsyncSendConstBuffer implements AutoCloseable {
    private final CSharedBroadcast.NativeWireWriter writer;
    private final int senderId;
    private final int largeFrameLength;
    private Source source;
    private boolean closed;
    private boolean freezing;

    /** Materialize the frame when the first connected recipient is eligible. */
    public CAsyncSendConstBuffer(CSharedBroadcast.NativeWireWriter writer,
                                 int senderId,int pendingLimit) {
        this(writer, senderId, pendingLimit, 0);
    }

    CAsyncSendConstBuffer(CSharedBroadcast.NativeWireWriter writer,
                          int senderId, int pendingLimit, int largeFrameLength) {
        if(writer==null||pendingLimit<=0)
            throw new IllegalArgumentException("Native constant writer/limit");
        if (largeFrameLength != 0 && (largeFrameLength <= CIOBufferPool.SMALL_SEND_BYTES
                || largeFrameLength > CIOBufferPool.MAX_WIRE_BYTES)) {
            throw new IllegalArgumentException("large SC wire length " + largeFrameLength);
        }
        this.writer=writer;
        this.senderId=senderId;
        this.largeFrameLength = largeFrameLength;
    }

    protected CAsyncSendConstBuffer(Source pooled, int senderId, int pendingLimit) {
        if (pooled == null || pooled.wire.remaining() <= CIOBufferPool.HEADER_BYTES
                || pooled.wire.remaining() > CIOBufferPool.SMALL_SEND_BYTES || pendingLimit <= 0)
            throw new IllegalArgumentException("pooled constant frame/limit");
        final ByteBuffer view = pooled.wire;
        if (((view.get(view.position()) & 255)
                | ((view.get(view.position() + 1) & 255) << 8))
                != view.remaining()) throw new IllegalArgumentException("pooled frame length");
        writer = null;
        source = pooled;
        this.senderId = senderId;
        largeFrameLength = 0;
    }

    protected boolean exclude(int recipientId) { return false; }
    protected final int senderId() { return senderId; }
    /** Returns true when the socket admits a SEND lease for the shared bytes. */
    public final boolean send(CUserSocket target, int recipientId) {
        return send(target, recipientId, false, null, null, null, 0, null, 0);
    }

    /** Visual sends may refuse admission without disconnecting the receiver. */
    public final boolean sendBestEffort(CUserSocket target, int recipientId) {
        return send(target, recipientId, true, null, null, null, 0, null, 0);
    }

    final boolean sendScoped(CUserSocket target, L1PcInstance recipient, L1Map terrain,
            L1Object anchor, int radius, Integer teamId, boolean optional) {
        return send(target, recipient.getId(), optional, recipient, terrain, anchor, radius, teamId, 0);
    }

    final boolean sendGroundScoped(CUserSocket target, L1PcInstance recipient, L1Map terrain,
            L1Object item, int sightEpoch, boolean optional) {
        return send(target, recipient.getId(), optional, recipient, terrain, item, 0, null, sightEpoch);
    }

    private boolean send(CUserSocket target, int recipientId, boolean optional, L1PcInstance recipient,
            L1Map terrain, L1Object anchor, int radius, Integer teamId, int groundSightEpoch) {
        final Source frozen;
        synchronized (this) {
            if (closed) throw new IllegalStateException("broadcast already closed");
            if (target == null || exclude(recipientId) || !target.isClientConnected()) return false;
            if (source == null) {
                if (freezing) throw new IllegalStateException("broadcast writer reentered send");
                final Source pending;
                freezing = true;
                try {
                    pending = freezeSource(target);
                } finally {
                    freezing = false;
                }
                if (pending == null) return false;
                if (closed) {
                    // A writer can close its own broadcaster before it returns.
                    pending.release();
                    return false;
                }
                source = pending;
            }
            frozen = source.retain();
        }

        // Admission can run socket/session callbacks. Pin the source while
        // copying without holding the broadcast monitor across those callbacks.
        final boolean admitted;
        try {
            // Freezing a lazy source runs its writer. The audience or socket owner
            // may change before it finishes, so recheck just before FIFO admission.
            admitted = (recipient == null || CSharedBroadcast.matchesScope(
                    target, recipient, terrain, anchor, radius, teamId, groundSightEpoch))
                    && (optional ? target.sendFrozenWireBestEffort(frozen.wire) : target.sendFrozenWire(frozen.wire));
        } catch (RuntimeException | Error failure) {
            releaseAfterFailure(frozen, failure);
            throw failure;
        }
        frozen.release();
        return admitted;
    }

    private Source freezeSource(CUserSocket target) {
        if (largeFrameLength != 0) return openWrittenSource(target, this::writeLarge, largeFrameLength);
        try {
            return openWrittenSource(target, writer);
        } catch (BufferOverflowException overflow) {
            return openWrittenSource(target, this::writeLarge, CIOBufferPool.MAX_WIRE_BYTES);
        }
    }

    private void writeLarge(ByteBuffer out) {
        writer.write(out);
        int length = out.position();
        if (largeFrameLength != 0 && length != largeFrameLength) {
            throw new IllegalStateException("large SC frame length mismatch");
        }
        if (length <= CIOBufferPool.SMALL_SEND_BYTES || length > CIOBufferPool.MAX_WIRE_BYTES) {
            throw new IllegalArgumentException("oversized writer length " + length);
        }
    }

    /**
     * Build final L1 bytes IN the borrowed native slab, no byte[] and no
     * second native copy. The first reference is still owned by producer.
     */
    static Source openWrittenSource(CUserSocket target,CSharedBroadcast.NativeWireWriter writer) {
        return openWrittenSource(target, writer, CIOBufferPool.SMALL_SEND_BYTES);
    }

    private static Source openWrittenSource(CUserSocket target, CSharedBroadcast.NativeWireWriter writer, int capacity) {
        if(target==null||writer==null)return null;
        final Source source=target.createPooledWritableConstBuffer(capacity);
        if(source==null)return null;
        try {
            final ByteBuffer out=source.mutableWire;
            if(out==null)throw new IllegalStateException("Native writable source lost");
            out.limit(capacity);
            out.position(2);
            writer.write(out);
            final int length=out.position();
            target.sealPooledWritableConstBuffer(source,length);
            return source;
        } catch(RuntimeException|Error failure) {
            // Releasing a failed Native source must not replace the actual
            // encoder/seal error. The release failure remains observable.
            try { source.release(); }
            catch(RuntimeException|Error releaseFailure) {
                if(releaseFailure!=failure)failure.addSuppressed(releaseFailure);
                // Do not treat a failed native slab release as an ordinary
                // 16KiB overflow and proceed to allocate another source.
                if(failure instanceof BufferOverflowException)
                    throw new IllegalStateException(
                            "Native oversized broadcast source release failed",failure);
            }
            throw failure;
        }
    }

    private static void releaseAfterFailure(Source source, Throwable failure) {
        try { source.release(); }
        catch (RuntimeException | Error releaseFailure) {
            if (releaseFailure != failure) failure.addSuppressed(releaseFailure);
        }
    }

    @Override public final void close() {
        final Source pending;
        synchronized (this) {
            if (closed) return;
            closed = true;
            pending = source;
            source = null;
        }
        if (pending != null) pending.release();
    }

    /** Used only after a failed SEND: distinguish a bad shared encoder from
     * a recipient-local enqueue failure without re-encoding for every client.
     * The ordinary hot path does not acquire this additional monitor.
     */
    public final synchronized boolean hasFrozenSource() {
        return source != null;
    }

    /** Owns the pooled plaintext until the producer and its in-flight copies release it. */
    public static final class Source {
        public final ByteBuffer wire;
        /** Never published before sealing; null after seal. */
        public ByteBuffer mutableWire;
        private final AtomicInteger references = new AtomicInteger(1);
        private final CIOBufferPool javaPool;
        private final CIOBufferEx javaLease;

        public Source(CIOBufferPool pool, CIOBufferEx lease) {
            javaPool = pool;
            javaLease = lease;
            mutableWire = lease.buffer().order(ByteOrder.LITTLE_ENDIAN);
            wire = mutableWire.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN);
        }

        public void sealJava(int length) {
            if (mutableWire == null || length < 3 || length > 0xffff
                    || length > mutableWire.limit()) throw new IllegalArgumentException("Invalid shared frame length");
            // The protocol header is little-endian even if a payload writer changes order.
            mutableWire.put(0, (byte) length).put(1, (byte) (length >>> Byte.SIZE));
            wire.position(0).limit(length);
            mutableWire = null;
        }
        public Source retain() {
            for (;;) {
                int n = references.get();
                if (n <= 0 || n == Integer.MAX_VALUE) throw new IllegalStateException("constant source lifetime");
                if (references.compareAndSet(n, n + 1)) return this;
            }
        }
        public void release() {
            // Never transition through a negative refcount. A double release
            // previously left -1, which retain() could increment and thereby
            // resurrect an already released lease.
            for (;;) {
                final int n = references.get();
                if (n <= 0) throw new IllegalStateException("constant source double release");
                if (references.compareAndSet(n, n - 1)) {
                    if (n == 1) {
                        javaPool.release(javaLease);
                    }
                    return;
                }
            }
        }
    }
}
