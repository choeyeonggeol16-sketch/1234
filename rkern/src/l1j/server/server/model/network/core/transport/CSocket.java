package l1j.server.server.model.network.core.transport;

import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.transport.selector.CSelectorTransport;

import java.util.function.IntConsumer;
import java.util.function.LongConsumer;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

/** Completion adapter consumed by the game socket pumps. */
public final class CSocket implements AutoCloseable {
    private final CIOServer javaServer;
    private final CSelectorTransport.Connection javaPeer;
    private final CSelectorTransport.OwnerCommand javaReadPump;
    private final CSelectorTransport.OwnerCommand javaWriteRequest;
    private final IntConsumer javaReadCallback;
    private final LongConsumer javaWriteCallback;
    private final LongConsumer javaGatherCallback;
    private final Runnable javaDeadlineCallback;
    private volatile long javaLoginDeadline;
    int javaAccount;
    long javaMac;
    boolean javaEntered;
    boolean cleanupIpReserved; // CIOServer IP registry
    private final long id;
    private final InetSocketAddress remote;
    private volatile boolean closing;
    // The adapter retains arguments only until a foreign WRITE reaches the owner.
    private ByteBuffer pendingSingle;
    private ByteBuffer[] pendingGather;
    private int pendingOffset, pendingCount;

    CSocket(CIOServer server, long id, CSelectorTransport.Connection peer) {
        this.id = id;
        remote = peer.remoteAddress();
        javaServer = server;
        javaPeer = peer;
        javaReadCallback = bytes -> userSocketOwner.transportReadCompleted(bytes);
        javaWriteCallback = bytes -> userSocketOwner.transportWriteCompleted(Math.toIntExact(bytes));
        javaGatherCallback = bytes -> userSocketOwner.transportGatherCompleted(bytes);
        javaDeadlineCallback = this::runJavaDeadline;
        javaReadPump = new CSelectorTransport.OwnerCommand() {
            @Override public void run(CSelectorTransport.Connection ignored) { userSocketOwner.run(); }
            @Override public void cancel() { userSocketOwner.cancel(); }
        };
        javaWriteRequest = new CSelectorTransport.OwnerCommand() {
            @Override public void run(CSelectorTransport.Connection connection) {
                ByteBuffer single;
                ByteBuffer[] sources;
                int offset, count;
                synchronized (CSocket.this) {
                    single = pendingSingle;
                    sources = pendingGather;
                    offset = pendingOffset;
                    count = pendingCount;
                    clearWriteRequest();
                }
                if (single != null || sources != null) armJavaWrite(single, sources, offset, count);
            }
            @Override public void cancel() { cancelWriteRequest(); }
        };
    }
    public long id() { return id; }
    public InetSocketAddress remoteAddress() { return remote; }
    public boolean isOpen() { return !closing && javaPeer.isOpen(); }
    private volatile CUserSocket userSocketOwner;
    private volatile boolean ioDrained;
    public void bindUserSocketOwner(CUserSocket owner) {
        userSocketOwner = owner;
        if (!isOpen()) owner.onTransportClosing();
        if (ioDrained) owner.onTransportTerminated();
    }
    public boolean isIoDrained() { return ioDrained; }
    // Only the provider's terminal callback sets this flag. Local aborting
    // of Java READ/WRITE requests must not start game-session destruction.
    void transportTerminated() {
        ioDrained = true;
        try { cancelWriteRequest(); }
        finally {
            CUserSocket owner = userSocketOwner;
            if (owner != null) owner.onTransportTerminated();
        }
    }
    void transportClosing() {
        closing = true;
        CUserSocket owner = userSocketOwner;
        if (owner != null) owner.onTransportClosing();
    }
    public boolean reserveCleanupIp(int limit) { return javaServer.reserveCleanupIp(this, limit); }
    public void releaseCleanupIp() { javaServer.releaseCleanupIp(this); }
    public boolean setAccountIdentity(int account){return javaServer.setAccount(this, account);}
    public boolean setCharacterEntered(boolean entered){return javaServer.setEntered(this, entered);}
    public boolean setMacIdentity(long value){return javaServer.setMac(this, value);}
    public boolean setUserState(int state) {
        if (!javaServer.setState(this, state)) return false;
        if (state != 0) javaLoginDeadline = 0;
        return true;
    }
    public boolean configureDeadlines(int loginMs) {
        if (loginMs < 0) return false;
        if (loginMs != 0) javaLoginDeadline = System.nanoTime() + loginMs * 1_000_000L;
        return requestDeadline(loginMs == 0 ? 5000 : Math.min(loginMs, 5000));
    }
    public boolean requestDeadline(long delayMs) {
        if (delayMs < 0 || delayMs > Integer.MAX_VALUE) return false;
        if (!isOpen()) return false;
        if (javaPeer.isOwnerThread()) return javaPeer.scheduleDeadline(delayMs, javaDeadlineCallback);
        return javaPeer.submit(connection -> connection.scheduleDeadline(delayMs, javaDeadlineCallback), false);
    }

    private void runJavaDeadline() {
        if (!isOpen()) return;
        long login = javaLoginDeadline;
        long remaining = login == 0 ? Long.MAX_VALUE : login - System.nanoTime();
        CUserSocket owner = userSocketOwner;
        int delay = owner == null ? 0 : owner.transportNetworkDeadline();
        if (delay < 0 || remaining <= 0) close();
        else if (delay > 0) javaPeer.scheduleDeadline(Math.min(delay, 1 + (remaining - 1) / 1_000_000), javaDeadlineCallback);
    }
    public boolean scheduleReadPump() { return javaPeer.submit(javaReadPump, true); }
    public int currentOwner() { return javaPeer.isOwnerThread() ? javaPeer.ownerIndex() : -1; }
    long[] selectorReport() {
        byte[] a = remote.getAddress().getAddress();
        long address = a.length == 4 ? (a[0] & 255L) << 24 | (a[1] & 255L) << 16 | (a[2] & 255L) << 8 | a[3] & 255L : 0;
        CUserSocket owner = userSocketOwner;
        return new long[] {id, id, 0, closing ? 1 : 0, ioDrained ? 1 : 0, 0,
                owner != null && owner.isTransportReadPending() ? 1 : 0,
                owner != null && owner.isTransportWritePending() ? 1 : 0,
                0, a.length == 4 ? 2 : 0, address, remote.getPort()};
    }

    void javaTerminated(CSelectorTransport.CloseReason reason) {
        closing = true;
        javaLoginDeadline = 0;
        try { transportClosing(); }
        finally { transportTerminated(); }
    }
    public void closeGracefully() {
        if (!javaPeer.isOpen()) return;
        closing = true;
        javaPeer.requestClose(true);
    }
    @Override public void close() {
        if (!javaPeer.isOpen()) return;
        closing = true;
        javaPeer.requestClose(false);
    }

    public void read(ByteBuffer dst) {
        if (!javaPeer.isOwnerThread()) throw new IllegalStateException("READ requires the transport owner");
        if (!isOpen() || !javaPeer.readRaw(dst, javaReadCallback)) javaReadCallback.accept(-1);
    }

    public void write(ByteBuffer src) {
        long bytes = src.remaining();
        if (!isOpen()) javaWriteCallback.accept(-1);
        else if (bytes == 0) javaWriteCallback.accept(0);
        else submitWrite(src, null, 0, 0);
    }

    public void write(ByteBuffer[] srcs, int offset, int count) {
        if (offset < 0 || count < 1 || offset > srcs.length - count) throw new IndexOutOfBoundsException();
        long bytes = 0;
        for (int i = offset; i < offset + count; i++) {
            bytes += srcs[i].remaining();
            if (bytes > Integer.MAX_VALUE) throw new IllegalArgumentException("WRITE too large");
        }
        if (!isOpen()) javaGatherCallback.accept(-1);
        else if (bytes == 0) javaGatherCallback.accept(0);
        else submitWrite(null, srcs, offset, count);
    }

    private void submitWrite(ByteBuffer single, ByteBuffer[] sources, int offset, int count) {
        if (javaPeer.isOwnerThread()) {
            armJavaWrite(single, sources, offset, count);
            return;
        }
        synchronized (this) {
            if (pendingSingle != null || pendingGather != null)
                throw new IllegalStateException("WRITE already pending");
            pendingSingle = single;
            pendingGather = sources;
            pendingOffset = offset;
            pendingCount = count;
        }
        if (!javaPeer.submit(javaWriteRequest, false)) cancelWriteRequest();
    }

    private void armJavaWrite(ByteBuffer single, ByteBuffer[] sources, int offset, int count) {
        if (single != null) {
            if (!javaPeer.writeRaw(single, javaWriteCallback)) javaWriteCallback.accept(-1);
        } else if (!javaPeer.writeRaw(sources, offset, count, javaGatherCallback)) {
            javaGatherCallback.accept(-1);
        }
    }

    private void cancelWriteRequest() {
        boolean single, gather;
        synchronized (this) {
            single = pendingSingle != null;
            gather = pendingGather != null;
            clearWriteRequest();
        }
        if (single) javaWriteCallback.accept(-1);
        else if (gather) javaGatherCallback.accept(-1);
    }

    private void clearWriteRequest() {
        pendingSingle = null;
        pendingGather = null;
        pendingOffset = pendingCount = 0;
    }
}
