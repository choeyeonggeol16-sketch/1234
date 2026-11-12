package l1j.server.server.model.network.core;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;

/** Raw companion channels use the same mandatory native IOCP engine as gameplay. */
public final class CTransportEndpoint implements AutoCloseable {
    public interface Handler {
        boolean accept(Peer peer);
        boolean receive(Peer peer, ByteBuffer bytes);
        default void sent(Peer peer, int bytes) {}
        void closed(Peer peer);
    }
    interface Events {
        boolean accept(Peer peer);
        boolean receive(long id, ByteBuffer bytes);
        void sent(long id, int bytes);
        void closed(long id);
    }
    private final CTransportServer server;
    private final ConcurrentHashMap<Long, Peer> peers = new ConcurrentHashMap<>();
    public CTransportEndpoint(InetSocketAddress address, int capacity, Handler handler) throws IOException {
        server = new CTransportServer(address, capacity, 2, 256, null, new Events() {
            public boolean accept(Peer peer) {
                peers.put(peer.id, peer);
                try { return handler.accept(peer); }
                catch (Throwable failure) { peer.close(); throw failure; }
            }
            public boolean receive(long id, ByteBuffer bytes) {
                Peer peer = peers.get(id);
                return peer != null && peer.open && handler.receive(peer, bytes);
            }
            public void sent(long id, int bytes) {
                Peer peer = peers.get(id);
                if (peer != null && peer.open) handler.sent(peer, bytes);
            }
            public void closed(long id) {
                Peer peer = peers.remove(id);
                if (peer != null) { peer.open = false; handler.closed(peer); }
            }
        });
    }
    public int port() throws IOException { return server.localAddress().getPort(); }
    public long schedule(Runnable task, long delayMillis) { return server.schedule(task, delayMillis); }
    public boolean cancel(long token) { return server.cancel(token); }
    @Override public void close() throws IOException {
        if (CTransportServer.isCompletionThread())
            throw new IOException("Close CTransportEndpoint from its owner, outside native callbacks");
        server.close();
    }
    public static final class Peer implements AutoCloseable {
        private final CTransportServer server;
        private final long id;
        private final InetSocketAddress remote;
        private volatile boolean open = true;
        Peer(CTransportServer server, long id, InetSocketAddress remote) { this.server=server;this.id=id;this.remote=remote; }
        public InetSocketAddress remoteAddress() { return remote; }
        public boolean isOpen() { return open; }
        public boolean send(ByteBuffer bytes) { return open && server.send(id, new ByteBuffer[]{bytes}, 0, 1); }
        @Override public void close() { if (open) { open=false;server.disconnect(id); } }
    }
}
