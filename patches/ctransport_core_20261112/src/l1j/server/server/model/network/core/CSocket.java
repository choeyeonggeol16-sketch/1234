package l1j.server.server.model.network.core;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.lang.foreign.MemorySegment;

/** Native IOCP socket handle; owned by CIOServer. */
final class CSocket implements AutoCloseable {
    private static final int MAX_STASH = 65536;
    private final CIOServer parent;
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
    }
    long id() { return id; }
    CIOServer server() { return parent; }
    public InetSocketAddress remoteAddress() { return remote; }
    public boolean isOpen() { return open && !closing; }
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
    void bindUserSocketOwner(CUserSocket owner) {
        userSocketOwner = owner;
        if (nativeCloseStatusKnown) owner.onNativeCloseStatus(nativeCloseOperation, nativeCloseError);
        if (closing || !open) owner.onTransportClosing();
        if (nativeIoDrained) owner.onTransportTerminated();
    }
    boolean isNativeIoDrained() { return nativeIoDrained; }
    void nativeTerminated(int operation, int error) {
        rememberNativeClose(operation, error);
        nativeTerminated();
    }
    void nativeClosing(int operation, int error) {
        rememberNativeClose(operation, error);
        nativeClosing();
    }
    // Only the real native terminal callback sets this flag. Local aborting
    // of Java READ/WRITE requests must not start game-session destruction.
    void nativeTerminated() {
        nativeIoDrained = true;
        nativeClosed();
        CUserSocket owner = userSocketOwner;
        if (owner != null) owner.onTransportTerminated();
    }
    void nativeClosing() {
        closing = true;
        CUserSocket owner = userSocketOwner;
        if (owner != null) owner.onTransportClosing();
    }
    @SuppressWarnings("preview")
    long socketNumber(){long[] state=userSocketState();return state==null?0:state[10];}
    boolean setAccountIdentity(int account){return parent.setAccountIdentity(id,account);}
    boolean setCharacterEntered(boolean entered){return parent.setCharacterEntered(id,entered);}
    boolean setMacIdentity(long value){return parent.setMacIdentity(id,value);}
    boolean SetKey(MemorySegment api,int seed){return parent.SetKey(id,api,seed);}
    boolean releaseUserPacket(long sequence){return parent.releaseUserPacket(id,sequence);}
    boolean encryptUserData(ByteBuffer bytes){return parent.encryptUserData(id,bytes);}
    boolean setUserState(int state){return parent.setUserState(id,state);}
    boolean pauseNativeUser(boolean pause){return parent.pause(id,pause);}
    boolean configureDeadlines(int loginMs){return parent.configureDeadlines(id,loginMs);}
    boolean requestDeadline(long delayMs){return delayMs>=0&&delayMs<=Integer.MAX_VALUE&&parent.requestDeadline(id,(int)delayMs);}
    boolean nativePacket(long sequence,ByteBuffer bytes,int length){
        CUserSocket owner=userSocketOwner;
        if(owner==null)return false;
        try{return owner.transportNativePacket(sequence,bytes,length);}
        catch(Throwable failure){owner.close();throw failure;}
    }
    int nativeNetworkDeadline(){CUserSocket owner=userSocketOwner;return owner==null?0:owner.transportNetworkDeadline();}
    boolean postCompletion(CIOObject task) { return parent.postCompletion(task); }
    int currentNativeOwner() { return parent.currentNativeOwner(); }
    long[] userSocketState() { return parent.userSocketState(id); }
    void closeGracefully(){
        if(!open)return;closing=true;
        if(!parent.closeGracefully(id,30000)){nativeClosed();parent.disconnect(id);}
    }
    @Override public void close() {
        if (!open) return;
        nativeClosed();
        parent.disconnect(id);
    }

    public void read(ByteBuffer dst, CUserSocket owner) {
        if (dst == null || owner == null) throw new NullPointerException();
        if (dst.isReadOnly()) throw new IllegalArgumentException("READ buffer is read-only");
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
    void SendV(ByteBuffer src,CUserSocket owner,int flags){write(src,owner,flags);}
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
        sendReserved();
    }
    public void write(ByteBuffer[] srcs,int offset,int count,CUserSocket owner){write(srcs,offset,count,owner,0);}
    void SendConstBuffer(ByteBuffer[] srcs,int offset,int count,CUserSocket owner,int flags){write(srcs,offset,count,owner,flags);}
    @SuppressWarnings("preview")
    void SendConstBuffer(ByteBuffer bytes, MemorySegment source, CUserSocket owner, int flags) {
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
        try { success = parent.sendConstBuffer(id, source, flags); }
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
        sendReserved();
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
        writeSubmitting = true;
    }
    private void sendReserved() {
        // Only one WRITE is outstanding. Every native SEND takes ownership of
        // a copy of its bytes; Java source leases stay pinned by the CIO pump.
        boolean success = false;
        try { success = parent.send(id, writeBuffers, writeOffset, writeCount,writeFrameFlags); }
        finally { finishSubmission(success); }
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
        if (reader != null) reader.transportReadCompleted(-1);
        if (writer != null) deliver(writer, gather, -1);
    }
}
