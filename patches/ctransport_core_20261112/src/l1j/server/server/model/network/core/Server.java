package l1j.server.server.model.network.core;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Native Windows TCP admission. IOCP workers are owned by CIOServer. */
public final class Server implements AutoCloseable, CUserSocket.Closed {
    private static final Logger LOG = Logger.getLogger(Server.class.getName());
    @FunctionalInterface public interface SocketFactory {
        CUserSocket CreateSocket(CSocket socket, CIOBufferPool pool,
                CUserSocket.Closed closedCallback) throws Exception;
    }
    private final CIOBufferPool pool;
    private final SocketFactory factory;
    private final CIOServer nativeServer;
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
            CIOBufferPool pool, SocketFactory factory) throws IOException {
        if (address == null || pool == null || factory == null || maxConnections < 1
                || ioThreads < 1 || backlog < 1) throw new IllegalArgumentException("invalid server configuration");
        // Do not pretend Windows native transport is a JDK asynchronous channel.
        // Linux benchmarking requires a separate, explicitly selected engine.
        if (!System.getProperty("os.name", "").startsWith("Windows"))
            throw new IOException("Native CIO transport requires Windows; no NIO.2 fallback");
        this.pool = pool;
        this.factory = factory;
        this.maxConnections = maxConnections;
        nativeServer = new CIOServer(address, maxConnections, ioThreads, backlog, this::activateAccepted, l1j.server.server.model.network.utill.ConnectionConfig.NETWORK_CLIENT_PERMISSION);
    }

    public long[][] ReportSocket(){return nativeServer.ReportSocket();}
    public long[] threadDiagnostics(){return nativeServer.threadDiagnostics();}
    public long[] listenerDiagnostics(){return nativeServer.listenerDiagnostics();}
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
    public InetSocketAddress localAddress() throws IOException { return nativeServer.localAddress(); }
    public boolean isOpen() { return !acceptingStopped.get() && !closed.get(); }
    public boolean isAdmissionThread() { return CIOServer.isCompletionThread(); }
    public boolean isCompletionThread() { return CIOServer.isCompletionThread(); }

    public void stopAccepting() {
        if (CIOServer.isCompletionThread())
            throw new IllegalStateException("cannot stop listener on IOCP worker");
        if (!acceptingStopped.compareAndSet(false, true)) return;
        try { nativeServer.stopAccepting(); }
        catch (IOException failure) {
            acceptingStopped.set(false);
            throw new IllegalStateException("CIO native stopAccepting failed", failure);
        }
    }

    public boolean awaitAcceptStopped(long timeout, TimeUnit unit) throws InterruptedException {
        if (timeout < 0) throw new IllegalArgumentException("negative timeout");
        if (CIOServer.isCompletionThread() || !acceptingStopped.get()) return false;
        if (nativeServer.isAcceptStopped()) return true;
        if (timeout == 0) return false;
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (!nativeServer.isAcceptStopped()) {
            final IOException failure = nativeServer.shutdownFailure();
            if (failure != null)
                throw new IllegalStateException("CIO native accept stop failed", failure);
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) return false;
            LockSupport.parkNanos(this, Math.min(remaining, 500_000L));
            if (Thread.interrupted()) throw new InterruptedException();
        }
        return true;
    }

    @Override public void close() {
        close(30L, TimeUnit.SECONDS);
    }

    /** The caller's budget includes coordinator admission, connection close,
     * and native shutdown. A timed-out attempt retains native memory. */
    public void close(long timeout, TimeUnit unit) {
        if (unit == null || timeout < 0L) throw new IllegalArgumentException("invalid shutdown timeout");
        if (CIOServer.isCompletionThread())
            throw new IllegalStateException("cannot destroy server on IOCP completion thread");
        final long started = System.nanoTime();
        final long budget = unit.toNanos(timeout);
        if (nativeServer.isTerminated()) return;
        if (budget <= 0L) throw new IllegalStateException("CIO shutdown deadline expired; native memory retained");
        final ShutdownAttempt attempt;
        synchronized (closed) {
            if (nativeServer.isTerminated()) return;
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
                throw new IllegalStateException("CIO shutdown timed out; coordinator/native memory retained");
            LockSupport.parkNanos(this, Math.min(remaining, 500_000L));
            if (Thread.interrupted()) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("CIO shutdown interrupted; coordinator/native memory retained");
            }
        }
        if (attempt.failure != null)
            throw new IllegalStateException("CIO shutdown incomplete; native memory retained", attempt.failure);
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
            nativeServer.closeWithBudget(requireShutdownTime(attempt));
        } catch (Throwable failure) {
            attempt.failure = failure;
        } finally {
            attempt.done = true;
        }
    }

    private static long requireShutdownTime(ShutdownAttempt attempt) throws IOException {
        final long remaining = attempt.remaining();
        if (remaining <= 0L)
            throw new IOException("CIO shutdown deadline expired before next phase; native memory retained");
        return remaining;
    }
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        if (unit == null) throw new NullPointerException("unit");
        if (timeout < 0) throw new IllegalArgumentException("negative timeout");
        if (CIOServer.isCompletionThread()) return false;
        final long budget = unit.toNanos(timeout);
        final long started = System.nanoTime();
        while (!nativeServer.isTerminated()) {
            if (System.nanoTime() - started >= budget) return false;
            LockSupport.parkNanos(this, Math.min(500_000L,
                    budget - (System.nanoTime() - started)));
            if (Thread.interrupted()) throw new InterruptedException();
        }
        return closed.get();
    }
}
