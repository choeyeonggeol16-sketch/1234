package l1j.server.server.model.network.core.connection;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;
import l1j.server.server.model.network.buffer.Disassemble;
import l1j.server.server.model.network.buffer.Assemble;
import l1j.server.server.model.network.codec.SocketSecureKey;
import l1j.server.server.model.network.core.config.CIOConfig;
import l1j.server.server.model.network.core.transport.selector.CSelectorTransport.Connection;

/** Packet framing and ordered cipher state of the existing socket.
 * Keeps game opcode policy in CUserSocket and raw I/O in CIOSocket.
 */
public abstract class CSocket extends CIOSocket {
    private static final Logger LOG = Logger.getLogger(CUserSocket.class.getName());
    volatile int _disconnecting;
    volatile long _partialReadStartedNs;
    static final int READ_PASS_MIN_BUFFERED_BYTES = 64;
    static final int FUSED_READ_MAX_BYTES = 256;
    int _readOffset; // READ pump only, unconsumed prefix in the write-mode receive buffer
    boolean _readPassBurstActive;
    static final VarHandle DISCONNECTING, PARTIAL_READ_STARTED;
    static {
        try {
            final MethodHandles.Lookup lookup = MethodHandles.lookup();
            DISCONNECTING = lookup.findVarHandle(CSocket.class, "_disconnecting", int.class);
            PARTIAL_READ_STARTED = lookup.findVarHandle(CSocket.class, "_partialReadStartedNs", long.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    // Existing bounded SEND ring, reservations and pressure accounting.
    volatile long _networkWriteProgress;
    volatile long _partialReadPausedAtNs;
    CIOBufferEx[] _pending;
    final int _maxQueuedFrames;
    static final int WRITE_DONE = 1;
    static final int WRITE_FAILED = 2;
    static final int GATHER_DONE = 3;
    static final int GATHER_FAILED = 4;
    int _head;
    int _tail;
    int _queued;
    int _stagedSends;
    static final int MAX_PENDING_WIRE_BYTES = 2 * 1024 * 1024;
    static final boolean SEND_READ_WATERMARK = true;
    static final int SEND_READ_HIGH_WATER_BYTES = 256 * 1024;
    static final int SEND_READ_LOW_WATER_BYTES = SEND_READ_HIGH_WATER_BYTES / 2;
    static final int SEND_READ_HIGH_WATER_FRAMES = 256;
    static final int SEND_READ_LOW_WATER_FRAMES = SEND_READ_HIGH_WATER_FRAMES / 2;
    int _outstandingWireBytes;
    int _reservedWireBytes;
    int _outstandingFrames;
    int _reservedDirect;
    static final int GATHER_MAX_FRAMES = 8;
    static final int GATHER_MAX_BYTES = 64 * 1024;
    CIOBufferEx[] _gatherFrames;
    int _zeroWriteCompletions;

    CSocket(Connection peer, long id, CIOBufferPool pool, int maxQueuedFrames) {
        super(peer, id, pool);
        _maxQueuedFrames = maxQueuedFrames;
    }

    abstract void onSendProgress();

    public abstract boolean isConnected();
    public abstract void disconnect();
    public abstract int incrementEmptyPacketCount();
    public abstract void resetEmptyPacketCount();
    abstract boolean dispatchPlaintext(Disassemble decoded);
    abstract void beginReadPassSendBurst();
    abstract void endReadPassSendBurst();

    private static final class DecodeState {
        final Disassemble buffer = new Disassemble();
        boolean active;
        DecodeState nested;
    }
    private static final ThreadLocal<DecodeState> DECODE = ThreadLocal.withInitial(DecodeState::new);
    private static DecodeState acquireDecodeState() {
        DecodeState state = DECODE.get();
        while (state.active) {
            if (state.nested == null) state.nested = new DecodeState();
            state = state.nested;
        }
        state.active = true;
        return state;
    }
    private volatile SocketSecureKey _cryptor;
    private volatile boolean _handshakePlainPending;
    public void SetKey(long seed) {
        final SocketSecureKey cryptor = new SocketSecureKey();
        cryptor.initKey(seed);
        _cryptor = cryptor;
        _handshakePlainPending = true;
    }
    public void clearCipher() {
        _handshakePlainPending = false;
        _cryptor = null;
    }
    public boolean encrypt(Assemble buffer, int offset, int length) {
        if (_handshakePlainPending) {
            _handshakePlainPending = false;
            return true;
        }
        final SocketSecureKey cipher = _cryptor;
        if (cipher == null) return true;
        try {
            cipher.encrypt(buffer, offset, length);
            return true;
        } catch (Exception error) {
            LOG.log(Level.SEVERE, "[CUserSocket] packet encryption failed", error);
            return false;
        }
    }
    private boolean encryptActionBurst(Assemble writer, int frameCount) {
        int frameOffset = 0;
        if (_handshakePlainPending) {
            _handshakePlainPending = false;
            frameOffset = Long.BYTES;
            frameCount--;
        }
        final SocketSecureKey cipher = _cryptor;
        if (cipher == null) return true;
        try {
            cipher.encryptActionBurst(writer, frameOffset, frameCount);
            return true;
        } catch (Exception error) {
            LOG.log(Level.SEVERE, "[CUserSocket] packet encryption failed", error);
            return false;
        }
    }

    public boolean decrypt(Disassemble buffer, int offset, int length) {
        final SocketSecureKey cipher = _cryptor;
        if (cipher == null) return true;
        try {
            cipher.decrypt(buffer, offset, length);
            return true;
        } catch (Exception error) {
            LOG.log(Level.WARNING, "[CUserSocket] packet decryption failed", error);
            return false;
        }
    }

    /**
     * The client cipher advances per game packet, not per transport write.
     * Each packed frame is encrypted individually in wire order.
     */
    final boolean preparePackedEncryption(CIOBufferEx lease) {
        final Assemble writer = lease.writer();
        final int length = lease.wireLength();
        if (lease._actionOnly) {
            final int frameCount = lease._frameCount;
            if (length != frameCount * Long.BYTES) return false;
            if (frameCount >= 4) return encryptActionBurst(writer, frameCount);
            for (int offset = 0; offset < length; offset += Long.BYTES) {
                if (!encrypt(writer, offset + CIOBufferPool.HEADER_BYTES, 6)) return false;
            }
            return true;
        }
        final ByteBuffer bytes = lease.buffer();
        int offset = 0;
        while (offset < length) {
            if (length - offset < CIOBufferPool.HEADER_BYTES) return false;
            final int frameLength = Short.toUnsignedInt(bytes.getShort(offset));
            if (frameLength <= CIOBufferPool.HEADER_BYTES
                    || frameLength > length - offset) return false;
            if (!encrypt(writer,
                    offset + CIOBufferPool.HEADER_BYTES,
                    frameLength - CIOBufferPool.HEADER_BYTES)) return false;
            offset += frameLength;
        }
        return true;
    }

    /**
     * Borrow the existing 512-slot serializer only for an uncontended,
     * complete small READ frame. A pending/unpublished producer reservation,
     * WRITE completion or SEND_START must execute in original FIFO order.
     * READ ownership remains with READ_KICK; this only joins the SEND pump.
     */
    @Override final boolean tryOwnReadWritePass() {
        if (!READ_WRITE_OWNER_FUSION || _closed != 0 || _closing != 0
                || _sendReadPaused || _readYielded || _reading == null || _readOffset != 0) return false;
        final ByteBuffer recv = _reading.buffer();
        final int filled = recv.position();
        if (filled < CIOBufferPool.HEADER_BYTES + 1
                || filled > FUSED_READ_MAX_BYTES
                || Short.toUnsignedInt(recv.getShort(0)) != filled) return false;
        if ((int) SOCKET_PENDING.getVolatile(this) != 0
                || (long) SOCKET_TICKET.getVolatile(this) != _socketHead
                || (COALESCE_SEND_START
                    && (int) SOCKET_START_PENDING.getAcquire(this) != 0)
                || (long) WRITE_EVENT.getVolatile(this) != 0L) return false;
        if (!SOCKET_DRAINING.compareAndSet(this, 0, 1)) return false;
        // A producer may reserve a FIFO position concurrently with the CAS.
        // Never execute this READ ahead of any earlier unpublished reservation.
        if ((int) SOCKET_PENDING.getVolatile(this) != 0
                || (long) SOCKET_TICKET.getVolatile(this) != _socketHead
                || (COALESCE_SEND_START
                    && (int) SOCKET_START_PENDING.getAcquire(this) != 0)
                || (long) WRITE_EVENT.getVolatile(this) != 0L) {
            drainSocketCallbacksAcquired();
            return false;
        }
        _socketOwner = Thread.currentThread();
        _readSocketOwnerActive = true;
        return true;
    }



    /**
     * Called only after tryOwnReadWritePass() verified an entire, single small
     * frame and claimed the socket serializer. The ordinary decoder keeps all
     * partial/multi-frame, contention, close and backpressure cases.
     */
    @Override final boolean onReadSingleCompleteFrame() {
        _readYielded = false;
        // CLOSE or a foreign SEND producer may change pressure after the
        // READ owner claim. Let the established loop handle those races.
        if (_closed != 0 || _closing != 0 || _sendReadPaused) return OnRead();
        final ByteBuffer recv = _reading.buffer();
        final int wireLength = recv.position();
        recv.flip();
        if (!onReadFrame(recv, CIOBufferPool.HEADER_BYTES,
                wireLength - CIOBufferPool.HEADER_BYTES) || _closed != 0) return false;
        recv.position(wireLength);
        _partialReadStartedNs = 0L;
        // Graceful-close preserves the original un-compacted receive view.
        if (_closing != 0) return true;
        if (_sendReadPaused) _readYielded = true;
        // Exactly one whole frame was consumed: no trailing bytes to compact.
        recv.clear();
        return true;
    }

    /** READ pump only; preserve the clock while extending the SAME partial frame. */
    private void refreshPartialReadDeadline(ByteBuffer inbound) {
        final int buffered = inbound.position() - _readOffset;
        if (buffered == 0) {
            _partialReadStartedNs = 0L;
            return;
        }
        if (buffered >= CIOBufferPool.HEADER_BYTES) {
            final int frameBytes = Short.toUnsignedInt(inbound.getShort(_readOffset));
            if (frameBytes >= CIOBufferPool.HEADER_BYTES && frameBytes <= buffered) {
                // SEND pressure may yield with a complete frame still buffered.
                // It must not inherit the old partial-frame clock on resume.
                _partialReadStartedNs = 0L;
                return;
            }
        }
        if (_partialReadStartedNs == 0L) _partialReadStartedNs = System.nanoTime();
    }

    @Override final boolean OnRead() {
        _readYielded = false;
        ByteBuffer recv = _reading.buffer();
        final int filled = recv.position() - _readOffset;
        if (filled == 0) return true;
        // Avoid an extra ThreadLocal scope for a single completed request.
        // Every pass, including fairness yield or an exception, flushes in
        // finally before the READ pump can post another asynchronous read.
        boolean passBurst = false;
        if (filled >= READ_PASS_MIN_BUFFERED_BYTES) {
            final int first = Short.toUnsignedInt(recv.getShort(_readOffset));
            if (first >= CIOBufferPool.HEADER_BYTES
                    && first <= filled - CIOBufferPool.HEADER_BYTES) {
                final int second = Short.toUnsignedInt(recv.getShort(_readOffset + first));
                passBurst = second >= CIOBufferPool.HEADER_BYTES
                        && second <= filled - first;
            }
        }
        if (passBurst) {
            beginReadPassSendBurst();
            _readPassBurstActive = true;
        }
        try {
        recv.flip().position(_readOffset);
        final boolean javaTransport = true;
        final int frameBudget = javaTransport
                ? Math.max(1, CIOConfig.MAX_PACKETS_PER_COMPLETION) : Integer.MAX_VALUE;
        int completed = 0;
        // The READ pump owns framing and posts the next read only after the
        // current handler returns; fairness yields at complete frame boundaries.
        while (recv.remaining() >= CIOBufferPool.HEADER_BYTES) {
            if (_closing != 0) break;
            // A READ already in flight may complete after SEND pressure rises.
            // Keep its frame encrypted/buffered until the low-water release.
            if (_sendReadPaused) {
                _readYielded = true;
                break;
            }
            final int start = recv.position();
            final int wireLength = Short.toUnsignedInt(recv.getShort(start));
            if (wireLength < CIOBufferPool.HEADER_BYTES
                    || wireLength > CIOBufferPool.MAX_WIRE_BYTES) return false;
            if (recv.remaining() < wireLength) break;
            if (completed == frameBudget) {
                _readYielded = true;
                break;
            }
            completed++;
            // This frame is complete, even when the same TCP read already
            // contains the beginning of the next frame. Its old partial-read
            // deadline must never carry over to that next frame.
            _partialReadStartedNs = 0L;
            final boolean accepted = onReadFrame(recv, start + CIOBufferPool.HEADER_BYTES,
                    wireLength - CIOBufferPool.HEADER_BYTES);
            if (!accepted || (_closed != 0)) return false;
            recv.position(start + wireLength);
            // requestClientClose() ends inbound dispatch, not the outbound
            // drain. Never decode the remaining coalesced requests or re-arm.
            if (_closing != 0) break;
            if (_sendReadPaused) {
                _readYielded = true;
                break;
            }
        }
        // Graceful close leaves any later coalesced inbound frames unread.
        // Do not treat a full READ lease as a malformed large frame here:
        // the lease belongs to the socket until the final SEND drains.
        if (_closing != 0) return true;
        // Avoid compact's copy when all bytes were consumed, or when an
        // incomplete frame already begins at offset zero.
        if (!recv.hasRemaining()) {
            recv.clear();
            _readOffset = 0;
        } else if (recv.position() == 0 || javaTransport && (recv.limit() < recv.capacity() || _readYielded)) {
            _readOffset = recv.position();
            final int buffered = recv.limit();
            recv.clear();
            recv.position(buffered);
        } else {
            recv.compact();
            _readOffset = 0;
        }
        if (!recv.hasRemaining()) {
            // A complete compact-buffer-sized frame can fill this lease exactly.
            // SEND-pressure backpressure can retain a complete frame.
            if (_readYielded) return true;
            if (recv.position() < CIOBufferPool.HEADER_BYTES) return false;
            final int wireLength = Short.toUnsignedInt(recv.getShort(0));
            if (wireLength <= recv.capacity()) return false;
            final CIOBufferEx large = _pool.acquireLargeRecv(_affinityShard);
            if (large == null) return false;
            recv.flip();
            large.buffer().put(recv);
            final CIOBufferEx old = _reading;
            _reading = large;
            _pool.release(old);
        } else if (_reading.buffer().capacity() > CIOBufferPool.RECV_BYTES
                && recv.position() == 0) {
            final CIOBufferEx compact = _pool.acquireRecv(_affinityShard);
            if (compact != null) {
                final CIOBufferEx old = _reading;
                _reading = compact;
                _pool.release(old);
            }
        }
        // _reading may now be a large receive lease after an upgrade above.
        // Only the incomplete frame at the *current head* owns a deadline:
        // a fully buffered frame held behind SEND backpressure is not partial.
        refreshPartialReadDeadline(_reading.buffer());
        return true;
        } finally {
            if (passBurst) {
                _readPassBurstActive = false;
                endReadPassSendBurst();
            }
        }
    }

    private boolean onReadFrame(ByteBuffer wire, int bodyStart, int bodyLength) {
        if (_closing != 0) return _closed == 0 && _disconnecting == 0;
        if (!this.isConnected()) return false;

        // Preserve the empty-packet flood handling (wire length == 2).
        if (bodyLength == 0) {
            if (this.incrementEmptyPacketCount() <= CIOConfig.PACKET_LIMIT_PER_SEC) {
                return true;
            }
            this.disconnect();
            return false;
        }
        this.resetEmptyPacketCount();

        // Lineage decrypt consumes its 4-byte cipher prefix and requires 8 bytes.
        if (bodyLength < 8) {
            this.disconnect();
            return false;
        }

        // Exactly one full decrypt per frame: it advances the rolling cipher
        // state, including filtered and unknown opcodes. Do not preview first.
        final DecodeState state = acquireDecodeState();
        try {
            final Disassemble decoded = state.buffer;
            decoded.resetTrusted(wire, bodyStart, bodyLength);
            if (decrypt(decoded, 0, bodyLength)) {
                int bytes = bodyLength - 4;
                decoded.limit(bytes);
                return dispatchPlaintext(decoded);
            }
            return false;
        } catch (Throwable error) {
            LOG.log(Level.WARNING, "[I/O OnRead] packet decode failed", error);
            return false;
        } finally {
            state.buffer.clear();
            state.active = false;
        }
    }

    /**
     * A fused READ owns the SEND serializer only while decoding/framing.
     * Game opcode handlers can run for tens of milliseconds; retaining the
     * 512-slot SEND token across them strands unrelated NPC/send producers
     * and causes false REJECT_SERIALIZER at only a few KiB of wire backlog.
     * Release using the existing drain/reacquire handshake BEFORE the handler;
     * the READ pump still owns its READ buffer and next-read registration.
     */
    @Override final void releaseFusedReadSendOwner() {
        if (!_readSocketOwnerActive) return;
        if (_socketOwner != Thread.currentThread()
                || (int) SOCKET_DRAINING.getVolatile(this) == 0)
            throw new IllegalStateException("fused READ lost socket serializer ownership");
        _readSocketOwnerActive = false;
        drainSocketCallbacksAcquired();
    }

    /** Always invoked with this socket's state lock held. */
    final boolean canAdmitBytes(int count) {
        return count > 0 && count <= MAX_PENDING_WIRE_BYTES - _outstandingWireBytes - _reservedWireBytes;
    }

    /** Physical SEND leases under _stateLock, including every in-flight gather member. */
    final int physicalSendLeasesLocked() {
        return _queued + _reservedDirect + _stagedSends
                + (_writing == null ? 0 : _gatherCount > 0 ? _gatherCount : 1);
    }

    final boolean physicalSendFullLocked() {
        return physicalSendLeasesLocked() >= _maxQueuedFrames + 1;
    }

    // _maxQueuedFrames bounds physical SEND leases (all gather members plus
    // queued leases and reservations), not packets packed into those leases. The
    // logical packet count is separately tracked for READ backpressure.  Each
    // accepted frame consumes at least HEADER_BYTES + 1 wire bytes, so the
    // MAX_PENDING_WIRE_BYTES admission cap also strictly bounds that count.

    final void bytesAdmitted(int count) {
        bytesAdmittedBatch(count, 1);
    }

    /** Caller holds _stateLock. A validated batch publishes one watermark change. */
    final void bytesAdmittedBatch(int wireBytes, int frames) {
        _outstandingWireBytes += wireBytes;
        _outstandingFrames += frames;
        updateSendReadPauseLocked();
    }

    /** Under the state lock, accounting both published and uncommitted frames. */
    final boolean updateSendReadPauseLocked() {
        if (!SEND_READ_WATERMARK) return false;
        final int bytes = _outstandingWireBytes + _reservedWireBytes;
        final int frames = _outstandingFrames + _reservedDirect;
        if (!_sendReadPaused && (bytes >= SEND_READ_HIGH_WATER_BYTES
                || frames >= SEND_READ_HIGH_WATER_FRAMES)) {
            _partialReadPausedAtNs = System.nanoTime();
            _sendReadPaused = true;
        } else if (_sendReadPaused && bytes <= SEND_READ_LOW_WATER_BYTES
                && frames <= SEND_READ_LOW_WATER_FRAMES) {
            // The READ pump may finish one frame and start another while SEND
            // admission is turning the pause off. CAS protects that new clock;
            // only the overlap between this frame and the pause is excluded.
            final long pausedAt = _partialReadPausedAtNs;
            if (pausedAt != 0L) {
                final long now = System.nanoTime();
                for (;;) {
                    final long started = (long) PARTIAL_READ_STARTED.getVolatile(this);
                    if (started == 0L) break;
                    final long overlapFrom = Math.max(started, pausedAt);
                    if (now <= overlapFrom || PARTIAL_READ_STARTED.compareAndSet(this,
                            started, started + now - overlapFrom)) break;
                }
            }
            _partialReadPausedAtNs = 0L;
            _sendReadPaused = false;
            return _closed == 0 && _readActive != 0;
        }
        return false;
    }

    /** Under the state lock; caller kicks READ only after unlocking. */
    final boolean bytesReleased(int count, int frames) {
        _outstandingWireBytes -= count;
        _outstandingFrames -= frames;
        if (_outstandingWireBytes < 0 || _outstandingFrames < 0)
            throw new IllegalStateException("negative outstanding SEND accounting");
        return updateSendReadPauseLocked();
    }


    @Override public void onRawWrite(long bytes, boolean gather) {
        if (gather) publishWriteCompletion(GATHER_DONE, GATHER_FAILED, bytes);
        else publishWriteCompletion(WRITE_DONE, WRITE_FAILED, bytes);
    }

    /**
     * A WRITE's pending I/O keeps its lease attached to this socket. The native IOCP
     * completion thread only publishes the result; the existing WRITE pump is
     * the sole executor of completion, next-send and buffer-release transitions.
     * There is only one in-flight WRITE per connection, so a single completion
     * mailbox is sufficient. The existing bounded _pending ring continues to
     * retain queued outbound buffers without a redundant callback queue.
     */
    final void publishWriteCompletion(int okKind, int failKind, long transferred) {

        final boolean ok = transferred >= 0L && transferred <= Integer.MAX_VALUE;
        final long event = (ok ? (transferred << 3) | okKind : failKind);
        if (!WRITE_EVENT.compareAndSet(this, 0L, event)) {
            // A second completion for one in-flight WRITE is a transport violation.
            // Never overwrite the first callback or release its lease twice.
            close();
            return;
        }
        // Native IOCP completions run inside write() while this thread
        // already owns the serializer. Defer their retirement until the write
        // invocation returns, then consume in the existing nonrecursive loop.
        if (INLINE_WRITE_COMPLETION && deferOwnerInlineWriteCompletion(event)) return;
        // A WRITE already on the wire must be able to retire while a later
        // SEND producer holds an unpublished FIFO reservation. Retire only
        // this completed prefix under the existing socket serializer token;
        // never consume or move the reserved SEND ticket itself.
        if (tryRetireCommittedWriteAcrossUnpublishedSend(event)) return;
        if (!serializeSocketCallback(SOCKET_WRITE_COMPLETE, event)) {
            // The completion remains in the mailbox. Close and let the current
            // drainer retire it once earlier callback tickets have finished.
            close();
        }
    }

    /**
     * Only the serializer owner may publish this local deferral. A ticket
     * reserved before this WRITE callback must still execute first. While
     * dispatching a ticket, _socketPending includes that one active ticket;
     * it has already been consumed and cannot be an earlier reservation.
     * Checking the pending reservation count first also sees unpublished
     * producer tickets whose ring slots are not yet visible.
     */
    final boolean deferOwnerInlineWriteCompletion(long event) {
        if (!_socketPosting || _socketOwner != Thread.currentThread()
                || _inlineWriteCompletion != 0L) return false;
        final int active = _socketExecutingTicket ? 1 : 0;
        if ((int) SOCKET_PENDING.getVolatile(this) != active
                || (long) SOCKET_TICKET.getVolatile(this) != _socketHead
                || (COALESCE_SEND_START
                    && (int) SOCKET_START_PENDING.getAcquire(this) != 0)) {
            // The current WRITE was committed before this callback. A later
            // SEND may have reserved the oldest pending slot but not sealed
            // it yet. Retiring the WRITE cannot overtake that SEND's bytes;
            // the reserved slot remains untouched until its producer seals it.
            if (!hasUnpublishedSendBehindWriting(active)) return false;
        }
        _inlineWriteCompletion = event;
        return true;
    }

    /**
     * Only a WRITE already in flight may retire ahead of a later producer's
     * unsealed SEND reservation. This does not claim or consume the reservation.
     * The serializer token must already be held before calling this check.
     */
    final boolean hasUnpublishedSendBehindWriting(int active) {
        if (_writing == null || !_writing._ioPending || _closed != 0
                || (int) SOCKET_PENDING.getVolatile(this) <= active) return false;
        final int slot = (int) _socketHead & SOCKET_MASK;
        return (byte) SOCKET_CALLBACK_SLOT.getAcquire(_socketCallbacks, slot) == 0L;
    }

    /**
     * An earlier committed WRITE may finish while a newer SEND encoder has
     * reserved, but not published, the head FIFO slot. Waiting for that
     * encoder would stall completion/lease retirement of an older WRITE.
     * Only consume the WRITE mailbox with the normal serializer ownership;
     * later SEND tickets stay reserved and are executed in their FIFO order.
     * The active provider call and owner-inline callback stay on their
     * original, write()-return-safe path.
     */
    final boolean tryRetireCommittedWriteAcrossUnpublishedSend(long event) {
        // The common inline completion was already handled by the owner.
        // A FOREIGN completion can arrive before write() returns while the
        // posting owner is blocked in the provider and a later SEND ticket
        // is reserved but unpublished. Hand off only the notification, never
        // its lease. Keep this rare case outside the ordinary serializer CAS.
        if (_socketPosting) {
            if (_closed != 0 || _socketOwner == Thread.currentThread()
                    || (int) SOCKET_PENDING.getAcquire(this) == 0
                    || (int) SOCKET_DRAINING.getAcquire(this) == 0) return false;
            final CIOBufferEx active = _writing;
            if (active == null || !active._ioPending) return false;
            _prefixWriteCompletion = event;
            drainSocketCallbacks();
            return true;
        }
        // Uncontended and already-returned completions retain the P8 path.
        if (_closed != 0 || (int) SOCKET_PENDING.getAcquire(this) == 0) return false;
        if (SOCKET_DRAINING.compareAndSet(this, 0, 1)) {
            _socketOwner = Thread.currentThread();
            try {
                if (!hasUnpublishedSendBehindWriting(0)) return false;
                if (WRITE_EVENT.compareAndSet(this, event, 0L))
                    drainWriteCompletion(event);
                return true;
            } catch (Throwable failure) {
                close();
                return true;
            } finally {
                drainSocketCallbacksAcquired();
            }
        }
        // The READ-fusion owner (or another callback drainer) already owns
        // the token. Do NOT append WRITE_COMPLETE behind a reserved/unsealed
        // SEND head: the owner would stop at that head and strand this WRITE.
        // The in-flight WRITE predates any newer SEND; hand its one completion
        // to the existing owner without moving any SEND ticket or lease.
        final CIOBufferEx active = _writing;
        if (active == null || !active._ioPending) return false;
        _prefixWriteCompletion = event;
        // A race with owner release is closed by the ordinary recheck below.
        // The callback may claim the token if the old owner has left.
        drainSocketCallbacks();
        return true;
    }

    /**
     * Claim the existing serializer token only when its FIFO is empty.
     * A concurrent producer's reservation is visible through _socketPending
     * before its ticket is published. Any contention takes the original
     * ticket path without changing WRITE ownership or lease lifetime.
     */


    /** Runs only with the socket serializer's execution token. */
    @Override final void drainWriteCompletion(long event) {

        final int kind = (int) (event & 7L);
        if (kind == WRITE_DONE || kind == GATHER_DONE)
            _networkWriteProgress += event >>> 3;
        if (kind == WRITE_FAILED) {
            final CIOBufferEx lease = _writing;
            if (lease != null) completeWrite(lease, false);
            else close();
            return;
        }
        if (kind == GATHER_FAILED) {
            completeGather(false);
            return;
        }
        if (kind == WRITE_DONE) {
            final CIOBufferEx lease = _writing;
            if (lease == null) { close(); return; }
            if (_closed != 0) { completeWrite(lease, false); return; }
            final ByteBuffer buffer = lease.buffer();
            if (buffer.hasRemaining()) {
                final long written = event >>> 3;

                if (written == 0L && ++_zeroWriteCompletions >= 8) {
                    completeWrite(lease, false);
                    return;
                }
                if (written != 0L) {
                    _zeroWriteCompletions = 0;
                }
                // This lease was already selected and has not been retired.
                // A partial WRITE must finish before any later queued frame.
                // The current serializer owns its lifetime; an inline callback
                // is published and drained iteratively by the same serializer.
                lease._ioPending = false;
                kickWrite();
            } else {
                _zeroWriteCompletions = 0;
                completeWrite(lease, true);
            }
            return;
        }
        if (kind == GATHER_DONE) {
            if (_closed != 0) { completeGather(false); return; }
            int firstRemaining = _gatherOffset;
            while (firstRemaining < _gatherCount && !_gatherBuffers[firstRemaining].hasRemaining()) ++firstRemaining;
            if (firstRemaining < _gatherCount) {
                _gatherOffset = firstRemaining;
                final long written = event >>> 3;

                if (written == 0L && ++_zeroWriteCompletions >= 8) {
                    completeGather(false);
                    return;
                }
                if (written != 0L) {
                    _zeroWriteCompletions = 0;
                }
                if (_writing == null) { close(); return; }
                // Like the single-buffer partial path: the same serializer
                // still owns every gather lease. Repost the remaining WSABUFs
                // without reacquiring _stateLock or changing the FIFO head.
                // The provider call remains outside the state lock and inline
                // completions are trampolined by the existing callback ring.
                _writing._ioPending = false;
                kickWrite();
            } else {
                _zeroWriteCompletions = 0;
                completeGather(true);
            }
            return;
        }
        close();
    }


    /** Acquire a packet-writing slot; false/null means backpressure, not a silent successful SEND. */
    public CIOBufferEx beginOutboundFrame(int maxWireBytes) {
        return (_closed != 0) ? null : _pool.acquireSend(maxWireBytes);
    }


    /**
     * Allocate the large pending ring only when a connection actually has a burst.
     * Called with the connection state lock held. The ring grows at most to the
     * original configured bound; no extra queue or per-frame nodes are created.
     */
    final boolean ensurePendingSlot() {
        final CIOBufferEx[] old = _pending;
        if (old == null) {
            _pending = new CIOBufferEx[Math.min(_maxQueuedFrames, 8)];
            return true;
        }
        if (_queued < old.length) return true;
        if (old.length >= _maxQueuedFrames) return false;
        final int capacity = (int) Math.min((long) _maxQueuedFrames, (long) old.length << 1);
        final CIOBufferEx[] next = new CIOBufferEx[capacity];
        final int first = Math.min(_queued, old.length - _head);
        System.arraycopy(old, _head, next, 0, first);
        if (_queued > first) System.arraycopy(old, 0, next, first, _queued - first);
        _pending = next;
        _head = 0;
        _tail = _queued;
        return true;
    }


    /** Only call this for a frame that was NOT committed. */
    public void abortOutboundFrame(CIOBufferEx lease) {
        if (lease == null) return;
        if (!_pool.owns(lease) || !lease.reserved())
            throw new IllegalStateException("abort after commit or foreign SEND lease");
        _pool.release(lease);
    }


    /**
     * An unpublished FIFO ticket bars later SENDs, not the already committed
     * prefix. A ready _writing lease was selected before that ticket and can
     * be posted without consuming or overtaking the unpublished slot.
     * Check under the existing state lock; never issue provider WRITE in it.
     */
    @Override final boolean hasCommittedWriteReady() {
        _stateLock.lock();
        try {
            return _writing != null && !_writing._ioPending;
        } finally {
            _stateLock.unlock();
        }
    }

    /** SEND registration and next-WRITE selection, executed by the serializer. */
    @Override final void kickWrite() {
        if (!COALESCE_SEND_START) {
            if (!serializeSocketCallback(SOCKET_SEND_START, 0L)) close();
            return;
        }
        // A ticket-free READ owner cannot bypass an earlier reserved SEND.
        if (canExecuteOwnerControlDirect()) {
            // Already-posted WRITE owns the committed FIFO prefix. Its
            // completion (or failure/close) drives the next lease. Avoid
            // reacquiring _stateLock only to find _ioPending=true. Keep the
            // original wakeup path for foreign/unpublished FIFO reservations.
            final CIOBufferEx active = _writing;
            if (_closed == 0 && _closing == 0 && active != null && active._ioPending)
                return;
            driveWriteOnce();
            return;
        }
        SOCKET_START_PENDING.setRelease(this, 1);
        drainSocketCallbacks();
    }

    @Override final void driveWriteOnce() {
        CIOBufferEx current = null;
        boolean gather = false;
        boolean cancelled = false;
        _stateLock.lock();
        try {
            if (_writing != null && !_writing._ioPending) {
                current = _writing;
                cancelled = (_closed != 0);
                if (!cancelled && _gatherCount == 0 && _queued != 0) {
                    if (_gatherFrames == null) {
                        _gatherFrames = new CIOBufferEx[GATHER_MAX_FRAMES];
                        _gatherBuffers = new ByteBuffer[GATHER_MAX_FRAMES];
                    }
                    int totalBytes = current.wireLength();
                    _gatherFrames[0] = current;
                    _gatherCount = 1;
                    while (_queued != 0 && _gatherCount < GATHER_MAX_FRAMES) {
                        final CIOBufferEx candidate = _pending[_head];
                        if (totalBytes + candidate.wireLength() > GATHER_MAX_BYTES)
                            break;
                        _pending[_head] = null;
                        if (++_head == _pending.length) _head = 0;
                        --_queued;
                        _gatherFrames[_gatherCount++] = candidate;
                        totalBytes += candidate.wireLength();
                    }
                    if (_gatherCount == 1) {
                        _gatherFrames[0] = null;
                        _gatherCount = 0;
                    }
                }
                gather = _gatherCount > 1;
                current._ioPending = true;
            }
        } finally {
            _stateLock.unlock();

        }

        if (current != null) {
            if (cancelled) {
                if (gather) completeGather(false);
                else completeWrite(current, false);
            } else if (gather) postGather();
            else postWrite(current);
        }
    }

    /** Ordered packet leases; native owns per-frame cipher and serialized SEND/WRITE. */
    final void postGather() {
        if (_closed != 0) {
            completeGather(false);
            return;
        }
        try {
            boolean preparedAny = false;
            for (int i = _gatherOffset; i < _gatherCount; i++) {
                final CIOBufferEx lease = _gatherFrames[i];
                final ByteBuffer bytes = lease.buffer();
                if (!lease._writePrepared) {
                    preparedAny = true;
                    if (lease._packedFrames ? !preparePackedEncryption(lease)
                            : !encrypt(lease.writer(),
                                    CIOBufferPool.HEADER_BYTES,
                                    lease.wireLength() - CIOBufferPool.HEADER_BYTES)) {
                        completeGather(false);
                        return;
                    }
                    bytes.limit(lease.wireLength());
                    bytes.position(0);
                    lease._writePrepared = true;

                }
                _gatherBuffers[i] = bytes;
            }


            _socketPosting = true;
            try {
                submitTransportWrite(GATHER_WRITE);
            } finally {
                _socketPosting = false;

            }
        } catch (Throwable error) {
            LOG.log(Level.WARNING, "[CIO WRITE] gather post failed remote=" + _remoteAddress, error);
            // A provider may publish a completion before throwing. Let the
            // serializer consume the mailbox exactly once after write returns.
            if (INLINE_WRITE_COMPLETION && (long) WRITE_EVENT.getVolatile(this) != 0L) close();
            else completeGather(false);
        }
    }

    final void completeGather(boolean success) {

        CIOBufferEx next = null;
        boolean resumeInbound = false;
        Throwable releaseFailure = null;
        _stateLock.lock();
        try {
            if (_gatherCount <= 1 || _writing != _gatherFrames[0]) {
                close();
                return;
            }
            int releasedBytes = 0;
            int releasedFrames = 0;
            for (int i = 0; i < _gatherCount; i++) {
                final CIOBufferEx frame = _gatherFrames[i];
                releasedBytes += frame.wireLength();
                releasedFrames += frame._frameCount;
                try { _pool.release(frame); }
                catch (Throwable failed) {
                    if (releaseFailure == null) releaseFailure = failed;
                    else if (releaseFailure != failed) releaseFailure.addSuppressed(failed);
                    success = false;
                } finally {
                    _gatherFrames[i] = null;
                    _gatherBuffers[i] = null;
                }
            }
            // READ resumes once, after all gather frames have been retired.
            resumeInbound = bytesReleased(releasedBytes, releasedFrames);
            _gatherCount = 0;
            _gatherOffset = 0;
            _writing = null;
            if (success && _closed == 0 && _queued != 0) {
                // The next lease is already committed in FIFO order. Do not
                // bypass unsealed producer tickets or the gather path.
                next = _pending[_head];
                _pending[_head] = null;
                if (++_head == _pending.length) _head = 0;
                --_queued;
                _writing = next;
                _zeroWriteCompletions = 0;
            }
        } finally {
            _stateLock.unlock();

        }
        if (releaseFailure != null) try {
            LOG.log(Level.WARNING, "[CIO WRITE] gather lease release failed remote="
                    + _remoteAddress, releaseFailure);
        } catch (Throwable ignored) { /* close broken connection below */ }
        if (!success) close();
        else if (next != null) {

            kickWrite();
        }
        if (resumeInbound && _closed == 0) kickRead();
        if (success && _closed == 0) notifySendSlotAvailable();
        notifyClosedWhenReleased();
    }

    final void notifySendSlotAvailable() {
        try { onSendProgress(); } catch (Throwable failure) { close(); }
    }

    final void postWrite(CIOBufferEx lease) {
        if (_closed != 0) {
            lease._ioPending = false;
            completeWrite(lease, false);
            return;
        }
        try {
            final ByteBuffer bytes = lease.buffer();
            final boolean preparing = !lease._writePrepared;
            if (preparing) {
                final Assemble writer = lease.writer();
                if (lease._packedFrames ? !preparePackedEncryption(lease)
                        : !encrypt(writer,
                                CIOBufferPool.HEADER_BYTES,
                                lease.wireLength() - CIOBufferPool.HEADER_BYTES)) {
                    lease._ioPending = false;
                    completeWrite(lease, false);
                    return;
                }
                bytes.limit(lease.wireLength());
                bytes.position(0);
                lease._writePrepared = true;

            }


            _socketPosting = true;
            try {
                submitTransportWrite(SINGLE_WRITE);
            } finally {
                _socketPosting = false;

            }
        } catch (Throwable error) {
            LOG.log(Level.WARNING, "[CIO WRITE] post failed remote=" + _remoteAddress, error);
            if (INLINE_WRITE_COMPLETION && (long) WRITE_EVENT.getVolatile(this) != 0L) close();
            else {
                lease._ioPending = false;
                completeWrite(lease, false);
            }
        }
    }

    final void completeWrite(CIOBufferEx lease, boolean success) {

        CIOBufferEx next = null;
        boolean resumeInbound;
        Throwable releaseFailure = null;
        _stateLock.lock();
        try {
            if (_writing != lease) {
                close();
                return;
            }
            resumeInbound = bytesReleased(lease.wireLength(), lease._frameCount);
            try { _pool.release(lease); }
            catch (Throwable failed) { releaseFailure = failed; success = false; }
            // Release failures must not leave the completed lease as _writing:
            // otherwise close()/the next WRITE can never make progress.
            _writing = null;
            if (success && _closed == 0 && _queued != 0) {
                // The next lease is already committed in FIFO order. Do not
                // bypass unsealed producer tickets or the gather path.
                next = _pending[_head];
                _pending[_head] = null;
                if (++_head == _pending.length) _head = 0;
                --_queued;
                _writing = next;
                _zeroWriteCompletions = 0;
            }
        } finally {
            _stateLock.unlock();

        }
        if (releaseFailure != null) try {
            LOG.log(Level.WARNING, "[CIO WRITE] completed lease release failed remote="
                    + _remoteAddress, releaseFailure);
        } catch (Throwable ignored) { /* close broken connection below */ }
        if (!success) {
            close();
        } else if (next != null) {

            kickWrite();
        }
        if (resumeInbound && _closed == 0) kickRead();
        if (success && _closed == 0) notifySendSlotAvailable();
        notifyClosedWhenReleased();
    }

    /** No allocation; useful for admission diagnostics and slow-peer checks. */
    public int pendingSendBytes() {
        _stateLock.lock();
        try { return _outstandingWireBytes + _reservedWireBytes; }
        finally { _stateLock.unlock(); }
    }

    /** True when the connection's outbound backlog allows further socket reads. */
    public boolean isSendReadWritable() { return !_sendReadPaused; }

    /** Snapshot only; includes the active WRITE, queued frames and reserved producers. */
    public int pendingSendFrames() {
        _stateLock.lock();
        try { return physicalSendLeasesLocked(); }
        finally { _stateLock.unlock(); }
    }

    final boolean isWriteIdleLocked() {
        // A primitive SOCKET_PACKED_ACTION is already accepted and charged
        // to outstanding accounting BEFORE it becomes a physical SEND lease.
        // A drain callback or graceful close must not overtake that FIFO ticket.
        // Do not use _socketPending here: it includes the currently executing
        // WRITE-completion control ticket and could prevent drain forever.
        return _queued == 0 && _reservedDirect == 0 && _stagedSends == 0
                && _writing == null && _outstandingWireBytes == 0
                && _outstandingFrames == 0 && _reservedWireBytes == 0;
    }

    public boolean isWriteIdle() {
        _stateLock.lock();
        try { return isWriteIdleLocked(); }
        finally { _stateLock.unlock(); }
    }


    long networkWriteProgress() { return _networkWriteProgress; }
}
