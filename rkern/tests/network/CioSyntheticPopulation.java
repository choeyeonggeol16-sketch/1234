import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;

import l1j.server.server.Account;
import l1j.server.server.authd.CAccountContainer;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.connection.CIOServer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.network.utill.CConnectionState;
import l1j.server.server.shared.EPcStatus;
import l1j.server.server.storage.CacheD;

/** Test-only identity publication after a real encrypted CIOServer connection.
 * Uses prepared identities and world identity registration. The scope covers
 * TCP ownership, login admission, and socket, world and CacheD registration.
 * Fixture logout exercises registry release.
 */
public final class CioSyntheticPopulation {
    private static final int FIRST_ACTOR_ID = 2_000_000;
    private static final int FIRST_ACCOUNT_ID = 3_000_000;
    private static final long FIXTURE_STORE_TIME = Instant.parse("2026-11-26T00:00:00Z").toEpochMilli();

    private final AtomicReferenceArray<Actor> seats;
    private final AtomicReference<RuntimeException> cleanupFailure = new AtomicReference<>();

    public CioSyntheticPopulation(int peers) {
        if (peers <= 0) throw new IllegalArgumentException("Empty test population");
        seats = new AtomicReferenceArray<>(peers);
    }

    public static int actorId(int seat) { return Math.addExact(FIRST_ACTOR_ID, seat); }
    public static int accountId(int seat) { return Math.addExact(FIRST_ACCOUNT_ID, seat); }
    public static String login(int seat) { return "cb_socket" + seat; }
    public static String characterName(int seat) { return login(seat); }

    /** Call from the verified fixture packet on the existing connection owner. */
    public L1PcInstance bind(CUserSocket socket, int seat) {
        if (seat < 0 || seat >= seats.length()) throw new IllegalArgumentException("Unknown seat");
        if (CIOThreadCallback.currentShardIndex() != socket.affinityShard() || !socket.isConnected())
            throw new IllegalStateException("Fixture actor must bind on its connected CIO owner");

        final Actor existing = seats.get(seat);
        if (existing != null) {
            if (socket.getActiveChar() != existing || !socket.ownsReadyGameSession())
                throw new IllegalStateException("Fixture seat used by a different connection");
            return existing;
        }
        if (socket.getAccount() != null || socket.getActiveChar() != null)
            throw new IllegalStateException("Fixture connection is already bound");

        final Actor actor = new Actor(seat);
        actor.setId(actorId(seat));
        actor.setAccountName(login(seat));
        actor.setName(characterName(seat));
        actor.setTitle("");
        actor.setClanname("");
        actor.setClanMemberNotes("");
        actor.setReturnStat(0);
        actor.setLevel(80);
        actor.setFishingShopBuyTime_1(FIXTURE_STORE_TIME);
        actor.setOnlineStatus(1);
        actor.set_instance_status(EPcStatus.WORLD);
        actor.nextSessionStamp();
        if (!seats.compareAndSet(seat, null, actor))
            throw new IllegalStateException("Concurrent fixture seat reuse");

        boolean loginRegistered = false;
        try {
            final Account account = new Account();
            account.setAccountId(accountId(seat));
            account.setName(login(seat));
            account.setValid(true);
            if (!socket.bindVerifiedMacIdentity(accountId(seat)))
                throw new IllegalStateException("Synthetic account identity rejected");
            CAccountContainer.getInstance().login(socket, account);
            loginRegistered = true;
            socket.setAccount(account);
            socket.setConnectionState(CConnectionState.AUTHENTICATED);
            if (CWorld.getInstance().findObject(actor.getId()) != null)
                throw new IllegalStateException("Fixture actor ID already exists");
            CWorld.getInstance().storeObject(actor);
            actor.worldRegistered = true;
            actor.setNetConnection(socket);
            socket.setActiveChar(actor);
            socket.setConnectionState(CConnectionState.IN_GAME);
            actor.setCacheDSessionEpoch(CacheD.getInstance().registerLoadedCharacterSession(
                    actor.getAccountName(), actor.getName(), actor.getId()));
            socket.publishGameSessionReady(actor);
            if (!socket.ownsReadyGameSession()) throw new IllegalStateException("Fixture publication failed");
            return actor;
        } catch (Exception failure) {
            try {
                actor.logout();
                if (loginRegistered) CAccountContainer.getInstance().logout(socket);
            } catch (RuntimeException cleanup) {
                failure.addSuppressed(cleanup);
            }
            try {
                socket.close();
            } catch (RuntimeException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw new IllegalStateException("Cannot publish fixture seat " + seat, failure);
        }
    }

    public L1PcInstance checkActive(CUserSocket socket, int seat) {
        if (seat < 0 || seat >= seats.length()) throw new IllegalArgumentException("Unknown seat");
        final Actor actor = seats.get(seat);
        if (actor == null || socket.getActiveChar() != actor
                || CIOThreadCallback.currentShardIndex() != socket.affinityShard()
                || !socket.ownsReadyGameSession())
            throw new IllegalStateException("Fixture request lacks its live seat " + seat);
        return actor;
    }

    public int published() {
        int ready = 0;
        for (int seat = 0; seat < seats.length(); seat++) {
            final Actor actor = seats.get(seat);
            final CUserSocket socket = actor == null ? null : actor.getNetConnection();
            if (socket != null && socket.ownsReadyGameSession()) ready++;
        }
        return ready;
    }

    public record Snapshot(long tcp, long authenticated, long entered, int loginRegistry,
                           int worldRegistry, int cacheAccounts, int cacheCharacters,
                           int verifiedReady) {}

    private static int[] cacheRegistrySizes() {
        try {
            final CacheD cache = CacheD.getInstance();
            final Field lock = CacheD.class.getDeclaredField("lock");
            final Field accounts = CacheD.class.getDeclaredField("activeAccounts");
            final Field characters = CacheD.class.getDeclaredField("activeUsers");
            lock.setAccessible(true);
            accounts.setAccessible(true);
            characters.setAccessible(true);
            synchronized (lock.get(cache)) {
                return new int[] {((Map<?, ?>) accounts.get(cache)).size(),
                        ((Map<?, ?>) characters.get(cache)).size()};
            }
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Cannot verify fixture CacheD registry", failure);
        }
    }

    /** Run once after the clients have acknowledged the fixture bind phase. */
    public Snapshot assertReady(CIOServer server) {
        final int expected = seats.length();
        final long[] registry = server.accountRegistryDiagnostics();
        final int online = CAccountContainer.getInstance().getOnlinePlayerCount();
        final CWorld world = CWorld.getInstance();
        int ready = 0;
        for (int seat = 0; seat < expected; seat++) {
            final Actor actor = seats.get(seat);
            if (actor == null) continue;
            final CUserSocket socket = actor.getNetConnection();
            final Account account = socket == null ? null : socket.getAccount();
            if (account != null && account.getAccountId() == accountId(seat)
                    && socket.ownsReadyGameSession() && socket.getActiveChar() == actor
                    && world.findObject(actorId(seat)) == actor && actor.getCacheDSessionEpoch() != 0L
                    && server.findEnteringAccount(accountId(seat)) > 0L) ready++;
        }
        final int[] cache = cacheRegistrySizes();
        final Snapshot snapshot = new Snapshot(registry[0], registry[1], registry[2], online,
                world.getAllPlayers().size(), cache[0], cache[1], ready);
        if (snapshot.tcp() != expected || snapshot.authenticated() != expected || snapshot.entered() != expected
                || snapshot.loginRegistry() != expected || snapshot.worldRegistry() != expected
                || snapshot.cacheAccounts() != expected || snapshot.cacheCharacters() != expected
                || snapshot.verifiedReady() != expected)
            throw new AssertionError("Expected " + expected + " published test sessions, got " + snapshot);
        return snapshot;
    }

    /** Transport termination precedes deferred session cleanup. */
    public void assertDrained(CIOServer server) throws InterruptedException {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        int pending;
        while ((pending = server.pendingSessionCleanup()) != 0 && System.nanoTime() - deadline < 0L)
            Thread.sleep(5L);
        if (pending != 0) throw new AssertionError("Deferred fixture sessions did not drain: " + pending);

        final RuntimeException failure = cleanupFailure.get();
        if (failure != null) throw new AssertionError("Fixture actor cleanup failed", failure);
        final long[] registry = server.accountRegistryDiagnostics();
        final int[] cache = cacheRegistrySizes();
        final int online = CAccountContainer.getInstance().getOnlinePlayerCount();
        final int world = CWorld.getInstance().getAllPlayers().size();
        if (registry[0] != 0 || registry[1] != 0 || registry[2] != 0
                || cache[0] != 0 || cache[1] != 0 || online != 0 || world != 0) {
            throw new AssertionError("Registrations remained after close: "
                    + new Snapshot(registry[0], registry[1], registry[2], online, world, cache[0], cache[1], 0));
        }
        for (int seat = 0; seat < seats.length(); seat++) {
            if (seats.get(seat) != null) throw new AssertionError("Fixture actor retained, seat " + seat);
        }
    }

    private final class Actor extends L1PcInstance {
        private final int seat;
        private final AtomicBoolean logoutStarted = new AtomicBoolean();
        private volatile boolean logoutComplete;
        private volatile boolean worldRegistered;

        Actor(int seat) { this.seat = seat; }

        @Override public boolean isLogoutStarted() { return logoutStarted.get(); }
        @Override public boolean isLogoutComplete() { return logoutComplete; }

        @Override public void logout() {
            if (!logoutStarted.compareAndSet(false, true)) return;
            try {
                final long epoch = getCacheDSessionEpoch();
                if (epoch != 0L) CacheD.getInstance().requestCharacterItemOwnerRelease(getId(), epoch);
            } catch (RuntimeException failure) {
                cleanupFailure.compareAndSet(null, failure);
            } finally {
                try {
                    if (worldRegistered) CWorld.getInstance().removeObject(this);
                    setNetConnection(null);
                } catch (RuntimeException failure) {
                    cleanupFailure.compareAndSet(null, failure);
                } finally {
                    logoutComplete = true;
                    seats.compareAndSet(seat, this, null);
                }
            }
        }
    }
}
