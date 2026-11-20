package l1j.server.server.model.network.core.connection;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import l1j.server.server.model.network.core.transport.selector.CSelectorTransport;

/** The ordinary RKern socket factory over Java TCP readiness and existing CIO owners. */
public final class CIOServer {
    private final Set<CUserSocket> sockets = new HashSet<>();
    // A listener restart must account for sessions still logging out from its predecessor.
    private static final Map<InetAddress, IpAdmissions> IP_ADMISSIONS = new HashMap<>();
    private static final class IpAdmissions {
        int live;
        int retained;
    }
    private final Map<Long, Integer> byMac = new HashMap<>();
    private final Map<Integer, CUserSocket> entering = new HashMap<>();
    private final int maxConnections;
    private final int ipLimit;
    private final int macLimit;
    private final CSelectorTransport transport;
    private long nextId;
    private long rejectedDuplicateEnter;
    private int authenticated;

    public CIOServer(InetSocketAddress address, int maxConnections, int owners, int backlog,
            int ipLimit, int macLimit, CSelectorTransport.OwnerHost host, BiConsumer<CIOServer, CSelectorTransport.Connection> admit) throws IOException {
        this.maxConnections = maxConnections;
        this.ipLimit = ipLimit;
        this.macLimit = macLimit;
        int capacityPerOwner = (int) ((maxConnections + (long) owners - 1) / owners);
        transport = CSelectorTransport.bindHosted(address,
                new CSelectorTransport.Config(owners, capacityPerOwner, backlog, Math.max(64, capacityPerOwner),
                        1, 1, 1, 3, 1, 0, Duration.ofSeconds(30)),
                new CSelectorTransport.RawHandler() {
                    @Override public void onOpen(CSelectorTransport.Connection peer) {
                        admit.accept(CIOServer.this, peer);
                    }
                    @Override public void onClosed(CSelectorTransport.Connection peer, CSelectorTransport.CloseReason reason) {
                        if (peer.rawEndpoint() instanceof CUserSocket socket && forget(socket))
                            socket.onTransportTerminated();
                    }
                }, host);
    }

    public synchronized CUserSocket register(CSelectorTransport.Connection peer, CIOBufferPool pool,
            CUserSocket.Closed closed, boolean reserveCleanup, int cleanupLimit) {
        InetAddress ip = peer.remoteAddress().getAddress();
        if (!(ip instanceof Inet4Address) || !peer.isOpen() || sockets.size() >= maxConnections) return null;
        IpAdmissions counts;
        synchronized (IP_ADMISSIONS) {
            counts = IP_ADMISSIONS.get(ip);
            if (ipLimit > 0 && counts != null && counts.live >= ipLimit) return null;
            if (reserveCleanup && (cleanupLimit <= 0 || counts != null && counts.retained >= cleanupLimit))
                return null;
            if (counts == null) {
                counts = new IpAdmissions();
                IP_ADMISSIONS.put(ip, counts);
            }
            counts.live++;
            if (reserveCleanup) counts.retained++;
        }
        CUserSocket socket = null;
        try {
            socket = new CUserSocket(this, peer, ++nextId, pool, 256, closed, true);
            socket.cleanupIpReserved = reserveCleanup;
            sockets.add(socket);
            peer.attachRawEndpoint(socket);
            return socket;
        } catch (RuntimeException | Error failure) {
            if (socket != null) sockets.remove(socket);
            synchronized (IP_ADMISSIONS) {
                counts.live--;
                if (reserveCleanup) counts.retained--;
                removeEmptyAddress(ip, counts);
            }
            throw failure;
        }
    }

    private synchronized boolean forget(CUserSocket socket) {
        if (!sockets.remove(socket)) return false;
        InetAddress address = socket.transportAddress().getAddress();
        synchronized (IP_ADMISSIONS) {
            IpAdmissions counts = IP_ADMISSIONS.get(address);
            counts.live--;
            removeEmptyAddress(address, counts);
        }
        if (socket.transportMac != 0) decrement(byMac, socket.transportMac);
        clearAccount(socket);
        return true;
    }

    void releaseCleanupIp(CUserSocket socket) {
        synchronized (IP_ADMISSIONS) {
            if (!socket.cleanupIpReserved) return;
            socket.cleanupIpReserved = false;
            InetAddress address = socket.transportAddress().getAddress();
            IpAdmissions counts = IP_ADMISSIONS.get(address);
            counts.retained--;
            removeEmptyAddress(address, counts);
        }
    }

    private static void removeEmptyAddress(InetAddress address, IpAdmissions counts) {
        if (counts.live == 0 && counts.retained == 0) IP_ADMISSIONS.remove(address);
    }

    synchronized boolean setAccount(CUserSocket socket, int account) {
        if (!socket.isOpen() || account < 0) return false;
        if (account == 0) {
            clearAccount(socket);
            return true;
        }
        if (macLimit > 0 && socket.transportMac == 0) return false;
        if (socket.transportAccount != 0) return socket.transportAccount == account;
        socket.transportAccount = account;
        authenticated++;
        return true;
    }

    synchronized boolean setMac(CUserSocket socket, long mac) {
        if (!socket.isOpen() || mac == 0 || socket.transportMac != 0) return false;
        int count = byMac.getOrDefault(mac, 0);
        if (macLimit > 0 && count >= macLimit) return false;
        byMac.put(mac, count + 1);
        socket.transportMac = mac;
        return true;
    }

    synchronized boolean setEntered(CUserSocket socket, boolean entered) {
        if (!socket.isOpen()) return false;
        if (entered == socket.transportEntered) return true;
        if (entered) {
            if (socket.transportAccount == 0) return false;
            CUserSocket existing = entering.putIfAbsent(socket.transportAccount, socket);
            if (existing != null && existing != socket) {
                rejectedDuplicateEnter++;
                return false;
            }
        } else entering.remove(socket.transportAccount, socket);
        socket.transportEntered = entered;
        return true;
    }

    synchronized boolean setState(CUserSocket socket, int state) {
        if (!socket.isOpen()) return false;
        switch (state) {
            case 0, 4 -> clearAccount(socket);
            case 1, 3 -> { if (!setEntered(socket, false)) return false; }
            case 2 -> { if (socket.transportAccount != 0 && !setEntered(socket, true)) return false; }
            default -> { return false; }
        }
        return true;
    }

    private void clearAccount(CUserSocket socket) {
        if (socket.transportEntered) entering.remove(socket.transportAccount, socket);
        socket.transportEntered = false;
        if (socket.transportAccount != 0) authenticated--;
        socket.transportAccount = 0;
    }

    private static <K> void decrement(Map<K, Integer> counts, K key) {
        int count = counts.getOrDefault(key, 0);
        if (count <= 1) counts.remove(key);
        else counts.put(key, count - 1);
    }

    public synchronized long[] accountRegistryDiagnostics() {
        return new long[] {sockets.size(), authenticated, entering.size(), rejectedDuplicateEnter};
    }

    public synchronized long findEnteringAccount(int account) {
        CUserSocket socket = entering.get(account);
        return socket == null || !socket.isOpen() ? 0 : socket.transportId();
    }

    public long[][] reportSockets() {
        CUserSocket[] snapshot;
        synchronized (this) { snapshot = sockets.toArray(CUserSocket[]::new); }
        long[][] socketsSnapshot = new long[snapshot.length][];
        for (int i = 0; i < snapshot.length; i++) socketsSnapshot[i] = snapshot[i].selectorReport();
        return socketsSnapshot;
    }

    public InetSocketAddress localAddress() { return transport.localAddress(); }
    public java.util.List<CSelectorTransport.OwnerDiagnostics> ownerDiagnostics() { return transport.ownerDiagnostics(); }
    public boolean isOwnerThread() { return transport.isOwnerThread(); }
    public void stopAccepting() throws IOException { transport.stopAccepting(); }
    public boolean isAcceptStopped() { return transport.isAcceptStopped(); }
    public boolean isTerminated() { return transport.isTerminated(); }
    public void requestStop() { transport.requestStop(); }
    public Throwable shutdownFailure() { return transport.shutdownFailure(); }
    public boolean awaitTermination(Duration duration) throws InterruptedException {
        return transport.awaitTermination(duration);
    }
}
