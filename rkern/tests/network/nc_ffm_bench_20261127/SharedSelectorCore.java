import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;

import l1j.server.server.model.network.core.transport.selector.CSelectorTransport;

/** Two hosted Selector owners also act as the benchmark's shared packet workers. */
public final class SharedSelectorCore {
    private static final int OWNERS = 2;
    private static final int BATCH = 8;
    private static final int WORK_BUDGET = 64;

    public static void main(String[] args) throws Exception {
        if ((args.length != 2 && args.length != 3) || (args.length == 3 && Integer.parseInt(args[2]) != OWNERS))
            throw new IllegalArgumentException("SharedSelectorCore <frameBytes> <maxClients> [2]");
        int wireBytes = Integer.parseInt(args[0]);
        int clients = Integer.parseInt(args[1]);
        if (wireBytes != CoreProtocol.FRAME_BYTES || clients < 1 || clients > 100_000)
            throw new IllegalArgumentException("Invalid frame size or connection capacity");
        int capacity = Math.max(128, (clients + OWNERS - 1) / OWNERS + 32);
        var work = new SharedWork(capacity);
        var config = new CSelectorTransport.Config(OWNERS, capacity, 256, capacity * 2,
                1, 1, 1, 256 * 1024, 256, 0, Duration.ofSeconds(5));
        try (var server = CSelectorTransport.bindHosted(new InetSocketAddress("127.0.0.1", 0), config, work, work)) {
            System.out.println("READY " + server.localAddress().getPort());
            String command = new BufferedReader(new InputStreamReader(System.in)).readLine();
            if (command != null && !command.equals("STOP"))
                throw new IllegalArgumentException("Unexpected control message");
            server.close();
            work.verifyReleased();
            for (var stats : server.shutdownStats()) {
                if (stats.remainingConnections() != 0 || stats.remainingCommands() != 0)
                    throw new IllegalStateException("Outstanding Selector connections or mailbox work");
            }
            work.report();
        }
    }

    private static final class SharedWork implements CSelectorTransport.RawHandler, CSelectorTransport.OwnerHost {
        final ArrayBlockingQueue<Session> ready;
        final Worker[] workers = new Worker[OWNERS];
        final AtomicReference<Throwable> failure = new AtomicReference<>();

        SharedWork(int capacity) {
            ready = new ArrayBlockingQueue<>(OWNERS * capacity);
            for (int i = 0; i < OWNERS; i++) workers[i] = new Worker(this, i, capacity);
        }

        @Override public void attach(List<CSelectorTransport.OwnerDriver> drivers) {
            if (drivers.size() != OWNERS) throw new IllegalArgumentException("Expected two Selector owners");
            for (Worker worker : workers) worker.driver = drivers.get(worker.id);
            for (Worker worker : workers) worker.thread.start();
        }

        @Override public void onOpen(CSelectorTransport.Connection connection) {
            Worker owner = workers[connection.ownerIndex()];
            owner.opened++;
            ByteBuffer buffer = owner.borrow();
            if (buffer == null) {
                connection.abort();
                return;
            }
            buffer.clear();
            var session = new Session(owner, connection, buffer);
            owner.sessions.put(connection, session);
            session.read();
        }

        @Override public void onClosed(CSelectorTransport.Connection connection, CSelectorTransport.CloseReason reason) {
            Worker owner = workers[connection.ownerIndex()];
            Session session = owner.sessions.remove(connection);
            owner.closed++;
            if (session != null) session.close();
        }

        boolean offer(Session session) {
            if (!ready.offer(session)) return false;
            Worker other = workers[1 - session.owner.id];
            if (other.waiting) other.driver.wakeup();
            return true;
        }

        void verifyReleased() throws InterruptedException {
            final long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            for (Worker worker : workers) {
                long millis = Math.max(1L, (deadline - System.nanoTime()) / 1_000_000L);
                worker.thread.join(millis);
                if (worker.thread.isAlive()) throw new IllegalStateException("Packet worker did not stop");
            }
            Throwable workerFailure = failure.get();
            if (workerFailure != null) throw new IllegalStateException("Shared Selector worker failed", workerFailure);
            if (!ready.isEmpty()) throw new IllegalStateException("Pending shared packet jobs");
            for (Worker worker : workers) {
                if (!worker.sessions.isEmpty() || worker.free.size() != worker.allocated || worker.opened != worker.closed)
                    throw new IllegalStateException("Unreleased shared packet buffer");
            }
        }

        void report() {
            Worker a = workers[0], b = workers[1];
            long accepted = a.opened + b.opened;
            long frames = a.frames + b.frames;
            if (accepted >= 128 && frames >= 8 * accepted
                    && (a.handled == 0 || b.handled == 0 || a.foreign == 0 || b.foreign == 0))
                throw new IllegalStateException("Shared workers did not cross Selector owners during load");
            System.out.printf("CLOSED accepted=%d closed=%d frames=%d handled0=%d foreign0=%d handled1=%d foreign1=%d%n",
                    accepted, a.closed + b.closed, frames, a.handled, a.foreign, b.handled, b.foreign);
        }
    }

    private static final class Worker implements Runnable {
        final SharedWork work;
        final int id;
        final int bufferLimit;
        final Thread thread;
        final Map<CSelectorTransport.Connection, Session> sessions = new IdentityHashMap<>();
        final ArrayDeque<ByteBuffer> free = new ArrayDeque<>();
        CSelectorTransport.OwnerDriver driver;
        volatile boolean waiting;
        int allocated;
        long opened, closed, frames, handled, foreign;

        Worker(SharedWork work, int id, int capacity) {
            this.work = work;
            this.id = id;
            this.bufferLimit = capacity * 2;
            thread = Thread.ofPlatform().daemon().name("shared-selector-" + id).unstarted(this);
        }

        synchronized ByteBuffer borrow() {
            ByteBuffer buffer = free.pollFirst();
            if (buffer != null || allocated == bufferLimit) return buffer;
            buffer = ByteBuffer.allocateDirect(BATCH * CoreProtocol.FRAME_BYTES);
            allocated++;
            return buffer;
        }

        synchronized void recycle(ByteBuffer buffer) {
            free.addLast(buffer);
        }

        @Override public void run() {
            try {
                for (;;) {
                    driver.advance();
                    for (int i = 0; i < WORK_BUDGET; i++) {
                        Session session = work.ready.poll();
                        if (session == null) break;
                        session.handle(this);
                    }
                    if (!driver.active()) {
                        if (work.ready.isEmpty()) break;
                        continue;
                    }
                    waiting = true;
                    try {
                        if (work.ready.isEmpty()) driver.awaitWork(25);
                    } finally {
                        waiting = false;
                    }
                }
            } catch (Throwable unexpected) {
                work.failure.compareAndSet(null, unexpected);
            } finally {
                if (driver.active()) driver.closeOnOwner();
            }
        }
    }

    private static final class Session {
        private static final int PACKET = 1, CLOSED = 2;
        private static final AtomicIntegerFieldUpdater<Session> LEASE =
                AtomicIntegerFieldUpdater.newUpdater(Session.class, "lease");

        final Worker owner;
        final CSelectorTransport.Connection connection;
        final ByteBuffer buffer;
        final IntConsumer readCompletion = this::onRead;
        final LongConsumer writeCompletion = this::onWrite;
        final CSelectorTransport.OwnerCommand complete = new CSelectorTransport.OwnerCommand() {
            @Override public void run(CSelectorTransport.Connection ignored) {
                releasePacket();
                if (validReply) connection.writeRaw(buffer, writeCompletion);
                else connection.abort();
            }

            @Override public void cancel(CSelectorTransport.Connection ignored) {
                releasePacket();
            }
        };
        @SuppressWarnings("unused")
        private volatile int lease;
        private int sequence;
        private int receivedEnd;
        private int replyFrames;
        private boolean validReply;

        Session(Worker owner, CSelectorTransport.Connection connection, ByteBuffer buffer) {
            this.owner = owner;
            this.connection = connection;
            this.buffer = buffer;
        }

        void read() {
            connection.readRaw(buffer, readCompletion);
        }

        private void onRead(int bytes) {
            if (bytes <= 0) return;
            receivedEnd = buffer.position();
            replyFrames = receivedEnd / CoreProtocol.FRAME_BYTES;
            if (replyFrames == 0) {
                read();
                return;
            }
            buffer.limit(replyFrames * CoreProtocol.FRAME_BYTES).position(0);
            if (!LEASE.compareAndSet(this, 0, PACKET)) throw new IllegalStateException("Packet lease already held");
            if (!owner.work.offer(this)) {
                releasePacket();
                connection.abort();
            }
        }

        void handle(Worker worker) {
            if (lease == (PACKET | CLOSED)) {
                releasePacket();
                return;
            }
            try {
                validReply = true;
                int replyEnd = replyFrames * CoreProtocol.FRAME_BYTES;
                for (int offset = 0; offset < replyEnd; offset += CoreProtocol.FRAME_BYTES) {
                    CoreProtocol.handleAt(buffer, offset, sequence);
                    sequence++;
                    worker.handled++;
                    if (worker != owner) worker.foreign++;
                }
            } catch (IllegalArgumentException invalidRequest) {
                validReply = false;
            } catch (RuntimeException | Error unexpected) {
                releasePacket();
                connection.requestClose(false);
                throw unexpected;
            }
            if (!connection.tryExecute(complete)) {
                releasePacket();
                connection.requestClose(false);
            }
        }

        private void onWrite(long bytes) {
            if (bytes < 0) return;
            if (buffer.hasRemaining()) {
                connection.writeRaw(buffer, writeCompletion);
                return;
            }
            owner.frames += replyFrames;
            buffer.limit(receivedEnd).compact();
            read();
        }

        void close() {
            int previous = LEASE.getAndUpdate(this, value -> value | CLOSED);
            if (previous == 0) owner.recycle(buffer);
        }

        private void releasePacket() {
            int previous = LEASE.getAndUpdate(this, value -> value & ~PACKET);
            if (previous == (PACKET | CLOSED)) owner.recycle(buffer);
            else if (previous != PACKET) throw new IllegalStateException("Packet lease already released");
        }
    }
}
