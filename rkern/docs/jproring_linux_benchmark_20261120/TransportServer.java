import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Selector;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollIoHandler;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.uring.IoUring;
import io.netty.channel.uring.IoUringIoHandler;
import io.netty.channel.uring.IoUringServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import l1j.server.server.model.network.core.transport.selector.CSelectorTransport;

public final class TransportServer {
    private static final int BACKLOG = 256;
    private static final InetSocketAddress ADDRESS = new InetSocketAddress("127.0.0.1", 0);

    public static void main(String[] args) throws Exception {
        String backend = args[0];
        int owners = Integer.parseInt(args[1]);
        int clients = Integer.parseInt(args[2]);
        int wireBytes = Integer.parseInt(args[3]);
        boolean framed = Boolean.parseBoolean(args[4]);
        if (backend.equals("rkern")) runRKern(owners, clients, wireBytes, framed);
        else runNetty(backend, owners, wireBytes, framed);
    }

    private static void runRKern(int owners, int clients, int wireBytes, boolean framed) throws Exception {
        int capacity = Math.max(128, (clients + owners - 1) / owners + 32);
        var raw = framed ? null : new RawEcho(owners, Math.min(wireBytes, 16_384));
        CSelectorTransport.Handler handler = framed ? (connection, payload) -> {
            if (payload.remaining() != wireBytes - 2
                    || !connection.trySend(payload.remaining(), out -> {
                        while (payload.remaining() != 0) out.writeByte(payload.readByte());
                    })) throw new java.io.IOException("Framed echo rejected");
        } : raw;
        var config = new CSelectorTransport.Config(owners, capacity, BACKLOG, capacity,
                framed ? capacity : 1, framed ? capacity * 8 : 1, framed ? capacity * 4 : 1,
                256 * 1024, 256, 0, Duration.ofSeconds(5));
        String provider;
        try (Selector selector = Selector.open()) { provider = selector.getClass().getName(); }
        try (var server = CSelectorTransport.bind(ADDRESS, config, handler)) {
            serveControl(server.localAddress().getPort(), provider, () -> raw == null ? -1L : raw.allocatedBytes());
            server.close();
            if (raw != null) raw.verifyReleased();
            for (var owner : server.shutdownStats()) {
                if (owner.remainingConnections() != 0 || owner.receive().inUse() != 0 || owner.send().inUse() != 0) {
                    throw new IllegalStateException("Outstanding connections or buffer leases");
                }
            }
        }
    }

    private static final class RawOwner {
        private final Map<CSelectorTransport.Connection, Session> sessions = new IdentityHashMap<>();
        private final ArrayDeque<ByteBuffer> free = new ArrayDeque<>();
        private volatile int allocated;
    }

    private static final class RawEcho implements CSelectorTransport.RawHandler {
        private final RawOwner[] owners;
        private final int chunkBytes;

        private RawEcho(int count, int chunkBytes) {
            this.chunkBytes = chunkBytes;
            owners = new RawOwner[count];
            for (int i = 0; i < count; i++) owners[i] = new RawOwner();
        }

        @Override public void onOpen(CSelectorTransport.Connection connection) {
            RawOwner owner = owners[connection.ownerIndex()];
            ByteBuffer buffer = owner.free.pollFirst();
            if (buffer == null) {
                buffer = ByteBuffer.allocateDirect(chunkBytes);
                owner.allocated++;
            }
            Session session = new Session(connection, buffer);
            owner.sessions.put(connection, session);
            session.read();
        }

        @Override public void onClosed(CSelectorTransport.Connection connection, CSelectorTransport.CloseReason reason) {
            RawOwner owner = owners[connection.ownerIndex()];
            owner.free.addLast(owner.sessions.remove(connection).buffer);
        }

        private long allocatedBytes() {
            long bytes = 0;
            for (RawOwner owner : owners) bytes += owner.allocated * (long) chunkBytes;
            return bytes;
        }

        private void verifyReleased() {
            for (RawOwner owner : owners) {
                if (!owner.sessions.isEmpty() || owner.free.size() != owner.allocated)
                    throw new IllegalStateException("Outstanding raw connection buffers");
            }
        }
    }

    private static final class Session {
        private final CSelectorTransport.Connection connection;
        private final ByteBuffer buffer;
        private final IntConsumer readCompletion = this::onRead;
        private final LongConsumer writeCompletion = this::onWrite;

        Session(CSelectorTransport.Connection connection, ByteBuffer buffer) {
            this.connection = connection;
            this.buffer = buffer;
        }

        void read() {
            buffer.clear();
            connection.readRaw(buffer, readCompletion);
        }

        private void onRead(int bytes) {
            if (bytes > 0) {
                buffer.flip();
                connection.writeRaw(buffer, writeCompletion);
            }
        }

        private void onWrite(long bytes) {
            if (bytes < 0) return;
            if (buffer.hasRemaining()) connection.writeRaw(buffer, writeCompletion);
            else read();
        }
    }

    private static void runNetty(String backend, int owners, int wireBytes, boolean framed) throws Exception {
        Class<? extends ServerChannel> channel;
        IoHandlerFactory factory;
        switch (backend) {
            case "netty-nio" -> {
                channel = NioServerSocketChannel.class;
                factory = NioIoHandler.newFactory();
            }
            case "netty-epoll" -> {
                if (!Epoll.isAvailable()) {
                    unsupported(Epoll.unavailabilityCause());
                    return;
                }
                channel = EpollServerSocketChannel.class;
                factory = EpollIoHandler.newFactory();
            }
            case "netty-io_uring" -> {
                if (!IoUring.isAvailable()) {
                    unsupported(IoUring.unavailabilityCause());
                    return;
                }
                channel = IoUringServerSocketChannel.class;
                factory = IoUringIoHandler.newFactory();
            }
            default -> throw new IllegalArgumentException(backend);
        }
        var group = new MultiThreadIoEventLoopGroup(owners, factory);
        var echo = new NettyEcho();
        try {
            Channel listener = new ServerBootstrap().group(group).channel(channel)
                    .option(ChannelOption.SO_BACKLOG, BACKLOG)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                    .childOption(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(Math.min(wireBytes, 16_384)))
                    .childHandler(new io.netty.channel.ChannelInitializer<Channel>() {
                        @Override protected void initChannel(Channel child) {
                            if (framed) child.pipeline().addLast(new LengthFieldBasedFrameDecoder(
                                    ByteOrder.LITTLE_ENDIAN, 65535, 0, 2, -2, 0, true));
                            child.pipeline().addLast(echo);
                        }
                    }).bind(ADDRESS).sync().channel();
            try {
                serveControl(((InetSocketAddress) listener.localAddress()).getPort(), channel.getSimpleName(),
                        () -> PooledByteBufAllocator.DEFAULT.metric().usedDirectMemory());
            } finally {
                listener.close().sync();
            }
        } finally {
            group.shutdownGracefully(0, 4, TimeUnit.SECONDS).sync();
        }
    }

    @ChannelHandler.Sharable
    private static final class NettyEcho extends ChannelInboundHandlerAdapter {
        @Override public void channelRead(ChannelHandlerContext ctx, Object buffer) {
            ctx.writeAndFlush(buffer);
        }
    }

    private static void serveControl(int port, String provider, LongSupplier poolBytes) throws Exception {
        System.out.println("READY " + port + " " + provider);
        var control = new BufferedReader(new InputStreamReader(System.in));
        for (String command; (command = control.readLine()) != null; ) {
            if (command.equals("STOP")) return;
            if (!command.equals("MEMORY")) throw new IllegalStateException("Unknown benchmark control command");
            memory(poolBytes.getAsLong());
        }
        throw new IllegalStateException("Expected benchmark shutdown signal");
    }

    private static void memory(long poolBytes) {
        long direct = ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class).stream()
                .filter(pool -> pool.getName().equals("direct")).mapToLong(BufferPoolMXBean::getMemoryUsed).sum();
        System.out.println("MEMORY " + direct + " " + poolBytes);
    }

    private static void unsupported(Throwable failure) {
        System.out.println("UNAVAILABLE " + failure.toString());
    }
}
