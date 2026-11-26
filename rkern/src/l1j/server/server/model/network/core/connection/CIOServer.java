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
import l1j.server.server.model.network.core.transport.selector.CSelectorTransport;
import l1j.server.server.model.network.core.config.CIOConfig;
import l1j.server.Config;
import l1j.server.server.auth.login.LoginController;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.ConcurrentHashMap;

/** The ordinary RKern socket factory over Java TCP readiness and existing CIO owners. */
public final class CIOServer implements AutoCloseable, CUserSocket.Closed {
    private static final Logger LOG = Logger.getLogger(CIOServer.class.getName());
    private final CIOBufferPool pool;
    private final int maxOnline;
    private final boolean preLoginTimeout;
    // Transport-live sockets and sessions retained until both I/O and logout finish are distinct.
    private final Set<CUserSocket> retained = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean acceptingStopped = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicInteger admitted = new AtomicInteger();
    private volatile ShutdownAttempt shutdownAttempt;

    /** One coordinator per attempt, never an IO/packet worker. A blocked
     * connection close cannot hold the caller past its deadline. */
    private static final class ShutdownAttempt {
        final long startedNs, budgetNs;
        volatile boolean done;
        volatile Throwable failure;
        ShutdownAttempt(long startedNs, long budgetNs) {
            this.startedNs = startedNs;
            this.budgetNs = budgetNs;
        }
        long remaining() { return budgetNs - (System.nanoTime() - startedNs); }
    }


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

    public CIOServer(InetSocketAddress address, int maxConnections, int ioThreads, int backlog,
            CIOBufferPool pool, CIOThreadCallback scheduler, CIOConfig config) throws IOException {
        if (address == null || pool == null || scheduler == null || config == null || maxConnections < 1
                || ioThreads < 1 || backlog < 1) throw new IllegalArgumentException("invalid server configuration");
        this.pool = pool;
        this.maxOnline = Config.MAX_ONLINE_USERS;
        this.maxConnections = maxConnections;
        this.preLoginTimeout = config.usePreLoginTimeout;
        if (maxOnline < 1) throw new IllegalStateException("invalid online limit");
        LoginController.getInstance().setMaxAllowedOnlinePlayers(maxOnline);
        this.ipLimit = CIOConfig.NETWORK_CLIENT_PERMISSION;
        this.macLimit = CIOConfig.macLimit();
        int owners = ioThreads;
        int capacityPerOwner = (int) ((maxConnections + (long) owners - 1) / owners);
        transport = CSelectorTransport.bindHosted(address,
                new CSelectorTransport.Config(owners, capacityPerOwner, backlog, Math.max(64, capacityPerOwner),
                        1, 1, 1, 3, 1, 0, Duration.ofSeconds(30)),
                new CSelectorTransport.RawHandler() {
                    @Override public void onOpen(CSelectorTransport.Connection peer) {
                        activateAccepted(CIOServer.this, peer);
                    }
                    @Override public void onClosed(CSelectorTransport.Connection peer, CSelectorTransport.CloseReason reason) {
                        if (peer.rawEndpoint() instanceof CUserSocket socket && forget(socket))
                            socket.onTransportTerminated();
                    }
                }, scheduler::attachSelectors);
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

    public InetSocketAddress localAddress() throws IOException { return transport.localAddress(); }
    public java.util.List<CSelectorTransport.OwnerDiagnostics> ownerDiagnostics() { return transport.ownerDiagnostics(); }
    public boolean isOwnerThread() { return transport.isOwnerThread(); }
    public boolean isAcceptStopped() { return transport.isAcceptStopped(); }
    public boolean isTerminated() { return transport.isTerminated(); }
    public void requestStop() { transport.requestStop(); }
    public Throwable shutdownFailure() { return transport.shutdownFailure(); }
    public boolean awaitTermination(Duration duration) throws InterruptedException {
        return transport.awaitTermination(duration);
    }

    public boolean isJavaTransport() { return true; }
    public long[][] ReportSocket() { return reportSockets(); }
    public long[] listenerDiagnostics() {
        return new long[] {isOpen() ? 1 : 0, liveConnections()};
    }
    public long[][] poolSnapshot() { return pool.snapshot(); }
    public String poolReport(){
        return "JProRing direct bytes=" + pool.allocatedDirectBytes() + "/" + pool.maxDirectBytes()
                + " receive slots=" + pool.usedRecvSlots() + " send slots=" + pool.usedSendSlots()
                + " shared source slots=" + pool.usedSharedSourceSlots()
                + " shared source refused=" + pool.sharedSourceRefusals()
                + " optional broadcast refused=" + pool.optionalBroadcastRefusals()
                + " accounts=" + Arrays.toString(accountRegistryDiagnostics())
                + " owners=" + ownerDiagnostics();
    }
    private void activateAccepted(CIOServer registry, CSelectorTransport.Connection peer) {
        if (acceptingStopped.get() || closed.get() || !peer.isOpen()
                || peer.remoteAddress().getPort() <= 1024
                || maxOnline <= LoginController.getInstance().getOnlinePlayerCount()) {
            peer.abort();
            return;
        }
        if (admitted.incrementAndGet() > maxConnections) {
            admitted.decrementAndGet();
            peer.abort();
            return;
        }
        CUserSocket socket = null;
        try {
            if (CIOThreadCallback.currentShardIndex() != peer.ownerIndex())
                throw new IllegalStateException("game admission must execute on its CIO owner");
            socket = registry.register(peer, pool, this, !Config.shutdownCheck,
                    CIOConfig.NETWORK_CLIENT_PERMISSION);
            if (socket == null) {
                admitted.decrementAndGet();
                peer.abort();
                return;
            }
            // Handshake may synchronously close the transport. Publish the retained socket first.
            retained.add(socket);
            if (!socket.onConnected() || !socket.isConnected())
                throw new IllegalStateException("handshake not admitted");
            socket.configureTransportDeadlines(preLoginTimeout);
            if (acceptingStopped.get() || closed.get() || !socket.isOpen()) {
                socket.close();
                return;
            }
            socket.startRead();
        } catch (Throwable failure) {
            LOG.log(Level.WARNING, "CIO connection admission failed", failure);
            if (socket == null) {
                admitted.decrementAndGet();
                peer.abort();
            } else socket.close();
        }
    }
    @Override public void onClosed(CUserSocket socket) {
        if (!retained.contains(socket) || !socket.markServerDrainReported()) return;
        if (socket.isClientCleanupComplete()) retained.remove(socket);
        admitted.decrementAndGet();
    }
    @Override public void onCleanupComplete(CUserSocket socket) {
        if (socket.isServerDrainReported()) retained.remove(socket);
    }
    public int pendingSessionCleanup() {
        int count = 0;
        for (CUserSocket socket : retained)
            if (socket.isServerDrainReported()) count++;
        return count;
    }
    public int liveConnections() { return admitted.get(); }
    public boolean isOpen() { return !acceptingStopped.get() && !closed.get(); }
    public boolean isAdmissionThread() { return isCompletionThread(); }
    public boolean isCompletionThread() { return isOwnerThread(); }

    public void stopAccepting() {
        if (isCompletionThread())
            throw new IllegalStateException("cannot stop listener on its owner");
        if (!acceptingStopped.compareAndSet(false, true)) return;
        try {
            transport.stopAccepting();
        }
        catch (IOException failure) {
            acceptingStopped.set(false);
            throw new IllegalStateException("CIO stopAccepting failed", failure);
        }
    }

    public boolean awaitAcceptStopped(long timeout, TimeUnit unit) throws InterruptedException {
        if (timeout < 0) throw new IllegalArgumentException("negative timeout");
        if (isCompletionThread() || !acceptingStopped.get()) return false;
        if (isAcceptStopped()) return true;
        if (timeout == 0) return false;
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (!isAcceptStopped()) {
            final Throwable failure = shutdownFailure();
            if (failure != null)
                throw new IllegalStateException("CIO accept stop failed", failure);
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) return false;
            LockSupport.parkNanos(this, Math.min(remaining, 500_000L));
            if (Thread.interrupted()) throw new InterruptedException();
        }
        return true;
    }

    public boolean isTransportTerminated() {
        return isTerminated();
    }

    private void checkTransportFailure() {
        final Throwable failure = shutdownFailure();
        if (failure != null) throw new IllegalStateException("CIO transport owner failed", failure);
    }

    @Override public void close() {
        close(30L, TimeUnit.SECONDS);
    }

    /** The caller's budget includes coordinator admission, connection close,
     * and native shutdown. A timed-out attempt retains native memory. */
    public void close(long timeout, TimeUnit unit) {
        if (unit == null || timeout < 0L) throw new IllegalArgumentException("invalid shutdown timeout");
        if (isCompletionThread())
            throw new IllegalStateException("cannot destroy server on a transport owner");
        final long started = System.nanoTime();
        final long budget = unit.toNanos(timeout);
        if (isTransportTerminated()) {
            checkTransportFailure();
            return;
        }
        if (budget <= 0L) throw new IllegalStateException("CIO shutdown deadline expired; I/O resources retained");
        final ShutdownAttempt attempt;
        synchronized (closed) {
            if (isTransportTerminated()) {
                checkTransportFailure();
                return;
            }
            ShutdownAttempt current = shutdownAttempt;
            if (current == null || current.done) {
                current = new ShutdownAttempt(started, budget);
                shutdownAttempt = current;
                closed.set(true); // deny new admissions before starting the coordinator
                final ShutdownAttempt task = current;
                final Thread worker = new Thread(() -> finishShutdown(task), "CIO-close-coordinator");
                worker.setDaemon(true);
                try { worker.start(); }
                catch (RuntimeException | Error failure) {
                    current.failure = failure;
                    current.done = true;
                    throw failure;
                }
            }
            attempt = current;
        }
        // No monitor is held while waiting or executing connection/native close.
        // A concurrent caller has its own deadline and never extends this attempt.
        while (!attempt.done) {
            final long remaining = budget - (System.nanoTime() - started);
            if (remaining <= 0L)
                throw new IllegalStateException("CIO shutdown timed out; coordinator and I/O resources retained");
            LockSupport.parkNanos(this, Math.min(remaining, 500_000L));
            if (Thread.interrupted()) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("CIO shutdown interrupted; coordinator and I/O resources retained");
            }
        }
        if (attempt.failure != null)
            throw new IllegalStateException("CIO shutdown incomplete; I/O resources retained", attempt.failure);
    }

    private void finishShutdown(ShutdownAttempt attempt) {
        try {
            requireShutdownTime(attempt);
            stopAccepting();
            for (CUserSocket connection : retained) {
                requireShutdownTime(attempt);
                // This may block in a native socket/codec lock. Only the single
                // coordinator waits; the close caller still observes its budget.
                connection.close();
            }
            requestStop();
            if (!awaitTermination(Duration.ofNanos(requireShutdownTime(attempt))))
                throw new IOException("JProRing owner shutdown timed out");
            Throwable transportFailure = shutdownFailure();
            if (transportFailure != null) throw new IOException("JProRing owner failure", transportFailure);
        } catch (Throwable failure) {
            attempt.failure = failure;
        } finally {
            attempt.done = true;
        }
    }

    private static long requireShutdownTime(ShutdownAttempt attempt) throws IOException {
        final long remaining = attempt.remaining();
        if (remaining <= 0L)
            throw new IOException("CIO shutdown deadline expired before next phase; I/O resources retained");
        return remaining;
    }
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        if (unit == null) throw new NullPointerException("unit");
        if (timeout < 0) throw new IllegalArgumentException("negative timeout");
        if (isCompletionThread()) return false;
        final long budget = unit.toNanos(timeout);
        final long started = System.nanoTime();
        while (!isTransportTerminated()) {
            if (System.nanoTime() - started >= budget) return false;
            LockSupport.parkNanos(this, Math.min(500_000L,
                    budget - (System.nanoTime() - started)));
            if (Thread.interrupted()) throw new InterruptedException();
        }
        return true;
    }
}
