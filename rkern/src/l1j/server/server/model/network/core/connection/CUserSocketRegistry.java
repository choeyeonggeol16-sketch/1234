package l1j.server.server.model.network.core.connection;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Active lookup ends at close-start. Ownership ends after I/O AND L1 cleanup.
 * L1 adaptation of the original CUserSocket unregister/deferred-close boundary;
 * keys are connection objects, so an old close cannot remove a new connection.
 */
public final class CUserSocketRegistry<T> {
    private static final int RETIRED = 1, IO_DRAINED = 2, CLEANED = 4;
    private final ConcurrentHashMap<T, AtomicInteger> entries = new ConcurrentHashMap<>();

    public boolean register(T socket) { return entries.putIfAbsent(socket, new AtomicInteger()) == null; }
    boolean isActive(T socket) {
        AtomicInteger state = entries.get(socket);
        return state != null && state.get() == 0;
    }
    public void retire(T socket) { mark(socket, RETIRED); }
    public boolean transportDrained(T socket) { return mark(socket, RETIRED | IO_DRAINED); }
    public void cleanupComplete(T socket) { mark(socket, CLEANED); }
    private boolean mark(T socket, int bits) {
        AtomicInteger state = entries.get(socket);
        if (state == null) return false;
        int before = state.getAndUpdate(value -> value | bits);
        if (((before | bits) & (IO_DRAINED | CLEANED)) == (IO_DRAINED | CLEANED))
            entries.remove(socket, state);
        return (bits & IO_DRAINED) != 0 && (before & IO_DRAINED) == 0;
    }
    public Set<T> retained() { return entries.keySet(); }
    public int pendingCleanup() {
        int count = 0;
        for (AtomicInteger state : entries.values())
            if ((state.get() & IO_DRAINED) != 0) count++;
        return count;
    }
}
