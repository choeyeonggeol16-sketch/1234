package l1j.server.server.model.network.core;

import java.io.IOException;
import java.lang.foreign.AddressLayout;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.LockSupport;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Mandatory CTransport native IOCP bridge.
 * Java 21 FFM preview: --enable-preview --enable-native-access=ALL-UNNAMED.
 * Normal native completions run on transport IOCP workers; no Java completion pool is added.
 */
final class CIOServer implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(CIOServer.class.getName());
    @SuppressWarnings("preview")
	private static final Linker LINKER = Linker.nativeLinker();
    @SuppressWarnings("preview")
	private static final ValueLayout.OfInt I = ValueLayout.JAVA_INT;
    @SuppressWarnings("preview")
	private static final ValueLayout.OfLong L = ValueLayout.JAVA_LONG;
    @SuppressWarnings("preview")
	private static final AddressLayout P = ValueLayout.ADDRESS;

    private static final ThreadLocal<int[]> CALLBACK_DEPTH = ThreadLocal.withInitial(() -> new int[1]);
    static boolean isCompletionThread() { return CALLBACK_DEPTH.get()[0] != 0; }
    private static void enterCallback() { CALLBACK_DEPTH.get()[0]++; }
    private static void leaveCallback() {
        int[] depth = CALLBACK_DEPTH.get();
        --depth[0];
    }

    interface Admitter { void admit(CSocket channel); }

    @SuppressWarnings("preview")
	private final Arena arena = Arena.ofShared();
    @SuppressWarnings("preview")
	private final SymbolLookup symbols;
    private final Admitter admitter;
    private final CIOServerEndpoint.Events rawEvents;
    private final ConcurrentHashMap<Long, Runnable> nativeTasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, CIOObject> completionTasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, CSocket> closingConnections = new ConcurrentHashMap<>();
    private final AtomicLong nextTask = new AtomicLong();
    private final MethodHandle scheduleTask, cancelTask, getDiagnostics;
    private final MethodHandle setCodecApi,initializeUserSocket,releaseUserPacket,encryptUserData,getUserSocketState,threadOwner;
    private final MethodHandle setUserState,configureDeadlines,requestDeadline,getRegistryDiagnostics;
    private final MethodHandle setAccountIdentity,setCharacterEntered,findEnteringAccount,getAccountRegistryDiagnostics,configurePerf,getPerfDiagnostics;
    private final MethodHandle configureLimits,getLimiterDiagnostics,getListenerDiagnostics,getThreadDiagnostics,findByNumber,setMacIdentity,reportSockets;
    private final MemorySegment userPacketStub,networkDeadlineStub;
    private volatile boolean drainClosed;
    private final MemorySegment taskStub, closingStub;
    @SuppressWarnings("unused")
	private final InetSocketAddress listenAddress;
    private volatile AtomicReferenceArray<CSocket> connections;
    private final int mask;
    private final CSocket tombstone;
    // Only accept/close writers take this lock; IOCP lookups remain lock-free.
    // Periodic rehash prevents long-lived tombstone-saturated miss scans.
    private int registryLive, registryTombstones;
    private final Object registryLock = new Object();
    @SuppressWarnings("preview")
	private final MemorySegment listener;
    @SuppressWarnings("preview")
	private final MemorySegment server;
    @SuppressWarnings("unused")
	private final MethodHandle start, stop, wait, stopAccept, disconnect, pause, send, sendPackets;
    private final MethodHandle sendFrames,sendFramePackets,gracefulClose,getSendDiagnostics;
    private final MethodHandle getRemote, getLocal, getListen;
    @SuppressWarnings("preview")
	private final MemorySegment acceptStub, receiveStub, sendStub, closeStub;
    private volatile boolean started;
    private volatile boolean destroyed;
    private volatile boolean accepting = true;
    // Prevent native server destruction while an arbitrary Java SEND,
    // PAUSE or DISCONNECT downcall is still executing. No global hot-path lock.
    private final AtomicInteger nativeCalls = new AtomicInteger();
    private volatile boolean shuttingDown;
    private volatile boolean nativeAcceptStopped;
    // One daemon thread only during shutdown; game IOCP and CIO worker layout is unchanged.
    // Never release FFM upcall arenas while a native Stop/StopAccepting call is hung.
    private volatile Thread nativeShutdownThread;
    private volatile boolean nativeStopRequested;
    private volatile boolean nativeStopCompleted;
    private volatile IOException nativeShutdownFailure;
    private static final long SHUTDOWN_BUDGET_NS = 30_000_000_000L;

    /** Fail closed on an unknown native ABI, rather than guessing callback/WSABUF lifetimes. */
    static void requireKnownDll(Path path, String expected) throws IOException {
        if (!Files.isRegularFile(path)) throw new IOException("native DLL missing: " + path);
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] chunk = new byte[8192];
                for (int n; (n = input.read(chunk)) >= 0;) {
                    if (n != 0) digest.update(chunk, 0, n);
                }
            }
            final byte[] hash = digest.digest();
            final char[] actual = new char[64];
            final char[] hex = "0123456789abcdef".toCharArray();
            for (int i = 0; i < hash.length; i++) {
                int value = hash[i] & 0xff;
                actual[i * 2] = hex[value >>> 4];
                actual[i * 2 + 1] = hex[value & 15];
            }
            if (!expected.contentEquals(new String(actual)))
                throw new IOException("Unrecognized native ABI: " + path
                        + ", sha256=" + new String(actual));
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 unavailable", e);
        }
    }

    @SuppressWarnings("preview")
	CIOServer(InetSocketAddress address, int maxConnections, int ioThreads,
             int backlog, Admitter admitter) throws IOException {
        this(address, maxConnections, ioThreads, backlog, admitter, null);
    }

    CIOServer(InetSocketAddress address,int maxConnections,int ioThreads,int backlog,Admitter admitter,int ipLimit)throws IOException{
        this(address,maxConnections,ioThreads,backlog,admitter,null,ipLimit,CIOConfig.MAC_LIMIT);
    }
    CIOServer(InetSocketAddress address,int maxConnections,int ioThreads,int backlog,Admitter admitter,CIOServerEndpoint.Events rawEvents)throws IOException{
        this(address,maxConnections,ioThreads,backlog,admitter,rawEvents,0,CIOConfig.MAC_LIMIT);
    }
    CIOServer(InetSocketAddress address,int maxConnections,int ioThreads,int backlog,Admitter admitter,int ipLimit,int macLimit)throws IOException{
        this(address,maxConnections,ioThreads,backlog,admitter,null,ipLimit,macLimit);
    }
    @SuppressWarnings("preview")
    private CIOServer(InetSocketAddress address, int maxConnections, int ioThreads,
                     int backlog, Admitter admitter, CIOServerEndpoint.Events rawEvents,int ipLimit,int macLimit) throws IOException {
        this.admitter = admitter;
        this.rawEvents = rawEvents;
        this.listenAddress = address;
        int slots = 16;
        while (slots < (long) maxConnections * 4L) slots <<= 1;
        this.connections = new AtomicReferenceArray<>(slots);
        this.mask = slots - 1;
        this.tombstone = new CSocket(this, -1L, address, address);
        MemorySegment createdListener = MemorySegment.NULL;
        MemorySegment createdServer = MemorySegment.NULL;
        try {
            Path dll = CIOConfig.DLL.toAbsolutePath();
            requireKnownDll(dll, CIOConfig.DLL_SHA256);
            symbols = SymbolLookup.libraryLookup(dll, arena);
            MethodHandle version = fn("CTransport_Version", FunctionDescriptor.of(I));
            if ((int) version.invokeExact() != CIOConfig.ABI)
                throw new IOException("Unsupported CTransport ABI");
            // Require our patched export, not an unmodified third-party binary.
            stopAccept = fn("CIO_StopAccepting", FunctionDescriptor.of(I, P));
            start = fn("CIO_Server_Start", FunctionDescriptor.of(I, P, P, ValueLayout.JAVA_SHORT));
            stop = fn("CIO_Server_Stop", FunctionDescriptor.of(I, P));
            wait = fn("CIO_Server_Wait", FunctionDescriptor.of(I, P, I));
            disconnect = fn("CIO_Server_Disconnect", FunctionDescriptor.of(I, P, L, I));
            pause = fn("CIO_Server_PauseReceive", FunctionDescriptor.of(I, P, L, I));
            sendFrames=fn("CTransport_UserSocket_SendFrames",FunctionDescriptor.of(I,P,L,P,I,I));
            sendFramePackets=fn("CTransport_UserSocket_SendFramePackets",FunctionDescriptor.of(I,P,L,P,I,I));
            gracefulClose=fn("CTransport_Server_GracefulClose",FunctionDescriptor.of(I,P,L,I));
            getSendDiagnostics=fn("CTransport_Server_GetSendDiagnostics",FunctionDescriptor.of(I,P,P,I));
            send = fn("CIO_Server_Send", FunctionDescriptor.of(I, P, L, P, I));
            sendPackets = fn("CIO_Server_SendPackets", FunctionDescriptor.of(I, P, L, P, I));
            getRemote = fn("CIO_Server_GetRemoteAddress", FunctionDescriptor.of(I, P, L, P, P, P));
            getLocal = fn("CIO_Server_GetLocalAddress", FunctionDescriptor.of(I, P, L, P, P, P));
            getListen = fn("CIO_Server_GetListenAddress", FunctionDescriptor.of(I, P, P, P, P));
            MethodHandle createListener = fn("CIO_Create_TcpServerListener", FunctionDescriptor.of(P));
            MethodHandle createServer = fn("CIO_Create_TcpServer", FunctionDescriptor.of(P, P));
            MethodHandle setAccept = fn("CIO_Set_FN_Server_OnAccept", FunctionDescriptor.ofVoid(P, P));
            MethodHandle setReceive = fn("CIO_Set_FN_Server_OnReceive", FunctionDescriptor.ofVoid(P, P));
            MethodHandle setSend = fn("CIO_Set_FN_Server_OnSend", FunctionDescriptor.ofVoid(P, P));
            MethodHandle setClose = fn("CIO_Set_FN_Server_OnClose", FunctionDescriptor.ofVoid(P, P));
            scheduleTask = fn("CTransport_Server_Schedule", FunctionDescriptor.of(I, P, L, I));
            setCodecApi=fn("CTransport_Server_SetCodecApi",FunctionDescriptor.of(I,P,P,I));
            initializeUserSocket=fn("CTransport_UserSocket_Initialize",FunctionDescriptor.of(I,P,L,I));
            releaseUserPacket=fn("CTransport_UserSocket_ReleasePacket",FunctionDescriptor.of(I,P,L,L));
            encryptUserData=fn("CTransport_UserSocket_Encrypt",FunctionDescriptor.of(I,P,L,P,I));
            getUserSocketState=fn("CTransport_UserSocket_GetState",FunctionDescriptor.of(I,P,L,P,I));
            setUserState=fn("CTransport_UserSocket_SetSessionState",FunctionDescriptor.of(I,P,L,I));
            configureDeadlines=fn("CTransport_UserSocket_ConfigureDeadlines",FunctionDescriptor.of(I,P,L,I));
            requestDeadline=fn("CTransport_UserSocket_RequestDeadline",FunctionDescriptor.of(I,P,L,I));
            getRegistryDiagnostics=fn("CTransport_Server_GetRegistryDiagnostics",FunctionDescriptor.of(I,P,P,I));
            configureLimits=fn("CTransport_Server_ConfigureLimits",FunctionDescriptor.of(I,P,I,I));
            getLimiterDiagnostics=fn("CTransport_Server_GetLimiterDiagnostics",FunctionDescriptor.of(I,P,P,I));
            getListenerDiagnostics=fn("CTransport_Server_GetListenerDiagnostics",FunctionDescriptor.of(I,P,P,I));
            setAccountIdentity=fn("CTransport_UserSocket_SetAccountIdentity",FunctionDescriptor.of(I,P,L,I));
            setCharacterEntered=fn("CTransport_UserSocket_SetCharacterEntered",FunctionDescriptor.of(I,P,L,I));
            findEnteringAccount=fn("CTransport_UserSocket_FindEnteringAccount",FunctionDescriptor.of(L,P,I));
            getAccountRegistryDiagnostics=fn("CTransport_Server_GetAccountRegistryDiagnostics",FunctionDescriptor.of(I,P,P,I));
            configurePerf=fn("CTransport_Server_ConfigurePerf",FunctionDescriptor.of(I,P,I));
            getPerfDiagnostics=fn("CTransport_Server_GetPerfDiagnostics",FunctionDescriptor.of(I,P,P,I));
            getThreadDiagnostics=fn("CTransport_Server_GetThreadDiagnostics",FunctionDescriptor.of(I,P,P,I));
            findByNumber=fn("CTransport_UserSocket_FindByNumber",FunctionDescriptor.of(L,P,I));
            setMacIdentity=fn("CTransport_UserSocket_SetMacIdentity",FunctionDescriptor.of(I,P,L,L));
            reportSockets=fn("CTransport_Server_ReportSockets",FunctionDescriptor.of(I,P,P,I,I));
            threadOwner = fn("CTransport_Server_ThreadOwner", FunctionDescriptor.of(I, P));
            cancelTask = fn("CTransport_Server_Cancel", FunctionDescriptor.of(I, P, L));
            MethodHandle setTask = fn("CTransport_SetTaskCallback", FunctionDescriptor.ofVoid(P, P));
            MethodHandle setClosing = fn("CTransport_SetClosingCallback", FunctionDescriptor.ofVoid(P, P));
            getDiagnostics = fn("CTransport_Server_GetDiagnostics", FunctionDescriptor.of(I, P, P, I));
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            userPacketStub=LINKER.upcallStub(lookup.findVirtual(CIOServer.class,"onUserPacket",
                    MethodType.methodType(int.class,MemorySegment.class,long.class,long.class,MemorySegment.class,int.class)).bindTo(this),FunctionDescriptor.of(I,P,L,L,P,I),arena);
            networkDeadlineStub=LINKER.upcallStub(lookup.findVirtual(CIOServer.class,"onNetworkDeadline",
                    MethodType.methodType(int.class,MemorySegment.class,long.class)).bindTo(this),FunctionDescriptor.of(I,P,L),arena);
            taskStub = LINKER.upcallStub(lookup.findVirtual(CIOServer.class, "onTask",
                    MethodType.methodType(void.class, MemorySegment.class, long.class, int.class)).bindTo(this),
                    FunctionDescriptor.ofVoid(P, L, I), arena);
            acceptStub = LINKER.upcallStub(lookup.findVirtual(CIOServer.class, "onAccept",
                    MethodType.methodType(int.class, MemorySegment.class, long.class, long.class)).bindTo(this),
                    FunctionDescriptor.of(I, P, L, L), arena);
            receiveStub = LINKER.upcallStub(lookup.findVirtual(CIOServer.class, "onReceive",
                    MethodType.methodType(int.class, MemorySegment.class, long.class, MemorySegment.class, int.class)).bindTo(this),
                    FunctionDescriptor.of(I, P, L, P, I), arena);
            sendStub = LINKER.upcallStub(lookup.findVirtual(CIOServer.class, "onSend",
                    MethodType.methodType(int.class, MemorySegment.class, long.class, MemorySegment.class, int.class)).bindTo(this),
                    FunctionDescriptor.of(I, P, L, P, I), arena);
            closingStub = LINKER.upcallStub(lookup.findVirtual(CIOServer.class, "onClosing",
                    MethodType.methodType(int.class, MemorySegment.class, long.class, int.class, int.class)).bindTo(this),
                    FunctionDescriptor.of(I, P, L, I, I), arena);
            closeStub = LINKER.upcallStub(lookup.findVirtual(CIOServer.class, "onClose",
                    MethodType.methodType(int.class, MemorySegment.class, long.class, int.class, int.class)).bindTo(this),
                    FunctionDescriptor.of(I, P, L, I, I), arena);
            listener = (MemorySegment) createListener.invokeExact();
            createdListener = listener;
            if (listener.address() == 0) throw new IOException("CIO listener allocation failed");
            setAccept.invokeExact(listener, acceptStub);
            setReceive.invokeExact(listener, receiveStub);
            setSend.invokeExact(listener, sendStub);
            setClose.invokeExact(listener, closeStub);
            fn("CTransport_SetUserPacketCallback",FunctionDescriptor.ofVoid(P,P)).invokeExact(listener,userPacketStub);
            fn("CTransport_SetNetworkDeadlineCallback",FunctionDescriptor.ofVoid(P,P)).invokeExact(listener,networkDeadlineStub);
            setClosing.invokeExact(listener, closingStub);
            setTask.invokeExact(listener, taskStub);
            server = (MemorySegment) createServer.invokeExact(listener);
            createdServer = server;
            if (server.address() == 0) throw new IOException("CIO server allocation failed");
            fn("CIO_Server_SetMaxConnectionCount", FunctionDescriptor.ofVoid(P, I)).invokeExact(server, maxConnections);
            fn("CIO_Server_SetWorkerThreadCount", FunctionDescriptor.ofVoid(P, I)).invokeExact(server, ioThreads);
            fn("CIO_TcpServer_SetSocketListenQueue", FunctionDescriptor.ofVoid(P, I)).invokeExact(server, backlog);
            fn("CIO_TcpServer_SetSocketBufferSize", FunctionDescriptor.ofVoid(P, I)).invokeExact(server, 16384);
            fn("CIO_TcpServer_SetNoDelay", FunctionDescriptor.ofVoid(P, I)).invokeExact(server, 1);
            // Native SP_PACK=0; CIO socket serializer already owns send ordering.
            fn("CIO_Server_SetSendPolicy", FunctionDescriptor.ofVoid(P, I)).invokeExact(server, 0);
            fn("CIO_Server_SetOnSendSyncPolicy", FunctionDescriptor.ofVoid(P, I)).invokeExact(server, 0);
            if((int)configurePerf.invokeExact(server,CIOConfig.PERF_INTERVAL_MS)==0)throw new IOException("Native CPerfMon configuration rejected");
            if((int)configureLimits.invokeExact(server,ipLimit,macLimit)==0)throw new IOException("Native CSocketLimiter configuration rejected");
            try (Arena a = Arena.ofConfined()) {
                String bind = address.getAddress() == null ? "0.0.0.0" : address.getAddress().getHostAddress();
                int result = (int) start.invokeExact(server, a.allocateUtf8String(bind), (short)address.getPort());
                if (result == 0) {
                    long[] stats = diagnostics();
                    throw new IOException("CTransport listener start failed for " + address
                            + "; allocatedBlocks=" + stats[0] + "; poolInitFailures=" + stats[2]);
                }
            }
            started = true;
        } catch (Throwable failure) {
            // A partially constructed native server/listener must be destroyed
            // BEFORE its FFM upcall arena is closed.
            try {
                if (createdServer.address() != 0) {
                    fn("CIO_Destroy_TcpServer", FunctionDescriptor.ofVoid(P)).invokeExact(createdServer);
                }
            } catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
            try {
                if (createdListener.address() != 0) {
                    fn("CIO_Destroy_TcpServerListener", FunctionDescriptor.ofVoid(P)).invokeExact(createdListener);
                }
            } catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
            try { arena.close(); }
            catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
            if (failure instanceof IOException e) throw e;
            throw new IOException("CIO FFM/IOCP startup failed (Java 21 preview and patched x64 DLL required)", failure);
        }
    }

    private MethodHandle fn(String symbol, @SuppressWarnings("preview") FunctionDescriptor desc) {
        return LINKER.downcallHandle(symbols.find(symbol)
                .orElseThrow(() -> new IllegalStateException("CIO native DLL missing export: " + symbol)), desc);
    }
    private int slot(long id) { long x = id * 0x9E3779B97F4A7C15L; return (int)(x ^ (x >>> 32)) & mask; }
    private CSocket find(long id) {
        for (;;) {
            final AtomicReferenceArray<CSocket> table = connections;
            final int start = slot(id);
            for (int n = 0; n < table.length(); n++) {
                final CSocket item = table.get((start + n) & mask);
                if (item == null) break;
                if (item != tombstone && item.id() == id) return item;
            }
            // A table rebuild concurrent with this miss may have moved the
            // entry. Retry on the newly published table, never on a stale one.
            if (table == connections) return null;
        }
    }
    private boolean register(CSocket channel) {
        synchronized (registryLock) {
        final AtomicReferenceArray<CSocket> table = connections;
        final int start = slot(channel.id());
        for (int n = 0; n < table.length(); n++) {
            final int p = (start + n) & mask;
            final CSocket cur = table.get(p);
            if (cur == null || cur == tombstone) {
                table.set(p, channel); // protected against all registry writers
                registryLive++;
                if (cur == tombstone) registryTombstones--;
                return true;
            }
        }
        return false;
        }
    }
    private void forget(CSocket channel) {
        synchronized (registryLock) {
        final AtomicReferenceArray<CSocket> table = connections;
        final int start = slot(channel.id());
        for (int n = 0; n < table.length(); n++) {
            final int p = (start + n) & mask;
            final CSocket cur = table.get(p);
            if (cur == channel) {
                table.set(p, tombstone);
                registryLive--;
                registryTombstones++;
                // Rare O(table) compaction is confined to connection churn;
                // ordinary RECV/SEND callback lookup stays allocation-free.
                if (registryTombstones >= Math.max(128, table.length() >>> 2)
                        && registryTombstones > registryLive) compactRegistry(table);
                return;
            }
            if (cur == null) return;
        }
        }
    }
    private void compactRegistry(AtomicReferenceArray<CSocket> old) {
        final AtomicReferenceArray<CSocket> fresh =
                new AtomicReferenceArray<>(old.length());
        for (int i = 0; i < old.length(); i++) {
            final CSocket item = old.get(i);
            if (item == null || item == tombstone) continue;
            final int start = slot(item.id());
            for (int n = 0; n < fresh.length(); n++) {
                final int at = (start + n) & mask;
                if (fresh.get(at) == null) {
                    fresh.set(at, item);
                    break;
                }
            }
        }
        // Volatile publication gives readers a complete, immutable snapshot
        // until a subsequent registration/deletion. Writers are synchronized.
        connections = fresh;
        registryTombstones = 0;
    }

    @SuppressWarnings({ "preview", "unused" })
	private int onAccept(MemorySegment sender, long id, long socket) {
        enterCallback();
        try {
            if (!accepting) return 2; // HR_ERROR: deny admission
            InetSocketAddress remote = address(getRemote, id);
            InetSocketAddress local = address(getLocal, id);
            if (remote == null || local == null) return 2;
            if (rawEvents != null) {
                CIOServerEndpoint.Peer peer = new CIOServerEndpoint.Peer(this, id, remote);
                if (!rawEvents.accept(peer) || !pause(id, false)) return 2;
                return 0;
            }
            CSocket channel = new CSocket(this, id, remote, local);
            if (!register(channel)) return 2;
            // Do not admit a session unless native inbound processing can
            // actually be paused until its CIO READ lease has been installed.
            if (!pause(id, true)) {
                forget(channel);
                channel.nativeClosed();
                return 2;
            }
            admitter.admit(channel);
            if (!channel.isOpen()) {
                // The library may suppress OnClose for rejected OnAccept callbacks.
                forget(channel);
                return 2;
            }
            return 0;
        } catch (Throwable failure) {
            LOG.log(Level.WARNING, "CIO native ACCEPT rejected", failure);
            if (rawEvents != null) { rawEvents.closed(id); return 0; }
            CSocket channel = closingConnections.remove(id);
            if (channel == null) { channel = find(id); if (channel != null) forget(channel); }
            if (channel != null) channel.nativeClosed();
            return 2;
        } finally { leaveCallback(); }
    }
    @SuppressWarnings("unused")
	private int onReceive(@SuppressWarnings("preview") MemorySegment sender, long id, @SuppressWarnings("preview") MemorySegment data, int len) {
        enterCallback();
        try {
            if (rawEvents != null) return len > 0 && rawEvents.receive(id, data.reinterpret(len).asByteBuffer()) ? 0 : 2;
            CSocket channel = find(id);
            if (channel == null) return 2;
            if (len <= 0) {
                channel.nativeClosed();
                return 2;
            }
            channel.nativeReceive(data, len);
            return 0;
        } catch (Throwable failure) {
            LOG.log(Level.WARNING, "CIO native RECV failed conn=" + id, failure);
            return 2;
        } finally { leaveCallback(); }
    }
    @SuppressWarnings({"preview","unused"})
    private int onUserPacket(MemorySegment sender,long id,long sequence,MemorySegment bytes,int length) {
        enterCallback();
        try {CSocket channel=find(id);return channel!=null&&channel.nativePacket(sequence,length==0?null:bytes.reinterpret(length).asByteBuffer().order(java.nio.ByteOrder.LITTLE_ENDIAN),length)?0:2;}
        catch(Throwable failure){LOG.log(Level.WARNING,"Native L1 packet callback failed conn="+id,failure);return 2;}
        finally{leaveCallback();}
    }
    @SuppressWarnings({"preview","unused"})
    private int onNetworkDeadline(MemorySegment sender,long id) {
        enterCallback();
        try{CSocket channel=find(id);return channel==null?0:channel.nativeNetworkDeadline();}
        catch(Throwable failure){LOG.log(Level.WARNING,"Native network deadline failed conn="+id,failure);return -1;}
        finally{leaveCallback();}
    }
    @SuppressWarnings("unused")
	private int onSend(@SuppressWarnings("preview") MemorySegment sender, long id, @SuppressWarnings("preview") MemorySegment data, int len) {
        enterCallback();
        try {
            if (rawEvents != null) { rawEvents.sent(id, len); return 0; }
            CSocket channel = find(id);
            if (channel != null) channel.nativeSent(len);
            return 0;
        } catch (Throwable failure) {
            LOG.log(Level.WARNING, "CIO native SEND failed conn=" + id, failure);
            disconnect(id);
            return 0;
        } finally { leaveCallback(); }
    }
    @SuppressWarnings("unused")
	private int onClose(@SuppressWarnings("preview") MemorySegment sender, long id, int operation, int error) {
        enterCallback();
        try {
            if (rawEvents != null) { rawEvents.closed(id); return 0; }
            CSocket channel = closingConnections.remove(id);
            if (channel == null) { channel = find(id); if (channel != null) forget(channel); }
            if (channel != null) channel.nativeTerminated();
            return 0;
        } catch (Throwable failure) {
            LOG.log(Level.WARNING, "CIO native CLOSE callback failed conn=" + id, failure);
            return 0;
        } finally { leaveCallback(); }
    }
    @SuppressWarnings({"preview", "unused"})
    private void onTask(MemorySegment sender, long token, int status) {
        enterCallback();
        try {
            Runnable callback = nativeTasks.remove(token);
            CIOObject completion = completionTasks.remove(token);
            if (status == 0 && callback != null && !shuttingDown) callback.run();
            else if (completion != null) completion.cancel();
        } catch (Throwable failure) { LOG.log(Level.WARNING, "CTransport task failed", failure); }
        finally { leaveCallback(); }
    }

    @SuppressWarnings("preview")
    long schedule(Runnable callback, long delayMillis) {
        return schedule(callback, null, delayMillis);
    }

    boolean postCompletion(CIOObject task) { return schedule(task, task, 0) != 0; }

    @SuppressWarnings("preview")
    private long schedule(Runnable callback, CIOObject completion, long delayMillis) {
        if (callback == null) throw new NullPointerException("callback");
        if (delayMillis < 0 || delayMillis > Integer.MAX_VALUE) throw new IllegalArgumentException("delayMillis");
        if (!enterNative()) return 0;
        long token = nextTask.incrementAndGet();
        if (token == 0) token = nextTask.incrementAndGet();
        try {
            if (completion != null) completionTasks.put(token, completion);
            nativeTasks.put(token, callback); // publish both before a native completion can run
            if ((int) scheduleTask.invokeExact(server, token, (int) delayMillis) != 0) return token;
            nativeTasks.remove(token); completionTasks.remove(token); return 0;
        } catch (Throwable failure) { nativeTasks.remove(token); completionTasks.remove(token); throw new IllegalStateException("CTransport schedule failed", failure); }
        finally { nativeCalls.decrementAndGet(); }
    }

    @SuppressWarnings("preview")
    boolean cancel(long token) {
        if (!enterNative()) return false;
        try {
            if ((int) cancelTask.invokeExact(server, token) == 0) return false;
            nativeTasks.remove(token);
            CIOObject completion = completionTasks.remove(token);
            if (completion != null) completion.cancel();
            return true;
        } catch (Throwable failure) { throw new IllegalStateException("CTransport cancellation failed", failure); }
        finally { nativeCalls.decrementAndGet(); }
    }

    @SuppressWarnings({"preview", "unused"})
    private int onClosing(MemorySegment sender, long id, int operation, int error) {
        enterCallback();
        try {
            if (rawEvents != null) rawEvents.closing(id);
            else {
                CSocket channel = find(id);
                if (channel != null) {
                    channel.nativeClosing();
                    closingConnections.put(id, channel);
                    forget(channel); // admission lookup ends before physical socket close
                }
            }
            if (error == 10055) LOG.warning("CTransport buffer capacity exhausted: conn=" + id);
            return 0;
        } catch (Throwable failure) { LOG.log(Level.WARNING, "CTransport closing hook failed", failure); return 0; }
        finally { leaveCallback(); }
    }

    @SuppressWarnings("preview")
    long[] diagnostics() {
        if (!enterNative()) throw new IllegalStateException("CTransport is stopping");
        try (Arena memory = Arena.ofConfined()) {
            MemorySegment values = memory.allocate(8 * Long.BYTES, Long.BYTES);
            if ((int) getDiagnostics.invokeExact(server, values, 8) == 0) throw new IllegalStateException("native diagnostics rejected");
            long[] result = new long[8];
            for (int i = 0; i < result.length; i++) result[i] = values.get(L, i * Long.BYTES);
            return result;
        } catch (RuntimeException failure) { throw failure; }
        catch (Throwable failure) { throw new IllegalStateException("native diagnostics failed", failure); }
        finally { nativeCalls.decrementAndGet(); }
    }

    private InetSocketAddress address(MethodHandle getter, long id) throws Throwable {
        try (@SuppressWarnings("preview")
		Arena a = Arena.ofConfined()) {
            @SuppressWarnings("preview")
			MemorySegment buf = a.allocate(64);
            @SuppressWarnings("preview")
			MemorySegment size = a.allocate(I);
            @SuppressWarnings("preview")
			MemorySegment port = a.allocate(ValueLayout.JAVA_SHORT);
            size.set(I, 0, 64);
            int ok = (int) getter.invokeExact(server, id, buf, size, port);
            if (ok == 0) return null;
            @SuppressWarnings("preview")
			int p = Short.toUnsignedInt(port.get(ValueLayout.JAVA_SHORT, 0));
            return new InetSocketAddress(buf.getUtf8String(0), p);
        }
    }
    @SuppressWarnings("preview")
    boolean initializeUserSocket(long id,MemorySegment functions,int seed) {
        if(!enterNative())return false;
        try {return (int)setCodecApi.invokeExact(server,functions,7)!=0&&(int)initializeUserSocket.invokeExact(server,id,seed)!=0;}
        catch(Throwable failure){throw new IllegalStateException("Native codec setup failed",failure);}
        finally{nativeCalls.decrementAndGet();}
    }
    private boolean enterDrain() {
        if(drainClosed)return false;
        nativeCalls.incrementAndGet();
        if(!drainClosed)return true;
        nativeCalls.decrementAndGet();return false;
    }
    boolean releaseUserPacket(long id,long sequence) {
        if(!enterDrain())return false;
        try{return (int)releaseUserPacket.invokeExact(server,id,sequence)!=0;}
        catch(Throwable failure){throw new IllegalStateException("Native packet release failed",failure);}
        finally{nativeCalls.decrementAndGet();}
    }
    @SuppressWarnings("preview")
    boolean encryptUserData(long id,ByteBuffer bytes) {
        if(!enterNative())return false;
        try{return (int)encryptUserData.invokeExact(server,id,MemorySegment.ofBuffer(bytes),bytes.remaining())!=0;}
        catch(Throwable failure){throw new IllegalStateException("Native encryption failed",failure);}
        finally{nativeCalls.decrementAndGet();}
    }
    boolean setUserState(long id,int state) {return userControl(setUserState,id,state);}
    boolean configureDeadlines(long id,int loginMs){return userControl(configureDeadlines,id,loginMs);}
    boolean requestDeadline(long id,int delayMs){return userControl(requestDeadline,id,delayMs);}
    private boolean userControl(MethodHandle method,long id,int value) {
        if(!enterNative())return false;
        try{return (int)method.invokeExact(server,id,value)!=0;}
        catch(Throwable failure){throw new IllegalStateException("Native user control failed",failure);}
        finally{nativeCalls.decrementAndGet();}
    }
    int currentNativeOwner() {
        if (!enterNative()) return -1;
        try { return (int) threadOwner.invokeExact(server); }
        catch (Throwable failure) { throw new IllegalStateException("Native worker identity query failed", failure); }
        finally { nativeCalls.decrementAndGet(); }
    }
    @SuppressWarnings("preview")
    long[] userSocketState(long id) {
        if (!enterNative()) return null;
        try (Arena a = Arena.ofConfined()) {
            MemorySegment data = a.allocate(14L * Long.BYTES, Long.BYTES);
            if ((int) getUserSocketState.invokeExact(server, id, data, 14) == 0) return null;
            return data.toArray(L);
        } catch (Throwable failure) { throw new IllegalStateException("L1 parser snapshot failed", failure); }
        finally { nativeCalls.decrementAndGet(); }
    }

    @SuppressWarnings("preview")
    long[] registryDiagnostics() {
        if(!enterNative())return null;
        try(Arena a=Arena.ofConfined()){MemorySegment data=a.allocate(48,8);if((int)getRegistryDiagnostics.invokeExact(server,data,6)==0)return null;return data.toArray(L);}
        catch(Throwable failure){throw new IllegalStateException("Native registry query failed",failure);}
        finally{nativeCalls.decrementAndGet();}
    }
    @SuppressWarnings("preview")
    private long[] readDiagnostics(MethodHandle method,int count){
        if(!enterNative())return null;
        try(Arena a=Arena.ofConfined()){
            MemorySegment out=a.allocate((long)count*Long.BYTES,Long.BYTES);
            return (int)method.invokeExact(server,out,count)==0?null:out.toArray(L);
        }catch(Throwable failure){throw new IllegalStateException("Native diagnostic read failed",failure);}
        finally{nativeCalls.decrementAndGet();}
    }
    boolean setAccountIdentity(long id,int account){return userControl(setAccountIdentity,id,account);}
    boolean setCharacterEntered(long id,boolean entered){return userControl(setCharacterEntered,id,entered?1:0);}
    long[] accountRegistryDiagnostics(){return readDiagnostics(getAccountRegistryDiagnostics,4);}
    long[] performanceDiagnostics(){return readDiagnostics(getPerfDiagnostics,16);}
    long findEnteringAccount(int account){
        if(!enterNative())return 0;
        try{return (long)findEnteringAccount.invokeExact(server,account);}
        catch(Throwable failure){throw new IllegalStateException("Native entering account lookup failed",failure);}
        finally{nativeCalls.decrementAndGet();}
    }
    long[] listenerDiagnostics(){return readDiagnostics(getListenerDiagnostics,9);}
    long[] threadDiagnostics(){return readDiagnostics(getThreadDiagnostics,54);}
    long[] limiterDiagnostics(){return readDiagnostics(getLimiterDiagnostics,4);}
    long findByNumber(int number){
        if(!enterNative())return 0;
        try{return (long)findByNumber.invokeExact(server,number);}
        catch(Throwable failure){throw new IllegalStateException("Native user-number lookup failed",failure);}
        finally{nativeCalls.decrementAndGet();}
    }
    boolean setMacIdentity(long id,long identity){
        if(!enterNative())return false;
        try{return (int)setMacIdentity.invokeExact(server,id,identity)!=0;}
        catch(Throwable failure){throw new IllegalStateException("Native socket identity rejected",failure);}
        finally{nativeCalls.decrementAndGet();}
    }
    @SuppressWarnings("preview")
    long[][] ReportSocket(){
        if(!enterNative())return new long[0][];
        try(Arena a=Arena.ofConfined()){
            int capacity=(int)reportSockets.invokeExact(server,MemorySegment.NULL,0,12);
            if(capacity<0)throw new IllegalStateException("Native snapshot failed");
            if(capacity==0)return new long[0][];
            MemorySegment out=a.allocate((long)capacity*12*Long.BYTES,Long.BYTES);
            int count=(int)reportSockets.invokeExact(server,out,capacity,12);
            if(count<0||count>capacity)throw new IllegalStateException("Native snapshot changed format");
            long[][] rows=new long[count][];
            for(int i=0;i<count;i++)rows[i]=out.asSlice((long)i*12*Long.BYTES,12L*Long.BYTES).toArray(L);
            return rows;
        }catch(Throwable failure){throw new IllegalStateException("Native socket report failed",failure);}
        finally{nativeCalls.decrementAndGet();}
    }
    private boolean enterNative() {
        if (shuttingDown) return false;
        nativeCalls.incrementAndGet();
        if (!shuttingDown) return true;
        nativeCalls.decrementAndGet();
        return false;
    }
    boolean pause(long id, boolean paused) {
        if (!enterNative()) return false;
        try { return (int) pause.invokeExact(server, id, paused ? 1 : 0) != 0; }
        catch (Throwable failure) { throw new IllegalStateException("CIO pause failed", failure); }
        finally { nativeCalls.decrementAndGet(); }
    }
    long[] sendDiagnostics(){return readDiagnostics(getSendDiagnostics,9);}
    boolean closeGracefully(long id,int timeoutMs){
        if(!enterNative())return false;
        try{return (int)gracefulClose.invokeExact(server,id,timeoutMs)!=0;}
        catch(Throwable failure){return false;}
        finally{nativeCalls.decrementAndGet();}
    }
    boolean disconnect(long id) {
        if (!enterNative()) return false;
        try { return (int) disconnect.invokeExact(server, id, 1) != 0; }
        catch (Throwable failure) { return false; }
        finally { nativeCalls.decrementAndGet(); }
    }
    @SuppressWarnings("preview")
	boolean send(long id, ByteBuffer[] bufs, int offset, int count) {return send(id,bufs,offset,count,0);}
    @SuppressWarnings("preview")
    boolean send(long id, ByteBuffer[] bufs, int offset, int count,int flags) {
        if (!enterNative()) return false;
        try {
            if (count == 1) {
                ByteBuffer b = bufs[offset];
                int n = b.remaining();
                if (b.isDirect()) {
                  
					MemorySegment data = MemorySegment.ofBuffer(b);
                    return flags==0?(int)send.invokeExact(server,id,data,n)!=0:(int)sendFrames.invokeExact(server,id,data,n,(flags&2)>>>1)!=0;
                }
                // FFM cannot pass a heap ByteBuffer address to native code.
                // The normal pooled SEND path is direct and avoids this copy.
                try (
				Arena a = Arena.ofConfined()) {
					MemorySegment data = a.allocate(n);
                    data.copyFrom(MemorySegment.ofBuffer(b));
                    return flags==0?(int)send.invokeExact(server,id,data,n)!=0:(int)sendFrames.invokeExact(server,id,data,n,(flags&2)>>>1)!=0;
                }
            }
            // WSABUF descriptors and any heap-data copies live until the
            // synchronous packed-native SendPackets() call has returned.
            try (
			Arena a = Arena.ofConfined()) {
				MemorySegment wsabufs = a.allocate(count * 16L, 8);
                for (int i = 0; i < count; i++) {
                    ByteBuffer b = bufs[offset + i];
                    long o = i * 16L;
                    int n = b.remaining();
                    
					MemorySegment data;
                    if (n == 0) {
                        data = MemorySegment.NULL;
                    } else if (b.isDirect()) {
                        data = MemorySegment.ofBuffer(b);
                    } else {
                        data = a.allocate(n);
                        data.copyFrom(MemorySegment.ofBuffer(b));
                    }
                    wsabufs.set(I, o, n);
                    wsabufs.set(P, o + 8, data);
                }
                return flags==0?(int)sendPackets.invokeExact(server,id,wsabufs,count)!=0:(int)sendFramePackets.invokeExact(server,id,wsabufs,count,(flags&2)>>>1)!=0;
            }
        } catch (Throwable failure) {
            LOG.log(Level.WARNING, "CIO native SEND call failed conn=" + id, failure);
            return false;
        } finally { nativeCalls.decrementAndGet(); }
    }
    /** Starts native accept-stop without blocking the Java shutdown owner.
     * This never runs on a completion callback; no extra packet worker is added.
     */
    synchronized void stopAccepting() throws IOException {
        if (destroyed || nativeAcceptStopped) return;
        if (isCompletionThread())
            throw new IOException("cannot stop native listener inside completion callback");
        accepting = false;
        if (nativeShutdownThread != null) return;
        final Thread t = new Thread(this::nativeShutdownRoutine, "CIO-native-shutdown");
        t.setDaemon(true);
        nativeShutdownThread = t;
        t.start();
    }

    private void nativeShutdownRoutine() {
        try {
            if ((int) stopAccept.invokeExact(server) == 0)
                throw new IOException("native StopAccepting returned false");
            nativeAcceptStopped = true;
            // Stop must not run before the last Java SEND/PAUSE downcall ends.
            // The owning shutdown thread requests the second phase explicitly.
            while (!nativeStopRequested) LockSupport.park(this);
            if (started && (int) stop.invokeExact(server) == 0)
                throw new IOException("native CIO_Server_Stop returned false");
            nativeStopCompleted = true;
        } catch (Throwable error) {
            nativeShutdownFailure = error instanceof IOException io ? io :
                    new IOException("CIO native shutdown downcall failed", error);
        }
    }

    IOException shutdownFailure() { return nativeShutdownFailure; }

    @SuppressWarnings("preview")
    @Override public synchronized void close() throws IOException {
        closeWithBudget(SHUTDOWN_BUDGET_NS);
    }

    @SuppressWarnings("preview")
    synchronized void closeWithBudget(long budgetNs) throws IOException {
        if (destroyed) return;
        if (isCompletionThread())
            throw new IOException("CIO native Stop cannot join its own IOCP worker");
        if (budgetNs <= 0L) throw new IOException("CIO shutdown deadline expired; native memory preserved");
        final long startedAt = System.nanoTime();
        // Starting stopAccepting is asynchronous: even an unresponsive native
        // listener cannot trap the caller in an unbounded downcall.
        stopAccepting();
        shuttingDown = true;
        for(var entry:completionTasks.entrySet()) {
            if(completionTasks.remove(entry.getKey(),entry.getValue())){nativeTasks.remove(entry.getKey());entry.getValue().cancel();}
        }
        while (!nativeAcceptStopped || nativeCalls.get() != 0) {
            final IOException failed = nativeShutdownFailure;
            if (failed != null) throw failed;
            if (System.nanoTime() - startedAt >= budgetNs)
                throw new IOException("CIO shutdown timed out waiting accept/downcalls; outstanding="
                        + nativeCalls.get() + "; native memory preserved");
            LockSupport.parkNanos(this, 100_000L);
            if (Thread.interrupted()) {
                Thread.currentThread().interrupt();
                throw new IOException("CIO native shutdown interrupted; native memory preserved");
            }
        }
        nativeStopRequested = true;
        final Thread t = nativeShutdownThread;
        if (t != null) LockSupport.unpark(t);
        // A hung C++ Stop/worker join cannot exceed the Java-side deadline.
        // Never force-close the shared arena while its upcalls may still run.
        while (!nativeStopCompleted) {
            final IOException failed = nativeShutdownFailure;
            if (failed != null) throw failed;
            if (System.nanoTime() - startedAt >= budgetNs)
                throw new IOException("CIO native Stop timed out; native arena retained and shutdown aborted");
            LockSupport.parkNanos(this, 100_000L);
            if (Thread.interrupted()) {
                Thread.currentThread().interrupt();
                throw new IOException("CIO native Stop interrupted; native arena retained");
            }
        }
        drainClosed=true;
        while(nativeCalls.get()!=0){
            if(System.nanoTime()-startedAt>=budgetNs)throw new IOException("Packet release downcall still active; native memory retained");
            LockSupport.parkNanos(this,100_000L);
        }
        // Stop returned successfully; native workers and their callbacks have
        // joined. Only now is it safe to destroy the server and FFM upcall stubs.
        try {
            started = false;
            fn("CIO_Destroy_TcpServer", FunctionDescriptor.ofVoid(P)).invokeExact(server);
            fn("CIO_Destroy_TcpServerListener", FunctionDescriptor.ofVoid(P)).invokeExact(listener);
            destroyed = true;
            // Native Stop has joined every callback. Resolve accepted packet
            // leases cancelled by native shutdown before discarding their map.
            for (CIOObject task : completionTasks.values()) task.cancel();
            completionTasks.clear();
            nativeTasks.clear();
            closingConnections.clear();
            arena.close();
        } catch (Throwable error) {
            throw new IOException("CIO native destruction failed", error);
        }
    }
    boolean isStopping() { return shuttingDown; }
    boolean isAcceptStopped() { return nativeAcceptStopped || destroyed; }
    @SuppressWarnings("preview")
	InetSocketAddress localAddress() throws IOException {
        if (!enterNative()) throw new IOException("native transport is stopping");
        try (
		Arena a = Arena.ofConfined()) {     
			MemorySegment address = a.allocate(64);
			MemorySegment capacity = a.allocate(I);
			MemorySegment port = a.allocate(ValueLayout.JAVA_SHORT);
            capacity.set(I, 0, 64);
            int ok = (int) getListen.invokeExact(server, address, capacity, port);
            if (ok == 0) throw new IOException("unable to query native listen address");
            return new InetSocketAddress(address.getUtf8String(0), Short.toUnsignedInt(port.get(ValueLayout.JAVA_SHORT, 0)));
        } catch (IOException e) { throw e; }
        catch (Throwable failure) { throw new IOException("native listen address failed", failure); }
        finally { nativeCalls.decrementAndGet(); }
    }
    boolean isTerminated() { return destroyed; }
}
