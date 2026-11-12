package l1j.server.server.model.network.core;

import java.net.InetAddress;
import java.lang.ref.WeakReference;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import l1j.server.Config;
import l1j.server.server.Controller.LoginController;
import l1j.server.server.model.network.client.manager.NetworkClientCounter;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.network.utill.ConnectionConfig;

/** Native IOCP admission factory with an L1 session/timer owner. */
public final class CIOSocketFactory implements Server.SocketFactory {
    private final CIOThreadCallback scheduler;
    private final ConnectionConfig config;
    private final int shardCount;
    private final AtomicInteger nextShard = new AtomicInteger();
    private final AtomicIntegerArray liveByShard;
    private final int maxOnline;

    public CIOSocketFactory(CIOThreadCallback scheduler, ConnectionConfig config) {
        if (scheduler == null || config == null)
            throw new IllegalArgumentException("scheduler/config required");
        this.scheduler = scheduler;
        this.config = config;
        this.shardCount = scheduler.bindNetworkIngress(config);
        this.liveByShard = new AtomicIntegerArray(shardCount);
        this.maxOnline = Config.MAX_ONLINE_USERS;
        if (shardCount < 1 || maxOnline < 1)
            throw new IllegalStateException("invalid game shard or online limit");
        LoginController.getInstance().setMaxAllowedOnlinePlayers(maxOnline);
    }

    @Override
    public CUserSocket CreateSocket(CSocket socket,
                                  CIOBufferPool pool,
                                  CUserSocket.Closed serverClosed) throws Exception {
        final InetSocketAddress peer = (InetSocketAddress) socket.remoteAddress();
        final InetAddress address = peer.getAddress();
        final String ip = address.getHostAddress();
        boolean counted = false;
        CIOClient user = null;
        CUserSocket transport = null;
        int assignedShard = -1;
        try {
            // Preserve the existing IPv4 game-client admission contract.
            if (!(address instanceof Inet4Address))
                throw new IllegalStateException("IPv6 game client not configured");
            if (peer.getPort() <= 1024 || maxOnline <=
                    LoginController.getInstance().getOnlinePlayerCount())
                throw new IllegalStateException("game connection denied");

            if (!Config.shutdownCheck) {
                if (NetworkClientCounter.getInstance().isPermission(address) < 0)
                    throw new IllegalStateException("IP connection limit reached");
                counted = true;
            }
            
            int shard = CIOThreadCallback.currentShardIndex();
            if (shard < 0 || shard >= shardCount) {
                final int start = (nextShard.getAndIncrement() & Integer.MAX_VALUE) % shardCount;
                shard = start;
                long best = Long.MAX_VALUE;
                for (int k = 0; k < shardCount; k++) {
                    final int candidate = (start + k) % shardCount;
                    final long score = ((long) liveByShard.get(candidate) << 5)
                            + scheduler.networkIngressPressure(candidate);
                    if (score < best) { best = score; shard = candidate; }
                }
            }
            liveByShard.incrementAndGet(shard);
            assignedShard = shard;
            final int ownerShard = shard;
            transport = new CUserSocket(socket, pool, scheduler, ip, shard,
                    256,
                    new CUserSocket.Closed() {
                        @Override public void onClosing(CUserSocket nio) { serverClosed.onClosing(nio); }
                        @Override public void onCleanupComplete(CUserSocket nio) { serverClosed.onCleanupComplete(nio); }
                        @Override public void onClosed(CUserSocket nio) {
                            try {
                                final CIOClient client = nio.client();
                                if (client != null) client.onTransportClosed();
                                else nio.onClientCleanupComplete();
                            } finally {
                                liveByShard.decrementAndGet(ownerShard);
                                serverClosed.onClosed(nio);
                            }
                        }
                    });
            user = new CIOClient(transport, counted);
            transport.bindClient(user);
            final CIOClient client = user;
            if (!client.onConnected() || !client.isConnected())
                throw new IllegalStateException("handshake not admitted");
            transport.configureNativeDeadlines(config.usePreLoginTimeout);
            return transport;
        } catch (Exception | Error failure) {
            if (transport != null) transport.close();
            else if (assignedShard >= 0) liveByShard.decrementAndGet(assignedShard);
            if (user != null) {
                // The normal game session cleanup owns the address counter from here.
                user.disconnect();
            } else if (counted) {
                NetworkClientCounter.getInstance().decrease(address);
            }
            throw failure;
        }
    }

}
