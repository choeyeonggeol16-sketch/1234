package l1j.server.server.model.network.core.connection;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Retains a connection until both I/O drain and L1 cleanup are complete. */
public final class CUserSocketMap {
    private final Set<CUserSocket> retained = ConcurrentHashMap.newKeySet();

    public void register(CUserSocket socket) { retained.add(socket); }

    public boolean transportDrained(CUserSocket socket) {
        if (!retained.contains(socket) || !socket.markServerDrainReported()) return false;
        if (socket.isClientCleanupComplete()) retained.remove(socket);
        return true;
    }

    public void cleanupComplete(CUserSocket socket) {
        if (socket.isServerDrainReported()) retained.remove(socket);
    }

    public Set<CUserSocket> retained() { return retained; }

    public int pendingCleanup() {
        int count = 0;
        for (CUserSocket socket : retained)
            if (socket.isServerDrainReported()) count++;
        return count;
    }
}
