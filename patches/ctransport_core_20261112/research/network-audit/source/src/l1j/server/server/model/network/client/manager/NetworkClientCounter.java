package l1j.server.server.model.network.client.manager;

import java.net.InetAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import l1j.server.Config;
import l1j.server.server.model.network.utill.ConnectionConfig;
import l1j.server.server.model.network.utill.DataParser;

public class NetworkClientCounter {

    private static volatile NetworkClientCounter _instance;

    public static NetworkClientCounter getInstance() {
        NetworkClientCounter instance = _instance;
        if (instance == null) {
            synchronized (NetworkClientCounter.class) {
                instance = _instance;
                if (instance == null) _instance = instance = new NetworkClientCounter();
            }
        }
        return instance;
    }

    public void release() {
        synchronized (NetworkClientCounter.class) {
            if (_instance == this) {
                clear();
                _instance = null;
            }
        }
    }

    private final ConcurrentHashMap<Integer, AtomicInteger> _counters;

    private NetworkClientCounter() {
        _counters = new ConcurrentHashMap<>(Config.MAX_ONLINE_USERS);
    }

    /**
     * Reserve one connection for this IPv4 address.
     * -1 in a counter is a tombstone: the last release claimed the empty
     * counter for removal. A new admission must use a new map entry.
     */
    public int isPermission(InetAddress addr) {
        if (addr == null) return -1;
        final int limit = ConnectionConfig.NETWORK_CLIENT_PERMISSION;
        if (limit <= 0) return -1;
        final int key = DataParser.parseInt(addr);
        for (;;) {
            AtomicInteger counter = _counters.get(key);
            if (counter == null) {
                final AtomicInteger created = new AtomicInteger();
                final AtomicInteger old = _counters.putIfAbsent(key, created);
                counter = old == null ? created : old;
            }
            final int current = counter.get();
            if (current < 0) {
                // Help complete concurrent last-release removal.
                _counters.remove(key, counter);
                continue;
            }
            if (current >= limit) return -1;
            if (counter.compareAndSet(current, current + 1)) return current + 1;
        }
    }

    /** Release exactly one previously accepted IP reservation. */
    public void decrese(InetAddress addr) {
        if (addr == null) return;
        final int key = DataParser.parseInt(addr);
        final AtomicInteger counter = _counters.get(key);
        if (counter == null) return;
        for (;;) {
            final int current = counter.get();
            if (current <= 0) return;
            if (current == 1) {
                // Claim removal before unlinking. Otherwise a new admission
                // could increment zero to one and then lose the entire entry.
                if (!counter.compareAndSet(1, -1)) continue;
                _counters.remove(key, counter);
                return;
            }
            if (counter.compareAndSet(current, current - 1)) return;
        }
    }

    public void decrease(InetAddress addr) {
        decrese(addr);
    }

    public void clear() {
        _counters.clear();
    }
}
