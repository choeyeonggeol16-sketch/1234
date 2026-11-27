import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import l1j.server.server.model.network.core.transport.selector.CSelectorTransport;
import l1j.server.server.model.network.core.transport.selector.CSelectorTransport.Connection;
import l1j.server.server.model.network.core.transport.selector.CSelectorTransport.OwnerDriver;

/** Exercises the hosted poll bound with real loopback connections and the Java Selector provider. */
public final class SelectorPollSkipRegression {
    private static final int MAX_SKIPS = 7;

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static Field field(Object target, String name) throws ReflectiveOperationException {
        final Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static int skips(OwnerDriver owner) throws ReflectiveOperationException {
        return field(owner, "consecutivePollSkips").getInt(owner);
    }

    private static boolean acceptReady(OwnerDriver owner) throws ReflectiveOperationException {
        return field(owner, "acceptReady").getBoolean(owner);
    }

    private static boolean backedUp(OwnerDriver owner) throws ReflectiveOperationException {
        return field(owner, "first").get(owner) != null;
    }

    private static final class Host implements AutoCloseable, CSelectorTransport.RawHandler {
        final List<Connection> endpoints = new ArrayList<>();
        final List<SocketChannel> peers = new ArrayList<>();
        final CSelectorTransport transport;
        OwnerDriver owner;

        Host() throws IOException {
            final var config = new CSelectorTransport.Config(1, 8, 16, 64,
                    2, 8, 2, 1024, 8, 0, Duration.ofSeconds(5));
            transport = CSelectorTransport.bindHosted(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                    config, this, owners -> owner = owners.getFirst());
        }

        SocketChannel connect() throws IOException {
            final SocketChannel peer = SocketChannel.open();
            peers.add(peer);
            peer.socket().connect(transport.localAddress(), 2000);
            peer.configureBlocking(false);
            return peer;
        }

        Connection endpoint(SocketChannel peer) throws IOException {
            final var address = peer.getLocalAddress();
            return endpoints.stream().filter(endpoint -> address.equals(endpoint.remoteAddress())).findFirst()
                    .orElseThrow(() -> new AssertionError("Peer was not accepted: " + address));
        }

        @Override public void onOpen(Connection connection) {
            check(connection.isOwnerThread(), "accept callback ran outside its hosted owner");
            endpoints.add(connection);
        }

        @Override public void close() throws IOException {
            try {
                owner.closeOnOwner();
                check(transport.isTerminated() && transport.shutdownFailure() == null, "clean transport termination");
                check(transport.shutdownStats().equals(List.of(new CSelectorTransport.OwnerStats(0, 0))),
                        "socket or command remained after transport shutdown");
            } finally {
                for (SocketChannel peer : peers) peer.close();
            }
        }
    }

    /** Re-arm short writes on their regular completion to retain a real ready list between turns. */
    private static final class WriteBacklog {
        final Connection endpoint;
        final ByteBuffer byteBuffer = ByteBuffer.wrap(new byte[] { 1 });
        int writes;

        WriteBacklog(Connection endpoint) {
            this.endpoint = endpoint;
        }

        void arm() {
            byteBuffer.rewind();
            check(endpoint.writeRaw(byteBuffer, this::complete), "busy write refused");
        }

        void complete(long bytes) {
            if (bytes < 0) return; // The final armed write is cancelled at test cleanup.
            check(endpoint.isOwnerThread() && bytes == 1, "wrong busy write completion");
            writes++;
            if (writes < 4096) arm();
        }
    }

    /** Observe readiness without consuming it or running the production selector. */
    private static void awaitKernelReadiness(Host host, Connection reader) throws Exception {
        try (Selector observation = Selector.open()) {
            ((ServerSocketChannel) field(host.transport, "listener").get(host.transport))
                    .register(observation, SelectionKey.OP_ACCEPT);
            ((SocketChannel) field(reader, "channel").get(reader)).register(observation, SelectionKey.OP_READ);
            final long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
            boolean accept = false, read = false;
            while ((!accept || !read) && System.nanoTime() < deadline) {
                observation.select(100);
                for (SelectionKey key : observation.selectedKeys()) {
                    accept |= key.isAcceptable();
                    read |= key.isReadable();
                }
                observation.selectedKeys().clear();
            }
            check(accept && read, "both new events must already be ready at the kernel");
        }
    }

    private static void testSharedBoundAndFairness() throws Exception {
        try (var host = new Host()) {
            final SocketChannel busyPeer = host.connect();
            final SocketChannel readPeer = host.connect();
            final OwnerDriver owner = host.owner;
            // First hosted call is await, before any advance has initialized the owner.
            owner.awaitWork(1000);
            check(host.endpoints.isEmpty() && acceptReady(owner), "initial await collects accept without running callbacks");
            owner.advance();
            final var busy = new WriteBacklog(host.endpoint(busyPeer));
            final Connection reader = host.endpoint(readPeer);
            owner.awaitWork(0); // Empty completed poll establishes a zero-skip starting point.
            busy.arm();

            final int[] progress = new int[3]; // deadline, readable byte, mailbox
            final ByteBuffer incoming = ByteBuffer.allocate(1);
            check(reader.readRaw(incoming, bytes -> {
                if (bytes < 0) return; // A stopped test cancels outstanding reads.
                check(reader.isOwnerThread() && bytes == 1 && incoming.get(0) == 37, "wrong readable payload");
                progress[1]++;
            }), "read refused");
            check(reader.scheduleDeadline(0, () -> progress[0]++), "deadline refused");
            check(readPeer.write(ByteBuffer.wrap(new byte[] { 37 })) == 1, "read stimulus write refused");
            final SocketChannel incomingPeer = host.connect();
            awaitKernelReadiness(host, reader);
            Thread.sleep(2); // The production deadline API has a minimum delay of 1 ms.
            final ByteBuffer drain = ByteBuffer.allocate(1024);

            for (int i = 1; i <= MAX_SKIPS; i++) {
                check(backedUp(owner), "ready backlog disappeared before phase " + i);
                final int before = busy.writes;
                if ((i & 1) == 0) {
                    owner.advance();
                    check(busy.writes > before && progress[0] == 1, "advance must service writes and deadline");
                    drain.clear();
                    busyPeer.read(drain);
                } else {
                    owner.awaitWork(0);
                    check(busy.writes == before, "await unexpectedly ran work");
                }
                // The old always-poll path fails this behavioral assertion before inspecting a new field.
                check(!acceptReady(owner) && host.endpoints.size() == 2 && progress[1] == 0,
                        "new readiness collected before the mandatory eighth poll");
                check(skips(owner) == i, "advance and await must share one consecutive skip bound");
            }

            owner.awaitWork(0);
            check(skips(owner) == 0 && acceptReady(owner) && progress[1] == 0 && host.endpoints.size() == 2,
                    "eighth opportunity must poll both events, without running callbacks");
            owner.advance();
            check(progress[1] == 1 && host.endpoint(incomingPeer).isOpen() && skips(owner) == 1 && backedUp(owner),
                    "new connection and readable peer serviced while old writes remain queued");

            check(reader.submit(connection -> {
                check(connection.isOwnerThread(), "mailbox callback escaped owner");
                progress[2]++;
            }, true), "deferred mailbox command refused");
            owner.advance();
            check(progress[2] == 1 && skips(owner) == 2 && backedUp(owner),
                    "a skipped advance must still service mailbox work while ready work remains");
        }
    }

    /** Observe actual JDK calls without replacing readiness, timeout behavior or keys.
     * Install only after all channels have registered on the delegate.
     */
    private static final class PollProbe extends Selector {
        final Selector delegate;
        int now, timed, blocking, lastResult;

        PollProbe(Selector delegate) { this.delegate = delegate; }
        @Override public boolean isOpen() { return delegate.isOpen(); }
        @Override public SelectorProvider provider() { return delegate.provider(); }
        @Override public Set<SelectionKey> keys() { return delegate.keys(); }
        @Override public Set<SelectionKey> selectedKeys() { return delegate.selectedKeys(); }
        @Override public int selectNow() throws IOException { return delegate.selectNow(); }
        @Override public int select(long timeout) throws IOException { return delegate.select(timeout); }
        @Override public int select() throws IOException { return delegate.select(); }
        @Override public Selector wakeup() { delegate.wakeup(); return this; }
        @Override public void close() throws IOException { delegate.close(); }
        @Override public int selectNow(Consumer<SelectionKey> action) throws IOException {
            lastResult = delegate.selectNow(action);
            now++;
            return lastResult;
        }
        @Override public int select(Consumer<SelectionKey> action, long timeout) throws IOException {
            lastResult = delegate.select(action, timeout);
            timed++;
            return lastResult;
        }
        @Override public int select(Consumer<SelectionKey> action) throws IOException {
            lastResult = delegate.select(action);
            blocking++;
            return lastResult;
        }
    }

    private static void consumeOneSkip(OwnerDriver owner, Connection peer) throws Exception {
        check(peer.writeRaw(ByteBuffer.wrap(new byte[] { 2 }), unused -> { }), "single write refused");
        owner.advance();
        check(skips(owner) == 1 && !backedUp(owner) && !acceptReady(owner), "single ready write should leave one skip");
    }

    private static void testPollPaths() throws Exception {
        try (var host = new Host()) {
            final SocketChannel client = host.connect();
            final OwnerDriver owner = host.owner;
            owner.awaitWork(1000);
            check(acceptReady(owner) && !backedUp(owner) && skips(owner) == 0, "accept is the only backlog");
            owner.awaitWork(0);
            check(skips(owner) == 1 && host.endpoints.isEmpty(), "accept alone permits an await skip");
            owner.advance();
            check(skips(owner) == 2 && !acceptReady(owner), "accept alone permits advance and still gets serviced");
            final Connection endpoint = host.endpoint(client);

            final Field selector = field(owner, "selector");
            final var probe = new PollProbe((Selector) selector.get(owner));
            selector.set(owner, probe);
            owner.awaitWork(0);
            check(probe.now == 1 && probe.lastResult == 0 && skips(owner) == 0, "empty selectNow must reset the counter");

            consumeOneSkip(owner, endpoint);
            owner.awaitWork(5);
            check(probe.now == 1 && probe.timed == 1 && skips(owner) == 0, "successful timed select must reset the counter");

            consumeOneSkip(owner, endpoint);
            // The standalone Owner.run uses this same private select(-1) path.
            final var select = owner.getClass().getDeclaredMethod("select", long.class);
            select.setAccessible(true);
            owner.wakeup(); // Bound a real blocking-mode poll even when there are no ready keys.
            select.invoke(owner, -1L);
            check(probe.blocking == 1 && probe.lastResult == 0 && skips(owner) == 0,
                    "successful blocking select must reset the counter");

            consumeOneSkip(owner, endpoint);
            final int[] actions = { 0 };
            check(endpoint.submit(connection -> actions[0]++, true), "mailbox-only command refused");
            owner.awaitWork(1000);
            check(probe.now == 2 && probe.timed == 1 && skips(owner) == 0 && actions[0] == 0,
                    "mailbox-only await must poll immediately and leave work to advance");
            owner.advance();
            check(probe.now == 3 && actions[0] == 1, "mailbox-only advance must poll and run its command");
        }
    }

    public static void main(String[] args) throws Exception {
        testSharedBoundAndFairness();
        testPollPaths();
        System.out.println("PASS Selector shared seven-skip bound, hosted fairness, and real poll resets");
    }
}
