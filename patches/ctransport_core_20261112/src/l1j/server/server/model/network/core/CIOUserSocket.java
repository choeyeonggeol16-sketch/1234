package l1j.server.server.model.network.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.TimeUnit;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.AtomicLong;

import l1j.server.server.Opcodes;
import java.util.concurrent.locks.ReentrantLock;
import l1j.server.server.model.network.buffer.ReadBuffer;
import l1j.server.server.model.network.codec.Cryptor;
import l1j.server.server.model.network.safe.NetworkSafeLoadManager;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.network.utill.ConnectionConfig;

import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.packet.CPacketHandler;
import l1j.server.server.model.network.packet.WritablePacket;

/**
 * Native Windows IOCP transport with a per-socket 512-slot callback serializer.
 * WRITE completions and SEND starts follow the connection's ticket/FIFO
 * execution rule. READ framing retains its independent direct callback path;
 * creature actions execute on completion workers through their own
 * controller serializer; session/wheel ownership is still isolated.
 */
public final class CIOUserSocket implements AutoCloseable, CIOThreadCallback.PacketTask {
    @FunctionalInterface
    public interface Closed {
        void onClosed(CIOUserSocket connection);
        default void onClosing(CIOUserSocket connection) {}
        default void onCleanupComplete(CIOUserSocket connection) {}
    }

    private final CTransportSocket _channel;
    private final CIOBufferPool _pool;
    private final ReentrantLock _stateLock = new ReentrantLock();
    private static final Logger LOG = Logger.getLogger(CIOUserSocket.class.getName());
    // Diagnostics only; no new per-connection atomics or per-SEND allocation.
    public static final int REJECT_WIRE_BYTES = 1;
    public static final int REJECT_LOCAL_FRAMES = 2;
    public static final int REJECT_SERIALIZER = 3;
    public static final int REJECT_POOL = 4;
    public static final int REJECT_ENCODING = 5;
    private static final AtomicLongArray SEND_REJECTIONS = new AtomicLongArray(6);
    // A bounded, allocation-free completion-worker blockage diagnostic.
    // Slow opcode handlers are counted without printing per-packet logs.
    private static final long SLOW_HANDLER_NS = TimeUnit.MILLISECONDS.toNanos(10);
    private static final AtomicLongArray SLOW_OPCODE_COUNTS = new AtomicLongArray(256);
    private static final AtomicLong SLOW_MAX_NS = new AtomicLong();

    public static long slowOpcodeCount(int opcode) {
        return SLOW_OPCODE_COUNTS.get(opcode & 0xFF);
    }

    public static long maxOpcodeHandlerNanos() { return SLOW_MAX_NS.get(); }

    public static long sendRejections(int reason) {
        if (reason <= 0 || reason >= SEND_REJECTIONS.length())
            throw new IllegalArgumentException("invalid SEND rejection reason");
        return SEND_REJECTIONS.get(reason);
    }
    // Written only on rejected sends. No additional work in the SEND hot path.
    private volatile int _lastSendReject;
    private boolean rejectSend(int reason) {
        _lastSendReject = reason;
        SEND_REJECTIONS.incrementAndGet(reason);
        return false;
    }
    // Claim an idle serializer owner directly when no earlier reservation exists.
    // The normal FIFO drain/release handshake still owns completion and closing.
    private static final class DecodeState {
        final ReadBuffer buffer = new ReadBuffer();
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
    private volatile Cryptor _cryptor;
    private volatile CIOPacketCodec _nativeCodec;
    // A single native plaintext packet is leased to its execution role at a time.
    // Until the task finishes, C++ cannot call codec.next(), so
    // the codec's direct scratch buffer cannot be overwritten. No per-packet
    // heap byte[] copy or per-packet Runnable allocation is necessary.
    private volatile boolean _nativeGamePending;

    private ByteBuffer _nativePacketBuffer;
    private int _nativePacketLength;
    private l1j.server.server.model.Instance.L1PcInstance _nativePacketActor;
    private int _nativePacketStamp;
    private boolean _nativePacketOnIo;
    private long _nativePacketSequence;
    private final java.util.concurrent.atomic.AtomicInteger nativePacketPhase=new java.util.concurrent.atomic.AtomicInteger();
    private boolean nativeDirect;
    private long deadlinePriorProgress,deadlineStalledSince;
    private volatile boolean _handshakePlainPending;
    void initializeCipher(long seed) {
        final Cryptor cryptor = new Cryptor();
        cryptor.initKey(seed);
        // Native framing/crypto is mandatory for all admitted sessions.
        // If its DLL is unavailable, reject the connection rather than
        // silently mixing Java and C++ cryptographic states.
        final CIOPacketCodec nativeCodec = new CIOPacketCodec(_channel,seed);
        nativeDirect=true;
        _cryptor = cryptor;

        _nativeCodec = nativeCodec;
        _handshakePlainPending = true;
    }
    void clearCipher() {
        _handshakePlainPending = false;
        final CIOPacketCodec nativeCodec = _nativeCodec;
        _nativeCodec = null;

        _cryptor = null;
        if (nativeCodec != null) nativeCodec.close();
    }
    private Cryptor readCipher() { return _cryptor; }
    boolean encrypt(WriteBuffer buffer, int offset, int length) {
        if (_handshakePlainPending) {
            _handshakePlainPending = false;
            return true;
        }
        final CIOPacketCodec nativeCodec = _nativeCodec;
        final Cryptor cipher = _cryptor;
        if (nativeCodec == null && cipher == null) return true;
        try {
            if (nativeCodec != null) {
                nativeCodec.encrypt(buffer.getBuffer(), buffer.baseOffset() + offset, length);
            } else {
                cipher.encrypt(buffer, offset, length);
            }
            return true;
        } catch (Exception error) {
            LOG.log(Level.SEVERE, "[CIOUserSocket] packet encryption failed", error);
            return false;
        }
    }
    boolean decrypt(ReadBuffer buffer, int offset, int length) {
        final Cryptor cipher = _cryptor;
        if (cipher == null) return true;
        try {
            cipher.decrypt(buffer, offset, length);
            return true;
        } catch (Exception error) {
            LOG.log(Level.WARNING, "[CIOUserSocket] packet decryption failed", error);
            return false;
        }
    }
    private volatile CIOClient _client;
    private final String _remoteAddress;
    private final int _affinityShard;
    private volatile int _closing;
    private volatile int _disconnecting;
    // Progress is measured by completed positive WRITE bytes, not admissions.
    // The native network wheel performs the periodic deadline check.
    static final long NETWORK_WRITE_STALL_NANOS = TimeUnit.SECONDS.toNanos(30);
    private static final long NETWORK_GRACEFUL_CLOSE_NANOS = TimeUnit.SECONDS.toNanos(30);
    private static final long NETWORK_PARTIAL_READ_NANOS = TimeUnit.SECONDS.toNanos(30);
    private volatile long _networkWriteProgress;
    private volatile long _gracefulCloseStartedNs;
    // Zero for ordinary graceful close; a denial retains its original grace.
    // The WRITE completion and native deadline inspect this under _stateLock.
    private long _closeNotBeforeNs;
    interface NetworkDeadline {
        boolean scheduleEarlier(long delayMs);
    }
    private volatile NetworkDeadline _networkDeadline;

    void configureNativeDeadlines(boolean loginTimeout) {
        _networkDeadline=_channel::requestDeadline;
        if(!_channel.configureDeadlines(loginTimeout?60000:0))throw new IllegalStateException("Native network deadline admission failed");
    }
    void onSessionStateChanged(l1j.server.server.model.network.utill.ConnectionState state) {
        if(nativeDirect&&_closed==0&&_channel.isOpen())_channel.setUserState(state.ordinal());
    }
    int transportNetworkDeadline() {
        if(!isOpen())return 0;
        long now=System.nanoTime(),progress=networkWriteProgress();
        if(hasPendingNetworkSend()) {
            if(deadlineStalledSince==0||deadlinePriorProgress!=progress)deadlineStalledSince=now;
            if(now-deadlineStalledSince>=NETWORK_WRITE_STALL_NANOS)return -1;
        }else deadlineStalledSince=0;
        deadlinePriorProgress=progress;
        return networkDeadlineExpired(now)?-1:(int)nextNetworkDeadlineDelayMs(now,5000);
    }

    long nextNetworkDeadlineDelayMs(long now, long normalDelayMs) {
        _stateLock.lock();
        try {
            long delayMs = normalDelayMs;
            if (_closing != 0 && _gracefulCloseStartedNs != 0L) {
                final long remaining = NETWORK_GRACEFUL_CLOSE_NANOS
                        - (now - _gracefulCloseStartedNs);
                if (remaining > 0L)
                    delayMs = Math.min(delayMs, 1L + (remaining - 1L) / 1_000_000L);
            }
            if (_closing == 2 && _closeNotBeforeNs != 0L) {
                final long remaining = _closeNotBeforeNs - now;
                if (remaining > 0L)
                    delayMs = Math.min(delayMs, 1L + (remaining - 1L) / 1_000_000L);
            }
            return delayMs;
        } finally { _stateLock.unlock(); }
    }
    private volatile long _partialReadStartedNs;
    // Only SEND-watermark transitions touch the pause start under _stateLock.
    // Pause time is excluded from the current partial-frame deadline on resume.
    private volatile long _partialReadPausedAtNs;

    long networkWriteProgress() { return _networkWriteProgress; }
    boolean hasPendingNetworkSend() {
        _stateLock.lock();
        try { return _closed == 0 && (_outstandingWireBytes != 0 || _reservedDirect != 0); }
        finally { _stateLock.unlock(); }
    }
    boolean networkDeadlineExpired(long now) {
        final long closeAt = _gracefulCloseStartedNs;
        if (closeAt != 0L && now - closeAt >= NETWORK_GRACEFUL_CLOSE_NANOS) return true;
        // SEND resume shifts the partial-frame start and then publishes
        // _sendReadPaused=false while holding _stateLock. Read both values
        // under that same lock: separate volatile reads can combine the old
        // start with the new resumed state and falsely expire a live socket.
        // This runs on the existing deadline wheel, not the READ hot path.
        _stateLock.lock();
        try {
            if (_closing == 2 && _closeNotBeforeNs != 0L
                    && now - _closeNotBeforeNs >= 0L && isWriteIdleLocked()) return true;
            final long partialAt = _partialReadStartedNs;
            return partialAt != 0L && !_sendReadPaused
                    && now - partialAt >= NETWORK_PARTIAL_READ_NANOS;
        } finally {
            _stateLock.unlock();
        }
    }
    private volatile Runnable _onDrained;
    // The final packet is retained only until preceding accepted SENDs drain.
    // No extra worker, queue, or physical SEND lease is reserved for it.
    private WritablePacket<CIOClient> _deferredClosePacket;

    private final Closed _closedCallback;
    private CIOBufferPool.Lease[] _pending;
    private final int _maxQueuedFrames;
    // Volatile primitives avoid five standalone Atomic* objects per connection.
    // VarHandles preserve CAS/getAndAdd semantics when completion threads race.
    private volatile int _closed;
    private volatile int _closeNotified;

    private static final int SOCKET_SLOTS = 512;
    private static final int SOCKET_MASK = SOCKET_SLOTS - 1;
    private static final int SOCKET_REJECT_AT = SOCKET_SLOTS - 8;
    private static final long SOCKET_SEND_START = 1L;
    private static final long SOCKET_WRITE_COMPLETE = 2L;
    private static final long SOCKET_SEND_ENQUEUE = 3L;
    private static final long SOCKET_CANCELLED = 4L;
    // The existing serializer ring can carry an 8-byte action in one long.
    // No SEND lease is rented while an earlier foreign producer ticket is unsealed.
    private static final long SOCKET_PACKED_ACTION = 5L;
    private final long[] _socketCallbacks = new long[SOCKET_SLOTS];
    private final long[] _socketArguments = new long[SOCKET_SLOTS];
    private final CIOBufferPool.Lease[] _socketSendArguments = new CIOBufferPool.Lease[SOCKET_SLOTS];

    private volatile long _socketTicket;
    // The producer may inspect whether a staged ticket was already claimed.
    private volatile long _socketHead;
    private volatile int _socketPending;
    private volatile int _socketDraining;
    // Optional SEND_START ticket coalescing: a reusable one-bit wake-up. The
    // serializer clears it only while owning the socket callback token.
    private volatile int _socketStartPending;
    private volatile Thread _socketOwner;
    // Owner-only scope: a single complete READ frame can reuse the otherwise
    // idle socket serializer. No additional ring slot or worker is allocated.
    private boolean _readSocketOwnerActive;
    // Java NIO.2 may complete synchronously inside the write() invocation.
    // Such a callback must not retire its pooled ByteBuffer until write() returns.
    private volatile boolean _socketPosting;
    // Owner-thread-only: a completion observed inside write() is consumed by
    // the serializer loop after write() returns, without a second ring ticket.
    // Never consume it from the completion handler or recurse into the pump.
    private long _inlineWriteCompletion;
    // A completed WRITE already committed before a later SEND reservation.
    // Published by the callback, consumed only by the socket serializer owner.
    // Unlike a ring ticket this cannot sit behind an unpublished SEND head.
    private volatile long _prefixWriteCompletion;
    // The current callback has already advanced _socketHead but is still
    // included in _socketPending until its finally block runs.
    private boolean _socketExecutingTicket;
    // A socket issues at most one outstanding WRITE. The mailbox rejects a
    // duplicate completion before its serializer ticket can run.
    private volatile long _writeEvent;
    // Diagnostic timestamps: one in-flight WRITE per socket, no event allocation.
    // Windows IOCP diagnostic stages. One READ can be outstanding per socket.
    private static final int WRITE_DONE = 1;
    private static final int WRITE_FAILED = 2;
    private static final int GATHER_DONE = 3;
    private static final int GATHER_FAILED = 4;
    private volatile int _readKick;
    // Defer automatic SEND burst flushing until the current READ
    // buffer pass ends, but only if that buffer holds multiple complete frames.
    // Forced flushSendBurst(), close and READ-pressure boundaries stay intact.
    // Most window-1 game requests are below this size. Do not inspect the
    // packet header or enter a burst scope unless the READ holds enough data
    // for meaningful cross-request batching.
    private static final int READ_PASS_MIN_BUFFERED_BYTES = 64;
    // P8: a completed READ may directly claim the idle READ pump token.
    // The already-owned/inline callback path still publishes its mailbox event.
    private static final boolean READ_WRITE_OWNER_FUSION = true;
    private static final int FUSED_READ_MAX_BYTES = 256;
    // READ completion is consumed only by the existing per-socket READ pump.
    // 0=empty, 1=failure, >=2=successful completion (transferred bytes + 2).
    // This keeps _readPending and zero-completion handling off foreign I/O threads.
    private volatile int _readEvent;
    private static final VarHandle CLOSED, CLOSE_NOTIFIED, READ_KICK, READ_ACTIVE,
            WRITE_EVENT, READ_EVENT, SOCKET_TICKET, SOCKET_PENDING, SOCKET_DRAINING,
            SOCKET_START_PENDING,
            DISCONNECTING, CLOSING, ON_DRAINED, PARTIAL_READ_STARTED;
    private static final VarHandle SOCKET_CALLBACK_SLOT = MethodHandles.arrayElementVarHandle(long[].class);
    private static final VarHandle SOCKET_ARGUMENT_SLOT = MethodHandles.arrayElementVarHandle(long[].class);
    static {
        try {
            final MethodHandles.Lookup lookup = MethodHandles.lookup();
            CLOSED = lookup.findVarHandle(CIOUserSocket.class, "_closed", int.class);
            CLOSE_NOTIFIED = lookup.findVarHandle(CIOUserSocket.class, "_closeNotified", int.class);
            SOCKET_TICKET = lookup.findVarHandle(CIOUserSocket.class, "_socketTicket", long.class);
            SOCKET_PENDING = lookup.findVarHandle(CIOUserSocket.class, "_socketPending", int.class);
            SOCKET_DRAINING = lookup.findVarHandle(CIOUserSocket.class, "_socketDraining", int.class);
            SOCKET_START_PENDING = lookup.findVarHandle(CIOUserSocket.class, "_socketStartPending", int.class);
            WRITE_EVENT = lookup.findVarHandle(CIOUserSocket.class, "_writeEvent", long.class);
            READ_KICK = lookup.findVarHandle(CIOUserSocket.class, "_readKick", int.class);
            READ_EVENT = lookup.findVarHandle(CIOUserSocket.class, "_readEvent", int.class);
            READ_ACTIVE = lookup.findVarHandle(CIOUserSocket.class, "_readActive", int.class);
            DISCONNECTING = lookup.findVarHandle(CIOUserSocket.class, "_disconnecting", int.class);
            CLOSING = lookup.findVarHandle(CIOUserSocket.class, "_closing", int.class);
            ON_DRAINED = lookup.findVarHandle(CIOUserSocket.class, "_onDrained", Runnable.class);
            PARTIAL_READ_STARTED = lookup.findVarHandle(CIOUserSocket.class, "_partialReadStartedNs", long.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    // One reusable flush list per CIO owner thread. This is not a packet queue:
    // leases remain owned by their connections, and no additional worker is used.
    private static final ThreadLocal<SendBurst> SEND_BURST = new ThreadLocal<>();
    private SendBurst _burstOwner;
    // Set under the socket state lock only when a burst first attaches.
    private int _burstWriteStartBytes = BURST_MAX_BYTES;
    // One producer-owned, not-yet-published packed SEND ticket per socket.
    // Reserved in FIFO order but never visible to the serializer until sealed.
    // No extra queue/worker; the existing SendBurst scope seals it in finally.
    // Experimental A/B gate: keep the verified low-load path as the default
    // until matched throughput/p99/CPU runs establish a global win.
    // Experimental: avoid a separate SEND_START ticket for foreign callers.
    // Retain the original ticket path unless a matched correctness/CPU trial
    // explicitly opts in; no new worker or packet queue is introduced.
    private static final boolean COALESCE_SEND_START = true;
    // Retire a synchronous owner-thread WRITE callback only after write()
    // returns. Profiling keeps the old ticket path for comparable traces.
    private static final boolean INLINE_WRITE_COMPLETION = true;
    // Experimental P6-A: one already-published first Action SEND may be
    // consumed by the existing SendBurst.flush() instead of entering the
    // serializer twice. Disabled until matched TCP performance checks pass.
    private static final boolean DEFER_OWNED_BURST_DRAIN = true;
    // P6-B A/B: select the sole ready WRITE in acceptStagedSend's existing
    // admission lock. Never invoke the NIO.2 provider under that lock.
    // Keep the gather, queued, and already-pending SEND_START paths unchanged.
    private static final boolean FUSE_STAGED_WRITE_SELECTION = true;
    private CIOBufferPool.Lease _producerLease;
    private long _producerTicket;
    // Only payload reservations, not WRITE_COMPLETE/SEND_START control tickets,
    // break producer-local packing. All payload reservations hold _stateLock.
    private int _payloadOrderEpoch;
    private int _producerOrderEpoch;
    private static final int BURST_MAX_BYTES = 8192;
    // READ-pass-only WRITE start threshold. Does not alter action/producer packing.
    private static final int READ_PASS_WRITE_START_BYTES = 32 * 1024;
    // Scatter/gather covers multiple independent 16K leases; this is not a lease size.
    private static final int GATHER_MAX_BYTES = 64 * 1024;
    private static final int BURST_INITIAL_SOCKETS = 32;
    private static final int BURST_MAX_SOCKETS = 256;

    private static final class SendBurst {
        // A burst never migrates between threads: the owning thread is fixed
        // when its ThreadLocal holder is first created.
        final Thread thread = Thread.currentThread();
        CIOUserSocket[] sockets = new CIOUserSocket[BURST_INITIAL_SOCKETS];
        int size;
        int depth;
        int readPassDepth;
        boolean flushing;

        boolean attach(CIOUserSocket socket) {
            if (flushing) return false;
            if (socket._burstOwner == this) return true;
            // Grow only when a large AOI burst actually needs more sockets.
            // The hard cap bounds how long a CIO owner can defer initial WRITEs.
            if (size == sockets.length) {
                if (size >= BURST_MAX_SOCKETS) return false;
                sockets = java.util.Arrays.copyOf(sockets,
                        Math.min(BURST_MAX_SOCKETS, sockets.length << 1));
            }
            sockets[size++] = socket;
            socket._burstOwner = this;

            return true;
        }

        void flush() {
            // Single-request dispatch ends with a second flush check. Avoid
            // the empty callback scope without changing ticket publication.
            if (flushing || size == 0) return;
            flushing = true;
            final int n = size;
            size = 0;
            try {
                for (int i = 0; i < n; i++) {
                    final CIOUserSocket socket = sockets[i];
                    sockets[i] = null;
                    final boolean publish;
                    boolean claimed = false;
                    CIOBufferPool.Lease fusedWrite = null;
                    boolean fusionFailed = false;
                    socket._stateLock.lock(); try {
                        publish = socket.publishProducerLeaseLocked();
                        final boolean owned = socket._burstOwner == this;
                        if (owned) {
                            socket._burstOwner = null;
                            socket._burstWriteStartBytes = BURST_MAX_BYTES;
                        }
                        if (owned && !publish) {
                            // A ticket-free fused READ already owns the serializer.
                            // Only select a committed sole lease; never steal the
                            // pending counter or owner release of a ticket callback.
                            // Anything queued/gathering/unpublished uses the old path.

                                claimed = socket.claimSingleFlushOwnerLocked();
                                if (claimed) {
                                    try { fusedWrite = socket.commitSingleFlushSendLocked(); }
                                    catch (Throwable failure) { fusionFailed = true; }
                                }
                            
                        }
                    } finally { socket._stateLock.unlock(); }
                    if (claimed) {
                        // The sole FIFO SEND ticket was consumed while locked.
                        // Provider write(), including any inline completion,
                        // must run after unlocking and before owner release.
                        try {
                            if (fusionFailed) socket.close();
                            else if (fusedWrite != null) {
                                // OneLock has already consumed the FIFO head,
                                // but its pending count remains until write()
                                // returns. Treat it as the executing ticket so
                                // an inline completion can be deferred locally.
                                if (INLINE_WRITE_COMPLETION) socket._socketExecutingTicket = true;
                                try { socket.postWrite(fusedWrite); }
                                finally {
                                    if (INLINE_WRITE_COMPLETION) socket._socketExecutingTicket = false;
                                }
                            }
                        } catch (Throwable failure) {
                            socket.close();
                        } finally {
                            if (fusedWrite != null)
                                SOCKET_PENDING.getAndAdd(socket, -1);
                            socket.drainSocketCallbacksAcquired();
                        }
                        // Unexpected eligibility loss is handled by the
                        // original FIFO drainer; retain the old final kick.
                        if (fusedWrite == null && !fusionFailed) {
                            try { socket.kickWrite(); }
                            catch (Throwable failure) { socket.close(); }
                        }
                    } else {
                        // The reserved FIFO ticket must be visible before WRITE is kicked.
                        if (publish) socket.drainSocketCallbacks();
                        // May have been posted by another producer; kickWrite is idempotent.
                        try { socket.kickWrite(); }
                        catch (Throwable failure) { socket.close(); }
                    }
                }
            } finally {
                flushing = false;
            }
        }
    }

    /** Scope only current direct opcode dispatch; leave external producers on immediate SEND. */
    public static void beginSendBurst() {
        SendBurst burst = SEND_BURST.get();
        if (burst == null) {
            burst = new SendBurst();
            SEND_BURST.set(burst);
        }
        ++burst.depth;
    }

    private static void beginReadPassSendBurst() {
        beginSendBurst();
        ++SEND_BURST.get().readPassDepth;
    }

    private static void endReadPassSendBurst() {
        final SendBurst burst = SEND_BURST.get();
        if (burst == null || burst.readPassDepth <= 0)
            throw new IllegalStateException("unbalanced READ-pass SEND burst");
        --burst.readPassDepth;
        endSendBurst();
    }

    public static void endSendBurst() {
        final SendBurst burst = SEND_BURST.get();
        if (burst == null || burst.depth <= 0)
            throw new IllegalStateException("unbalanced SEND burst");
        if (--burst.depth == 0) burst.flush();
    }

    /** Flush the current game command without discarding the owner-local burst scope. */
    public static void flushSendBurst() {
        final SendBurst burst = SEND_BURST.get();
        if (burst != null && burst.depth != 0) burst.flush();
    }

    /* Queue and active WRITE mutations use _stateLock, never a global pool lock. */
    private int _head;
    private int _tail;
    private int _queued;
    // Frames admitted to the 512-slot serializer but not yet attached to SEND.
    private int _stagedSends;
    // One still-unconsumed SEND_ENQUEUE can absorb consecutive small packets
    // before the socket FIFO owner encrypts it. State-lock protected. The
    // later tickets remain CANCELLED FIFO positions, never bypassed.
    private CIOBufferPool.Lease _stagedPacketPack;
    private long _stagedPacketFirstTicket;
    private long _stagedPacketLastTicket;
    // Byte-based connection-local backpressure, independent of the physical lease bound.
    // Counts all committed wire bytes including gather writes and packed frames.
    private static final int MAX_PENDING_WIRE_BYTES = 4 * 1024 * 1024;
    // Flow-control stops inbound processing before a slow client accumulates
    // hundreds of 16K leases. The wire-byte and physical SEND lease caps are
    // preserved; logical frame pressure is READ backpressure, not a lease cap.
    private static final boolean SEND_READ_WATERMARK = true;
    private static final int SEND_READ_HIGH_WATER_BYTES = 256 * 1024;
    private static final int SEND_READ_LOW_WATER_BYTES = SEND_READ_HIGH_WATER_BYTES / 2;
    private static final int SEND_READ_HIGH_WATER_FRAMES = 256;
    private static final int SEND_READ_LOW_WATER_FRAMES = SEND_READ_HIGH_WATER_FRAMES / 2;
    private int _outstandingWireBytes;
    // Uncommitted direct producers own pooled leases too. Include their maximum
    // wire sizes in admission and READ watermark decisions until commit/abort.
    private int _reservedWireBytes;
    private int _outstandingFrames;
    private volatile boolean _sendReadPaused;

    /** Always invoked with this socket's state lock held. */
    private boolean canAdmitBytes(int count) {
        return count > 0 && count <= MAX_PENDING_WIRE_BYTES - _outstandingWireBytes - _reservedWireBytes;
    }

    /** Physical SEND leases under _stateLock, including every in-flight gather member. */
    private int physicalSendLeasesLocked() {
        return _queued + _reservedDirect + _stagedSends
                + (_writing == null ? 0 : _gatherCount > 0 ? _gatherCount : 1);
    }

    private boolean physicalSendFullLocked() {
        return physicalSendLeasesLocked() >= _maxQueuedFrames + 1;
    }

    // _maxQueuedFrames bounds physical SEND leases (all gather members plus
    // queued leases and reservations), not packets packed into those leases. The
    // logical packet count is separately tracked for READ backpressure.  Each
    // accepted frame consumes at least HEADER_BYTES + 1 wire bytes, so the
    // MAX_PENDING_WIRE_BYTES admission cap also strictly bounds that count.

    private void bytesAdmitted(int count) {
        bytesAdmittedBatch(count, 1);
    }

    /** Caller holds _stateLock. A validated batch publishes one watermark change. */
    private void bytesAdmittedBatch(int wireBytes, int frames) {
        _outstandingWireBytes += wireBytes;
        _outstandingFrames += frames;
        updateSendReadPauseLocked();
    }

    /** Under the state lock, accounting both published and uncommitted frames. */
    private boolean updateSendReadPauseLocked() {
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
    private boolean bytesReleased(int count, int frames) {
        _outstandingWireBytes -= count;
        _outstandingFrames -= frames;
        if (_outstandingWireBytes < 0 || _outstandingFrames < 0)
            throw new IllegalStateException("negative outstanding SEND accounting");
        return updateSendReadPauseLocked();
    }

    // Reserved producer slots for existing direct-frame serializers. A reservation
    // survives the serializer and is consumed atomically by commit or abort.
    private int _reservedDirect;
    private volatile CIOBufferPool.Lease _writing;
    // State-lock-owned first Action lease: only while a P6-A SEND_ENQUEUE is
    // published but has not yet been accepted by its FIFO serializer callback.
    // Do not store a lease after commit: it may be recycled by the pool.
    private CIOBufferPool.Lease _stagedBurstAction;
    // Per-connection fixed gather array: no byte-copy and no batch allocation.
    // Native gathering WRITE maps to WSABUF arrays on Windows IOCP.
    // No new staging buffers or copies: select a wider gather only when a
    // connection already has enough committed SEND leases. Default unchanged.
    private static final int GATHER_MAX_FRAMES = 8;
    // On a partial asynchronous WRITE the current lease is still the oldest
    // committed output. Reposting from the existing serializer avoids
    // reacquiring the state lock, without adding a completion worker or
    // bypassing any reserved FIFO ticket.
    // P19: retain the serializer-driven partial WRITE progression; no runtime flag.
    // Medium S-packets share the same ordered wire batch when space permits.
    private static final int PACK_MAX_FRAME_BYTES = 2048;
    private CIOBufferPool.Lease[] _gatherFrames;
    private ByteBuffer[] _gatherBuffers;
    private int _gatherCount;
    private int _zeroWriteCompletions;
    private int _zeroReadCompletions;

    /* READ lease is released only after the last completion callback, never during a pending read. */
    private volatile int _readActive;
    private CIOBufferPool.Lease _reading;
    private volatile boolean _readReleased;
    private volatile boolean _readPending;
    // Only the READ pump touches this; preserves buffered frames if an ACK races pause.
    private boolean _readYielded;
    // READ pump only. Do not add a ThreadLocal.get() to single-frame dispatch.
    private boolean _readPassBurstActive;

    // Native completion publishes into the existing READ pump, without a
    // JDK asynchronous channel or provider adapter.
    void transportReadCompleted(int bytes) { publishReadCompletion(bytes); }

    private void publishReadCompletion(int bytes) {

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

    void transportWriteCompleted(int bytes) {
        publishWriteCompletion(WRITE_DONE, WRITE_FAILED, bytes);
    }
    void transportGatherCompleted(long bytes) {
        publishWriteCompletion(GATHER_DONE, GATHER_FAILED, bytes);
    }

    /**
     * A WRITE's pending I/O keeps its lease attached to this socket. The native IOCP
     * completion thread only publishes the result; the existing WRITE pump is
     * the sole executor of completion, next-send and buffer-release transitions.
     * There is only one in-flight WRITE per connection, so a single completion
     * mailbox is sufficient. The existing bounded _pending ring continues to
     * retain queued outbound buffers without a redundant callback queue.
     */
    private void publishWriteCompletion(int okKind, int failKind, long transferred) {

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
    private boolean deferOwnerInlineWriteCompletion(long event) {
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
    private boolean hasUnpublishedSendBehindWriting(int active) {
        if (_writing == null || !_writing._ioPending || _closed != 0
                || (int) SOCKET_PENDING.getVolatile(this) <= active) return false;
        final int slot = (int) _socketHead & SOCKET_MASK;
        return (long) SOCKET_CALLBACK_SLOT.getAcquire(_socketCallbacks, slot) == 0L;
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
    private boolean tryRetireCommittedWriteAcrossUnpublishedSend(long event) {
        // The common inline completion was already handled by the owner.
        // A FOREIGN completion can arrive before write() returns while the
        // posting owner is blocked in the provider and a later SEND ticket
        // is reserved but unpublished. Hand off only the notification, never
        // its lease. Keep this rare case outside the ordinary serializer CAS.
        if (_socketPosting) {
            if (_closed != 0 || _socketOwner == Thread.currentThread()
                    || (int) SOCKET_PENDING.getAcquire(this) == 0
                    || (int) SOCKET_DRAINING.getAcquire(this) == 0) return false;
            final CIOBufferPool.Lease active = _writing;
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
        final CIOBufferPool.Lease active = _writing;
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
    private void drainWriteCompletion(long event) {

        final int kind = (int) (event & 7L);
        if (kind == WRITE_DONE || kind == GATHER_DONE)
            _networkWriteProgress += event >>> 3;
        if (kind == WRITE_FAILED) {
            final CIOBufferPool.Lease lease = _writing;
            if (lease != null) completeWrite(lease, false);
            else close();
            return;
        }
        if (kind == GATHER_FAILED) {
            completeGather(false);
            return;
        }
        if (kind == WRITE_DONE) {
            final CIOBufferPool.Lease lease = _writing;
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
            boolean remaining = false;
            for (int i = 0; i < _gatherCount; i++) {
                if (_gatherBuffers[i].hasRemaining()) {
                    remaining = true;
                    break;
                }
            }
            if (remaining) {
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

    public CIOUserSocket(CTransportSocket channel, CIOBufferPool pool,
                         CIOThreadCallback scheduler, String remoteAddress,
                         int affinityShard, int maxQueuedFrames, Closed closedCallback) {
        if (channel == null || pool == null || scheduler == null
                || affinityShard < 0 || affinityShard >= scheduler.shardCount()
                || maxQueuedFrames < 1)
            throw new IllegalArgumentException("invalid game socket arguments");
        _channel = channel;
        _pool = pool;
        _remoteAddress = remoteAddress == null ? "Unknown" : remoteAddress;
        _affinityShard = affinityShard;
        _closedCallback = closedCallback;
        _maxQueuedFrames = maxQueuedFrames;
        _channel.bindUserSocketOwner(this);
    }

    /** Bind the L1 session before the first native READ is posted. */


    void bindClient(CIOClient client) {
        if (client == null || client.getConnection() != this || _client != null || _closed != 0)
            throw new IllegalStateException("socket client already bound or closed");
        _client = client;
    }

    private final java.util.concurrent.atomic.AtomicBoolean closeStarted = new java.util.concurrent.atomic.AtomicBoolean();
    private volatile boolean clientCleanupComplete;
    void onTransportClosing() {
        if (!closeStarted.compareAndSet(false, true)) return;
        CIOClient client = _client;
        if (client != null) client.onTransportClosing();
        cancelQueuedNativePacket();
        if (_closedCallback != null) {
            try { _closedCallback.onClosing(this); }
            catch (Throwable failure) { LOG.log(Level.WARNING, "User-socket closing observer failed", failure); }
        }
    }
    boolean isCloseStarted() { return closeStarted.get(); }
    void onTransportTerminated() {
        close();
        notifyClosedWhenReleased();
    }
    boolean isClientCleanupComplete() { return clientCleanupComplete; }
    void onClientCleanupComplete() {
        clientCleanupComplete = true;
        if (_closedCallback != null) {
            try { _closedCallback.onCleanupComplete(this); }
            catch (Throwable failure) { LOG.log(Level.WARNING, "User-socket cleanup observer failed", failure); }
        }
    }

    CIOClient client() { return _client; }

    public String getRemoteAddress() { return _remoteAddress; }
    public int affinityShard() { return _affinityShard; }

    public boolean isClientConnected() {
        return isOpen() && _closing == 0 && _disconnecting == 0;
    }

    public int getEstimateQueueSize() { return pendingSendFrames(); }
    public void read() { startRead(); }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public void enqueuePacket(WritablePacket packet) {
        if (packet == null || !isClientConnected()) return;
        final CIOClient client = _client;
        if (client == null || !tryWritePacket(client, packet)) close();
    }

    /**
     * Graceful close: 0=open, 1=waiting to admit final, 3=encoding final,
     * 2=draining. A WRITE completion must never close before the final encoder
     * attaches its serializer ticket. This path is distinct from
     * close(), which remains the immediate error/cancellation path.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void requestClientClose(WritablePacket packet) {
        if (_disconnecting != 0 || !isOpen()
                || !CLOSING.compareAndSet(this, 0, 1)) return;
        _gracefulCloseStartedNs = System.nanoTime();
        if (_client == null) {
            close();
            return;
        }
        // Wait for previously accepted SENDs before admitting the final packet.
        // Saturated local slots cannot cause an error-close that discards the
        // accepted prefix. The final packet is encoded exactly once.
        boolean publish;
        _stateLock.lock();
        try {
            // close() may win after the open-state CAS and before this lock.
            // Never reattach a final packet after forced-close cleanup.
            if (_closed != 0) return;
            _deferredClosePacket = packet;
            publish = publishProducerLeaseLocked();
        } finally { _stateLock.unlock(); }
        if (publish) drainSocketCallbacks();
        flushSendBurst();
        if (packet == null) CLOSING.setRelease(this, 2);
        kickWrite();
        onSendProgress();
    }

    public boolean sendAction(int objectId, int actionId) {
        if (!isClientConnected()) return false;
        if (tryWriteAction(objectId, actionId)) return true;
        close();
        return false;
    }

    /** Drain accepted SENDs and retain a bounded minimum grace before closing. */
    public void requestClientCloseAfter(long delayMs) {
        if (delayMs < 0L || delayMs > 30_000L)
            throw new IllegalArgumentException("close grace must be between 0 and 30000 ms");
        final NetworkDeadline deadline;
        final boolean publish;
        _stateLock.lock();
        try {
            if (_closed != 0 || _disconnecting != 0
                    || !CLOSING.compareAndSet(this, 0, 1)) return;
            final long now = System.nanoTime();
            _gracefulCloseStartedNs = now;
            _closeNotBeforeNs = now + TimeUnit.MILLISECONDS.toNanos(delayMs);
            publish = publishProducerLeaseLocked();
            deadline = _networkDeadline;
            CLOSING.setRelease(this, 2);
        } finally { _stateLock.unlock(); }
        if (publish) drainSocketCallbacks();
        flushSendBurst();
        kickWrite();
        onSendProgress();
        // Reuse the already installed weak-reference task. Bring its normal
        // 5-second check forward only when this grace period requires it.
        // Failed deadline admission must not leave an orphaned denied peer.
        if (_closed == 0 && (deadline == null
                || !deadline.scheduleEarlier(Math.max(1L, delayMs)))) close();
    }

    public boolean beginClientDisconnect() {
        if (!DISCONNECTING.compareAndSet(this, 0, 1)) return false;
        if (_gracefulCloseStartedNs == 0L) _gracefulCloseStartedNs = System.nanoTime();
        _closing = 1;
        ON_DRAINED.setVolatile(this, null);
        return true;
    }

    public void finishClientDisconnect() { close(); }
    public int drainOnOwner() { return 0; }
    public void onSendDrainedOwner() { onSendProgress(); }
    public boolean hasDrainWorkOnOwner() { return getEstimateQueueSize() != 0; }
    public void requestDrain() { /* WRITE completion pump owns the drain. */ }

    public boolean whenWriteDrained(Runnable callback) {
        if (callback == null || _closed != 0) return false;
        ON_DRAINED.setVolatile(this, callback);
        if (_closed != 0) {
            ON_DRAINED.compareAndSet(this, callback, null);
            return false;
        }
        if (isWriteIdle()) onSendProgress();
        return true;
    }

    public void cancelWriteDrainCallback() {
        ON_DRAINED.setVolatile(this, null);
    }

    private void onSendProgress() {
        // Admission is blocked during close. An accepted encoder reservation
        // remains visible until it has attached its FIFO ticket.
        if (_closing == 0 && ON_DRAINED.getVolatile(this) == null) return;
        WritablePacket<CIOClient> finalPacket = null;
        Runnable callback = null;
        _stateLock.lock();
        try {
            if (!isWriteIdleLocked() || _closed != 0) return;
            if (_closing == 1 && _deferredClosePacket != null) {
                finalPacket = _deferredClosePacket;
                _deferredClosePacket = null;
                // Prevent another completion thread from encoding it twice.
                _closing = 3;
            } else if (_closing != 1 && _closing != 3) {
                callback = (Runnable) ON_DRAINED.getAndSet(this, null);
            }
        } finally { _stateLock.unlock(); }
        if (finalPacket != null) {
            final boolean accepted;
            try {
                accepted = tryWritePacketInternal(_client, finalPacket, true);
            } catch (Throwable failed) {
                close();
                return;
            }
            if (!accepted) {
                // At this point the earlier accepted prefix has drained.
                close();
                return;
            }
            CLOSING.setRelease(this, 2);
            kickWrite();
            onSendProgress();
            return;
        }
        if (callback != null) callback.run();
        // The callback may have admitted a new SEND. The earlier idle
        // snapshot is not a valid close decision: recheck after it returns.
        if (_closing == 2) {
            boolean closeNow;
            _stateLock.lock();
            try {
                closeNow = _closed == 0 && _closing == 2 && isWriteIdleLocked()
                        && (_closeNotBeforeNs == 0L
                                || System.nanoTime() - _closeNotBeforeNs >= 0L);
            }
            finally { _stateLock.unlock(); }
            if (closeNow) close();
        }
    }

    /** Acquire a packet-writing slot; false/null means backpressure, not a silent successful SEND. */
    public CIOBufferPool.Lease beginOutboundFrame(int maxWireBytes) {
        return (_closed != 0) ? null : _pool.acquireSend(maxWireBytes);
    }

    /**
     * Direct-frame entry point compatible with CIOUserSocket's existing packet API.
     * Reserve one bounded queue position before exposing the writable buffer.
     * The reusable handle is owned by its pooled lease, not allocated per packet.
     */
    public CIOFrame beginDirectFrame(int maxWireBytes) {
        // This connection may have hundreds of packed CANCELLED tickets but
        // only one or two physical SEND leases. Give its ready FIFO prefix an
        // opportunity to retire before applying the serializer hard cap.
        if ((int) SOCKET_PENDING.getAcquire(this) >= SOCKET_REJECT_AT - 32)
            drainPublishedSocketCallbacks();
        int slot = -1;
        long ticket = -1L;
        boolean refused = false;
        boolean publishProducer = false;
        _stateLock.lock();
        try {
            if (_closed != 0 || _closing != 0) return null;
            if (maxWireBytes <= CIOBufferPool.HEADER_BYTES
                    || maxWireBytes > CIOBufferPool.MAX_WIRE_BYTES
                    || !canAdmitBytes(maxWireBytes)) {
                rejectSend(REJECT_WIRE_BYTES);
                refused = true;
            } else {
                if (physicalSendFullLocked()) {
                    // Compaction is only an admission recovery step; success
                    // MUST continue into ordinary ticket/byte reservation.
                    // The old else-if silently skipped that reservation and
                    // made commitDirectFrame() decrement the counters below 0.
                    compactReadyStagedPacketsLocked();
                    if (physicalSendFullLocked()) {
                        rejectSend(REJECT_LOCAL_FRAMES);
                        refused = true;
                    }
                }
                // A frame reserves FIFO BEFORE it is encoded, even when
                // the producer is also the current callback owner.
                if (!refused) for (;;) {
                    final int pending = (int) SOCKET_PENDING.getVolatile(this);
                    if (pending >= SOCKET_REJECT_AT) {
                        rejectSend(REJECT_SERIALIZER);
                        refused = true;
                        break;
                    }
                    if (SOCKET_PENDING.compareAndSet(this, pending, pending + 1)) break;
                }
                if (!refused) {
                    ticket = (long) SOCKET_TICKET.getAndAdd(this, 1L);
                    slot = (int) ticket & SOCKET_MASK;
                }
                if (!refused) {
                    if (_producerLease != null) {
                        ++_payloadOrderEpoch;
                        publishProducer = publishProducerLeaseLocked();
                    }
                    ++_reservedDirect;
                    _reservedWireBytes += maxWireBytes;
                    updateSendReadPauseLocked();
                }
            }
        } finally { _stateLock.unlock(); }
        if (publishProducer) drainSocketCallbacks();
        if (refused) {
            // A packet that cannot be admitted must never look like a successful SEND.
            close();
            return null;
        }

        CIOBufferPool.Lease lease = null;
        try {
            lease = beginOutboundFrame(maxWireBytes);
            if (lease != null) {
                final CIOFrame frame = lease.beginDirectFrame(this);
                frame.setReservation(maxWireBytes, slot, ticket);
                return frame;
            }
            rejectSend(REJECT_POOL);
        } catch (Throwable failure) {
            rejectSend(REJECT_ENCODING);
            // Preserve the original failure, but release the ticket and lease.
            rollbackDirectBegin(maxWireBytes, slot, lease);
            close();
            throw failure;
        }
        rollbackDirectBegin(maxWireBytes, slot, lease);
        close();
        return null;
    }

    private void cancelReservedTicketLocked(int slot) {
        if (slot < 0) return;
        _socketSendArguments[slot] = null;
        SOCKET_ARGUMENT_SLOT.setRelease(_socketArguments, slot, 0L);
        SOCKET_CALLBACK_SLOT.setRelease(_socketCallbacks, slot, SOCKET_CANCELLED);
    }

    private void rollbackDirectBegin(int bytes, int slot,
            CIOBufferPool.Lease lease) {
        boolean resumeRead;
        _stateLock.lock();
        try {
            --_reservedDirect;
            _reservedWireBytes -= bytes;
            cancelReservedTicketLocked(slot);
            if (lease != null && lease.reserved()) abortOutboundFrame(lease);
            resumeRead = updateSendReadPauseLocked();
        } finally { _stateLock.unlock(); }
        if (slot >= 0) drainPublishedSocketCallbacks();
        if (resumeRead) kickRead();
        notifyClosedWhenReleased();
    }

    public boolean commitDirectFrame(CIOFrame frame) {
        if (frame == null) return false;
        final CIOBufferPool.Lease lease = frame.ioLease(this);
        final int reservedBytes = frame.reservedBytes();
        final int slot = frame.reservedTicket();
        final long ticket = frame.reservedTicketId();
        boolean accepted = false;
        boolean packed = false;
        boolean resumeRead = false;
        _stateLock.lock();
        try {
            --_reservedDirect;
            _reservedWireBytes -= reservedBytes;
            try {
                final int length = lease.writer().position();
                // The public DirectFrame writer must retain its original
                // per-packet bound even when its committed lease will later
                // have space for packed frames.
                if (length > CIOBufferPool.HEADER_BYTES && length <= reservedBytes) {
                    packed = packAdjacentStagedPacketLocked(lease,
                            ticket, slot, true);
                    accepted = packed || stageOutboundFrameLocked(lease, false, slot);
                    if (accepted) {
                        // Finish the reusable DirectFrame BEFORE releasing its
                        // lease: the pool may immediately hand it to another
                        // socket on a different thread.
                        frame.finishIO();
                        if (packed) {
                            _pool.release(lease);
                        } else {
                            // Only a committed, unclaimed small SEND lease can
                            // be expanded for later FIFO packing. No producer
                            // can still write to this buffer after finishIO().
                            if (lease.reserved()
                                    && lease.buffer().capacity() == CIOBufferPool.SMALL_SEND_BYTES)
                                promoteDirectPackCapacityLocked(lease);
                            _stagedPacketPack = lease;
                            _stagedPacketFirstTicket = ticket;
                            _stagedPacketLastTicket = _stagedPacketFirstTicket;
                            if (_stagedSends >= Math.max(8, _maxQueuedFrames >>> 2)) {
                                compactReadyStagedPacketsLocked();
                                packed = (long) SOCKET_CALLBACK_SLOT.getAcquire(
                                        _socketCallbacks, slot) == SOCKET_CANCELLED;
                            }
                        }
                    }
                }
            } finally {
                if (!accepted) {
                    ++_reservedDirect;
                    _reservedWireBytes += reservedBytes;
                }
                resumeRead = updateSendReadPauseLocked();
            }
        } finally { _stateLock.unlock(); }
        if (accepted) {
            if (packed) drainPublishedSocketCallbacks();
            else finishStagedSend(lease, false);
        }
        if (resumeRead && _closed == 0) kickRead();
        if (!accepted) {
            // The caller's finally aborts the still-owned frame/ticket.
            close();
        }
        return accepted;
    }

    public void abortDirectFrame(CIOFrame frame) {
        if (frame == null) return;
        final CIOBufferPool.Lease lease = frame.ioLease(this);
        final int bytes = frame.reservedBytes();
        final int slot = frame.reservedTicket();
        boolean resumeRead;
        _stateLock.lock();
        try {
            --_reservedDirect;
            _reservedWireBytes -= bytes;
            frame.finishIO();
            cancelReservedTicketLocked(slot);
            abortOutboundFrame(lease);
            resumeRead = updateSendReadPauseLocked();
        } finally { _stateLock.unlock(); }
        if (slot >= 0) drainPublishedSocketCallbacks();
        if (resumeRead) kickRead();
        // Direct send failure is fatal for packet-stream consistency: never
        // silently lose an attack/HP update when callers ignore the boolean.
        if (_closed == 0) close();
        notifyClosedWhenReleased();
    }

    /**
     * Allocate the large pending ring only when a connection actually has a burst.
     * Called with the connection state lock held. The ring grows at most to the
     * original configured bound; no extra queue or per-frame nodes are created.
     */
    private boolean ensurePendingSlot() {
        final CIOBufferPool.Lease[] old = _pending;
        if (old == null) {
            _pending = new CIOBufferPool.Lease[Math.min(_maxQueuedFrames, 8)];
            return true;
        }
        if (_queued < old.length) return true;
        if (old.length >= _maxQueuedFrames) return false;
        final int capacity = (int) Math.min((long) _maxQueuedFrames, (long) old.length << 1);
        final CIOBufferPool.Lease[] next = new CIOBufferPool.Lease[capacity];
        final int first = Math.min(_queued, old.length - _head);
        System.arraycopy(old, _head, next, 0, first);
        if (_queued > first) System.arraycopy(old, 0, next, first, _queued - first);
        _pending = next;
        _head = 0;
        _tail = _queued;
        return true;
    }

    /**
     * Pack only consecutive already-reserved packet tickets while the first
     * SEND_ENQUEUE has not been claimed. No provider WRITE, cipher advancement,
     * other producer ticket or payload callback may be passed by this merge.
     * Caller owns _stateLock; the socket serializer takes the same lock before
     * committing the first SEND and encrypting its complete frame sequence.
     */
    private boolean packAdjacentStagedPacketLocked(CIOBufferPool.Lease packet,
            long ticket, int slot) {
        return packAdjacentStagedPacketLocked(packet, ticket, slot, false);
    }

    private boolean packAdjacentStagedPacketLocked(CIOBufferPool.Lease packet,
            long ticket, int slot, boolean directFrame) {
        final CIOBufferPool.Lease earlier = _stagedPacketPack;
        if (earlier == null || ticket != _stagedPacketLastTicket + 1L
                || _socketHead > _stagedPacketFirstTicket || _closed != 0
                || !earlier.reserved() || earlier._writePrepared || earlier._ioPending
                || earlier.buffer().capacity() != CIOBufferPool.SMALL_SEND_BYTES)
            return false;
        final int length = packet.writer().position();
        final int prior = earlier.writer().position();
        if (length <= CIOBufferPool.HEADER_BYTES
                || length > packet._reservedWireBytes
                || length > PACK_MAX_FRAME_BYTES || !canAdmitBytes(length)
                || prior > CIOBufferPool.SMALL_SEND_BYTES - length
                || prior > earlier._reservedWireBytes - length)
            return false;
        // A reserved ticket may have been claimed while this producer waited
        // for the state lock. Claiming advances _socketHead before the owner
        // enters acceptStagedSend(), so no posted or encrypted lease is edited.
        if (_socketHead > _stagedPacketFirstTicket) return false;
        final boolean firstMerge = !earlier._packedFrames;
        if (firstMerge)
            earlier.writer().writeShort(0, (short) prior);
        packet.writer().writeShort(0, (short) length);
        final ByteBuffer dest = earlier.buffer();
        dest.limit(dest.capacity());
        dest.put(prior, packet.buffer(), 0, length);
        earlier.writer().position(prior + length);
        earlier._packedFrames = true;
        earlier._frameCount = firstMerge ? 2 : earlier._frameCount + 1;
        earlier._actionOnly = false;
        bytesAdmitted(length);
        _stagedPacketLastTicket = ticket;
        if (!directFrame) _pool.release(packet);
        // The earlier ticket carries these bytes. Retire this ticket in place
        // rather than changing the FIFO or the serializer's pending count.
        SOCKET_ARGUMENT_SLOT.setRelease(_socketArguments, slot, 0L);
        SOCKET_CALLBACK_SLOT.setRelease(_socketCallbacks, slot, SOCKET_CANCELLED);
        return true;
    }

    /** The direct writer's exact encode bound is relaxed only after commit. */
    private void promoteDirectPackCapacityLocked(CIOBufferPool.Lease lease) {
        if (lease._reservedWireBytes < CIOBufferPool.SMALL_SEND_BYTES) {
            lease.writer().expandExternalForPacking(CIOBufferPool.SMALL_SEND_BYTES);
            lease._reservedWireBytes = CIOBufferPool.SMALL_SEND_BYTES;
        }
    }

    /**
     * Compact ready FIFO SEND tickets while their first lease is still in the
     * serializer ring. A stalled/unpublished earlier ticket can prevent the
     * owner from draining hundreds of already encoded tiny packets. The
     * previous single-producer packing fast path cannot combine those packets
     * when encoders finish out of order or a control ticket intervenes.
     *
     * This scans the existing 512-slot ring only under SEND lease pressure;
     * it never consumes a ticket, crosses an unpublished/control ticket, or
     * modifies a lease after the owner begins encrypting it. Later merged
     * tickets remain explicit CANCELLED positions, preserving FIFO order.
     * Caller holds _stateLock. No new work queue or buffers are allocated.
     */
    private void compactReadyStagedPacketsLocked() {
        if (_stagedSends < 2) return;
        final long end = (long) SOCKET_TICKET.getVolatile(this);
        final long start = Math.max(_socketHead, end - SOCKET_SLOTS);
        CIOBufferPool.Lease first = null;
        long firstTicket = -1L;
        for (long ticket = start; ticket < end; ++ticket) {
            if (ticket < _socketHead) { first = null; continue; }
            final int slot = (int) ticket & SOCKET_MASK;
            final long kind = (long) SOCKET_CALLBACK_SLOT.getAcquire(_socketCallbacks, slot);
            if (kind == SOCKET_CANCELLED) continue;
            if (kind != SOCKET_SEND_ENQUEUE) { first = null; continue; }
            final CIOBufferPool.Lease next = _socketSendArguments[slot];
            if (next == null || !next.reserved() || next._writePrepared
                    || next._ioPending || next.buffer().capacity() != CIOBufferPool.SMALL_SEND_BYTES) {
                first = null;
                continue;
            }
            if (first != null && first != next && firstTicket >= _socketHead
                    && first.reserved() && !first._writePrepared && !first._ioPending) {
                final int before = first.writer().position();
                final int length = next.writer().position();
                if (before > CIOBufferPool.HEADER_BYTES
                        && length > CIOBufferPool.HEADER_BYTES
                        && length <= PACK_MAX_FRAME_BYTES
                        && before <= CIOBufferPool.SMALL_SEND_BYTES - length
                        && before <= first._reservedWireBytes - length) {
                    if (!first._packedFrames)
                        first.writer().writeShort(0, (short) before);
                    if (!next._packedFrames)
                        next.writer().writeShort(0, (short) length);
                    final ByteBuffer dst = first.buffer();
                    dst.limit(dst.capacity());
                    dst.put(before, next.buffer(), 0, length);
                    first.writer().position(before + length);
                    final int priorFrames = first._packedFrames ? first._frameCount : 1;
                    final int mergedFrames = next._packedFrames ? next._frameCount : 1;
                    first._frameCount = priorFrames + mergedFrames;
                    first._packedFrames = true;
                    first._actionOnly = false;
                    // The bytes and logical frames were already admitted by
                    // each ticket; only one physical lease is retired here.
                    --_stagedSends;
                    if (_stagedPacketPack == next || _stagedPacketPack == first)
                        _stagedPacketPack = null;
                    _socketSendArguments[slot] = null;
                    SOCKET_ARGUMENT_SLOT.setRelease(_socketArguments, slot, 0L);
                    SOCKET_CALLBACK_SLOT.setRelease(_socketCallbacks, slot, SOCKET_CANCELLED);
                    _pool.release(next);
                    continue;
                }
            }
            first = next;
            firstTicket = ticket;
        }
    }

    /** Successful commit means accepted for asynchronous SEND, not delivered to the peer. */
    public boolean commitOutboundFrame(CIOBufferPool.Lease lease) {
        final boolean admitted;
        final boolean direct = false;
        _stateLock.lock(); try {
            // Without an early reservation this legacy commit must never
            // bypass queued SEND tickets just because it runs on the owner.
            admitted = stageOutboundFrameLocked(lease, direct);
        } finally { _stateLock.unlock(); }
        if (admitted) finishStagedSend(lease, direct);
        return admitted;
    }

    private boolean stageOutboundFrameLocked(CIOBufferPool.Lease lease, boolean direct) {
        return stageOutboundFrameLocked(lease, direct, -1);
    }

    private boolean stageOutboundFrameLocked(CIOBufferPool.Lease lease, boolean direct,
            int reservedTicketSlot) {
        if (lease == null || !_pool.owns(lease) || !lease.reserved()) return false;
        final int length = lease.writer().position();
        if (length <= CIOBufferPool.HEADER_BYTES
                || length > lease._reservedWireBytes
                || length > CIOBufferPool.MAX_WIRE_BYTES
                || _closed != 0) return false;
        if (!canAdmitBytes(length)) return rejectSend(REJECT_WIRE_BYTES);
        if (physicalSendFullLocked()) {
            compactReadyStagedPacketsLocked();
            if (physicalSendFullLocked())
                return rejectSend(REJECT_LOCAL_FRAMES);
        }

        final int frames = lease._packedFrames ? lease._frameCount : 1;
        if (frames < 1) return rejectSend(REJECT_LOCAL_FRAMES);

        // Every staged SEND with no prior reservation takes a ticket unless
        // it is an inline completed action with no earlier work in the FIFO.
        int slot = reservedTicketSlot;
        if (!direct) {
            if (slot < 0) {
                // One atomic reservation instead of a read + CAS retry loop.
                // On saturation, revert the tentative count before publishing
                // any FIFO ticket, retaining the same bounded admission rule.
                final int pending = (int) SOCKET_PENDING.getAndAdd(this, 1);
                if (pending >= SOCKET_REJECT_AT) {
                    SOCKET_PENDING.getAndAdd(this, -1);
                    return rejectSend(REJECT_SERIALIZER);
                }
                slot = (int) (long) SOCKET_TICKET.getAndAdd(this, 1L) & SOCKET_MASK;
                if (_producerLease != null) {
                    ++_payloadOrderEpoch;
                    publishProducerLeaseLocked();
                }
            }
            _socketSendArguments[slot] = lease;
            SOCKET_ARGUMENT_SLOT.setRelease(_socketArguments, slot, 0L);
        }
        ++_stagedSends;
        bytesAdmittedBatch(length, frames);
        final SendBurst burst = SEND_BURST.get();
        if (_burstOwner == null && burst != null && burst.depth != 0
                && _writing == null && _queued == 0) {
            final int startBytes = burst.readPassDepth != 0
                    ? READ_PASS_WRITE_START_BYTES : BURST_MAX_BYTES;
            if (_outstandingWireBytes <= startBytes && burst.attach(this))
                _burstWriteStartBytes = startBytes;
        }
        if (!direct) {

            SOCKET_CALLBACK_SLOT.setRelease(_socketCallbacks, slot, SOCKET_SEND_ENQUEUE);
        }
        return true;
    }

    private boolean isSocketSerializerOwner() {
        return _socketOwner == Thread.currentThread() && !_socketPosting;
    }

    /**
     * A ticket-free READ owner may execute a SEND control callback directly
     * only while the FIFO is still empty. Foreign producers can reserve a
     * ticket without taking _stateLock, including before publishing its slot.
     * Normal ticket-owning serializer callbacks retain their existing path.
     */
    private boolean canExecuteOwnerControlDirect() {
        if (!isSocketSerializerOwner()) return false;
        if (!READ_WRITE_OWNER_FUSION || !_readSocketOwnerActive) return true;
        return (int) SOCKET_PENDING.getVolatile(this) == 0
                && (long) SOCKET_TICKET.getVolatile(this) == _socketHead
                && (!COALESCE_SEND_START
                    || (int) SOCKET_START_PENDING.getAcquire(this) == 0);
    }

    /**
     * Inline only a fully encoded 8-byte action while executing the sole
     * serializer callback. _socketHead has already consumed that callback's
     * ticket. Foreign reservations or staged frames must never be bypassed.
     * Called with _stateLock held.
     */
    private boolean canInlineActionLocked() {
        if (!isSocketSerializerOwner() || _reservedDirect != 0 || _stagedSends != 0
                || (long) SOCKET_TICKET.getVolatile(this) != _socketHead) return false;
        final int pending = (int) SOCKET_PENDING.getVolatile(this);
        // Normal serializer callbacks own one already-consumed ticket.
        // The fused READ owns the same serializer token, but no ticket.
        return _readSocketOwnerActive ? pending == 0 : pending == 1;
    }

    // Evaluated only inside the existing action admission lock, after the
    // SEND_ENQUEUE slot has been release-published. The current producer's
    // live burst scope is responsible for the mandatory finally flush.
    private boolean canDeferOwnedBurstDrainLocked() {
        if (!DEFER_OWNED_BURST_DRAIN || !COALESCE_SEND_START
                || _closed != 0 || _closing != 0
                || _sendReadPaused || _writing != null || _queued != 0
                || _stagedSends != 1 || _reservedDirect != 0
                || _producerLease != null
                || (int) SOCKET_START_PENDING.getVolatile(this) != 0
                || (int) SOCKET_DRAINING.getVolatile(this) != 0
                || (int) SOCKET_PENDING.getVolatile(this) != 1)
            return false;
        final SendBurst burst = _burstOwner;
        return burst != null && burst.thread == Thread.currentThread()
                && burst.depth != 0 && !burst.flushing;
    }

    /** Only this action admission path may defer an already-published SEND. */
    private void finishStagedSend(CIOBufferPool.Lease lease, boolean direct,
            boolean deferDrain) {
        if (deferDrain) return;
        finishStagedSend(lease, direct);
    }

    /** Direct SEND must run only after releasing the socket state lock. */
    private void finishStagedSend(CIOBufferPool.Lease lease, boolean direct) {
        if (!direct) {
            drainPublishedSocketCallbacks();
            return;
        }
        try {
            acceptStagedSend(lease);
        } catch (Throwable failure) {
            close();
        }
    }

    /** Consume SEND under the current socket serializer token. */
    private void acceptStagedSend(CIOBufferPool.Lease lease) {
        boolean start = false;
        boolean resumeRead = false;
        boolean success = false;
        CIOBufferPool.Lease selectedWrite = null;
        _stateLock.lock(); try {
            if (_stagedPacketPack == lease) _stagedPacketPack = null;
            // The first published Action may have been packed by the same
            // burst before this callback acquired the state lock.
            if (_stagedBurstAction == lease) _stagedBurstAction = null;
            --_stagedSends;
            final int length = lease.writer().position();
            if (_closed == 0) {
                success = commitStagedOutboundFrameLocked(lease);
                if (success && _writing != null && !_writing._ioPending) {
                    start = _burstOwner == null
                            || _outstandingWireBytes >= _burstWriteStartBytes;
                    // For the common single-write case, avoid a second
                    // _stateLock acquisition in driveWriteOnce(). This
                    // callback already owns the FIFO serializer token. An
                    // outstanding coalesced SEND_START is left to its normal
                    // loop, and multi-lease gather remains on the old path.
                    if (start && FUSE_STAGED_WRITE_SELECTION
                            
                            && isSocketSerializerOwner()
                            && _queued == 0 && _gatherCount == 0
                            && _stagedSends == 0 && _reservedDirect == 0
                            && _producerLease == null
                            && (((int) SOCKET_PENDING.getVolatile(this)
                                    == (_readSocketOwnerActive ? 0 : 1))
                                // An idle action owner holds the serializer token
                                // without reserving a ticket, just like a fused READ.
                                // A concurrent reservation must disable this path.
                                || ((int) SOCKET_PENDING.getVolatile(this) == 0
                                    && (long) SOCKET_TICKET.getVolatile(this)
                                        == _socketHead))) {
                        // The burst flush may have scheduled a coalesced
                        // start before this final SEND_ENQUEUE is consumed.
                        // With no other reserved FIFO ticket, absorb that
                        // wakeup here; otherwise its old outer-loop path
                        // must remain responsible for the next WRITE.
                        final int startPending = COALESCE_SEND_START
                                ? (int) SOCKET_START_PENDING.getAcquire(this) : 0;
                        if (startPending == 0 || DEFER_OWNED_BURST_DRAIN) {
                            if (startPending != 0)
                                SOCKET_START_PENDING.getAndSet(this, 0);
                            selectedWrite = _writing;
                            selectedWrite._ioPending = true;
                            start = false;
                        }
                    }
                }
            }
            if (!success) {
                resumeRead = bytesReleased(length,
                        lease._packedFrames ? lease._frameCount : 1);
                _pool.release(lease);
            }
        } finally { _stateLock.unlock(); }
        if (!success && _closed == 0) close();
        // The only selected lease was marked pending under _stateLock. The
        // NIO.2 provider call and its possible inline completion run outside
        // the lock, exactly as in driveWriteOnce() -> postWrite().
        if (selectedWrite != null) postWrite(selectedWrite);
        // P6-A: SendBurst.flush() may already have published SEND_START.
        // If we own this serializer, its outer loop will consume that bit
        // after the FIFO SEND_ENQUEUE callbacks. Posting here as well would
        // drive the same WRITE twice under the same owner. Do not suppress
        // kickWrite for any other caller or when no start is outstanding.
        if (start && _closed == 0
                && !(DEFER_OWNED_BURST_DRAIN && COALESCE_SEND_START
                     && isSocketSerializerOwner()
                     && (int) SOCKET_START_PENDING.getAcquire(this) != 0))
            kickWrite();
        if (resumeRead && _closed == 0) kickRead();
        notifyClosedWhenReleased();
    }

    /**
     * State-lock-owned attempt to claim an otherwise idle serializer for the
     * already-published, sole Action ticket at the head. Recheck after CAS:
     * callbacks can reserve control tickets without taking _stateLock.
     * The caller MUST drainSocketCallbacksAcquired() after unlocking if true,
     * including when commitSingleFlushSendLocked() returns null.
     */
    private boolean claimSingleFlushOwnerLocked() {
        if (!FUSE_STAGED_WRITE_SELECTION || !DEFER_OWNED_BURST_DRAIN
                || !COALESCE_SEND_START 
                || _closed != 0 || _closing != 0
                || _socketPosting || _sendReadPaused || _writing != null
                || _queued != 0 || _gatherCount != 0 || _stagedSends != 1
                || _reservedDirect != 0 || _producerLease != null
                || (int) SOCKET_START_PENDING.getAcquire(this) != 0
                || (long) WRITE_EVENT.getVolatile(this) != 0L
                || (int) SOCKET_PENDING.getVolatile(this) != 1
                || (long) SOCKET_TICKET.getVolatile(this) != _socketHead + 1L)
            return false;
        final int slot = (int) _socketHead & SOCKET_MASK;
        final CIOBufferPool.Lease lease = _socketSendArguments[slot];
        if (lease == null || lease != _stagedBurstAction
                || !lease.reserved() || !lease._actionOnly
                || lease._packedFrames || lease._frameCount != 1
                || lease.writer().position() != 8
                || (long) SOCKET_CALLBACK_SLOT.getAcquire(_socketCallbacks, slot)
                        != SOCKET_SEND_ENQUEUE)
            return false;
        if (!SOCKET_DRAINING.compareAndSet(this, 0, 1)) return false;
        _socketOwner = Thread.currentThread();
        return true;
    }

    /**
     * Only the claimed FIFO owner, holding _stateLock, can commit this ticket.
     * If a competing control reservation appeared after the owner claim,
     * leave the ticket untouched for the ordinary drainer (safe fallback).
     */
    private CIOBufferPool.Lease commitSingleFlushSendLocked() {
        // _writing, _queued, _stagedSends and _reservedDirect were checked
        // by claimSingleFlushOwnerLocked() under this same _stateLock.
        // No callback or other producer can change them until this method
        // commits. Keep rechecking foreign atomics and CLOSE: those can
        // change without acquiring _stateLock.
        if (_closed != 0 || _closing != 0
                || (int) SOCKET_START_PENDING.getAcquire(this) != 0
                || (int) SOCKET_PENDING.getVolatile(this) != 1
                || (long) SOCKET_TICKET.getVolatile(this) != _socketHead + 1L)
            return null;
        final int slot = (int) _socketHead & SOCKET_MASK;
        final CIOBufferPool.Lease lease = _socketSendArguments[slot];
        if (lease == null || lease != _stagedBurstAction
                || (long) SOCKET_CALLBACK_SLOT.getAcquire(_socketCallbacks, slot)
                        != SOCKET_SEND_ENQUEUE)
            return null;
        --_stagedSends;
        if (!commitStagedOutboundFrameLocked(lease)) {
            ++_stagedSends;
            return null;
        }
        // Consume exactly the oldest ticket, but retire its pending count only
        // after postWrite() returns, matching the normal drainer's finally.
        _stagedBurstAction = null;
        _socketSendArguments[slot] = null;
        SOCKET_ARGUMENT_SLOT.setRelease(_socketArguments, slot, 0L);
        SOCKET_CALLBACK_SLOT.setRelease(_socketCallbacks, slot, 0L);
        ++_socketHead;
        lease._ioPending = true;
        return lease;
    }

    /** Only the FIFO serializer owner may attach a staged SEND lease. */
    private boolean commitStagedOutboundFrameLocked(CIOBufferPool.Lease lease) {
        if (lease == null) return false;
        if (!_pool.owns(lease)) throw new IllegalArgumentException("foreign SEND lease");
        final WriteBuffer writer = lease.writer();
        final int length = writer.position();
        if (length <= CIOBufferPool.HEADER_BYTES
                || length > lease._reservedWireBytes
                || length > CIOBufferPool.MAX_WIRE_BYTES) return false;
        final int frameCount = lease._packedFrames ? lease._frameCount : 1;
        if (lease._packedFrames) {
            // Internal action-only leases were written under the admission lock:
            // each frame has a verified 8-byte header and 6-byte payload.
            // Mixed or externally encoded packets retain the full header walk.
            if (lease._actionOnly) {
                if (length != frameCount * 8) return false;
            } else {
                int pos = 0;
                final ByteBuffer packed = lease.buffer();
                for (int i = 0; i < frameCount; ++i) {
                    if (length - pos < CIOBufferPool.HEADER_BYTES) return false;
                    final int wireSize = Short.toUnsignedInt(packed.getShort(pos));
                    if (wireSize <= CIOBufferPool.HEADER_BYTES || wireSize > length - pos)
                        return false;
                    pos += wireSize;
                }
                if (pos != length) return false;
            }
        } else {
            writer.writeShort(0, (short) length);
        }
        writer.sealToPosition();
        lease._wireLength = length;
        lease._frameCount = frameCount;
        lease.publish();

        if (_closed != 0) {
            lease.unpublish();
            return false;
        }
        if (_writing != null && length <= PACK_MAX_FRAME_BYTES) {
            final CIOBufferPool.Lease tail = _queued != 0
                    ? _pending[_tail == 0 ? _pending.length - 1 : _tail - 1]
                    : (_burstOwner != null ? _writing : null);
            if (tail != null && !tail._ioPending && !tail._writePrepared
                    && tail.buffer().capacity() == CIOBufferPool.SMALL_SEND_BYTES
                    && tail._wireLength + length <= CIOBufferPool.SMALL_SEND_BYTES) {
                // Source packet is already serialized, but not yet encrypted.
                // Reuse the previously queued wire buffer without a new object.
                final ByteBuffer src = lease.buffer();
                final ByteBuffer dst = tail.buffer();
                final int offset = tail._wireLength;
                dst.limit(dst.capacity());
                // JDK absolute bulk put avoids per-byte bounds checks and
                // preserves both buffers' positions and limits.
                dst.put(offset, src, 0, length);
                tail._wireLength += length;
                tail._frameCount += frameCount;
                tail._packedFrames = true;
                tail._actionOnly &= lease._actionOnly;
                _pool.release(lease);
                return true;
            }
        }
        if (physicalSendFullLocked()) {
            lease.unpublish();
            return false;
        }
        if (_writing == null) {
            _writing = lease;
            _zeroWriteCompletions = 0;

        } else {
            if (!ensurePendingSlot()) {
                lease.unpublish();
                return false;
            }
            _pending[_tail] = lease;
            if (++_tail == _pending.length) _tail = 0;
            ++_queued;

        }
        return true;
    }

    /** Reserve FIFO position before serializing; encode does not hold the socket lock.
     * A true result means admitted to SEND, not delivered to the peer. */
    public boolean tryWritePacket(CIOClient client, WritablePacket<CIOClient> packet) {
        return tryWritePacketInternal(client, packet, false);
    }

    private boolean tryWritePacketInternal(CIOClient client,
            WritablePacket<CIOClient> packet, boolean finalPacket) {
        if (client == null || packet == null || !isOpen()
                || (!finalPacket && _closing != 0)) return false;
        if ((int) SOCKET_PENDING.getAcquire(this) >= SOCKET_REJECT_AT - 32)
            drainPublishedSocketCallbacks();
        final int learned = WriteBuffer.learnedPacketSize(packet.getClass());
        final boolean direct;
        final int slot;
        final long ticket;
        boolean publishProducer = false;
        _stateLock.lock();
        try {
            if (_closed != 0 || (!finalPacket && _closing != 0)) return false;
            if (!canAdmitBytes(Math.max(3, learned))) return rejectSend(REJECT_WIRE_BYTES);
            // A full lease count must not reject a packet that can join the
            // last unconsumed small SEND. The actual merge is revalidated
            // after encoding; ordinary hard caps remain in effect otherwise.
            if (physicalSendFullLocked()) {
                // Reclaim already-published small SENDs before treating a
                // transient serializer backlog as a hard socket overflow.
                compactReadyStagedPacketsLocked();
                if (physicalSendFullLocked() && (_stagedPacketPack == null
                        || _socketHead > _stagedPacketFirstTicket
                        || (long) SOCKET_TICKET.getVolatile(this)
                                != _stagedPacketLastTicket + 1L))
                    return rejectSend(REJECT_LOCAL_FRAMES);
            }
            // Packet bytes are encoded OUTSIDE the lock. Reserve a FIFO slot
            // before encoding, including when called by the serializer owner.
            direct = false;
            for (;;) {
                final int pending = (int) SOCKET_PENDING.getVolatile(this);
                if (pending >= SOCKET_REJECT_AT) return rejectSend(REJECT_SERIALIZER);
                if (SOCKET_PENDING.compareAndSet(this, pending, pending + 1)) break;
            }
            ticket = (long) SOCKET_TICKET.getAndAdd(this, 1L);
            slot = (int) ticket & SOCKET_MASK;
            if (_producerLease != null) {
                ++_payloadOrderEpoch;
                publishProducer = publishProducerLeaseLocked();
            }
            ++_reservedDirect;
        } finally {
            _stateLock.unlock();
        }
        if (publishProducer) drainSocketCallbacks();

        CIOBufferPool.Lease encoded = null;
        boolean encodeFailed = false;
        int capacity = learned > CIOBufferPool.SMALL_SEND_BYTES
                ? CIOBufferPool.MAX_WIRE_BYTES : CIOBufferPool.SMALL_SEND_BYTES;
        for (int attempt = 0; attempt < 2; attempt++) {
            CIOBufferPool.Lease lease = null;
            boolean retryLarge = false;
            try {
                lease = beginOutboundFrame(capacity);
                if (lease == null) {
                    rejectSend(REJECT_POOL);
                    break;
                }
                final WriteBuffer writer = lease.beginPacket(packet.getClass());
                packet.writeData(client, writer);
                writer.notePacketSize();
                encoded = lease;
            } catch (WriteBuffer.RegisteredCapacityExceededException oversized) {
                retryLarge = capacity < CIOBufferPool.MAX_WIRE_BYTES;
                if (!retryLarge) encodeFailed = true;
            } catch (Throwable failure) {
                encodeFailed = true;
            } finally {
                if (lease != null && lease != encoded && lease.reserved())
                    abortOutboundFrame(lease);
            }
            if (encoded != null || !retryLarge) break;
            capacity = CIOBufferPool.MAX_WIRE_BYTES;
        }

        boolean accepted = false;
        boolean packed = false;
        _stateLock.lock();
        try {
            --_reservedDirect;
            try {
                if (encoded != null) {
                    packed = packAdjacentStagedPacketLocked(encoded, ticket, slot);
                    accepted = packed || stageOutboundFrameLocked(encoded, direct, slot);
                    if (accepted && !packed) {
                        _stagedPacketPack = encoded;
                        _stagedPacketFirstTicket = ticket;
                        _stagedPacketLastTicket = ticket;
                        if (_stagedSends >= Math.max(8, _maxQueuedFrames >>> 2)) {
                            compactReadyStagedPacketsLocked();
                            packed = (long) SOCKET_CALLBACK_SLOT.getAcquire(
                                    _socketCallbacks, slot) == SOCKET_CANCELLED;
                        }
                    }
                }
            } catch (Throwable failure) {
                encodeFailed = true;
            } finally {
                if (!accepted) {
                    try {
                        // Keep the encoder reservation until its lease is
                        // released. Close must never publish completion first.
                        if (encoded != null && encoded.reserved())
                            abortOutboundFrame(encoded);
                    } catch (Throwable failure) {
                        encodeFailed = true;
                    } finally {
                        if (slot >= 0) {
                            // Retire even failed encoders in original FIFO order.
                            SOCKET_ARGUMENT_SLOT.setRelease(_socketArguments, slot, 0L);
                            SOCKET_CALLBACK_SLOT.setRelease(_socketCallbacks, slot, SOCKET_CANCELLED);
                        }
                    }
                }
            }
        } finally {
            _stateLock.unlock();
        }
        if (accepted) {
            if (packed) drainPublishedSocketCallbacks();
            else finishStagedSend(encoded, direct);
        } else {
            if (encodeFailed) rejectSend(REJECT_ENCODING);
            if (slot >= 0) drainSocketCallbacks();
            if (encodeFailed || encoded != null) close();
            if (_closed != 0) kickWrite();
            notifyClosedWhenReleased();
        }
        return accepted;
    }

    /**
     * Seal the producer's reserved FIFO ticket. Must hold _stateLock; drain the
     * serializer only after unlocking. A later foreign reservation is never
     * allowed to overtake this ticket, even if its encoder finished earlier.
     */
    private boolean publishProducerLeaseLocked() {
        final CIOBufferPool.Lease lease = _producerLease;
        if (lease == null) return false;
        final int slot = (int) _producerTicket & SOCKET_MASK;
        _producerLease = null;
        _socketSendArguments[slot] = lease;
        SOCKET_ARGUMENT_SLOT.setRelease(_socketArguments, slot,
                0L);
        SOCKET_CALLBACK_SLOT.setRelease(_socketCallbacks, slot, SOCKET_SEND_ENQUEUE);
        return true;
    }

    /**
     * Append to the reserved ticket while no other payload producer has
     * reserved a later FIFO position. Control tickets (WRITE completion and
     * SEND wakeup) are not payload boundaries. The state lock orders this
     * append against every packet/direct/action payload reservation.
     */


    /**
     * Open one bounded packed SEND using the existing burst's producer thread.
     * The ticket is reserved before writing and its callback published at the
     * next foreign-ticket, full-buffer, close, or burst-end boundary.
     */


    /**
     * Reserve each action as a completed frame for the socket serializer.
     * Its callback coalesces into the last unsubmitted 16K batch when possible,
     * preserving FIFO order and the existing wire-frame cipher boundaries.
     */
    /**
     * Admission under _stateLock: store an eight-byte action in the existing
     * serializer slot rather than allocating a 16KiB SEND lease behind an
     * unsealed earlier payload. FIFO order and encryption remain owner-only.
     */
    private boolean deferForeignActionLocked(int objectId, int actionId) {
        if (_reservedDirect == 0 && _producerLease == null
                && (int) SOCKET_PENDING.getVolatile(this) == 0
                && physicalSendLeasesLocked() < (_maxQueuedFrames >>> 2))
            return false;
        final int pending = (int) SOCKET_PENDING.getAndAdd(this, 1);
        if (pending >= SOCKET_REJECT_AT) {
            SOCKET_PENDING.getAndAdd(this, -1);
            return false;
        }
        final int slot = (int) (long) SOCKET_TICKET.getAndAdd(this, 1L) & SOCKET_MASK;
        if (_producerLease != null) {
            ++_payloadOrderEpoch;
            publishProducerLeaseLocked();
        }
        bytesAdmitted(8);
        final long packed = ((long) objectId << 32) | (actionId & 0xffffffffL);
        SOCKET_ARGUMENT_SLOT.setRelease(_socketArguments, slot, packed);
        SOCKET_CALLBACK_SLOT.setRelease(_socketCallbacks, slot, SOCKET_PACKED_ACTION);
        return true;
    }

    /** Convert one FIFO-reserved primitive action to wire bytes on the owner. */
    private void acceptPackedAction(long packed) {
        boolean valid = true;
        boolean resume = false;
        _stateLock.lock();
        try {
            if (_closed != 0) {
                resume = bytesReleased(8, 1);
            } else {
                final int objectId = (int) (packed >>> 32);
                final int actionId = (int) packed;
                CIOBufferPool.Lease tail = null;
                if (_queued > 0) {
                    tail = _pending[_tail == 0 ? _pending.length - 1 : _tail - 1];
                } else if (_writing != null) {
                    tail = _writing;
                }
                if (tail != null && !tail._ioPending && !tail._writePrepared
                        && tail.buffer().capacity() == CIOBufferPool.SMALL_SEND_BYTES
                        && tail._wireLength <= CIOBufferPool.SMALL_SEND_BYTES - 8) {
                    final ByteBuffer dest = tail.buffer();
                    dest.limit(dest.capacity());
                    putAction(dest, tail._wireLength, objectId, actionId);
                    tail._wireLength += 8;
                    ++tail._frameCount;
                    tail._packedFrames = true;
                    // Mixed packet tails retain their full per-frame cipher walk.
                } else {
                    final CIOBufferPool.Lease lease = _pool.acquireSend(CIOBufferPool.SMALL_SEND_BYTES);
                    if (lease == null) valid = false;
                    else {
                        putAction(lease.buffer(), 0, objectId, actionId);
                        lease.writer().position(8);
                        lease._frameCount = 1;
                        lease._actionOnly = true;
                        if (!commitStagedOutboundFrameLocked(lease)) {
                            if (lease.reserved()) _pool.release(lease);
                            valid = false;
                        }
                    }
                }
            }
        } finally { _stateLock.unlock(); }
        if (resume && _closed == 0) kickRead();
        if (!valid) { close(); return; }
        if (_closed == 0) kickWrite();
    }

    public boolean tryWriteAction(int objectId, int actionId) {
        if (_closing != 0) return false;
        boolean retriedDeferredSend = false;
        for (;;) {
            boolean drainDeferredSend = false;
            boolean deferredAction = false;
            boolean accepted = false;
            boolean direct = false;
            boolean deferDrain = false;
            boolean idleOwnerClaimed = false;
            boolean idleOwnerFinishDeferred = false;
            CIOBufferPool.Lease stagedLease = null;
            _stateLock.lock();
            try {
                if (_closed != 0 || _closing != 0) return false;
                if (!canAdmitBytes(8)) return rejectSend(REJECT_WIRE_BYTES);
                // When P6-A deferred only the first SEND, append to that
                // still-reserved lease under the admission lock instead of
                // draining the FIFO and reacquiring this lock on action #2.
                if (appendStagedBurstActionLocked(objectId, actionId)) return true;
                // The first unsubmitted burst lease already owns its physical slot.
                if (appendBurstActionLocked(objectId, actionId)) return true;
                if (deferForeignActionLocked(objectId, actionId)) {
                    deferredAction = true;
                    accepted = true;
                }
                // P6-A may leave the first already-published SEND staged until the
                // burst flush. Before renting another physical lease, let the
                // existing FIFO drainer attach that SEND so this action can use
                // the original writable tail. Never drain while holding the lock.
                if (!deferredAction && !retriedDeferredSend && DEFER_OWNED_BURST_DRAIN
                        && _writing == null && _queued == 0
                        && _stagedSends == 1 && _reservedDirect == 0
                        && _producerLease == null
                        && (int) SOCKET_PENDING.getVolatile(this) == 1
                        && (int) SOCKET_DRAINING.getVolatile(this) == 0) {
                    final SendBurst burst = _burstOwner;
                    drainDeferredSend = burst != null
                            && burst.thread == Thread.currentThread()
                            && burst.depth != 0 && !burst.flushing;
                }
                if (!deferredAction && !drainDeferredSend) {
                    // Fast single-frame/READ-pass workloads do not enter the producer
                    // packing path at all until there is a deferred lease or >8KiB
                    // pending in this owned burst.

                    if (physicalSendFullLocked()) {
                        rejectSend(REJECT_LOCAL_FRAMES);
                    } else {
                        final CIOBufferPool.Lease lease = _pool.acquireSend(CIOBufferPool.SMALL_SEND_BYTES);
                        if (lease == null) {
                            rejectSend(REJECT_POOL);
                        } else {
                            try {
                                final WriteBuffer writer = lease.writer();
                                putAction(lease.buffer(), 0, objectId, actionId);
                                writer.position(8);
                                lease._frameCount = 1;
                                lease._actionOnly = true;
                                direct = canInlineActionLocked();
                                // A live READ/handler burst must keep its first SEND
                                // staged until flush; claiming the idle token here
                                // defeats deferred burst drain and causes an extra
                                // owner release/reacquire before WRITE submission.
                                final SendBurst currentBurst = SEND_BURST.get();
                                if (!direct && (currentBurst == null
                                        || currentBurst.depth == 0 || currentBurst.flushing)
                                        && _stagedSends == 0
                                        && _reservedDirect == 0 && _producerLease == null
                                        && (int) SOCKET_PENDING.getVolatile(this) == 0
                                        && (long) SOCKET_TICKET.getVolatile(this) == _socketHead
                                        && (int) SOCKET_START_PENDING.getAcquire(this) == 0
                                        && (long) WRITE_EVENT.getVolatile(this) == 0L
                                        && SOCKET_DRAINING.compareAndSet(this, 0, 1)) {
                                    // A WRITE callback may reserve a FIFO ticket while we
                                    // attempt the CAS. Check again after owning the token.
                                    idleOwnerClaimed = true;
                                    _socketOwner = Thread.currentThread();
                                    direct = (int) SOCKET_PENDING.getVolatile(this) == 0
                                            && (long) SOCKET_TICKET.getVolatile(this) == _socketHead
                                            && (int) SOCKET_START_PENDING.getAcquire(this) == 0
                                            && (long) WRITE_EVENT.getVolatile(this) == 0L;
                                }
                                accepted = stageOutboundFrameLocked(lease, direct);
                                // Even a rejected stage must release an acquired token.
                                // An exception before this point is handled in finally.
                                idleOwnerFinishDeferred = idleOwnerClaimed;
                                if (accepted) {
                                    stagedLease = lease;
                                    if (!direct) {
                                        deferDrain = canDeferOwnedBurstDrainLocked();
                                        if (deferDrain) _stagedBurstAction = lease;
                                    }
                                }
                            } finally {
                                if (!accepted && lease.reserved()) _pool.release(lease);
                            }
                        }
                    }
                }
            } finally {
                _stateLock.unlock();

                // Exceptions while staging must not strand the token. The
                // normal path releases it AFTER commit and WRITE posting.
                if (idleOwnerClaimed && !idleOwnerFinishDeferred)
                    drainSocketCallbacksAcquired();
            }
            if (deferredAction) {
                // Primitive NPC action tickets share the same serializer cap.
                // Retire a ready prefix even when this thread is its owner.
                drainPublishedSocketCallbacks();
                return true;
            }
            if (drainDeferredSend) {
                // Published first SEND is still FIFO-ordered; this consumes it
                // without starting a WRITE while the owner burst is held.
                drainSocketCallbacks();
                retriedDeferredSend = true;
                continue;
            }
            if (idleOwnerClaimed) {
                try {
                    if (accepted)
                        finishStagedSend(stagedLease, direct, deferDrain);
                } finally {
                    // This caller won the token: drain pending completions,
                    // subsequent reservations, and inline callbacks before release.
                    drainSocketCallbacksAcquired();
                }
            } else if (accepted) {
                finishStagedSend(stagedLease, direct, deferDrain);
            }
            return accepted;
        }
    }

    /**
     * Append to the first still-staged P6-A Action without a second serializer
     * entry or a second state-lock acquisition. The lease is not published
     * (nor encrypted) until acceptStagedSend takes this same lock. Even if a
     * foreign drainer has removed the FIFO slot, it must wait for this lock
     * before touching the lease. A later ticket reservation disables append.
     */
    private boolean appendStagedBurstActionLocked(int objectId, int actionId) {
        final CIOBufferPool.Lease lease = _stagedBurstAction;
        if (lease == null || !DEFER_OWNED_BURST_DRAIN
                || _stagedSends != 1 || _writing != null || _queued != 0
                || _reservedDirect != 0 || _producerLease != null
                || (int) SOCKET_PENDING.getVolatile(this) != 1)
            return false;
        final SendBurst burst = _burstOwner;
        if (burst == null || burst.thread != Thread.currentThread()
                || burst.depth == 0 || burst.flushing)
            return false;
        // The single outstanding ticket is either not consumed yet (head+1),
        // or is being executed (head). No foreign reservation may precede it.
        final long ticket = (long) SOCKET_TICKET.getVolatile(this);
        final long head = _socketHead;
        if (ticket != head && ticket != head + 1L) return false;
        final int pos = lease.writer().position();
        if (pos > CIOBufferPool.SMALL_SEND_BYTES - 8
                || pos > lease._reservedWireBytes - 8
                || _outstandingWireBytes > BURST_MAX_BYTES - 8
                || !canAdmitBytes(8)) return false;
        putAction(lease.buffer(), pos, objectId, actionId);
        lease.writer().position(pos + 8);
        if (!lease._packedFrames) {
            lease._packedFrames = true;
            lease._frameCount = 2;
        } else {
            ++lease._frameCount;
        }
        bytesAdmitted(8);
        return true;
    }

    /**
     * The current SendBurst owns the unsubmitted first lease. No foreign
     * reservation, pending serializer callback, or I/O may precede an append.
     * Caller holds _stateLock. SEND admission remains FIFO at frame boundaries.
     */
    private CIOBufferPool.Lease writableBurstTailLocked() {
        // The connection already retains the burst. Avoid a ThreadLocal map
        // lookup on every 8-byte action; reject a foreign producer explicitly.
        final SendBurst burst = _burstOwner;
        if (burst == null || burst.thread != Thread.currentThread()
                || burst.depth == 0
                || _queued != 0 || _stagedSends != 0 || _reservedDirect != 0)
            return null;
        // The current callback still counts in SOCKET_PENDING until its
        // handler returns. It may append to its own unpublished burst, but
        // only when no other callback/ticket was admitted ahead of it.
        if ((int) SOCKET_PENDING.getVolatile(this) != 0
                && !canInlineActionLocked()) return null;
        final CIOBufferPool.Lease tail = _writing;
        if (tail == null || tail._ioPending || tail._writePrepared
                || tail.buffer().capacity() != CIOBufferPool.SMALL_SEND_BYTES)
            return null;
        return tail;
    }

    /**
     * Reuse a queued, not-yet-encrypted tail even when it is not burst-owned.
     * The active WRITE must already be in flight, so no kickWrite is required.
     * This avoids a temporary third lease for a small action appended behind
     * the currently in-flight WRITE. Called with _stateLock held.
     */
    private CIOBufferPool.Lease writableQueuedActionTailLocked() {
        if (_queued == 0 || _writing == null || !_writing._ioPending
                || _burstOwner != null || _stagedSends != 0 || _reservedDirect != 0
                || _socketPosting) return null;
        final int pending = (int) SOCKET_PENDING.getVolatile(this);
        if ((pending != 0 && !canInlineActionLocked())
                || (long) SOCKET_TICKET.getVolatile(this) != _socketHead)
            return null;
        final CIOBufferPool.Lease tail = _pending[_tail == 0
                ? _pending.length - 1 : _tail - 1];
        return tail != null && !tail._ioPending && !tail._writePrepared
                && tail.buffer().capacity() == CIOBufferPool.SMALL_SEND_BYTES
                ? tail : null;
    }

    /** Append one wire-complete action without a new lease or serializer ticket. */
    private boolean appendBurstActionLocked(int objectId, int actionId) {
        CIOBufferPool.Lease tail = writableBurstTailLocked();
        final boolean burstTail = tail != null;
        if (tail == null) tail = writableQueuedActionTailLocked();
        if (tail == null || !canAdmitBytes(8)
                || (burstTail && _outstandingWireBytes > BURST_MAX_BYTES - 8)
                || tail._wireLength > CIOBufferPool.SMALL_SEND_BYTES - 8)
            return false;
        final ByteBuffer bytes = tail.buffer();
        bytes.limit(bytes.capacity());
        putAction(bytes, tail._wireLength, objectId, actionId);
        tail._wireLength += 8;
        ++tail._frameCount;
        tail._packedFrames = true;
        bytesAdmitted(8);
        return true;
    }

    /**
     * Network-only action batch: no allocation, one lock and one watermark
     * update for N complete 8-byte frames. Only a writable burst-owned tail
     * permits the batch fast path; otherwise original single-frame FIFO is used.
     * Existing sendAction/tryWriteAction success and failure contracts remain.
     * The supplied arrays are borrowed for this call, never retained.
     */
    public boolean tryWriteActionBatch(int[] objectIds, int[] actionIds,
            int offset, int count) {
        if (objectIds == null || actionIds == null || offset < 0 || count < 0
                || offset > objectIds.length || count > objectIds.length - offset
                || offset > actionIds.length || count > actionIds.length - offset)
            throw new IllegalArgumentException("invalid action batch range");
        if (_closing != 0) return false;
        int done = 0;
        while (done < count) {
            // One lock per successful chunk: append to the owned tail or
            // encode/publish a private packed lease under that same lock.
            final int appended = tryWriteActionChunk(objectIds, actionIds,
                    offset + done, count - done);
            if (appended != 0) {
                done += appended;
            } else {
                // Only fall back to a single frame when a full chunk could
                // not be admitted under the current flow-control limits.
                if (!tryWriteAction(objectIds[offset + done], actionIds[offset + done])) {
                    // Previous frames in this batch cannot be rolled back after
                    // publication; never keep a partially accepted batch live.
                    if (done != 0 && _closed == 0) close();
                    return false;
                }
                ++done;
            }
        }
        return true;
    }

    /**
     * Encode an action chunk while holding the admission lock: unlike a generic
     * packet, no external encoder can delay publication after the chunk is
     * ordered. One lease, one serializer ticket, N logical encrypted frames.
     */
    private int tryWriteActionChunk(int[] objectIds, int[] actionIds,
            int offset, int requested) {
        CIOBufferPool.Lease lease = null;
        int frames = 0;
        boolean direct = false;
        boolean accepted = false;
        _stateLock.lock();
        try {
            if (_closed != 0 || _closing != 0) return 0;
            final int appended = appendBurstActionBatchLocked(objectIds, actionIds,
                    offset, requested);
            if (appended != 0) return appended;
            final int wireRoom = Math.min(
                    CIOBufferPool.SMALL_SEND_BYTES,
                    MAX_PENDING_WIRE_BYTES - _outstandingWireBytes - _reservedWireBytes);
            frames = Math.min(requested, wireRoom / 8);
            if (frames <= 0) return 0;
            if (physicalSendFullLocked())
                return 0;
            lease = _pool.acquireSend(CIOBufferPool.SMALL_SEND_BYTES);
            if (lease == null) return 0;
            final ByteBuffer bytes = lease.buffer();
            for (int i = 0, pos = 0; i < frames; ++i, pos += 8)
                putAction(bytes, pos, objectIds[offset + i], actionIds[offset + i]);
            lease.writer().position(frames * 8);
            lease._frameCount = frames;
            lease._packedFrames = frames > 1;
            lease._actionOnly = true;
            direct = canInlineActionLocked();
            accepted = stageOutboundFrameLocked(lease, direct);
        } finally {
            try {
                if (!accepted && lease != null && lease.reserved()) _pool.release(lease);
            } finally {
                _stateLock.unlock();
            }
        }
        if (accepted) finishStagedSend(lease, direct);
        return accepted ? frames : 0;
    }

    /** Batch admission and accounting are indivisible under _stateLock. */
    private int appendBurstActionBatchLocked(int[] objectIds, int[] actionIds,
            int offset, int count) {
        if (_closed != 0) return 0;
        final CIOBufferPool.Lease tail = writableBurstTailLocked();
        if (tail == null) return 0;
        final int freeBytes = Math.min(
                Math.min(BURST_MAX_BYTES - _outstandingWireBytes,
                        CIOBufferPool.SMALL_SEND_BYTES - tail._wireLength),
                MAX_PENDING_WIRE_BYTES - _outstandingWireBytes - _reservedWireBytes);
        final int frames = Math.min(count, freeBytes / 8);
        if (frames <= 0 || !canAdmitBytes(frames * 8)) return 0;

        final ByteBuffer bytes = tail.buffer();
        bytes.limit(bytes.capacity());
        int position = tail._wireLength;
        for (int i = 0; i < frames; ++i) {
            putAction(bytes, position, objectIds[offset + i], actionIds[offset + i]);
            position += 8;
        }
        tail._wireLength = position;
        tail._frameCount += frames;
        tail._packedFrames = true;
        bytesAdmittedBatch(frames * 8, frames);
        return frames;
    }

    private static void putAction(ByteBuffer out, int position, int objectId, int actionId) {
        // CIOBufferPool SEND leases are LITTLE_ENDIAN. Encode the complete
        // 8-byte wire frame with one checked direct-buffer write instead of
        // four separate absolute stores (including unaligned putInt).
        out.putLong(position,
                8L | ((Opcodes.S_ACTION & 0xffL) << 16)
                        | ((objectId & 0xffff_ffffL) << 24)
                        | ((actionId & 0xffL) << 56));
    }

    /**
     * The client cipher advances per game packet, not per transport write.
     * Each packed frame is encrypted individually in wire order.
     */
    private boolean preparePackedEncryption(CIOBufferPool.Lease lease) {
        final WriteBuffer writer = lease.writer();
        final ByteBuffer bytes = lease.buffer();
        final int length = lease.wireLength();
        if (lease._actionOnly) {
            if (length != lease._frameCount * 8) return false;
            for (int offset = 0; offset < length; offset += 8) {
                // Per-frame cipher initialization/advancement is unchanged.
                if (!encrypt(writer, offset + CIOBufferPool.HEADER_BYTES, 6)) return false;
            }
            return true;
        }
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

    /** Only call this for a frame that was NOT committed. */
    public void abortOutboundFrame(CIOBufferPool.Lease lease) {
        if (lease == null) return;
        if (!_pool.owns(lease) || !lease.reserved())
            throw new IllegalStateException("abort after commit or foreign SEND lease");
        _pool.release(lease);
    }

    /** Exactly one pending read per socket. */
    public void startRead() {
        if(nativeDirect){if(_closed==0&&READ_ACTIVE.compareAndSet(this,0,1))kickRead();return;}
        try {
            _stateLock.lock(); try {
                if ((_closed != 0) || !READ_ACTIVE.compareAndSet(this, 0, 1)) return;
                try {
                    _reading = _pool.acquireRecv();
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
    private void kickRead() {
        if(nativeDirect){
            if(_closed!=0){finishReadIfClosed();notifyClosedWhenReleased();return;}
            if(!_channel.isOpen())return;
            boolean pause=_sendReadPaused||_closing!=0;
            if(!_channel.pauseNativeUser(pause)){close();return;}
            if(!pause)dispatchNativePacket();
            return;
        }
        if ((int) READ_KICK.getAndAdd(this, 1) != 0) return;
        drainReadPump(1, 0);
    }

    /** Called with READ_KICK ownership; firstEvent is an unposted completion. */
    private void drainReadPump(int missed, int firstEvent) {
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
                    // Native framing owns its own bounded byte queue. Do not
                    // run Java-frame fusion against its consumed READ buffer.
                    final boolean nativeFraming = false;
                    final boolean fused = !nativeFraming && tryOwnReadWritePass();
                    boolean accepted;
                    try {
                        accepted = fused ? onReadSingleCompleteFrame() : onRead();
                    } finally {
                        // Dispatch releases the borrowed SEND token before any
                        // game handler executes. If decode/filtering exited before
                        // dispatch, the READ pump must still release that token.
                        if (fused && _readSocketOwnerActive)
                            releaseFusedReadSendOwner();
                    }
                    if (!accepted) {
                        close();
                    } else if (_closed == 0 && !_sendReadPaused && !_readYielded) {
                        postRead();
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

    /**
     * Borrow the existing 512-slot serializer only for an uncontended,
     * complete small READ frame. A pending/unpublished producer reservation,
     * WRITE completion or SEND_START must execute in original FIFO order.
     * READ ownership remains with READ_KICK; this only joins the SEND pump.
     */
    private boolean tryOwnReadWritePass() {
        if (!READ_WRITE_OWNER_FUSION || _closed != 0 || _closing != 0
                || _sendReadPaused || _readYielded || _reading == null) return false;
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



    private void postRead() {
        if (_closed != 0 || _closing != 0) return;
        final ByteBuffer recv = _reading.buffer();
        if (!recv.hasRemaining()) {
            close();
            return;
        }
        // Set before the call: async I/O may invoke completed() synchronously.
        _readPending = true;

        try {
            _channel.read(recv, this);
        } catch (Throwable t) {
            _readPending = false;
            close();
        } finally {

        }
    }

    /** Release the READ lease on close only after the last completion. */
    private void finishReadIfClosed() {
        if(nativeDirect&&_closed!=0){READ_ACTIVE.setVolatile(this,0);_readReleased=true;return;}
        if ((_closed != 0) && !_readPending && _readKick == 0) {
            _stateLock.lock(); try {
                if (!_readPending && _readKick == 0) endRead();
            } finally { _stateLock.unlock(); }
        }
    }

    /**
     * Called only after tryOwnReadWritePass() verified an entire, single small
     * frame and claimed the socket serializer. The ordinary decoder keeps all
     * partial/multi-frame, contention, close and backpressure cases.
     */
    private boolean onReadSingleCompleteFrame() {
        _readYielded = false;
        // CLOSE or a foreign SEND producer may change pressure after the
        // READ owner claim. Let the established loop handle those races.
        if (_closed != 0 || _closing != 0 || _sendReadPaused) return onRead();
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
        final int buffered = inbound.position();
        if (buffered == 0) {
            _partialReadStartedNs = 0L;
            return;
        }
        if (buffered >= CIOBufferPool.HEADER_BYTES) {
            final int frameBytes = Short.toUnsignedInt(inbound.getShort(0));
            if (frameBytes >= CIOBufferPool.HEADER_BYTES && frameBytes <= buffered) {
                // SEND pressure may yield with a complete frame still buffered.
                // It must not inherit the old partial-frame clock on resume.
                _partialReadStartedNs = 0L;
                return;
            }
        }
        if (_partialReadStartedNs == 0L) _partialReadStartedNs = System.nanoTime();
    }

    /** Leased C++ plaintext; wire assembly and codec Feed/Next stay native. */
    boolean transportNativePacket(long sequence,ByteBuffer decoded,int bytes) {
        if(_closed!=0||_closing!=0||_client==null||!_client.isConnected())return false;
        if(bytes==0)return _client.incrementEmptyPacketCount()<=ConnectionConfig.PACKET_LIMIT_PER_SEC;
        if(bytes<4||sequence==0||!nativePacketPhase.compareAndSet(0,5))return false;
        _nativePacketBuffer=decoded;_nativePacketLength=bytes;_nativePacketSequence=sequence;
        _nativeGamePending=true;
        try {
            _nativePacketActor=_client.getActiveChar();
            _nativePacketStamp=_nativePacketActor==null?0:_nativePacketActor.getSessionStamp();
            _nativePacketOnIo=!CUserSocketDispatch.sessionOwner(decoded.get(0)&255,_client.isGameSessionReady());
            _client.resetEmptyPacketCount();
        } catch(Throwable failure) {
            // Publish the owned lease before close/cancel can return it. A
            // failed session lookup must not strand phase 5 during shutdown.
            nativePacketPhase.set(1);
            LOG.log(Level.WARNING,"Native packet session capture failed",failure);
            close();cancelQueuedNativePacket();return true;
        }
        nativePacketPhase.set(1); // waiting for SEND pressure/dispatch
        if(_closed!=0||!_client.isConnected()){close();cancelQueuedNativePacket();return true;}
        if(_sendReadPaused){_channel.pauseNativeUser(true);return true;}
        dispatchNativePacket();return true;
    }
    private void dispatchNativePacket() {
        if(_closed!=0||_closing!=0){cancelQueuedNativePacket();return;}
        if(_sendReadPaused||!nativePacketPhase.compareAndSet(1,2))return;
        try {
            if(_nativePacketOnIo&&_channel.currentNativeOwner()>=0)executeNativePacket();
            else if(!(_nativePacketOnIo?_channel.postCompletion(this)
                    :CIOThreadCallback.getInstance().enqueuePacketImmediate(_affinityShard,this)))close();
        }catch(Throwable failure){LOG.log(Level.WARNING,"Native packet dispatch admission failed",failure);close();}
    }
    @Override public void run(){executeNativePacket();}
    @Override public void cancel(){close();cancelQueuedNativePacket();}
    private void cancelQueuedNativePacket() {
        for(;;){int phase=nativePacketPhase.get();if(phase!=1&&phase!=2)return;
            if(nativePacketPhase.compareAndSet(phase,4)){finishNativePacket();return;}}
    }
    private void finishNativePacket() {
        long sequence=_nativePacketSequence;
        _nativePacketBuffer=null;_nativePacketLength=0;_nativePacketActor=null;_nativePacketSequence=0;
        _nativeGamePending=false;nativePacketPhase.set(0);
        // Publish all Java lease release before C++ may deliver the next packet.
        // Release remains legal during shutdown until native Stop has drained.
        if(_channel.isOpen())_channel.pauseNativeUser(_sendReadPaused||_closing!=0);
        if(sequence!=0)_channel.releaseUserPacket(sequence);
        notifyClosedWhenReleased();
    }
    private void executeNativePacket() {
        if(!nativePacketPhase.compareAndSet(2,3))return;
        if(_closed==0&&_sendReadPaused){nativePacketPhase.set(1);kickRead();return;}
        try {
            final var actor=_nativePacketActor;
            boolean current=actor==null||(actor==_client.getActiveChar()&&actor.getNetConnection()==_client&&actor.getSessionStamp()==_nativePacketStamp);
            if(_closed==0&&_client.isConnected()&&current){
                if(_nativePacketOnIo?_channel.currentNativeOwner()<0:CIOThreadCallback.currentShardIndex()!=_affinityShard)
                    throw new IllegalStateException("Packet executed outside its native/session role");
                if(!onNativePlaintext(_nativePacketBuffer,_nativePacketLength))close();
            }
        }catch(Throwable failure){LOG.log(Level.WARNING,"[CIO NATIVE] packet execution failed",failure);close();}
        finally{finishNativePacket();}
    }

    private boolean onNativePlaintext(ByteBuffer payload, int bytes) {
        if (_closing != 0) return _closed == 0;
        if (!_client.isConnected() || bytes < 4) return false;
        final DecodeState state = acquireDecodeState();
        try {
            final ReadBuffer decoded = state.buffer;
            decoded.resetTrusted(payload, 0, bytes);
            final int opcode = decoded.readUnsignedByte();
            // Native decrypt already advanced the rolling key before filtering.
            if (_client.checkPacketRateLimit(System.currentTimeMillis())) return false;
            if (!CUserSocketDispatch.allowed(opcode, _client.getConnectionState(),
                    _client.getAccount() != null, _client.getActiveChar() != null,
                    _client.isGameSessionReady())) return inboundHandlerConnected();
            if (!CPacketHandler.isFastOpcode(opcode)) {
                NetworkSafeLoadManager.isFilter(_client, opcode);
                return inboundHandlerConnected();
            }
            if (NetworkSafeLoadManager.isFilter(_client, opcode)
                    != NetworkSafeLoadManager.RESULT_FILTER) {
                if (CPacketHandler.isDirectOpcode(opcode)
                        || (_client.isGameSessionReady()
                            && !CPacketHandler.isSessionOpcode(opcode))) {
                    dispatchDecodedFrame(opcode, decoded);
                } else {
                    synchronized (_client) {
                        if (_client.isConnected()) dispatchDecodedFrame(opcode, decoded);
                    }
                }
            }
            return inboundHandlerConnected();
        } finally {
            state.buffer.clear();
            state.active = false;
        }
    }

    private boolean onRead() {
        _readYielded = false;
        ByteBuffer recv = _reading.buffer();
        if (recv.position() == 0) return true;
        // Avoid an extra ThreadLocal scope for a single completed request.
        // Every pass, including fairness yield or an exception, flushes in
        // finally before the READ pump can post another asynchronous read.
        boolean passBurst = false;
        if (recv.position() >= READ_PASS_MIN_BUFFERED_BYTES) {
            final int first = Short.toUnsignedInt(recv.getShort(0));
            final int filled = recv.position();
            if (first >= CIOBufferPool.HEADER_BYTES
                    && first <= filled - CIOBufferPool.HEADER_BYTES) {
                final int second = Short.toUnsignedInt(recv.getShort(first));
                passBurst = second >= CIOBufferPool.HEADER_BYTES
                        && second <= filled - first;
            }
        }
        if (passBurst) {
            beginReadPassSendBurst();
            _readPassBurstActive = true;
        }
        try {
        recv.flip();
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
        } else if (recv.position() == 0) {
            final int buffered = recv.limit();
            recv.clear();
            recv.position(buffered);
        } else {
            recv.compact();
        }
        if (!recv.hasRemaining()) {
            // A complete compact-buffer-sized frame can fill this lease exactly.
            // SEND-pressure backpressure can retain a complete frame.
            if (_readYielded) return true;
            if (recv.position() < CIOBufferPool.HEADER_BYTES) return false;
            final int wireLength = Short.toUnsignedInt(recv.getShort(0));
            if (wireLength <= recv.capacity()) return false;
            final CIOBufferPool.Lease large = _pool.acquireLargeRecv();
            if (large == null) return false;
            recv.flip();
            large.buffer().put(recv);
            final CIOBufferPool.Lease old = _reading;
            _reading = large;
            _pool.release(old);
        } else if (_reading.buffer().capacity() > CIOBufferPool.RECV_BYTES
                && recv.position() == 0) {
            final CIOBufferPool.Lease compact = _pool.acquireRecv();
            if (compact != null) {
                final CIOBufferPool.Lease old = _reading;
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

    private void endRead() {
        if (!READ_ACTIVE.compareAndSet(this, 1, 0)) return;
        releaseReadWhenIdle();
        notifyClosedWhenReleased();
    }

    private void releaseReadWhenIdle() {
        _stateLock.lock();
        try {
        if (_readReleased || (_readActive != 0)) return;
        final CIOBufferPool.Lease recv = _reading;
        _reading = null;
        if (recv != null) _pool.release(recv);
        _readReleased = true;
        } finally {
            _stateLock.unlock();
        }
    }

    private boolean onReadFrame(ByteBuffer wire, int bodyStart, int bodyLength) {
        if (_closing != 0) return _closed == 0 && _disconnecting == 0;
        if (!_client.isConnected()) return false;

        // Preserve the empty-packet flood handling (wire length == 2).
        if (bodyLength == 0) {
            if (_client.incrementEmptyPacketCount() <= ConnectionConfig.PACKET_LIMIT_PER_SEC) {
                return true;
            }
            _client.disconnect();
            return false;
        }
        _client.resetEmptyPacketCount();

        // Lineage decrypt consumes its 4-byte cipher prefix and requires 8 bytes.
        if (bodyLength < 8) {
            _client.disconnect();
            return false;
        }

        // Exactly one full decrypt per frame: it advances the rolling cipher
        // state, including filtered and unknown opcodes. Do not preview first.
        final DecodeState state = acquireDecodeState();
        try {
            final ReadBuffer decoded = state.buffer;
            boolean success = false;
            try {
                final boolean hasCipher = readCipher() != null;
                decoded.resetTrusted(wire, bodyStart, bodyLength);
                if (decrypt(decoded, 0, bodyLength)) {
                    decoded.limit(bodyLength - 4);
                    if (decoded.remaining() == 0) {
                        success = inboundHandlerConnected();
                    } else if (!_client.checkPacketRateLimit(System.currentTimeMillis())) {
                        final int opcode = decoded.readUnsignedByte();
                        if (hasCipher
                                && !CPacketHandler.isFastOpcode(opcode)) {
                            // The unknown-opcode filter runs AFTER decrypt,
                            // so later packets retain the correct key state.
                            NetworkSafeLoadManager.isFilter(_client, opcode);
                            success = true;
                        } else if (NetworkSafeLoadManager.isFilter(_client, opcode)
                                != NetworkSafeLoadManager.RESULT_FILTER) {
                            if (CPacketHandler.isDirectOpcode(opcode)
                                    || (_client.isGameSessionReady()
                                    && !CPacketHandler.isSessionOpcode(opcode))) {
                                dispatchDecodedFrame(opcode, decoded);
                            } else {
                                synchronized (_client) {
                                    if (_client.isConnected()) {
                                        dispatchDecodedFrame(opcode, decoded);
                                    }
                                }
                            }
                            success = inboundHandlerConnected();
                        } else {
                            success = inboundHandlerConnected();
                        }
                    } else {
                        _client.disconnect();
                        return false;
                    }
                } else {
                }
            } catch (Throwable error) {
                LOG.log(Level.WARNING, "[I/O OnRead] packet decode failed", error);
            }
            // DecodeState's outer finally releases the same ReadBuffer once.
            if (!success) _client.disconnect();
            return success;
        } finally {
            state.buffer.clear();
            state.active = false;
        }
    }

    /** Graceful disconnect is an accepted handler outcome, not a decode error. */
    private boolean inboundHandlerConnected() {
        return _closed == 0 && _disconnecting == 0
                && (_closing != 0 || _client.isConnected());
    }

    private void executeMeasured(CPacketHandler.PacketHandler handler, int opcode, ReadBuffer decoded) {
        final long started = System.nanoTime();
        try {
            handler.execute(decoded, _client);
        } finally {
            final long elapsed = System.nanoTime() - started;
            if (elapsed >= SLOW_HANDLER_NS) {
                SLOW_OPCODE_COUNTS.incrementAndGet(opcode & 0xFF);
                SLOW_MAX_NS.accumulateAndGet(elapsed, Math::max);
            }
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
    private void releaseFusedReadSendOwner() {
        if (!_readSocketOwnerActive) return;
        if (_socketOwner != Thread.currentThread()
                || (int) SOCKET_DRAINING.getVolatile(this) == 0)
            throw new IllegalStateException("fused READ lost socket serializer ownership");
        _readSocketOwnerActive = false;
        drainSocketCallbacksAcquired();
    }

    private void dispatchDecodedFrame(int opcode, ReadBuffer decoded) {
        // Even if this handler blocks on game logic or client synchronization,
        // unrelated SEND ticket owners must be able to drain immediately.
        releaseFusedReadSendOwner();
        final CPacketHandler.PacketHandler handler = CPacketHandler.HANDLERS[opcode & 0xFF];
        // Only the current socket READ pump can set this flag. A full buffer
        // owns the SEND burst and its finally always flushes it.
        if (_readPassBurstActive) {
            if (handler != null) executeMeasured(handler, opcode, decoded);
            // With large responses, READ pressure may stop the pass after a
            // single request. Submit its already accepted output immediately;
            // do not make that request wait for the outer frame-loop epilogue.
            if (_sendReadPaused) flushSendBurst();
            return;
        }
        // One ThreadLocal lookup per decoded single-frame handler instead of
        // begin/flush/end's three lookups. Keep nested scope boundaries and
        // the same flush-on-error contract; no SEND reorder or extra queue.
        SendBurst burst = SEND_BURST.get();
        if (burst == null) {
            burst = new SendBurst();
            SEND_BURST.set(burst);
        }
        ++burst.depth;
        try {
            if (handler != null) executeMeasured(handler, opcode, decoded);
            // The ordinary single-frame path exits the scope below. Its
            // finally performs the one mandatory flush, including on throw.
            // Nested dispatch must still flush immediately even while the
            // outer burst remains active.
            if (burst.depth != 1) burst.flush();
        } finally {
            if (--burst.depth == 0) burst.flush();
        }
    }

    private boolean serializeSocketCallback(long kind, long argument) {
        if (canExecuteOwnerControlDirect()) {
            if (kind == SOCKET_SEND_START) driveWriteOnce();
            else if (kind == SOCKET_WRITE_COMPLETE
                    && WRITE_EVENT.compareAndSet(this, argument, 0L))
                drainWriteCompletion(argument);
            return true;
        }
        for (;;) {
            final int pending = (int) SOCKET_PENDING.getVolatile(this);
            if (pending >= SOCKET_REJECT_AT) return false;
            if (SOCKET_PENDING.compareAndSet(this, pending, pending + 1)) break;
        }
        final long ticket = (long) SOCKET_TICKET.getAndAdd(this, 1L);
        final int slot = (int) ticket & SOCKET_MASK;
        SOCKET_ARGUMENT_SLOT.setRelease(_socketArguments, slot, argument);
        SOCKET_CALLBACK_SLOT.setRelease(_socketCallbacks, slot, kind);
        drainSocketCallbacks();
        return true;
    }

    /**
     * A packed SEND still occupies a FIFO CANCELLED ticket until the owner
     * consumes it. If the current thread already owns the serializer, a plain
     * drainSocketCallbacks() CAS fails and leaves hundreds of ready slots in
     * the 512-entry ring. Drive the ready prefix with the existing owner token.
     *
     * Never recurse during an active ticket or inside write(): their outer
     * serializer loop will drain after the callback/provider returns.
     */
    private void drainPublishedSocketCallbacks() {
        if (_socketOwner == Thread.currentThread()
                && (int) SOCKET_DRAINING.getVolatile(this) != 0) {
            if (!_socketPosting && !_socketExecutingTicket)
                drainSocketCallbacksOwnedReady();
            return;
        }
        drainSocketCallbacks();
    }

    /** First contender becomes the inline drainer; other callers return. */
    private void drainSocketCallbacks() {
        if (!SOCKET_DRAINING.compareAndSet(this, 0, 1)) return;
        drainSocketCallbacksAcquired();
    }

    /**
     * Consume only ready FIFO callbacks while the current thread holds the
     * serializer. Never releases the ownership bit: a fused READ still owns
     * it until its outer finally runs. An unsealed foreign ticket stops the
     * drain rather than permitting later SENDs to overtake it.
     */
    private void drainSocketCallbacksOwnedReady() {
            for (;;) {
                // Deferred owner-inline completions precede any SEND ticket
                // reserved after their callback. Consume only after write()
                // returned; kickWrite may post another inline completion, so
                // drive this iteratively rather than recursively.
                if (INLINE_WRITE_COMPLETION && _inlineWriteCompletion != 0L) {
                    final long inline = _inlineWriteCompletion;
                    _inlineWriteCompletion = 0L;
                    try {
                        // A mailbox retirement must be atomic. Keeping the
                        // compare-and-clear protects a late/duplicate provider
                        // callback or unexpected CLOSE race from losing an event.
                        // The owner still consumes only after write() returns.
                        if (WRITE_EVENT.compareAndSet(this, inline, 0L))
                            drainWriteCompletion(inline);
                    } catch (Throwable failure) { close(); }
                    continue;
                }
                final int pending = (int) SOCKET_PENDING.getVolatile(this);
                final int slot = (int) _socketHead & SOCKET_MASK;
                final long kind = pending == 0 ? 0L
                        : (long) SOCKET_CALLBACK_SLOT.getAcquire(_socketCallbacks, slot);
                // Consume a completed on-wire WRITE only at an empty or
                // unpublished FIFO head. Published earlier tickets still run
                // in order. No WRITE_COMPLETE ticket exists for this event,
                // so an identical later completion value cannot be mistaken
                // for an old queued callback.
                if (_prefixWriteCompletion != 0L && !_socketPosting
                        && (pending == 0 || kind == 0L)) {
                    final long prefix = _prefixWriteCompletion;
                    _prefixWriteCompletion = 0L;
                    try {
                        if (WRITE_EVENT.compareAndSet(this, prefix, 0L))
                            drainWriteCompletion(prefix);
                    } catch (Throwable failure) { close(); }
                    continue;
                }
                if (pending == 0 || kind == 0L) break;
                final long argument = (long) SOCKET_ARGUMENT_SLOT.getAcquire(_socketArguments, slot);
                final CIOBufferPool.Lease sendLease = kind == SOCKET_SEND_ENQUEUE
                        ? _socketSendArguments[slot] : null;
                _socketSendArguments[slot] = null;
                SOCKET_ARGUMENT_SLOT.setRelease(_socketArguments, slot, 0L);
                SOCKET_CALLBACK_SLOT.setRelease(_socketCallbacks, slot, 0L);
                ++_socketHead;
                // An on-send-progress callback can reenter this owner drain.
                // Preserve the surrounding ticket's posting/inline state.
                final boolean outerExecutingTicket = _socketExecutingTicket;
                if (INLINE_WRITE_COMPLETION) _socketExecutingTicket = true;
                try {
                    if (kind == SOCKET_WRITE_COMPLETE) {
                        if (WRITE_EVENT.compareAndSet(this, argument, 0L))
                            drainWriteCompletion(argument);
                    } else if (kind == SOCKET_SEND_START) {
                        driveWriteOnce();
                    } else if (kind == SOCKET_SEND_ENQUEUE && sendLease != null) {

                        acceptStagedSend(sendLease);
                    } else if (kind == SOCKET_PACKED_ACTION) {
                        acceptPackedAction(argument);
                    } else if (kind != SOCKET_CANCELLED) {
                        close();
                    }
                } catch (Throwable failure) {
                    close();
                } finally {
                    if (INLINE_WRITE_COMPLETION) _socketExecutingTicket = outerExecutingTicket;
                    SOCKET_PENDING.getAndAdd(this, -1);
                }
            }
    }

    /** The caller already holds _socketDraining == 1. */
    private void drainSocketCallbacksAcquired() {
        for (;;) {
            _socketOwner = Thread.currentThread();
            drainSocketCallbacksOwnedReady();

            // A SEND_START notification is not a payload reservation. Every
            // attached SEND was already consumed in ticket order above; any
            // earlier unsealed ticket keeps later SENDs in that FIFO ring.
            // A reserved but not yet published FIFO ticket bars newer SENDs.
            // It cannot hold up an already committed, ready _writing prefix.
            if (COALESCE_SEND_START
                    && (int) SOCKET_START_PENDING.getAcquire(this) != 0
                    && ((int) SOCKET_PENDING.getVolatile(this) == 0
                        || hasCommittedWriteReady())
                    && (int) SOCKET_START_PENDING.getAndSet(this, 0) != 0) {
                try { driveWriteOnce(); }
                catch (Throwable failure) { close(); }
            }

            if (_closed != 0 && (int) SOCKET_PENDING.getVolatile(this) == 0) {
                final long stranded = (long) WRITE_EVENT.getAndSet(this, 0L);
                if (stranded != 0L) {
                    try { drainWriteCompletion(stranded); }
                    catch (Throwable ignored) { close(); }
                }
                try { driveWriteOnce(); }
                catch (Throwable ignored) { close(); }
            }
            
            // The coalesced SEND_START drive above can complete write()
            // inline after the inner loop has already exited. Keep the
            // current owner until the deferred completion is retired.
            if (INLINE_WRITE_COMPLETION && _inlineWriteCompletion != 0L)
                continue;

            final int nextSlot = (int) _socketHead & SOCKET_MASK;
            _socketOwner = null;
            SOCKET_DRAINING.setRelease(this, 0);
            
            // Release/reacquire handshake: a writer that posts just before
            // release sees the old owner, which must recheck this bit; a
            // writer arriving afterwards can itself acquire the token.
            // A deferred start may progress across an unpublished head only
            // when an earlier committed WRITE is ready. Otherwise wait for
            // the publisher instead of spinning on an empty slot.
            final int pending = (int) SOCKET_PENDING.getVolatile(this);
            final boolean startReady = COALESCE_SEND_START
                    && (int) SOCKET_START_PENDING.getAcquire(this) != 0
                    && (pending == 0 || hasCommittedWriteReady());
            final boolean callbackReady = pending != 0
                    && (long) SOCKET_CALLBACK_SLOT.getAcquire(_socketCallbacks, nextSlot) != 0L;
            // A foreign completion may publish the prefix after this owner
            // checked the blocked head but before it released its token.
            // Reacquire rather than losing the sole completion notification.
            final boolean prefixReady = _prefixWriteCompletion != 0L && !_socketPosting;
            if ((!startReady && !callbackReady && !prefixReady)
                    || !SOCKET_DRAINING.compareAndSet(this, 0, 1)) break;
        }
        notifyClosedWhenReleased();
    }

    /**
     * An unpublished FIFO ticket bars later SENDs, not the already committed
     * prefix. A ready _writing lease was selected before that ticket and can
     * be posted without consuming or overtaking the unpublished slot.
     * Check under the existing state lock; never issue provider WRITE in it.
     */
    private boolean hasCommittedWriteReady() {
        _stateLock.lock();
        try {
            return _writing != null && !_writing._ioPending;
        } finally {
            _stateLock.unlock();
        }
    }

    /** SEND registration and next-WRITE selection, executed by the serializer. */
    private void kickWrite() {
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
            final CIOBufferPool.Lease active = _writing;
            if (_closed == 0 && _closing == 0 && active != null && active._ioPending)
                return;
            driveWriteOnce();
            return;
        }
        SOCKET_START_PENDING.setRelease(this, 1);
        drainSocketCallbacks();
    }

    private void driveWriteOnce() {
        CIOBufferPool.Lease current = null;
        boolean gather = false;
        boolean cancelled = false;
        _stateLock.lock();
        try {
            if (_writing != null && !_writing._ioPending) {
                current = _writing;
                cancelled = (_closed != 0);
                if (!cancelled && _gatherCount == 0 && _queued != 0) {
                    if (_gatherFrames == null) {
                        _gatherFrames = new CIOBufferPool.Lease[GATHER_MAX_FRAMES];
                        _gatherBuffers = new ByteBuffer[GATHER_MAX_FRAMES];
                    }
                    int totalBytes = current.wireLength();
                    _gatherFrames[0] = current;
                    _gatherCount = 1;
                    while (_queued != 0 && _gatherCount < GATHER_MAX_FRAMES) {
                        final CIOBufferPool.Lease candidate = _pending[_head];
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

    /** Ordered packet encryption + async I/O scatter/gather WRITE, no coalescing copy. */
    private void postGather() {
        if (_closed != 0) {
            completeGather(false);
            return;
        }
        try {
            boolean preparedAny = false;
            for (int i = 0; i < _gatherCount; i++) {
                final CIOBufferPool.Lease lease = _gatherFrames[i];
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
                _channel.write(_gatherBuffers, 0, _gatherCount, this);
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

    private void completeGather(boolean success) {

        CIOBufferPool.Lease next = null;
        boolean resumeInbound = false;
        _stateLock.lock();
        try {
            if (_gatherCount <= 1 || _writing != _gatherFrames[0]) {
                close();
                return;
            }
            int releasedBytes = 0;
            int releasedFrames = 0;
            for (int i = 0; i < _gatherCount; i++) {
                final CIOBufferPool.Lease frame = _gatherFrames[i];
                releasedBytes += frame.wireLength();
                releasedFrames += frame._frameCount;
                _pool.release(frame);
                _gatherFrames[i] = null;
                _gatherBuffers[i] = null;
            }
            // READ resumes once, after all gather frames have been retired.
            resumeInbound = bytesReleased(releasedBytes, releasedFrames);
            _gatherCount = 0;
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
        if (!success) close();
        else if (next != null) {

            kickWrite();
        }
        if (resumeInbound && _closed == 0) kickRead();
        if (success && _closed == 0) notifySendSlotAvailable();
        notifyClosedWhenReleased();
    }

    private void notifySendSlotAvailable() {
        try { onSendProgress(); } catch (Throwable failure) { close(); }
    }

    private void postWrite(CIOBufferPool.Lease lease) {
        if (_closed != 0) {
            lease._ioPending = false;
            completeWrite(lease, false);
            return;
        }
        try {
            final ByteBuffer bytes = lease.buffer();
            final boolean preparing = !lease._writePrepared;
            if (preparing) {
                final WriteBuffer writer = lease.writer();
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
                _channel.write(bytes, this);
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

    private void completeWrite(CIOBufferPool.Lease lease, boolean success) {

        CIOBufferPool.Lease next = null;
        boolean resumeInbound;
        _stateLock.lock();
        try {
            if (_writing != lease) {
                close();
                return;
            }
            resumeInbound = bytesReleased(lease.wireLength(), lease._frameCount);
            _pool.release(lease);
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

    private boolean isWriteIdleLocked() {
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

    public boolean isOpen() {
        return _closed == 0 && _channel.isOpen();
    }

    /** Transport close callback is emitted after every in-flight I/O lease is released. */
    public boolean isCloseComplete() {
        return (_closeNotified != 0);
    }

    private void notifyClosedWhenReleased() {
        if (_closed == 0 || !_channel.isNativeIoDrained() || !_readReleased || _nativeGamePending
                || _socketDraining != 0 || _socketPending != 0
                || _writeEvent != 0L) return;
        _stateLock.lock(); try {
            if (_writing != null || _queued != 0 || _reservedDirect != 0 || _stagedSends != 0) return;
        } finally { _stateLock.unlock(); }
        if (!CLOSE_NOTIFIED.compareAndSet(this, 0, 1)) return;
        clearCipher();
        if (_closedCallback != null) {
            try {
                _closedCallback.onClosed(this);
            } catch (Throwable ignored) {
                // Never retain a closed socket when a lifecycle observer fails.
            }
        }
    }

    @Override
    public void close() {
        if (!CLOSED.compareAndSet(this, 0, 1)) return;
        onTransportClosing();
        cancelQueuedNativePacket();
        _networkDeadline = null;
        // Failure-only report, outside state-lock/serializer ownership. A
        // rejected frame can be the cause of this disconnect, but is not
        // treated as proof: the peer may have closed for a different reason.
        final int reject = _lastSendReject;
        if (reject != 0) {
            // A lease cap includes queued, staged and encoder-reserved frames.
            // Report their separate values only on failure to distinguish a
            // stalled serializer from a slow physical WRITE without adding
            // counters or allocations on the normal SEND path.
            final int wireBytes, leases, queued, staged, encoders, active, logical;
            _stateLock.lock();
            try {
                wireBytes = _outstandingWireBytes + _reservedWireBytes;
                leases = physicalSendLeasesLocked();
                queued = _queued;
                staged = _stagedSends;
                encoders = _reservedDirect;
                active = _writing == null ? 0 : _gatherCount > 0 ? _gatherCount : 1;
                logical = _outstandingFrames;
            } finally { _stateLock.unlock(); }
            final long head = _socketHead;
            final Thread owner = _socketOwner;
            final long headKind = (long) SOCKET_CALLBACK_SLOT.getAcquire(
                    _socketCallbacks, (int) head & SOCKET_MASK);
            LOG.warning("[CIO SEND] closing socket after rejection reason=" + reject
                    + " remote=" + _remoteAddress
                    + " pendingWireBytes=" + wireBytes
                    + " queuedLeases=" + leases
                    + " queued=" + queued + " staged=" + staged
                    + " encoders=" + encoders + " writing=" + active
                    + " logicalFrames=" + logical
                    + " serializerPending=" + (int) SOCKET_PENDING.getVolatile(this)
                    + " serializerDraining=" + (int) SOCKET_DRAINING.getVolatile(this)
                    + " serializerHeadKind=" + headKind
                    + " serializerOwner=" + (owner == null ? "none" : owner.getName())
                    + " readOwner=" + _readSocketOwnerActive
                    + " poolSmallUsed=" + _pool.usedSmallSendSlots()
                    + "/" + _pool.smallSendSlots()
                    + " poolLargeUsed=" + _pool.usedLargeSendSlots()
                    + "/" + _pool.largeSendSlots());
        }
        ON_DRAINED.setVolatile(this, null);
        _channel.close();
        boolean drainProducer;
        _stateLock.lock(); try {
            _deferredClosePacket = null;
            _stagedPacketPack = null;
            drainProducer = publishProducerLeaseLocked();
            int releasedBytes = 0;
            int releasedFrames = 0;
            while (_queued != 0) {
                final CIOBufferPool.Lease lease = _pending[_head];
                _pending[_head] = null;
                if (++_head == _pending.length) _head = 0;
                --_queued;
                releasedBytes += lease.wireLength();
                releasedFrames += lease._frameCount;
                _pool.release(lease);
            }
            if (releasedFrames != 0) bytesReleased(releasedBytes, releasedFrames);
        } finally { _stateLock.unlock(); }
        if (drainProducer) drainSocketCallbacks();
        _stateLock.lock(); try {
            if (_readActive == 0) releaseReadWhenIdle();
        } finally { _stateLock.unlock(); }
        finishReadIfClosed();
        if (!_stateLock.isHeldByCurrentThread()) kickWrite();
        notifyClosedWhenReleased();
    }
}
