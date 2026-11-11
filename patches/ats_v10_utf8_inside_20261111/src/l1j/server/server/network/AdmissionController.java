package l1j.server.server.network;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Session slots are retained until game cleanup AND transport budgets have retired. */
final class AdmissionController {
    private final NetworkConfig config;
    private final AtomicInteger active = new AtomicInteger();
    private final ConcurrentHashMap<String, Integer> ips = new ConcurrentHashMap<>();
    AdmissionController(NetworkConfig config) { this.config = config; }
    Permit acquire(String ip) {
        for (;;) { int n = active.get(); if (n >= config.maxSessions) return null;
            if (active.compareAndSet(n, n + 1)) break; }
        AtomicBoolean accepted = new AtomicBoolean();
        ips.compute(ip, (k, n) -> { int count = n == null ? 0 : n;
            if (count >= config.maxPerIp) return n;
            accepted.set(true); return count + 1; });
        if (!accepted.get()) { active.decrementAndGet(); return null; }
        return new Permit(ip);
    }
    int active() { return active.get(); }
    int ipEntries() { return ips.size(); }
    final class Permit {
        private final String ip;
        private final AtomicBoolean released = new AtomicBoolean();
        Permit(String ip) { this.ip = ip; }
        void release() {
            if (!released.compareAndSet(false, true)) return;
            ips.compute(ip, (k, n) -> { if (n == null || n < 1) throw new IllegalStateException("IP double release");
                return n == 1 ? null : n - 1; });
            active.decrementAndGet();
        }
    }
}
