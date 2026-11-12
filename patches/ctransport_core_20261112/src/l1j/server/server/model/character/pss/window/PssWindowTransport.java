package l1j.server.server.model.character.pss.window;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import l1j.server.server.model.network.core.CTransportEndpoint;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
    private final CTransportEndpoint listener;
    private final ConcurrentHashMap<CTransportEndpoint.Peer, Peer> nativePeers = new ConcurrentHashMap<>();
    private volatile long sweepTask;
    private final LongSupplier clock;
    private final int capacity;

    public PssWindowTransport(InetSocketAddress address, int capacity) throws IOException {
        this(address, capacity, System::nanoTime);
    }

    PssWindowTransport(InetSocketAddress address, int capacity, LongSupplier clock) throws IOException {
        if (capacity < 1) throw new IllegalArgumentException("capacity");
        this.capacity = capacity;
        this.clock = clock;
        listener = new CTransportEndpoint(address, capacity, new CTransportEndpoint.Handler() {
            public boolean accept(CTransportEndpoint.Peer channel) {
                if (closed.get()) return false;
                if (connections.incrementAndGet() > capacity) { connections.decrementAndGet(); return false; }
                Peer peer = new Peer(channel);
                nativePeers.put(channel, peer);
                peers.add(peer);
                if (closed.get()) { peer.close(); return false; }
                return true;
            }
            public boolean receive(CTransportEndpoint.Peer channel, ByteBuffer bytes) {
                Peer peer = nativePeers.get(channel);
                return peer != null && peer.receive(bytes);
            }
            public void sent(CTransportEndpoint.Peer channel, int bytes) {
                Peer peer = nativePeers.get(channel);
                if (peer != null) peer.written(bytes);
            }
            public void closed(CTransportEndpoint.Peer channel) {
                Peer peer = nativePeers.remove(channel);
                if (peer != null) peer.close();
            }
        });
        sweepTask = listener.schedule(this::sweepAndSchedule, 1000);
        if (sweepTask == 0) { listener.close(); throw new IOException("CTransport expiry timer registration failed"); }
    }

    private void sweepAndSchedule() {
        if (closed.get()) return;
        try { sweep(); }
        finally { if (!closed.get()) sweepTask = listener.schedule(this::sweepAndSchedule, 1000); }
    }

    public int port() throws IOException { return listener.port(); }

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

    private void bind(Peer peer, Token token, Registration registration) {
        try {
            Owner owner = registration.owner();
            String ip = peer.channel.remoteAddress().getAddress().getHostAddress();
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
        } catch (RuntimeException failure) {
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
        for (Peer peer : peers) peer.close();
        listener.cancel(sweepTask);
        try { listener.close(); }
        catch (IOException failure) { throw new IllegalStateException("CTransport companion shutdown failed", failure); }
        pending.clear();
        waiting.clear();
    }

    public final class Peer implements AutoCloseable {
        private final CTransportEndpoint.Peer channel;
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

        private Peer(CTransportEndpoint.Peer channel) { this.channel = channel; }

        private boolean helloComplete;
        private synchronized boolean receive(ByteBuffer bytes) {
            if (stopped.get()) return false;
            if (!helloComplete) {
                while (input.hasRemaining() && bytes.hasRemaining()) input.put(bytes.get());
                if (input.hasRemaining()) return true;
                input.flip();
                for (byte expected : HELLO) if (input.get() != expected) return false;
                token = new Token(input.getLong(), input.getLong());
                if (waiting.putIfAbsent(token, this) != null) return false;
                if (stopped.get() || closed.get()) { waiting.remove(token, this); return false; }
                Registration registration = pending.remove(token);
                if (registration != null) { waiting.remove(token, this); bind(this, token, registration); }
                helloComplete = true;
            }
            while (bytes.hasRemaining()) {
                if (bytes.get() != PING) return false;
                lastSeen = clock.getAsLong();
            }
            return !stopped.get();
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
            if (!stopped.get() && !channel.send(output)) close();
        }
        private void written(int count) {
            if (stopped.get()) return;
            if (count != 1) { close(); return; }
            writing.set(false);
            drain();
        }

        @Override public void close() {
            if (!stopped.compareAndSet(false, true)) return;
            channel.close();
            nativePeers.remove(channel, this);
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
