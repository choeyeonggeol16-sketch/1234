package l1j.server.server.model.network.core.connection;

import java.io.DataInputStream;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import l1j.server.Config;
import l1j.server.server.Account;
import l1j.server.server.Opcodes;
import l1j.server.server.authd.AccountAlreadyLoginException;
import l1j.server.server.authd.GameServerFullException;
import l1j.server.server.authd.CAccountContainer;
import l1j.server.server.authd.CAuthD;
import l1j.server.server.model.network.core.config.CIOConfig;
import l1j.server.server.model.network.core.session.CSessionLifecycle;
import l1j.server.server.model.network.safe.CPacketFilter;
import l1j.server.server.model.network.thread.CIOThreadCallback;

/**
 * Real loopback peers and production login ownership transitions.
 * Compile against the game classes and run this class in a fresh JVM without a database.
 */
public final class LoginRegistryRegression {
    private static final CAccountContainer LOGIN = CAccountContainer.getInstance();
    private static final int WAIT_MS = 5_000;

    static void check(boolean result, String label) {
        if (!result) throw new AssertionError(label);
    }

    static void await(BooleanSupplier condition, String label) throws InterruptedException {
        final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(WAIT_MS);
        while (!condition.getAsBoolean() && System.nanoTime() - deadline < 0L) Thread.sleep(1L);
        check(condition.getAsBoolean(), label);
    }

    static CUserSocket connect(CIOServer listener, Socket peer, Field sockets) throws Exception {
        peer.setSoTimeout(WAIT_MS);
        peer.connect(listener.localAddress(), WAIT_MS);
        final DataInputStream input = new DataInputStream(peer.getInputStream());
        final int length = (input.readUnsignedByte() | input.readUnsignedByte() << 8) - 2;
        check(length > 0 && Arrays.equals(input.readNBytes(length), Opcodes.S_KEYBYTES), "key handshake");
        synchronized (listener) {
            for (Object entry : (Set<?>) sockets.get(listener)) {
                final CUserSocket client = (CUserSocket) entry;
                if (client.transportAddress().getPort() == peer.getLocalPort()) return client;
            }
        }
        throw new AssertionError("accepted socket missing from registry");
    }

    private static Account account(String name) {
        final Account account = new Account();
        account.setName(name);
        account.setAccountId(81);
        account.setValid(true);
        return account;
    }

    /** Pause valid registry publications after the admission check. */
    private static final class PausedRegistrations extends ConcurrentHashMap<String, Object> {
        final AtomicInteger atPublication = new AtomicInteger();
        final CountDownLatch publish = new CountDownLatch(1);

        @Override public Object putIfAbsent(String id, Object session) {
            atPublication.incrementAndGet();
            try {
                check(publish.await(WAIT_MS, TimeUnit.MILLISECONDS), "login publication stalled");
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new AssertionError("login publication interrupted", failure);
            }
            return super.putIfAbsent(id, session);
        }
    }

    private static void verifyCapacity(CIOServer listener, Field sockets) throws Exception {
        final int cap = 2;
        final int ordinary = 7;
        final var peers = new ArrayList<Socket>(ordinary + 1);
        final var clients = new ArrayList<CUserSocket>(ordinary + 1);
        final Field sessions = CAccountContainer.class.getDeclaredField("_sessions");
        sessions.setAccessible(true);
        final Object savedSessions = sessions.get(LOGIN);
        final int originalCap = LOGIN.getMaxAllowedOnlinePlayers();
        final var paused = new PausedRegistrations();
        try {
            for (int i = 0; i <= ordinary; i++) {
                final var peer = new Socket();
                peers.add(peer);
                clients.add(connect(listener, peer, sockets));
            }
            LOGIN.setMaxAllowedOnlinePlayers(cap);
            sessions.set(LOGIN, paused);
            final var finished = new AtomicInteger();
            final List<Future<Boolean>> outcomes = new ArrayList<>();
            try (var pool = Executors.newFixedThreadPool(ordinary)) {
                for (int i = 0; i < ordinary; i++) {
                    final var client = clients.get(i);
                    final Account account = account("capacity-fixture-" + i);
                    outcomes.add(pool.submit(() -> {
                        try {
                            LOGIN.login(client, account);
                            return true;
                        } catch (GameServerFullException full) {
                            finished.incrementAndGet();
                            return false;
                        }
                    }));
                }
                try {
                    await(() -> paused.atPublication.get() + finished.get() == ordinary,
                            "all ordinary admissions reached a decision");
                } finally {
                    paused.publish.countDown();
                }
                int accepted = 0;
                for (Future<Boolean> outcome : outcomes)
                    if (outcome.get(WAIT_MS, TimeUnit.MILLISECONDS)) accepted++;
                check(accepted == cap && LOGIN.getOnlinePlayerCount() == cap && paused.size() == cap,
                        "ordinary concurrent admissions exceeded cap");
            }
            final Account gm = account("capacity-fixture-gm");
            gm.setAccessLevel(1);
            LOGIN.login(clients.get(ordinary), gm);
            check(LOGIN.getOnlinePlayerCount() == cap + 1 && paused.size() == cap + 1,
                    "GM admission did not retain its capacity bypass");
        } finally {
            paused.publish.countDown();
            for (Socket peer : peers) peer.close();
            for (CUserSocket client : clients)
                await(client::isClientCleanupComplete, "capacity test socket cleaned");
            sessions.set(LOGIN, savedSessions);
            LOGIN.setMaxAllowedOnlinePlayers(originalCap);
        }
        check(LOGIN.getOnlinePlayerCount() == 0 && LOGIN.getIpCount("127.0.0.1") == 0,
                "capacity reservations leaked after disconnect");
    }

    public static void main(String[] args) throws Exception {
        final int max = 16;
        Config.MAX_ONLINE_USERS = max;
        CPacketFilter.getInstance().load();
        LOGIN.setReloginGrace(Duration.ofSeconds(60));
        final var owners = CIOThreadCallback.getInstance();
        final var target = new InetSocketAddress("127.0.0.1", 0);
        final var config = new CIOConfig(target);
        config.packetExecutorShardCount = 2;
        config.usePreLoginTimeout = false;
        CIOConfig.NETWORK_CLIENT_PERMISSION = max;
        final int count = owners.bindNetworkIngress(config);
        final var pool = new CIOBufferPool(max, max * 2, max, max, count);
        final Field sockets = CIOServer.class.getDeclaredField("sockets");
        sockets.setAccessible(true);
        try (var listener = new CIOServer(target, max, count, max, pool, owners, config)) {
            if (args.length == 0) verifyOwnership(listener, sockets);
            verifyCapacity(listener, sockets);
            verifyIpCapacity(listener, sockets);
        } finally {
            owners.shutdown();
            check(owners.awaitTermination(5, TimeUnit.SECONDS), "network owners did not join");
            CSessionLifecycle.stopAccepting();
            check(CSessionLifecycle.awaitDrained(5, TimeUnit.SECONDS), "session workers did not join");
        }
        check(ManagementFactory.getThreadMXBean().findDeadlockedThreads() == null, "JVM detected a deadlock");
        System.out.println("PASS login registry ownership, concurrent capacity, GM bypass and teardown");
    }

    private static void verifyIpCapacity(CIOServer listener, Field sockets) throws Exception {
        final var admit = CAccountContainer.class.getDeclaredMethod(
                "loginWithinIpLimit", CUserSocket.class, Account.class, int.class);
        admit.setAccessible(true);
        final Locale previousLocale = Locale.getDefault();
        try (var firstPeer = new Socket(); var secondPeer = new Socket();
                var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            final var first = connect(listener, firstPeer, sockets);
            final var second = connect(listener, secondPeer, sockets);
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            var start = new CountDownLatch(1);
            var a = pool.submit(() -> { start.await(); return (Boolean) admit.invoke(LOGIN, first, account(" FIRST "), 1); });
            var b = pool.submit(() -> { start.await(); return (Boolean) admit.invoke(LOGIN, second, account(" SECOND "), 1); });
            start.countDown();
            boolean firstWon = a.get(WAIT_MS, TimeUnit.MILLISECONDS);
            boolean secondWon = b.get(WAIT_MS, TimeUnit.MILLISECONDS);
            check(firstWon != secondWon && LOGIN.getIpCount("127.0.0.1") == 1
                    && LOGIN.getOnlinePlayerCount() == 1, "only one password admission reserves the IP");
            check(LOGIN.getClientByAccount(firstWon ? "first" : "second") == (firstWon ? first : second),
                    "canonical account ownership does not depend on JVM locale");
            check(!LOGIN.logout(firstWon ? second : first), "losing admission left an ownership key");
            firstPeer.close();
            secondPeer.close();
            await(first::isClientCleanupComplete, "IP-cap first socket cleaned");
            await(second::isClientCleanupComplete, "IP-cap second socket cleaned");
        } finally {
            Locale.setDefault(previousLocale);
        }
        check(LOGIN.getOnlinePlayerCount() == 0 && LOGIN.getIpCount("127.0.0.1") == 0,
                "IP count and online count returned to zero");
    }

    private static void verifyOwnership(CIOServer listener, Field sockets) throws Exception {
        try (var oldPeer = new Socket(); var replacementPeer = new Socket(); var preAuthPeer = new Socket()) {
            final CUserSocket old = connect(listener, oldPeer, sockets);
            final CUserSocket replacement = connect(listener, replacementPeer, sockets);
            final CUserSocket preAuth = connect(listener, preAuthPeer, sockets);
            LOGIN.login(old, account("  Registry-Fixture  "));
            check(old.getAccount() == null && LOGIN.getClientByAccount("registry-fixture") == old,
                    "registration owns its canonical key before Account publication");
            try {
                LOGIN.login(replacement, account("registry-fixture"));
                throw new AssertionError("duplicate gained ownership before Account publication");
            } catch (AccountAlreadyLoginException expected) {
                check(LOGIN.getClientByAccount("registry-fixture") == old, "duplicate changed owner");
            }

            preAuthPeer.close();
            await(preAuth::isClientCleanupComplete, "pre-auth socket cleaned");
            check(LOGIN.getOnlinePlayerCount() == 1 && LOGIN.getIpCount(old.getIp()) == 1
                    && LOGIN.getCClassCount(old.getIp()) == 1, "pre-auth close changed live counters");

            oldPeer.close();
            await(old::isClientCleanupComplete, "first session cleaned");
            check(LOGIN.getOnlinePlayerCount() == 0 && LOGIN.getClientByAccount("registry-fixture") == null,
                    "cleanup missed an owner without an Account object");
            final Account next = account("registry-fixture");
            check(replacement.bindVerifiedMacIdentity(81L), "replacement MAC registered");
            CAuthD.attachHandoff(replacement, next);
            check(replacement.getAccount() == next && replacement.getConnectionState() ==
                    l1j.server.server.model.network.utill.CConnectionState.AUTHENTICATED,
                    "verified handoff publishes the account and connection state together");
            check(!LOGIN.logout(old) && LOGIN.getClientByAccount("registry-fixture") == replacement,
                    "late cleanup removed replacement");
            check(LOGIN.logout(replacement) && !LOGIN.logout(replacement), "explicit logout is not idempotent");
            check(LOGIN.getOnlinePlayerCount() == 0 && LOGIN.getIpCount(old.getIp()) == 0
                    && LOGIN.getCClassCount(old.getIp()) == 0, "session counters did not drain");
            replacementPeer.close();
            await(replacement::isClientCleanupComplete, "replacement socket cleaned");
        }
    }
}
