import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.DataInputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import l1j.server.Config;
import l1j.server.DatabaseFactory;
import l1j.server.server.Account;
import l1j.server.server.Opcodes;
import l1j.server.server.datatables.CObjectDB;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.HpMpGrowthHistory;
import l1j.server.server.model.network.core.config.CIOConfig;
import l1j.server.server.model.network.core.connection.CIOBufferPool;
import l1j.server.server.model.network.core.connection.CIOServer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.packet.CBytesPacket;
import l1j.server.server.model.network.packet.UserPacketCallbackMap;
import l1j.server.server.model.network.safe.CPacketFilter;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.storage.CacheD;
import l1j.server.server.storage.cached.CTransaction;
import l1j.server.server.storage.mysql.MySqlCharacterStorage.CharacterSnapshot;
import l1j.server.server.storage.mysql.MySqlCharactersItemStorage;

/** Real loopback TCP and production CIO/CacheD, with synthetic session installation and actions.
 * Run in a fresh private working directory. See CacheDNetMixedLoad for JDBC environment.
 * Arguments: connected population (default 5120), repeating active subset (default 1024).
 * Use -Drkern.population.soakRounds=15 for sixty seconds of repeated full-population work.
 * Use -Drkern.population.allStorage=true for a separate two-second full-population storage burst.
 * Use -Drkern.population.outageOnly=true for controlled JDBC checkout failure and recovery.
 * Use -Drkern.population.serverOutageOnly=true with an external disposable DB stop/restart.
 * The test creates db-stop.ready and db-start.ready, then waits for db-stopped and db-restored.
 * -Drkern.population.observers=N controls only the fixture's blocking save waiters (default six).
 */
public final class CacheDCioPopulationLoad {
    private static final int REQUEST = Opcodes.C_ALIVE & 255;
    private static final int RESPONSE = 0x45;
    private static final int ATTACH = 0, ECHO = 1, RECEIPT = 2, ACCOUNT = 3, CHARACTER = 4, LOGIN = 5, INVENTORY = 6, ITEM_SAVE = 7;
    private static final String[] KIND = {"attach", "echo", "receipt", "account_save", "character_save", "account_read", "inventory_read", "item_save_duplicates"};
    private static final int INVENTORY_ROWS = 8;
    private static final int INITIAL_BALANCE = 10000;
    private static final long SNAPSHOT_START = 1795651200000L;

    private final int population;
    private final int active;
    private final AtomicReference<Throwable> failure = new AtomicReference<>();
    private final AtomicIntegerArray receiptCounts;
    private final AtomicIntegerArray accountSaves;
    private final AtomicIntegerArray characterSaves;
    private final AtomicIntegerArray itemSaves;
    private final AtomicIntegerArray observedActors;
    private volatile int phaseNumber;
    private final ConcurrentLinkedQueue<String> receiptIds = new ConcurrentLinkedQueue<>();
    private final MySqlCharactersItemStorage itemStore = new MySqlCharactersItemStorage();
    private final ThreadPoolExecutor observers;
    private final boolean outageOnly = Boolean.getBoolean("rkern.population.outageOnly");
    private final boolean serverOutageOnly = Boolean.getBoolean("rkern.population.serverOutageOnly");
    private HikariDataSource db;
    private CacheD cache;
    private CIOServer server;
    private CioSyntheticPopulation actors;

    CacheDCioPopulationLoad(int population, int active) {
        this.population = population;
        this.active = active;
        check(!outageOnly || !serverOutageOnly, "select one outage fixture");
        receiptCounts = new AtomicIntegerArray(population);
        accountSaves = new AtomicIntegerArray(population);
        characterSaves = new AtomicIntegerArray(population);
        itemSaves = new AtomicIntegerArray(population);
        observedActors = new AtomicIntegerArray(population);
        int observerThreads = Integer.getInteger("rkern.population.observers", 6);
        check(observerThreads > 0 && observerThreads <= 128, "invalid fixture observer count");
        observers = (ThreadPoolExecutor) Executors.newFixedThreadPool(observerThreads);
    }

    public static void main(String[] args) throws Exception {
        int population = args.length == 0 ? 5120 : Integer.parseInt(args[0]);
        int active = args.length < 2 ? 1024 : Integer.parseInt(args[1]);
        check(population > 0 && population <= 10000 && active > 0 && active <= population, "invalid population");
        new CacheDCioPopulationLoad(population, active).run();
    }

    private void run() throws Exception {
        String jdbc = Objects.requireNonNull(System.getenv("RKERN_BENCH_JDBC"), "RKERN_BENCH_JDBC");
        String user = Objects.requireNonNull(System.getenv("RKERN_BENCH_USER"), "RKERN_BENCH_USER");
        String password = System.getenv().getOrDefault("RKERN_BENCH_PASSWORD", "");
        String reference = Objects.requireNonNull(System.getenv("RKERN_BENCH_REFERENCE_SCHEMA"), "RKERN_BENCH_REFERENCE_SCHEMA");
        check(reference.matches("[0-9A-Za-z_]+"), "invalid reference schema");
        String schema = "rkern_population_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Files.createDirectory(Path.of("config"));
        Files.writeString(Path.of("config/Network.ini"), "DisruptorShardCount=2\nNetworkClientPermission=" + (population + 64)
                + "\nNetworkAddress2Account=" + (population + 64) + "\nMaxPacketsPerCompletion=32\nPacketLimitPerSec=10000\nDropPackets=false\nUsePreLoginTimeout=false\n");
        Files.writeString(Path.of("etc-templates.txt"), "40001\n80500\n");
        Files.createFile(Path.of("equip-templates.txt"));
        System.setProperty("rkern.etc.base", Path.of("etc-templates.txt").toAbsolutePath().toString());
        System.setProperty("rkern.equipment.base", Path.of("equip-templates.txt").toAbsolutePath().toString());
        Config.CACHE_MAP_FILES = true;
        Config.TIME_ZONE = "UTC";
        Config.MAX_ONLINE_USERS = (short) (population + 64);
        java.util.logging.Logger.getLogger("l1j.server.server.auth.login.LoginController").setLevel(java.util.logging.Level.WARNING);
        try (var admin = DriverManager.getConnection(jdbc, user, password); var ddl = admin.createStatement()) {
            ddl.execute("CREATE DATABASE `" + schema + "` DEFAULT CHARACTER SET utf8mb4");
            try {
                for (String table : CacheDNetMixedLoad.TABLES) ddl.execute("CREATE TABLE `" + schema + "`.`" + table
                        + "` LIKE `" + reference + "`.`" + table + "`");
                ddl.execute("CREATE TABLE `" + schema + "`.`mapids` LIKE `" + reference + "`.`mapids`");
                System.out.println("SCHEMA " + schema);
                var config = new HikariConfig();
                config.setJdbcUrl(jdbc);
                config.setUsername(user);
                config.setPassword(password);
                config.setCatalog(schema);
                config.setMaximumPoolSize(8);
                config.setMinimumIdle(8);
                if (outageOnly || serverOutageOnly) config.setConnectionTimeout(1000);
                config.setTransactionIsolation("TRANSACTION_REPEATABLE_READ");
                try (var source = new HikariDataSource(config)) {
                    db = source;
                    var pool = DatabaseFactory.class.getDeclaredField("DATABASE_POOL");
                    pool.setAccessible(true);
                    pool.set(null, source);
                    seed();
                    l1j.server.server.model.CWorld.getInstance();
                    exercise();
                }
            } finally {
                if (serverOutageOnly) {
                    // The original admin session also disconnected with MariaDB.
                    try (var adminAfterRestart = DriverManager.getConnection(jdbc, user, password);
                         var remove = adminAfterRestart.createStatement()) {
                        remove.execute("DROP DATABASE `" + schema + "`");
                    }
                } else {
                    ddl.execute("DROP DATABASE `" + schema + "`");
                }
            }
        }
    }

    private void seed() throws Exception {
        try (var con = db.getConnection(); var sql = con.createStatement()) {
            sql.execute("CREATE TABLE bench_population_balance (owner_id INT PRIMARY KEY,balance INT NOT NULL) ENGINE=InnoDB");
            sql.execute("INSERT INTO etcitem(item_id,name,name_id,item_type,use_type,material) VALUES "
                    + "(40001,'Fixture token','fixture_token','questitem','none','none'),"
                    + "(80500,'Fixture key','fixture_key','questitem','none','none')");
            try (var account = con.prepareStatement("INSERT INTO accounts(id,login,charslot,gamepassword,Shop_open_count) VALUES(?,?,8,0,0)");
                 var character = con.prepareStatement("INSERT INTO characters(objid,char_name,account_name,Clanname,notes,ReturnStat) VALUES(?,?,?,'','',0)");
                 var balance = con.prepareStatement("INSERT INTO bench_population_balance VALUES(?,?)");
                 var item = con.prepareStatement("INSERT INTO character_items(id,char_id,item_id,item_name,count) VALUES(?,?,?,'Fixture',1)");
                 var key = con.prepareStatement("INSERT INTO item_key_boss(item_obj_id,key_id) VALUES(?,601)")) {
                con.setAutoCommit(false);
                for (int i = 0; i < population; i++) {
                    int actor = CioSyntheticPopulation.actorId(i);
                    String name = CioSyntheticPopulation.login(i);
                    account.setInt(1, CioSyntheticPopulation.accountId(i));
                    account.setString(2, name);
                    account.addBatch();
                    character.setInt(1, actor);
                    character.setString(2, name);
                    character.setString(3, name);
                    character.addBatch();
                    balance.setInt(2, INITIAL_BALANCE);
                    balance.setInt(1, actor);
                    balance.addBatch();
                    balance.setInt(1, escrow(i));
                    balance.addBatch();
                    for (int slot = 0; slot < INVENTORY_ROWS; slot++) {
                        int itemId = 100_000 + i * INVENTORY_ROWS + slot;
                        item.setInt(1, itemId);
                        item.setInt(2, actor);
                        item.setInt(3, slot < 2 ? 80500 : 40001);
                        item.addBatch();
                        if (slot < 2) {
                            key.setInt(1, itemId);
                            key.addBatch();
                        }
                    }
                    if ((i + 1) % 128 == 0 || i + 1 == population) {
                        for (var batch : List.of(account, character, balance, item, key)) batch.executeBatch();
                        con.commit();
                    }
                }
            }
        }
        CTransaction.verifySchema();
        check(CObjectDB.GetItem(80500) != null, "fixture item prototypes");
    }

    private void exercise() throws Exception {
        cache = CacheD.getInstance();
        check(cache.flushAll(5000), "initial CacheD schema probe drained");
        actors = new CioSyntheticPopulation(population);
        var scheduler = CIOThreadCallback.getInstance();
        var address = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
        var config = new CIOConfig(address);
        int owners = scheduler.bindNetworkIngress(config);
        int capacity = population + Math.max(256, population / 8);
        var pool = new CIOBufferPool(capacity, 3 * capacity, 256, 512, owners);
        System.out.printf("CONFIG observers=%d jdbc_pool=%d cache_workers=%d%n", observers.getCorePoolSize(),
                db.getMaximumPoolSize(), ((Thread[]) privateField(cache, "workers")).length);
        CPacketFilter.getInstance().load();
        var growth = new HpMpGrowthHistory(40);
        for (int level = 41; level <= 80; level++) growth.record(level, 10 + level % 3, 4 + level % 2);
        final String encodedGrowth = growth.encode();
        var old = UserPacketCallbackMap.gPacketTableFromClient[REQUEST];
        UserPacketCallbackMap.gPacketTableFromClient[REQUEST] = (payload, socket) -> {
            final int ordinal = payload.readInt();
            final int sequence = payload.readInt();
            final int kind = payload.readUnsignedByte();
            try {
                if (kind == ATTACH) {
                    L1PcInstance pc = actors.bind(socket, ordinal);
                    pc.setClanname("");
                    pc.setClanMemberNotes("");
                    pc.setLevel(80);
                    pc.setFishingShopBuyTime_1(SNAPSHOT_START);
                    pc.restoreHpMpGrowth(encodedGrowth);
                } else {
                    L1PcInstance pc = actors.checkActive(socket, ordinal);
                    int phase = phaseNumber;
                    if (observedActors.get(ordinal) != phase) observedActors.set(ordinal, phase);
                    if (kind != ECHO) {
                        perform(kind, ordinal, sequence, pc, socket);
                        return true;
                    }
                }
                reply(socket, ordinal, sequence, kind);
                return true;
            } catch (Throwable problem) {
                if (failure.compareAndSet(null, problem)) problem.printStackTrace(System.err);
                return false;
            }
        };
        Client[] clients = new Client[population];
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            server = new CIOServer(address, capacity, owners, Math.min(capacity, 1024), pool, scheduler, config);
            long connectStart = System.nanoTime();
            var gate = new Semaphore(96);
            List<Future<?>> openings = new ArrayList<>(population);
            for (int i = 0; i < population; i++) {
                final int index = i;
                openings.add(executor.submit(() -> {
                    gate.acquire();
                    try { clients[index] = new Client(server.localAddress(), index); }
                    finally { gate.release(); }
                    return null;
                }));
            }
            for (var opening : openings) opening.get(35, TimeUnit.SECONDS);
            System.out.printf(Locale.ROOT, "CONNECT elapsed_ms=%.1f target=%d owners=%d%n",
                    (System.nanoTime() - connectStart) / 1e6, population, owners);
            sample("connected");
            Thread.sleep(750);
            sample("idle");
            churn(executor, population >= 1024 ? 256 : 8);
            sample("after_churn");
            // A real restart resets MariaDB's global status counters.
            long deadlocksBefore = serverOutageOnly ? 0 : status("Innodb_deadlocks");
            long locksBefore = serverOutageOnly ? 0 : status("Innodb_row_lock_waits");
            long commitsBefore = serverOutageOnly ? 0 : status("Com_commit");
            if (serverOutageOnly) {
                serverOutage(executor, clients);
            } else if (outageOnly) {
                outage(executor, clients);
            } else {
                phase(executor, clients, "all_actors", population, 1, 2800, false);
                phase(executor, clients, "steady", active, 3, 1600, false);
                phase(executor, clients, "burst", active, 1, 160, true);
                phase(executor, clients, "recover", active, 2, 1800, false);
                int soak = Integer.getInteger("rkern.population.soakRounds", 0);
                if (soak > 0) phase(executor, clients, "soak", population, soak, 4000, false);
                if (Boolean.getBoolean("rkern.population.allStorage"))
                    phase(executor, clients, "all_db", population, 1, 2000, false, true);
            }
            check(cache.flushAll(8000), "final storage drain");
            verifyState();
            long deadlocks = status("Innodb_deadlocks") - deadlocksBefore;
            check(deadlocks == 0 && ManagementFactory.getThreadMXBean().findDeadlockedThreads() == null, "deadlocks under load");
            System.out.printf("DB_WORK explicit_commits=%d row_lock_waits=%d innodb_deadlocks=%d jvm_deadlocks=0%n",
                    status("Com_commit") - commitsBefore, status("Innodb_row_lock_waits") - locksBefore, deadlocks);
            sample("verified");
        } catch (Throwable problem) {
            problem.printStackTrace(System.err);
            throw problem;
        } finally {
            long disconnectStart = System.nanoTime();
            try {
                for (Client client : clients) if (client != null) client.close();
                if (server != null) {
                    server.close(20, TimeUnit.SECONDS);
                    check(server.awaitTermination(Duration.ofSeconds(10)), "listener close");
                    check(server.accountRegistryDiagnostics()[0] == 0, "all transport sockets closed");
                    for (long[] bucket : server.poolSnapshot()) check(bucket[2] == 0, "direct leases drained");
                }
                if (server != null) {
                    actors.assertDrained(server);
                    check(cache.flushAll(5000), "terminal CacheD owner removals finished");
                }
                System.out.printf(Locale.ROOT, "DISCONNECT elapsed_ms=%.1f sockets=0 actors=0%n",
                        (System.nanoTime() - disconnectStart) / 1e6);
            } finally {
                UserPacketCallbackMap.gPacketTableFromClient[REQUEST] = old;
                l1j.server.server.model.network.core.session.CSessionLifecycle.stopAccepting();
                check(l1j.server.server.model.network.core.session.CSessionLifecycle.awaitDrained(5, TimeUnit.SECONDS),
                        "terminal release workers drained");
                observers.shutdown();
                check(observers.awaitTermination(5, TimeUnit.SECONDS), "observers terminate");
                final boolean drained;
                try {
                    drained = cache.drainBeforeOwnerShutdown(8000);
                } finally {
                    scheduler.shutdown();
                    check(scheduler.awaitTermination(8, TimeUnit.SECONDS), "CIO owner threads joined");
                }
                check(drained && cache.shutdown(8000), "CacheD workers joined");
            }
        }
    }

    private void sample(String phase) throws Exception {
        var snapshot = actors.assertReady(server);
        long bytes = 0, leases = 0;
        for (long[] bucket : server.poolSnapshot()) {
            leases += bucket[2];
            bytes += bucket.length >= 7 ? bucket[5] : bucket[3];
        }
        long heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
        System.out.printf("POPULATION %s sockets=%d authenticated=%d entered=%d verified_ready=%d world_actors=%d owners=%d leases=%d direct_bytes=%d heap_used_bytes=%d%n",
                phase, snapshot.tcp(), snapshot.authenticated(), snapshot.entered(), snapshot.verifiedReady(),
                snapshot.worldRegistry(), server.ownerDiagnostics().size(), leases, bytes, heap);
    }

    private void outage(ExecutorService executor, Client[] clients) throws Exception {
        check(population >= 32, "outage test needs at least 32 clients");
        check(cache.flushAll(5000), "outage starts with a drained CacheD");

        // Exhaust only this test's pool. The daemon and other sandbox schemas
        // remain available throughout the failure and recovery.
        Throwable previousFailure = cache.lastFailure();
        String account = CioSyntheticPopulation.login(0);
        Future<?> accountSave;
        double echoDuringFailure;
        try (var held = new PoolHold()) {
            accountSave = executor.submit(() -> { clients[0].exchange(ACCOUNT); return null; });
            awaitCheckout();
            echoDuringFailure = echoes(executor, clients, 1, 16);
            awaitConnectionFailure(previousFailure, false);
            check(!accountSave.isDone() && cache.pendingAccounts() == 1,
                    "failed checkout retains one dirty account and emits no success");
        }
        accountSave.get(5, TimeUnit.SECONDS);
        check(cache.flushAll(5000), "account retry drained after pool recovery");
        verifyAccountRetry(account);

        int blocked = Math.min(population / 2, 128);
        var storage = new ArrayList<Future<?>>(blocked);
        double echoDuringHold;
        int queued;
        try (var held = new PoolHold()) {
            for (int i = 0; i < blocked; i++) {
                Client client = clients[i];
                int kind = RECEIPT + i % (KIND.length - RECEIPT);
                storage.add(executor.submit(() -> { client.exchange(kind); return null; }));
            }
            awaitCheckout();
            echoDuringHold = echoes(executor, clients, blocked, 16);
            queued = cache.pendingDbJobs();
            check(queued > 0 && queued <= blocked && storage.stream().noneMatch(Future::isDone),
                    "outage queues owner work within submitted count and emits no durable success");
            sample("db_checkout_blocked");
        }
        for (var task : storage) task.get(8, TimeUnit.SECONDS);
        System.out.printf(Locale.ROOT,
                "OUTAGE checkout_failure_verified=1 dirty_retry=1 held_requests=%d sampled_pending_db=%d "
                + "echo_failure_max_ms=%.3f echo_hold_max_ms=%.3f recovered=%d sockets=%d%n",
                blocked, queued, echoDuringFailure, echoDuringHold, storage.size(), population);
    }

    private void serverOutage(ExecutorService executor, Client[] clients) throws Exception {
        check(population >= 32, "server outage test needs at least 32 clients");
        check(cache.flushAll(5000), "server outage starts with a drained CacheD");
        Throwable previousFailure = cache.lastFailure();
        Files.writeString(Path.of("db-stop.ready"), db.getCatalog());
        awaitSignal("db-stopped");

        var accountSave = executor.submit(() -> { clients[0].exchange(ACCOUNT); return null; });
        awaitConnectionFailure(previousFailure, true);
        check(!accountSave.isDone() && cache.pendingAccounts() == 1,
                "no account success while MariaDB is stopped");
        double offlineEchoMax = echoes(executor, clients, 1, 16);
        sample("mariadb_stopped");
        Files.createFile(Path.of("db-start.ready"));
        awaitSignal("db-restored");

        accountSave.get(7, TimeUnit.SECONDS);
        verifyAccountRetry(CioSyntheticPopulation.login(0));
        check(receiptIds.isEmpty(), "no receipt from the stopped database");
        clients[0].exchange(RECEIPT);
        clients[1].exchange(ITEM_SAVE);
        clients[2].exchange(CHARACTER);
        clients[3].exchange(INVENTORY);
        check(receiptIds.size() == 1, "exactly one fresh receipt after restart");
        sample("mariadb_restored");
        System.out.printf(Locale.ROOT,
                "RESTART checkout_failure_verified=1 dirty_retry=1 offline_echoes=16 "
                + "offline_echo_max_ms=%.3f fresh_receipts=1 sockets=%d%n", offlineEchoMax, population);
    }

    private static void awaitSignal(String marker) throws InterruptedException {
        var path = Path.of(marker);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!Files.exists(path)) {
            check(System.nanoTime() < deadline, "DB harness did not signal " + marker);
            Thread.sleep(10);
        }
    }

    private void verifyAccountRetry(String account) throws SQLException {
        try (var con = db.getConnection(); var query = con.prepareStatement(
                "SELECT UNIX_TIMESTAMP(raid_buff)*1000 FROM accounts WHERE login=?")) {
            query.setString(1, account);
            try (var row = query.executeQuery()) {
                check(row.next() && row.getLong(1) == SNAPSHOT_START + 1000L * accountSaves.get(0),
                        "the failed account snapshot was durably retried");
            }
        }
    }

    private void awaitCheckout() {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(600);
        while (db.getHikariPoolMXBean().getThreadsAwaitingConnection() == 0) {
            check(System.nanoTime() < deadline, "CacheD reached the exhausted connection pool");
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        }
    }

    private void awaitConnectionFailure(Throwable previousFailure, boolean serverStopped) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (cache.lastFailure() == previousFailure) {
            check(System.nanoTime() < deadline, "the account save failed its JDBC checkout");
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        }
        for (Throwable cause = cache.lastFailure(); cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLTransientConnectionException) return;
            if (serverStopped && cause instanceof SQLException sql && sql.getSQLState() != null
                    && sql.getSQLState().startsWith("08")) return;
        }
        throw new AssertionError("expected Hikari checkout timeout", cache.lastFailure());
    }

    private static double echoes(ExecutorService executor, Client[] clients, int first, int count) throws Exception {
        var replies = new ArrayList<Future<Long>>(count);
        for (int i = first; i < first + count; i++) {
            Client client = clients[i];
            replies.add(executor.submit(() -> {
                long begin = System.nanoTime();
                client.exchange(ECHO);
                return System.nanoTime() - begin;
            }));
        }
        long max = 0;
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(350);
        for (var reply : replies) max = Math.max(max, reply.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS));
        return max / 1e6;
    }

    private final class PoolHold implements AutoCloseable {
        private final List<Connection> held = new ArrayList<>(db.getMaximumPoolSize());

        PoolHold() throws SQLException {
            try {
                for (int i = 0; i < db.getMaximumPoolSize(); i++) held.add(db.getConnection());
            } catch (SQLException failure) {
                try { close(); } catch (SQLException cleanup) { failure.addSuppressed(cleanup); }
                throw failure;
            }
        }

        @Override
        public void close() throws SQLException {
            SQLException failure = null;
            for (Connection connection : held) {
                try { connection.close(); } catch (SQLException unableToClose) {
                    if (failure == null) failure = unableToClose;
                    else failure.addSuppressed(unableToClose);
                }
            }
            if (failure != null) throw failure;
        }
    }

    /** Anonymous TCP peers join and close while every published actor stays live. */
    private void churn(ExecutorService executor, int attempts) throws Exception {
        var gate = new Semaphore(16);
        var jobs = new ArrayList<Future<?>>();
        long start = System.nanoTime();
        for (int i = 0; i < attempts; i++) {
            jobs.add(executor.submit(() -> {
                gate.acquire();
                try (var socket = new Socket()) {
                    socket.setSoTimeout(5000);
                    socket.connect(server.localAddress(), 5000);
                    var input = new DataInputStream(socket.getInputStream());
                    int length = input.readUnsignedByte() | (input.readUnsignedByte() << 8);
                    check(length == Opcodes.S_KEYBYTES.length + 2
                            && Arrays.equals(input.readNBytes(length - 2), Opcodes.S_KEYBYTES), "churn TCP handshake");
                } finally {
                    gate.release();
                }
                return null;
            }));
        }
        for (var job : jobs) job.get(10, TimeUnit.SECONDS);
        long limit = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (server.accountRegistryDiagnostics()[0] > population && System.nanoTime() < limit) Thread.sleep(2);
        check(server.accountRegistryDiagnostics()[0] == population, "pre-auth churn namespace drained");
        while (server.pendingSessionCleanup() != 0 && System.nanoTime() < limit) Thread.sleep(2);
        check(server.pendingSessionCleanup() == 0, "pre-auth releases completed");
        System.out.printf(Locale.ROOT, "CHURN connected_closed=%d retained_population=%d drain_ms=%.3f%n",
                attempts, population, (System.nanoTime() - start) / 1e6);
    }

    private void phase(ExecutorService executor, Client[] clients, String label, int count, int repeats,
            long configuredSpanMillis, boolean injectLock) throws Exception {
        phase(executor, clients, label, count, repeats, configuredSpanMillis, injectLock, false);
    }

    private void phase(ExecutorService executor, Client[] clients, String label, int count, int repeats,
            long configuredSpanMillis, boolean injectLock, boolean storageOnly) throws Exception {
        final long spanMillis = population < 512 ? Math.max(1L, configuredSpanMillis / 4) : configuredSpanMillis;
        final int phase = ++phaseNumber;
        var samples = new ConcurrentLinkedQueue<Observation>();
        var completions = new ArrayList<Future<?>>();
        var done = new java.util.concurrent.CountDownLatch(count);
        long start = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(300);
        Future<?> release = null;
        try (Connection hold = injectLock ? db.getConnection() : null) {
            if (hold != null) {
                hold.setAutoCommit(false);
                try (var query = hold.prepareStatement("SELECT balance FROM bench_population_balance WHERE owner_id=? FOR UPDATE")) {
                    query.setInt(1, CioSyntheticPopulation.actorId(0));
                    try (var row = query.executeQuery()) { check(row.next(), "injected row acquired"); }
                }
                release = executor.submit(() -> {
                    await(start + TimeUnit.MILLISECONDS.toNanos(150));
                    hold.rollback();
                    return null;
                });
            }
            for (int i = 0; i < count; i++) {
                Client client = clients[i];
                final long offsetMillis = spanMillis * i / count;
                completions.add(executor.submit(() -> {
                    try {
                        for (int n = 0; n < repeats; n++) {
                            long due = start + TimeUnit.MILLISECONDS.toNanos(offsetMillis + n * spanMillis);
                            await(due);
                            int kind = storageOnly ? RECEIPT + client.ordinal % (KIND.length - RECEIPT)
                                    : kind(client.ordinal, client.sequence + 1, injectLock);
                            long sent = System.nanoTime();
                            client.exchange(kind);
                            long completed = System.nanoTime();
                            samples.add(new Observation(kind, completed - sent, completed - due));
                        }
                    } finally {
                        done.countDown();
                    }
                    return null;
                }));
            }
            long deadline = start + TimeUnit.MILLISECONDS.toNanos(spanMillis * repeats) + TimeUnit.SECONDS.toNanos(28);
            int sampledPending = 0;
            int sampledObservers = 0;
            while (!done.await(35, TimeUnit.MILLISECONDS)) {
                check(System.nanoTime() < deadline, "phase timed out: " + label);
                check(server.accountRegistryDiagnostics()[0] == population, "lost or replaced a live socket: " + label);
                sampledPending = Math.max(sampledPending, cache.pendingDbJobs());
                sampledObservers = Math.max(sampledObservers, observers.getQueue().size());
            }
            long elapsed = System.nanoTime() - start;
            for (var task : completions) task.get();
            if (release != null) release.get();
            if (failure.get() != null) throw new AssertionError("synthetic operation failed", failure.get());
            check(samples.size() == count * repeats, "every scheduled operation replied exactly once");
            for (int i = 0; i < population; i++) {
                check((observedActors.get(i) == phase) == (i < count), "exact server-side active-actor set");
            }
            long plannedSpan = TimeUnit.MILLISECONDS.toNanos(spanMillis * repeats);
            System.out.printf(Locale.ROOT, "PHASE %s active=%d offered_ops_s=%.1f completed=%d observed_ops_s=%.1f sampled_pending_db=%d sampled_observer_queue=%d tail_drain_ms=%.1f injected_lock_ms=%d%n",
                    label, count, count * 1000.0 / spanMillis, samples.size(), samples.size() * 1e9 / elapsed,
                    sampledPending, sampledObservers, Math.max(0, elapsed - plannedSpan) / 1e6, injectLock ? 150 : 0);
            printTail(label, "mixed", samples);
            for (int op = 1; op < KIND.length; op++) {
                final int kind = op;
                printTail(label, KIND[op], samples.stream().filter(value -> value.kind() == kind).toList());
            }
            sample(label);
        }
    }

    private static int kind(int actor, int sequence, boolean burst) {
        if (burst && actor == 0) return RECEIPT;
        int draw = Math.floorMod(actor * 53 + sequence * 97, 100);
        if (burst) return draw < 40 ? ECHO : draw < 76 ? RECEIPT : ACCOUNT + draw % 5;
        return draw < 70 ? ECHO : draw < 82 ? RECEIPT : ACCOUNT + draw % 5;
    }

    private void perform(int kind, int ordinal, int sequence, L1PcInstance pc, CUserSocket socket) {
        int actor = pc.getId();
        String account = CioSyntheticPopulation.login(ordinal);
        Runnable operation = switch (kind) {
            case RECEIPT -> () -> {
                String tx = new UUID(1 + ordinal, sequence).toString();
                try {
                    CTransaction.persistEconomy(tx, actor, escrow(ordinal), new CTransaction.Mutation[]{
                        con -> transfer(con, actor, escrow(ordinal))
                    });
                    receiptIds.add(tx);
                    receiptCounts.incrementAndGet(ordinal);
                } catch (Exception problem) {
                    throw new IllegalStateException(problem);
                }
            };
            case LOGIN -> () -> {
                Account loaded = Account.load(account);
                check(loaded != null && account.equals(loaded.getName()), "account load matched actor");
            };
            case INVENTORY -> () -> {
                try {
                    var items = itemStore.loadItems(actor);
                    check(items.size() == INVENTORY_ROWS && items.stream().filter(item -> item.getKeyId() == 601).count() == 2,
                            "actual storage restored correct inventory and boss keys");
                } catch (Exception problem) {
                    throw new IllegalStateException(problem);
                }
            };
            case ITEM_SAVE -> () -> {
                try {
                    var items = itemStore.loadItems(actor);
                    var item = items.stream().filter(row -> row.getId() == itemId(ordinal)).findFirst().orElseThrow();
                    int enchant = (sequence & 7) + 1;
                    item.setEnchantLevel(enchant);
                    for (int duplicate = 0; duplicate < 3; duplicate++) cache.saveItem(item, 16L);
                    check(cache.flushItemsByCharacter(actor, 8000), "durable duplicate item snapshot");
                    itemSaves.set(ordinal, enchant);
                } catch (Exception problem) {
                    throw new IllegalStateException(problem);
                }
            };
            case ACCOUNT -> {
                cache.saveRaidBuff(account, CioSyntheticPopulation.accountId(ordinal),
                        SNAPSHOT_START + 1000L * sequence);
                accountSaves.set(ordinal, sequence);
                yield () -> check(cache.flushAccount(account, 8000), "durable account snapshot");
            }
            case CHARACTER -> {
                pc.setTitle("load-" + sequence);
                check(cache.saveCharacter(new CharacterSnapshot(pc)), "character snapshot accepted");
                characterSaves.set(ordinal, sequence);
                yield () -> check(cache.flushCharacter(pc.getName(), 8000), "durable character snapshot");
            }
            default -> throw new IllegalArgumentException("synthetic operation " + kind);
        };
        Runnable acknowledge = () -> {
            try {
                operation.run();
                reply(socket, ordinal, sequence, kind);
            } catch (Throwable problem) {
                if (failure.compareAndSet(null, problem)) problem.printStackTrace(System.err);
                socket.close();
            }
        };
        switch (kind) {
            case RECEIPT -> cache.submitDbJobOrdered(actor, escrow(ordinal), acknowledge);
            case LOGIN -> cache.submitDbJobOrdered(account, acknowledge);
            case INVENTORY -> cache.submitWarehouseLoginJob(actor, CacheD.accountOrderKey(account),
                    CacheD.accountOrderKey("special:" + pc.getName()), acknowledge);
            default -> observers.execute(acknowledge);
        }
    }

    private static int escrow(int ordinal) { return 4_000_000 + ordinal; }
    private static int itemId(int ordinal) { return 100_000 + ordinal * INVENTORY_ROWS; }

    private static void transfer(Connection con, int source, int target) throws SQLException {
        try (PreparedStatement sql = con.prepareStatement("UPDATE bench_population_balance SET balance=balance+? WHERE owner_id=?")) {
            sql.setInt(1, -1);
            sql.setInt(2, source);
            check(sql.executeUpdate() == 1, "receipt debit row");
            sql.setInt(1, 1);
            sql.setInt(2, target);
            check(sql.executeUpdate() == 1, "receipt credit row");
        }
    }

    private void verifyState() throws Exception {
        var pending = new java.util.HashSet<>(receiptIds);
        check(pending.size() == receiptIds.size(), "distinct receipt IDs");
        try (var con = db.getConnection(); var sql = con.createStatement()) {
            try (var rows = sql.executeQuery("SELECT tx_id FROM cached_transaction_receipts")) {
                while (rows.next()) check(pending.remove(rows.getString(1)), "one exact durable receipt");
                check(pending.isEmpty(), "all exact durable receipts");
            }
            try (var rows = sql.executeQuery("SELECT owner_id,balance FROM bench_population_balance ORDER BY owner_id")) {
                for (int i = 0; i < population; i++) check(rows.next() && rows.getInt(1) == CioSyntheticPopulation.actorId(i)
                        && rows.getInt(2) == INITIAL_BALANCE - receiptCounts.get(i), "source balance");
                for (int i = 0; i < population; i++) check(rows.next() && rows.getInt(1) == escrow(i)
                        && rows.getInt(2) == INITIAL_BALANCE + receiptCounts.get(i), "escrow balance");
                check(!rows.next(), "no extra balance rows");
            }
            try (var rows = sql.executeQuery("SELECT id,UNIX_TIMESTAMP(raid_buff)*1000 FROM accounts ORDER BY id")) {
                for (int i = 0; i < population; i++) {
                    check(rows.next() && rows.getInt(1) == CioSyntheticPopulation.accountId(i), "account row identity");
                    int sequence = accountSaves.get(i);
                    if (sequence != 0) check(rows.getLong(2) == SNAPSHOT_START + 1000L * sequence, "final account save durable");
                }
            }
            try (var rows = sql.executeQuery("SELECT id,enchantlvl FROM character_items WHERE (id-100000) MOD "
                    + INVENTORY_ROWS + "=0 ORDER BY id")) {
                for (int i = 0; i < population; i++) {
                    check(rows.next() && rows.getInt(1) == itemId(i), "item row identity");
                    check(rows.getInt(2) == itemSaves.get(i), "final duplicate item save durable");
                }
            }
            try (var rows = sql.executeQuery("SELECT objid,Title FROM characters ORDER BY objid")) {
                for (int i = 0; i < population; i++) {
                    check(rows.next() && rows.getInt(1) == CioSyntheticPopulation.actorId(i), "character row identity");
                    int sequence = characterSaves.get(i);
                    if (sequence != 0) check(("load-" + sequence).equals(rows.getString(2)), "final character save durable");
                }
            }
        }
        check(cache.pendingDbJobs() == 0 && cache.pendingAccounts() == 0 && cache.pendingCharacters() == 0
                && cache.pendingItems() == 0, "CacheD pending work released");
        System.out.printf("CHECKS receipts=%d accounts=%d characters=%d pending_db=0 pending_items=0%n",
                receiptIds.size(), population, population);
    }

    private static Object privateField(Object target, String name) throws ReflectiveOperationException {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private long status(String name) throws Exception {
        try (var con = db.getConnection(); var sql = con.createStatement();
             var row = sql.executeQuery("SHOW GLOBAL STATUS WHERE Variable_name='" + name + "'")) {
            check(row.next(), "MariaDB status " + name);
            return row.getLong(2);
        }
    }

    private static void reply(CUserSocket socket, int ordinal, int sequence, int kind) {
        check(socket.tryWritePacket(socket, new CBytesPacket(body(RESPONSE, ordinal, sequence, kind))), "response admitted");
    }

    private record Observation(int kind, long rtt, long planned) { }

    private static void printTail(String phase, String kind, Iterable<Observation> rows) {
        var sorted = new ArrayList<Observation>();
        rows.forEach(sorted::add);
        if (sorted.isEmpty()) return;
        long[] rtt = sorted.stream().mapToLong(Observation::rtt).sorted().toArray();
        long[] planned = sorted.stream().mapToLong(Observation::planned).sorted().toArray();
        System.out.printf(Locale.ROOT, "TAIL %s %s n=%d rtt_p50_ms=%.3f rtt_p99_ms=%.3f offered_p99_ms=%.3f%n",
                phase, kind, rtt.length, percentile(rtt, 0.5), percentile(rtt, 0.99), percentile(planned, 0.99));
    }

    private static double percentile(long[] sorted, double p) {
        return sorted[(int) Math.ceil(sorted.length * p) - 1] / 1e6;
    }

    private static void await(long time) {
        for (long delay; (delay = time - System.nanoTime()) > 0;) LockSupport.parkNanos(delay);
    }

    private static byte[] body(int opcode, int actor, int sequence, int kind) {
        return ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN).put((byte) opcode)
                .putInt(actor).putInt(sequence).put((byte) kind).array();
    }

    private static final class Client implements AutoCloseable {
        final int ordinal;
        final Socket socket;
        final DataInputStream input;
        final SelectorTransportTest.ScalarClient cipher = new SelectorTransportTest.ScalarClient(Opcodes.getSeed());
        int sequence = -1;

        Client(InetSocketAddress endpoint, int ordinal) throws IOException {
            this.ordinal = ordinal;
            socket = new Socket();
            try {
                socket.setTcpNoDelay(true);
                socket.setSoTimeout(10000);
                socket.connect(endpoint, 5000);
                input = new DataInputStream(socket.getInputStream());
                check(Arrays.equals(read(), Opcodes.S_KEYBYTES), "production initial handshake key");
                exchange(ATTACH);
            } catch (IOException | RuntimeException | Error problem) {
                socket.close();
                throw problem;
            }
        }

        void exchange(int kind) throws IOException {
            int sent = ++sequence;
            byte[] data = cipher.toServer(body(REQUEST, ordinal, sent, kind));
            byte[] framed = ByteBuffer.allocate(data.length + 2).order(ByteOrder.LITTLE_ENDIAN)
                    .putShort((short) (data.length + 2)).put(data).array();
            socket.getOutputStream().write(framed);
            check(Arrays.equals(read(), cipher.fromServer(body(RESPONSE, ordinal, sent, kind))), "encrypted reply for exact actor and request");
        }

        private byte[] read() throws IOException {
            int length = input.readUnsignedByte() | input.readUnsignedByte() << 8;
            if (length < 3 || length > 256) throw new IOException("unexpected fixture frame length " + length);
            byte[] body = new byte[length - 2];
            input.readFully(body);
            return body;
        }

        @Override public void close() throws IOException { socket.close(); }
    }

    private static void check(boolean valid, String message) {
        if (!valid) throw new AssertionError(message);
    }
}
