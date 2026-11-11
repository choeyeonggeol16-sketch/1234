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

/** Native Windows TCP admission. IOCP workers are owned by CIOWinServer. */
public final class CIOServer implements AutoCloseable, CIOUserSocket.Closed {
    private static final Logger LOG = Logger.getLogger(CIOServer.class.getName());
    @FunctionalInterface public interface SocketFactory {
        CIOUserSocket CreateSocket(CIOWinSocket socket, CIOBufferPool pool,
                CIOUserSocket.Closed closedCallback) throws Exception;
    }
    private final CIOBufferPool pool;
    private final SocketFactory factory;
    private final CIOWinServer nativeServer;
    private final Set<CIOUserSocket> connections = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean acceptingStopped = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicInteger admitted = new AtomicInteger();
    private final int maxConnections;

    public CIOServer(InetSocketAddress address, int maxConnections, int ioThreads, int backlog,
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
        nativeServer = new CIOWinServer(address, maxConnections, ioThreads, backlog, this::activateAccepted);
    }

    private void activateAccepted(CIOWinSocket socket) {
        if (acceptingStopped.get() || closed.get() || !socket.isOpen()) {
            socket.close();
            return;
        }
        if (admitted.incrementAndGet() > maxConnections) {
            admitted.decrementAndGet();
            socket.close();
            return;
        }
        CIOUserSocket connection = null;
        try {
            connection = factory.CreateSocket(socket, pool, this);
            if (connection == null) {
                admitted.decrementAndGet();
                socket.close();
                return;
            }
            connections.add(connection);
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
    @Override public void onClosed(CIOUserSocket socket) {
        if (connections.remove(socket)) admitted.decrementAndGet();
    }
    public int liveConnections() { return admitted.get(); }
    public InetSocketAddress localAddress() throws IOException { return nativeServer.localAddress(); }
    public boolean isOpen() { return !acceptingStopped.get() && !closed.get(); }
    public boolean isAdmissionThread() { return CIOWinServer.isCompletionThread(); }
    public boolean isCompletionThread() { return CIOWinServer.isCompletionThread(); }

    public void stopAccepting() {
        if (CIOWinServer.isCompletionThread())
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
        if (CIOWinServer.isCompletionThread() || !acceptingStopped.get()) return false;
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

    @Override public synchronized void close() {
        close(30L, TimeUnit.SECONDS);
    }
    /** Bounded shutdown including native Stop and worker join, not just await(). */
    public synchronized void close(long timeout, TimeUnit unit) {
        if (unit == null || timeout < 0L) throw new IllegalArgumentException("invalid shutdown timeout");
        if (CIOWinServer.isCompletionThread())
            throw new IllegalStateException("cannot destroy server on IOCP completion thread");
        if (nativeServer.isTerminated()) return;
        if (closed.compareAndSet(false, true)) {
            try { stopAccepting(); }
            catch (RuntimeException failure) { LOG.log(Level.SEVERE, "CIO accept shutdown failed", failure); }
            for (CIOUserSocket connection : connections) {
                try { connection.close(); }
                catch (RuntimeException failure) { LOG.log(Level.WARNING, "CIO session close failed", failure); }
            }
        }
        // Do not swallow a timeout and proceed to CacheD/world teardown while
        // native workers or FFM downcalls can still access server objects.
        try { nativeServer.closeWithBudget(unit.toNanos(timeout)); }
        catch (IOException failure) {
            throw new IllegalStateException("CIO native transport shutdown incomplete", failure);
        }
    }
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        if (unit == null) throw new NullPointerException("unit");
        if (timeout < 0) throw new IllegalArgumentException("negative timeout");
        if (CIOWinServer.isCompletionThread()) return false;
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
