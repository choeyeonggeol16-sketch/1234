package l1j.server.server.model.network.core.transport;

import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.execution.CIOObject;
import l1j.server.server.model.network.core.transport.selector.SelectorTransport;

import java.util.function.IntConsumer;
import java.util.function.LongConsumer;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.lang.foreign.MemorySegment;

/** Completion adapter consumed by the game socket pumps. */
public final class CSocket implements AutoCloseable {
    private static final int MAX_STASH = 65536;
    private final CIOServer parent;
    private final JavaCioServer javaServer;
    private final SelectorTransport.Connection javaPeer;
    private final SelectorTransport.OwnerCommand javaReadRequest;
    private final SelectorTransport.OwnerCommand javaWriteRequest;
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
    private int writeOffset, writeCount, writeExpected, writeAcknowledged,writeFrameFlags;
    private boolean writeSubmitting;
    private CUserSocket writeOwner;
    private boolean gathering;
    private byte[] stash;
    private int stashStart, stashEnd;

    CSocket(CIOServer parent, long id, InetSocketAddress remote, InetSocketAddress local) {
        this.parent = parent;
        this.id = id;
        this.remote = remote;
        javaServer = null;
        javaPeer = null;
        javaReadRequest = null;
        javaWriteRequest = null;
        javaReadCallback = null;
        javaWriteCallback = null;
        javaDeadlineCallback = null;
    }
    CSocket(JavaCioServer server, long id, SelectorTransport.Connection peer) {
        this.parent = null;
        this.id = id;
        remote = peer.remoteAddress();
        javaServer = server;
        javaPeer = peer;
        javaReadCallback = this::completeJavaRead;
        javaWriteCallback = this::completeJavaWrite;
        javaDeadlineCallback = this::runJavaDeadline;
        javaReadRequest = new SelectorTransport.OwnerCommand() {
            @Override public void run(SelectorTransport.Connection connection) {
                ByteBuffer target;
                synchronized (CSocket.this) { target = readBuffer; }
                if (target != null && !connection.readRaw(target, javaReadCallback)) completeJavaRead(-1);
            }
            @Override public void cancel() { completeJavaRead(-1); }
        };
        javaWriteRequest = new SelectorTransport.OwnerCommand() {
            @Override public void run(SelectorTransport.Connection connection) {
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
    public boolean isJavaTransport() { return javaPeer != null; }
    public long id() { return id; }
    public CIOServer server() { return parent; }
    public InetSocketAddress remoteAddress() { return remote; }
    public boolean isOpen() { return open && !closing && (javaPeer == null || javaPeer.isOpen()); }
    private volatile CUserSocket userSocketOwner;
    private volatile boolean nativeIoDrained;
    private volatile boolean nativeCloseStatusKnown;
    private int nativeCloseOperation, nativeCloseError;
    private void rememberNativeClose(int operation, int error) {
        synchronized (this) {
            if (!nativeCloseStatusKnown) {
                nativeCloseOperation = operation; nativeCloseError = error;
                nativeCloseStatusKnown = true;
            }
        }
        // No game/lifecycle callback while holding the provider I/O lock.
        CUserSocket owner = userSocketOwner;
        if (owner != null) owner.onNativeCloseStatus(nativeCloseOperation, nativeCloseError);
    }
    public void bindUserSocketOwner(CUserSocket owner) {
        userSocketOwner = owner;
        if (nativeCloseStatusKnown) owner.onNativeCloseStatus(nativeCloseOperation, nativeCloseError);
        if (closing || !open) owner.onTransportClosing();
        if (nativeIoDrained) owner.onTransportTerminated();
    }
    public boolean isNativeIoDrained() { return nativeIoDrained; }
    void nativeTerminated(int operation, int error) {
        rememberNativeClose(operation, error);
        nativeTerminated();
    }
    void nativeClosing(int operation, int error) {
        rememberNativeClose(operation, error);
        nativeClosing();
    }
    // Only the provider's terminal callback sets this flag. Local aborting
    // of Java READ/WRITE requests must not start game-session destruction.
    void nativeTerminated() {
        nativeIoDrained = true;
        try { nativeClosed(); }
        finally {
            CUserSocket owner = userSocketOwner;
            if (owner != null) owner.onTransportTerminated();
        }
    }
    void nativeClosing() {
        closing = true;
        CUserSocket owner = userSocketOwner;
        if (owner != null) owner.onTransportClosing();
    }
    @SuppressWarnings("preview")
    long socketNumber(){long[] state=userSocketState();return state==null?0:state[10];}
    public boolean setAccountIdentity(int account){return javaServer != null ? javaServer.setAccount(this, account) : parent.setAccountIdentity(id,account);}
    public boolean setCharacterEntered(boolean entered){return javaServer != null ? javaServer.setEntered(this, entered) : parent.setCharacterEntered(id,entered);}
    public boolean setMacIdentity(long value){return javaServer != null ? javaServer.setMac(this, value) : parent.setMacIdentity(id,value);}
    private CIOServer requireNativeServer() {
        if (parent == null) throw new IllegalStateException("Native operation on a Java Selector socket");
        return parent;
    }
    public boolean setUserState(int state) {
        if (javaServer == null) return parent.setUserState(id, state);
        if (!javaServer.setState(this, state)) return false;
        if (state != 0) javaLoginDeadline = 0;
        return true;
    }
    public boolean configureDeadlines(int loginMs) {
        if (javaPeer == null) return parent.configureDeadlines(id, loginMs);
        if (loginMs < 0) return false;
        if (loginMs != 0) javaLoginDeadline = System.nanoTime() + loginMs * 1_000_000L;
        return requestDeadline(loginMs == 0 ? 5000 : Math.min(loginMs, 5000));
    }
    public boolean requestDeadline(long delayMs) {
        if (delayMs < 0 || delayMs > Integer.MAX_VALUE) return false;
        if (javaPeer == null) return parent.requestDeadline(id, (int) delayMs);
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
    int nativeNetworkDeadline(){CUserSocket owner=userSocketOwner;return owner==null?0:owner.transportNetworkDeadline();}
    public boolean postCompletion(CIOObject task) {
        if (javaPeer == null) return parent.postCompletion(task);
        return javaPeer.submit(new SelectorTransport.OwnerCommand() {
            @Override public void run(SelectorTransport.Connection ignored) { task.OnIOCallback(); }
            @Override public void cancel() { task.cancel(); }
        }, true);
    }
    public int currentNativeOwner() { return javaPeer == null ? parent.currentNativeOwner() : javaPeer.isOwnerThread() ? javaPeer.ownerIndex() : -1; }
    public long[] userSocketState() { return javaPeer == null ? parent.userSocketState(id) : null; }
    synchronized long[] selectorReport() {
        byte[] a = remote.getAddress().getAddress();
        long address = a.length == 4 ? (a[0] & 255L) << 24 | (a[1] & 255L) << 16 | (a[2] & 255L) << 8 | a[3] & 255L : 0;
        return new long[] {id, id, 0, closing ? 1 : 0, nativeIoDrained ? 1 : 0, 0,
                readOwner == null ? 0 : 1, writeOwner == null ? 0 : 1, 0, a.length == 4 ? 2 : 0, address, remote.getPort()};
    }

    void javaTerminated(SelectorTransport.CloseReason reason) {
        closing = true;
        javaLoginDeadline = 0;
        try { nativeClosing(); }
        finally { nativeTerminated(); }
    }
    public void closeGracefully(){
        if(!open)return;closing=true;
        if (javaPeer != null) {
            javaPeer.requestClose(true);
            return;
        }
        if(!parent.closeGracefully(id,30000)){nativeClosed();parent.disconnect(id);}
    }
    @Override public void close() {
        if (!open) return;
        if (javaPeer != null) {
            closing = true;
            javaPeer.requestClose(false);
            return;
        }
        nativeClosed();
        parent.disconnect(id);
    }

    public void read(ByteBuffer dst, CUserSocket owner) {
        if (dst == null || owner == null) throw new NullPointerException();
        if (dst.isReadOnly()) throw new IllegalArgumentException("READ buffer is read-only");
        if (javaPeer != null) {
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
                // The READ pump already owns the destination. No deferred command
                // needs to reacquire this monitor merely to recover the same buffer.
                try {
                    if (!javaPeer.readRaw(dst, javaReadCallback)) completeJavaRead(-1);
                } catch (RuntimeException failure) {
                    try { javaPeer.abort(); }
                    finally { completeJavaRead(-1); }
                    throw failure;
                }
            } else if (!javaPeer.submit(javaReadRequest, false)) completeJavaRead(-1);
            return;
        }
        int bytes = 0;
        boolean rejected;
        boolean armed = false;
        synchronized (this) {
            if (readOwner != null) throw new IllegalStateException("READ already pending");
            rejected = !isOpen();
            if (!rejected && dst.hasRemaining()) {
                bytes = drainStash(dst);
                if (bytes == 0) {
                    readBuffer = dst;
                    readOwner = owner;
                    try { armed = parent.pause(id, false); }
                    catch (RuntimeException failure) { armed = false; }
                    if (!armed) {
                        readBuffer = null;
                        readOwner = null;
                        rejected = true;
                    }
                }
            }
        }
        if (rejected) owner.transportReadCompleted(-1);
        else if (!armed) owner.transportReadCompleted(bytes);
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

    /** Never retain the native buffer; it is valid only during the upcall. */
    @SuppressWarnings("preview")
	void nativeReceive(MemorySegment ptr, int length) {
        if (length <= 0) { nativeClosed(); return; }
        final MemorySegment src = ptr.reinterpret(length);
        int at = 0;
        while (at < length) {
            CUserSocket callback = null;
            int n = 0;
            synchronized (this) {
                if (!open) return;
                if (readOwner == null) {
                    if (stash == null) stash = new byte[4096];
                    int pending = stashEnd - stashStart;
                    int required = pending + length - at;
                    if (required > MAX_STASH) throw new IllegalStateException("CIO receive backlog overflow");
                    if (required > stash.length) {
                        byte[] expanded = new byte[Math.min(MAX_STASH, Math.max(required, stash.length << 1))];
                        System.arraycopy(stash, stashStart, expanded, 0, pending);
                        stash = expanded;
                    } else if (stashStart > 0) {
                        System.arraycopy(stash, stashStart, stash, 0, pending);
                    }
                    stashStart = 0;
                    stashEnd = pending;
                    MemorySegment.ofArray(stash).asSlice(stashEnd, length - at)
                            .copyFrom(src.asSlice(at, length - at));
                    stashEnd += length - at;
                    if (!parent.pause(id, true))
                        throw new IllegalStateException("CIO receive pause failed");
                    at = length;
                } else {
                    ByteBuffer dst = readBuffer;
                    n = Math.min(dst.remaining(), length - at);
                    MemorySegment.ofBuffer(dst).asSlice(0, n).copyFrom(src.asSlice(at, n));
                    dst.position(dst.position() + n);
                    at += n;
                    callback = readOwner;
                    readOwner = null;
                    readBuffer = null;
                }
            }
            if (callback != null) callback.transportReadCompleted(n);
        }
        // A complete native user frame can end exactly at the consumer's
        // buffer boundary. Pause now if its packet lease kept READ unarmed;
        // otherwise the assembler could read a whole next frame ahead before
        // the old byte-stream "stash on next upcall" pause takes effect.
        synchronized (this) {
            if (isOpen() && readOwner == null && !parent.pause(id, true))
                throw new IllegalStateException("CIO frame-boundary receive pause failed");
        }
    }
    private int drainStash(ByteBuffer dst) {
        int pending = stashEnd - stashStart;
        if (pending <= 0) return 0;
        int n = Math.min(pending, dst.remaining());
        dst.put(stash, stashStart, n);
        stashStart += n;
        if (stashStart == stashEnd) stashStart = stashEnd = 0;
        return n;
    }

    public void write(ByteBuffer src,CUserSocket owner){write(src,owner,0);}
    public void SendV(ByteBuffer src,CUserSocket owner,int flags){write(src,owner,flags);}
    private void write(ByteBuffer src, CUserSocket owner,int flags) {
        if (src == null || owner == null) throw new NullPointerException();
        int bytes = src.remaining();
        boolean rejected;
        synchronized (this) {
            if (writeOwner != null || writeSubmitting) throw new IllegalStateException("WRITE already pending");
            rejected = !isOpen();
            if (!rejected && bytes != 0) {
                singleWrite[0] = src;
                reserveWrite(singleWrite, 0, 1, bytes, owner, false,flags);
            }
        }
        if (rejected) { owner.transportWriteCompleted(-1); return; }
        if (bytes == 0) { owner.transportWriteCompleted(0); return; }
        sendReserved(src, null, 0, 0);
    }
    public void write(ByteBuffer[] srcs,int offset,int count,CUserSocket owner){write(srcs,offset,count,owner,0);}
    public void SendConstBuffer(ByteBuffer[] srcs,int offset,int count,CUserSocket owner,int flags){write(srcs,offset,count,owner,flags);}
    @SuppressWarnings("preview")
    public void SendConstBuffer(ByteBuffer bytes, MemorySegment source, CUserSocket owner, int flags) {
        CIOServer nativeServer = requireNativeServer();
        boolean rejected;
        synchronized (this) {
            if (writeOwner != null || writeSubmitting) throw new IllegalStateException("WRITE already pending");
            rejected = !isOpen();
            if (!rejected) {
                singleWrite[0] = bytes;
                reserveWrite(singleWrite, 0, 1, bytes.remaining(), owner, false, flags);
            }
        }
        if (rejected) { owner.transportWriteCompleted(-1); return; }
        // The source reference transferred from the Java FIFO is pinned until this
        // downcall exits, including a simultaneous close/inline completion.
        boolean success = false;
        try { success = nativeServer.sendConstBuffer(id, source, flags); }
        finally { finishSubmission(success); }
    }
    private void write(ByteBuffer[] srcs, int offset, int count, CUserSocket owner,int flags) {
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
            if (writeOwner != null || writeSubmitting) throw new IllegalStateException("WRITE already pending");
            rejected = !isOpen();
            if (!rejected && bytes != 0) reserveWrite(srcs, offset, count, bytes, owner, true,flags);
        }
        if (rejected) { owner.transportGatherCompleted(-1); return; }
        if (bytes == 0) { owner.transportGatherCompleted(0); return; }
        sendReserved(null, srcs, offset, count);
    }
    private void reserveWrite(ByteBuffer[] buffers, int offset, int count,
                              int expected, CUserSocket owner, boolean gather,int flags) {
        writeFrameFlags=flags;
        writeBuffers = buffers;
        writeOffset = offset;
        writeCount = count;
        writeExpected = expected;
        writeAcknowledged = 0;
        writeOwner = owner;
        gathering = gather;
        // Prevent inline native SEND completion from exposing descriptor reuse
        // until the downcall has consumed its input buffers.
        writeSubmitting = javaPeer == null;
    }
    private void sendReserved(ByteBuffer single, ByteBuffer[] sources, int offset, int count) {
        if (javaPeer != null) {
            if (javaPeer.isOwnerThread()) {
                // A queued close can only run on this owner. The already
                // published descriptor stays pinned throughout inline registration.
                try { armJavaWrite(single, sources, offset, count); }
                catch (RuntimeException failure) { javaPeer.abort(); }
            } else if (!javaPeer.submit(javaWriteRequest, false)) completeJavaWrite(-1);
            return;
        }
        // Only one WRITE is outstanding. Every native SEND takes ownership of
        // a copy of its bytes; Java source leases stay pinned by the CIO pump.
        boolean success = false;
        try { success = parent.send(id, writeBuffers, writeOffset, writeCount,writeFrameFlags); }
        finally { finishSubmission(success); }
    }

    private void armJavaWrite(ByteBuffer single, ByteBuffer[] sources, int offset, int count) {
        boolean accepted = single != null ? javaPeer.writeRaw(single, javaWriteCallback)
                : javaPeer.writeRaw(sources, offset, count, javaWriteCallback);
        if (!accepted) completeJavaWrite(-1);
    }
    private void finishSubmission(boolean success) {
        CUserSocket callback = null;
        boolean gather = false;
        int bytes = 0;
        synchronized (this) {
            writeSubmitting = false;
            if (!open) {
                callback=writeOwner;gather=gathering;bytes=-1;
                clearWrite();
            } else if (success && writeAcknowledged == writeExpected && writeExpected > 0) {
                callback = writeOwner;
                gather = gathering;
                bytes = completeWriteState();
            }
        }
        if (!success) {
            nativeClosed();
            parent.disconnect(id);
        }
        if (callback != null) deliver(callback, gather, bytes);
    }
    void nativeSent(int length) {
        CUserSocket callback = null;
        boolean gather = false;
        int bytes = 0;
        synchronized (this) {
            if (!open || writeOwner == null) return;
            if (length <= 0 || length > writeExpected - writeAcknowledged)
                throw new IllegalStateException("invalid native SEND completion");
            writeAcknowledged += length;
            if (writeAcknowledged < writeExpected || writeSubmitting) return;
            callback = writeOwner;
            gather = gathering;
            bytes = completeWriteState();
        }
        deliver(callback, gather, bytes);
    }
    private void deliver(CUserSocket owner, boolean gather, int count) {
        if (gather) owner.transportGatherCompleted(count);
        else owner.transportWriteCompleted(count);
    }
    private int completeWriteState() {
        int bytes = writeExpected;
        for (int i = writeOffset; i < writeOffset + writeCount; i++) {
            ByteBuffer b = writeBuffers[i];
            b.position(b.limit());
        }
        clearWrite();
        return bytes;
    }
    private void clearWrite() {
        writeBuffers = null;
        singleWrite[0] = null;
        writeOwner = null;
        gathering = false;
        writeFrameFlags=0;
        writeCount = writeOffset = writeExpected = writeAcknowledged = 0;
    }
    void nativeClosed() {
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
            if (writeSubmitting) {
                // Keep descriptors live until the in-progress FFM downcall exits.
                // Defer the writer failure too: releasing its Java lease here
                // would let another producer overwrite bytes still being copied
                // by the in-progress native downcall.
                writer = null;
            } else {
                clearWrite();
            }
        }
        try { if (reader != null) reader.transportReadCompleted(-1); }
        finally { if (writer != null) deliver(writer, gather, -1); }
    }
}
