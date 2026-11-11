package l1j.server.server.network;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/** Low-cardinality counters; no per-account metric labels or permanent session history. */
public final class NetworkMetrics {
    final AtomicLong inboundBytes = new AtomicLong(), outboundBytes = new AtomicLong();
    final AtomicLong inboundPackets = new AtomicLong(), outboundPackets = new AtomicLong();
    final LongAdder accepted = new LongAdder(), rejected = new LongAdder(), processed = new LongAdder();
    final LongAdder gameBatches = new LongAdder(), flushes = new LongAdder(), readPauses = new LongAdder();
    final LongAdder handlerErrors = new LongAdder();
    final LongAdder readResumes = new LongAdder(), readResumeRecoveries = new LongAdder();
    final LongAdder mailboxDispatchFailures = new LongAdder(), mailboxWaitStalls = new LongAdder();
    final LongAdder mailboxRunStalls = new LongAdder();
    final AtomicLong mailboxMaxWaitNanos = new AtomicLong(), mailboxMaxRunNanos = new AtomicLong();
    private final AtomicLong lastStallLogNanos = new AtomicLong();
    private final AtomicLong lastOutputOverloadLogNanos = new AtomicLong();

    boolean allowOutputOverloadLog(long now) {
        long old = lastOutputOverloadLogNanos.get();
        return (old == 0L || now - old >= java.util.concurrent.TimeUnit.SECONDS.toNanos(10))
                && lastOutputOverloadLogNanos.compareAndSet(old, now);
    }

    static void observeMax(AtomicLong maximum, long value) {
        long old;
        while (value > (old = maximum.get()) && !maximum.compareAndSet(old, value)) { }
    }

    // One warning across all sessions per ten seconds. No per-packet logging.
    boolean allowStallLog(long now) {
        long old = lastStallLogNanos.get();
        return (old == 0L || now - old >= java.util.concurrent.TimeUnit.SECONDS.toNanos(10))
                && lastStallLogNanos.compareAndSet(old, now);
    }
    private final EnumMap<CloseReason, LongAdder> closes = new EnumMap<>(CloseReason.class);
    NetworkMetrics() { for (CloseReason r : CloseReason.values()) closes.put(r, new LongAdder()); }
    void closed(CloseReason r) { closes.get(r).increment(); }
    public record Snapshot(long accepted, long rejected, int activeAndRetiring, int ipEntries,
                           long inboundBytes, long outboundBytes, long inboundPackets, long outboundPackets,
                           long processed, long gameBatches, long flushes, long readPauses,
                           long handlerErrors, Map<CloseReason, Long> closes,
                           long readResumes, long readResumeRecoveries, long mailboxDispatchFailures,
                           long mailboxWaitStalls, long mailboxRunStalls,
                           long mailboxMaxWaitMs, long mailboxMaxRunMs,
                           int gameQueued, int gameRunning, int authQueued, int authRunning) { }
    Snapshot snapshot(int active, int ips, int gameQueued, int gameRunning, int authQueued, int authRunning) {
        EnumMap<CloseReason, Long> c = new EnumMap<>(CloseReason.class);
        closes.forEach((k,v) -> c.put(k, v.sum()));
        return new Snapshot(accepted.sum(), rejected.sum(), active, ips, inboundBytes.get(), outboundBytes.get(),
                inboundPackets.get(), outboundPackets.get(), processed.sum(), gameBatches.sum(), flushes.sum(),
                readPauses.sum(), handlerErrors.sum(), Map.copyOf(c),
                readResumes.sum(), readResumeRecoveries.sum(), mailboxDispatchFailures.sum(),
                mailboxWaitStalls.sum(), mailboxRunStalls.sum(),
                java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(mailboxMaxWaitNanos.get()),
                java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(mailboxMaxRunNanos.get()),
                gameQueued, gameRunning, authQueued, authRunning);
    }
    static boolean reserve(AtomicLong n, long amount, long max) {
        for (;;) { long old = n.get(); if (amount > max - old) return false;
            if (n.compareAndSet(old, old + amount)) return true; }
    }
    static void release(AtomicLong n, long amount) {
        if (n.addAndGet(-amount) < 0) throw new IllegalStateException("Budget double release");
    }
}
