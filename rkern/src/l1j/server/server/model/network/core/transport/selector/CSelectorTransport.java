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
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;

import l1j.server.server.model.network.buffer.CPacketReadBuffer;
import l1j.server.server.model.network.buffer.CPacketWriteBuffer;
import l1j.server.server.model.network.codec.CPacketCryptor;

/** Bounded, standard-Java TCP transport. Each connection has one Selector owner for its entire lifetime. */
public final class CSelectorTransport implements AutoCloseable {
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
        void onFrame(Connection connection, CPacketReadBuffer payload) throws IOException;
        default void onClosed(Connection connection, CloseReason reason) { }
    }

    /** The server's existing wire codec and FIFO can supply buffers directly. */
    public interface RawHandler extends Handler {
        @Override
        default void onFrame(Connection connection, CPacketReadBuffer payload) {
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
        void encode(CPacketWriteBuffer writer) throws IOException;
    }

    @FunctionalInterface
    public interface OwnerCommand {
        void run(Connection connection) throws IOException;
        default void cancel() { }
    }

    public record OwnerStats(CSelectorBufferPool.Stats receive, CSelectorBufferPool.Stats send,
            int remainingConnections, int remainingCommands, long zeroWrites, long callbackFailures) { }

    public record OwnerDiagnostics(int index, int admitted, long registered, int queuedCommands,
            int commandHighWater, long rejectedCommands, int deadlines, int stalledWrites,
            long zeroWrites, long writeTimeouts, long callbackFailures,
            long rawWrites, long rawGatherWrites, long rawWriteSegments) { }

    private static final int ACCEPT_BUDGET = 32;
    private static final int COMMAND_BUDGET = 64;
    private static final int READY_BUDGET = 256;
    private static final int FRAME_BUDGET = 16;
    private static final int GATHER_LIMIT = 16;
    private static final long OWNER_PHASE_NANOS = 500_000;
    private static final boolean MEASURE_WRITES = Boolean.getBoolean("rkern.network.measureWrites");
    private static final Duration CLOSE_TIMEOUT = Duration.ofSeconds(5);
    private static final System.Logger LOG = System.getLogger(CSelectorTransport.class.getName());
    private static final CSelectorBufferPool.Stats EMPTY_POOL_STATS = new CSelectorBufferPool.Stats(0, 0, 0, 0, 0);

    private final Config config;
    private final Handler handler;
    private final Owner[] owners;
    private final ServerSocketChannel listener;
    private final InetSocketAddress localAddress;
    private final CountDownLatch terminated;
    private final long writeStallNanos;
    private final AtomicReference<Throwable> failure = new AtomicReference<>();
    private final Object startupLock = new Object();
    private volatile boolean started;
    private int acceptOwner;
    private volatile boolean accepting = true;

    public static CSelectorTransport bind(InetSocketAddress address, Config config, Handler handler) throws IOException {
        return new CSelectorTransport(address, config, handler, null);
    }

    public static CSelectorTransport bindHosted(InetSocketAddress address, Config config, Handler handler,
            OwnerHost host) throws IOException {
        return new CSelectorTransport(address, config, handler, Objects.requireNonNull(host));
    }

    private CSelectorTransport(InetSocketAddress address, Config config, Handler handler, OwnerHost host) throws IOException {
        this.config = Objects.requireNonNull(config);
        this.handler = Objects.requireNonNull(handler);
        writeStallNanos = config.writeStallTimeout().toNanos();
        owners = new Owner[config.owners()];
        terminated = new CountDownLatch(owners.length);
        listener = ServerSocketChannel.open();
        int startedOwners = 0;
        try {
            listener.configureBlocking(false);
            listener.bind(address, config.acceptBacklog());
            localAddress = (InetSocketAddress) listener.getLocalAddress();
            for (int i = 0; i < owners.length; i++) owners[i] = new Owner(i, host != null);
            listener.register(owners[0].selector, SelectionKey.OP_ACCEPT);
            if (host == null) {
                started = true;
                for (Owner owner : owners) {
                    owner.thread.start();
                    startedOwners++;
                }
            } else {
                host.attach(List.of(owners));
                synchronized (startupLock) {
                    for (Owner owner : owners) {
                        if (owner.stopping) throw new IllegalStateException("Hosted Selector stopped during attachment");
                    }
                    started = true;
                }
            }
        } catch (IOException | RuntimeException | Error startupFailure) {
            for (int i = 0; i < owners.length; i++) {
                if (owners[i] == null) continue;
                Owner owner = owners[i];
                if (i < startedOwners) owner.requestStop();
                else {
                    closeSuppressing(owner.selector, startupFailure);
                    owner.finished = true;
                    terminated.countDown();
                }
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

    /** Live observations. Each queue depth, limit counter, and socket count is a snapshot. */
    public List<OwnerDiagnostics> ownerDiagnostics() {
        List<OwnerDiagnostics> report = new ArrayList<>(owners.length);
        for (Owner owner : owners) report.add(owner.diagnostics());
        return List.copyOf(report);
    }

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
        private final CSelectorRawIo raw;
        private CSelectorFrameReader reader;
        private CSelectorSendQueue send;
        private final CSelectorFrameReader.Handler dispatch = this::dispatch;
        private CPacketCryptor cipher;
        private boolean plaintextNext;
        private boolean inputPaused;
        private boolean sendPressure;
        private boolean writeBlocked;
        private volatile boolean closed;
        private boolean linked;
        private int readyOps;
        private long stallDeadline;
        private long scheduledDeadline;
        private int stallIndex = -1;
        private int deadlineIndex = -1;
        private Runnable deadlineAction;
        private int closeRequest; // guarded by the owner mailbox monitor
        private CloseReason drainReason;
        private Connection previous;
        private Connection next;

        private Connection(Owner owner, SocketChannel channel) throws IOException {
            this.owner = owner;
            this.channel = channel;
            remoteAddress = (InetSocketAddress) channel.getRemoteAddress();
            raw = handler instanceof RawHandler ? new CSelectorRawIo() : null;
            if (raw == null) {
                reader = new CSelectorFrameReader(owner.receivePool);
                send = new CSelectorSendQueue(owner.sendPool, config.sendBytesPerConnection(), config.sendFramesPerConnection());
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
            if (deadlineAction != null && scheduledDeadline - due <= 0) return true;
            deadlineAction = Objects.requireNonNull(callback);
            scheduledDeadline = due;
            owner.deadlines.addOrAdvance(this);
            owner.deadlineDepth = owner.deadlines.size();
            return true;
        }

        public boolean readRaw(ByteBuffer target, IntConsumer completion) {
            owner.requireOwner();
            requireRaw();
            if (closed || drainReason != null || owner.stopping) return false;
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
            if (closed || drainReason != null || owner.stopping) return false;
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
            return owner.offer(this, command);
        }

        private void runCommand(OwnerCommand command) {
            try { command.run(this); }
            catch (IOException | RuntimeException e) { terminate(CloseReason.HANDLER_ERROR); }
        }

        private void cancelCommand(OwnerCommand command) {
            try { command.cancel(); }
            catch (RuntimeException error) { owner.reportCallbackFailure("command cancellation", error); }
        }

        public void enableCipher(long seed, boolean firstOutboundPlaintext) {
            owner.requireOwner();
            requireFramed();
            if (closed || drainReason != null || cipher != null || !send.isEmpty() || send.isEncoding())
                throw new IllegalStateException("Cipher must start before any pending send");
            cipher = new CPacketCryptor();
            cipher.initKey(seed);
            plaintextNext = firstOutboundPlaintext;
        }

        public boolean trySend(int maxPayloadBytes, FrameEncoder encoder) throws IOException {
            owner.requireOwner();
            requireFramed();
            Objects.requireNonNull(encoder);
            if (closed || drainReason != null) return false;
            CSelectorSendQueue queue = send;
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

        /** Drain the accepted framed queue or complete the single pending raw WRITE, then send FIN. */
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

        private boolean dispatch(CPacketReadBuffer payload) throws IOException {
            if (cipher != null) {
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
            if (MEASURE_WRITES) {
                int segments = raw.writeSegments();
                owner.rawWrites++;
                if (segments > 1) owner.rawGatherWrites++;
                owner.rawWriteSegments += segments;
            }
            long written = raw.write(channel, drainReason != null);
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
                    owner.stalled.addOrAdvance(this);
                    owner.stalledDepth = owner.stalled.size();
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
                owner.stalledDepth = owner.stalled.size();
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
                owner.deadlineDepth = owner.deadlines.size();
                deadlineAction = null;
            }
            key.cancel();
            try { channel.close(); }
            catch (IOException closeError) { failure.compareAndSet(null, closeError); }
            finally {
                CSelectorFrameReader inbound = reader;
                CSelectorSendQueue outbound = send;
                // Application code may retain a closed handle. It must not pin either owner pool.
                reader = null;
                send = null;
                cipher = null;
                try {
                    try {
                        if (raw != null) raw.cancel(owner.reportRawCancellation);
                        else inbound.close();
                    } finally {
                        try { if (outbound != null) outbound.close(); }
                        finally {
                            owner.admitted.decrementAndGet();
                            try { handler.onClosed(this, reason); }
                            catch (RuntimeException error) { owner.reportCallbackFailure("terminal notification", error); }
                        }
                    }
                } catch (RuntimeException | Error invariantFailure) {
                    owner.fail(invariantFailure);
                    throw invariantFailure;
                }
            }
        }
    }

    /** Owner-only binary heap with one reusable index per connection and timer kind. */
    private final class DeadlineHeap {
        private final ArrayList<Connection> nodes = new ArrayList<>();
        private final boolean writeStall;

        private DeadlineHeap(boolean writeStall) { this.writeStall = writeStall; }

        private int size() { return nodes.size(); }
        private boolean isEmpty() { return nodes.isEmpty(); }
        private Connection peek() { return nodes.getFirst(); }

        private long time(Connection connection) {
            return writeStall ? connection.stallDeadline : connection.scheduledDeadline;
        }

        private int index(Connection connection) {
            return writeStall ? connection.stallIndex : connection.deadlineIndex;
        }

        private void setIndex(Connection connection, int index) {
            if (writeStall) connection.stallIndex = index;
            else connection.deadlineIndex = index;
        }

        private void place(int index, Connection connection) {
            nodes.set(index, connection);
            setIndex(connection, index);
        }

        private void addOrAdvance(Connection connection) {
            int index = index(connection);
            if (index < 0) {
                index = nodes.size();
                nodes.add(connection);
            }
            siftUp(index, connection);
        }

        private void remove(Connection connection) {
            int index = index(connection);
            if (index < 0 || nodes.get(index) != connection) throw new IllegalStateException("Deadline heap index");
            Connection last = nodes.removeLast();
            setIndex(connection, -1);
            if (last == connection) return;
            if (index > 0 && time(last) - time(nodes.get((index - 1) >>> 1)) < 0) siftUp(index, last);
            else siftDown(index, last);
        }

        private void siftUp(int index, Connection connection) {
            long due = time(connection);
            while (index > 0) {
                int parent = (index - 1) >>> 1;
                Connection earlier = nodes.get(parent);
                if (due - time(earlier) >= 0) break;
                place(index, earlier);
                index = parent;
            }
            place(index, connection);
        }

        private void siftDown(int index, Connection connection) {
            int half = nodes.size() >>> 1;
            long due = time(connection);
            while (index < half) {
                int child = (index << 1) + 1;
                Connection earlier = nodes.get(child);
                if (child + 1 < nodes.size() && time(nodes.get(child + 1)) - time(earlier) < 0) {
                    earlier = nodes.get(++child);
                }
                if (due - time(earlier) <= 0) break;
                place(index, earlier);
                index = child;
            }
            place(index, connection);
        }
    }

    private final class Owner implements Runnable, OwnerDriver {
        private final int index;
        private volatile Thread thread;
        private final Selector selector;
        private final AtomicInteger admitted = new AtomicInteger();
        // Targets are a Connection with a command or a SocketChannel awaiting registration.
        // Slots are published in one monitor transaction and cleared before running callbacks.
        private final Object[] mailbox = new Object[config.mailboxCapacity()];
        private final OwnerCommand[] mailboxCommands = new OwnerCommand[mailbox.length];
        private int mailboxHead, mailboxTail, mailboxSize;
        private final ArrayDeque<Connection> closing = new ArrayDeque<>();
        private final DeadlineHeap stalled = new DeadlineHeap(true);
        private final DeadlineHeap deadlines = new DeadlineHeap(false);
        private final ByteBuffer[] gather = new ByteBuffer[GATHER_LIMIT];
        private final Consumer<SelectionKey> collector = this::collect;
        private final Consumer<RuntimeException> reportRawCancellation =
                error -> reportCallbackFailure("raw I/O cancellation", error);
        private CSelectorBufferPool receivePool;
        private CSelectorBufferPool sendPool;
        private volatile boolean stopping;
        private volatile boolean finished;
        private boolean acceptReady;
        private Connection first;
        private Connection last;
        private volatile long zeroWrites;
        private volatile long rawWrites, rawGatherWrites, rawWriteSegments;
        private volatile long callbackFailures;
        private volatile long registered;
        private volatile long writeTimeouts;
        private volatile int deadlineDepth;
        private volatile int stalledDepth;
        private int commandHighWater; // guarded by mailbox
        private long rejectedCommands; // guarded by mailbox
        private long lastCallbackLog;
        private OwnerStats stats;

        private Owner(int index, boolean hosted) throws IOException {
            this.index = index;
            if (!hosted) thread = Thread.ofPlatform().name("jproring-selector-" + index).unstarted(this);
            selector = Selector.open();
        }

        private void requireOwner() {
            if (Thread.currentThread() != thread) throw new IllegalStateException("Wrong connection owner");
        }

        private boolean offer(Object target, OwnerCommand command) {
            boolean wake;
            synchronized (mailbox) {
                if (stopping || mailboxSize == mailbox.length) {
                    rejectedCommands++;
                    return false;
                }
                wake = mailboxSize == 0;
                mailbox[mailboxTail] = target;
                mailboxCommands[mailboxTail] = command;
                if (++mailboxTail == mailbox.length) mailboxTail = 0;
                commandHighWater = Math.max(commandHighWater, ++mailboxSize);
            }
            if (wake) selector.wakeup();
            return true;
        }

        private OwnerDiagnostics diagnostics() {
            synchronized (mailbox) {
                return new OwnerDiagnostics(index, admitted.get(), registered, mailboxSize, commandHighWater,
                        rejectedCommands, deadlineDepth, stalledDepth, zeroWrites, writeTimeouts, callbackFailures,
                        rawWrites, rawGatherWrites, rawWriteSegments);
            }
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

        private boolean processCommand(boolean cancel) {
            final Object target;
            final OwnerCommand command;
            synchronized (mailbox) {
                if (mailboxSize == 0) return false;
                target = mailbox[mailboxHead];
                command = mailboxCommands[mailboxHead];
                mailbox[mailboxHead] = null;
                mailboxCommands[mailboxHead] = null;
                if (++mailboxHead == mailbox.length) mailboxHead = 0;
                --mailboxSize;
            }
            if (target instanceof Connection connection) {
                if (cancel || connection.closed) connection.cancelCommand(command);
                else connection.runCommand(command);
            } else {
                SocketChannel channel = (SocketChannel) target;
                if (cancel) dropChannel(channel);
                else register(channel);
            }
            return true;
        }

        private int pendingCommands() {
            synchronized (mailbox) { return mailboxSize; }
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
            if (!started || finished) return;
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
            // A host may publish drivers on running threads before its attach call returns.
            if (!started) {
                LockSupport.parkNanos(Math.max(0L, Math.min(1L, maxWaitMillis)) * 1_000_000L);
                return;
            }
            try {
                // Attachment can commit between the host's advance and awaitWork calls.
                // In that case this is the first active call on the owning thread.
                if (thread == null) initialize();
                else requireOwner();
                select(Math.max(0, maxWaitMillis));
            }
            catch (Throwable unexpected) { fail(unexpected); }
            if (stopping) finish();
        }

        @Override public void wakeup() { selector.wakeup(); }
        @Override public boolean active() { return !finished; }

        @Override
        public void closeOnOwner() {
            synchronized (startupLock) {
                if (finished) return;
                if (!started) {
                    CSelectorTransport.this.requestStop();
                    return;
                }
            }
            if (thread == null) thread = Thread.currentThread();
            requireOwner();
            CSelectorTransport.this.requestStop();
            finish();
        }

        private void initialize() {
            if (thread == null) thread = Thread.currentThread();
            requireOwner();
            if (handler instanceof RawHandler || receivePool != null) return;
            receivePool = CSelectorBufferPool.receive(config.connectionsPerOwner(), config.largeReceivesPerOwner());
            sendPool = CSelectorBufferPool.send(config.smallSendsPerOwner(), config.largeSendsPerOwner());
        }

        private void fail(Throwable unexpected) {
            failure.compareAndSet(null, unexpected);
            CSelectorTransport.this.requestStop();
        }

        private void reportCallbackFailure(String stage, RuntimeException error) {
            long now = System.nanoTime();
            if (++callbackFailures == 1 || now - lastCallbackLog >= TimeUnit.SECONDS.toNanos(1)) {
                lastCallbackLog = now;
                LOG.log(System.Logger.Level.WARNING, "Selector owner " + index + " failed during " + stage, error);
            }
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
                if (!processCommand(false)) break;
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
            synchronized (mailbox) { return mailboxSize != 0 || !closing.isEmpty(); }
        }

        private void drainExpired(long now) {
            for (int i = 0; i < COMMAND_BUDGET && !deadlines.isEmpty(); i++) {
                Connection connection = deadlines.peek();
                if (now - connection.scheduledDeadline < 0) break;
                deadlines.remove(connection);
                deadlineDepth = deadlines.size();
                Runnable callback = connection.deadlineAction;
                connection.deadlineAction = null;
                try { callback.run(); }
                catch (RuntimeException error) { connection.terminate(CloseReason.HANDLER_ERROR); }
            }
            for (int i = 0; i < COMMAND_BUDGET && !stalled.isEmpty(); i++) {
                Connection connection = stalled.peek();
                if (now - connection.stallDeadline < 0) break;
                stalled.remove(connection);
                stalledDepth = stalled.size();
                connection.stallDeadline = 0;
                writeTimeouts++;
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
                registered++;
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
            for (;;) {
                try { if (!processCommand(true)) break; }
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
            stats = new OwnerStats(receivePool == null ? EMPTY_POOL_STATS : receivePool.stats(),
                    sendPool == null ? EMPTY_POOL_STATS : sendPool.stats(), admitted.get(), pendingCommands(),
                    zeroWrites, callbackFailures);
            receivePool = sendPool = null;
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
            } else if (!owner.offer(channel, null)) {
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
