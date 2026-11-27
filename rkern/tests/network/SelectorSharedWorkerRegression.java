import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import l1j.server.server.model.network.core.transport.selector.CSelectorTransport;
import l1j.server.server.model.network.core.transport.selector.CSelectorTransport.Connection;
import l1j.server.server.model.network.core.transport.selector.CSelectorTransport.OwnerDriver;

/**
 * Compile with production CSelectorTransport. Run with
 * {@code -Drkern.network.sharedSendWorkers=true} for shared work or pass
 * {@code disabled} with the default JVM configuration for refusal.
 */
public final class SelectorSharedWorkerRegression {
    private static final InetSocketAddress LOOPBACK = new InetSocketAddress("127.0.0.1", 0);
    private static final int TIMEOUT = 5;

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("disabled")) {
            require(!CSelectorTransport.SHARED_SEND_WORKERS, "shared work is disabled by default");
            exerciseDisabled();
            System.out.println("PASS two-owner default-off refusal");
            return;
        }
        require(args.length == 0 && CSelectorTransport.SHARED_SEND_WORKERS, "enable shared work for this test");
        exerciseSharedWorkers(false);
        exerciseSharedWorkers(true);
        exerciseSingleOwner();
        System.out.println("PASS standalone and hosted shared workers, close/stop and single-owner fallback");
    }

    private static void exerciseSharedWorkers(boolean hosted) throws Exception {
        var events = new OpenEvents(2);
        var drivers = new ArrayList<OwnerDriver>();
        var threads = new ArrayList<Thread>();
        var driverFailure = new AtomicReference<Throwable>();
        var transport = hosted ? CSelectorTransport.bindHosted(LOOPBACK, config(2), events, drivers::addAll)
                : CSelectorTransport.bind(LOOPBACK, config(2), events);
        try (transport) {
            if (hosted) {
                require(drivers.size() == 2, "hosted two Selector drivers");
                for (var driver : drivers) {
                    threads.add(Thread.ofPlatform().daemon()
                            .start(() -> drive(driver, driverFailure)));
                }
            }
            try (var first = connect(transport); var second = connect(transport)) {
                var connectionByOwner = new Connection[2];
                for (int i = 0; i < connectionByOwner.length; i++) {
                    var connection = events.next();
                    connectionByOwner[connection.ownerIndex()] = connection;
                }
                var origin = connectionByOwner[0];
                var peer = connectionByOwner[1];
                require(origin != null && peer != null, "live socket on each existing owner");
                Thread originThread = onOwner(origin, Thread::currentThread);
                Thread foreignThread = onOwner(peer, Thread::currentThread);
                require(originThread != foreignThread, "distinct Selector owner threads");

                var ownerWork = new CompletableFuture<Thread>();
                var ownerCount = new AtomicInteger();
                onOwner(origin, () -> {
                    admitEventually(origin, () -> {
                        ownerCount.incrementAndGet();
                        ownerWork.complete(Thread.currentThread());
                    });
                    return null;
                });
                require(ownerWork.get(TIMEOUT, TimeUnit.SECONDS) == foreignThread,
                        "submission from socket owner ran on the other existing owner");

                var runningOn = new CompletableFuture<Thread>();
                var runningDone = new CompletableFuture<Void>();
                var releaseRunning = new CountDownLatch(1);
                var runningCount = new AtomicInteger();
                var rejectedCount = new AtomicInteger();

                // Keep the accepted task active while closing its origin and stopping the transport.
                admitEventually(origin, () -> {
                    runningCount.incrementAndGet();
                    runningOn.complete(Thread.currentThread());
                    try {
                        if (!releaseRunning.await(TIMEOUT, TimeUnit.SECONDS))
                            throw new AssertionError("timed out awaiting release of the shared task");
                        runningDone.complete(null);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        runningDone.completeExceptionally(interrupted);
                    } catch (AssertionError failure) {
                        runningDone.completeExceptionally(failure);
                    }
                });

                try {
                    require(runningOn.get(TIMEOUT, TimeUnit.SECONDS) == foreignThread,
                            "external submission ran on the other existing owner");
                    require(!origin.tryShareWork(rejectedCount::incrementAndGet),
                            "busy peer rejects another transfer");
                    onOwner(origin, () -> {
                        origin.requestClose(false);
                        return null;
                    });
                    require(!origin.isOpen() && events.closed.get() == 1,
                            "origin closed while its accepted work was in flight");
                    require(!runningDone.isDone(), "first shared task still in flight at close");
                    require(!origin.tryShareWork(rejectedCount::incrementAndGet),
                            "closed connection refuses new shared work");
                    transport.requestStop();
                    require(!peer.tryShareWork(rejectedCount::incrementAndGet),
                            "stopping transport refuses new shared work");
                } finally {
                    releaseRunning.countDown();
                }

                runningDone.get(TIMEOUT, TimeUnit.SECONDS);
                require(transport.awaitTermination(Duration.ofSeconds(TIMEOUT)), "shared workers terminated");
                require(events.closed.get() == 2, "both live connections closed");
                require(ownerCount.get() == 1 && runningCount.get() == 1 && rejectedCount.get() == 0,
                        "each accepted transfer ran exactly once and rejected work never ran");
                require(transport.shutdownStats().stream()
                        .allMatch(owner -> owner.remainingConnections() == 0 && owner.remainingCommands() == 0),
                        "no connections or commands retained after stop");
            }
        } finally {
            for (var thread : threads) {
                thread.join(TimeUnit.SECONDS.toMillis(TIMEOUT));
                require(!thread.isAlive(), "hosted owner joined");
            }
        }
        if (driverFailure.get() != null) throw new AssertionError("hosted driver failed", driverFailure.get());
    }

    private static void exerciseDisabled() throws Exception {
        var events = new OpenEvents(2);
        var invocations = new AtomicInteger();
        try (var server = CSelectorTransport.bind(LOOPBACK, config(2), events);
             var first = connect(server); var second = connect(server)) {
            Connection origin = events.next();
            require(origin.ownerIndex() != events.next().ownerIndex(), "disabled test still has two live owners");
            require(!origin.tryShareWork(invocations::incrementAndGet), "default-off external refusal");
            onOwner(origin, () -> {
                require(!origin.tryShareWork(invocations::incrementAndGet), "default-off owner refusal");
                return null;
            });
            server.close();
            require(invocations.get() == 0, "default-off never invoked refused work");
        }
    }

    private static void exerciseSingleOwner() throws Exception {
        var events = new OpenEvents(1);
        var invocations = new AtomicInteger();
        try (var server = CSelectorTransport.bind(LOOPBACK, config(1), events);
             var socket = connect(server)) {
            Connection connection = events.next();
            require(!connection.tryShareWork(invocations::incrementAndGet), "single owner external fallback");
            onOwner(connection, () -> {
                require(!connection.tryShareWork(invocations::incrementAndGet), "single owner local fallback");
                invocations.incrementAndGet();
                return null;
            });
            server.close();
            require(invocations.get() == 1, "only the explicit single-owner fallback ran");
        }
    }

    private static void admitEventually(Connection connection, Runnable task) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT);
        do {
            if (connection.tryShareWork(task)) return;
            Thread.yield();
        } while (System.nanoTime() < deadline);
        throw new AssertionError("peer did not advertise readiness for shared work");
    }

    private static void drive(OwnerDriver driver, AtomicReference<Throwable> failures) {
        try {
            while (driver.active()) {
                driver.advance();
                if (driver.active()) driver.awaitWork(TIMEOUT * 1000L);
            }
        } catch (Throwable error) {
            failures.compareAndSet(null, error);
        } finally {
            try { driver.closeOnOwner(); }
            catch (Throwable error) { failures.compareAndSet(null, error); }
        }
    }

    private static CSelectorTransport.Config config(int owners) {
        return new CSelectorTransport.Config(owners, 4, 8, 32, 1, 1, 1,
                8192, 16, 0, Duration.ofSeconds(TIMEOUT));
    }

    private static Socket connect(CSelectorTransport transport) throws Exception {
        var socket = new Socket();
        try {
            socket.connect(transport.localAddress(), TIMEOUT * 1000);
            return socket;
        } catch (Exception failure) {
            socket.close();
            throw failure;
        }
    }

    private static <T> T onOwner(Connection connection, Callable<T> action) throws Exception {
        var reply = new CompletableFuture<T>();
        require(connection.tryExecute(ignored -> {
            try { reply.complete(action.call()); }
            catch (Exception | AssertionError failure) { reply.completeExceptionally(failure); }
        }), "fixture owner command admitted");
        return reply.get(TIMEOUT, TimeUnit.SECONDS);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class OpenEvents implements CSelectorTransport.RawHandler {
        private final ArrayBlockingQueue<Connection> opened;
        final AtomicInteger closed = new AtomicInteger();

        OpenEvents(int owners) { opened = new ArrayBlockingQueue<>(owners); }

        @Override public void onOpen(Connection connection) {
            require(opened.offer(connection), "fixture connection tracked");
        }

        @Override public void onClosed(Connection connection, CSelectorTransport.CloseReason reason) {
            closed.incrementAndGet();
        }

        Connection next() throws InterruptedException {
            var connection = opened.poll(TIMEOUT, TimeUnit.SECONDS);
            require(connection != null, "fixture connection accepted");
            return connection;
        }
    }
}
