package l1j.server.server.model.network.core.broadcast;

import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.transport.CIOServer;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicInteger;

/** Original broadcast role: one immutable plaintext, per-socket ordered output.
 * A false apply result means the caller must use its existing serializer.
 * True includes an excluded/closed peer or a peer deliberately skipped by limit.
 */
public class CAsyncSendConstBuffer implements AutoCloseable {
    private final byte[] wire;
    private final int senderId, pendingLimit;
    private Source source;
    private boolean closed;

    public CAsyncSendConstBuffer(byte[] frame, int senderId, int pendingLimit) {
        if (frame == null || frame.length < 3 || frame.length > 16384
                || ((frame[0] & 255) | ((frame[1] & 255) << 8)) != frame.length
                || pendingLimit <= 0) throw new IllegalArgumentException("constant frame/limit");
        wire = frame.clone(); // one defensive copy, never a per-recipient copy
        this.senderId = senderId;
        this.pendingLimit = pendingLimit;
    }

    protected boolean exclude(int recipientId) { return false; }
    protected final int senderId() { return senderId; }

    public final synchronized boolean apply(CUserSocket target, int recipientId) {
        if (closed) throw new IllegalStateException("broadcast already closed");
        if (target == null || exclude(recipientId) || !target.isClientConnected()) return true;
        if (!target.supportsConstBuffer()) return false;
        final int limit = recipientId == senderId ? Integer.MAX_VALUE : pendingLimit;
        if (target.pendingSendBytes() >= limit) return true;
        if (source == null) source = target.createConstBuffer(wire);
        if (source == null || !target.ownsConstBuffer(source)) return false;
        target.enqueueConstBuffer(source, limit);
        return true;
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
        private final AtomicInteger references = new AtomicInteger(1);
        public Source(CIOServer owner, MemorySegment handle, byte[] bytes) {
            this.owner = owner;
            this.handle = handle;
            wire = ByteBuffer.wrap(bytes).asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN);
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
