package l1j.server.server.network;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.EventExecutor;

import java.net.InetSocketAddress;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.logging.Logger;

/** Netty 4.2 I/O-handler API. No custom Selector and no packet-per-worker tickets. */
public final class CServer implements AutoCloseable {

    @FunctionalInterface
    public interface ApplicationFactory {
        SessionApplication create(NettySession session) throws Exception;
    }

    private static final Logger LOG = Logger.getLogger(CServer.class.getName());
    
    final NetworkConfig config;
    final NetworkMetrics metrics = new NetworkMetrics();
    final SessionDispatcher dispatcher;
    
    private final AdmissionController admission;
    private final MultiThreadIoEventLoopGroup acceptors, io;
    private final Set<NettySession> sessions = ConcurrentHashMap.newKeySet();
    private final ApplicationFactory factory;
    private final Predicate<String> allowedIp;
    private final AtomicBoolean stopping = new AtomicBoolean();
    private final AtomicBoolean asynchronousClose = new AtomicBoolean();
    private volatile Channel listener;
    private volatile Channel nativeStepListener;

    public CServer(NetworkConfig config, ApplicationFactory factory) {
        this(config, factory, ip -> true);
    }

    public CServer(NetworkConfig config, ApplicationFactory factory, Predicate<String> allowedIp) {
        this.config = config;
        this.factory = factory;
        this.allowedIp = allowedIp;
        this.admission = new AdmissionController(config);
        this.acceptors = new MultiThreadIoEventLoopGroup(1, new DefaultThreadFactory("Network42-Accept"), NioIoHandler.newFactory());
        this.io = new MultiThreadIoEventLoopGroup(config.ioThreads, new DefaultThreadFactory("Network42-IO"), NioIoHandler.newFactory());
        this.dispatcher = new SessionDispatcher(config);
    }

    public synchronized void start(InetSocketAddress address) throws InterruptedException {
        if (listener != null || stopping.get()) {
            throw new IllegalStateException("Server already started/closed");
        }
        
        try {
            listener = new ServerBootstrap()
                    .group(acceptors, io)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 512)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(config.writeLowWater, config.writeHighWater))
                    .childOption(ChannelOption.RCVBUF_ALLOCATOR, new AdaptiveRecvByteBufAllocator(1024, 4096, 16384))
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel channel) {
                            String ip = channel.remoteAddress().getAddress().getHostAddress();
                            
                            if (stopping.get() || !allowedIp.test(ip)) {
                                metrics.rejected.increment();
                                channel.close();
                                return;
                            }
                            
                            AdmissionController.Permit permit = admission.acquire(ip);
                            if (permit == null) {
                                metrics.rejected.increment();
                                channel.close();
                                return;
                            }
                            
                            NettySession session = new NettySession(CServer.this, channel, permit);
                            sessions.add(session);
                            metrics.accepted.increment();
                            
                            channel.pipeline().addLast(
                                    new SessionPipeline.ReadBudget(session),
                                    new SessionPipeline.Frames(session),
                                    new SessionPipeline.Lifecycle(session)
                            );
                            
                            try {
                                session.configure(factory.create(session));
                            } catch (Exception failure) {
                                session.close(CloseReason.INITIALIZATION_ERROR);
                            }
                        }
                    })
                    .bind(address)
                    .sync()
                    .channel();
                    
            // Control connections use the SAME acceptor and I/O event loops as the game.
            // No legacy ATS relay and no per-player TCP worker threads.
            try {
                nativeStepListener = new ServerBootstrap()
                    .group(acceptors, io)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 64)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override protected void initChannel(SocketChannel channel) {
                            String ip = channel.remoteAddress().getAddress().getHostAddress();
                            if (stopping.get() || !allowedIp.test(ip)) { channel.close(); return; }
                            channel.pipeline().addLast(new NativeStepControl.Handler());
                        }
                    }).bind(new InetSocketAddress(NativeStepControl.PORT)).sync().channel();
                LOG.info("NativeStep control listening on TCP " + NativeStepControl.PORT);
            } catch (Exception failure) {
                // An optional auto-hunt test port must never prevent the game listener
                // from starting. Item 49027 will refuse to start when not paired.
                LOG.warning("NativeStep control TCP " + NativeStepControl.PORT
                        + " unavailable: " + failure.getMessage());
            }
            LOG.info("CServer listening on " + listener.localAddress());
            
        } catch (InterruptedException e) {
            close();
            throw e;
        } catch (Throwable failure) {
            close();
            throw new IllegalStateException("Network bind failed", failure);
        }
    }

    public InetSocketAddress localAddress() {
        return (InetSocketAddress) listener.localAddress();
    }

    public NetworkMetrics.Snapshot snapshot() {
        return metrics.snapshot(admission.active(), admission.ipEntries(),
                dispatcher.gameQueued(), dispatcher.gameRunning(),
                dispatcher.authQueued(), dispatcher.authRunning());
    }

    boolean isStopping() {
        return stopping.get();
    }

    public void closeSessions(CloseReason reason) {
        for (NettySession s : sessions) {
            s.close(reason);
        }
    }

    void retired(NettySession session) {
        sessions.remove(session);
    }

    public boolean awaitDrained(long timeoutMs) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (!sessions.isEmpty() && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        return sessions.isEmpty();
    }

    @Override
    public void close() {
        boolean owned = dispatcher.inWorker();
        
        for (EventExecutor e : io) {
            if (e.inEventLoop()) owned = true;
        }
        
        for (EventExecutor e : acceptors) {
            if (e.inEventLoop()) owned = true;
        }
        
        if (owned) {
            if (asynchronousClose.compareAndSet(false, true)) {
                new Thread(this::close, "Network42-Shutdown").start();
            }
            return;
        }
        
        if (!stopping.compareAndSet(false, true)) {
            return;
        }
        
        if (nativeStepListener != null) nativeStepListener.close().awaitUninterruptibly(config.shutdownMs);
        if (listener != null) {
            listener.close().awaitUninterruptibly(config.shutdownMs);
        }
        
        closeSessions(CloseReason.SERVER_SHUTDOWN);
        
        boolean drained = false;
        try {
            drained = awaitDrained(config.shutdownMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        if (drained) {
            dispatcher.close();
        } else {
            LOG.warning("Transport closed; bounded game cleanup still pending: " + snapshot());
            
            // Do not silently discard persistence work just because its DB callback exceeded the socket deadline.
            Thread reaper = new Thread(() -> {
                try {
                    while (!awaitDrained(1000)) { }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                dispatcher.close();
            }, "Network42-Late-Cleanup");
            
            reaper.setDaemon(true);
            reaper.start();
        }
        
        io.shutdownGracefully(0, 2, TimeUnit.SECONDS).awaitUninterruptibly(3000);
        acceptors.shutdownGracefully(0, 2, TimeUnit.SECONDS).awaitUninterruptibly(3000);
    }
}
