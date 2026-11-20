package l1j.server.server.model.network.core.transport;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import l1j.server.server.model.network.core.transport.selector.CSelectorTransport;

/** The ordinary RKern socket factory over Java TCP readiness and existing CIO owners. */
public final class CIOServer {
    private final Map<CSelectorTransport.Connection, CSocket> sockets = new HashMap<>();
    private final Map<InetAddress, Integer> byAddress = new HashMap<>();
    private final Map<Long, Integer> byMac = new HashMap<>();
    private final Map<Integer, CSocket> entering = new HashMap<>();
    private final int maxConnections;
    private final int ipLimit;
    private final int macLimit;
    private final CSelectorTransport transport;
    private long nextId;
    private long rejectedDuplicateEnter;
    private int authenticated;

    public CIOServer(InetSocketAddress address, int maxConnections, int owners, int backlog,
            int ipLimit, int macLimit, CSelectorTransport.OwnerHost host, Consumer<CSocket> admit) throws IOException {
        this.maxConnections = maxConnections;
        this.ipLimit = ipLimit;
        this.macLimit = macLimit;
        int capacityPerOwner = (int) ((maxConnections + (long) owners - 1) / owners);
        transport = CSelectorTransport.bindHosted(address,
                new CSelectorTransport.Config(owners, capacityPerOwner, backlog, Math.max(64, capacityPerOwner),
                        1, 1, 1, 3, 1, 0, Duration.ofSeconds(30)),
                new CSelectorTransport.RawHandler() {
                    @Override public void onOpen(CSelectorTransport.Connection peer) {
                        CSocket socket = register(peer);
                        if (socket == null) peer.abort();
                        else admit.accept(socket);
                    }
                    @Override public void onClosed(CSelectorTransport.Connection peer, CSelectorTransport.CloseReason reason) {
                        CSocket socket = forget(peer);
                        if (socket != null) socket.javaTerminated(reason);
                    }
                }, host);
    }

    private synchronized CSocket register(CSelectorTransport.Connection peer) {
        InetAddress ip = peer.remoteAddress().getAddress();
        if (!(ip instanceof Inet4Address) || sockets.size() >= maxConnections) return null;
        int current = byAddress.getOrDefault(ip, 0);
        if (ipLimit > 0 && current >= ipLimit) return null;
        CSocket socket = new CSocket(this, ++nextId, peer);
        sockets.put(peer, socket);
        byAddress.put(ip, current + 1);
        return socket;
    }

    private synchronized CSocket forget(CSelectorTransport.Connection peer) {
        CSocket socket = sockets.remove(peer);
        if (socket == null) return null;
        decrement(byAddress, peer.remoteAddress().getAddress());
        if (socket.javaMac != 0) decrement(byMac, socket.javaMac);
        clearAccount(socket);
        return socket;
    }

    synchronized boolean setAccount(CSocket socket, int account) {
        if (!socket.isOpen() || account < 0) return false;
        if (account == 0) {
            clearAccount(socket);
            return true;
        }
        if (macLimit > 0 && socket.javaMac == 0) return false;
        if (socket.javaAccount != 0) return socket.javaAccount == account;
        socket.javaAccount = account;
        authenticated++;
        return true;
    }

    synchronized boolean setMac(CSocket socket, long mac) {
        if (!socket.isOpen() || mac == 0 || socket.javaMac != 0) return false;
        int count = byMac.getOrDefault(mac, 0);
        if (macLimit > 0 && count >= macLimit) return false;
        byMac.put(mac, count + 1);
        socket.javaMac = mac;
        return true;
    }

    synchronized boolean setEntered(CSocket socket, boolean entered) {
        if (!socket.isOpen()) return false;
        if (entered == socket.javaEntered) return true;
        if (entered) {
            if (socket.javaAccount == 0) return false;
            CSocket existing = entering.putIfAbsent(socket.javaAccount, socket);
            if (existing != null && existing != socket) {
                rejectedDuplicateEnter++;
                return false;
            }
        } else entering.remove(socket.javaAccount, socket);
        socket.javaEntered = entered;
        return true;
    }

    synchronized boolean setState(CSocket socket, int state) {
        if (!socket.isOpen()) return false;
        switch (state) {
            case 0, 4 -> clearAccount(socket);
            case 1, 3 -> { if (!setEntered(socket, false)) return false; }
            case 2 -> { if (socket.javaAccount != 0 && !setEntered(socket, true)) return false; }
            default -> { return false; }
        }
        return true;
    }

    private void clearAccount(CSocket socket) {
        if (socket.javaEntered) entering.remove(socket.javaAccount, socket);
        socket.javaEntered = false;
        if (socket.javaAccount != 0) authenticated--;
        socket.javaAccount = 0;
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
        CSocket socket = entering.get(account);
        return socket == null || !socket.isOpen() ? 0 : socket.id();
    }

    public long[][] reportSockets() {
        CSocket[] snapshot;
        synchronized (this) { snapshot = sockets.values().toArray(CSocket[]::new); }
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
