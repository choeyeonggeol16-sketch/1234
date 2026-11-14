package l1j.server.server.model.network.core.diagnostics;

import l1j.server.server.model.network.core.config.CIOConfig;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.atomic.*;

/** Bounded metadata only: opcode/time/length, never credentials or packet bodies. */
public final class CNetworkDiagnostics {
    private CNetworkDiagnostics() {}
    public enum PacketKind { EXTENDED, PROTOBUF, CRAFT, ACTION_UI }
    /** L1 diagnostic labels; these are not reconstructed L2 enum numbers. */
    public enum CloseReason {
        SERVER_CLOSE, GRACEFUL_CLOSE, SEND_LIMIT, SEND_POOL, SEND_REJECTED, ENCODING_ERROR,
        PROTOCOL_ERROR, HANDLER_ERROR, WRITE_TIMEOUT, PARTIAL_READ_TIMEOUT, GRACE_TIMEOUT
    }
    private static final AtomicLong NEXT = new AtomicLong();
    private static final Map<Long, Trace> LIVE = new LinkedHashMap<>();
    private static final Trace[] CLOSED = new Trace[64];
    private static int closedCursor;
    private static final List<Work> OWNERS = new ArrayList<>();
    // These are observational totals, not admission/lifetime counters. Stripe
    // concurrent updates instead of bouncing one cache line between workers.
    // Reports are live snapshots; after writers stop, sums are exact.
    private static LongAdder[] counters() {
        LongAdder[] result = new LongAdder[256];
        for (int i = 0; i < result.length; i++) result[i] = new LongAdder();
        return result;
    }
    private static volatile LongAdder[] counts = counters();
    private static final class Timing {
        final LongAdder[] completed = counters(), nanos = counters();
        final AtomicLongArray max = new AtomicLongArray(256);
    }
    private static final class Traffic {
        final long started = System.nanoTime();
        final LongAdder received = new LongAdder(), admitted = new LongAdder();
    }
    private static volatile Timing timing = new Timing();
    private static volatile Traffic traffic = new Traffic();
    private static final ThreadLocal<Work> LOCAL = new ThreadLocal<>();
    private static Work localWork() {
        Work present = LOCAL.get();
        if (present != null) return present;
        Work work = new Work(Thread.currentThread());
        synchronized (CNetworkDiagnostics.class) {
            OWNERS.removeIf(w -> { Thread thread = w.thread.get(); return thread == null || !thread.isAlive(); });
            if (OWNERS.size() < 256) OWNERS.add(work);
        }
        LOCAL.set(work);
        return work;
    }
    public static final class Trace {
        public final long id = NEXT.incrementAndGet();
        final String remote;
        final long nativeHandle;
        private final int[] opcode = new int[128];
        private final PacketKind[] kinds = new PacketKind[128];
        private final int[] commands = new int[128];
        private final long[] at = new long[128];
        private long sequence;
        volatile boolean closed;
        private CloseReason javaReason;
        private boolean nativeStatusKnown;
        private int nativeOperation, nativeError;
        Trace(String remote, long nativeHandle) { this.remote = remote; this.nativeHandle = nativeHandle; }
        public synchronized void javaClose(CloseReason reason) {
            // A cleanup close following native shutdown is not its initiator.
            if (javaReason == null && !(reason == CloseReason.SERVER_CLOSE && nativeStatusKnown)) javaReason = reason;
        }
        public synchronized void nativeClose(int operation, int error) {
            if (nativeStatusKnown) return;
            nativeOperation = operation; nativeError = error; nativeStatusKnown = true;
        }
        private String nativeCause() {
            if (!nativeStatusKnown) return "UNKNOWN";
            if (nativeError == 10060) return "TIMEOUT";
            if (nativeError == 13 && nativeOperation == 2) return "PROTOCOL_ERROR";
            if (nativeError == 995) return "IO_CANCELLED";
            return switch (nativeOperation) {
                case 1 -> "ACCEPT_REJECTED";
                case 2 -> nativeError == 0 ? "PEER_EOF" : "READ_ERROR";
                case 3 -> "WRITE_ERROR";
                case 5 -> nativeError == 0 ? "TRANSPORT_CLOSE" : "SHUTDOWN_ERROR";
                default -> "NATIVE_CLOSE";
            };
        }
        synchronized String summary() {
            return "id=" + id + " native=" + nativeHandle + " peer=" + remote + " closed=" + closed
                    + " javaReason=" + (javaReason == null ? "NONE" : javaReason)
                    + " nativeCause=" + nativeCause()
                    + (nativeStatusKnown ? " operation=" + nativeOperation + " error=" + Integer.toUnsignedString(nativeError) : "") + "\n";
        }
        synchronized long record(int op) {
            long ticket = sequence++;
            int slot = (int)(ticket & 127);
            opcode[slot] = op; kinds[slot] = null; commands[slot] = 0; at[slot] = System.nanoTime();
            return ticket;
        }
        synchronized void command(long ticket, PacketKind kind, int command) {
            // A paused decoder must not relabel a newer event after ring wrap.
            if (ticket < 0 || ticket >= sequence || sequence - ticket > 128) return;
            int slot = (int)(ticket & 127);
            kinds[slot] = kind; commands[slot] = command;
        }
        synchronized String recent() {
            StringBuilder b = new StringBuilder(summary());
            long now = System.nanoTime();
            for (long i = sequence - 1, end = Math.max(0, sequence - 128); i >= end; --i) {
                int slot = (int)(i & 127);
                b.append("#").append(i + 1).append(" opcode=").append(opcode[slot])
                        .append(" ageMs=").append(Math.max(0, now - at[slot]) / 1_000_000);
                appendCommand(b, kinds[slot], commands[slot]);
                b.append('\n');
            }
            return b.toString();
        }
    }
    public static Trace open(String remote) { return open(remote, 0); }
    public static Trace open(String remote, long nativeHandle) {
        if (!CIOConfig.DIAGNOSTICS_ENABLED) return null;
        synchronized (CNetworkDiagnostics.class) {
            if (LIVE.size() >= CIOConfig.DIAGNOSTICS_MAX_CONNECTIONS) return null;
            Trace trace = new Trace(remote, nativeHandle); LIVE.put(trace.id, trace); return trace;
        }
    }
    public static void close(Trace trace) {
        if (trace == null) return;
        synchronized (CNetworkDiagnostics.class) {
            if (LIVE.remove(trace.id, trace)) {
                trace.closed = true; CLOSED[closedCursor++ & 63] = trace;
            }
        }
    }
    public static void javaClose(Trace trace, CloseReason reason) { if (trace != null && reason != null) trace.javaClose(reason); }
    public static void nativeClose(Trace trace, int operation, int error) { if (trace != null) trace.nativeClose(operation, error); }
    public static final class Work {
        final WeakReference<Thread> thread;
        final String name;
        final long[] ids = new long[32], started = new long[32];
        final int[] opcodes = new int[32];
        final Trace[] traces = new Trace[32];
        final long[] tickets = new long[32];
        final PacketKind[] kinds = new PacketKind[32];
        final int[] commands = new int[32];
        final Timing[] epochs = new Timing[32];
        int depth, skippedDepth;
        Work(Thread thread) { this.thread = new WeakReference<>(thread); name = thread.getName(); }
    }
    public static Work begin(Trace trace, int opcode, int plaintextBytes) {
        if (!CIOConfig.DIAGNOSTICS_ENABLED) return null;
        int op = opcode & 255;
        counts[op].increment(); traffic.received.add(plaintextBytes);
        long ticket = trace == null ? -1 : trace.record(op);
        Work work = localWork();
        synchronized (work) {
            if (work.depth == work.ids.length) { ++work.skippedDepth; return work; }
            int slot = work.depth++;
            work.ids[slot] = trace == null ? 0 : trace.id; work.opcodes[slot] = op;
            work.traces[slot] = trace; work.tickets[slot] = ticket;
            work.kinds[slot] = null; work.commands[slot] = 0;
            work.epochs[slot] = timing; work.started[slot] = System.nanoTime();
        }
        return work;
    }
    /** Called at the actual decoder branch, never by guessing the header. */
    public static boolean subcommand(int opcode, PacketKind kind, int command) {
        if (!CIOConfig.DIAGNOSTICS_ENABLED || kind == null || command < 0 || command > 65535
                || kind == PacketKind.CRAFT && command > 255) return false;
        Work work = LOCAL.get();
        if (work == null) return false;
        synchronized (work) {
            if (work.depth == 0 || work.skippedDepth != 0) return false;
            int slot = work.depth - 1;
            if (work.opcodes[slot] != opcode || work.kinds[slot] != null) return false;
            work.kinds[slot] = kind; work.commands[slot] = command;
            Trace trace = work.traces[slot];
            if (trace != null) trace.command(work.tickets[slot], kind, command);
            return true;
        }
    }
    private static void appendCommand(StringBuilder b, PacketKind kind, int command) {
        if (kind != null) b.append(" protocol=").append(kind).append(" command=").append(command);
    }
    public static void end(Work work) {
        if (work == null) return;
        synchronized (work) {
            if (work.skippedDepth != 0) { --work.skippedDepth; return; }
            if (work.depth == 0) return;
            int slot = --work.depth, op = work.opcodes[slot]; Timing epoch = work.epochs[slot];
            work.epochs[slot] = null;
            work.traces[slot] = null; work.kinds[slot] = null;
            long elapsed = Math.max(0, System.nanoTime() - work.started[slot]);
            epoch.nanos[op].add(elapsed); epoch.completed[op].increment();
            long old = epoch.max.get(op);
            while (old < elapsed && !epoch.max.compareAndSet(op, old, elapsed)) old = epoch.max.get(op);
        }
    }
    public static void admitted(int wireBytes) { if (CIOConfig.DIAGNOSTICS_ENABLED) traffic.admitted.add(wireBytes); }
    public static void resetCounts() { counts = counters(); }
    public static void resetTiming() { timing = new Timing(); }
    public static void resetTraffic() { traffic = new Traffic(); }
    public static String packetCounts() {
        LongAdder[] snapshot = counts; StringBuilder b = new StringBuilder("diagnostics=" + CIOConfig.DIAGNOSTICS_ENABLED + "\n");
        for (int i = 0; i < 256; ++i) if (snapshot[i].sum() != 0) b.append("opcode=").append(i).append(" count=").append(snapshot[i].sum()).append('\n');
        return b.toString();
    }
    public static String timings() {
        Timing t = timing; StringBuilder b = new StringBuilder("Handler/filter elapsed time (completed packets)\n");
        for (int i = 0; i < 256; ++i) {
            long n = t.completed[i].sum(); if (n == 0) continue;
            b.append("opcode=").append(i).append(" completed=").append(n)
                    .append(" avgUs=").append(t.nanos[i].sum() / n / 1000).append(" maxUs=").append(t.max.get(i) / 1000).append('\n');
        }
        return b.toString();
    }
    public static String traffic() {
        Traffic t = traffic; double seconds = Math.max(0.001, (System.nanoTime() - t.started) / 1_000_000_000.0);
        return String.format(Locale.ROOT, "진단=%s\n복호화 후 수신 본문: %d bytes (%.1f KiB/s)\n송신 접수 wire: %d bytes (%.1f KiB/s)\n누적 구간: %.1f s\nOS/TCP 전송 완료량과 구분한 수치입니다.",
                CIOConfig.DIAGNOSTICS_ENABLED, t.received.sum(), t.received.sum() / seconds / 1024,
                t.admitted.sum(), t.admitted.sum() / seconds / 1024, seconds);
    }
    public static String connections() {
        List<Trace> traces; synchronized (CNetworkDiagnostics.class) { traces = new ArrayList<>(LIVE.values()); }
        StringBuilder b = new StringBuilder("진단 연결 수=" + traces.size() + " (최대 " + CIOConfig.DIAGNOSTICS_MAX_CONNECTIONS + ")\n");
        for (int i = 0; i < Math.min(traces.size(), 512); i++) b.append(traces.get(i).summary());
        b.append("최근 종료 연결(최대 64):\n");
        synchronized (CNetworkDiagnostics.class) { for (Trace t : CLOSED) if (t != null) b.append(t.summary()); }
        return b.toString();
    }
    public static String recent(long id) {
        Trace found; synchronized (CNetworkDiagnostics.class) {
            found = LIVE.get(id);
            if (found == null) for (Trace t : CLOSED) if (t != null && t.id == id) { found = t; break; }
        }
        return found == null ? "해당 진단 연결 기록이 없습니다." : found.recent();
    }
    public static String workers() {
        List<Work> workers; synchronized (CNetworkDiagnostics.class) { workers = new ArrayList<>(OWNERS); }
        StringBuilder b = new StringBuilder("실행 중 패킷 작업 (최대 256개 thread, 중첩 32단계)\n");
        for (Work w : workers) synchronized (w) {
            if (w.depth == 0) continue; int slot = w.depth - 1;
            b.append(w.name).append(" id=").append(w.ids[slot]).append(" opcode=").append(w.opcodes[slot]);
            appendCommand(b, w.kinds[slot], w.commands[slot]);
            b.append(" elapsedMs=").append(Math.max(0, System.nanoTime() - w.started[slot]) / 1_000_000);
            if (w.skippedDepth != 0) b.append(" untrackedNested=").append(w.skippedDepth);
            b.append('\n');
        }
        return b.toString();
    }
}
