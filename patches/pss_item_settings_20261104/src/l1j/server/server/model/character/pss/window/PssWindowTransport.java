package l1j.server.server.model.character.pss.window;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

/** Authenticated, window-only companion channel; never accepts gameplay commands. */
public final class PssWindowTransport implements AutoCloseable {
    public static final byte OPEN = 1, PING = 2, BOUND = 16;
    private static final byte[] HELLO = {'P', 'S', 'S', 'U', 'I', 1, 0, 0};
    private static final long REGISTER_TTL = TimeUnit.SECONDS.toNanos(15);
    private static final long HELLO_TTL = TimeUnit.SECONDS.toNanos(5);
    private static final long IDLE_TTL = TimeUnit.SECONDS.toNanos(45);

    public record Token(long high, long low) {
        public static Token from(byte[] bytes) {
            if (bytes.length != 16) throw new IllegalArgumentException("token length");
            long high = 0, low = 0;
            for (int i = 0; i < 8; i++) high = high << 8 | bytes[i] & 255L;
            for (int i = 8; i < 16; i++) low = low << 8 | bytes[i] & 255L;
            return new Token(high, low);
        }
    }

    public interface Owner {
        boolean valid();
        String remoteIp();
        boolean attach(Token token, Peer peer);
        void detach(Peer peer);
    }

    private record Registration(Owner owner, long expires) {}
    private final ConcurrentHashMap<Token, Registration> pending = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Token, Peer> waiting = new ConcurrentHashMap<>();
    private final Set<Peer> peers = ConcurrentHashMap.newKeySet();
    private final AtomicInteger connections = new AtomicInteger();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AsynchronousChannelGroup group;
    private final AsynchronousServerSocketChannel listener;
    private final ScheduledExecutorService sweeper;
    private final LongSupplier clock;
    private final int capacity;

    public PssWindowTransport(InetSocketAddress address, int capacity) throws IOException {
        this(address, capacity, System::nanoTime);
    }

    PssWindowTransport(InetSocketAddress address, int capacity, LongSupplier clock) throws IOException {
        if (capacity < 1) throw new IllegalArgumentException("capacity");
        this.capacity = capacity;
        this.clock = clock;
        group = AsynchronousChannelGroup.withFixedThreadPool(2, task -> {
            Thread thread = new Thread(task, "RKern-PSS-UI");
            thread.setDaemon(true);
            return thread;
        });
        try {
            listener = AsynchronousServerSocketChannel.open(group).bind(address);
        } catch (IOException failure) {
            group.shutdownNow();
            throw failure;
        }
        sweeper = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "RKern-PSS-UI-expiry");
            thread.setDaemon(true);
            return thread;
        });
        sweeper.scheduleWithFixedDelay(this::sweep, 1, 1, TimeUnit.SECONDS);
        accept();
    }

    public int port() throws IOException {
        return ((InetSocketAddress) listener.getLocalAddress()).getPort();
    }

    public boolean isOpen() { return !closed.get(); }

    public boolean register(Token token, Owner owner) {
        if (closed.get() || !owner.valid() || pending.size() >= capacity) return false;
        Registration entry = new Registration(owner, clock.getAsLong() + REGISTER_TTL);
        if (pending.putIfAbsent(token, entry) != null) return false;
        if (closed.get() || !owner.valid()) {
            pending.remove(token, entry);
            return false;
        }
        Peer peer = waiting.remove(token);
        if (peer != null) {
            Registration claimed = pending.remove(token);
            if (claimed != null) bind(peer, token, claimed);
        }
        return true;
    }

    public void cancel(Token token, Owner owner) {
        if (token == null) return;
        Registration entry = pending.get(token);
        if (entry != null && entry.owner() == owner) pending.remove(token, entry);
        Peer peer = waiting.remove(token);
        if (peer != null) peer.close();
    }

    private void accept() {
        if (closed.get()) return;
        try {
        listener.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {
            @Override public void completed(AsynchronousSocketChannel channel, Void ignored) {
                if (closed.get()) { closeChannel(channel); return; }
                accept();
                if (connections.incrementAndGet() > capacity) {
                    connections.decrementAndGet();
                    closeChannel(channel);
                    return;
                }
                Peer peer = new Peer(channel);
                peers.add(peer);
                if (closed.get()) peer.close();
                else {
                    try {
                        channel.setOption(StandardSocketOptions.TCP_NODELAY, true);
                        channel.setOption(StandardSocketOptions.SO_SNDBUF, 4096);
                        channel.setOption(StandardSocketOptions.SO_RCVBUF, 4096);
                        peer.readHello();
                    } catch (IOException | RuntimeException failure) { peer.close(); }
                }
            }
            @Override public void failed(Throwable failure, Void ignored) {
                if (!closed.get()) close();
            }
        });
        } catch (RuntimeException failure) {
            if (!closed.get()) close();
        }
    }

    private static void closeChannel(AsynchronousSocketChannel channel) {
        try { channel.close(); } catch (IOException ignored) { }
    }

    private void bind(Peer peer, Token token, Registration registration) {
        try {
            Owner owner = registration.owner();
            String ip = ((InetSocketAddress) peer.channel.getRemoteAddress()).getAddress().getHostAddress();
            if (closed.get() || clock.getAsLong() >= registration.expires()
                    || !owner.valid() || !owner.remoteIp().equals(ip) || peer.stopped.get()) {
                peer.close();
                return;
            }
            peer.owner = owner;
            peer.ackPending.set(true);
            if (!peer.bound.compareAndSet(false, true) || !owner.attach(token, peer)) {
                peer.close();
                return;
            }
            peer.drain();
        } catch (RuntimeException | IOException failure) {
            peer.close();
        }
    }

    void sweep() {
        long now = clock.getAsLong();
        pending.forEach((token, registration) -> {
            if (now >= registration.expires() || !registration.owner().valid())
                pending.remove(token, registration);
        });
        for (Peer peer : peers) {
            if (!peer.bound.get() ? now - peer.created >= HELLO_TTL
                    : now - peer.lastSeen >= IDLE_TTL || !peer.owner.valid()) peer.close();
        }
    }

    int pendingCount() { return pending.size(); }
    int waitingCount() { return waiting.size(); }
    int connectionCount() { return connections.get(); }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        try { listener.close(); } catch (IOException ignored) { }
        for (Peer peer : peers) peer.close();
        pending.clear();
        waiting.clear();
        sweeper.shutdownNow();
        try { group.shutdownNow(); } catch (IOException ignored) { }
    }

    public final class Peer implements AutoCloseable {
        private final AsynchronousSocketChannel channel;
        private final ByteBuffer input = ByteBuffer.allocate(24);
        private final ByteBuffer output = ByteBuffer.allocate(1);
        private final AtomicBoolean stopped = new AtomicBoolean();
        private final AtomicBoolean bound = new AtomicBoolean();
        private final AtomicBoolean ackPending = new AtomicBoolean();
        private final AtomicBoolean openPending = new AtomicBoolean();
        private final AtomicBoolean writing = new AtomicBoolean();
        private final long created = clock.getAsLong();
        private volatile long lastSeen = created;
        private volatile Owner owner;
        private volatile Token token;

        private Peer(AsynchronousSocketChannel channel) { this.channel = channel; }

        private void readHello() {
            if (stopped.get()) return;
            try {
            channel.read(input, null, new CompletionHandler<Integer, Void>() {
                @Override public void completed(Integer count, Void ignored) {
                    if (count < 0 || stopped.get()) { close(); return; }
                    if (input.hasRemaining()) { readHello(); return; }
                    input.flip();
                    for (byte expected : HELLO) if (input.get() != expected) { close(); return; }
                    token = new Token(input.getLong(), input.getLong());
                    if (waiting.putIfAbsent(token, Peer.this) != null) { close(); return; }
                    if (stopped.get() || closed.get()) {
                        waiting.remove(token, Peer.this);
                        close();
                        return;
                    }
                    Registration registration = pending.remove(token);
                    if (registration != null) {
                        waiting.remove(token, Peer.this);
                        bind(Peer.this, token, registration);
                    }
                    input.clear().limit(1);
                    readPing();
                }
                @Override public void failed(Throwable failure, Void ignored) { close(); }
            });
            } catch (RuntimeException failure) { close(); }
        }

        private void readPing() {
            if (stopped.get()) return;
            try {
            channel.read(input, null, new CompletionHandler<Integer, Void>() {
                @Override public void completed(Integer count, Void ignored) {
                    if (count < 0 || stopped.get()) { close(); return; }
                    if (count > 0) {
                        if (input.get(0) != PING) { close(); return; }
                        lastSeen = clock.getAsLong();
                    }
                    input.clear().limit(1);
                    readPing();
                }
                @Override public void failed(Throwable failure, Void ignored) { close(); }
            });
            } catch (RuntimeException failure) { close(); }
        }

        /** Coalesce repeated clicks; never block a game I/O completion thread. */
        public boolean requestOpen() {
            Owner current = owner;
            if (closed.get() || stopped.get() || !bound.get() || current == null || !current.valid()) return false;
            openPending.set(true);
            drain();
            return !stopped.get();
        }

        private void drain() {
            if (stopped.get() || !writing.compareAndSet(false, true)) return;
            byte command = ackPending.getAndSet(false) ? BOUND : openPending.getAndSet(false) ? OPEN : 0;
            if (command == 0) {
                writing.set(false);
                if (ackPending.get() || openPending.get()) drain();
                return;
            }
            output.clear().put(command).flip();
            write();
        }

        private void write() {
            if (stopped.get()) return;
            try {
            channel.write(output, null, new CompletionHandler<Integer, Void>() {
                @Override public void completed(Integer count, Void ignored) {
                    if (count < 0 || stopped.get()) { close(); return; }
                    if (output.hasRemaining()) { write(); return; }
                    writing.set(false);
                    drain();
                }
                @Override public void failed(Throwable failure, Void ignored) { close(); }
            });
            } catch (RuntimeException failure) { close(); }
        }

        @Override public void close() {
            if (!stopped.compareAndSet(false, true)) return;
            closeChannel(channel);
            peers.remove(this);
            connections.decrementAndGet();
            Token key = token;
            if (key != null) waiting.remove(key, this);
            Owner current = owner;
            if (current != null) {
                try { current.detach(this); } catch (RuntimeException ignored) { }
            }
        }
    }
}
