package l1j.server.server.model.network.core.broadcast;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicInteger;

import l1j.server.server.model.network.core.connection.CIOBufferEx;
import l1j.server.server.model.network.core.connection.CIOBufferPool;
import l1j.server.server.model.network.core.connection.CUserSocket;

/** Original broadcast role: one immutable plaintext, per-socket ordered output.
 * The same frozen plaintext is sent to every accepted recipient.
 * No per-packet legacy encoder is called during broadcast.
 */
public class CAsyncSendConstBuffer implements AutoCloseable {
    private final CSharedBroadcast.NativeWireWriter writer;
    private final int senderId;
    private final int largeFrameLength;
    private Source source;
    private boolean closed;

    /** Lazy Native packet assembly, at the first eligible connected socket.
     * The producer never allocates a legacy Java wire array.
     */
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

    /**
     * reference server pool ownership path. Source is constructed from a freshly formatted
     * CIOBufferEx slab, and this object takes exactly the initial producer ref.
     */
    protected CAsyncSendConstBuffer(Source pooled, int senderId, int pendingLimit) {
        if (pooled == null || pooled.wire.remaining() < 3
                || pooled.wire.remaining() > 16384 || pendingLimit <= 0)
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

    


    /**
     * The single shared-broadcast SEND contract.
     * Native <=16 KiB: retain one immutable ConstBuffer source per owner.
     * Other transports/large frames: enqueue those SAME frozen bytes directly.
     * No recipient-specific packet construction, no legacy serializer.
     * true means this socket actually admitted a SEND lease.
     */
    public final synchronized boolean send(CUserSocket target,int recipientId) {
        if(closed)throw new IllegalStateException("broadcast already closed");
        if(target==null || exclude(recipientId) || !target.isClientConnected())return false;
        // Always materialize the one Native slab before any per-socket FIFO
        // decision (including a full queue / owner-mismatch direct route).
        if (source == null) {
            if (largeFrameLength != 0) {
                freezeLargeSource(target, largeFrameLength);
            } else {
                try {
                    source = openWrittenSource(target, writer);
                } catch (java.nio.BufferOverflowException overflow) {
                    freezeLargeSource(target, CIOBufferPool.MAX_WIRE_BYTES);
                }
            }
            if (source == null) return false;
        }
        return target.sendFrozenWire(source.wire);
    }

    private void freezeLargeSource(CUserSocket target, int capacity) {
        source = openWrittenSource(target, this::writeLarge, capacity);
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
        return openWrittenSource(target, writer, 16384);
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
                if(failure instanceof java.nio.BufferOverflowException)
                    throw new IllegalStateException(
                            "Native oversized broadcast source release failed",failure);
            }
            throw failure;
        }
    }

    @Override public final synchronized void close() {
        if (closed) return;
        closed = true;
        if (source != null) { source.release(); source = null; }
    }

    /** Used only after a failed SEND: distinguish a bad shared encoder from
     * a recipient-local enqueue failure without re-encoding for every client.
     * The ordinary hot path does not acquire this additional monitor.
     */
    public final synchronized boolean hasFrozenSource() {
        return source != null;
    }

    /** Java queue references keep the FFM handle/DLL live through its last use.
     * Native requests separately retain the CIOBufferEx block before returning.
     */
    @SuppressWarnings("preview")
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
            mutableWire = lease.buffer();
            wire = mutableWire.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN);
        }

        public void sealJava(int length) {
            if (mutableWire == null || length < 3 || length > 0xffff
                    || length > mutableWire.limit()) throw new IllegalArgumentException("Invalid shared frame length");
            mutableWire.putShort(0, (short) length);
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
            // resurrect an already released Native handle.
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
