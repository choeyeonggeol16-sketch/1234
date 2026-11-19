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
    private final CSharedBroadcast.NativeWireWriter writer;
    private final int senderId, pendingLimit;
    private Source source;
    // 16KiB native pool slabs are fixed. A rare protocol-valid larger
    // immutable frame uses the already-existing per-socket direct CIO FIFO.
    // No new thread/queue or Java wire[] buffer on either path.
    private java.lang.foreign.Arena largeArena;
    private ByteBuffer largeWire;
    private boolean closed;

    /** Lazy Native packet assembly, at the first eligible connected socket.
     * The producer never allocates a legacy Java wire array.
     */
    public CAsyncSendConstBuffer(CSharedBroadcast.NativeWireWriter writer,
                                 int senderId,int pendingLimit) {
        if(writer==null||pendingLimit<=0)
            throw new IllegalArgumentException("Native constant writer/limit");
        this.writer=writer;
        this.senderId=senderId;
        this.pendingLimit=pendingLimit;
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
        writer = null;
        source = pooled;
        this.senderId = senderId;
        this.pendingLimit = pendingLimit;
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
        if(source==null&&writer!=null&&largeWire==null) {
            try {
                source=openWrittenSource(target,writer);
                if(source==null)throw new IllegalStateException("Native source unavailable");
            } catch(java.nio.BufferOverflowException overflow) {
                // The 16KiB source was too small. Build ONE immutable
                // off-heap frame, then copy its bytes into each existing
                // socket FIFO lease. No per-recipient re-encoding or Java array.
                final var arena=java.lang.foreign.Arena.ofShared();
                try {
                    final ByteBuffer frozen=arena.allocate(0xFFFF).asByteBuffer()
                            .order(ByteOrder.LITTLE_ENDIAN);
                    frozen.position(2);
                    writer.write(frozen);
                    final int size=frozen.position();
                    if(size<=16384||size>0xFFFF)
                        throw new IllegalArgumentException("oversized Native writer length " + size);
                    frozen.putShort(0,(short)size);
                    frozen.flip();
                    largeWire=frozen.asReadOnlyBuffer();
                    largeArena=arena;
                } catch(RuntimeException|Error failed) {
                    arena.close();
                    throw failed;
                }
            }
        }
        final int limit=recipientId==senderId?Integer.MAX_VALUE:pendingLimit;
        if(largeWire!=null)
            return target.sendFrozenWire(largeWire);
        final int length = source.wire.remaining();
        if(length<=16384 && target.supportsConstBuffer()
                && target.pendingSendBytes()<limit
                && target.ownsConstBuffer(source)
                && target.enqueueConstBufferConfirmed(source,limit))
            return true;
        // Foreign owner/full queue: copy the immutable pooled wire into the
        // exact same connection-owned CIO FIFO, never to Java byte[].
        return target.sendFrozenWire(source.wire);
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
        try {
            if (source != null) { source.release(); source = null; }
        } finally {
            if(largeArena!=null) {
                largeArena.close();
                largeArena=null;
                largeWire=null;
            }
        }
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
