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

/** Java async I/O game-session factory; no owner-thread admission handoff. */
public final class CIOSocketFactory implements CIOServer.SocketFactory {
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

    // Timer payloads never retain a disconnected socket/client for their TTL.
    // The live server's connection set owns active sockets until close.
    private static final class LoginTimeout extends WeakReference<CIOClient>
            implements CIOThreadCallback.WheelTask {
        LoginTimeout(CIOClient client) { super(client); }
        @Override public boolean isCancelled() {
            final CIOClient client = get();
            return client == null || client.isCancelled();
        }
        @Override public void run() {
            final CIOClient client = get();
            if (client != null && !client.isCancelled() && client.checkPreLoginTimeout())
                client.disconnect();
        }
    }

    private static final class SocketDeadline extends WeakReference<CIOUserSocket>
            implements CIOThreadCallback.WheelTask, CIOUserSocket.NetworkDeadline {
        private static final long TICK_MS = 5000L;
        private final CIOThreadCallback scheduler;
        private final int shard;
        private long priorProgress;
        private long stalledSinceNs;
        private long nextCheckAtMs;
        SocketDeadline(CIOUserSocket socket, CIOThreadCallback scheduler, int shard) {
            super(socket);
            this.scheduler = scheduler;
            this.shard = shard;
        }
        @Override public boolean isCancelled() {
            final CIOUserSocket socket = get();
            return socket == null || !socket.isOpen();
        }
        @Override public synchronized boolean scheduleEarlier(long delayMs) {
            final CIOUserSocket socket = get();
            if (socket == null || !socket.isOpen()) return false;
            // Match the wheel's monotonic millisecond clock. Coalesce a stale
            // periodic entry after bringing the same task forward for close.
            final long due = System.nanoTime() / 1_000_000L + delayMs;
            if (nextCheckAtMs != 0L && nextCheckAtMs <= due) return true;
            nextCheckAtMs = due;
            return scheduler.scheduleNetworkTimeout(shard, this, delayMs);
        }
        @Override public synchronized void run() {
            final CIOUserSocket socket = get();
            if (socket == null || !socket.isOpen()) return;
            final long now = System.nanoTime();
            if (now / 1_000_000L < nextCheckAtMs) return;
            nextCheckAtMs = 0L;
            final long progress = socket.networkWriteProgress();
            if (socket.hasPendingNetworkSend()) {
                if (stalledSinceNs == 0L || priorProgress != progress)
                    stalledSinceNs = now;
                if (now - stalledSinceNs >= CIOUserSocket.NETWORK_WRITE_STALL_NANOS) {
                    socket.close();
                    return;
                }
            } else {
                stalledSinceNs = 0L;
            }
            priorProgress = progress;
            if (socket.networkDeadlineExpired(now)) {
                socket.close();
                return;
            }
            if (!scheduleEarlier(socket.nextNetworkDeadlineDelayMs(now, TICK_MS))) socket.close();
        }
    }


    @Override
    public CIOUserSocket CreateSocket(CTransportSocket socket,
                                  CIOBufferPool pool,
                                  CIOUserSocket.Closed serverClosed) throws Exception {
        final InetSocketAddress peer = (InetSocketAddress) socket.remoteAddress();
        final InetAddress address = peer.getAddress();
        final String ip = address.getHostAddress();
        boolean counted = false;
        CIOClient user = null;
        CIOUserSocket transport = null;
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
            transport = new CIOUserSocket(socket, pool, scheduler, ip, shard,
                    256,
                    nio -> {
                        try {
                            final CIOClient client = nio.client();
                            if (client != null) client.onTransportClosed();
                        } finally {
                            liveByShard.decrementAndGet(ownerShard);
                            serverClosed.onClosed(nio);
                        }
                    });
            user = new CIOClient(transport, counted);
            transport.bindClient(user);
            final CIOClient client = user;
            if (config.usePreLoginTimeout
                    && !scheduler.scheduleLoginTimeout(shard, new LoginTimeout(client)))
                throw new IllegalStateException("CIO login timeout scheduling failed");
            if (!client.onConnected() || !client.isConnected())
                throw new IllegalStateException("handshake not admitted");
            final SocketDeadline deadline = new SocketDeadline(transport, scheduler, shard);
            transport.bindNetworkDeadline(deadline);
            if (!deadline.scheduleEarlier(SocketDeadline.TICK_MS))
                throw new IllegalStateException("socket deadline scheduling failed");
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
