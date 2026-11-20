package l1j.server.server.model.network.core.transport;

import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.execution.CIOObject;
import l1j.server.server.model.network.core.transport.selector.CSelectorTransport;

import java.util.function.IntConsumer;
import java.util.function.LongConsumer;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

/** Completion adapter consumed by the game socket pumps. */
public final class CSocket implements AutoCloseable {
    private final CIOServer javaServer;
    private final CSelectorTransport.Connection javaPeer;
    private final CSelectorTransport.OwnerCommand javaReadRequest;
    private final CSelectorTransport.OwnerCommand javaWriteRequest;
    private final IntConsumer javaReadCallback;
    private final LongConsumer javaWriteCallback;
    private final Runnable javaDeadlineCallback;
    private volatile long javaLoginDeadline;
    int javaAccount;
    long javaMac;
    boolean javaEntered;
    private final long id;
    private final InetSocketAddress remote;
    private volatile boolean open = true, closing;
    private ByteBuffer readBuffer;
    private CUserSocket readOwner;
    private final ByteBuffer[] singleWrite = new ByteBuffer[1];
    private ByteBuffer[] writeBuffers;
    private int writeOffset, writeCount;
    private CUserSocket writeOwner;
    private boolean gathering;

    CSocket(CIOServer server, long id, CSelectorTransport.Connection peer) {
        this.id = id;
        remote = peer.remoteAddress();
        javaServer = server;
        javaPeer = peer;
        javaReadCallback = this::completeJavaRead;
        javaWriteCallback = this::completeJavaWrite;
        javaDeadlineCallback = this::runJavaDeadline;
        javaReadRequest = new CSelectorTransport.OwnerCommand() {
            @Override public void run(CSelectorTransport.Connection connection) {
                ByteBuffer target;
                synchronized (CSocket.this) { target = readBuffer; }
                if (target != null && !connection.readRaw(target, javaReadCallback)) completeJavaRead(-1);
            }
            @Override public void cancel() { completeJavaRead(-1); }
        };
        javaWriteRequest = new CSelectorTransport.OwnerCommand() {
            @Override public void run(CSelectorTransport.Connection connection) {
                ByteBuffer[] sources;
                int offset, count;
                boolean gather;
                synchronized (CSocket.this) {
                    sources = writeBuffers;
                    offset = writeOffset;
                    count = writeCount;
                    gather = gathering;
                }
                if (sources == null) return;
                armJavaWrite(gather ? null : sources[0], sources, offset, count);
            }
            @Override public void cancel() { completeJavaWrite(-1); }
        };
    }
    public boolean isJavaTransport() { return true; }
    public long id() { return id; }
    public InetSocketAddress remoteAddress() { return remote; }
    public boolean isOpen() { return open && !closing && javaPeer.isOpen(); }
    private volatile CUserSocket userSocketOwner;
    private volatile boolean ioDrained;
    public void bindUserSocketOwner(CUserSocket owner) {
        userSocketOwner = owner;
        if (closing || !open) owner.onTransportClosing();
        if (ioDrained) owner.onTransportTerminated();
    }
    public boolean isIoDrained() { return ioDrained; }
    // Only the provider's terminal callback sets this flag. Local aborting
    // of Java READ/WRITE requests must not start game-session destruction.
    void transportTerminated() {
        ioDrained = true;
        try { transportClosed(); }
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
    @SuppressWarnings("preview")
    long socketNumber(){long[] state=userSocketState();return state==null?0:state[10];}
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
    public boolean postCompletion(CIOObject task) {
        return javaPeer.submit(new CSelectorTransport.OwnerCommand() {
            @Override public void run(CSelectorTransport.Connection ignored) { task.OnIOCallback(); }
            @Override public void cancel() { task.cancel(); }
        }, true);
    }
    public int currentOwner() { return javaPeer.isOwnerThread() ? javaPeer.ownerIndex() : -1; }
    public long[] userSocketState() { return null; }
    synchronized long[] selectorReport() {
        byte[] a = remote.getAddress().getAddress();
        long address = a.length == 4 ? (a[0] & 255L) << 24 | (a[1] & 255L) << 16 | (a[2] & 255L) << 8 | a[3] & 255L : 0;
        return new long[] {id, id, 0, closing ? 1 : 0, ioDrained ? 1 : 0, 0,
                readOwner == null ? 0 : 1, writeOwner == null ? 0 : 1, 0, a.length == 4 ? 2 : 0, address, remote.getPort()};
    }

    void javaTerminated(CSelectorTransport.CloseReason reason) {
        closing = true;
        javaLoginDeadline = 0;
        try { transportClosing(); }
        finally { transportTerminated(); }
    }
    public void closeGracefully() {
        if (!open) return;
        closing = true;
        javaPeer.requestClose(true);
    }
    @Override public void close() {
        if (!open) return;
        closing = true;
        javaPeer.requestClose(false);
    }

    public void read(ByteBuffer dst, CUserSocket owner) {
        if (dst == null || owner == null) throw new NullPointerException();
        if (dst.isReadOnly()) throw new IllegalArgumentException("READ buffer is read-only");
        boolean admitted;
        synchronized (this) {
            if (readOwner != null) throw new IllegalStateException("READ already pending");
            admitted = isOpen() && dst.hasRemaining();
            if (admitted) {
                readOwner = owner;
                readBuffer = dst;
            }
        }
        if (!admitted) owner.transportReadCompleted(-1);
        else if (javaPeer.isOwnerThread()) {
            // The READ pump owns the destination; register inline.
            try {
                if (!javaPeer.readRaw(dst, javaReadCallback)) completeJavaRead(-1);
            } catch (RuntimeException failure) {
                try { javaPeer.abort(); }
                finally { completeJavaRead(-1); }
                throw failure;
            }
        } else if (!javaPeer.submit(javaReadRequest, false)) completeJavaRead(-1);
    }

    private void completeJavaRead(int bytes) {
        CUserSocket callback;
        synchronized (this) {
            callback = readOwner;
            readOwner = null;
            readBuffer = null;
        }
        if (callback != null) callback.transportReadCompleted(bytes);
    }

    private void completeJavaWrite(long bytes) {
        CUserSocket callback;
        boolean gather;
        synchronized (this) {
            callback = writeOwner;
            gather = gathering;
            clearWrite();
        }
        if (callback != null) deliver(callback, gather, Math.toIntExact(bytes));
    }

    public void write(ByteBuffer src, CUserSocket owner) {
        if (src == null || owner == null) throw new NullPointerException();
        int bytes = src.remaining();
        boolean rejected;
        synchronized (this) {
            if (writeOwner != null) throw new IllegalStateException("WRITE already pending");
            rejected = !isOpen();
            if (!rejected && bytes != 0) {
                singleWrite[0] = src;
                reserveWrite(singleWrite, 0, 1, owner, false);
            }
        }
        if (rejected) { owner.transportWriteCompleted(-1); return; }
        if (bytes == 0) { owner.transportWriteCompleted(0); return; }
        sendReserved(src, null, 0, 0);
    }
    public void write(ByteBuffer[] srcs, int offset, int count, CUserSocket owner) {
        if (srcs == null || owner == null) throw new NullPointerException();
        if (offset < 0 || count < 1 || offset > srcs.length - count)
            throw new IndexOutOfBoundsException();
        long requested = 0;
        for (int i = offset; i < offset + count; i++) {
            if (srcs[i] == null) throw new NullPointerException("WRITE segment");
            requested += srcs[i].remaining();
            if (requested > Integer.MAX_VALUE) throw new IllegalArgumentException("WRITE too large");
        }
        int bytes = (int) requested;
        boolean rejected;
        synchronized (this) {
            if (writeOwner != null) throw new IllegalStateException("WRITE already pending");
            rejected = !isOpen();
            if (!rejected && bytes != 0) reserveWrite(srcs, offset, count, owner, true);
        }
        if (rejected) { owner.transportGatherCompleted(-1); return; }
        if (bytes == 0) { owner.transportGatherCompleted(0); return; }
        sendReserved(null, srcs, offset, count);
    }
    private void reserveWrite(ByteBuffer[] buffers, int offset, int count,
                              CUserSocket owner, boolean gather) {
        writeBuffers = buffers;
        writeOffset = offset;
        writeCount = count;
        writeOwner = owner;
        gathering = gather;
    }
    private void sendReserved(ByteBuffer single, ByteBuffer[] sources, int offset, int count) {
        if (javaPeer.isOwnerThread()) {
            // A queued close can only run on this owner.
            try { armJavaWrite(single, sources, offset, count); }
            catch (RuntimeException failure) { javaPeer.abort(); }
        } else if (!javaPeer.submit(javaWriteRequest, false)) completeJavaWrite(-1);
    }

    private void armJavaWrite(ByteBuffer single, ByteBuffer[] sources, int offset, int count) {
        boolean accepted = single != null ? javaPeer.writeRaw(single, javaWriteCallback)
                : javaPeer.writeRaw(sources, offset, count, javaWriteCallback);
        if (!accepted) completeJavaWrite(-1);
    }
    private void deliver(CUserSocket owner, boolean gather, int count) {
        if (gather) owner.transportGatherCompleted(count);
        else owner.transportWriteCompleted(count);
    }
    private void clearWrite() {
        writeBuffers = null;
        singleWrite[0] = null;
        writeOwner = null;
        gathering = false;
        writeCount = writeOffset = 0;
    }
    void transportClosed() {
        CUserSocket reader;
        CUserSocket writer;
        boolean gather;
        synchronized (this) {
            if (!open) return;
            open = false;
            reader = readOwner;
            readOwner = null;
            readBuffer = null;
            writer = writeOwner;
            gather = gathering;
            clearWrite();
        }
        try { if (reader != null) reader.transportReadCompleted(-1); }
        finally { if (writer != null) deliver(writer, gather, -1); }
    }
}
