import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.foreign.ValueLayout.*;

/** Linux x86-64 benchmark model with one completion source shared by all I/O workers. */
public final class NcFfmCore {
    // Linux packs epoll_event to 12 bytes on x86-64, unaligned epoll_data included.
    private static final int EVENT_SIZE = 12, EVENT_BATCH = 64, ACCEPT_BATCH = 32;
    private static final int FRAME_BATCH = 8, SERVICE_TURNS = 4;
    private static final int IN = 1, OUT = 4, ONE_SHOT = 1 << 30;
    private static final int ADD = 1, MODIFY = 3, NONBLOCK = 0x800, CLOEXEC = 0x80000;
    private static final int EINTR = 4, EAGAIN = 11, EPIPE = 32, ECONNABORTED = 103, ECONNRESET = 104;
    private static final int MSG_NOSIGNAL = 0x4000;
    private static final Linker LINKER = Linker.nativeLinker();
    private static final Linker.Option ERRNO = Linker.Option.captureCallState("errno");
    private static final long ERRNO_OFFSET = Linker.Option.captureStateLayout().byteOffset(
            java.lang.foreign.MemoryLayout.PathElement.groupElement("errno"));

    private static MethodHandle bind(String name, ValueLayout result, ValueLayout... args) {
        return LINKER.downcallHandle(LINKER.defaultLookup().find(name).orElseThrow(),
                FunctionDescriptor.of(result, args), ERRNO);
    }

    private static final MethodHandle SOCKET = bind("socket", JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT);
    private static final MethodHandle BIND = bind("bind", JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT);
    private static final MethodHandle LISTEN = bind("listen", JAVA_INT, JAVA_INT, JAVA_INT);
    private static final MethodHandle GETSOCKNAME = bind("getsockname", JAVA_INT, JAVA_INT, ADDRESS, ADDRESS);
    private static final MethodHandle ACCEPT = bind("accept4", JAVA_INT, JAVA_INT, ADDRESS, ADDRESS, JAVA_INT);
    private static final MethodHandle SOCKOPT = bind("setsockopt", JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT);
    private static final MethodHandle EPOLL = bind("epoll_create1", JAVA_INT, JAVA_INT);
    private static final MethodHandle CTL = bind("epoll_ctl", JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS);
    private static final MethodHandle WAIT = bind("epoll_wait", JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT);
    private static final MethodHandle RECV = bind("recv", JAVA_LONG, JAVA_INT, ADDRESS, JAVA_LONG, JAVA_INT);
    private static final MethodHandle SEND = bind("send", JAVA_LONG, JAVA_INT, ADDRESS, JAVA_LONG, JAVA_INT);
    private static final MethodHandle CLOSE = bind("close", JAVA_INT, JAVA_INT);

    private static final class NativeScratch {
        final MemorySegment errno, event;

        NativeScratch(Arena arena) {
            errno = arena.allocate(Linker.Option.captureStateLayout());
            event = arena.allocate(EVENT_SIZE, 8);
        }

        int error() {
            return errno.get(JAVA_INT, ERRNO_OFFSET);
        }

        int checked(String operation, int result) {
            if (result < 0) throw new IllegalStateException(operation + " errno=" + error());
            return result;
        }

        void interest(int epoll, int operation, int fd, int flags, long token) throws Throwable {
            event.set(JAVA_INT_UNALIGNED, 0, flags | ONE_SHOT);
            event.set(JAVA_LONG_UNALIGNED, 4, token);
            checked("epoll_ctl", (int) CTL.invokeExact(errno, epoll, operation, fd, event));
        }

        void close(int fd) throws Throwable {
            int result = (int) CLOSE.invokeExact(errno, fd);
            // Linux releases the descriptor even if close reports EINTR.
            if (result < 0 && error() != EINTR) checked("close", result);
        }
    }

    private static final class Session {
        final int slot;
        final MemorySegment memory;
        final ByteBuffer[] frameViews = new ByteBuffer[FRAME_BATCH];
        int fd = -1, generation, sequence;
        int received, responseEnd, sent;

        Session(int slot, int frameSize, MemorySegment memory) {
            this.slot = slot;
            this.memory = memory;
            for (int i = 0; i < frameViews.length; i++) {
                frameViews[i] = memory.asSlice((long) i * frameSize, frameSize)
                        .asByteBuffer().order(ByteOrder.LITTLE_ENDIAN);
            }
        }

        long token() {
            return ((long) generation << 32) | (slot + 1L);
        }
    }

    private final class Worker implements Runnable {
        long accepted, closed, frames;

        @Override
        public void run() {
            try (Arena arena = Arena.ofConfined()) {
                NativeScratch nativeState = new NativeScratch(arena);
                MemorySegment events = arena.allocate((long) EVENT_BATCH * EVENT_SIZE, 8);
                MemorySegment noDelay = arena.allocate(JAVA_INT);
                noDelay.set(JAVA_INT, 0, 1);
                while (!stopping) {
                    int count = (int) WAIT.invokeExact(nativeState.errno, epoll, events, EVENT_BATCH, 100);
                    if (count < 0 && nativeState.error() == EINTR) continue;
                    nativeState.checked("epoll_wait", count);
                    for (int i = 0; i < count && !stopping; i++) {
                        long token = events.get(JAVA_LONG_UNALIGNED, (long) i * EVENT_SIZE + 4);
                        if (token == 0) acceptReady(nativeState, noDelay);
                        else sessionReady(nativeState, token);
                    }
                }
            } catch (Throwable problem) {
                failure.compareAndSet(null, problem);
                stopping = true;
            }
        }

        private void acceptReady(NativeScratch scratch, MemorySegment noDelay) throws Throwable {
            // The listen socket itself is one-shot. Workers can accept on the same
            // descriptor over time, with at most one accept batch active.
            for (int i = 0; i < ACCEPT_BATCH && !stopping; i++) {
                int fd = (int) ACCEPT.invokeExact(scratch.errno, listener,
                        MemorySegment.NULL, MemorySegment.NULL, NONBLOCK | CLOEXEC);
                if (fd < 0) {
                    int error = scratch.error();
                    if (error == EINTR || error == ECONNABORTED) continue;
                    if (error == EAGAIN) break;
                    scratch.checked("accept4", fd);
                }
                Session session = takeSlot();
                if (session == null) {
                    scratch.close(fd);
                    throw new IllegalStateException("benchmark connection capacity reached");
                }
                synchronized (session) {
                    session.fd = fd;
                    session.generation++;
                    session.sequence = 0;
                    session.received = session.responseEnd = session.sent = 0;
                    accepted++;
                    scratch.checked("TCP_NODELAY", (int) SOCKOPT.invokeExact(
                            scratch.errno, fd, 6, 1, noDelay, 4));
                    scratch.interest(epoll, ADD, fd, IN, session.token());
                }
            }
            scratch.interest(epoll, MODIFY, listener, IN, 0);
        }

        private void sessionReady(NativeScratch scratch, long token) throws Throwable {
            int slot = (int) token - 1;
            Session session = sessions[slot];
            // ONESHOT serializes kernel notifications. The monitor also publishes
            // Java session fields before a different completion worker re-enters.
            synchronized (session) {
                if (session.fd < 0 || session.token() != token) return;
                for (int i = 0; i < SERVICE_TURNS; i++) {
                    if (session.sent < session.responseEnd) {
                        if (!write(scratch, session)) break;
                        if (session.sent < session.responseEnd) continue;
                        int tail = session.received - session.responseEnd;
                        if (tail != 0) {
                            MemorySegment.copy(session.memory, session.responseEnd, session.memory, 0, tail);
                        }
                        session.received = tail;
                        session.responseEnd = session.sent = 0;
                    }
                    if (!read(scratch, session)) break;
                }
                if (session.fd >= 0) {
                    scratch.interest(epoll, MODIFY, session.fd,
                            session.sent < session.responseEnd ? OUT : IN, token);
                }
            }
        }

        private boolean read(NativeScratch scratch, Session session) throws Throwable {
            int offset = session.received;
            MemorySegment target = offset == 0 ? session.memory : session.memory.asSlice(offset);
            long n = (long) RECV.invokeExact(scratch.errno, session.fd, target,
                    session.memory.byteSize() - offset, 0);
            if (n < 0) return handleIoError(scratch, session, "recv");
            if (n == 0) {
                retire(scratch, session);
                return false;
            }
            session.received += (int) n;
            int ready = session.received / frameSize;
            for (int i = 0; i < ready; i++) {
                ByteBuffer frame = session.frameViews[i];
                try {
                    CoreProtocol.handle(frame, session.sequence);
                    session.sequence++;
                } catch (IllegalArgumentException invalidFrame) {
                    retire(scratch, session);
                    return false;
                }
            }
            session.responseEnd = ready * frameSize;
            return true;
        }

        private boolean write(NativeScratch scratch, Session session) throws Throwable {
            int offset = session.sent;
            MemorySegment source = offset == 0 ? session.memory : session.memory.asSlice(offset);
            long n = (long) SEND.invokeExact(scratch.errno, session.fd, source,
                    (long) (session.responseEnd - offset), MSG_NOSIGNAL);
            if (n < 0) return handleIoError(scratch, session, "send");
            if (n == 0) {
                retire(scratch, session);
                return false;
            }
            session.sent += (int) n;
            frames += session.sent / frameSize - offset / frameSize;
            return true;
        }

        private boolean handleIoError(NativeScratch scratch, Session session, String operation) throws Throwable {
            int error = scratch.error();
            if (error == EINTR) return true;
            if (error == EAGAIN) return false;
            if (error == EPIPE || error == ECONNRESET) {
                retire(scratch, session);
                return false;
            }
            throw new IllegalStateException(operation + " errno=" + error);
        }

        private void retire(NativeScratch scratch, Session session) throws Throwable {
            int fd = session.fd;
            session.fd = -1;
            scratch.close(fd);
            closed++;
            putSlot(session);
        }
    }

    private final Session[] sessions;
    private final int[] free;
    private final int frameSize;
    private final AtomicReference<Throwable> failure = new AtomicReference<>();
    private final Worker[] workers;
    private final Thread[] threads;
    private int freeCount, listener = -1, epoll = -1;
    private volatile boolean stopping;

    private NcFfmCore(Arena arena, int frameSize, int capacity, int workerCount) {
        this.frameSize = frameSize;
        long sessionBytes = (long) frameSize * FRAME_BATCH;
        MemorySegment slab = arena.allocate(sessionBytes * capacity, 8);
        sessions = new Session[capacity];
        free = new int[capacity];
        for (int i = 0; i < capacity; i++) {
            free[i] = i;
            sessions[i] = new Session(i, frameSize, slab.asSlice(sessionBytes * i, sessionBytes));
        }
        freeCount = capacity;
        workers = new Worker[workerCount];
        threads = new Thread[workerCount];
        for (int i = 0; i < workerCount; i++) {
            workers[i] = new Worker();
            threads[i] = Thread.ofPlatform().name("nc-ffm-worker-" + i).unstarted(workers[i]);
        }
    }

    private Session takeSlot() {
        synchronized (free) {
            return freeCount == 0 ? null : sessions[free[--freeCount]];
        }
    }

    private void putSlot(Session session) {
        synchronized (free) {
            free[freeCount++] = session.slot;
        }
    }

    private int listen(NativeScratch scratch, Arena arena) throws Throwable {
        epoll = scratch.checked("epoll_create1", (int) EPOLL.invokeExact(scratch.errno, CLOEXEC));
        listener = scratch.checked("socket", (int) SOCKET.invokeExact(scratch.errno, 2, 1 | NONBLOCK | CLOEXEC, 0));
        MemorySegment addr = arena.allocate(16, 4);
        addr.set(JAVA_SHORT, 0, (short) 2);
        addr.set(JAVA_INT.withOrder(ByteOrder.BIG_ENDIAN), 4, 0x7f000001);
        scratch.checked("bind", (int) BIND.invokeExact(scratch.errno, listener, addr, 16));
        scratch.checked("listen", (int) LISTEN.invokeExact(scratch.errno, listener, 256));
        MemorySegment size = arena.allocate(JAVA_INT);
        size.set(JAVA_INT, 0, 16);
        scratch.checked("getsockname", (int) GETSOCKNAME.invokeExact(scratch.errno, listener, addr, size));
        scratch.interest(epoll, ADD, listener, IN, 0);
        return Short.toUnsignedInt(addr.get(JAVA_SHORT.withOrder(ByteOrder.BIG_ENDIAN), 2));
    }

    private void reportAndClose(NativeScratch scratch) throws Throwable {
        long accepted = 0, closed = 0, frames = 0;
        StringBuilder breakdown = new StringBuilder();
        for (Worker worker : workers) {
            if (!breakdown.isEmpty()) breakdown.append(',');
            breakdown.append(worker.frames);
            accepted += worker.accepted;
            closed += worker.closed;
            frames += worker.frames;
        }
        for (Session session : sessions) {
            if (session.fd < 0) continue;
            scratch.close(session.fd);
            session.fd = -1;
            closed++;
            putSlot(session);
        }
        if (listener >= 0) scratch.close(listener);
        if (epoll >= 0) scratch.close(epoll);
        if (failure.get() != null) throw failure.get();
        if (accepted != closed || freeCount != sessions.length) throw new IllegalStateException("slot accounting");
        System.err.println("WORKERS frames=" + breakdown);
        System.out.printf("CLOSED accepted=%d closed=%d frames=%d%n", accepted, closed, frames);
    }

    public static void main(String[] args) throws Throwable {
        if (!System.getProperty("os.name").equals("Linux") || !System.getProperty("os.arch").equals("amd64"))
            throw new IllegalStateException("requires x86-64 Linux");
        int size = Integer.parseInt(args[0]), capacity = Integer.parseInt(args[1]);
        int workerCount = args.length < 3 ? 2 : Integer.parseInt(args[2]);
        if (size != CoreProtocol.FRAME_BYTES || capacity < 1 || workerCount < 1)
            throw new IllegalArgumentException("invalid benchmark frame, capacity or worker count");
        try (Arena shared = Arena.ofShared(); Arena bootstrap = Arena.ofConfined()) {
            NcFfmCore server = new NcFfmCore(shared, size, capacity, workerCount);
            NativeScratch scratch = new NativeScratch(bootstrap);
            try {
                int port = server.listen(scratch, bootstrap);
                Thread.ofPlatform().daemon().name("bench-control").start(() -> {
                    try (var in = new BufferedReader(new InputStreamReader(System.in))) {
                        for (String s; (s = in.readLine()) != null; ) if (s.equals("STOP")) break;
                    } catch (java.io.IOException ignored) { }
                    finally { server.stopping = true; }
                });
                for (Thread thread : server.threads) thread.start();
                System.out.println("READY " + port);
                for (Thread thread : server.threads) thread.join();
            } finally {
                server.stopping = true;
                for (Thread thread : server.threads) thread.join();
                server.reportAndClose(scratch);
            }
        }
    }
}
