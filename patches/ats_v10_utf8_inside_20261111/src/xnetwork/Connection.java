package xnetwork;
import l1j.server.server.network.CloseReason;
import l1j.server.server.network.NettySession;
/** Compatibility view for the old GameClient API; all I/O is owned by Netty 4.2. */
public final class Connection {
    private final NettySession session;
    public Connection(NettySession session) { this.session = session; }
    public boolean isOpen() { return session.isOpen(); }
    public String getHostAddress() { return session.remoteIp(); }
    public void close() { session.close(CloseReason.PEER_CLOSED); }
}
