import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.atomic.LongAdder;

import static java.lang.foreign.MemorySegment.NULL;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

/** Windows x64 benchmark: independent overlapped read/write with two ordered batch buffers. */
final class WinIocpCore {
    private static final long INVALID_SOCKET = -1;
    private static final int WSA_IO_PENDING = 997, WSA_EWOULDBLOCK = 10035;
    private static final int WAIT_TIMEOUT = 258;
    private static final int FIONBIO = 0x8004667e, POLLRDNORM = 0x0100;
    private static final int FRAME = CoreProtocol.FRAME_BYTES;
    private static final int BATCH = 8, BATCH_BYTES = BATCH * FRAME;
    private static final int READ_BUF_OFFSET = 2 * BATCH_BYTES, WRITE_BUF_OFFSET = READ_BUF_OFFSET + 16;
    private static final int READ_OVL_OFFSET = WRITE_BUF_OFFSET + 16, WRITE_OVL_OFFSET = READ_OVL_OFFSET + 32;
    private static final int FLAGS_OFFSET = WRITE_OVL_OFFSET + 32, SESSION_BYTES = FLAGS_OFFSET + 8;

    private final Native api;
    private final int capacity;
    private final MemorySegment port, buffers;
    private final long listener;
    private final int localPort;
    private final AtomicReferenceArray<Session> sessions;
    private final ArrayBlockingQueue<Integer> freeSlots;
    private final AtomicInteger accepted = new AtomicInteger(), closed = new AtomicInteger();
    private final LongAdder frames = new LongAdder();
    private final Phaser outstanding = new Phaser(1);
    private final AtomicReference<Throwable> failure = new AtomicReference<>();
    private final Thread acceptor;
    private final Thread[] workers;
    private volatile boolean stopping, stopWorkers;
    private boolean drained;

    private WinIocpCore(Native api, Arena memory, int capacity, int workerCount) throws Throwable {
        this.api = api;
        this.capacity = capacity;
        sessions = new AtomicReferenceArray<>(capacity);
        freeSlots = new ArrayBlockingQueue<>(capacity);
        for (int i = 0; i < capacity; i++) freeSlots.add(i);
        buffers = memory.allocate((long) capacity * SESSION_BYTES, 8);
        workers = new Thread[workerCount];

        try (Arena local = Arena.ofConfined()) {
            final MemorySegment error = local.allocate(api.state);
            final long created = (long) api.socket.invokeExact(error, 2, 1, 6, NULL, 0, 1);
            if (created == INVALID_SOCKET) throw nativeError("WSASocketW", api.wsaError(error));
            MemorySegment createdPort = NULL;
            try {
                final MemorySegment address = local.allocate(16, 4);
                address.set(JAVA_SHORT, 0, (short) 2);
                address.set(JAVA_BYTE, 4, (byte) 127);
                address.set(JAVA_BYTE, 7, (byte) 1);
                if ((int) api.bind.invokeExact(error, created, address, 16) != 0)
                    throw nativeError("bind", api.wsaError(error));
                final MemorySegment length = local.allocate(JAVA_INT);
                length.set(JAVA_INT, 0, 16);
                if ((int) api.getsockname.invokeExact(error, created, address, length) != 0)
                    throw nativeError("getsockname", api.wsaError(error));
                localPort = Short.toUnsignedInt(address.get(JAVA_SHORT.withOrder(ByteOrder.BIG_ENDIAN), 2));
                if ((int) api.listen.invokeExact(error, created, capacity) != 0)
                    throw nativeError("listen", api.wsaError(error));
                length.set(JAVA_INT, 0, 1);
                if ((int) api.ioctl.invokeExact(error, created, FIONBIO, length) != 0)
                    throw nativeError("ioctlsocket", api.wsaError(error));
                createdPort = (MemorySegment) api.iocp.invokeExact(error, MemorySegment.ofAddress(-1), NULL,
                        0L, workerCount);
                if (createdPort.address() == 0) throw nativeError("CreateIoCompletionPort", api.winError(error));
            } catch (Throwable setup) {
                api.closeSocket(error, created);
                if (createdPort.address() != 0) api.closeHandle(createdPort);
                throw setup;
            }
            listener = created;
            port = createdPort;
        }
        acceptor = daemon("WinIOCP-Accept", this::accept);
        for (int i = 0; i < workers.length; i++) workers[i] = daemon("WinIOCP-Completion-" + i, this::work);
    }

    private static Thread daemon(String name, Runnable run) {
        final Thread thread = new Thread(run, name);
        thread.setDaemon(true);
        return thread;
    }

    private void start() {
        for (Thread worker : workers) worker.start();
        acceptor.start();
    }

    private void accept() {
        try (Arena local = Arena.ofConfined()) {
            final MemorySegment error = local.allocate(api.state);
            final MemorySegment poll = local.allocate(16, 8);
            final MemorySegment option = local.allocate(JAVA_INT);
            poll.set(JAVA_LONG, 0, listener);
            poll.set(JAVA_SHORT, 8, (short) POLLRDNORM);
            option.set(JAVA_INT, 0, 1);
            while (!stopping) {
                final int available = (int) api.poll.invokeExact(error, poll, 1, 100);
                if (available < 0) throw nativeError("WSAPoll", api.wsaError(error));
                if (available == 0) continue;
                while (!stopping) {
                    final Integer index = freeSlots.poll(100, TimeUnit.MILLISECONDS);
                    if (index == null) break;
                    final long child = (long) api.accept.invokeExact(error, listener, NULL, NULL);
                    if (child == INVALID_SOCKET) {
                        freeSlots.add(index);
                        final int code = api.wsaError(error);
                        if (code == WSA_EWOULDBLOCK) break;
                        throw nativeError("accept", code);
                    }
                    admit(child, index, error, option);
                }
            }
        } catch (Throwable problem) {
            fail(problem);
        }
        // Close the listener from the stopping thread, after WSAPoll/accept has exited.
    }

    private void admit(long child, Integer index, MemorySegment error, MemorySegment option) throws Throwable {
        boolean admitted = false;
        try {
            if ((int) api.setsockopt.invokeExact(error, child, 6, 1, option, 4) != 0)
                throw nativeError("TCP_NODELAY", api.wsaError(error));
            final Session session = new Session(child, index);
            final MemorySegment attached = (MemorySegment) api.iocp.invokeExact(error,
                    MemorySegment.ofAddress(child), port, index.longValue() + 1L, 0);
            if (attached.address() != port.address())
                throw nativeError("Associate socket", api.winError(error));
            outstanding.register();
            sessions.set(index, session);
            accepted.incrementAndGet();
            admitted = true;
            synchronized (session) {
                try { session.pump(error); }
                catch (Throwable problem) {
                    fail(problem);
                    session.close(error);
                }
            }
        } finally {
            if (!admitted) {
                freeSlots.add(index);
                api.closeSocket(error, child);
            }
        }
    }

    private void work() {
        long reads = 0, writes = 0;
        try (Arena local = Arena.ofConfined()) {
            final MemorySegment error = local.allocate(api.state);
            final MemorySegment bytes = local.allocate(JAVA_INT);
            final MemorySegment key = local.allocate(JAVA_LONG);
            final MemorySegment operation = local.allocate(JAVA_LONG);
            while (!stopWorkers) {
                final int ok = (int) api.complete.invokeExact(error, port, bytes, key, operation, 1000);
                final long overlappedAddress = operation.get(JAVA_LONG, 0);
                if (overlappedAddress == 0) {
                    if (ok != 0 && key.get(JAVA_LONG, 0) == 0) return;
                    if (ok == 0 && api.winError(error) == WAIT_TIMEOUT) continue;
                    throw nativeError("GetQueuedCompletionStatus", api.winError(error));
                }
                final long slot = key.get(JAVA_LONG, 0);
                if (slot <= 0 || slot > capacity)
                    throw new IllegalStateException("Invalid IOCP completion key: " + slot);
                final Session session = sessions.get((int) slot - 1);
                if (session == null)
                    throw new IllegalStateException("Completion for a released IOCP slot: " + slot);
                if (session.complete(error, overlappedAddress, bytes.get(JAVA_INT, 0),
                        ok != 0 ? 0 : api.winError(error))) writes++;
                else reads++;
            }
        } catch (Throwable problem) {
            fail(problem);
        } finally {
            System.err.printf("%s read=%d write=%d%n", Thread.currentThread().getName(), reads, writes);
        }
    }

    private void fail(Throwable problem) {
        if (failure.compareAndSet(null, problem)) problem.printStackTrace(System.err);
        stopping = true;
    }

    private void stopAndDrain() throws Throwable {
        stopping = true;
        acceptor.join(5_000);
        if (acceptor.isAlive()) throw new IllegalStateException("Winsock acceptor did not stop");
        try (Arena local = Arena.ofConfined()) {
            final MemorySegment error = local.allocate(api.state);
            api.closeSocket(error, listener);
            for (int i = 0; i < capacity; i++) {
                final Session session = sessions.get(i);
                if (session != null) {
                    synchronized (session) { session.close(error); }
                }
            }
            outstanding.awaitAdvanceInterruptibly(outstanding.arriveAndDeregister(), 15, TimeUnit.SECONDS);
            stopWorkers = true;
            for (Thread ignored : workers)
                if ((int) api.wake.invokeExact(error, port, 0, 0L, NULL) == 0)
                    throw nativeError("PostQueuedCompletionStatus", api.winError(error));
            for (Thread worker : workers) {
                worker.join(2_000);
                if (worker.isAlive()) throw new IllegalStateException("IOCP completion worker did not stop");
            }
        }
        drained = true;
    }

    private static final class Batch {
        final MemorySegment data;
        final ByteBuffer[] frames = new ByteBuffer[BATCH];
        int length, sendLength, sent;

        Batch(MemorySegment data) {
            this.data = data;
            final ByteBuffer view = data.asByteBuffer();
            for (int i = 0; i < frames.length; i++) frames[i] = view.slice(i * FRAME, FRAME);
        }
    }

    private final class Session {
        private final long socket;
        private final Integer index;
        private final Batch first, second;
        private final MemorySegment readBuf, writeBuf, readOvl, writeOvl, flags;
        private Batch reading, writing, ready;
        private int sequence;
        private boolean readPending, writePending, inputEnded, socketClosed, retired;

        Session(long socket, Integer index) {
            this.socket = socket;
            this.index = index;
            final MemorySegment slot = buffers.asSlice(index.longValue() * SESSION_BYTES, SESSION_BYTES);
            first = new Batch(slot.asSlice(0, BATCH_BYTES));
            second = new Batch(slot.asSlice(BATCH_BYTES, BATCH_BYTES));
            readBuf = slot.asSlice(READ_BUF_OFFSET, 16);
            writeBuf = slot.asSlice(WRITE_BUF_OFFSET, 16);
            readOvl = slot.asSlice(READ_OVL_OFFSET, 32);
            writeOvl = slot.asSlice(WRITE_OVL_OFFSET, 32);
            flags = slot.asSlice(FLAGS_OFFSET, 4);
        }

        // The session monitor protects both completion paths. Native access to
        // either OVERLAPPED or data buffer continues until its packet is dequeued.
        private void pump(MemorySegment error) throws Throwable {
            if (socketClosed) return;
            if (writing == null && ready != null) {
                writing = ready;
                ready = null;
                postWrite(error);
                if (socketClosed) return;
            }
            if (!inputEnded && !stopping && reading == null && ready == null) {
                reading = writing == first ? second : first;
                final int tail = writing == null ? 0 : writing.length - writing.sendLength;
                if (tail != 0) {
                    // Only the leading whole frames are owned by WSASend.
                    MemorySegment.copy(writing.data, writing.sendLength, reading.data, 0, tail);
                    writing.length = writing.sendLength;
                }
                reading.length = tail;
                postRead(error);
            } else if (inputEnded && writing == null && ready == null) {
                close(error);
            }
        }

        private void postRead(MemorySegment error) throws Throwable {
            if (readPending || reading == null || reading.length >= BATCH_BYTES)
                throw new IllegalStateException("Invalid overlapped read state");
            readBuf.set(JAVA_INT, 0, BATCH_BYTES - reading.length);
            readBuf.set(JAVA_LONG, 8, reading.data.address() + reading.length);
            readOvl.fill((byte) 0);
            flags.set(JAVA_INT, 0, 0);
            final int result = (int) api.recv.invokeExact(error, socket, readBuf, 1, NULL, flags, readOvl, NULL);
            if (result == 0 || api.wsaError(error) == WSA_IO_PENDING) readPending = true;
            else submissionFailed(error, "WSARecv");
        }

        private void postWrite(MemorySegment error) throws Throwable {
            if (writePending || writing == null || writing.sent >= writing.sendLength)
                throw new IllegalStateException("Invalid overlapped write state");
            writeBuf.set(JAVA_INT, 0, writing.sendLength - writing.sent);
            writeBuf.set(JAVA_LONG, 8, writing.data.address() + writing.sent);
            writeOvl.fill((byte) 0);
            final int result = (int) api.send.invokeExact(error, socket, writeBuf, 1, NULL, 0, writeOvl, NULL);
            if (result == 0 || api.wsaError(error) == WSA_IO_PENDING) writePending = true;
            else submissionFailed(error, "WSASend");
        }

        private void submissionFailed(MemorySegment error, String operation) throws Throwable {
            final int code = api.wsaError(error);
            if (!peerDisconnected(code)) fail(nativeError(operation, code));
            close(error);
        }

        synchronized boolean complete(MemorySegment error, long operation, int bytes, int status) throws Throwable {
            final boolean write = operation == writeOvl.address();
            if (write ? !writePending : operation != readOvl.address() || !readPending)
                throw new IllegalStateException("Unexpected overlapped completion for socket " + index);
            if (write) writePending = false;
            else readPending = false;
            try {
                if (status != 0) {
                    if (!socketClosed && !peerDisconnected(status))
                        fail(nativeError("Overlapped socket completion", status));
                    close(error);
                } else if (write) {
                    writeCompleted(error, bytes);
                } else if (socketClosed || stopping) {
                    close(error);
                } else {
                    readCompleted(error, bytes);
                }
            } catch (Throwable problem) {
                fail(problem);
                close(error);
            }
            return write;
        }

        private void readCompleted(MemorySegment error, int bytes) throws Throwable {
            if (bytes == 0) {
                // A peer may half-close its send side while still reading the last reply.
                inputEnded = true;
                reading = null;
                pump(error);
                return;
            }
            if (bytes < 0 || bytes > BATCH_BYTES - reading.length)
                throw new IllegalStateException("Invalid overlapped read count: " + bytes);
            reading.length += bytes;
            final int framesRead = reading.length / FRAME;
            if (framesRead == 0) {
                postRead(error);
                return;
            }
            if (ready != null) throw new IllegalStateException("Overfilled session send queue");
            for (int i = 0; i < framesRead; i++) CoreProtocol.handle(reading.frames[i], sequence++);
            reading.sendLength = framesRead * FRAME;
            reading.sent = 0;
            ready = reading;
            reading = null;
            pump(error);
        }

        private void writeCompleted(MemorySegment error, int bytes) throws Throwable {
            if (bytes < 0 || bytes > writing.sendLength - writing.sent)
                throw new IllegalStateException("Invalid overlapped write count: " + bytes);
            // Count complete frame boundaries acknowledged by the kernel,
            // including a successful write dequeued after closesocket.
            final int before = writing.sent / FRAME;
            writing.sent += bytes;
            frames.add(writing.sent / FRAME - before);
            if (socketClosed || bytes == 0) {
                close(error);
            } else if (writing.sent < writing.sendLength) {
                postWrite(error);
            } else {
                writing = null;
                pump(error);
            }
        }

        private void close(MemorySegment error) throws Throwable {
            if (!socketClosed) {
                socketClosed = true;
                api.closeSocket(error, socket);
            }
            if (!readPending && !writePending && !retired) {
                retired = true;
                sessions.set(index, null);
                closed.incrementAndGet();
                freeSlots.add(index);
                outstanding.arriveAndDeregister();
            }
        }
    }

    private static boolean peerDisconnected(int code) {
        // Win32 errors from GQCS and Winsock errors from WSARecv/WSASend.
        return code == 64 || code == 109 || code == 995 || code == 1236
                || code == 10053 || code == 10054 || code == 10058;
    }

    private static IllegalStateException nativeError(String call, int code) {
        return new IllegalStateException(call + " failed (Windows " + code + ")");
    }

    public static void main(String[] args) throws Throwable {
        final String arch = System.getProperty("os.arch");
        if (!System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows")
                || !("amd64".equalsIgnoreCase(arch) || "x86_64".equalsIgnoreCase(arch)))
            throw new IllegalStateException("WinIocpCore requires Windows x64 and JDK 21");
        if (args.length != 3 || Integer.parseInt(args[0]) != FRAME)
            throw new IllegalArgumentException("Usage: WinIocpCore " + FRAME + " <capacity> <workers>");
        final int capacity = Integer.parseInt(args[1]), workers = Integer.parseInt(args[2]);
        if (capacity < 1 || capacity > 60_000 || workers < 1 || workers > 8)
            throw new IllegalArgumentException("Capacity or worker count outside benchmark range");

        final Arena memory = Arena.ofShared();
        Native api = null;
        WinIocpCore server = null;
        boolean winsockStarted = false;
        try {
            api = new Native(memory);
            try (Arena local = Arena.ofConfined()) {
                final int status = (int) api.startup.invokeExact(0x0202, local.allocate(512, 8));
                if (status != 0) throw nativeError("WSAStartup 2.2", status);
            }
            winsockStarted = true;
            server = new WinIocpCore(api, memory, capacity, workers);
            try {
                server.start();
                System.out.println("READY " + server.localPort);
                System.out.flush();
                final BufferedReader control = new BufferedReader(new InputStreamReader(System.in));
                for (String line; (line = control.readLine()) != null && !line.equals("STOP"); ) { }
            } finally {
                server.stopAndDrain();
            }
            final Throwable problem = server.failure.get();
            if (problem != null) throw new IllegalStateException("IOCP benchmark failed", problem);
        } finally {
            // If the kernel has not returned a cancellation completion for every
            // handle, let this throwaway process exit without freeing native buffers.
            if (server == null || server.drained) {
                if (server != null) api.closeHandle(server.port);
                if (winsockStarted) {
                    final int status = (int) api.cleanup.invokeExact();
                    if (status != 0) throw new IllegalStateException("WSACleanup failed");
                }
                memory.close();
            }
        }
        System.out.printf("CLOSED accepted=%d closed=%d frames=%d%n",
                server.accepted.get(), server.closed.get(), server.frames.sum());
    }

    private static final class Native {
        private static final Linker LINKER = Linker.nativeLinker();
        private static final Linker.Option WSA_ERROR = Linker.Option.captureCallState("WSAGetLastError");
        private static final Linker.Option WIN_ERROR = Linker.Option.captureCallState("GetLastError");
        final MemoryLayout state = Linker.Option.captureStateLayout();
        private final long wsaOffset = state.byteOffset(MemoryLayout.PathElement.groupElement("WSAGetLastError"));
        private final long winOffset = state.byteOffset(MemoryLayout.PathElement.groupElement("GetLastError"));

        final MethodHandle startup, cleanup, socket, bind, getsockname, listen, ioctl, poll, accept, setsockopt,
                recv, send, closeSocket, iocp, complete, wake, closeHandle;

        Native(Arena lifetime) {
            final SymbolLookup ws = SymbolLookup.libraryLookup("Ws2_32.dll", lifetime);
            final SymbolLookup k32 = SymbolLookup.libraryLookup("Kernel32.dll", lifetime);
            startup = link(ws, "WSAStartup", JAVA_INT, JAVA_INT, ADDRESS);
            cleanup = link(ws, "WSACleanup", JAVA_INT);
            socket = captured(ws, "WSASocketW", WSA_ERROR, JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_INT,
                    ADDRESS, JAVA_INT, JAVA_INT);
            bind = captured(ws, "bind", WSA_ERROR, JAVA_INT, JAVA_LONG, ADDRESS, JAVA_INT);
            getsockname = captured(ws, "getsockname", WSA_ERROR, JAVA_INT, JAVA_LONG, ADDRESS, ADDRESS);
            listen = captured(ws, "listen", WSA_ERROR, JAVA_INT, JAVA_LONG, JAVA_INT);
            ioctl = captured(ws, "ioctlsocket", WSA_ERROR, JAVA_INT, JAVA_LONG, JAVA_INT, ADDRESS);
            poll = captured(ws, "WSAPoll", WSA_ERROR, JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT);
            accept = captured(ws, "accept", WSA_ERROR, JAVA_LONG, JAVA_LONG, ADDRESS, ADDRESS);
            setsockopt = captured(ws, "setsockopt", WSA_ERROR, JAVA_INT, JAVA_LONG, JAVA_INT, JAVA_INT,
                    ADDRESS, JAVA_INT);
            recv = captured(ws, "WSARecv", WSA_ERROR, JAVA_INT, JAVA_LONG, ADDRESS, JAVA_INT,
                    ADDRESS, ADDRESS, ADDRESS, ADDRESS);
            send = captured(ws, "WSASend", WSA_ERROR, JAVA_INT, JAVA_LONG, ADDRESS, JAVA_INT,
                    ADDRESS, JAVA_INT, ADDRESS, ADDRESS);
            closeSocket = captured(ws, "closesocket", WSA_ERROR, JAVA_INT, JAVA_LONG);
            iocp = captured(k32, "CreateIoCompletionPort", WIN_ERROR, ADDRESS, ADDRESS, ADDRESS, JAVA_LONG, JAVA_INT);
            complete = captured(k32, "GetQueuedCompletionStatus", WIN_ERROR, JAVA_INT,
                    ADDRESS, ADDRESS, ADDRESS, ADDRESS, JAVA_INT);
            wake = captured(k32, "PostQueuedCompletionStatus", WIN_ERROR, JAVA_INT,
                    ADDRESS, JAVA_INT, JAVA_LONG, ADDRESS);
            closeHandle = link(k32, "CloseHandle", JAVA_INT, ADDRESS);
        }

        int wsaError(MemorySegment error) { return error.get(JAVA_INT, wsaOffset); }
        int winError(MemorySegment error) { return error.get(JAVA_INT, winOffset); }

        void closeSocket(MemorySegment error, long handle) throws Throwable {
            if ((int) closeSocket.invokeExact(error, handle) != 0)
                throw nativeError("closesocket", wsaError(error));
        }

        void closeHandle(MemorySegment handle) throws Throwable {
            if ((int) closeHandle.invokeExact(handle) == 0)
                throw new IllegalStateException("CloseHandle failed");
        }

        private static MethodHandle link(SymbolLookup library, String name, MemoryLayout result, MemoryLayout... args) {
            return LINKER.downcallHandle(library.find(name).orElseThrow(), FunctionDescriptor.of(result, args));
        }

        private static MethodHandle captured(SymbolLookup library, String name, Linker.Option option,
                MemoryLayout result, MemoryLayout... args) {
            return LINKER.downcallHandle(library.find(name).orElseThrow(), FunctionDescriptor.of(result, args), option);
        }
    }
}
