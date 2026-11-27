package l1j.server.server.model.network.core.connection;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.Account;
import l1j.server.server.Opcodes;
import l1j.server.server.auth.login.LoginController;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.inventory.warehouse.ClanWarehouse;
import l1j.server.server.model.inventory.warehouse.WarehouseManager;
import l1j.server.server.model.network.buffer.Assemble;
import l1j.server.server.model.network.buffer.Disassemble;
import l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer;
import l1j.server.server.model.network.core.broadcast.CSharedPacketFormat;
import l1j.server.server.model.network.core.config.CIOConfig;
import l1j.server.server.model.network.core.connection.CIOBufferEx.CIOFrame;
import l1j.server.server.model.network.core.sc.packet.SC_Disconnect;
import l1j.server.server.model.network.core.session.CSessionLifecycle;
import l1j.server.server.model.network.core.session.DominationHybrid;
import l1j.server.server.model.network.core.transport.selector.CSelectorTransport.Connection;
import l1j.server.server.model.network.packet.CBytesPacket;
import l1j.server.server.model.network.packet.CServerPacket;
import l1j.server.server.model.network.packet.IPacketWriter;
import l1j.server.server.model.network.packet.UserPacketCallbackMap;
import l1j.server.server.model.network.safe.CPacketFilter;
import l1j.server.server.model.network.utill.CConnectionState;
import l1j.server.server.model.shop.NpcShopView;
import l1j.server.server.model.shop.PlayerShopView;
import l1j.server.server.packets.Protocol.MainServer.PlaySupport.ClientAutomationSession;
import l1j.server.server.packets.Protocol.MainServer.PlaySupport.PlaySupport2019;
import l1j.server.server.packets.Protocol.MainServer.PlaySupport.PssConfig;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_CRAFT_LIST_ALL_ACK;
import l1j.server.server.packets.Protocol.MainServer.Proto.Stream.ProtoOutputStream;
import l1j.server.server.storage.CacheD;

/**
 * Game packet framing and the per-socket 512-slot callback serializer.
 * WRITE completions and SEND starts follow the connection's ticket/FIFO
 * execution rule. READ framing retains its independent direct callback path;
 * game state remains subject to the session and creature ownership rules.
 */
public final class CUserSocket extends CSocket {
    private static final boolean[] SESSION_OPCODE = new boolean[256];
    static {
        SESSION_OPCODE[Opcodes.C_LOGIN & 0xFF] = true;
        SESSION_OPCODE[Opcodes.C_ENTER_WORLD & 0xFF] = true;
        SESSION_OPCODE[Opcodes.C_CREATE_CUSTOM_CHARACTER & 0xFF] = true;
        SESSION_OPCODE[Opcodes.C_DELETE_CHARACTER & 0xFF] = true;
        SESSION_OPCODE[Opcodes.C_RESTART & 0xFF] = true;
        SESSION_OPCODE[Opcodes.C_QUIT & 0xFF] = true;
        SESSION_OPCODE[Opcodes.C_LOGOUT & 0xFF] = true;
        SESSION_OPCODE[Opcodes.C_CHANNEL & 0xFF] = true;
        SESSION_OPCODE[Opcodes.C_EXTENDED_PROTOBUF & 0xFF] = true;
        SESSION_OPCODE[Opcodes.C_EXTENDED & 0xFF] = true;
        SESSION_OPCODE[Opcodes.C_HYPERTEXT_INPUT_RESULT & 0xFF] = true;
    }
    public static boolean isSessionOpcode(int opcode) {
        return (opcode & ~255) == 0 && SESSION_OPCODE[opcode];
    }
    static boolean allowed(int opcode, CConnectionState state, boolean hasAccount,
                           boolean hasCharacter, boolean ready) {
        if (opcode < 0 || opcode > 255 || state == null || state == CConnectionState.DISCONNECTED) return false;
        if (opcode == (Opcodes.C_ALIVE & 255) || opcode == (Opcodes.C_QUIT & 255)
                || opcode == (Opcodes.C_EXTENDED & 255)
                || opcode == (Opcodes.C_EXTENDED_PROTOBUF & 255)
                || opcode == (Opcodes.C_CHANNEL & 255)
                || opcode == (Opcodes.C_HYPERTEXT_INPUT_RESULT & 255)) return true;
        if (opcode == (Opcodes.C_LOGIN & 255))
            return state == CConnectionState.CONNECTED && !hasAccount && !hasCharacter;
        if (opcode == (Opcodes.C_READ_NEWS & 255))
            return !hasCharacter;
        if (opcode == (Opcodes.C_CREATE_CUSTOM_CHARACTER & 255)
                || opcode == (Opcodes.C_DELETE_CHARACTER & 255)
                || opcode == (Opcodes.C_ENTER_WORLD & 255))
            return hasAccount && !hasCharacter && (state == CConnectionState.AUTHENTICATED
                    || state == CConnectionState.OUT_GAME);
        if (opcode == (Opcodes.C_RESTART & 255) || opcode == (Opcodes.C_LOGOUT & 255))
            return hasAccount;
        // L1 game handlers require a fully published active player. Character
        // loading packets use the multiplexed exceptions above.
        return state == CConnectionState.IN_GAME && hasAccount && hasCharacter && ready;
    }

    @FunctionalInterface
    public interface Closed {
        void onClosed(CUserSocket connection);
        default void onClosing(CUserSocket connection) {}
        default void onCleanupComplete(CUserSocket connection) {}
    }

    private final CIOServer registry;
    // Transport identity is guarded by the CIOServer registry monitor.
    int transportAccount;
    long transportMac;
    boolean transportEntered;
    boolean cleanupIpReserved; // shared IP registry monitor
    private volatile long loginDeadlineNs;

    private static final Logger LOG = Logger.getLogger(CUserSocket.class.getName());
    // Diagnostics only; no new per-connection atomics or per-SEND allocation.
    public static final int REJECT_WIRE_BYTES = 1;
    public static final int REJECT_LOCAL_FRAMES = 2;
    public static final int REJECT_SERIALIZER = 3;
    public static final int REJECT_POOL = 4;
    public static final int REJECT_ENCODING = 5;
    private boolean rejectDirectSend(boolean optional, int reason) {
        if (optional) return false;
        return rejectSend(reason);
    }



    // Written only on rejected sends. No additional work in the SEND hot path.
    private volatile int _lastSendReject;
    private boolean rejectSend(int reason) {
        _lastSendReject = reason;
        return false;
    }
    // Claim an idle serializer owner directly when no earlier reservation exists.
    // The normal FIFO drain/release handshake still owns completion and closing.
    private long deadlinePriorProgress,deadlineStalledSince;
    private final boolean _gameSession;
    // Progress is measured by completed positive WRITE bytes, not admissions.
    // The native network wheel performs the periodic deadline check.
    static final long NETWORK_WRITE_STALL_NANOS = TimeUnit.SECONDS.toNanos(30);
    private static final long NETWORK_GRACEFUL_CLOSE_NANOS = TimeUnit.SECONDS.toNanos(30);
    private static final long NETWORK_PARTIAL_READ_NANOS = TimeUnit.SECONDS.toNanos(30);
    private volatile long _gracefulCloseStartedNs;
    // Zero for ordinary graceful close; a denial retains its original grace.
    // The WRITE completion and native deadline inspect this under _stateLock.
    private long _closeNotBeforeNs;
    private volatile boolean deadlinesConfigured;

    public void configureTransportDeadlines(boolean loginTimeout) {
        final int loginMs = loginTimeout ? CIOConfig.loginTimeoutMs() : 0;
        if (loginMs > 0) loginDeadlineNs = System.nanoTime() + loginMs * 1_000_000L;
        deadlinesConfigured = true;
        if (!requestTransportDeadline(loginMs == 0 ? 5000 : Math.min(loginMs, 5000)))
            throw new IllegalStateException("Network deadline admission failed");
    }

    @Override public void onRawDeadline() {
        if (!isOpen()) return;
        long login = loginDeadlineNs;
        long remaining = login == 0 ? Long.MAX_VALUE : login - System.nanoTime();
        int delay = transportNetworkDeadline();
        if (delay < 0 || remaining <= 0) close();
        else if (delay > 0) peer.scheduleRawDeadline(Math.min(delay, 1 + (remaining - 1) / 1_000_000));
    }

    public void onAccountChanged(l1j.server.server.Account account) {
        if (!isOpen()) return;
        final int accountId = account == null ? 0 : account.getAccountId();
        if ((account != null && accountId <= 0) || !registry.setAccount(this, accountId)) {
            close();
            throw new IllegalStateException("Account identity rejected (missing MAC, invalid account or closed socket)");
        }
    }
    public void onCharacterChanged(l1j.server.server.model.Instance.L1PcInstance pc) {
        if (isOpen() && !registry.setEntered(this, pc != null)) {
            close();
            throw new IllegalStateException("Entering-character account registration rejected");
        }
    }
    /** Only a server-side provider that verified the identity may call this. */
    public boolean bindVerifiedMacIdentity(long identity) {
        return identity != 0 && isOpen() && registry.setMac(this, identity);
    }
    public void onSessionStateChanged(l1j.server.server.model.network.utill.CConnectionState state) {
        if (!isOpen()) return;
        if (!registry.setState(this, state.ordinal())) {
            close();
            throw new IllegalStateException("Session registry transition rejected");
        }
        if (state.ordinal() != 0) loginDeadlineNs = 0;
    }
    private int transportNetworkDeadline() {
        // An earlier request can overlap the currently running wheel callback.
        // Share the existing socket state lock with SEND/grace transitions.
        _stateLock.lock();
        try {
            if(!isOpen())return 0;
            long now=System.nanoTime(),progress=networkWriteProgress();
            if(hasPendingNetworkSend()) {
                if(deadlineStalledSince==0||deadlinePriorProgress!=progress)deadlineStalledSince=now;
                if(now-deadlineStalledSince>=NETWORK_WRITE_STALL_NANOS){
                    return -1;
                }
            }else deadlineStalledSince=0;
            deadlinePriorProgress=progress;
            if (networkDeadlineExpired(now)) {
                return -1;
            }
            return (int)nextNetworkDeadlineDelayMs(now,5000);
        }finally{_stateLock.unlock();}
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
    // Only SEND-watermark transitions touch the pause start under _stateLock.
    // Pause time is excluded from the current partial-frame deadline on resume.

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
    private IPacketWriter<CUserSocket> _deferredClosePacket;

    private final Closed _closedCallback;
    // Volatile primitives avoid five standalone Atomic* objects per connection.
    // VarHandles preserve CAS/getAndAdd semantics when completion threads race.
    private volatile int _closeNotified;

    // Defer automatic SEND burst flushing until the current READ
    // buffer pass ends, but only if that buffer holds multiple complete frames.
    // Forced flushSendBurst(), close and READ-pressure boundaries stay intact.
    // Most window-1 game requests are below this size. Do not inspect the
    // packet header or enter a burst scope unless the READ holds enough data
    // for meaningful cross-request batching.
    // P8: a completed READ may directly claim the idle READ pump token.
    // The already-owned/inline callback path still publishes its mailbox event.
    // READ completion is consumed only by the existing per-socket READ pump.
    // 0=empty, 1=failure, >=2=successful completion (transferred bytes + 2).
    // This keeps _readPending and zero-completion handling off foreign I/O threads.
    private static final VarHandle CLOSE_NOTIFIED, LIFECYCLE, ON_DRAINED;
    static {
        try {
            final MethodHandles.Lookup lookup = MethodHandles.lookup();
            CLOSE_NOTIFIED = lookup.findVarHandle(CUserSocket.class, "_closeNotified", int.class);
            LIFECYCLE = lookup.findVarHandle(CUserSocket.class, "socketLifecycle", int.class);
            ON_DRAINED = lookup.findVarHandle(CUserSocket.class, "_onDrained", Runnable.class);
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
    // Retire a synchronous owner-thread WRITE callback only after write()
    // returns. Profiling keeps the old ticket path for comparable traces.
    // Experimental P6-A: one already-published first Action SEND may be
    // consumed by the existing SendBurst.flush() instead of entering the
    // serializer twice. Disabled until matched TCP performance checks pass.
    private static final boolean DEFER_OWNED_BURST_DRAIN = true;
    // P6-B A/B: select the sole ready WRITE in acceptStagedSend's existing
    // admission lock. Never invoke the NIO.2 provider under that lock.
    // Keep the gather, queued, and already-pending SEND_START paths unchanged.
    private static final boolean FUSE_STAGED_WRITE_SELECTION = true;
    private CIOBufferEx _producerLease;
    private long _producerTicket;
    // Only payload reservations, not WRITE_COMPLETE/SEND_START control tickets,
    // break producer-local packing. All payload reservations hold _stateLock.
    private int _payloadOrderEpoch;
    private int _producerOrderEpoch;
    private static final int BURST_MAX_BYTES = 8192;
    // READ-pass-only WRITE start threshold. Does not alter action/producer packing.
    private static final int READ_PASS_WRITE_START_BYTES = 32 * 1024;
    // Scatter/gather covers multiple independent 16K leases; this is not a lease size.
    private static final int BURST_INITIAL_SOCKETS = 32;
    private static final int BURST_MAX_SOCKETS = 256;

    private static final class SendBurst {
        // A burst never migrates between threads: the owning thread is fixed
        // when its ThreadLocal holder is first created.
        final Thread thread = Thread.currentThread();
        CUserSocket[] sockets = new CUserSocket[BURST_INITIAL_SOCKETS];
        int size;
        int depth;
        int readPassDepth;
        boolean flushing;

        boolean attach(CUserSocket socket) {
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
                    final CUserSocket socket = sockets[i];
                    sockets[i] = null;
                    final boolean publish;
                    boolean claimed = false;
                    CIOBufferEx fusedWrite = null;
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

    @Override final void beginReadPassSendBurst() {
        beginSendBurst();
        ++SEND_BURST.get().readPassDepth;
    }

    @Override final void endReadPassSendBurst() {
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
    // Frames admitted to the 512-slot serializer but not yet attached to SEND.
    // One still-unconsumed SEND_ENQUEUE can absorb consecutive small packets
    // before the socket FIFO owner encrypts it. State-lock protected. The
    // later tickets remain CANCELLED FIFO positions, never bypassed.
    private CIOBufferEx _stagedPacketPack;
    private long _stagedPacketFirstTicket;
    private long _stagedPacketLastTicket;
    // Byte-based connection-local backpressure, independent of the physical lease bound.
    // Counts all committed wire bytes including gather writes and packed frames.
    // Flow-control stops inbound processing before a slow client accumulates
    // hundreds of 16K leases. The wire-byte and physical SEND lease caps are
    // preserved; logical frame pressure is READ backpressure, not a lease cap.
    // Uncommitted direct producers own pooled leases too. Include their maximum
    // wire sizes in admission and READ watermark decisions until commit/abort.

    // Reserved producer slots for existing direct-frame serializers. A reservation
    // survives the serializer and is consumed atomically by commit or abort.
    // State-lock-owned first Action lease: only while a P6-A SEND_ENQUEUE is
    // published but has not yet been accepted by its FIFO serializer callback.
    // Do not store a lease after commit: it may be recycled by the pool.
    private CIOBufferEx _stagedBurstAction;
    // Per-connection fixed gather array: no byte-copy and no batch allocation.
    // Native gathering WRITE maps to WSABUF arrays on Windows IOCP.
    // No new staging buffers or copies: select a wider gather only when a
    // connection already has enough committed SEND leases. Default unchanged.
    // On a partial asynchronous WRITE the current lease is still the oldest
    // committed output. Reposting from the existing serializer avoids
    // reacquiring the state lock, without adding a completion worker or
    // bypassing any reserved FIFO ticket.
    // P19: retain the serializer-driven partial WRITE progression; no runtime flag.
    // Medium S-packets share the same ordered wire batch when space permits.
    private static final int PACK_MAX_FRAME_BYTES = 2048;

    /* READ lease is released only after the last completion callback, never during a pending read. */
    // Only the READ pump touches this; preserves buffered frames if an ACK races pause.
    // READ pump only. Do not add a ThreadLocal.get() to single-frame dispatch.

    // Native completion publishes into the existing READ pump, without a
    // JDK asynchronous channel or provider adapter.
    CUserSocket(CIOServer registry, Connection peer, long id, CIOBufferPool pool,
            int maxQueuedFrames, Closed closedCallback, boolean gameSession) {
        super(validateSocketArguments(registry, peer, pool, maxQueuedFrames), id, pool, maxQueuedFrames);
        this.registry = registry;
        _closedCallback = closedCallback;
        _gameSession = gameSession;
    }

    private static Connection validateSocketArguments(CIOServer registry, Connection peer,
            CIOBufferPool pool, int maxQueuedFrames) {
        if (registry == null || peer == null || pool == null || maxQueuedFrames < 1)
            throw new IllegalArgumentException("invalid game socket arguments");
        return peer;
    }

    private static final int CLOSE_STARTED = 1, CLEANUP_COMPLETE = 2, SERVER_DRAIN_REPORTED = 4,
            LOGOUT_STARTED = 8, IO_DRAINED = 16, CRAFT_LIST_SENDING = 32, FORCED_CHARACTER_CLOSE = 64;
    private volatile int socketLifecycle;
    private record ForcedLogout(L1PcInstance pc, int stamp) { }
    private volatile ForcedLogout forcedLogout;
    private volatile CUserSocketReleaseTask terminalRelease;
    private static final AtomicReferenceFieldUpdater<CUserSocket, ForcedLogout> FORCED_LOGOUT =
            AtomicReferenceFieldUpdater.newUpdater(CUserSocket.class, ForcedLogout.class, "forcedLogout");

    private boolean markLifecycle(int event) {
        return ((int) LIFECYCLE.getAndBitwiseOr(this, event) & event) == 0;
    }

    public boolean isRetiringItemTransfers() {
        return !isClientCleanupComplete() && (_closed != 0 || _closing != 0
                || (socketLifecycle & (CLOSE_STARTED | LOGOUT_STARTED | FORCED_CHARACTER_CLOSE)) != 0);
    }

    public void onItemTransferFinished(L1PcInstance pc) {
        final CUserSocketReleaseTask release = terminalRelease;
        if (release != null && release.pc == pc) release.requestRelease();
    }

    /** An already closing actor is retired by the exact socket's release task. */
    public boolean requiresTransportLogout(L1PcInstance pc) {
        if (_activeChar != pc || !isRetiringItemTransfers()) return false;
        final CUserSocketReleaseTask release = terminalRelease;
        return release == null || release.pc != pc || release.logoutThread != Thread.currentThread();
    }

    boolean markServerDrainReported() { return markLifecycle(SERVER_DRAIN_REPORTED); }
    boolean isServerDrainReported() { return (socketLifecycle & SERVER_DRAIN_REPORTED) != 0; }
    public boolean isCraftListSending() { return (socketLifecycle & CRAFT_LIST_SENDING) != 0; }
    public boolean beginCraftListSend() {
        int state;
        do {
            state = socketLifecycle;
            if ((state & (CLOSE_STARTED | LOGOUT_STARTED | CRAFT_LIST_SENDING)) != 0
                    || !isConnected()) return false;
        } while (!LIFECYCLE.compareAndSet(this, state, state | CRAFT_LIST_SENDING));
        return true;
    }
    public void endCraftListSend() { LIFECYCLE.getAndBitwiseAnd(this, ~CRAFT_LIST_SENDING); }

    public void onTransportClosing() {
        if (!markLifecycle(CLOSE_STARTED)) return;
        endCraftListSend();
        if (_gameSession) {
            _gameSessionReady = false;
            playerShopView = null;
            npcShopView = null;
        }
        if (_closedCallback != null) {
            try { _closedCallback.onClosing(this); }
            catch (Throwable failure) { LOG.log(Level.WARNING, "User-socket closing observer failed", failure); }
        }
    }
    void onTransportTerminated() {
        onTransportClosing();
        loginDeadlineNs = 0;
        markLifecycle(IO_DRAINED);
        try { cancelQueuedWrite(); }
        finally {
            close(false);
            notifyClosedWhenReleased();
        }
    }

    long[] selectorReport() {
        var remote = transportAddress();
        byte[] ip = remote.getAddress().getAddress();
        long address = (ip[0] & 255L) << 24 | (ip[1] & 255L) << 16 | (ip[2] & 255L) << 8 | ip[3] & 255L;
        return new long[] {id, id, 0, _closed != 0 ? 1 : 0, (socketLifecycle & IO_DRAINED) != 0 ? 1 : 0, 0,
                isTransportReadPending() ? 1 : 0, isTransportWritePending() ? 1 : 0, 0, 2, address, remote.getPort()};
    }

    public boolean isClientCleanupComplete() { return (socketLifecycle & CLEANUP_COMPLETE) != 0; }
    public void onClientCleanupComplete() {
        if (!markLifecycle(CLEANUP_COMPLETE)) return;
        if (_closedCallback != null) {
            try { _closedCallback.onCleanupComplete(this); }
            catch (Throwable failure) { LOG.log(Level.WARNING, "User-socket cleanup observer failed", failure); }
        }
    }

    public CUserSocket client() { return _gameSession ? this : null; }

    public boolean isClientConnected() {
        return isOpen() && _closing == 0 && _disconnecting == 0;
    }

    public int getEstimateQueueSize() { return pendingSendFrames(); }
    public void read() { startRead(); }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public void enqueuePacket(IPacketWriter packet) {
        if (packet == null || !isClientConnected()) return;
        if (!_gameSession) close();
        else if (!tryWritePacket(this, packet)) close();
    }

    /**
     * Graceful close: 0=open, 1=waiting to admit final, 3=encoding final,
     * 2=draining. A WRITE completion must never close before the final encoder
     * attaches its serializer ticket. This path is distinct from
     * close(), which remains the immediate error/cancellation path.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void requestClientClose(IPacketWriter packet) {
        if (beginClientClose()) finishClientClose(packet);
    }

    private boolean beginClientClose() {
        if (_disconnecting != 0 || !isOpen()
                || !CLOSING.compareAndSet(this, 0, 1)) return false;
        _gracefulCloseStartedNs = System.nanoTime();
        return true;
    }

    private void finishClientClose(IPacketWriter<CUserSocket> packet) {
        if (!_gameSession) {
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

    public CAsyncSendConstBuffer.Source createConstBuffer(byte[] wire) {
        if (wire == null || wire.length < 3 || wire.length > 0xffff
                || ((wire[0] & 255) | ((wire[1] & 255) << 8)) != wire.length) return null;
        CAsyncSendConstBuffer.Source source = createPooledWritableConstBuffer(wire.length);
        if (source == null) return null;
        try {
            source.mutableWire.put(wire);
            source.sealJava(wire.length);
            return source;
        } catch (RuntimeException | Error failure) {
            releaseSharedSourceAfterFailure(source, failure);
            throw failure;
        }
    }

    public CAsyncSendConstBuffer.Source createPooledActionConstBuffer(int opcode, int objectId, int actionId) {
        return createPooledFormattedConstBuffer(1, opcode, objectId, actionId, 0, 0);
    }

    public CAsyncSendConstBuffer.Source createPooledMoveConstBuffer(int opcode, int objectId, int x, int y, int heading) {
        return createPooledFormattedConstBuffer(2, opcode, objectId, x, y, heading);
    }

    public CAsyncSendConstBuffer.Source createPooledFormattedConstBuffer(int kind, int opcode, int p0, int p1, int p2, int p3) {
        CAsyncSendConstBuffer.Source source = createPooledWritableConstBuffer();
        if (source == null) return null;
        try {
            source.mutableWire.position(2);
            CSharedPacketFormat.writeFormatted(source.mutableWire, kind, opcode, p0, p1, p2, p3);
            source.sealJava(source.mutableWire.position());
            return source;
        } catch (RuntimeException | Error failure) {
            releaseSharedSourceAfterFailure(source, failure);
            throw failure;
        }
    }

    public CAsyncSendConstBuffer.Source createPooledExtendedConstBuffer(
            int kind, int opcode, int p0, int p1, int p2, int p3, int p4, int p5, int p6, int p7, int p8, int p9, int p10) {
        CAsyncSendConstBuffer.Source source = createPooledWritableConstBuffer();
        if (source == null) return null;
        try {
            source.mutableWire.position(2);
            CSharedPacketFormat.writeExtended(source.mutableWire, kind, opcode, p0, p1, p2, p3, p4, p5, p6, p7, p8, p9, p10);
            source.sealJava(source.mutableWire.position());
            return source;
        } catch (RuntimeException | Error failure) {
            releaseSharedSourceAfterFailure(source, failure);
            throw failure;
        }
    }

    private static void releaseSharedSourceAfterFailure(CAsyncSendConstBuffer.Source source, Throwable failure) {
        try { source.release(); }
        catch (RuntimeException | Error cleanup) {
            if (cleanup != failure) failure.addSuppressed(cleanup);
        }
    }

    public CAsyncSendConstBuffer.Source createPooledWritableConstBuffer() {
        return createPooledWritableConstBuffer(CIOBufferPool.SMALL_SEND_BYTES);
    }

    public CAsyncSendConstBuffer.Source createPooledWritableConstBuffer(int capacity) {
        CIOBufferEx lease = _pool.acquireSharedSource(capacity);
        if (lease == null) return null;
        try { return new CAsyncSendConstBuffer.Source(_pool, lease); }
        catch (RuntimeException | Error failure) {
            try { _pool.release(lease); }
            catch (RuntimeException | Error cleanup) {
                if (cleanup != failure) failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    public void sealPooledWritableConstBuffer(CAsyncSendConstBuffer.Source source, int length) {
        source.sealJava(length);
    }


    /** Frozen bytes need no encoder reservation; their SEND_ENQUEUE still
     * takes an ordinary FIFO ticket behind any unpublished earlier encoder.
     */
    

    /** Drain accepted SENDs and retain a bounded minimum grace before closing. */
    public void requestClientCloseAfter(long delayMs) {
        if (delayMs < 0L || delayMs > 30_000L)
            throw new IllegalArgumentException("close grace must be between 0 and 30000 ms");
        final boolean deadlineConfigured;
        final boolean publish;
        _stateLock.lock();
        try {
            if (_closed != 0 || _disconnecting != 0
                    || !CLOSING.compareAndSet(this, 0, 1)) return;
            final long now = System.nanoTime();
            _gracefulCloseStartedNs = now;
            _closeNotBeforeNs = now + TimeUnit.MILLISECONDS.toNanos(delayMs);
            publish = publishProducerLeaseLocked();
            deadlineConfigured = deadlinesConfigured;
            CLOSING.setRelease(this, 2);
        } finally { _stateLock.unlock(); }
        if (publish) drainSocketCallbacks();
        flushSendBurst();
        kickWrite();
        onSendProgress();
        // Bring the existing Selector deadline forward for this grace period.
        // Failed deadline admission must not leave an orphaned denied peer.
        if (_closed == 0 && (!deadlineConfigured || !requestTransportDeadline(Math.max(1L, delayMs)))) close();
    }

    public boolean beginClientDisconnect() {
        if (!DISCONNECTING.compareAndSet(this, 0, 1)) return false;
        if (_gracefulCloseStartedNs == 0L) _gracefulCloseStartedNs = System.nanoTime();
        _closing = 1;
        ON_DRAINED.setVolatile(this, null);
        endCraftListSend();
        return true;
    }

    public void finishClientDisconnect() { close(); }
    public void onSendDrainedOwner() { onSendProgress(); }
    public boolean hasDrainWorkOnOwner() { return getEstimateQueueSize() != 0; }

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

    @Override final void onSendProgress() {
        // Admission is blocked during close. An accepted encoder reservation
        // remains visible until it has attached its FIFO ticket.
        if (_closing == 0 && ON_DRAINED.getVolatile(this) == null) return;
        IPacketWriter<CUserSocket> finalPacket = null;
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
                accepted = tryWritePacketInternal(this, finalPacket, true);
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
            // The provider drains its own I/O only. The game FIFO must be idle before handing off closure.
            if (closeNow) closeGracefully();
        }
    }

    /**
     * Direct-frame entry point compatible with CUserSocket's existing packet API.
     * Reserve one bounded queue position before exposing the writable buffer.
     * The reusable handle is owned by its pooled lease, not allocated per packet.
     */
    public CIOFrame beginDirectFrame(int maxWireBytes) {
        return beginDirectFrame(maxWireBytes, 0, false);
    }

    /** Original SafeSend policy adapted to L1 direct frames. Only optional
     * payloads may use this API. Admission includes outstanding and reserved
     * bytes under one lock; null never means that the payload was sent.
     */
    public CIOFrame beginSafeDirectFrame(int maxWireBytes, int marginBytes) {
        if (maxWireBytes <= CIOBufferPool.HEADER_BYTES || maxWireBytes > CIOBufferPool.MAX_WIRE_BYTES
                || marginBytes < 0 || marginBytes > MAX_PENDING_WIRE_BYTES)
            throw new IllegalArgumentException("invalid SafeSend size or margin");
        return beginDirectFrame(maxWireBytes, marginBytes, true);
    }

    private CIOFrame beginDirectFrame(int maxWireBytes, int marginBytes, boolean optional) {
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
                    || !canAdmitBytes(Math.max(maxWireBytes, marginBytes))) {
                rejectDirectSend(optional, REJECT_WIRE_BYTES);
                refused = true;
            } else {
                if (physicalSendFullLocked()) {
                    // Compaction is only an admission recovery step; success
                    // MUST continue into ordinary ticket/byte reservation.
                    // The old else-if silently skipped that reservation and
                    // made commitDirectFrame() decrement the counters below 0.
                    compactReadyStagedPacketsLocked();
                    if (physicalSendFullLocked()) {
                        rejectDirectSend(optional, REJECT_LOCAL_FRAMES);
                        refused = true;
                    }
                }
                // A frame reserves FIFO BEFORE it is encoded, even when
                // the producer is also the current callback owner.
                if (!refused) for (;;) {
                    final int pending = (int) SOCKET_PENDING.getVolatile(this);
                    if (pending >= SOCKET_REJECT_AT) {
                        rejectDirectSend(optional, REJECT_SERIALIZER);
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
        if (refused) {
            // A packet that cannot be admitted must never look like a successful SEND.
            if (!optional) close();
            return null;
        }

        CIOBufferEx lease = null;
        try {
            // Draining a published producer can run a close callback. Include
            // it in rollback coverage for the new reservation.
            if (publishProducer) drainSocketCallbacks();
            if (_closed == 0) {
                lease = beginOutboundFrame(maxWireBytes);
                if (lease != null) {
                    final CIOFrame frame = lease.beginDirectFrame(this);
                    frame.setReservation(maxWireBytes, slot, ticket);
                    frame.setOptional(optional);
                    return frame;
                }
                rejectDirectSend(optional, REJECT_POOL);
            }
        } catch (Throwable failure) {
            rejectSend(REJECT_ENCODING);
            // The original encoder/pool failure takes precedence; a Native
            // lease-release failure must not skip socket close or replace it.
            try { rollbackDirectBegin(maxWireBytes, slot, lease); }
            catch (Throwable cleanup) {
                if (cleanup != failure) failure.addSuppressed(cleanup);
            }
            try { close(); }
            catch (Throwable closing) {
                if (closing != failure) failure.addSuppressed(closing);
            }
            throw failure;
        }
        try { rollbackDirectBegin(maxWireBytes, slot, lease); }
        catch (Throwable cleanup) {
            close();
            throw cleanup;
        }
        if (!optional) close();
        return null;
    }

    private void cancelReservedTicketLocked(int slot) {
        if (slot < 0) return;
        _socketSendArguments[slot] = null;
        SOCKET_ARGUMENT_SLOT.setRelease(_socketArguments, slot, 0L);
        SOCKET_CALLBACK_SLOT.setRelease(_socketCallbacks, slot, SOCKET_CANCELLED);
    }

    private void rollbackDirectBegin(int bytes, int slot,
            CIOBufferEx lease) {
        boolean resumeRead = false;
        Throwable failure = null;
        _stateLock.lock();
        try {
            --_reservedDirect;
            _reservedWireBytes -= bytes;
            cancelReservedTicketLocked(slot);
            try {
                if (lease != null && lease.reserved()) abortOutboundFrame(lease);
            } catch (Throwable releaseFailure) {
                failure = releaseFailure;
            }
            try { resumeRead = updateSendReadPauseLocked(); }
            catch (Throwable updateFailure) {
                if (failure == null) failure = updateFailure;
                else if (failure != updateFailure) failure.addSuppressed(updateFailure);
            }
        } finally { _stateLock.unlock(); }
        // A failed release must never strand a CANCELLED serializer ticket.
        if (slot >= 0) try { drainPublishedSocketCallbacks(); }
        catch (Throwable drainFailure) {
            if (failure == null) failure = drainFailure;
            else if (failure != drainFailure) failure.addSuppressed(drainFailure);
        }
        if (resumeRead) try { kickRead(); }
        catch (Throwable readFailure) {
            if (failure == null) failure = readFailure;
            else if (failure != readFailure) failure.addSuppressed(readFailure);
        }
        try { notifyClosedWhenReleased(); }
        catch (Throwable notifyFailure) {
            if (failure == null) failure = notifyFailure;
            else if (failure != notifyFailure) failure.addSuppressed(notifyFailure);
        }
        if (failure instanceof RuntimeException runtime) throw runtime;
        if (failure instanceof Error error) throw error;
    }

    public boolean commitDirectFrame(CIOFrame frame) {
        if (frame == null) return false;
        final CIOBufferEx lease = frame.ioLease(this);
        final int reservedBytes = frame.reservedBytes();
        final int slot = frame.reservedTicket();
        final long ticket = frame.reservedTicketId();
        final boolean optional = frame.optional();
        boolean accepted = false;
        boolean packed = false;
        boolean resumeRead = false;
        try {
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
                    accepted = packed || stageOutboundFrameLocked(lease, false, slot, optional);
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
                                packed = (byte) SOCKET_CALLBACK_SLOT.getAcquire(
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
        if (!accepted && !optional) {
            // The caller's finally aborts the still-owned frame/ticket.
            close();
        }
        return accepted;
        } catch (Throwable failure) {
            if (!accepted) throw failure;
            // A successful stage has already transferred ownership to the FIFO
            // and finished the pooled CIOFrame. The pool may have reissued the
            // same handle. Letting this exception escape would make the many
            // caller finally-aborts cancel a *different* frame (or throw).
            // Admission is not delivery: close the broken connection, but
            // report the already accepted transfer to its caller.
            try {
                LOG.log(Level.SEVERE,
                        "[CIO SEND] failure after direct frame admission remote=" + _remoteAddress,
                        failure);
            } catch (Throwable ignored) {
                // Logging handlers must not re-expose the recycled frame.
            }
            try { close(); }
            catch (Throwable closing) {
                try { LOG.log(Level.SEVERE,
                        "[CIO SEND] close after admitted frame failed remote=" + _remoteAddress,
                        closing); }
                catch (Throwable ignored) { /* already committed; no caller abort */ }
            }
            return true;
        }
    }

    public void abortDirectFrame(CIOFrame frame) {
        if (frame == null) return;
        final CIOBufferEx lease = frame.ioLease(this);
        final int bytes = frame.reservedBytes();
        final int slot = frame.reservedTicket();
        final boolean optional = frame.optional();
        // Retire the handle before returning its lease to the pool. The same
        // CIOFrame object is reused on a later SEND; never touch it after
        // the pool release, even when Native cleanup fails.
        frame.finishIO();
        Throwable failure = null;
        try { rollbackDirectBegin(bytes, slot, lease); }
        catch (Throwable cleanup) { failure = cleanup; }
        // A failed Native cleanup is fatal even for optional packets. A plain
        // optional admission refusal retains its original non-fatal policy.
        if (_closed == 0 && (!optional || failure != null)) {
            try { close(); }
            catch (Throwable closing) {
                if (failure == null) failure = closing;
                else if (failure != closing) failure.addSuppressed(closing);
            }
        }
        if (failure != null) {
            // abortDirectFrame is commonly invoked from an encoder's finally.
            // Do not replace the original encode failure with cleanup failure.
            try { LOG.log(Level.WARNING,
                    "[CIO SEND] aborted frame cleanup failed remote=" + _remoteAddress,
                    failure); }
            catch (Throwable ignored) { /* socket has been closed */ }
        }
    }

    /**
     * Pack only consecutive already-reserved packet tickets while the first
     * SEND_ENQUEUE has not been claimed. No provider WRITE, cipher advancement,
     * other producer ticket or payload callback may be passed by this merge.
     * Caller owns _stateLock; the socket serializer takes the same lock before
     * committing the first SEND and encrypting its complete frame sequence.
     */
    private boolean packAdjacentStagedPacketLocked(CIOBufferEx packet,
            long ticket, int slot) {
        return packAdjacentStagedPacketLocked(packet, ticket, slot, false);
    }

    private boolean packAdjacentStagedPacketLocked(CIOBufferEx packet,
            long ticket, int slot, boolean directFrame) {
        final CIOBufferEx earlier = _stagedPacketPack;
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
    private void promoteDirectPackCapacityLocked(CIOBufferEx lease) {
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
        if (_stagedSends < 2 || _tailEncoding != null) return;
        final long end = (long) SOCKET_TICKET.getVolatile(this);
        final long start = Math.max(_socketHead, end - SOCKET_SLOTS);
        CIOBufferEx first = null;
        long firstTicket = -1L;
        for (long ticket = start; ticket < end; ++ticket) {
            if (ticket < _socketHead) { first = null; continue; }
            final int slot = (int) ticket & SOCKET_MASK;
            final long kind = (byte) SOCKET_CALLBACK_SLOT.getAcquire(_socketCallbacks, slot);
            if (kind == SOCKET_CANCELLED) continue;
            if (kind != SOCKET_SEND_ENQUEUE) { first = null; continue; }
            final CIOBufferEx next = _socketSendArguments[slot];
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
    public boolean commitOutboundFrame(CIOBufferEx lease) {
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

    private boolean stageOutboundFrameLocked(CIOBufferEx lease, boolean direct) {
        return stageOutboundFrameLocked(lease, direct, -1);
    }

    private boolean stageOutboundFrameLocked(CIOBufferEx lease, boolean direct,
            int reservedTicketSlot) {
        return stageOutboundFrameLocked(lease, direct, reservedTicketSlot, false);
    }

    private boolean stageOutboundFrameLocked(CIOBufferEx lease, boolean direct,
            int reservedTicketSlot, boolean optional) {
        if (!_pool.ownsSend(lease) || !lease.reserved()) return false;
        final int length = lease.writer().position();
        if (length <= CIOBufferPool.HEADER_BYTES
                || length > lease._reservedWireBytes
                || length > CIOBufferPool.MAX_WIRE_BYTES
                || _closed != 0) return false;
        if (!canAdmitBytes(length)) return rejectDirectSend(optional, REJECT_WIRE_BYTES);
        if (physicalSendFullLocked()) {
            compactReadyStagedPacketsLocked();
            if (physicalSendFullLocked())
                return rejectDirectSend(optional, REJECT_LOCAL_FRAMES);
        }

        final int frames = lease._packedFrames ? lease._frameCount : 1;
        if (frames < 1) return rejectDirectSend(optional, REJECT_LOCAL_FRAMES);

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
                    return rejectDirectSend(optional, REJECT_SERIALIZER);
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
    private void finishStagedSend(CIOBufferEx lease, boolean direct,
            boolean deferDrain) {
        if (deferDrain) return;
        finishStagedSend(lease, direct);
    }

    /** Direct SEND must run only after releasing the socket state lock. */
    private void finishStagedSend(CIOBufferEx lease, boolean direct) {
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
    @Override final void acceptStagedSend(CIOBufferEx lease) {
        boolean start = false;
        boolean resumeRead = false;
        boolean success = false;
        CIOBufferEx selectedWrite = null;
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
        final CIOBufferEx lease = _socketSendArguments[slot];
        if (lease == null || lease != _stagedBurstAction
                || !lease.reserved() || !lease._actionOnly
                || lease._packedFrames || lease._frameCount != 1
                || lease.writer().position() != 8
                || (byte) SOCKET_CALLBACK_SLOT.getAcquire(_socketCallbacks, slot)
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
    private CIOBufferEx commitSingleFlushSendLocked() {
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
        final CIOBufferEx lease = _socketSendArguments[slot];
        if (lease == null || lease != _stagedBurstAction
                || (byte) SOCKET_CALLBACK_SLOT.getAcquire(_socketCallbacks, slot)
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
        SOCKET_CALLBACK_SLOT.setRelease(_socketCallbacks, slot, (byte) 0);
        ++_socketHead;
        lease._ioPending = true;
        return lease;
    }

    /** Only the FIFO serializer owner may attach a staged SEND lease. */
    private boolean commitStagedOutboundFrameLocked(CIOBufferEx lease) {
        if (lease == null) return false;
        if (!_pool.ownsSend(lease)) throw new IllegalArgumentException("foreign SEND lease");
        final Assemble writer = lease.writer();
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
        } else if (writer != null) {
            writer.writeShort(0, (short) length);
        }
        if (writer != null) writer.sealToPosition();
        lease._wireLength = length;
        lease._frameCount = frameCount;
        lease.publish();

        if (_closed != 0) {
            lease.unpublish();
            return false;
        }
        if (_writing != null && length <= PACK_MAX_FRAME_BYTES) {
            final CIOBufferEx tail = _queued != 0
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
    public boolean tryWritePacket(CUserSocket client, IPacketWriter<CUserSocket> packet) {
        return tryWritePacketInternal(client, packet, false);
    }

    // The existing serializer token pins the staged lease while an encoder runs
    // outside _stateLock. The compaction pass must not return that lease either.
    private CIOBufferEx _tailEncoding; // guarded by _stateLock
    private Assemble _tailWriter;   // used only by the pinned encoder

    private CIOBufferEx reservePacketTailLocked(long ticket, int maxWireBytes) {
        final CIOBufferEx tail = _stagedPacketPack;
        if (maxWireBytes < 3 || maxWireBytes > PACK_MAX_FRAME_BYTES || tail == null
                || _tailEncoding != null || !tail.reserved() || tail._writePrepared || tail._ioPending
                || _stagedPacketLastTicket + 1 != ticket || _socketHead > _stagedPacketFirstTicket
                || tail.buffer().capacity() != CIOBufferPool.SMALL_SEND_BYTES || !canAdmitBytes(maxWireBytes)
                || tail.writer().position() > tail._reservedWireBytes - maxWireBytes)
            return null;
        if (!SOCKET_DRAINING.compareAndSet(this, 0, 1)) return null;
        // A drainer that claimed this staged SEND still needs _stateLock to
        // commit or retire it. Holding the lock and winning the token pins it.
        _socketOwner = Thread.currentThread();
        _socketExecutingTicket = true;
        _tailEncoding = tail;
        _reservedWireBytes += maxWireBytes;
        updateSendReadPauseLocked();
        return tail;
    }

    private boolean encodePacketTail(CUserSocket client, IPacketWriter<CUserSocket> packet,
            CIOBufferEx tail, int slot, long ticket, int maxWireBytes) {
        final ByteBuffer bytes = tail.buffer();
        final int start = tail.writer().position();
        final int oldPosition = bytes.position();
        final int oldLimit = bytes.limit();
        boolean admitted = false;
        boolean encodeFailed = false;
        boolean resumeRead = false;
        int length = 0;
        try {
            try {
                if (_tailWriter == null) _tailWriter = new Assemble();
                _tailWriter.armExternal(bytes, start, maxWireBytes);
                _tailWriter.preparePacket(packet.getClass());
                packet.writeData(client, _tailWriter);
                length = _tailWriter.position();
                if (length < 3 || length > maxWireBytes || !_tailWriter.isExternalBacking(bytes)
                        || _tailWriter.baseOffset() != start)
                    throw new IllegalStateException("Invalid bounded tail encoder result");
                _tailWriter.notePacketSize();
            } catch (Throwable failure) {
                encodeFailed = true;
            } finally {
                if (_tailWriter != null) _tailWriter.releaseResources();
                bytes.clear().limit(oldLimit).position(oldPosition);
            }

            _stateLock.lock();
            final boolean pausedBeforeCommit = _sendReadPaused;
            try {
                --_reservedDirect;
                _reservedWireBytes -= maxWireBytes;
                if (!encodeFailed && _closed == 0) {
                    final boolean packed = tail._packedFrames;
                    if (!packed) tail.writer().writeShort(0, (short) start);
                    tail.writer().writeShort(start, (short) length);
                    tail.writer().position(start + length);
                    tail._packedFrames = true;
                    tail._frameCount = packed ? tail._frameCount + 1 : 2;
                    tail._actionOnly = false;
                    if (_stagedPacketPack == tail) _stagedPacketLastTicket = ticket;
                    bytesAdmitted(length);
                    admitted = true;
                }
            } catch (Throwable failure) {
                encodeFailed = true;
            } finally {
                _tailEncoding = null;
                SOCKET_ARGUMENT_SLOT.setRelease(_socketArguments, slot, 0L);
                SOCKET_CALLBACK_SLOT.setRelease(_socketCallbacks, slot, SOCKET_CANCELLED);
                try {
                    updateSendReadPauseLocked();
                    resumeRead = pausedBeforeCommit && !_sendReadPaused;
                }
                finally { _stateLock.unlock(); }
            }
            if (encodeFailed) {
                rejectSend(REJECT_ENCODING);
                close();
            }
        } finally {
            _socketExecutingTicket = false;
            drainSocketCallbacksAcquired();
        }
        if (resumeRead && _closed == 0) kickRead();
        return admitted;
    }


    private boolean tryWritePacketInternal(CUserSocket client,
            IPacketWriter<CUserSocket> packet, boolean finalPacket) {
        if (client == null || packet == null || !isOpen()
                || (!finalPacket && _closing != 0)) return false;
        if ((int) SOCKET_PENDING.getAcquire(this) >= SOCKET_REJECT_AT - 32)
            drainPublishedSocketCallbacks();
        final int learned = Assemble.learnedPacketSize(packet.getClass());
        final int maxTailWireBytes = packet instanceof IPacketWriter.DirectTail bounded
                && peer.isOwnerThread() ? bounded.maximumWireBytes() : 0;
        final boolean direct;
        final int slot;
        final long ticket;
        boolean publishProducer = false;
        CIOBufferEx packetTail = null;
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
            if (maxTailWireBytes > 0 && !publishProducer)
                packetTail = reservePacketTailLocked(ticket, maxTailWireBytes);
        } finally {
            _stateLock.unlock();
        }
        if (publishProducer) drainSocketCallbacks();
        if (packetTail != null) return encodePacketTail(client, packet, packetTail, slot, ticket, maxTailWireBytes);

        CIOBufferEx encoded = null;
        boolean encodeFailed = false;
        int capacity = learned > CIOBufferPool.SMALL_SEND_BYTES
                ? CIOBufferPool.MAX_WIRE_BYTES : CIOBufferPool.SMALL_SEND_BYTES;
        for (int attempt = 0; attempt < 2; attempt++) {
            CIOBufferEx lease = null;
            boolean retryLarge = false;
            try {
                lease = beginOutboundFrame(capacity);
                if (lease == null) {
                    rejectSend(REJECT_POOL);
                    break;
                }
                final Assemble writer = lease.beginPacket(packet.getClass());
                packet.writeData(client, writer);
                writer.notePacketSize();
                encoded = lease;
            } catch (Assemble.RegisteredCapacityExceededException oversized) {
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
                            packed = (byte) SOCKET_CALLBACK_SLOT.getAcquire(
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
        final CIOBufferEx lease = _producerLease;
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
    @Override final void acceptPackedAction(long packed) {
        boolean valid = true;
        boolean resume = false;
        _stateLock.lock();
        try {
            if (_closed != 0) {
                resume = bytesReleased(8, 1);
            } else {
                final int objectId = (int) (packed >>> 32);
                final int actionId = (int) packed;
                CIOBufferEx tail = null;
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
                    final CIOBufferEx lease = _pool.acquireSend(CIOBufferPool.SMALL_SEND_BYTES);
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
            CIOBufferEx stagedLease = null;
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
                        final CIOBufferEx lease = _pool.acquireSend(CIOBufferPool.SMALL_SEND_BYTES);
                        if (lease == null) {
                            rejectSend(REJECT_POOL);
                        } else {
                            try {
                                final Assemble writer = lease.writer();
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
        final CIOBufferEx lease = _stagedBurstAction;
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
    private CIOBufferEx writableBurstTailLocked() {
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
        final CIOBufferEx tail = _writing;
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
    private CIOBufferEx writableQueuedActionTailLocked() {
        if (_queued == 0 || _writing == null || !_writing._ioPending
                || _burstOwner != null || _stagedSends != 0 || _reservedDirect != 0
                || _socketPosting) return null;
        final int pending = (int) SOCKET_PENDING.getVolatile(this);
        if ((pending != 0 && !canInlineActionLocked())
                || (long) SOCKET_TICKET.getVolatile(this) != _socketHead)
            return null;
        final CIOBufferEx tail = _pending[_tail == 0
                ? _pending.length - 1 : _tail - 1];
        return tail != null && !tail._ioPending && !tail._writePrepared
                && tail.buffer().capacity() == CIOBufferPool.SMALL_SEND_BYTES
                ? tail : null;
    }

    /** Append one wire-complete action without a new lease or serializer ticket. */
    private boolean appendBurstActionLocked(int objectId, int actionId) {
        CIOBufferEx tail = writableBurstTailLocked();
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
        CIOBufferEx lease = null;
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
        final CIOBufferEx tail = writableBurstTailLocked();
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

    @Override final boolean dispatchPlaintext(Disassemble decoded) {
        final int opcode = decoded.readUnsignedByte();
        if (this.checkPacketRateLimit(System.currentTimeMillis())) return false;
        if (!allowed(opcode, this.getConnectionState(), this.getAccount() != null,
                this.getActiveChar() != null, this.isGameSessionReady())) return inboundHandlerConnected();
        final UserPacketCallbackMap.PacketFunc callback = UserPacketCallbackMap.gPacketTableFromClient[opcode];
        if (callback == UserPacketCallbackMap.DummyUserPacket) {
            CPacketFilter.isFilter(this, opcode);
        } else if (CPacketFilter.isFilter(this, opcode) != CPacketFilter.RESULT_FILTER) {
            if (UserPacketCallbackMap.isDirectOpcode(opcode) || (this.isGameSessionReady() && !isSessionOpcode(opcode))) {
                dispatchDecodedFrame(callback, decoded);
            } else {
                synchronized (this) {
                    if (this.isConnected()) dispatchDecodedFrame(callback, decoded);
                }
            }
        }
        return inboundHandlerConnected();
    }

    /** Graceful disconnect is an accepted handler outcome, not a decode error. */
    private boolean inboundHandlerConnected() {
        return _closed == 0 && _disconnecting == 0
                && (_closing != 0 || this.isConnected());
    }


    private void dispatchDecodedFrame(UserPacketCallbackMap.PacketFunc callback, Disassemble decoded) {
        // Even if this handler blocks on game logic or client synchronization,
        // unrelated SEND ticket owners must be able to drain immediately.
        releaseFusedReadSendOwner();
        // Only the current socket READ pump can set this flag. A full buffer
        // owns the SEND burst and its finally always flushes it.
        if (_readPassBurstActive) {
            callback.execute(decoded, this);
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
            callback.execute(decoded, this);
            // The ordinary single-frame path exits the scope below. Its
            // finally performs the one mandatory flush, including on throw.
            // Nested dispatch must still flush immediately even while the
            // outer burst remains active.
            if (burst.depth != 1) burst.flush();
        } finally {
            if (--burst.depth == 0) burst.flush();
        }
    }

    /** Transport close callback is emitted after every in-flight I/O lease is released. */
    public boolean isCloseComplete() {
        return (_closeNotified != 0);
    }

    @Override final void notifyClosedWhenReleased() {
        if (_closed == 0 || (socketLifecycle & IO_DRAINED) == 0 || !_readReleased
                || _socketDraining != 0 || _socketPending != 0
                || _writeEvent != 0L) return;
        _stateLock.lock(); try {
            if (_writing != null || _queued != 0 || _reservedDirect != 0 || _stagedSends != 0) return;
        } finally { _stateLock.unlock(); }
        if (!CLOSE_NOTIFIED.compareAndSet(this, 0, 1)) return;
        clearCipher();
        try {
            if (_gameSession) onTransportClosed();
        } finally {
            if (_closedCallback != null) {
                try {
                    _closedCallback.onClosed(this);
                } catch (Throwable ignored) {
                    // Never retain a closed socket when a lifecycle observer fails.
                }
            }
        }
    }

    private void closeGracefully(){close(true);}
    @Override public void close(){_gameSessionReady = false; close(false);}
    private void close(boolean graceful) {
        if (!CLOSED.compareAndSet(this, 0, 1)) return;
        finishClose(graceful);
    }

    /** Claim the disconnect while the original actor still owns this socket. */
    public void closeIfCurrentCharacter(L1PcInstance pc, int stamp) {
        synchronized (this) {
            if (pc == null || !pc.isItemTransferSession(this, stamp)
                    || !CLOSED.compareAndSet(this, 0, 1)) return;
            _gameSessionReady = false;
        }
        // Transport teardown has its own locks and must not run under this monitor.
        finishClose(false);
    }

    private void finishClose(boolean graceful) {
        // Session observer / Native packet cleanup can fail independently of
        // transport shutdown. Neither may strand the SEND/READ leases.
        try { onTransportClosing(); }
        catch (Throwable failure) {
            try { LOG.log(Level.WARNING, "[CIO CLOSE] session cleanup failed", failure); }
            catch (Throwable ignored) { /* continue closing */ }
        }
        deadlinesConfigured = false;
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
            final long headKind = (byte) SOCKET_CALLBACK_SLOT.getAcquire(
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
        try {
            peer.requestClose(graceful);
        } catch (Throwable failure) {
            // Native transport close failure must not skip Java FIFO cleanup.
            try { LOG.log(Level.WARNING, "[CIO CLOSE] transport close failed remote="
                    + _remoteAddress, failure); }
            catch (Throwable ignored) { /* still release the Java FIFO */ }
        }
        boolean drainProducer;
        Throwable releaseFailure = null;
        _stateLock.lock(); try {
            _deferredClosePacket = null;
            _stagedPacketPack = null;
            drainProducer = publishProducerLeaseLocked();
            int releasedBytes = 0;
            int releasedFrames = 0;
            while (_queued != 0) {
                final CIOBufferEx lease = _pending[_head];
                _pending[_head] = null;
                if (++_head == _pending.length) _head = 0;
                --_queued;
                releasedBytes += lease.wireLength();
                releasedFrames += lease._frameCount;
                try { _pool.release(lease); }
                catch (Throwable failed) {
                    if (releaseFailure == null) releaseFailure = failed;
                    else if (releaseFailure != failed) releaseFailure.addSuppressed(failed);
                }
            }
            if (releasedFrames != 0) bytesReleased(releasedBytes, releasedFrames);
        } finally { _stateLock.unlock(); }
        if (releaseFailure != null) try {
            LOG.log(Level.WARNING, "[CIO CLOSE] queued lease release failed remote="
                    + _remoteAddress, releaseFailure);
        } catch (Throwable ignored) { /* continue close notifications */ }
        if (drainProducer) drainSocketCallbacks();
        _stateLock.lock(); try {
            if (_readActive == 0) releaseReadWhenIdle();
        } finally { _stateLock.unlock(); }
        finishReadIfClosed();
        if (!_stateLock.isHeldByCurrentThread()) kickWrite();
        notifyClosedWhenReleased();
    }


    /** Immutable pooled plaintext -> regular ordered per-socket SEND lease. */
    public boolean sendFrozenWire(ByteBuffer frozenFrame) {
        return sendFrozenWire(frozenFrame, false);
    }

    /** Visual broadcast backpressure may skip this socket without closing it. */
    public boolean sendFrozenWireBestEffort(ByteBuffer frozenFrame) {
        return sendFrozenWire(frozenFrame, true);
    }

    private boolean sendFrozenWire(ByteBuffer frozenFrame, boolean optional) {
        if (frozenFrame == null || !isClientConnected()) return false;
        final int length = frozenFrame.remaining();
        final int begin = frozenFrame.position();
        if (length <= CIOBufferPool.HEADER_BYTES || length > CIOBufferPool.MAX_WIRE_BYTES
                || ((frozenFrame.get(begin) & 255)
                    | ((frozenFrame.get(begin + 1) & 255) << 8)) != length) return false;
        final CIOFrame frame = beginDirectFrame(length, 0, optional);
        if (frame == null) {
            if (optional && isClientConnected()) _pool.recordOptionalBroadcastRefusal();
            return false;
        }
        boolean committed = false;
        try {
            final Assemble writer = frame.buffer();
            final ByteBuffer dest = writer.getBuffer();
            dest.put(writer.baseOffset() + writer.position(), frozenFrame,
                    begin + CIOBufferPool.HEADER_BYTES, length - CIOBufferPool.HEADER_BYTES);
            writer.position(length);
            committed = commitDirectFrame(frame);
            if (!committed && optional && isClientConnected()) _pool.recordOptionalBroadcastRefusal();
            return committed;
        } catch (RuntimeException failure) {
            LOG.log(Level.WARNING, "Failed to enqueue frozen broadcast frame", failure);
            return false;
        } finally {
            if (!committed) abortDirectFrame(frame);
        }
    }

    public boolean sendFrozenWire(byte[] frameBytes) {
        if(frameBytes==null || frameBytes.length<=CIOBufferPool.HEADER_BYTES
                || frameBytes.length>CIOBufferPool.MAX_WIRE_BYTES
                || ((frameBytes[0]&0xff)|((frameBytes[1]&0xff)<<8))!=frameBytes.length
                || !isClientConnected()) return false;
        final CIOFrame frame=beginDirectFrame(frameBytes.length);
        if(frame==null)return false;
        boolean committed=false;
        try {
            final l1j.server.server.model.network.buffer.Assemble writer=frame.buffer();
            final java.nio.ByteBuffer memory=writer.getBuffer();
            memory.position(writer.baseOffset()+writer.position());
            memory.put(frameBytes,CIOBufferPool.HEADER_BYTES,
                    frameBytes.length-CIOBufferPool.HEADER_BYTES);
            writer.position(frameBytes.length);
            committed=commitDirectFrame(frame);
            return committed;
        } catch(RuntimeException failure) {
            LOG.log(Level.WARNING, "Failed to enqueue frozen wire frame", failure);
            return false;
        } finally {
            if(!committed)abortDirectFrame(frame);
        }
    }

    // L1 game-session state and operations (same socket lifetime).




	private volatile int _packetShard = -1;

	// Authentication-only connections need no separate AtomicBoolean object.
	private volatile int _shardLogged;
	private static final VarHandle SHARD_LOGGED;
	static {
		try {
			SHARD_LOGGED = MethodHandles.lookup().findVarHandle(
					CUserSocket.class, "_shardLogged", int.class);
		} catch (ReflectiveOperationException e) {
			throw new ExceptionInInitializerError(e);
		}
	}

	private int _emptyPacketCount;

	private volatile Account account;
	private volatile L1PcInstance _activeChar;
    private String loginRegistryKey; // guarded by this, published by LoginController
    // Published only after the full asynchronous character-enter completion.
    // Prevents a half-initialized IN_GAME state from bypassing session guard.
    private volatile boolean _gameSessionReady;
	private int chatCount;

    // Shutdown may dispose the PC only after network drain and asynchronous cleanup.
    public boolean isLogoutComplete() { return isClientCleanupComplete(); }
	

	private int _packetCount = 0;
	private long _lastCheckTime = System.currentTimeMillis();


    /** An admitted session must have its initial key packet staged for SEND. */
    public boolean onConnected() {
        try {
            if (!isOpen()) return false;
            SetKey(Opcodes.getSeed());
            // sendPacket(byte[]) swallows staging failures. ACCEPT must not treat
            // a failed initial SEND reservation as a successful login handshake.
            if (!tryWritePacket(this, new CBytesPacket(Opcodes.S_KEYBYTES))) {
                clearCipher();
                return false;
            }
            return isOpen();
        } catch (Exception e) {
            clearCipher();
            LOG.log(Level.SEVERE,
                    String.format("[GameClient Handshake Err] 초기 핸드셰이크 키 전송 실패 - IP: %s", getIp()), e);
            return false;
        }
    }

	protected void onDisconnection() {
		close();
	}



	public void kick() {
		try {
            logKick();
            SC_Disconnect.send(this);
        }
		finally { close(); }
	}

    private void logKick() {
        LOG.info(String.format(
                "[GameClient Kick] 강제 강퇴 처리 (DISCONNECT 전송) - IP: %s, Account: %s",
                getIp(), getAccountName()));
    }

    /** An administrative kick also retires the exact private-store or auto-clan
     * session after its transport and packets have drained. */
    public void kickAndLogout(L1PcInstance pc, int stamp) {
        final boolean startClose;
        synchronized (this) {
            if (_activeChar != pc || pc.getNetConnection() != this || pc.getSessionStamp() != stamp) return;
            pc.fenceForcedItemTransferRetirement();
            FORCED_LOGOUT.compareAndSet(this, null, new ForcedLogout(pc, stamp));
            markLifecycle(FORCED_CHARACTER_CLOSE);
            startClose = beginClientClose();
            if (startClose) _gameSessionReady = false;
        }
        try {
            if (startClose) {
                try {
                    logKick();
                    finishClientClose(SC_Disconnect.closePacket());
                }
                catch (RuntimeException | Error failure) {
                    close();
                    throw failure;
                }
            }
        }
        finally {
            if (isClientCleanupComplete()) finishForcedLogout();
        }
    }

    /** Terminal packet/I/O drain starts one deferred L1 logout operation. */
    private void onTransportClosed() {
        if (!isCloseComplete())
            throw new IllegalStateException("logout before transport/packet drain");
        _gameSessionReady = false;
		if (!markLifecycle(LOGOUT_STARTED)) {
			// 종료 과정에서는 여러 경로에서 close()가 재진입할 수 있으므로 로그 도배 방지
			LOG.fine(String.format(
					"[GameClient Close Dup] 이미 close 처리 중인 클라이언트 - IP: %s", getIp()));
			return;
		}

        try { PlaySupport2019.onClientDetached(this); }
        catch (RuntimeException failure) { LOG.log(Level.WARNING, "Session detach failed during close", failure); }
        automationSession = null;
		final L1PcInstance pc = _activeChar;

        if (pc == null) {
            cleanupAfterLogout(null);
            return;
        }
        final CUserSocketReleaseTask task = new CUserSocketReleaseTask(pc);
        terminalRelease = task;
        task.requestRelease();
    }

    private boolean matchesForcedLogout(L1PcInstance pc, int stamp) {
        final ForcedLogout request = forcedLogout;
        return request != null && request.pc() == pc && request.stamp() == stamp;
    }

    /** A kick arriving after the ordinary release made its decision must still
     * complete after that release has finished, even if it detached the PC. */
    private void finishForcedLogout() {
        final ForcedLogout request = FORCED_LOGOUT.getAndSet(this, null);
        if (request == null || request.pc().isLogoutStarted()) return;
        submitTerminalRelease(() -> {
            final L1PcInstance pc = request.pc();
            final CUserSocket current = pc.getNetConnection();
            if (pc.getSessionStamp() == request.stamp() && (current == this || current == null))
                logoutPlayer(pc);
        });
    }

    private static void logoutPlayer(L1PcInstance pc) {
        try { pc.logout(); }
        catch (Throwable failure) {
            LOG.log(Level.SEVERE, "[GameClient Close] deferred player logout failed", failure);
        }
    }

    private void submitTerminalRelease(Runnable task) {
        try {
            CSessionLifecycle.release(task);
        } catch (Exception failure) {
            LOG.log(Level.SEVERE, "[GameClient Close] deferred logout admission failed", failure);
            // Wakes may come from CacheD or a game owner as well as the transport.
            try { Thread.ofVirtual().name("CIO-logout-recovery").start(task); return; }
            catch (Throwable startFailure) {
                LOG.log(Level.SEVERE, "[GameClient Close] emergency logout thread unavailable", startFailure);
            }
            task.run();
        }
    }


	private void cleanupAfterLogout(L1PcInstance pc) {
        try { registry.releaseCleanupIp(this); }
        catch (RuntimeException failure) { LOG.log(Level.WARNING, "Connection limit release failed", failure); }

		try {
			LoginController.getInstance().logout(this);
		} catch (Throwable e) {
			LOG.log(Level.WARNING, "LoginController logout 예외 발생", e);
		}

		try {
			if (_activeChar == pc) {
                final boolean detachPrivateStore = pc != null && pc.isPrivateShop() && pc.getNetConnection() == this;
                try {
                    setActiveChar(null);
                } finally {
                    if (detachPrivateStore && pc.getNetConnection() == this) pc.setNetConnection(null);
                }
			}
		} catch (Throwable ignored) {
		}

		try {
			// 메모리 제거
			SC_CRAFT_LIST_ALL_ACK.onDisconnect(this);
            clearCipher();
			setAccount(null);

			disconnect();

			setConnectionState(CConnectionState.DISCONNECTED);
		} catch (Throwable e) {
			LOG.log(Level.WARNING, "disconnect 처리 중 예외 발생", e);
		} finally {
            // Hybrid handoff is allowed only after full logout/session removal.
            try { DominationHybrid.onClientClosed(this); }
            finally {
                onClientCleanupComplete();
                finishForcedLogout();
            }
		}
	}

    // Coalesce re-entry requests while the detached merchant is being retired.
    private volatile boolean privateStoreRetirementPending;
    private volatile LoginDbLoad loginDbLoad;

    public boolean isPrivateStoreRetirementPending() { return privateStoreRetirementPending; }

    public synchronized boolean tryBeginPrivateStoreRetirement() {
        if (privateStoreRetirementPending || loginDbLoad != null) return false;
        privateStoreRetirementPending = true;
        return true;
    }

    public synchronized void endPrivateStoreRetirement() {
        privateStoreRetirementPending = false;
    }

    /** A single detached login read owns the socket until its owner completes it. */
    public static final class LoginDbLoad {
        private final CConnectionState state;
        private final Account account;

        private LoginDbLoad(CConnectionState state, Account account) {
            this.state = state;
            this.account = account;
        }
    }

    public boolean isLoginDbLoadPending() { return loginDbLoad != null; }

    public synchronized LoginDbLoad tryBeginLoginDbLoad(CConnectionState state, Account account) {
        if (loginDbLoad != null || privateStoreRetirementPending || !matchesLoginState(state, account)) return null;
        return loginDbLoad = new LoginDbLoad(state, account);
    }

    public boolean isCurrentLoginDbLoad(LoginDbLoad request) {
        return request != null && loginDbLoad == request && matchesLoginState(request.state, request.account);
    }

    public synchronized boolean finishLoginDbLoad(LoginDbLoad request) {
        if (request == null || loginDbLoad != request) return false;
        loginDbLoad = null;
        return true;
    }

    private boolean matchesLoginState(CConnectionState state, Account account) {
        return isConnected() && getActiveChar() == null && getConnectionState() == state && getAccount() == account;
    }

    private volatile PlayerShopView playerShopView;

    public PlayerShopView getPlayerShopView() { return playerShopView; }

    /** Retain only the view whose exact viewer had its list admitted to SEND. */
    public synchronized void publishPlayerShopView(L1PcInstance viewer, PlayerShopView view) {
        if (!view.currentFor(this, viewer)) return;
        playerShopView = view;
        // Transport closing can clear readiness before acquiring the session monitor.
        if (view.currentFor(this, viewer)) npcShopView = null;
        else playerShopView = null;
    }

    public synchronized void clearPlayerShopView(PlayerShopView expected) {
        if (playerShopView == expected) playerShopView = null;
    }

    private volatile NpcShopView npcShopView;

    public NpcShopView getNpcShopView() { return npcShopView; }

    /** Publish only after the matching shop list is admitted to SEND. */
    public synchronized void publishNpcShopView(L1PcInstance pc, NpcShopView view) {
        if (view != null && view.currentFor(this, pc)) {
            playerShopView = null;
            npcShopView = view;
        }
    }

    public synchronized void clearNpcShopView(NpcShopView view) {
        if (npcShopView == view) npcShopView = null;
    }

    public synchronized void clearNpcShopViewFor(L1PcInstance pc) {
        if (_activeChar == pc) npcShopView = null;
    }

    public void checkNpcShopRange(L1PcInstance pc) {
        final NpcShopView view = npcShopView;
        if (view != null && !view.currentFor(this, pc)) clearNpcShopView(view);
    }

    // Owned by this session's CIO. Only deferred NPC replies use this state.
    private long npcDialogueTarget;
    private int npcDialogueVersion;

    public synchronized void trackNpcDialogue(long smartId, boolean newTalk) {
        if (newTalk || smartId != npcDialogueTarget) {
            npcShopView = null;
            warehouseNpcTarget = 0L;
            ClearWarehouseView();
            npcDialogueTarget = smartId;
            npcDialogueVersion++;
        }
        if (smartId != 0L && warehouseNpcTarget != smartId
                && IsWarehouseActor(_activeChar)
                && CWorld.getInstance().findObjectBySmartId(smartId) instanceof L1NpcInstance npc
                && IsWarehouseNpc(npc) && IsInWarehouseRange(_activeChar, npc)) {
            warehouseNpcTarget = smartId;
        }
    }

    // Warehouse-specific dialogue identity; invalidating it does not cancel unrelated NPC replies.
    // The volatile fast path avoids a monitor or world lookup for ordinary movement.
    private volatile long warehouseNpcTarget;
    private int warehouseViewGeneration;
    private int warehouseViewObjectId;
    private int warehouseViewKind;
    private int warehouseViewClanId;
    private int warehouseViewPassword;
    private int warehouseViewSession;
    private int[] warehouseViewItemIds;
    private ClanWarehouse pledgeWarehouseView;
    private long pledgeWarehouseLease;

    public synchronized void ClearWarehouseView() {
        final ClanWarehouse vault = pledgeWarehouseView;
        final long lease = pledgeWarehouseLease;
        pledgeWarehouseView = null;
        pledgeWarehouseLease = 0L;
        if (++warehouseViewGeneration == 0) ++warehouseViewGeneration;
        warehouseViewObjectId = 0;
        warehouseViewKind = 0;
        warehouseViewClanId = 0;
        warehouseViewPassword = 0;
        warehouseViewSession = 0;
        warehouseViewItemIds = null;
        if (vault != null) vault.ReleaseWarehouseUser(this, lease);
    }

    public synchronized void ClearWarehouseViewFor(L1PcInstance pc) {
        if (pc != null && _activeChar == pc) {
            warehouseNpcTarget = 0L;
            ClearWarehouseView();
        }
    }

    public void CheckWarehouseRange(L1PcInstance pc) {
        if (warehouseNpcTarget == 0L) return;
        synchronized (this) {
            if (_activeChar != pc || warehouseNpcTarget == 0L) return;
            if (!IsWarehouseActor(pc)
                    || !(CWorld.getInstance().findObjectBySmartId(warehouseNpcTarget) instanceof L1NpcInstance npc)
                    || !IsWarehouseNpc(npc) || !IsInWarehouseRange(pc, npc)) {
                warehouseNpcTarget = 0L;
                // A supplementary service uses the character, not this NPC.
                if (warehouseViewKind != 20 && warehouseViewKind != -20) ClearWarehouseView();
            }
        }
    }

    private boolean IsWarehouseActor(L1PcInstance pc) {
        return pc != null && _activeChar == pc && pc.getNetConnection() == this
                && isConnected() && getConnectionState() == CConnectionState.IN_GAME
                && !pc.isLogoutStarted() && !pc.isDead() && !pc.isGhost()
                && !pc.get_teleport() && !pc.isPrivateShop() && !pc.is무인상점()
                && !pc.isAutoClanjoin() && getAccount() != null;
    }

    private static boolean IsWarehouseNpc(L1NpcInstance npc) {
        return !npc.isDead() && "L1Dwarf".equalsIgnoreCase(npc.getNpcTemplate().getImpl());
    }

    private static boolean IsInWarehouseRange(L1PcInstance pc, L1NpcInstance npc) {
        return pc.getMapId() == npc.getMapId()
                && Math.abs((long) pc.getX() - npc.getX()) <= 12
                && Math.abs((long) pc.getY() - npc.getY()) <= 12;
    }

    // Called under the vault monitor by another session. Never take this
    // socket's monitor here (session -> inventory -> warehouse lock order).
    public boolean IsWarehouseLeaseTargetLive(L1PcInstance pc) {
        final long target = warehouseNpcTarget;
        return target != 0L && IsWarehouseActor(pc)
                && CWorld.getInstance().findObjectBySmartId(target) instanceof L1NpcInstance npc
                && IsWarehouseNpc(npc) && IsInWarehouseRange(pc, npc);
    }

    public boolean IsWarehouseNpcInRange(L1PcInstance pc, int objectId) {
        final long target = warehouseNpcTarget;
        if (!IsWarehouseActor(pc) || target == 0L) return false;
        return CWorld.getInstance().findObjectBySmartId(target) instanceof L1NpcInstance npc
                && npc.getId() == objectId && IsWarehouseNpc(npc) && IsInWarehouseRange(pc, npc);
    }

    private boolean IsWarehouseViewTarget(L1PcInstance pc, int objectId, int kind) {
        if (kind == 20) return IsWarehouseActor(pc) && objectId == pc.getId();
        return IsWarehouseNpcInRange(pc, objectId);
    }

    private boolean CanOpenWarehouseView(L1PcInstance pc, int objectId, int kind,
            int suppliedPassword, int expectedClanId) {
        if (!IsWarehouseViewTarget(pc, objectId, kind)) return false;
        return switch (kind) {
            case 3 -> pc.getLevel() >= 5
                    && (getAccount().getGamePassword() == 0
                        || getAccount().getGamePassword() == suppliedPassword);
            case 5 -> pc.getLevel() >= 5 && expectedClanId > 0
                    && pc.getClanid() == expectedClanId && pc.getClan() != null;
            case 9 -> pc.getLevel() >= 5 && pc.isElf();
            case 10, 20 -> true;
            default -> false;
        };
    }

    public synchronized int BeginWarehouseView(L1PcInstance pc, int objectId, int kind,
            int suppliedPassword, int expectedClanId) {
        if (!IsWarehouseActor(pc)) return 0;
        ClearWarehouseView();
        if (!CanOpenWarehouseView(pc, objectId, kind, suppliedPassword, expectedClanId)) return 0;
        warehouseViewObjectId = objectId;
        warehouseViewKind = -kind; // pending list; never grants item access
        if (kind == 20) warehouseNpcTarget = 0L;
        return warehouseViewGeneration;
    }

    public synchronized boolean IsPendingWarehouseView(L1PcInstance pc, int objectId, int kind, int generation) {
        return generation != 0 && generation == warehouseViewGeneration
                && warehouseViewObjectId == objectId && warehouseViewKind == -kind
                && IsWarehouseViewTarget(pc, objectId, kind)
                && (kind != 5 || OwnsPledgeWarehouse(pc, pledgeWarehouseView));
    }

    // Call only after the complete list was admitted by the existing SEND path.
    public synchronized boolean BindWarehouseView(L1PcInstance pc, int objectId, int kind,
            int suppliedPassword, int expectedClanId, int generation, int[] listedItemIds) {
        if (generation == 0 || generation != warehouseViewGeneration
                || objectId != warehouseViewObjectId || warehouseViewKind != -kind
                || listedItemIds == null || listedItemIds.length == 0
                || !CanOpenWarehouseView(pc, objectId, kind, suppliedPassword, expectedClanId)) return false;
        if (kind == 5 && !OwnsPledgeWarehouse(pc, pledgeWarehouseView)) return false;
        // The list sender transfers this private array only after all frames are queued.
        java.util.Arrays.sort(listedItemIds);
        warehouseViewItemIds = listedItemIds;
        warehouseViewSession = pc.getSessionStamp();
        warehouseViewClanId = kind == 5 ? expectedClanId : 0;
        warehouseViewPassword = kind == 3 ? getAccount().getGamePassword() : 0;
        warehouseViewKind = kind;
        return true;
    }

    public synchronized boolean IsListedWarehouseItem(int itemId) {
        return itemId > 0 && warehouseViewItemIds != null
                && java.util.Arrays.binarySearch(warehouseViewItemIds, itemId) >= 0;
    }

    public synchronized boolean CanUseWarehouse(L1PcInstance pc, int objectId, int kind) {
        // L1 deposit is selected locally from inventory after NPC dialogue.
        if (kind == 2 || kind == 4 || kind == 8) return IsWarehouseNpcInRange(pc, objectId);
        if (warehouseViewKind != kind || warehouseViewObjectId != objectId) return false;
        if (!IsWarehouseViewTarget(pc, objectId, kind)
                || warehouseViewSession != pc.getSessionStamp()) {
            if (_activeChar == pc) {
                warehouseNpcTarget = 0L;
                ClearWarehouseView();
            }
            return false;
        }
        return switch (kind) {
            case 3 -> warehouseViewPassword == getAccount().getGamePassword();
            case 5 -> warehouseViewClanId > 0 && pc.getClanid() == warehouseViewClanId
                    && pc.getClan() != null && OwnsPledgeWarehouse(pc, pledgeWarehouseView);
            case 9 -> pc.isElf() && pc.getLevel() >= 5;
            case 10, 20 -> true;
            default -> false;
        };
    }

    public synchronized boolean CloseWarehouseView(L1PcInstance pc, int objectId, int kind) {
        if (pc == null || _activeChar != pc || kind <= 0 || warehouseViewObjectId != objectId
                || (warehouseViewKind != kind && warehouseViewKind != -kind)) return false;
        ClearWarehouseView();
        return true;
    }

    public synchronized long AcquirePledgeWarehouseView(L1PcInstance pc, ClanWarehouse vault,
            int objectId, int clanId, int generation) {
        if (generation == 0 || generation != warehouseViewGeneration || objectId != warehouseViewObjectId
                || warehouseViewKind != -5 || vault == null || pledgeWarehouseView != null
                || !CanOpenWarehouseView(pc, objectId, 5, 0, clanId)) return 0L;
        final long lease = WarehouseManager.nextSessionLease();
        if (!vault.AcquireWarehouseUser(pc, this, lease)) return 0L;
        pledgeWarehouseView = vault;
        pledgeWarehouseLease = lease;
        return lease;
    }

    public synchronized boolean OwnsPledgeWarehouse(L1PcInstance pc, ClanWarehouse vault) {
        return vault != null && pledgeWarehouseView == vault
                && vault.OwnsWarehouseUser(pc, this, pledgeWarehouseLease);
    }

    public synchronized boolean BindPledgeWarehouseView(L1PcInstance pc, int objectId,
            int clanId, ClanWarehouse vault, long lease, int generation, int[] listedItemIds) {
        return pledgeWarehouseView == vault && pledgeWarehouseLease == lease
                && BindWarehouseView(pc, objectId, 5, 0, clanId, generation, listedItemIds);
    }

    public synchronized void ReleasePledgeWarehouseView(ClanWarehouse vault, long lease) {
        if (vault == null || lease <= 0L) return;
        if (pledgeWarehouseView == vault && pledgeWarehouseLease == lease) ClearWarehouseView();
        else vault.ReleaseWarehouseUser(this, lease);
    }

    public int npcDialogueVersion() { return npcDialogueVersion; }

    public boolean canReplyToNpcDialogue(L1PcInstance pc, long smartId, int version) {
        if (smartId == 0 || npcDialogueTarget != smartId || npcDialogueVersion != version
                || !isConnected() || _activeChar != pc || pc.getNetConnection() != this
                || getConnectionState() != CConnectionState.IN_GAME || pc.isDead()
                || pc.isGhost() || pc.isPrivateShop() || pc.is무인상점() || pc.isAutoClanjoin()) return false;
        return CWorld.getInstance().findObjectBySmartId(smartId) instanceof L1NpcInstance npc
                && !npc.isDead() && pc.getMapId() == npc.getMapId()
                && Math.abs((long) pc.getX() - npc.getX()) <= 6
                && Math.abs((long) pc.getY() - npc.getY()) <= 6;
    }

	public void setActiveChar(L1PcInstance pc) {
        onCharacterChanged(pc);
        if (_activeChar != pc || pc == null) {
            _gameSessionReady = false;
            PlaySupport2019.onClientDetached(this);
            automationSession = null;
            clearPssWindowSession();
        }
        synchronized (this) {
            if (pc != null && _activeChar != pc && (socketLifecycle & FORCED_CHARACTER_CLOSE) != 0)
                throw new IllegalStateException("Cannot replace the character during a forced logout");
            if (_activeChar != pc || pc == null) {
                playerShopView = null;
                trackNpcDialogue(0L, true);
            }
            _activeChar = pc;
        }
	}

    private volatile ClientAutomationSession automationSession;
    private volatile int pssClaimPending, pssQueryPending;
    private static final AtomicIntegerFieldUpdater<CUserSocket> PSS_CLAIM =
            AtomicIntegerFieldUpdater.newUpdater(CUserSocket.class, "pssClaimPending");
    private static final AtomicIntegerFieldUpdater<CUserSocket> PSS_QUERY =
            AtomicIntegerFieldUpdater.newUpdater(CUserSocket.class, "pssQueryPending");
    private volatile PssConfig.Settings pssCacheSettings;
    private volatile long pssCacheDay;
    private volatile int pssCacheSeconds = -1;

    public boolean tryBeginPssClaim() { return PSS_CLAIM.compareAndSet(this, 0, 1); }
    public void endPssClaim() { pssClaimPending = 0; }
    public boolean isPssClaimPending() { return pssClaimPending != 0; }
    public boolean tryBeginPssQuery() { return PSS_QUERY.compareAndSet(this, 0, 1); }
    public void endPssQuery() { pssQueryPending = 0; }
    public int cachedPssSeconds(PssConfig.Settings settings, long day) {
        return pssCacheSettings == settings && pssCacheDay == day ? pssCacheSeconds : -1;
    }
    public void cachePssSeconds(PssConfig.Settings settings, long day, int seconds) {
        pssCacheSeconds = seconds;
        pssCacheDay = day;
        pssCacheSettings = settings;
    }
    public void invalidatePssCache() { pssCacheSettings = null; }

    private volatile l1j.server.server.packets.Protocol.MainServer.PlaySupport.window.PlaySupportWindow.Session pssWindowSession;

    public l1j.server.server.packets.Protocol.MainServer.PlaySupport.window.PlaySupportWindow.Session peekPssWindowSession() {
        return pssWindowSession;
    }

    public synchronized l1j.server.server.packets.Protocol.MainServer.PlaySupport.window.PlaySupportWindow.Session pssWindowSession() {
        if (pssWindowSession == null)
            pssWindowSession = new l1j.server.server.packets.Protocol.MainServer.PlaySupport.window.PlaySupportWindow.Session(this);
        return pssWindowSession;
    }

    private void clearPssWindowSession() {
        final var current = pssWindowSession;
        pssWindowSession = null;
        if (current != null) current.close();
    }
    private volatile boolean clientPssMapRules;
    private static final AtomicReferenceFieldUpdater<CUserSocket, ClientAutomationSession> AUTOMATION =
            AtomicReferenceFieldUpdater.newUpdater(CUserSocket.class, ClientAutomationSession.class, "automationSession");

    public ClientAutomationSession getAutomationSession() { return automationSession; }
    public boolean requiresPlayerTeleportRules() { return clientPssMapRules; }

    public boolean tryClaimAutomation(ClientAutomationSession requested) {
        if (requested == null) throw new NullPointerException("requested");
        if (requested.clientDriven() && (!isConnected() || !_gameSessionReady)) return false;
        if (!AUTOMATION.compareAndSet(this, null, requested)) return false;
        if (requested.clientDriven()) clientPssMapRules = true;
        if (requested.clientDriven() && (!isConnected() || !_gameSessionReady)) {
            AUTOMATION.compareAndSet(this, requested, null);
            return false;
        }
        return true;
    }

    public boolean releaseAutomation(ClientAutomationSession expected) {
        return expected != null && AUTOMATION.compareAndSet(this, expected, null);
    }

    /** CAS replacement keeps PSS running while the persisted daily lease rotates. */
    public boolean replaceAutomation(ClientAutomationSession expected, ClientAutomationSession next) {
        return expected != null && next != null && expected.clientDriven()
                && next.clientDriven() && AUTOMATION.compareAndSet(this, expected, next);
    }


    public boolean isGameSessionReady() {
        return _gameSessionReady;
    }

    public boolean isCurrentCharacter(L1PcInstance pc) {
        return pc != null && isConnected() && getActiveChar() == pc
                && pc.getNetConnection() == this;
    }

    public boolean ownsReadyGameSession() {
        if (!_gameSessionReady || getConnectionState() != CConnectionState.IN_GAME
                || getAccount() == null) {
            return false;
        }
        final L1PcInstance pc = getActiveChar();
        return isCurrentCharacter(pc) && !pc.isLogoutStarted();
    }

    public void publishGameSessionReady(L1PcInstance pc) {
        if (pc != null && _activeChar == pc &&
                _connectionState == CConnectionState.IN_GAME && isConnected()) {
            _gameSessionReady = true;
        }
    }

    public void clearGameSessionReady() {
        _gameSessionReady = false;
        playerShopView = null;
        PlaySupport2019.onClientDetached(this);
        automationSession = null;
        clearPssWindowSession();
    }

	public L1PcInstance getActiveChar() {
		return _activeChar;
	}

	public void setAccount(Account account) {
        onAccountChanged(account);
		this.account = account;
	}

    /** Called by a verified server-side identity provider before authentication. */

	public Account getAccount() {
		return account;
	}

    public String loginRegistryKey() { return loginRegistryKey; }
    public void setLoginRegistryKey(String key) { loginRegistryKey = key; }

	public String getAccountName() {
		return account != null ? account.getName() : null;
	}

	public String getIp() {
		return _remoteAddress;
	}

	public String getHostname() {
		return getIp();
	}

	public boolean isConnected() {
		return _gameSession && isClientConnected() && (socketLifecycle & LOGOUT_STARTED) == 0;
	}

	public boolean isClosed() {
		return !isConnected();
	}

	private void enqueue(CServerPacket data) {
		if (data == null || !isConnected()) return;

		try {
			enqueuePacket(data);
		} catch (Exception e) {
			LOG.log(Level.SEVERE, "Failed to enqueue packet write", e);
		}
	}

	public void sendPacket(byte[] data) {
		enqueue(data);
	}

	public void sendPacket(CServerPacket bp) {
		enqueue(bp);
	}

	private void enqueue(byte[] data) {
		if (data == null || data.length == 0 || !isConnected()) return;

		try {
            enqueuePacket(new CBytesPacket(data));
		} catch (Exception e) {
			LOG.log(Level.SEVERE, "Failed to enqueue packet write", e);
		}
	}

	    

    

    

    public void sendPacket(ProtoOutputStream stream, boolean isClear) {
		if (stream == null) return;

		try {
			if (!stream.isCreated()) {
				stream.createProtoBytes();
			}
			enqueue(stream.getProtoBytes());

		} finally {
			if (isClear) {
				stream.dispose();
			}
		}
	}

	public void directSendPacketBytes(Collection<byte[]> col) {
		if (col == null || col.isEmpty()) return;

		for (byte[] data : col) {
			if (data == null) continue;

			try {
				enqueue(data);
			} catch (Exception e) {
				LOG.log(Level.WARNING, "directSendPacketBytes 실패", e);
			}
		}
	}


	// ------------------------------------------------------------------
	// Game-session operations
	// ------------------------------------------------------------------

	protected void writePacket(
			IPacketWriter<CUserSocket> packet) {

		if (packet == null || !isConnected()) {
			return;
		}

		enqueuePacket(packet);
	}







	public void close(
			IPacketWriter<CUserSocket> packet) {

		if (!isConnected()) {
			LOG.warning(
					String.format(
							"[GameClient Close Skip] 이미 종료 중 - IP: %s",
							getHostAddress()));
			return;
		}

		requestClientClose(packet);
	}

	public void disconnect() {
		if (!beginClientDisconnect()) {
			return;
		}

		try {
			onDisconnection();

		} catch (Throwable t) {
			/*
			 * RWorker owner close path에서도 들어올 수 있다.
			 * 상위 게임 life-cycle 예외가 owner pump까지 빠져나가지 않게 한다.
			 */
			LOG.log(
					Level.SEVERE,
					String.format(
							"[GameClient Disconnect Err] onDisconnection() 중 예외 발생 - IP: %s",
							getHostAddress()),
					t);

		} finally {
			finishClientDisconnect();
		}
	}

	/** Socket shard chosen when the connection was admitted. */

	public String getHostAddress() {
		return getRemoteAddress();
	}


	public int incrementEmptyPacketCount() {
		return ++_emptyPacketCount;
	}

	public void resetEmptyPacketCount() {
		_emptyPacketCount = 0;
	}

	public boolean markShardLogged() {
		return SHARD_LOGGED.compareAndSet(this, 0, 1);
	}

	public void initPacketShard(int shardMask) {
		if (_packetShard >= 0) {
			return;
		}

		_packetShard = affinityShard();
	}

	public int getPacketShard() {
		return _packetShard;
	}

	// 현재 클라이언트의 연결 상태 (기본값: 최초 접속인 CONNECTED)
	private volatile CConnectionState _connectionState = CConnectionState.CONNECTED;

	public CConnectionState getConnectionState() {
		return _connectionState;
	}

	public void setConnectionState(CConnectionState connectionState) {
        if (connectionState != CConnectionState.IN_GAME) {
            _gameSessionReady = false;
            PlaySupport2019.onClientDetached(this);
            clearPssWindowSession();
        }
		_connectionState = connectionState;
        onSessionStateChanged(connectionState);
	}

	public boolean isLoggedIn() {
		return _connectionState == CConnectionState.IN_GAME || _connectionState == CConnectionState.AUTHENTICATED
				|| _connectionState == CConnectionState.OUT_GAME || _connectionState == CConnectionState.DISCONNECTED;
	}

	// 패킷 도배 방지
	//
	// 일반 호출 호환용. network completion hot path는 completion당 한 번 읽은 now를
	// checkPacketRateLimit(long)으로 넘겨 System.currentTimeMillis()를
	// 패킷마다 호출하지 않는다.
	public boolean checkPacketRateLimit() {
		return checkPacketRateLimit(System.currentTimeMillis());
	}

	public boolean checkPacketRateLimit(long now) {
		if (now < _lastCheckTime) {
			_lastCheckTime = now;
			_packetCount = 0;
			return false;
		}

		if (now - _lastCheckTime > 1000L) {
			_packetCount = 0;
			_lastCheckTime = now;
		}

		_packetCount++;
		return _packetCount > CIOConfig.PACKET_LIMIT_PER_SEC;
	}

	// 2차 비번 인증 캐릭터 이름
	private String _lastSelectedCharName;
	public String getLastSelectedCharName() {
		return _lastSelectedCharName;
	}
	public void setLastSelectedCharName(String name) {
		_lastSelectedCharName = name;
	}

	public int getChatCount() {
		return chatCount;
	}

	public void setChatCount(int i) {
		chatCount = i;
	}

	private int secondpasswordfailurecount = 0;
	public int getSecondPasswordFailureCount() {
		return secondpasswordfailurecount;
	}
	public int incSecondPasswordFailureCount() {
		return ++secondpasswordfailurecount;
	}
	public void resetSecondPasswordFailureCount() {
		secondpasswordfailurecount = 0;
	}


    /** Keep exact actor ownership until admitted receipt work has settled. */
    private final class CUserSocketReleaseTask implements Runnable {
        private final L1PcInstance pc;
        private final int stamp;
        private volatile boolean waitingForCacheD;
        private volatile Thread logoutThread;
        private boolean scheduled;
        private boolean signaled;
        private boolean complete;

        private CUserSocketReleaseTask(L1PcInstance pc) {
            this.pc = pc;
            stamp = pc.getSessionStamp();
        }

        private void requestRelease() {
            synchronized (this) {
                if (complete) return;
                if (scheduled) {
                    signaled = true;
                    return;
                }
                scheduled = true;
            }
            submitTerminalRelease(this);
        }

        private void ownerApplyFinished() {
            waitingForCacheD = false;
            requestRelease();
        }

        private boolean ownsActor() {
            return _activeChar == pc && pc.getNetConnection() == CUserSocket.this
                    && pc.getSessionStamp() == stamp;
        }

        private boolean awaitItemTransfers() {
            if (waitingForCacheD) return true;
            final CacheD cache = CacheD.getInstance();
            final int ownerId = pc.getId();
            waitingForCacheD = true;
            if (cache.deferLogoutForOwnerApply(ownerId, this::ownerApplyFinished)) return true;
            waitingForCacheD = false;
            // A terminal effect acquires its continuation hold before releasing
            // the item gate. Read the gate first to observe that handoff.
            return (pc.hasReservedItemTransfer() && !cache.isOwnerPersistenceQuarantined(ownerId))
                    || pc.hasItemTransferContinuation();
        }

        private boolean releaseActor() {
            if (pc.isLogoutStarted() && !pc.isLogoutComplete()) return false;
            if (ownsActor() && awaitItemTransfers()) return false;
            try {
                if (ownsActor() && ((!pc.isPrivateShop() && !pc.isAutoClanjoin())
                        || matchesForcedLogout(pc, stamp)
                        || CacheD.getInstance().isOwnerPersistenceQuarantined(pc.getId()))) {
                    logoutThread = Thread.currentThread();
                    try { logoutPlayer(pc); }
                    finally { logoutThread = null; }
                }
            } finally {
                try { cleanupAfterLogout(pc); }
                catch (RuntimeException failure) { LOG.log(Level.SEVERE, "Terminal session cleanup failed", failure); }
                finally { terminalRelease = null; }
            }
            return true;
        }

        @Override public void run() {
            boolean released = false;
            try {
                released = releaseActor();
            } finally {
                final boolean repeat;
                synchronized (this) {
                    complete = released;
                    scheduled = false;
                    repeat = !released && signaled;
                    signaled = false;
                }
                if (repeat) requestRelease();
            }
        }
    }
}
