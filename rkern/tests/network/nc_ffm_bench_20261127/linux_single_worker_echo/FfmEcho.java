import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.ByteOrder;
import java.util.Arrays;

import static java.lang.foreign.ValueLayout.*;

public final class FfmEcho {
    // Linux x86-64 glibc ABI: epoll_event is a packed 12-byte struct.
    private static final int EVENT_SIZE = 12, EVENT_LIMIT = 256;
    private static final int IN = 1, OUT = 4, ERROR = 8, HUP = 16;
    private static final int ADD = 1, MODIFY = 3, NONBLOCK = 0x800, CLOEXEC = 0x80000;
    private static final int EINTR = 4, EAGAIN = 11, EPIPE = 32, ECONNRESET = 104;
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

    private final Arena arena = Arena.ofConfined();
    private final MemorySegment errno = arena.allocate(Linker.Option.captureStateLayout());
    private final MemorySegment events = arena.allocate((long)EVENT_LIMIT * EVENT_SIZE, 8);
    private final MemorySegment event = arena.allocate(EVENT_SIZE, 8);
    private final MemorySegment one = arena.allocate(JAVA_INT);
    private final MemorySegment[] buffers;
    private final int frameSize;
    private final int[] fds, free, generation, received, sent;
    private final boolean[] awaitingWrite;
    private int freeCount, listener = -1, poller = -1;
    private long accepted, closed, frames, invalidFrames;
    private volatile boolean stopping;

    private FfmEcho(int size, int capacity) {
        frameSize = size;
        buffers = new MemorySegment[capacity];
        MemorySegment slab = arena.allocate((long) size * capacity, 8);
        fds = new int[capacity];
        Arrays.fill(fds, -1);
        free = new int[capacity];
        generation = new int[capacity];
        received = new int[capacity];
        sent = new int[capacity];
        awaitingWrite = new boolean[capacity];
        for (int i = 0; i < capacity; i++) {
            free[i] = i;
            buffers[i] = slab.asSlice((long)i * size, size);
        }
        freeCount = capacity;
        one.set(JAVA_INT, 0, 1);
    }

    private int error() { return errno.get(JAVA_INT, ERRNO_OFFSET); }

    private int checked(String operation, int result) {
        if (result < 0) throw new IllegalStateException(operation + " errno=" + error());
        return result;
    }

    private void interest(int operation, int fd, int flags, long token) throws Throwable {
        event.set(JAVA_INT_UNALIGNED, 0, flags);
        event.set(JAVA_LONG_UNALIGNED, 4, token);
        checked("epoll_ctl", (int)CTL.invokeExact(errno, poller, operation, fd, event));
    }

    private int start() throws Throwable {
        poller = checked("epoll_create1", (int)EPOLL.invokeExact(errno, CLOEXEC));
        listener = checked("socket", (int)SOCKET.invokeExact(errno, 2, 1 | NONBLOCK | CLOEXEC, 0));
        MemorySegment addr = arena.allocate(16, 4);
        addr.set(JAVA_SHORT, 0, (short)2);
        addr.set(JAVA_INT.withOrder(ByteOrder.BIG_ENDIAN), 4, 0x7f000001);
        checked("bind", (int)BIND.invokeExact(errno, listener, addr, 16));
        checked("listen", (int)LISTEN.invokeExact(errno, listener, 256));
        MemorySegment size = arena.allocate(JAVA_INT);
        size.set(JAVA_INT, 0, 16);
        checked("getsockname", (int)GETSOCKNAME.invokeExact(errno, listener, addr, size));
        interest(ADD, listener, IN, 0);
        return Short.toUnsignedInt(addr.get(JAVA_SHORT.withOrder(ByteOrder.BIG_ENDIAN), 2));
    }

    private long token(int slot) { return ((long)generation[slot] << 32) | (slot + 1L); }

    private void acceptReady() throws Throwable {
        for (int count = 0; count < 32; count++) {
            int fd = (int)ACCEPT.invokeExact(errno, listener, MemorySegment.NULL, MemorySegment.NULL, NONBLOCK | CLOEXEC);
            if (fd < 0) {
                if (error() == EINTR) continue;
                if (error() == EAGAIN) return;
                checked("accept4", fd);
            }
            if (freeCount == 0) {
                checked("close excess", (int)CLOSE.invokeExact(errno, fd));
                throw new IllegalStateException("benchmark connection capacity reached");
            }
            int slot = free[--freeCount];
            fds[slot] = fd;
            generation[slot]++;
            received[slot] = sent[slot] = 0;
            awaitingWrite[slot] = false;
            accepted++;
            checked("TCP_NODELAY", (int)SOCKOPT.invokeExact(errno, fd, 6, 1, one, 4));
            interest(ADD, fd, IN, token(slot));
        }
    }

    private void remove(int slot) throws Throwable {
        // Closing this fd removes it from epoll. Generation rejects stale events on slot reuse.
        int fd = fds[slot];
        fds[slot] = -1;
        free[freeCount++] = slot;
        closed++;
        checked("close connection", (int)CLOSE.invokeExact(errno, fd));
    }

    private int ioError(String op, int slot) throws Throwable {
        int e = error();
        if (e == EINTR || e == EAGAIN) return e;
        if (e == EPIPE || e == ECONNRESET) { remove(slot); return e; }
        throw new IllegalStateException(op + " errno=" + e);
    }

    private void transfer(int slot) throws Throwable {
        MemorySegment buffer = buffers[slot];
        if (received[slot] < frameSize) {
            int offset = received[slot];
            MemorySegment target = offset == 0 ? buffer : buffer.asSlice(offset);
            long n = (long)RECV.invokeExact(errno, fds[slot], target,
                    (long)(frameSize - offset), 0);
            if (n == 0) { remove(slot); return; }
            if (n < 0) { ioError("recv", slot); return; }
            received[slot] += (int)n;
            if (received[slot] < frameSize) return;
            if (Short.toUnsignedInt(buffer.get(JAVA_SHORT_UNALIGNED, 0)) != frameSize) {
                invalidFrames++;
                remove(slot);
                return;
            }
        }
        int offset = sent[slot];
        MemorySegment source = offset == 0 ? buffer : buffer.asSlice(offset);
        long n;
        do {
            n = (long)SEND.invokeExact(errno, fds[slot], source,
                    (long)(frameSize - offset), MSG_NOSIGNAL);
        } while (n < 0 && error() == EINTR);
        if (n < 0 && ioError("send", slot) != EAGAIN) return;
        if (n == 0) throw new IllegalStateException("zero byte send");
        if (n > 0) sent[slot] += (int)n;
        if (sent[slot] < frameSize) {
            if (!awaitingWrite[slot]) {
                interest(MODIFY, fds[slot], OUT, token(slot));
                awaitingWrite[slot] = true;
            }
        } else {
            if (awaitingWrite[slot]) {
                interest(MODIFY, fds[slot], IN, token(slot));
                awaitingWrite[slot] = false;
            }
            received[slot] = sent[slot] = 0;
            frames++;
        }
    }

    private void loop() throws Throwable {
        while (!stopping) {
            int n = (int)WAIT.invokeExact(errno, poller, events, EVENT_LIMIT, 100);
            if (n < 0 && error() == EINTR) continue;
            checked("epoll_wait", n);
            for (int i = 0; i < n; i++) {
                long offset = (long)i * EVENT_SIZE;
                long key = events.get(JAVA_LONG_UNALIGNED, offset + 4);
                if (key == 0) { acceptReady(); continue; }
                int slot = (int)key - 1;
                if (fds[slot] == -1 || key != token(slot)) continue;
                int flags = events.get(JAVA_INT_UNALIGNED, offset);
                if ((flags & (IN | OUT | HUP | ERROR)) != 0) transfer(slot);
            }
        }
    }

    private void release() throws Throwable {
        try {
            for (int i = 0; i < fds.length; i++) if (fds[i] >= 0) remove(i);
            if (listener >= 0) checked("close listener", (int)CLOSE.invokeExact(errno, listener));
            if (poller >= 0) checked("close epoll", (int)CLOSE.invokeExact(errno, poller));
            if (accepted != closed || freeCount != fds.length) throw new IllegalStateException("slot accounting");
        } finally { arena.close(); }
    }

    public static void main(String[] args) throws Throwable {
        if (!System.getProperty("os.name").equals("Linux") || !System.getProperty("os.arch").equals("amd64"))
            throw new IllegalStateException("requires x86-64 Linux");
        int size = Integer.parseInt(args[0]), capacity = Integer.parseInt(args[1]);
        if (size < 2 || size > 65535 || capacity < 1) throw new IllegalArgumentException();
        FfmEcho server = new FfmEcho(size, capacity);
        try {
            int port = server.start();
            Thread.ofPlatform().daemon().name("bench-control").start(() -> {
                try (var in = new BufferedReader(new InputStreamReader(System.in))) {
                    for (String s; (s = in.readLine()) != null; ) if (s.equals("STOP")) break;
                } catch (java.io.IOException ignored) { }
                finally { server.stopping = true; }
            });
            System.out.println("READY " + port);
            server.loop();
        } finally {
            server.release();
        }
        if (server.invalidFrames != 0) throw new IllegalStateException("invalid frames: " + server.invalidFrames);
        System.out.printf("CLOSED accepted=%d closed=%d frames=%d%n", server.accepted, server.closed, server.frames);
    }
}
