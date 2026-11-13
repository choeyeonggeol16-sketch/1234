package l1j.server.server.model.network.core;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.atomic.*;

/** Bounded metadata only: opcode/time/length, never credentials or packet bodies. */
public final class CNetworkDiagnostics {
    private CNetworkDiagnostics() {}
    private static final AtomicLong NEXT = new AtomicLong();
    private static final Map<Long, Trace> LIVE = new LinkedHashMap<>();
    private static final Trace[] CLOSED = new Trace[64];
    private static int closedCursor;
    private static final List<Work> OWNERS = new ArrayList<>();
    private static volatile AtomicLongArray counts = new AtomicLongArray(256);
    private static final class Timing {
        final AtomicLongArray completed = new AtomicLongArray(256), nanos = new AtomicLongArray(256), max = new AtomicLongArray(256);
    }
    private static final class Traffic {
        final long started = System.nanoTime();
        final AtomicLong received = new AtomicLong(), admitted = new AtomicLong();
    }
    private static volatile Timing timing = new Timing();
    private static volatile Traffic traffic = new Traffic();
    private static final ThreadLocal<Work> LOCAL = ThreadLocal.withInitial(() -> {
        Work work = new Work(Thread.currentThread());
        synchronized (CNetworkDiagnostics.class) {
            OWNERS.removeIf(w -> { Thread thread = w.thread.get(); return thread == null || !thread.isAlive(); });
            if (OWNERS.size() < 256) OWNERS.add(work);
        }
        return work;
    });
    static final class Trace {
        final long id = NEXT.incrementAndGet();
        final String remote;
        final long nativeHandle;
        private final int[] opcode = new int[128];
        private final long[] at = new long[128];
        private long sequence;
        volatile boolean closed;
        Trace(String remote, long nativeHandle) { this.remote = remote; this.nativeHandle = nativeHandle; }
        synchronized void record(int op) { int slot = (int)(sequence++ & 127); opcode[slot] = op; at[slot] = System.nanoTime(); }
        synchronized String recent() {
            StringBuilder b = new StringBuilder("id=" + id + " native=" + nativeHandle + " peer=" + remote + " closed=" + closed + "\n");
            long now = System.nanoTime();
            for (long i = sequence - 1, end = Math.max(0, sequence - 128); i >= end; --i) {
                int slot = (int)(i & 127);
                b.append("#").append(i + 1).append(" opcode=").append(opcode[slot])
                        .append(" ageMs=").append(Math.max(0, now - at[slot]) / 1_000_000).append('\n');
            }
            return b.toString();
        }
    }
    static Trace open(String remote) { return open(remote, 0); }
    static Trace open(String remote, long nativeHandle) {
        if (!CIOConfig.DIAGNOSTICS_ENABLED) return null;
        synchronized (CNetworkDiagnostics.class) {
            if (LIVE.size() >= CIOConfig.DIAGNOSTICS_MAX_CONNECTIONS) return null;
            Trace trace = new Trace(remote, nativeHandle); LIVE.put(trace.id, trace); return trace;
        }
    }
    static void close(Trace trace) {
        if (trace == null) return;
        synchronized (CNetworkDiagnostics.class) {
            if (LIVE.remove(trace.id, trace)) {
                trace.closed = true; CLOSED[closedCursor++ & 63] = trace;
            }
        }
    }
    static final class Work {
        final WeakReference<Thread> thread;
        final String name;
        final long[] ids = new long[32], started = new long[32];
        final int[] opcodes = new int[32];
        final Timing[] epochs = new Timing[32];
        int depth;
        Work(Thread thread) { this.thread = new WeakReference<>(thread); name = thread.getName(); }
    }
    static Work begin(Trace trace, int opcode, int plaintextBytes) {
        if (!CIOConfig.DIAGNOSTICS_ENABLED) return null;
        int op = opcode & 255;
        counts.incrementAndGet(op); traffic.received.addAndGet(plaintextBytes);
        if (trace != null) trace.record(op);
        Work work = LOCAL.get();
        synchronized (work) {
            if (work.depth == work.ids.length) return null;
            int slot = work.depth++;
            work.ids[slot] = trace == null ? 0 : trace.id; work.opcodes[slot] = op;
            work.epochs[slot] = timing; work.started[slot] = System.nanoTime();
        }
        return work;
    }
    static void end(Work work) {
        if (work == null) return;
        synchronized (work) {
            if (work.depth == 0) return;
            int slot = --work.depth, op = work.opcodes[slot]; Timing epoch = work.epochs[slot];
            work.epochs[slot] = null;
            long elapsed = Math.max(0, System.nanoTime() - work.started[slot]);
            epoch.nanos.addAndGet(op, elapsed); epoch.completed.incrementAndGet(op);
            long old = epoch.max.get(op);
            while (old < elapsed && !epoch.max.compareAndSet(op, old, elapsed)) old = epoch.max.get(op);
        }
    }
    static void admitted(int wireBytes) { if (CIOConfig.DIAGNOSTICS_ENABLED) traffic.admitted.addAndGet(wireBytes); }
    public static void resetCounts() { counts = new AtomicLongArray(256); }
    public static void resetTiming() { timing = new Timing(); }
    public static void resetTraffic() { traffic = new Traffic(); }
    public static String packetCounts() {
        AtomicLongArray snapshot = counts; StringBuilder b = new StringBuilder("diagnostics=" + CIOConfig.DIAGNOSTICS_ENABLED + "\n");
        for (int i = 0; i < 256; ++i) if (snapshot.get(i) != 0) b.append("opcode=").append(i).append(" count=").append(snapshot.get(i)).append('\n');
        return b.toString();
    }
    public static String timings() {
        Timing t = timing; StringBuilder b = new StringBuilder("Handler/filter elapsed time (completed packets)\n");
        for (int i = 0; i < 256; ++i) {
            long n = t.completed.get(i); if (n == 0) continue;
            b.append("opcode=").append(i).append(" completed=").append(n)
                    .append(" avgUs=").append(t.nanos.get(i) / n / 1000).append(" maxUs=").append(t.max.get(i) / 1000).append('\n');
        }
        return b.toString();
    }
    public static String traffic() {
        Traffic t = traffic; double seconds = Math.max(0.001, (System.nanoTime() - t.started) / 1_000_000_000.0);
        return String.format(Locale.ROOT, "진단=%s\n복호화 후 수신 본문: %d bytes (%.1f KiB/s)\n송신 접수 wire: %d bytes (%.1f KiB/s)\n누적 구간: %.1f s\nOS/TCP 전송 완료량과 구분한 수치입니다.",
                CIOConfig.DIAGNOSTICS_ENABLED, t.received.get(), t.received.get() / seconds / 1024,
                t.admitted.get(), t.admitted.get() / seconds / 1024, seconds);
    }
    public static String connections() {
        List<Trace> traces; synchronized (CNetworkDiagnostics.class) { traces = new ArrayList<>(LIVE.values()); }
        StringBuilder b = new StringBuilder("진단 연결 수=" + traces.size() + " (최대 " + CIOConfig.DIAGNOSTICS_MAX_CONNECTIONS + ")\n");
        for (int i = 0; i < Math.min(traces.size(), 512); i++) { Trace t = traces.get(i); b.append("id=").append(t.id).append(" native=").append(t.nativeHandle).append(" peer=").append(t.remote).append('\n'); }
        b.append("최근 종료 연결(최대 64):\n");
        synchronized (CNetworkDiagnostics.class) { for (Trace t : CLOSED) if (t != null) b.append("id=").append(t.id).append(" native=").append(t.nativeHandle).append(" peer=").append(t.remote).append('\n'); }
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
            b.append(w.name).append(" id=").append(w.ids[slot]).append(" opcode=").append(w.opcodes[slot])
                    .append(" elapsedMs=").append(Math.max(0, System.nanoTime() - w.started[slot]) / 1_000_000).append('\n');
        }
        return b.toString();
    }
}
