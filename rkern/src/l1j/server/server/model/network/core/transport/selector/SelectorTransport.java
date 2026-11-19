package l1j.server.server.model.network.core.transport.selector;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import l1j.server.server.model.network.buffer.ReadBuffer;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.codec.Cryptor;

/** Bounded, standard-Java TCP transport. Each connection has one Selector owner for its entire lifetime. */
public final class SelectorTransport implements AutoCloseable {
    public record Config(int owners, int connectionsPerOwner, int acceptBacklog, int mailboxCapacity,
            int largeReceivesPerOwner, int smallSendsPerOwner, int largeSendsPerOwner,
            int sendBytesPerConnection, int sendFramesPerConnection, int socketSendBytes,
            Duration writeStallTimeout) {
        public Config {
            if (owners < 1 || connectionsPerOwner < 1 || acceptBacklog < 1 || mailboxCapacity < 1
                    || largeReceivesPerOwner < 1 || smallSendsPerOwner < 1 || largeSendsPerOwner < 1
                    || sendBytesPerConnection < 3 || sendFramesPerConnection < 1 || socketSendBytes < 0
                    || writeStallTimeout.toNanos() <= 0) {
                throw new IllegalArgumentException("Invalid transport configuration");
            }
        }
    }

    public enum CloseReason { APPLICATION, REMOTE_EOF, WRITE_TIMEOUT, IO_ERROR, HANDLER_ERROR, SHUTDOWN }

    public interface Handler {
        default void onOpen(Connection connection) throws IOException { }
        void onFrame(Connection connection, ReadBuffer payload) throws IOException;
        default void onClosed(Connection connection, CloseReason reason) { }
    }

    @FunctionalInterface
    public interface FrameEncoder {
        /** Write the body into the borrowed writer without retaining it. */
        void encode(WriteBuffer writer) throws IOException;
    }

    @FunctionalInterface
    public interface OwnerCommand {
        void run(Connection connection) throws IOException;
        default void cancel() { }
    }

    public record OwnerStats(SelectorBufferPool.Stats receive, SelectorBufferPool.Stats send,
            int remainingConnections, int remainingCommands, long zeroWrites) { }

    private static final int ACCEPT_BUDGET = 32;
    private static final int COMMAND_BUDGET = 64;
    private static final int READY_BUDGET = 256;
    private static final int FRAME_BUDGET = 16;
    private static final int GATHER_LIMIT = 16;
    private static final long OWNER_PHASE_NANOS = 500_000;
    private static final Duration CLOSE_TIMEOUT = Duration.ofSeconds(5);

    private final Config config;
    private final Handler handler;
    private final Owner[] owners;
    private final ServerSocketChannel listener;
    private final InetSocketAddress localAddress;
    private final CountDownLatch terminated;
    private final long writeStallNanos;
    private final AtomicReference<Throwable> failure = new AtomicReference<>();
    private int acceptOwner;

    public static SelectorTransport bind(InetSocketAddress address, Config config, Handler handler) throws IOException {
        return new SelectorTransport(address, config, handler);
    }

    private SelectorTransport(InetSocketAddress address, Config config, Handler handler) throws IOException {
        this.config = Objects.requireNonNull(config);
        this.handler = Objects.requireNonNull(handler);
        writeStallNanos = config.writeStallTimeout().toNanos();
        owners = new Owner[config.owners()];
        terminated = new CountDownLatch(owners.length);
        listener = ServerSocketChannel.open();
        int started = 0;
        try {
            listener.configureBlocking(false);
            listener.bind(address, config.acceptBacklog());
            localAddress = (InetSocketAddress) listener.getLocalAddress();
            for (int i = 0; i < owners.length; i++) owners[i] = new Owner(i);
            listener.register(owners[0].selector, SelectionKey.OP_ACCEPT);
            for (Owner owner : owners) {
                owner.thread.start();
                started++;
            }
        } catch (IOException | RuntimeException | Error startupFailure) {
            for (int i = 0; i < owners.length; i++) {
                if (owners[i] == null) continue;
                if (i < started) owners[i].requestStop();
                else closeSuppressing(owners[i].selector, startupFailure);
            }
            closeSuppressing(listener, startupFailure);
            throw startupFailure;
        }
    }

    public InetSocketAddress localAddress() { return localAddress; }

    public void requestStop() {
        for (Owner owner : owners) if (owner != null) owner.requestStop();
    }

    public boolean awaitTermination(Duration timeout) throws InterruptedException {
        requireExternalThread();
        return terminated.await(timeout.toNanos(), TimeUnit.NANOSECONDS);
    }

    public List<OwnerStats> shutdownStats() {
        if (terminated.getCount() != 0) throw new IllegalStateException("Transport is running");
        List<OwnerStats> result = new ArrayList<>(owners.length);
        for (Owner owner : owners) result.add(owner.stats);
        return List.copyOf(result);
    }

    @Override
    public void close() throws IOException {
        requireExternalThread();
        requestStop();
        try {
            if (!awaitTermination(CLOSE_TIMEOUT)) throw new IOException("Selector transport shutdown timed out");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while closing Selector transport", interrupted);
        }
        Throwable cause = failure.get();
        if (cause != null) throw new IOException("Selector owner failed", cause);
    }

    private void requireExternalThread() {
        Thread current = Thread.currentThread();
        for (Owner owner : owners) if (owner.thread == current) throw new IllegalStateException("Owner cannot wait for itself");
    }

    public final class Connection {
        private final Owner owner;
        private final SocketChannel channel;
        private final SelectionKey key;
        private final InetSocketAddress remoteAddress;
        private SelectorFrameReader reader;
        private SelectorSendQueue send;
        private final SelectorFrameReader.Handler dispatch = this::dispatch;
        private Cryptor cipher;
        private boolean plaintextNext;
        private boolean inputPaused;
        private boolean sendPressure;
        private boolean writeBlocked;
        private boolean closed;
        private boolean linked;
        private int readyOps;
        private long stallDeadline;
        private CloseReason drainReason;
        private Connection previous;
        private Connection next;

        private Connection(Owner owner, SocketChannel channel) throws IOException {
            this.owner = owner;
            this.channel = channel;
            remoteAddress = (InetSocketAddress) channel.getRemoteAddress();
            reader = new SelectorFrameReader(owner.receivePool);
            send = new SelectorSendQueue(owner.sendPool, config.sendBytesPerConnection(), config.sendFramesPerConnection());
            key = channel.register(owner.selector, SelectionKey.OP_READ, this);
        }

        public InetSocketAddress remoteAddress() { return remoteAddress; }
        public int ownerIndex() { return owner.index; }

        /** A successful publish will run or cancel on this owner. A false return leaves ownership with the caller. */
        public boolean tryExecute(OwnerCommand command) {
            Objects.requireNonNull(command);
            return owner.offer(new OwnerTask() {
                @Override
                public void run() {
                    if (closed) {
                        command.cancel();
                    } else {
                        try { command.run(Connection.this); }
                        catch (IOException | RuntimeException e) { terminate(CloseReason.HANDLER_ERROR); }
                    }
                }

                @Override public void cancel() { command.cancel(); }
            });
        }

        public void enableCipher(long seed, boolean firstOutboundPlaintext) {
            owner.requireOwner();
            if (closed || drainReason != null || cipher != null || !send.isEmpty() || send.isEncoding())
                throw new IllegalStateException("Cipher must start before any pending send");
            cipher = new Cryptor();
            cipher.initKey(seed);
            plaintextNext = firstOutboundPlaintext;
        }

        public boolean trySend(int maxPayloadBytes, FrameEncoder encoder) throws IOException {
            owner.requireOwner();
            Objects.requireNonNull(encoder);
            if (closed || drainReason != null) return false;
            SelectorSendQueue queue = send;
            boolean accepted;
            try {
                accepted = queue.send(maxPayloadBytes, encoder, cipher, plaintextNext);
            } catch (RuntimeException | Error error) {
                if (queue.isClosed()) terminate(CloseReason.HANDLER_ERROR);
                throw error;
            }
            if (!accepted) return false;
            plaintextNext = false;
            updatePressure();
            updateInterest();
            if (!writeBlocked) owner.schedule(this);
            return true;
        }

        public int pendingSendBytes() { owner.requireOwner(); return closed ? 0 : send.pendingBytes(); }

        public void pauseReads(boolean paused) {
            owner.requireOwner();
            inputPaused = paused;
            if (!closed) {
                updateInterest();
                if (canRead() && reader.hasBufferedFrame()) owner.schedule(this);
            }
        }

        public void closeAfterFlush() {
            owner.requireOwner();
            if (!closed && send.isEncoding()) throw new IllegalStateException("Cannot drain inside an encoder");
            beginDrain(CloseReason.APPLICATION);
        }

        public void abort() {
            owner.requireOwner();
            if (!closed && send.isEncoding()) throw new IllegalStateException("Cannot abort inside an encoder");
            terminate(CloseReason.APPLICATION);
        }

        private boolean dispatch(ReadBuffer payload) throws IOException {
            if (cipher != null && payload.remaining() != 0) {
                int length = payload.remaining();
                if (length < 8) throw new IOException("Encrypted inbound frame is too short");
                cipher.decrypt(payload, 0, length);
                payload.limit(length - 4);
            }
            try {
                handler.onFrame(this, payload);
            } catch (IOException | RuntimeException e) {
                terminate(CloseReason.HANDLER_ERROR);
            }
            return canRead();
        }

        private void turn() {
            int operations = readyOps;
            readyOps = 0;
            if (closed) return;
            try {
                if ((operations & SelectionKey.OP_WRITE) != 0) writeBlocked = false;
                if (canRead()) {
                    if ((operations & SelectionKey.OP_READ) != 0 && !reader.hasBufferedFrame()
                            && reader.read(channel) == -1) beginDrain(CloseReason.REMOTE_EOF);
                    if (canRead() && reader.hasBufferedFrame()) reader.drain(FRAME_BUDGET, dispatch);
                }
                if (closed) return;
                if (!writeBlocked && !send.isEmpty()) flush();
                if (send.isEmpty() && drainReason != null) {
                    terminate(drainReason);
                    return;
                }
                updateInterest();
                if ((!writeBlocked && !send.isEmpty()) || (canRead() && reader.hasBufferedFrame())) owner.schedule(this);
            } catch (IOException | RuntimeException e) {
                terminate(CloseReason.IO_ERROR);
            }
        }

        private void flush() throws IOException {
            long written = send.flush(channel, owner.gather);
            if (written > 0 || send.isEmpty()) clearStall();
            if (written == 0 && !send.isEmpty()) {
                writeBlocked = true;
                owner.zeroWrites++;
                if (stallDeadline == 0) {
                    stallDeadline = System.nanoTime() + writeStallNanos;
                    owner.stalled.add(this);
                }
            }
            updatePressure();
        }

        private void updatePressure() {
            int bytes = send.pendingBytes();
            int frames = send.pendingFrames();
            int byteLimit = config.sendBytesPerConnection();
            int frameLimit = config.sendFramesPerConnection();
            if (sendPressure) {
                if (bytes <= byteLimit / 2 && frames <= frameLimit / 2) sendPressure = false;
            } else if (bytes >= byteLimit - byteLimit / 4 || frames >= frameLimit - frameLimit / 4) {
                sendPressure = true;
            }
        }

        private boolean canRead() { return !closed && drainReason == null && !inputPaused && !sendPressure; }

        private void updateInterest() {
            int interests = canRead() ? SelectionKey.OP_READ : 0;
            if (writeBlocked) interests |= SelectionKey.OP_WRITE;
            if (key.interestOps() != interests) key.interestOps(interests);
        }

        private void beginDrain(CloseReason reason) {
            if (closed || drainReason != null) return;
            drainReason = reason;
            reader.close();
            if (send.isEmpty()) terminate(reason);
            else {
                updateInterest();
                if (!writeBlocked) owner.schedule(this);
            }
        }

        private void clearStall() {
            if (stallDeadline != 0) {
                owner.stalled.remove(this);
                stallDeadline = 0;
            }
        }

        private void terminate(CloseReason reason) {
            if (closed) return;
            closed = true;
            owner.unlink(this);
            clearStall();
            key.cancel();
            try { channel.close(); }
            catch (IOException closeError) { failure.compareAndSet(null, closeError); }
            finally {
                SelectorFrameReader inbound = reader;
                SelectorSendQueue outbound = send;
                // Application code may retain a closed handle. It must not pin either owner pool.
                reader = null;
                send = null;
                cipher = null;
                try { inbound.close(); }
                finally {
                    try { outbound.close(); }
                    finally {
                        owner.admitted.decrementAndGet();
                        handler.onClosed(this, reason);
                    }
                }
            }
        }
    }

    private interface OwnerTask {
        void run();
        void cancel();
    }

    private final class Owner implements Runnable {
        private final int index;
        private final Thread thread;
        private final Selector selector;
        private final AtomicInteger admitted = new AtomicInteger();
        private final ArrayDeque<OwnerTask> mailbox = new ArrayDeque<>(config.mailboxCapacity());
        private final PriorityQueue<Connection> stalled = new PriorityQueue<>(Comparator.comparingLong(c -> c.stallDeadline));
        private final ByteBuffer[] gather = new ByteBuffer[GATHER_LIMIT];
        private final Consumer<SelectionKey> collector = this::collect;
        private SelectorBufferPool receivePool;
        private SelectorBufferPool sendPool;
        private volatile boolean stopping;
        private boolean acceptReady;
        private Connection first;
        private Connection last;
        private long zeroWrites;
        private OwnerStats stats;

        private Owner(int index) throws IOException {
            this.index = index;
            thread = Thread.ofPlatform().name("jproring-selector-" + index).unstarted(this);
            selector = Selector.open();
        }

        private void requireOwner() {
            if (Thread.currentThread() != thread) throw new IllegalStateException("Wrong connection owner");
        }

        private boolean offer(OwnerTask task) {
            boolean wake;
            synchronized (mailbox) {
                if (stopping || mailbox.size() == config.mailboxCapacity()) return false;
                wake = mailbox.isEmpty();
                mailbox.addLast(task);
            }
            if (wake) selector.wakeup();
            return true;
        }

        private OwnerTask poll() {
            synchronized (mailbox) { return mailbox.pollFirst(); }
        }

        private int pendingCommands() {
            synchronized (mailbox) { return mailbox.size(); }
        }

        private void requestStop() {
            synchronized (mailbox) { stopping = true; }
            selector.wakeup();
        }

        @Override
        public void run() {
            try {
                receivePool = SelectorBufferPool.receive(config.connectionsPerOwner(), config.largeReceivesPerOwner());
                sendPool = SelectorBufferPool.send(config.smallSendsPerOwner(), config.largeSendsPerOwner());
                while (!stopping) {
                    long now = System.nanoTime();
                    drainExpired(now);
                    long commandDeadline = now + OWNER_PHASE_NANOS;
                    for (int i = 0; i < COMMAND_BUDGET && !stopping; i++) {
                        OwnerTask task = poll();
                        if (task == null) break;
                        task.run();
                        if ((i & 7) == 7 && System.nanoTime() - commandDeadline >= 0) break;
                    }
                    if (acceptReady && !stopping) {
                        acceptReady = false;
                        accept();
                    }
                    if (first != null) {
                        long readyDeadline = System.nanoTime() + OWNER_PHASE_NANOS;
                        for (int i = 0; i < READY_BUDGET && !stopping && first != null; i++) {
                            Connection connection = first;
                            unlink(connection);
                            connection.turn();
                            if ((i & 7) == 7 && System.nanoTime() - readyDeadline >= 0) break;
                        }
                    }
                    if (!stopping) select();
                }
            } catch (Throwable unexpected) {
                failure.compareAndSet(null, unexpected);
                SelectorTransport.this.requestStop();
            } finally {
                try { cleanup(); }
                catch (Throwable error) { failure.compareAndSet(null, error); }
                finally { terminated.countDown(); }
            }
        }

        private void select() throws IOException {
            if (first != null || pendingCommands() != 0) {
                selector.selectNow(collector);
            } else if (stalled.isEmpty()) {
                selector.select(collector);
            } else {
                long delay = stalled.peek().stallDeadline - System.nanoTime();
                if (delay <= 0) selector.selectNow(collector);
                else selector.select(collector, 1 + (delay - 1) / 1_000_000);
            }
        }

        private void collect(SelectionKey key) {
            if (!key.isValid()) return;
            int ready = key.readyOps();
            if ((ready & SelectionKey.OP_ACCEPT) != 0) {
                acceptReady = true;
            } else if (ready != 0) {
                Connection connection = (Connection) key.attachment();
                connection.readyOps |= ready;
                schedule(connection);
            }
        }

        private void drainExpired(long now) {
            for (int i = 0; i < COMMAND_BUDGET && !stalled.isEmpty(); i++) {
                Connection connection = stalled.peek();
                if (now - connection.stallDeadline < 0) break;
                stalled.remove();
                connection.stallDeadline = 0;
                connection.terminate(CloseReason.WRITE_TIMEOUT);
            }
        }

        private void schedule(Connection connection) {
            if (connection.linked || connection.closed) return;
            connection.linked = true;
            connection.previous = last;
            if (last == null) first = connection;
            else last.next = connection;
            last = connection;
        }

        private void unlink(Connection connection) {
            if (!connection.linked) return;
            Connection before = connection.previous;
            Connection after = connection.next;
            if (before == null) first = after;
            else before.next = after;
            if (after == null) last = before;
            else after.previous = before;
            connection.linked = false;
            connection.previous = connection.next = null;
        }

        private void register(SocketChannel channel) {
            Connection connection = null;
            try {
                channel.configureBlocking(false);
                channel.setOption(StandardSocketOptions.TCP_NODELAY, true);
                if (config.socketSendBytes() > 0) channel.setOption(StandardSocketOptions.SO_SNDBUF, config.socketSendBytes());
                connection = new Connection(this, channel);
                handler.onOpen(connection);
            } catch (IOException | RuntimeException e) {
                if (connection != null) connection.terminate(CloseReason.HANDLER_ERROR);
                else dropChannel(channel);
            } catch (Error error) {
                if (connection == null) dropChannel(channel);
                throw error;
            }
        }

        private void dropChannel(SocketChannel channel) {
            try { channel.close(); }
            catch (IOException e) { failure.compareAndSet(null, e); }
            finally { admitted.decrementAndGet(); }
        }

        private void cleanup() {
            OwnerTask task;
            while ((task = poll()) != null) {
                try { task.cancel(); }
                catch (Throwable error) { failure.compareAndSet(null, error); }
            }
            for (SelectionKey key : selector.keys()) {
                if (key.attachment() instanceof Connection connection) {
                    try { connection.terminate(CloseReason.SHUTDOWN); }
                    catch (Throwable error) { failure.compareAndSet(null, error); }
                }
            }
            try (Selector closingSelector = selector) {
                if (index == 0) listener.close();
            } catch (IOException e) { failure.compareAndSet(null, e); }
            if (receivePool != null && sendPool != null) {
                stats = new OwnerStats(receivePool.stats(), sendPool.stats(), admitted.get(), pendingCommands(), zeroWrites);
                receivePool = sendPool = null;
            }
        }
    }

    private void accept() throws IOException {
        for (int i = 0; i < ACCEPT_BUDGET; i++) {
            SocketChannel channel = listener.accept();
            if (channel == null) return;
            Owner owner = reserveOwner();
            if (owner == null) {
                channel.close();
            } else if (owner.index == 0) {
                owner.register(channel);
            } else if (!owner.offer(new OwnerTask() {
                @Override public void run() { owner.register(channel); }
                @Override public void cancel() { owner.dropChannel(channel); }
            })) {
                owner.dropChannel(channel);
            }
        }
    }

    private Owner reserveOwner() {
        for (int i = 0; i < owners.length; i++) {
            Owner owner = owners[acceptOwner];
            acceptOwner = (acceptOwner + 1) % owners.length;
            if (!owner.stopping && owner.admitted.get() < config.connectionsPerOwner()) {
                // Only the listener owner increments. Other owners can only return slots.
                owner.admitted.incrementAndGet();
                return owner;
            }
        }
        return null;
    }

    private static void closeSuppressing(AutoCloseable resource, Throwable failure) {
        try { resource.close(); }
        catch (Exception closeError) { failure.addSuppressed(closeError); }
    }
}
