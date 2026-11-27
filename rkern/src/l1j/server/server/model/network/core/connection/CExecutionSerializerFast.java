package l1j.server.server.model.network.core.connection;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

import l1j.server.server.model.network.core.transport.selector.CSelectorTransport;

/** Inherited, allocation-neutral storage and execution of the socket FIFO.
 * The socket itself is the serializer state; no separate delegate, queue,
 * worker, or additional per-SEND allocation is introduced.
 * Transport/SEND hooks execute under the original owner token.
 */
public abstract class CExecutionSerializerFast {
    static final int SOCKET_SLOTS = 512;
    static final int SOCKET_MASK = SOCKET_SLOTS - 1;
    static final int SOCKET_REJECT_AT = SOCKET_SLOTS - 8;
    private static final int SOCKET_SHARE_MIN_SENDS = 16;
    private static final int SOCKET_SHARE_SLICE = 32;
    private static final int DRAIN_TRANSFERRED = -1;
    static final byte SOCKET_SEND_START = 1;
    static final byte SOCKET_WRITE_COMPLETE = 2;
    static final byte SOCKET_SEND_ENQUEUE = 3;
    static final byte SOCKET_CANCELLED = 4;
    // The existing serializer ring can carry an 8-byte action in one long.
    // No SEND lease is rented while an earlier foreign producer ticket is unsealed.
    static final byte SOCKET_PACKED_ACTION = 5;
    final byte[] _socketCallbacks = new byte[SOCKET_SLOTS];
    final long[] _socketArguments = new long[SOCKET_SLOTS];
    final CIOBufferEx[] _socketSendArguments = new CIOBufferEx[SOCKET_SLOTS];

    volatile long _socketTicket;
    // The producer may inspect whether a staged ticket was already claimed.
    volatile long _socketHead;
    volatile int _socketPending;
    volatile int _socketDraining;
    // Optional SEND_START ticket coalescing: a reusable one-bit wake-up. The
    // serializer clears it only while owning the socket callback token.
    volatile int _socketStartPending;
    volatile Thread _socketOwner;
    // Owner-only scope: a single complete READ frame can reuse the otherwise
    // idle socket serializer. No additional ring slot or worker is allocated.
    boolean _readSocketOwnerActive;
    // Java NIO.2 may complete synchronously inside the write() invocation.
    // Such a callback must not retire its pooled ByteBuffer until write() returns.
    volatile boolean _socketPosting;
    // Owner-thread-only: a completion observed inside write() is consumed by
    // the serializer loop after write() returns, without a second ring ticket.
    // Never consume it from the completion handler or recurse into the pump.
    long _inlineWriteCompletion;
    // A completed WRITE already committed before a later SEND reservation.
    // Published by the callback, consumed only by the socket serializer owner.
    // Unlike a ring ticket this cannot sit behind an unpublished SEND head.
    volatile long _prefixWriteCompletion;
    // The current callback has already advanced _socketHead but is still
    // included in _socketPending until its finally block runs.
    boolean _socketExecutingTicket;
    // A socket issues at most one outstanding WRITE. The mailbox rejects a
    // duplicate completion before its serializer ticket can run.
    volatile long _writeEvent;

    static final boolean READ_WRITE_OWNER_FUSION = true;
    static final boolean COALESCE_SEND_START = true;
    static final boolean INLINE_WRITE_COMPLETION = true;

    static final VarHandle SOCKET_TICKET, SOCKET_PENDING, SOCKET_DRAINING, SOCKET_START_PENDING, WRITE_EVENT;
    static final VarHandle SOCKET_CALLBACK_SLOT = MethodHandles.arrayElementVarHandle(byte[].class);
    static final VarHandle SOCKET_ARGUMENT_SLOT = MethodHandles.arrayElementVarHandle(long[].class);
    static {
        try {
            final MethodHandles.Lookup lookup = MethodHandles.lookup();
            SOCKET_TICKET = lookup.findVarHandle(CExecutionSerializerFast.class, "_socketTicket", long.class);
            SOCKET_PENDING = lookup.findVarHandle(CExecutionSerializerFast.class, "_socketPending", int.class);
            SOCKET_DRAINING = lookup.findVarHandle(CExecutionSerializerFast.class, "_socketDraining", int.class);
            SOCKET_START_PENDING = lookup.findVarHandle(CExecutionSerializerFast.class, "_socketStartPending", int.class);
            WRITE_EVENT = lookup.findVarHandle(CExecutionSerializerFast.class, "_writeEvent", long.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public abstract void close();
    abstract boolean serializerClosed();
    abstract void notifyClosedWhenReleased();
    abstract void driveWriteOnce();
    abstract void drainWriteCompletion(long event);
    abstract void acceptStagedSend(CIOBufferEx lease);
    abstract void acceptPackedAction(long packed);
    abstract boolean hasCommittedWriteReady();

    private enum DrainMode { PINNED, LOCAL, SHARED, RETURNED }

    /** Move the already-claimed token; refusal keeps it on the caller. */
    boolean transferSocketDrain(boolean returning) { return false; }

    final boolean isSocketSerializerOwner() {
        return _socketOwner == Thread.currentThread() && !_socketPosting;
    }

    /**
     * A ticket-free READ owner may execute a SEND control callback directly
     * only while the FIFO is still empty. Foreign producers can reserve a
     * ticket without taking _stateLock, including before publishing its slot.
     * Normal ticket-owning serializer callbacks retain their existing path.
     */
    final boolean canExecuteOwnerControlDirect() {
        if (!isSocketSerializerOwner()) return false;
        if (!READ_WRITE_OWNER_FUSION || !_readSocketOwnerActive) return true;
        return (int) SOCKET_PENDING.getVolatile(this) == 0
                && (long) SOCKET_TICKET.getVolatile(this) == _socketHead
                && (!COALESCE_SEND_START
                    || (int) SOCKET_START_PENDING.getAcquire(this) == 0);
    }

    final boolean serializeSocketCallback(byte kind, long argument) {
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
    final void drainPublishedSocketCallbacks() {
        if (_socketOwner == Thread.currentThread()
                && (int) SOCKET_DRAINING.getVolatile(this) != 0) {
            if (!_socketPosting && !_socketExecutingTicket)
                drainSocketCallbacksOwnedReady();
            return;
        }
        drainSocketCallbacks();
    }

    /** First contender claims the drain; other callers return. */
    final void drainSocketCallbacks() {
        if (!SOCKET_DRAINING.compareAndSet(this, 0, 1)) return;
        drainSocketCallbacksAcquired(CSelectorTransport.SHARED_SEND_WORKERS
                ? DrainMode.LOCAL : DrainMode.PINNED);
    }

    /**
     * Consume only ready FIFO callbacks while the current thread holds the
     * serializer. Never releases the ownership bit: a fused READ still owns
     * it until its outer finally runs. An unsealed foreign ticket stops the
     * drain rather than permitting later SENDs to overtake it.
     */
    final void drainSocketCallbacksOwnedReady() {
        drainSocketCallbacksOwnedReady(DrainMode.PINNED, 0);
    }

    private int drainSocketCallbacksOwnedReady(DrainMode mode, int untilTransfer) {
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
                        : (byte) SOCKET_CALLBACK_SLOT.getAcquire(_socketCallbacks, slot);
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
                if (pending == 0 || kind == 0L) return untilTransfer;
                if (mode != DrainMode.PINNED) {
                    if (untilTransfer == 0) {
                        // Reservations include unpublished slots and packing
                        // tombstones. A peer needs a ready prefix with real SENDs.
                        if (!_readSocketOwnerActive && !_socketPosting && !_socketExecutingTicket
                                && _prefixWriteCompletion == 0L
                                && (mode == DrainMode.SHARED || hasReadySendBacklog(pending))
                                && transferSocketDrain(mode == DrainMode.SHARED)) return DRAIN_TRANSFERRED;
                        untilTransfer = SOCKET_SHARE_SLICE;
                    }
                    --untilTransfer;
                }
                final long argument = (long) SOCKET_ARGUMENT_SLOT.getAcquire(_socketArguments, slot);
                final CIOBufferEx sendLease = kind == SOCKET_SEND_ENQUEUE
                        ? _socketSendArguments[slot] : null;
                _socketSendArguments[slot] = null;
                SOCKET_ARGUMENT_SLOT.setRelease(_socketArguments, slot, 0L);
                SOCKET_CALLBACK_SLOT.setRelease(_socketCallbacks, slot, (byte) 0);
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

    private boolean hasReadySendBacklog(int pending) {
        if (pending < SOCKET_SHARE_MIN_SENDS) return false;
        int sends = 0;
        int limit = Math.min(pending, SOCKET_SHARE_SLICE);
        int head = (int) _socketHead;
        for (int i = 0; i < limit; i++) {
            byte kind = (byte) SOCKET_CALLBACK_SLOT.getAcquire(_socketCallbacks, (head + i) & SOCKET_MASK);
            if (kind == 0) return false;
            if ((kind == SOCKET_SEND_ENQUEUE || kind == SOCKET_PACKED_ACTION)
                    && ++sends == SOCKET_SHARE_MIN_SENDS) return true;
        }
        return false;
    }

    /** The caller already holds the serializer, possibly in a fused scope. */
    final void drainSocketCallbacksAcquired() {
        drainSocketCallbacksAcquired(DrainMode.PINNED);
    }

    final void drainSharedSocketCallbacks() {
        drainSocketCallbacksAcquired(DrainMode.SHARED);
    }

    final void resumeSharedSocketCallbacks(boolean cancelled) {
        drainSocketCallbacksAcquired(cancelled ? DrainMode.PINNED : DrainMode.RETURNED);
    }

    private void drainSocketCallbacksAcquired(DrainMode mode) {
        // Returned work gets a local slice before it can be shared again.
        // Keep the remaining count if a publication races with token release.
        int untilTransfer = mode == DrainMode.LOCAL ? 0 : SOCKET_SHARE_SLICE;
        for (;;) {
            _socketOwner = Thread.currentThread();
            untilTransfer = drainSocketCallbacksOwnedReady(mode, untilTransfer);
            if (untilTransfer == DRAIN_TRANSFERRED) return;

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

            if (serializerClosed() && (int) SOCKET_PENDING.getVolatile(this) == 0) {
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
            // This store must precede the readiness loads below (StoreLoad).
            // A release-only store permits both this owner to miss a newly
            // published callback and its publisher to observe the old owner.
            // Then the ready callback has no drainer and no future I/O to
            // wake it. Volatile release closes that lost-wakeup window.
            SOCKET_DRAINING.setVolatile(this, 0);
            
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
                    && (byte) SOCKET_CALLBACK_SLOT.getAcquire(_socketCallbacks, nextSlot) != 0L;
            // A foreign completion may publish the prefix after this owner
            // checked the blocked head but before it released its token.
            // Reacquire rather than losing the sole completion notification.
            final boolean prefixReady = _prefixWriteCompletion != 0L && !_socketPosting;
            if ((!startReady && !callbackReady && !prefixReady)
                    || !SOCKET_DRAINING.compareAndSet(this, 0, 1)) break;
        }
        notifyClosedWhenReleased();
    }
}
