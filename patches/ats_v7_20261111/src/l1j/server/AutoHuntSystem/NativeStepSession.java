package l1j.server.AutoHuntSystem;

/**
 * One server-authorized move per character. Never mutates character position.
 * The client DLL receives server-issued direction commands on its control
 * channel and generates a normal C_MoveChar through the game's input code.
 */
public final class NativeStepSession {
    public interface CommandWriter { void send(String command); }

    // Authorizations are checked on packet receipt, not on a queued action worker.
    // This window accounts for the real client input / TCP round trip.
    public static final long EXPIRY_NANOS = 900_000_000L;
    private static final long IN_FLIGHT_NANOS = 3_000_000_000L;
    private static final long MAX_UNANSWERED_MOVE_NANOS = 900_000_000L;
    private static final int MAX_SILENT_RETRIES = 2;
    private static final long POST_MOVE_SETTLE_NANOS = 1_500_000_000L;
    private static final long RESIDUAL_QUIET_NANOS = 600_000_000L;
    // Keep recovery spacing after failed or silent input. Successful WALKs
    // use their actual sprite interval in completeMove instead.
    private static final long RETRY_GAP_NANOS = 600_000_000L;
    private static final long LEGACY_WALK_INTERVAL_MILLIS = 600L;
    public static final int MOVE_APPROVED = 0;
    public static final int MOVE_INACTIVE = 1;
    public static final int MOVE_NO_OFFER = 2;
    public static final int MOVE_IN_FLIGHT = 3;
    public static final int MOVE_EXPIRED = 4;
    public static final int MOVE_WORLD_CHANGED = 5;
    public static final int MOVE_CLIENT_ORIGIN = 6;
    public static final int MOVE_HEADING = 7;
    // Reserved for backward-compatible callers; no adjusted heading is
    // authorized in the strict one-step protocol.
    public static final int MOVE_ADJUSTED = 8;
    private static final java.util.concurrent.atomic.AtomicInteger TRACE =
            new java.util.concurrent.atomic.AtomicInteger();
    private final CommandWriter writer;
    private final java.util.function.LongSupplier clock;
    // Increments only after the WALK handler applies the authoritative tile.
    // A receive-side approval by itself does NOT advance a reserved route.
    private long appliedStepSequence;
    // Monotonic across OFF/ON: a queued WALK from a previous session must not
    // acknowledge or apply a new session's offer.
    private long approvalSerial;
    private long inFlightSerial;
    private boolean active;
    private boolean pending;
    private boolean moving;
    private boolean inFlight;
    private long inFlightDeadline;
    private long lastDispatch;
    private long firstDispatch;
    private long nextOfferAt;
    private int originMismatchCount;
    private int silentRetries;
    private boolean packetSeenForOffer;
    private boolean timedOutWithoutPacket;
    private boolean needsResync;
    private int unsolicitedMoves;
    private int x, y, mapId, heading, screenHeading;
    private long deadline;

    public NativeStepSession(CommandWriter writer) {
        this(writer, System::nanoTime);
    }

    NativeStepSession(CommandWriter writer, java.util.function.LongSupplier clock) {
        if (writer == null || clock == null) throw new IllegalArgumentException("writer/clock");
        this.writer = writer;
        this.clock = clock;
    }

    public synchronized void begin() {
        appliedStepSequence = 0L;
        pending = moving = inFlight = false;
        inFlightSerial = 0L;
        lastDispatch = firstDispatch = 0L;
        nextOfferAt = 0L;
        originMismatchCount = 0;
        silentRetries = 0;
        packetSeenForOffer = false;
        timedOutWithoutPacket = false;
        needsResync = false;
        unsolicitedMoves = 0;
        System.out.println("[ClientInput] step-v5 sprite-cadence strict-heading control armed");
        active = true;
        try { writer.send("@@NATIVESTEP:START@@"); }
        catch (RuntimeException ex) { active = false; throw ex; }
    }

    public synchronized boolean offerMove(int x, int y, int map, int dir, long now) {
        if (!active || dir < 0 || dir > 7) return false;
        if (needsResync || now - nextOfferAt < 0) return false;
        // C_MoveChar can be in the existing CharacterActionExecutor queue.
        // Do not grant another move for the same server origin until that
        // previously received move has actually been applied.
        if (inFlight) {
            if (now - inFlightDeadline < 0) return false;
            markMoveNotApplied(); // queued/dropped action must not be skipped
            return false;
        }
        // One server offer == one DLL click. A pending offer is not refreshed
        // or redirected; later AI ticks must wait for the move or timeout.
        if (pending) return false;
        if (!pending) {
            silentRetries = 0;
            packetSeenForOffer = false;
            timedOutWithoutPacket = false;
            firstDispatch = now;
            // No speculative mouse-sector calibration. Old C_MoveChar packets
            // after turning are NOT evidence of a stable input->world mapping.
            screenHeading = dir;
        }
        pending = true;
        moving = true;
        this.x = x;
        this.y = y;
        this.mapId = map;
        heading = dir;
        deadline = now + EXPIRY_NANOS;
        lastDispatch = now;
        try {
            writer.send("@@NATIVESTEP:" + screenHeading + "@@");
            int n = TRACE.getAndIncrement();
            if (n < 40) System.out.println("[ClientInput] move offered direction=" + dir +
                    " screen=" + screenHeading);
            return true;
        }
        catch (RuntimeException ex) { pending = moving = false; throw ex; }
    }

    /**
     * Timed cleanup only. The client issues ONE short click per offer; a
     * 75ms network keepalive would grant extra time for client-side movement
     * and is both unnecessary and unsafe.
     */
    public synchronized void refreshPending(long now) {
        if (!active || !pending || inFlight) return;
        if (now - firstDispatch < MAX_UNANSWERED_MOVE_NANOS) return;
        pending = moving = false;
        timedOutWithoutPacket = !packetSeenForOffer;
        nextOfferAt = now + RETRY_GAP_NANOS;
        if (!timedOutWithoutPacket) {
            // A move packet reached the server but never produced a valid step.
            // Retrying would risk a second local step from a stale origin.
            needsResync = true;
        }
        try { writer.send("@@NATIVESTEP:CANCEL@@"); }
        catch (RuntimeException ignored) {}
    }

    /**
     * The client can ignore a short press (e.g. a click inside the sprite).
     * Retry only when NO C_MoveChar was observed, the server origin is still
     * unchanged, and the previous press has been explicitly cancelled.
     * Returning true also means the quiet interval is still in progress.
     */
    public synchronized boolean retrySilentMove(int actualX, int actualY, int actualMap, long now) {
        if (!active || needsResync || !timedOutWithoutPacket || pending || inFlight)
            return false;
        if (actualX != x || actualY != y || actualMap != mapId) {
            needsResync = true;
            return false;
        }
        if (now - nextOfferAt < 0) return true;
        if (silentRetries >= MAX_SILENT_RETRIES) return false;
        ++silentRetries;
        pending = moving = true;
        timedOutWithoutPacket = false;
        packetSeenForOffer = false;
        firstDispatch = lastDispatch = now;
        deadline = now + EXPIRY_NANOS;
        try {
            writer.send("@@NATIVESTEP:" + screenHeading + "@@");
            System.out.println("[ClientInput] silent click retry=" + silentRetries +
                    " direction=" + heading);
            return true;
        } catch (RuntimeException ex) {
            pending = moving = false;
            needsResync = true;
            throw ex;
        }
    }

    public synchronized boolean hasOutstandingMove(long now) {
        if (inFlight && now - inFlightDeadline >= 0) markMoveNotApplied();
        return pending || inFlight;
    }

    /**
     * Categorize the first receive point, before the action worker runs.
     * In particular, a duplicate packet while the previously approved action
     * is still queued is NOT a new user movement command.
     */
    public synchronized int consumeMoveStatus(int actualX, int actualY, int map,
            int packetX, int packetY, int dir, long now) {
        if (!active) return MOVE_INACTIVE;
        if (!pending) {
            // The client has already executed the mouse command and might
            // still be finishing the prior path. These extra C_MoveChar
            // packets must never overwrite the single WALK executor slot.
            ++unsolicitedMoves;
            // The previous client click can still have an internal destination.
            // Give it time to drain before posting the next synthetic click.
            if (now > 0L && now + RESIDUAL_QUIET_NANOS > nextOfferAt)
                nextOfferAt = now + RESIDUAL_QUIET_NANOS;
            if (unsolicitedMoves >= 2 && (inFlight || now - lastDispatch < POST_MOVE_SETTLE_NANOS)) {
                needsResync = true;
                nextOfferAt = now + POST_MOVE_SETTLE_NANOS;
                try { writer.send("@@NATIVESTEP:CANCEL@@"); }
                catch (RuntimeException ignored) {}
            }
            return inFlight ? MOVE_IN_FLIGHT : MOVE_NO_OFFER;
        }
        // One incoming packet is enough to prohibit a blind click retry.
        packetSeenForOffer = true;
        if (now - deadline >= 0) {
            pending = false;
            timedOutWithoutPacket = false;
            needsResync = true;
            return MOVE_EXPIRED;
        }
        if (this.x != actualX || this.y != actualY || this.mapId != map) {
            needsResync = true;
            pending = false;
            return MOVE_WORLD_CHANGED;
        }
        if (actualX != packetX || actualY != packetY) {
            // Repeated wrong-origin packets prove local client prediction has
            // outpaced the authoritative server. Do not attack from desynced
            // coordinates; request a one-off authoritative position reset.
            if (++originMismatchCount >= 2) {
                needsResync = true;
                pending = false;
                try { writer.send("@@NATIVESTEP:CANCEL@@"); }
                catch (RuntimeException ignored) {}
            }
            return MOVE_CLIENT_ORIGIN;
        }
        if (dir < 0 || dir > 7) return MOVE_HEADING;
        if (heading != dir) {
            // A stale C_MoveChar from a prior click can have the correct
            // origin but a DIFFERENT heading. Accepting it was the source
            // of random walking and off-position ranged attack effects.
            // Never mutate server position for a non-authorized direction.
            pending = moving = false;
            needsResync = true;
            nextOfferAt = now + POST_MOVE_SETTLE_NANOS;
            try { writer.send("@@NATIVESTEP:CANCEL@@"); }
            catch (RuntimeException ignored) {}
            return MOVE_HEADING;
        }
        originMismatchCount = 0;
        unsolicitedMoves = 0;
        pending = false;
        inFlight = true;
        inFlightSerial = ++approvalSerial;
        inFlightDeadline = now + IN_FLIGHT_NANOS;
        // Stop the held click before the client can send a second move.
        try { writer.send("@@NATIVESTEP:CANCEL@@"); }
        catch (RuntimeException ignored) { /* Keep this packet's approval. */ }
        return MOVE_APPROVED;
    }

    public synchronized boolean consumeMove(int actualX, int actualY, int map,
            int packetX, int packetY, int dir, long now) {
        int status = consumeMoveStatus(actualX, actualY, map, packetX, packetY, dir, now);
        return status == MOVE_APPROVED;
    }

    /** Ignore residual synthetic movement. Manual cancellation has a separate
     * explicit DLL-to-server control message. */
    public synchronized boolean discardUnapproved(int status, long now) {
        // Physical manual clicks are reported explicitly by the DLL (U\n).
        // Auto-start, path leftovers and delayed C_MoveChar cannot be used to
        // infer a manual stop, otherwise the same click cancels the hunt.
        return active && status != MOVE_APPROVED;
    }

    /** The action executor dropped or invalidated the packet after admission. */
    /** A queued packet may finish after OFF/ON; ignore that obsolete failure. */
    public synchronized void markMoveNotApplied(long acceptedSerial) {
        if (acceptedSerial == 0L || !inFlight || acceptedSerial != inFlightSerial) return;
        markMoveNotApplied();
    }

    public synchronized void markMoveNotApplied() {
        if (!active) return;
        needsResync = true;
        timedOutWithoutPacket = false;
        pending = moving = inFlight = false;
        inFlightSerial = 0L;
        try { writer.send("@@NATIVESTEP:CANCEL@@"); }
        catch (RuntimeException ignored) {}
    }

    public static String moveReason(int status) {
        switch (status) {
        case MOVE_APPROVED: return "approved";
        case MOVE_ADJUSTED: return "screen-heading-adjusted";
        case MOVE_INACTIVE: return "inactive";
        case MOVE_NO_OFFER: return "no-offer";
        case MOVE_IN_FLIGHT: return "in-flight-duplicate";
        case MOVE_EXPIRED: return "expired";
        case MOVE_WORLD_CHANGED: return "server-origin-changed";
        case MOVE_CLIENT_ORIGIN: return "packet-origin-mismatch";
        case MOVE_HEADING: return "heading-mismatch";
        default: return "unknown";
        }
    }

    /** Capture this immediately after MOVE_APPROVED, on the inbound thread. */
    public synchronized long currentApprovedSerial() {
        return inFlight ? inFlightSerial : 0L;
    }

    public synchronized boolean isCurrentApprovedSerial(long acceptedSerial) {
        return active && inFlight && acceptedSerial != 0L && acceptedSerial == inFlightSerial;
    }

    /** Completes only its own accepted move; never a later or restarted move. */
    public synchronized void completeMove(boolean applied, long acceptedSerial) {
        completeMove(applied, acceptedSerial, LEGACY_WALK_INTERVAL_MILLIS);
    }

    /** Pace successful steps using the same sprite interval as the WALK handler. */
    public synchronized void completeMove(boolean applied, long acceptedSerial, long walkIntervalMillis) {
        if (!isCurrentApprovedSerial(acceptedSerial)) return;
        if (applied) ++appliedStepSequence;
        timedOutWithoutPacket = false;
        inFlight = false;
        inFlightSerial = 0L;
        moving = false;
        long now = clock.getAsLong();
        // A successful step needs its normal animation interval, not a fixed
        // extra 600ms recovery pause. Failed/silent input keeps the retry gap.
        long interval = walkIntervalMillis > 0L && walkIntervalMillis <= Long.MAX_VALUE / 1_000_000L
                ? walkIntervalMillis * 1_000_000L : RETRY_GAP_NANOS;
        nextOfferAt = now + (applied ? interval : RETRY_GAP_NANOS);
        if (!applied) {
            needsResync = true;
            try { writer.send("@@NATIVESTEP:CANCEL@@"); }
            catch (RuntimeException ignored) {}
        }
    }

    // Legacy direct-test API: production WALK actions use the captured serial.
    public synchronized void completeMove(boolean applied) {
        completeMove(applied, inFlightSerial);
    }
    public synchronized void completeMove() { completeMove(true); }

    public synchronized boolean takeResyncRequest() {
        if (!needsResync) return false;
        needsResync = false;
        return true;
    }

    public synchronized void cancelMovement() {
        boolean shouldCancel = moving;
        pending = moving = inFlight = false;
        timedOutWithoutPacket = false;
        inFlightSerial = 0L;
        nextOfferAt = 0L;
        if (active && shouldCancel) {
            try { writer.send("@@NATIVESTEP:CANCEL@@"); }
            catch (RuntimeException ignored) { /* disconnect must not block teleport */ }
        }
    }

    public synchronized void end() {
        boolean shouldCancel = active;
        active = pending = moving = inFlight = false;
        timedOutWithoutPacket = false;
        inFlightSerial = 0L;
        nextOfferAt = 0L;
        if (shouldCancel) {
            try { writer.send("@@NATIVESTEP:STOP@@"); }
            catch (RuntimeException ignored) { /* disconnect must not block cleanup */ }
        }
    }

    public synchronized long appliedStepSequence() { return appliedStepSequence; }

    public synchronized boolean isActive() { return active; }
}
