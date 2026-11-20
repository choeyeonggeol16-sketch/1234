package l1j.server.server.model.network.core.server;

import l1j.server.server.model.network.core.connection.CIOBufferPool;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CUserSocketRegistry;
import l1j.server.server.model.network.core.diagnostics.CPerfMon;
import l1j.server.server.model.network.core.diagnostics.CPoolDiagnostics;
import l1j.server.server.model.network.core.transport.CIOServer;
import l1j.server.server.model.network.core.transport.CSocket;
import l1j.server.server.model.network.core.transport.JavaCioServer;
import l1j.server.server.model.network.core.config.CIOConfig;
import l1j.server.server.model.network.core.transport.selector.SelectorTransport;
import l1j.server.server.model.network.utill.ConnectionConfig;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Gameplay listener lifecycle, sharing the same game factory across transports. */
public final class Server implements AutoCloseable, CUserSocket.Closed {
    private static final Logger LOG = Logger.getLogger(Server.class.getName());
    private final CIOBufferPool pool;
    private final IServerCallback factory;
    private final CIOServer nativeServer;
    private final JavaCioServer javaServer;
    private final CUserSocketRegistry<CUserSocket> connections = new CUserSocketRegistry<>();
    private final AtomicBoolean acceptingStopped = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicInteger admitted = new AtomicInteger();
    private final int maxConnections;
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


    public Server(InetSocketAddress address, int maxConnections, int ioThreads, int backlog,
            CIOBufferPool pool, IServerCallback factory) throws IOException {
        this(address, maxConnections, ioThreads, backlog, pool, factory, null);
    }

    public Server(InetSocketAddress address, int maxConnections, int backlog,
            CIOBufferPool pool, CDummyServerCallback factory) throws IOException {
        this(address, maxConnections, factory.selectorOwnerCount(), backlog, pool, factory, factory::attachSelectors);
    }

    private Server(InetSocketAddress address, int maxConnections, int ioThreads, int backlog,
            CIOBufferPool pool, IServerCallback factory, SelectorTransport.OwnerHost owners) throws IOException {
        if (address == null || pool == null || factory == null || maxConnections < 1
                || ioThreads < 1 || backlog < 1) throw new IllegalArgumentException("invalid server configuration");
        this.pool = pool;
        this.factory = factory;
        this.maxConnections = maxConnections;
        if (owners == null) {
            if (!System.getProperty("os.name", "").startsWith("Windows"))
                throw new IOException("Native CIO transport requires Windows");
            javaServer = null;
            nativeServer = new CIOServer(address, maxConnections, ioThreads, backlog,
                    this::activateAccepted, ConnectionConfig.NETWORK_CLIENT_PERMISSION);
        } else {
            nativeServer = null;
            javaServer = new JavaCioServer(address, maxConnections, ioThreads, backlog,
                    ConnectionConfig.NETWORK_CLIENT_PERMISSION, CIOConfig.MAC_LIMIT, owners, this::activateAccepted);
        }
    }

    public CPerfMon.Snapshot performance() { return nativeServer == null ? null : CPerfMon.Read(nativeServer); }
    public boolean isJavaTransport() { return javaServer != null; }
    public long[] sendDiagnostics() {
        if (nativeServer != null) return nativeServer.sendDiagnostics();
        return new long[] {CUserSocket.softSendRejections(), pool.smallSendExhaustions(), pool.largeSendExhaustions()};
    }
    public long[] accountRegistryDiagnostics() {
        return javaServer != null ? javaServer.accountRegistryDiagnostics() : nativeServer.accountRegistryDiagnostics();
    }
    public long findEnteringAccount(int accountId) {
        return javaServer != null ? javaServer.findEnteringAccount(accountId) : nativeServer.findEnteringAccount(accountId);
    }
    public long[][] ReportSocket() { return javaServer != null ? javaServer.reportSockets() : nativeServer.ReportSocket(); }
    public long[] threadDiagnostics() { return nativeServer == null ? new long[0] : nativeServer.threadDiagnostics(); }
    public long[] listenerDiagnostics() {
        return nativeServer == null ? new long[] {isOpen() ? 1 : 0, liveConnections()} : nativeServer.listenerDiagnostics();
    }
    private final CPoolDiagnostics poolDiagnostics = new CPoolDiagnostics();
    public String poolReport(){
        if (javaServer != null) {
            return "JProRing direct bytes=" + pool.allocatedDirectBytes() + "/" + pool.maxDirectBytes()
                    + " receive slots=" + pool.usedRecvSlots() + " send slots=" + pool.usedSendSlots()
                    + " accounts=" + Arrays.toString(javaServer.accountRegistryDiagnostics())
                    + " owners=" + javaServer.ownerDiagnostics();
        }
        long[] registry=nativeServer.registryDiagnostics(),send=nativeServer.sendDiagnostics(),references=nativeServer.referenceDiagnostics();
        return poolDiagnostics.sample(System.nanoTime(),pool.snapshot(),nativeServer.poolDiagnostics(),new long[]{
                connections.retained().size(),connections.pendingCleanup(),registry[0],registry[1],nativeServer.liveConstBuffers(),send[7],CPoolDiagnostics.referenceActive(references)})+CPoolDiagnostics.referenceReport(references)+CPoolDiagnostics.objectAllocatorReport(nativeServer.objectAllocatorDiagnostics());
    }
    private void activateAccepted(CSocket socket) {
        if (acceptingStopped.get() || closed.get() || !socket.isOpen()) {
            socket.close();
            return;
        }
        if (admitted.incrementAndGet() > maxConnections) {
            admitted.decrementAndGet();
            socket.close();
            return;
        }
        CUserSocket connection = null;
        try {
            connection = factory.CreateSocket(socket, pool, this);
            if (connection == null) {
                admitted.decrementAndGet();
                socket.close();
                return;
            }
            connections.register(connection);
            if (connection.isCloseStarted()) connections.retire(connection);
            if (connection.isClientCleanupComplete()) connections.cleanupComplete(connection);
            if (connection.isCloseComplete()) {
                onClosed(connection);
                return;
            }
            if (acceptingStopped.get() || closed.get() || !connection.isOpen()) {
                connection.close();
                if (connection.isCloseComplete()) onClosed(connection);
                return;
            }
            connection.startRead();
        } catch (Throwable failure) {
            LOG.log(Level.WARNING, "CIO connection admission failed", failure);
            if (connection == null) {
                admitted.decrementAndGet();
                socket.close();
            } else {
                connection.close();
                if (connection.isCloseComplete()) onClosed(connection);
            }
        }
    }
    @Override public void onClosed(CUserSocket socket) {
        if (connections.transportDrained(socket)) admitted.decrementAndGet();
    }
    @Override public void onClosing(CUserSocket socket) { connections.retire(socket); }
    @Override public void onCleanupComplete(CUserSocket socket) { connections.cleanupComplete(socket); }
    public int pendingSessionCleanup() { return connections.pendingCleanup(); }
    public int liveConnections() { return admitted.get(); }
    public InetSocketAddress localAddress() throws IOException { return javaServer != null ? javaServer.localAddress() : nativeServer.localAddress(); }
    public boolean isOpen() { return !acceptingStopped.get() && !closed.get(); }
    public boolean isAdmissionThread() { return isCompletionThread(); }
    public boolean isCompletionThread() { return javaServer != null ? javaServer.isOwnerThread() : CIOServer.isCompletionThread(); }

    public void stopAccepting() {
        if (isCompletionThread())
            throw new IllegalStateException("cannot stop listener on its owner");
        if (!acceptingStopped.compareAndSet(false, true)) return;
        try {
            if (javaServer != null) javaServer.stopAccepting();
            else nativeServer.stopAccepting();
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
            final Throwable failure = javaServer != null ? javaServer.shutdownFailure() : nativeServer.shutdownFailure();
            if (failure != null)
                throw new IllegalStateException("CIO accept stop failed", failure);
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) return false;
            LockSupport.parkNanos(this, Math.min(remaining, 500_000L));
            if (Thread.interrupted()) throw new InterruptedException();
        }
        return true;
    }

    private boolean isAcceptStopped() {
        return javaServer != null ? javaServer.isAcceptStopped() : nativeServer.isAcceptStopped();
    }

    private boolean isTransportTerminated() {
        return javaServer != null ? javaServer.isTerminated() : nativeServer.isTerminated();
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
        if (isTransportTerminated()) return;
        if (budget <= 0L) throw new IllegalStateException("CIO shutdown deadline expired; I/O resources retained");
        final ShutdownAttempt attempt;
        synchronized (closed) {
            if (isTransportTerminated()) return;
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
            for (CUserSocket connection : connections.retained()) {
                requireShutdownTime(attempt);
                // This may block in a native socket/codec lock. Only the single
                // coordinator waits; the close caller still observes its budget.
                connection.close();
            }
            if (javaServer == null) nativeServer.closeWithBudget(requireShutdownTime(attempt));
            else {
                javaServer.requestStop();
                if (!javaServer.awaitTermination(Duration.ofNanos(requireShutdownTime(attempt))))
                    throw new IOException("JProRing owner shutdown timed out");
                Throwable transportFailure = javaServer.shutdownFailure();
                if (transportFailure != null) throw new IOException("JProRing owner failure", transportFailure);
            }
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
        return closed.get();
    }
}
