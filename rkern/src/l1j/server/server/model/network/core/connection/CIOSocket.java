package l1j.server.server.model.network.core.connection;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import l1j.server.server.model.network.core.transport.selector.CSelectorTransport;
import l1j.server.server.model.network.core.transport.selector.CSelectorTransport.Connection;
import l1j.server.server.model.network.core.transport.selector.CSelectorTransport.OwnerCommand;

/** Transport identity, owner handoff, READ pump and raw buffer lifetime.
 * The subclass retains packet framing, SEND serialization and session policy.
 * All state is inherited by the existing socket object; no delegate is allocated.
 */
public abstract class CIOSocket implements AutoCloseable, CSelectorTransport.RawEndpoint {
    private static final Logger LOG = Logger.getLogger(CUserSocket.class.getName());
    final Connection peer;
    final long id;
    final CIOBufferPool _pool;
    final ReentrantLock _stateLock = new ReentrantLock();
    final String _remoteAddress;
    final int _affinityShard;
    volatile int _closing;
    volatile int _closed;
    volatile int _readKick;
    volatile int _readEvent;
    boolean _readSocketOwnerActive;
    volatile boolean _sendReadPaused;
    volatile CIOBufferEx _writing;
    ByteBuffer[] _gatherBuffers;
    int _gatherCount;
    int _gatherOffset;
    int _zeroReadCompletions;
    volatile int _readActive;
    CIOBufferEx _reading;
    volatile boolean _readReleased;
    volatile boolean _readPending;
    boolean _readYielded;
    boolean javaReadScheduled; // guarded by _stateLock

    private static final OwnerCommand READ_PUMP = new OwnerCommand() {
        @Override public void run(Connection peer) { ((CIOSocket) peer.rawEndpoint()).runReadPump(); }
        @Override public void cancel(Connection peer) { ((CIOSocket) peer.rawEndpoint()).cancelReadPump(); }
    };
    private static final OwnerCommand ARM_WRITE = new OwnerCommand() {
        @Override public void run(Connection peer) { ((CIOSocket) peer.rawEndpoint()).armQueuedWrite(); }
        @Override public void cancel(Connection peer) { ((CIOSocket) peer.rawEndpoint()).cancelQueuedWrite(); }
    };
    static final int SINGLE_WRITE = 1;
    static final int GATHER_WRITE = 2;
    // The serializer keeps the active SEND lease alive while this handoff is pending.
    private volatile int queuedTransportWrite;


    static final VarHandle CLOSED, TRANSPORT_WRITE, READ_KICK, READ_EVENT, READ_ACTIVE, CLOSING;
    static {
        try {
            final MethodHandles.Lookup lookup = MethodHandles.lookup();
            CLOSED = lookup.findVarHandle(CIOSocket.class, "_closed", int.class);
            TRANSPORT_WRITE = lookup.findVarHandle(CIOSocket.class, "queuedTransportWrite", int.class);
            READ_KICK = lookup.findVarHandle(CIOSocket.class, "_readKick", int.class);
            READ_EVENT = lookup.findVarHandle(CIOSocket.class, "_readEvent", int.class);
            READ_ACTIVE = lookup.findVarHandle(CIOSocket.class, "_readActive", int.class);
            CLOSING = lookup.findVarHandle(CIOSocket.class, "_closing", int.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    CIOSocket(Connection peer, long id, CIOBufferPool pool) {
        this.peer = peer;
        this.id = id;
        _pool = pool;
        _remoteAddress = peer.remoteAddress().getAddress().getHostAddress();
        _affinityShard = peer.ownerIndex();
    }

    @Override public abstract void close();
    abstract boolean tryOwnReadWritePass();
    abstract boolean onReadSingleCompleteFrame();
    abstract boolean OnRead();
    abstract void releaseFusedReadSendOwner();
    abstract void kickWrite();
    abstract void notifyClosedWhenReleased();

    long transportId() { return id; }
    InetSocketAddress transportAddress() { return peer.remoteAddress(); }


    final boolean requestTransportDeadline(long delayMs) {
        if (!isOpen() || delayMs < 0 || delayMs > Integer.MAX_VALUE) return false;
        if (peer.isOwnerThread()) return peer.scheduleRawDeadline(delayMs);
        return peer.submit(connection -> connection.scheduleRawDeadline(delayMs), false);
    }


    final boolean isTransportReadPending() { return _readPending; }

    final boolean isTransportWritePending() {
        CIOBufferEx writing = _writing;
        return writing != null && writing._ioPending;
    }

    @Override public void onRawRead(int bytes) { publishReadCompletion(bytes); }

    final void publishReadCompletion(int bytes) {

        final int event = bytes < 0 || bytes > CIOBufferPool.LARGE_BYTES
                ? 1 : bytes + 2;
        // A single READ is outstanding per socket. With an idle pump there
        // cannot be a preceding unretired READ completion. Claim its existing
        // ownership token and consume this event directly: no mailbox CAS,
        // mailbox exchange, or second ownership atomic on the common path.
        // If postRead() completed inline while the pump was active, use the
        // old mailbox + kick path to trampoline without recursion.
        if (READ_KICK.compareAndSet(this, 0, 1)) {
            drainReadPump(1, event);
            return;
        }
        if (!READ_EVENT.compareAndSet(this, 0, event)) {
            // An already-published completion cannot be overwritten by a
            // second callback; the first one must still release its READ lease.
            close();
            return;
        }
        kickRead();
    }


    final void submitTransportWrite(int kind) {
        if (!isOpen()) {
            onRawWrite(-1, kind == GATHER_WRITE);
        } else if (peer.isOwnerThread()) {
            armTransportWrite(kind);
        } else {
            if (!TRANSPORT_WRITE.compareAndSet(this, 0, kind))
                throw new IllegalStateException("WRITE already queued");
            if (!peer.submit(ARM_WRITE, false)) cancelQueuedWrite();
        }
    }

    final void armQueuedWrite() {
        int kind = (int) TRANSPORT_WRITE.getAndSet(this, 0);
        if (kind != 0) armTransportWrite(kind);
    }

    final void cancelQueuedWrite() {
        int kind = (int) TRANSPORT_WRITE.getAndSet(this, 0);
        if (kind != 0) onRawWrite(-1, kind == GATHER_WRITE);
    }

    final void armTransportWrite(int kind) {
        if (kind == GATHER_WRITE) {
            if (!peer.writeRaw(_gatherBuffers, _gatherOffset, _gatherCount - _gatherOffset)) onRawWrite(-1, true);
        } else {
            ByteBuffer bytes = _writing.buffer();
            if (!bytes.hasRemaining()) onRawWrite(0, false);
            else if (!peer.writeRaw(bytes)) onRawWrite(-1, false);
        }
    }


    public String getRemoteAddress() { return _remoteAddress; }
    public int affinityShard() { return _affinityShard; }


    public boolean isTransportOwnerThread() { return peer.isOwnerThread(); }

    /** Exactly one pending read per socket. */
    public void startRead() {
        try {
            _stateLock.lock(); try {
                if ((_closed != 0) || !READ_ACTIVE.compareAndSet(this, 0, 1)) return;
                try {
                    _reading = _pool.acquireRecv(_affinityShard);
                } catch (RuntimeException | Error failure) {
                    READ_ACTIVE.setVolatile(this, 0);
                    close();
                    throw failure;
                }
                if (_reading == null) {
                    READ_ACTIVE.setVolatile(this, 0);
                    close();
                    return;
                }
            } finally { _stateLock.unlock(); }
        } finally {
            // close() may reenter under the state lock on pool failure.
            if (_closed != 0) kickWrite();
        }
        kickRead();
    }

    /**
     * Only one thread may touch the compacting READ ByteBuffer at a time.
     * Synchronous completed() callbacks are trampolined without recursion.
     * Cross-thread SEND acknowledgements may race with the NIO completion.
     */
    final void kickRead() {
        if (!peer.isOwnerThread()) {
            if (_closed == 0) scheduleJavaRead();
            else {
                finishReadIfClosed();
                notifyClosedWhenReleased();
            }
            return;
        }
        if ((int) READ_KICK.getAndAdd(this, 1) != 0) return;
        drainReadPump(1, 0);
    }

    /** Called with READ_KICK ownership; firstEvent is an unposted completion. */
    final void drainReadPump(int missed, int firstEvent) {
        do {
            // The callback may be invoked inline, even before channel.read()
            // returns. Only this pump retires an outstanding READ operation.
            final int event;
            if (firstEvent != 0) {
                event = firstEvent;
                firstEvent = 0;
            } else {
                event = (int) READ_EVENT.getAndSet(this, 0);
            }
            if (event != 0) {

                _readPending = false;
                if (event == 1 || _closed != 0) {
                    close();
                } else if (event == 2) {
                    if (++_zeroReadCompletions >= 8) close();
                } else {
                    _zeroReadCompletions = 0;
                }
            }
            if (_closed == 0 && _closing == 0 && !_readPending && !_sendReadPaused) {
                try {
                    // Only one short, fully buffered frame may borrow an idle
                    // WRITE token. The READ pump still owns framing and read()
                    // registration. Queued SEND reservations retain original FIFO.
                    final boolean fused = tryOwnReadWritePass();
                    boolean accepted;
                    try {
                        accepted = fused ? onReadSingleCompleteFrame() : OnRead();
                    } finally {
                        // Dispatch releases the borrowed SEND token before any
                        // game handler executes. If decode/filtering exited before
                        // dispatch, the READ pump must still release that token.
                        if (fused && _readSocketOwnerActive)
                            releaseFusedReadSendOwner();
                    }
                    if (!accepted) {
                        close();
                    } else if (_closed == 0 && !_sendReadPaused) {
                        if (!_readYielded) postRead();
                        else scheduleJavaRead();
                    }
                } catch (Throwable failure) {
                    LOG.log(Level.SEVERE, "[CIO READ] dispatch failed remote=" + _remoteAddress, failure);
                    close();
                }
            }
            missed = ((int) READ_KICK.getAndAdd(this, -missed) - missed);
        } while (missed != 0);
        finishReadIfClosed();
    }


    final void postRead() {
        if (_closed != 0 || _closing != 0) return;
        final ByteBuffer recv = _reading.buffer();
        if (!recv.hasRemaining()) {
            close();
            return;
        }
        // Set before the call: async I/O may invoke completed() synchronously.
        _readPending = true;

        try {
            if (!peer.readRaw(recv)) onRawRead(-1);
        } catch (Throwable t) {
            _readPending = false;
            close();
        }
    }

    /** Release the READ lease on close only after the last completion. */
    final void finishReadIfClosed() {
        if ((_closed != 0) && !_readPending && _readKick == 0) {
            _stateLock.lock(); try {
                if (!_readPending && _readKick == 0) endRead();
            } finally { _stateLock.unlock(); }
        }
    }


    final void scheduleJavaRead() {
        _stateLock.lock();
        try {
            if (_closed != 0 || javaReadScheduled) return;
            javaReadScheduled = true;
        } finally { _stateLock.unlock(); }
        if (!peer.submit(READ_PUMP, true)) cancelReadPump();
    }

    final void clearJavaReadSchedule() {
        _stateLock.lock();
        try { javaReadScheduled = false; }
        finally { _stateLock.unlock(); }
    }

    final void runReadPump() {
        clearJavaReadSchedule();
        kickRead();
    }
    final void cancelReadPump() {
        clearJavaReadSchedule();
        close();
    }

    final void endRead() {
        if (!READ_ACTIVE.compareAndSet(this, 1, 0)) return;
        releaseReadWhenIdle();
        notifyClosedWhenReleased();
    }

    final void releaseReadWhenIdle() {
        Throwable releaseFailure = null;
        _stateLock.lock();
        try {
            if (_readReleased || _readActive != 0) return;
            final CIOBufferEx recv = _reading;
            _reading = null;
            try { if (recv != null) _pool.release(recv); }
            catch (Throwable failure) { releaseFailure = failure; }
            // CIOBufferEx.release() retires its owned slot even if a Native
            // cleanup throws. Keeping _readReleased=false after clearing
            // _reading would permanently block close-complete notification.
            _readReleased = true;
        } finally {
            _stateLock.unlock();
        }
        if (releaseFailure != null) {
            try { LOG.log(Level.WARNING,
                    "[CIO READ] lease release failed remote=" + _remoteAddress,
                    releaseFailure); }
            catch (Throwable ignored) { /* must still close */ }
            if (_closed == 0) close();
        }
    }


    public boolean isOpen() {
        return _closed == 0 && peer.isOpen();
    }
}
