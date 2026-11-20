package l1j.server.server.model.network.core.server;

import l1j.server.server.model.network.core.connection.CIOBufferPool;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.transport.CSocket;
import l1j.server.server.model.network.core.transport.selector.CSelectorTransport;

import java.net.InetSocketAddress;
import l1j.server.Config;
import l1j.server.server.Controller.LoginController;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.network.utill.CConnectionConfig;

/** L1 game admission with the existing session and timer owners. */
public final class CDummyServerCallback implements IServerCallback {
    private final CIOThreadCallback scheduler;
    private final CConnectionConfig config;
    private final int shardCount;
    private final int maxOnline;

    public CDummyServerCallback(CIOThreadCallback scheduler, CConnectionConfig config) {
        if (scheduler == null || config == null)
            throw new IllegalArgumentException("scheduler/config required");
        this.scheduler = scheduler;
        this.config = config;
        this.shardCount = scheduler.bindNetworkIngress(config);
        this.maxOnline = Config.MAX_ONLINE_USERS;
        if (shardCount < 1 || maxOnline < 1)
            throw new IllegalStateException("invalid game shard or online limit");
        LoginController.getInstance().setMaxAllowedOnlinePlayers(maxOnline);
    }

    public int selectorOwnerCount() { return shardCount; }

    public void attachSelectors(java.util.List<CSelectorTransport.OwnerDriver> owners) { scheduler.attachSelectors(owners); }

    @Override
    public CUserSocket CreateSocket(CSocket socket,
                                  CIOBufferPool pool,
                                  CUserSocket.Closed serverClosed) throws Exception {
        final InetSocketAddress peer = socket.remoteAddress();
        final String ip = peer.getAddress().getHostAddress();
        CUserSocket transport = null;
        try {
            if (peer.getPort() <= 1024 || maxOnline <=
                    LoginController.getInstance().getOnlinePlayerCount())
                throw new IllegalStateException("game connection denied");

            if (!Config.shutdownCheck && !socket.reserveCleanupIp(CConnectionConfig.NETWORK_CLIENT_PERMISSION))
                throw new IllegalStateException("IP connection limit reached");

            final int shard = CIOThreadCallback.currentShardIndex();
            if (shard < 0 || shard >= shardCount || socket.currentOwner() != shard)
                throw new IllegalStateException("game admission must execute on its CIO owner");
            transport = new CUserSocket(socket, pool, scheduler, ip, shard, 256, serverClosed, true);
            if (!transport.onConnected() || !transport.isConnected())
                throw new IllegalStateException("handshake not admitted");
            transport.configureTransportDeadlines(config.usePreLoginTimeout);
            return transport;
        } catch (Exception | Error failure) {
            if (transport != null) {
                transport.close();
                transport.disconnect();
            } else {
                socket.releaseCleanupIp();
            }
            throw failure;
        }
    }

}
