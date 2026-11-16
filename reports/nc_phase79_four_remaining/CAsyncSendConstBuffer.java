package l1j.server.server.model.network.core.broadcast;

import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.transport.CIOServer;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicInteger;

/** Original broadcast role: one immutable plaintext, per-socket ordered output.
 * The same frozen plaintext is sent to every accepted recipient.
 * No per-packet legacy encoder is called during broadcast.
 */
public class CAsyncSendConstBuffer implements AutoCloseable {
    private final byte[] wire;
    private final int senderId, pendingLimit;
    private Source source;
    private boolean closed;

    public CAsyncSendConstBuffer(byte[] frame, int senderId, int pendingLimit) {
        this(frame, senderId, pendingLimit, false);
    }

    /**
     * Package-private owned-frame path. Only broadcast code that has just
     * created the array and NEVER exposes a mutable alias may pass owned=true.
     * Ordinary callers ALWAYS retain the defensive clone contract.
     */
    CAsyncSendConstBuffer(byte[] frame, int senderId, int pendingLimit, boolean owned) {
        if (frame == null || frame.length < 3 || frame.length > 0xFFFF
                || ((frame[0] & 255) | ((frame[1] & 255) << 8)) != frame.length
                || pendingLimit <= 0) throw new IllegalArgumentException("constant frame/limit");
        wire = owned ? frame : frame.clone();
        this.senderId = senderId;
        this.pendingLimit = pendingLimit;
    }

    /**
     * NC pool ownership path. Source is constructed from a freshly formatted
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
        wire = null;
        source = pooled;
        this.senderId = senderId;
        this.pendingLimit = pendingLimit;
    }

    protected boolean exclude(int recipientId) { return false; }
    protected final int senderId() { return senderId; }

    


    /** True only when this recipient's shared SEND lease was staged.
     * Used by broadcasts that must update world state after SEND acceptance.
     */
    


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
        final int limit=recipientId==senderId?Integer.MAX_VALUE:pendingLimit;
        final int length = wire == null ? source.wire.remaining() : wire.length;
        if(length<=16384 && target.supportsConstBuffer()
                && target.pendingSendBytes()<limit) {
            if(source==null) {
                try {
                    source=target.createConstBuffer(wire);
                } catch (RuntimeException failure) {
                    // Resource acquisition failed before any SEND was staged.
                    // The same frozen bytes remain available to the FIFO path.
                }
            }
            if(source!=null && target.ownsConstBuffer(source)
                    && target.enqueueConstBufferConfirmed(source,limit))
                return true;
        }
        // A pool-backed immutable frame is read directly from the native
        // CIOBufferEx slab. For a foreign owner the existing socket FIFO
        // receives a copy into its own pooled output slot, never a byte[].
        return wire == null ? target.sendFrozenWire(source.wire)
                : target.sendFrozenWire(wire);
    }

    /**
     * Build final L1 bytes IN the borrowed native slab, no byte[] and no
     * second native copy. The first reference is still owned by producer.
     */
    static Source openWrittenSource(CUserSocket target,CSharedBroadcast.NativeWireWriter writer) {
        if(target==null||writer==null)return null;
        final Source source=target.createPooledWritableConstBuffer();
        if(source==null)return null;
        try {
            final ByteBuffer out=source.mutableWire;
            if(out==null)throw new IllegalStateException("Native writable source lost");
            out.position(2);
            writer.write(out);
            final int length=out.position();
            target.sealPooledWritableConstBuffer(source,length);
            return source;
        } catch(RuntimeException|Error failure) {
            source.release();
            throw failure;
        }
    }

    @Override public final synchronized void close() {
        if (closed) return;
        closed = true;
        if (source != null) { source.release(); source = null; }
    }

    /** Java queue references keep the FFM handle/DLL live through its last use.
     * Native requests separately retain the CIOBufferEx block before returning.
     */
    @SuppressWarnings("preview")
    public static final class Source {
        public final CIOServer owner;
        public final MemorySegment handle;
        public final ByteBuffer wire;
        /** Never published before sealing; null after seal. */
        public ByteBuffer mutableWire;
        private final AtomicInteger references = new AtomicInteger(1);
        public Source(CIOServer owner, MemorySegment handle, byte[] bytes) {
            this.owner = owner;
            this.handle = handle;
            wire = ByteBuffer.wrap(bytes).asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN);
        }
        /** Native pooled memory, not a Java copy. Held until last lease release. */
        public Source(CIOServer owner, MemorySegment handle, ByteBuffer nativeBytes) {
            this(owner,handle,nativeBytes,false);
        }
        public Source(CIOServer owner, MemorySegment handle, ByteBuffer nativeBytes,
                      boolean writableSource) {
            this.owner = owner;
            this.handle = handle;
            wire = nativeBytes.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN);
            mutableWire = writableSource ? nativeBytes : null;
        }
        public Source retain() {
            for (;;) {
                int n = references.get();
                if (n == 0 || n == Integer.MAX_VALUE) throw new IllegalStateException("constant source lifetime");
                if (references.compareAndSet(n, n + 1)) return this;
            }
        }
        public void release() {
            int n = references.decrementAndGet();
            if (n < 0) throw new IllegalStateException("constant source double release");
            if (n == 0) owner.releaseConstBuffer(handle);
        }
    }
}
