package l1j.server.server.model.network.core.connection;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;
import l1j.server.server.model.network.buffer.CPacketReadBuffer;
import l1j.server.server.model.network.buffer.CPacketWriteBuffer;
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

    CSocket(Connection peer, long id, CIOBufferPool pool) {
        super(peer, id, pool);
    }

    public abstract boolean isConnected();
    public abstract void disconnect();
    public abstract int incrementEmptyPacketCount();
    public abstract void resetEmptyPacketCount();
    abstract boolean dispatchPlaintext(CPacketReadBuffer decoded);
    abstract void beginReadPassSendBurst();
    abstract void endReadPassSendBurst();

    private static final class DecodeState {
        final CPacketReadBuffer buffer = new CPacketReadBuffer();
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
    public boolean encrypt(CPacketWriteBuffer buffer, int offset, int length) {
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
    private boolean encryptActionBurst(CPacketWriteBuffer writer, int frameCount) {
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

    public boolean decrypt(CPacketReadBuffer buffer, int offset, int length) {
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
        final CPacketWriteBuffer writer = lease.writer();
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
            final CPacketReadBuffer decoded = state.buffer;
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
}
