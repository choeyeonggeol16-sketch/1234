package l1j.server.AutoHuntSystem;

/** Active time only, measured with a monotonic clock. No game/network dependencies. */
public final class AtsBudget {
    public interface Store {
        Reservation reserve(long requested) throws Exception;
        void refund(long unused) throws Exception;
    }
    public static final class Reservation {
        public final long credit, balance;
        public Reservation(long credit, long balance) { this.credit = credit; this.balance = balance; }
    }
    // Reserve before use so a crash cannot restore already consumed time.
    public static final long CHUNK_MILLIS = 10_000;
    private final Store store;
    private long credit, balance, lastNanos;
    private boolean open;
    public AtsBudget(Store store) { this.store = store; }
    public boolean start(long nowNanos) throws Exception { return start(() -> nowNanos); }
    public boolean start(java.util.function.LongSupplier clock) throws Exception {
        if (open) throw new IllegalStateException("ATS budget already open");
        take(CHUNK_MILLIS); open = credit > 0;
        // Loading the durable balance is not active hunting time.
        lastNanos = clock.getAsLong(); return open;
    }
    private void take(long requested) throws Exception {
        Reservation r = store.reserve(requested);
        if (r.credit < 0 || r.credit > requested || r.balance < 0 || r.credit + r.balance > AtsRules.MAX_MILLIS)
            throw new IllegalStateException("ATS persisted balance invalid");
        credit = r.credit; balance = r.balance;
    }
    public long remaining(long nowNanos) throws Exception {
        if (!open) return 0;
        // nanoTime can have either sign. Subtraction handles wrap for intervals < 292 years.
        long delta = nowNanos - lastNanos;
        if (delta < 0) delta = 0;
        long elapsed = delta / 1_000_000;
        lastNanos += elapsed * 1_000_000;
        while (elapsed > 0) {
            if (credit == 0) {
                if (balance == 0) break;
                // Catch up a long scheduler pause in one transaction, not thousands of tiny reservations.
                take(Math.min(AtsRules.MAX_MILLIS, Math.max(CHUNK_MILLIS, elapsed + CHUNK_MILLIS)));
                if (credit == 0) break;
            }
            long used = Math.min(credit, elapsed); credit -= used; elapsed -= used;
        }
        return credit + balance;
    }
    public void stop(long nowNanos) throws Exception {
        if (!open) return;
        try {
            remaining(nowNanos);
            if (credit > 0) store.refund(credit);
        } finally { open = false; credit = balance = 0; }
    }
}
