import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;

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
import l1j.server.server.model.network.core.transport.selector.SelectorTransport;

public final class TransportServer {
    private static final int BUFFER_SIZE = 64;
    private static final int BACKLOG = 256;
    private static final InetSocketAddress ADDRESS = new InetSocketAddress("127.0.0.1", 0);

    public static void main(String[] args) throws Exception {
        String backend = args[0];
        if (backend.equals("rkern")) runRKern();
        else runNetty(backend);
    }

    private static void runRKern() throws Exception {
        var handler = new RawEcho();
        var config = new SelectorTransport.Config(1, 128, BACKLOG, 128, 1, 1, 1,
                3, 1, 0, Duration.ofSeconds(5));
        try (Selector selector = Selector.open(); var server = SelectorTransport.bind(ADDRESS, config, handler)) {
            ready(server.localAddress().getPort(), selector.getClass().getName());
            server.close();
            if (!handler.sessions.isEmpty() || handler.free.size() != handler.allocated || server.shutdownFailure() != null) {
                throw new IllegalStateException("Outstanding raw connections or transport error", server.shutdownFailure());
            }
            memory(handler.allocated * (long) BUFFER_SIZE);
        }
    }

    private static final class RawEcho implements SelectorTransport.RawHandler {
        private final Map<SelectorTransport.Connection, Session> sessions = new IdentityHashMap<>();
        private final ArrayDeque<ByteBuffer> free = new ArrayDeque<>();
        private int allocated;

        @Override public void onOpen(SelectorTransport.Connection connection) {
            ByteBuffer buffer = free.pollFirst();
            if (buffer == null) {
                buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
                allocated++;
            }
            Session session = new Session(connection, buffer);
            sessions.put(connection, session);
            session.read();
        }

        @Override public void onClosed(SelectorTransport.Connection connection, SelectorTransport.CloseReason reason) {
            free.addLast(sessions.remove(connection).buffer);
        }
    }

    private static final class Session {
        private final SelectorTransport.Connection connection;
        private final ByteBuffer buffer;
        private final IntConsumer readCompletion = this::onRead;
        private final LongConsumer writeCompletion = this::onWrite;

        Session(SelectorTransport.Connection connection, ByteBuffer buffer) {
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

    private static void runNetty(String backend) throws Exception {
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
        var group = new MultiThreadIoEventLoopGroup(1, factory);
        try {
            Channel listener = new ServerBootstrap().group(group).channel(channel)
                    .option(ChannelOption.SO_BACKLOG, BACKLOG)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                    .childOption(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(BUFFER_SIZE))
                    .childHandler(new NettyEcho()).bind(ADDRESS).sync().channel();
            try {
                ready(((InetSocketAddress) listener.localAddress()).getPort(), channel.getSimpleName());
                memory(PooledByteBufAllocator.DEFAULT.metric().usedDirectMemory());
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

    private static void ready(int port, String provider) throws Exception {
        System.out.println("READY " + port + " " + provider);
        if (System.in.read() != '\n') throw new IllegalStateException("Expected benchmark shutdown signal");
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
