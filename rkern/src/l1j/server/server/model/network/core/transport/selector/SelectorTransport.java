package l1j.server.server.model.network.core.transport.selector;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
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
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;

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

    /** The server's existing wire codec and FIFO can supply buffers directly. */
    public interface RawHandler extends Handler {
        @Override
        default void onFrame(Connection connection, ReadBuffer payload) {
            throw new IllegalStateException("Raw handler received a framed payload");
        }
    }

    /** Installed on existing owner threads by the game scheduler. */
    public interface OwnerDriver {
        void advance();
        void awaitWork(long maxWaitMillis);
        void wakeup();
        void closeOnOwner();
        boolean active();
    }

    @FunctionalInterface
    public interface OwnerHost {
        void attach(List<OwnerDriver> owners);
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
    private volatile boolean accepting = true;

    public static SelectorTransport bind(InetSocketAddress address, Config config, Handler handler) throws IOException {
        return new SelectorTransport(address, config, handler, null);
    }

    public static SelectorTransport bindHosted(InetSocketAddress address, Config config, Handler handler,
            OwnerHost host) throws IOException {
        return new SelectorTransport(address, config, handler, Objects.requireNonNull(host));
    }

    private SelectorTransport(InetSocketAddress address, Config config, Handler handler, OwnerHost host) throws IOException {
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
            for (int i = 0; i < owners.length; i++) owners[i] = new Owner(i, host != null);
            listener.register(owners[0].selector, SelectionKey.OP_ACCEPT);
            if (host == null) {
                for (Owner owner : owners) {
                    owner.thread.start();
                    started++;
                }
            } else host.attach(List.of(owners));
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

    public boolean isOwnerThread() {
        Thread current = Thread.currentThread();
        for (Owner owner : owners) if (owner != null && owner.thread == current) return true;
        return false;
    }

    public boolean isAcceptStopped() { return !listener.isOpen(); }
    public boolean isTerminated() { return terminated.getCount() == 0; }
    public Throwable shutdownFailure() { return failure.get(); }

    public void stopAccepting() throws IOException {
        accepting = false;
        try { listener.close(); }
        finally { owners[0].selector.wakeup(); }
    }

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
        private final SelectorRawIo raw;
        private SelectorFrameReader reader;
        private SelectorSendQueue send;
        private final SelectorFrameReader.Handler dispatch = this::dispatch;
        private Cryptor cipher;
        private boolean plaintextNext;
        private boolean inputPaused;
        private boolean sendPressure;
        private boolean writeBlocked;
        private volatile boolean closed;
        private boolean linked;
        private int readyOps;
        private long stallDeadline;
        private long scheduledDeadline;
        private Runnable deadlineAction;
        private int closeRequest; // guarded by the owner mailbox monitor
        private CloseReason drainReason;
        private Connection previous;
        private Connection next;

        private Connection(Owner owner, SocketChannel channel) throws IOException {
            this.owner = owner;
            this.channel = channel;
            remoteAddress = (InetSocketAddress) channel.getRemoteAddress();
            raw = handler instanceof RawHandler ? new SelectorRawIo() : null;
            if (raw == null) {
                reader = new SelectorFrameReader(owner.receivePool);
                send = new SelectorSendQueue(owner.sendPool, config.sendBytesPerConnection(), config.sendFramesPerConnection());
            }
            key = channel.register(owner.selector, raw == null ? SelectionKey.OP_READ : 0, this);
        }

        public InetSocketAddress remoteAddress() { return remoteAddress; }
        public int ownerIndex() { return owner.index; }
        public boolean isOwnerThread() { return Thread.currentThread() == owner.thread; }
        public boolean isOpen() { return !closed; }

        /** Control-plane close cannot be refused by a full regular mailbox. */
        public void requestClose(boolean drain) {
            if (isOwnerThread()) {
                if (drain) closeAfterFlush();
                else abort();
            } else owner.queueClose(this, drain ? 1 : 2);
        }

        public boolean scheduleDeadline(long delayMillis, Runnable callback) {
            owner.requireOwner();
            if (closed || owner.stopping) return false;
            if (delayMillis < 0 || delayMillis > Integer.MAX_VALUE) throw new IllegalArgumentException("Deadline delay");
            long due = System.nanoTime() + Math.max(1L, delayMillis) * 1_000_000;
            if (deadlineAction != null) {
                if (scheduledDeadline - due <= 0) return true;
                owner.deadlines.remove(this);
            }
            deadlineAction = Objects.requireNonNull(callback);
            scheduledDeadline = due;
            owner.deadlines.add(this);
            return true;
        }

        public boolean readRaw(ByteBuffer target, IntConsumer completion) {
            owner.requireOwner();
            requireRaw();
            if (closed || drainReason != null) return false;
            if (!target.hasRemaining() || target.isReadOnly()) throw new IllegalArgumentException("READ buffer");
            raw.armRead(target, Objects.requireNonNull(completion));
            updateInterest();
            return true;
        }

        public boolean writeRaw(ByteBuffer source, LongConsumer completion) {
            if (!source.hasRemaining()) throw new IllegalArgumentException("Empty WRITE buffer");
            return armRawWrite(source, null, 0, 0, completion);
        }

        public boolean writeRaw(ByteBuffer[] sources, int offset, int count, LongConsumer completion) {
            Objects.checkFromIndexSize(offset, count, sources.length);
            if (count == 0) throw new IllegalArgumentException("Empty gather WRITE");
            return armRawWrite(null, sources, offset, count, completion);
        }

        private boolean armRawWrite(ByteBuffer source, ByteBuffer[] sources, int offset, int count, LongConsumer completion) {
            owner.requireOwner();
            requireRaw();
            if (closed || drainReason != null) return false;
            raw.armWrite(source, sources, offset, count, Objects.requireNonNull(completion));
            if (!writeBlocked) owner.schedule(this);
            return true;
        }

        private void requireRaw() {
            if (raw == null) throw new IllegalStateException("Connection uses the framed transport");
        }

        private void requireFramed() {
            if (raw != null) throw new IllegalStateException("Connection uses caller-managed buffers");
        }

        /** A successful publish will run or cancel on this owner. A false return leaves ownership with the caller. */
        public boolean tryExecute(OwnerCommand command) {
            return submit(command, false);
        }

        /** Reusable command for foreign submissions whose arguments are stored by the caller. */
        public boolean submit(OwnerCommand command, boolean alwaysDefer) {
            Objects.requireNonNull(command);
            if (!alwaysDefer && isOwnerThread()) {
                if (closed || owner.stopping) return false;
                runCommand(command);
                return true;
            }
            return owner.offer(new OwnerTask() {
                @Override
                public void run() {
                    if (closed) command.cancel();
                    else runCommand(command);
                }

                @Override public void cancel() { command.cancel(); }
            });
        }

        private void runCommand(OwnerCommand command) {
            try { command.run(this); }
            catch (IOException | RuntimeException e) { terminate(CloseReason.HANDLER_ERROR); }
        }

        public void enableCipher(long seed, boolean firstOutboundPlaintext) {
            owner.requireOwner();
            requireFramed();
            if (closed || drainReason != null || cipher != null || !send.isEmpty() || send.isEncoding())
                throw new IllegalStateException("Cipher must start before any pending send");
            cipher = new Cryptor();
            cipher.initKey(seed);
            plaintextNext = firstOutboundPlaintext;
        }

        public boolean trySend(int maxPayloadBytes, FrameEncoder encoder) throws IOException {
            owner.requireOwner();
            requireFramed();
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

        public int pendingSendBytes() { owner.requireOwner(); return closed || raw != null ? 0 : send.pendingBytes(); }

        public void pauseReads(boolean paused) {
            owner.requireOwner();
            inputPaused = paused;
            if (!closed) {
                updateInterest();
                if (raw == null && canRead() && reader.hasBufferedFrame()) owner.schedule(this);
            }
        }

        public void closeAfterFlush() {
            owner.requireOwner();
            if (send != null && send.isEncoding()) throw new IllegalStateException("Cannot drain inside an encoder");
            beginDrain(CloseReason.APPLICATION);
        }

        public void abort() {
            owner.requireOwner();
            if (send != null && send.isEncoding()) throw new IllegalStateException("Cannot abort inside an encoder");
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
                if (raw != null) {
                    if (canRead() && (operations & SelectionKey.OP_READ) != 0 && raw.read(channel) < 0)
                        beginDrain(CloseReason.REMOTE_EOF);
                    if (!closed && !writeBlocked && raw.writing()) rawFlush();
                    finishTurn();
                    return;
                }
                if (canRead()) {
                    if ((operations & SelectionKey.OP_READ) != 0 && !reader.hasBufferedFrame()
                            && reader.read(channel) == -1) beginDrain(CloseReason.REMOTE_EOF);
                    if (canRead() && reader.hasBufferedFrame()) reader.drain(FRAME_BUDGET, dispatch);
                }
                if (closed) return;
                if (!writeBlocked && !send.isEmpty()) flush();
                finishTurn();
            } catch (IOException | RuntimeException e) {
                terminate(CloseReason.IO_ERROR);
            }
        }

        private void finishTurn() {
            if (closed) return;
            if (!hasWrites() && drainReason != null) {
                terminate(drainReason);
                return;
            }
            updateInterest();
            if ((!writeBlocked && hasWrites()) || (raw == null && canRead() && reader.hasBufferedFrame()))
                owner.schedule(this);
        }

        private boolean hasWrites() { return raw == null ? !send.isEmpty() : raw.writing(); }

        private void rawFlush() throws IOException {
            long written = raw.write(channel);
            if (!closed) recordWrite(written);
        }

        private void flush() throws IOException {
            recordWrite(send.flush(channel, owner.gather));
            updatePressure();
        }

        private void recordWrite(long written) {
            if (written > 0 || !hasWrites()) clearStall();
            if (written == 0 && hasWrites()) {
                writeBlocked = true;
                owner.zeroWrites++;
                if (stallDeadline == 0) {
                    stallDeadline = System.nanoTime() + writeStallNanos;
                    owner.stalled.add(this);
                }
            }
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
            int interests = canRead() && (raw == null || raw.reading()) ? SelectionKey.OP_READ : 0;
            if (writeBlocked) interests |= SelectionKey.OP_WRITE;
            if (key.interestOps() != interests) key.interestOps(interests);
        }

        private void beginDrain(CloseReason reason) {
            if (closed || drainReason != null) return;
            drainReason = reason;
            if (reader != null) reader.close();
            if (!hasWrites()) terminate(reason);
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
            if (deadlineAction != null) {
                owner.deadlines.remove(this);
                deadlineAction = null;
            }
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
                try {
                    if (raw != null) raw.cancel();
                    else inbound.close();
                } finally {
                    try { if (outbound != null) outbound.close(); }
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

    private final class Owner implements Runnable, OwnerDriver {
        private final int index;
        private volatile Thread thread;
        private final Selector selector;
        private final AtomicInteger admitted = new AtomicInteger();
        private final ArrayDeque<OwnerTask> mailbox = new ArrayDeque<>(config.mailboxCapacity());
        private final ArrayDeque<Connection> closing = new ArrayDeque<>();
        private final PriorityQueue<Connection> stalled = new PriorityQueue<>(Comparator.comparingLong(c -> c.stallDeadline));
        private final PriorityQueue<Connection> deadlines = new PriorityQueue<>(Comparator.comparingLong(c -> c.scheduledDeadline));
        private final ByteBuffer[] gather = new ByteBuffer[GATHER_LIMIT];
        private final Consumer<SelectionKey> collector = this::collect;
        private SelectorBufferPool receivePool;
        private SelectorBufferPool sendPool;
        private volatile boolean stopping;
        private volatile boolean finished;
        private boolean acceptReady;
        private Connection first;
        private Connection last;
        private long zeroWrites;
        private OwnerStats stats;

        private Owner(int index, boolean hosted) throws IOException {
            this.index = index;
            if (!hosted) thread = Thread.ofPlatform().name("jproring-selector-" + index).unstarted(this);
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

        private void queueClose(Connection connection, int request) {
            boolean wake = false;
            synchronized (mailbox) {
                if (stopping || connection.closed || connection.closeRequest >= request) return;
                if (connection.closeRequest == 0) {
                    wake = closing.isEmpty();
                    closing.addLast(connection);
                }
                connection.closeRequest = request;
            }
            if (wake) selector.wakeup();
        }

        private void drainCloses() {
            for (int i = 0; i < COMMAND_BUDGET && !stopping; i++) {
                Connection connection;
                int request;
                synchronized (mailbox) {
                    connection = closing.pollFirst();
                    if (connection == null) break;
                    request = connection.closeRequest;
                    connection.closeRequest = 0;
                }
                if (request == 1) connection.closeAfterFlush();
                else connection.abort();
            }
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
                initialize();
                while (!stopping) {
                    work();
                    if (!stopping) select(-1);
                }
            } catch (Throwable unexpected) {
                fail(unexpected);
            } finally {
                finish();
            }
        }

        @Override
        public void advance() {
            if (finished) return;
            try {
                initialize();
                if (!stopping) {
                    selector.selectNow(collector);
                    work();
                }
            } catch (Throwable unexpected) { fail(unexpected); }
            if (stopping) finish();
        }

        @Override
        public void awaitWork(long maxWaitMillis) {
            if (stopping || finished) return;
            requireOwner();
            try { select(Math.max(0, maxWaitMillis)); }
            catch (Throwable unexpected) { fail(unexpected); }
            if (stopping) finish();
        }

        @Override public void wakeup() { selector.wakeup(); }
        @Override public boolean active() { return !finished; }

        @Override
        public void closeOnOwner() {
            if (finished) return;
            if (thread == null) thread = Thread.currentThread();
            requireOwner();
            SelectorTransport.this.requestStop();
            finish();
        }

        private void initialize() {
            if (thread == null) thread = Thread.currentThread();
            requireOwner();
            if (receivePool != null) return;
            receivePool = SelectorBufferPool.receive(config.connectionsPerOwner(), config.largeReceivesPerOwner());
            sendPool = SelectorBufferPool.send(config.smallSendsPerOwner(), config.largeSendsPerOwner());
        }

        private void fail(Throwable unexpected) {
            failure.compareAndSet(null, unexpected);
            SelectorTransport.this.requestStop();
        }

        private void finish() {
            if (finished) return;
            try { cleanup(); }
            catch (Throwable error) { failure.compareAndSet(null, error); }
            finally {
                finished = true;
                terminated.countDown();
            }
        }

        private void work() throws IOException {
            long now = System.nanoTime();
            drainExpired(now);
            drainCloses();
            long commandDeadline = now + OWNER_PHASE_NANOS;
            for (int i = 0; i < COMMAND_BUDGET && !stopping; i++) {
                OwnerTask task = poll();
                if (task == null) break;
                task.run();
                if ((i & 7) == 7 && System.nanoTime() - commandDeadline >= 0) break;
            }
            if (acceptReady) {
                acceptReady = false;
                if (accepting && !stopping) accept();
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
        }

        private void select(long maxWaitMillis) throws IOException {
            if (maxWaitMillis == 0 || first != null || acceptReady || hasMailboxWork()) {
                selector.selectNow(collector);
                return;
            }
            long waitMillis = maxWaitMillis;
            if (!stalled.isEmpty() || !deadlines.isEmpty()) {
                long deadline = stalled.isEmpty() ? deadlines.peek().scheduledDeadline : stalled.peek().stallDeadline;
                if (!deadlines.isEmpty() && deadlines.peek().scheduledDeadline - deadline < 0)
                    deadline = deadlines.peek().scheduledDeadline;
                long delay = deadline - System.nanoTime();
                if (delay <= 0) {
                    selector.selectNow(collector);
                    return;
                }
                long deadlineMillis = 1 + (delay - 1) / 1_000_000;
                if (waitMillis < 0 || deadlineMillis < waitMillis) waitMillis = deadlineMillis;
            }
            if (waitMillis < 0) selector.select(collector);
            else selector.select(collector, waitMillis);
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

        private boolean hasMailboxWork() {
            synchronized (mailbox) { return !mailbox.isEmpty() || !closing.isEmpty(); }
        }

        private void drainExpired(long now) {
            for (int i = 0; i < COMMAND_BUDGET && !deadlines.isEmpty(); i++) {
                Connection connection = deadlines.peek();
                if (now - connection.scheduledDeadline < 0) break;
                deadlines.remove();
                Runnable callback = connection.deadlineAction;
                connection.deadlineAction = null;
                try { callback.run(); }
                catch (RuntimeException error) { connection.terminate(CloseReason.HANDLER_ERROR); }
            }
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
            synchronized (mailbox) { closing.clear(); }
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
            SocketChannel channel;
            try { channel = listener.accept(); }
            catch (ClosedChannelException stopped) {
                if (!accepting) return;
                throw stopped;
            }
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
