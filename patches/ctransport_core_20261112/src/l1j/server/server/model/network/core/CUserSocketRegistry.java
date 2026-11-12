package l1j.server.server.model.network.core;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Active lookup ends at close-start. Ownership ends after I/O AND L1 cleanup.
 * L1 adaptation of the original CUserSocket unregister/deferred-close boundary;
 * keys are connection objects, so an old close cannot remove a new connection.
 */
final class CUserSocketRegistry<T> {
    private static final int RETIRED = 1, IO_DRAINED = 2, CLEANED = 4;
    private final ConcurrentHashMap<T, AtomicInteger> entries = new ConcurrentHashMap<>();

    boolean register(T socket) { return entries.putIfAbsent(socket, new AtomicInteger()) == null; }
    boolean isActive(T socket) {
        AtomicInteger state = entries.get(socket);
        return state != null && state.get() == 0;
    }
    void retire(T socket) { mark(socket, RETIRED); }
    boolean transportDrained(T socket) { return mark(socket, RETIRED | IO_DRAINED); }
    void cleanupComplete(T socket) { mark(socket, CLEANED); }
    private boolean mark(T socket, int bits) {
        AtomicInteger state = entries.get(socket);
        if (state == null) return false;
        int before = state.getAndUpdate(value -> value | bits);
        if (((before | bits) & (IO_DRAINED | CLEANED)) == (IO_DRAINED | CLEANED))
            entries.remove(socket, state);
        return (bits & IO_DRAINED) != 0 && (before & IO_DRAINED) == 0;
    }
    Set<T> retained() { return entries.keySet(); }
    int pendingCleanup() {
        int count = 0;
        for (AtomicInteger state : entries.values())
            if ((state.get() & IO_DRAINED) != 0) count++;
        return count;
    }
}
