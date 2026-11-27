import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.lang.management.ManagementFactory;
import java.sql.DriverManager;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import l1j.server.DatabaseFactory;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1PendingCredit;
import l1j.server.server.storage.CacheD;
import l1j.server.server.storage.cached.CTransaction;

/**
 * Integration fixture for isolated MySQL/MariaDB. Pass the JDBC server URL
 * and database user. Set RKERN_STORAGE_TEST_PASSWORD if required.
 * Creates and drops only its own freshly named test schema.
 */
public final class CacheDAdmissionReserveRegression {
    private static final int OWNER = 1957999999;
    private static final int HARD_CAP = 2048;
    private static final int SHARED_CAP = 2044;
    private static final int PROBE = OWNER + 5;

    private static HikariDataSource db;

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Expected JDBC server URL and DB user");
        final String password = System.getenv().getOrDefault("RKERN_STORAGE_TEST_PASSWORD", "");
        final String schema = "rkern_admission_" + UUID.randomUUID().toString().replace("-", "");
        try (var admin = DriverManager.getConnection(args[0], args[1], password);
             var ddl = admin.createStatement()) {
            ddl.executeUpdate("CREATE DATABASE " + schema);
            try {
                admin.setCatalog(schema);
                for (String table : List.of("character_warehouse", "clan_warehouse",
                        "character_elf_warehouse", "character_special_warehouse",
                        "character_supplementary_service")) {
                    ddl.executeUpdate("CREATE TABLE " + table + "(id INT PRIMARY KEY) ENGINE=InnoDB");
                }
                ddl.executeUpdate("CREATE TABLE characters (objid INT PRIMARY KEY) ENGINE=InnoDB");
                ddl.executeUpdate("CREATE TABLE character_items (id INT PRIMARY KEY, char_id INT, item_id INT, count INT) ENGINE=InnoDB");
                ddl.executeUpdate("CREATE TABLE cached_transaction_receipts (tx_id CHAR(36) PRIMARY KEY,kind VARCHAR(16),owner_a INT,owner_b INT) ENGINE=InnoDB");
                ddl.executeUpdate("CREATE TABLE character_pending_credits (credit_id CHAR(36) PRIMARY KEY, char_id INT, amount INT, reason VARCHAR(40)) ENGINE=InnoDB");
                ddl.executeUpdate("INSERT INTO characters VALUES (" + OWNER + ")");
                ddl.executeUpdate("INSERT INTO character_items VALUES (111," + OWNER + ",40308,10)");
                ddl.executeUpdate("CREATE TABLE character_quests (char_id INT, quest_id INT, PRIMARY KEY(char_id,quest_id)) ENGINE=InnoDB");
                ddl.executeUpdate("CREATE TABLE character_level_gifts (char_id INT, gift_level INT, PRIMARY KEY(char_id,gift_level)) ENGINE=InnoDB");
                ddl.executeUpdate("CREATE TABLE bench_owner_state(owner_id INT PRIMARY KEY, balance INT NOT NULL) ENGINE=InnoDB");

                final var config = new HikariConfig();
                config.setJdbcUrl(args[0]);
                config.setUsername(args[1]);
                config.setPassword(password);
                config.setCatalog(schema);
                config.setMaximumPoolSize(8);
                try (var source = new HikariDataSource(config)) {
                    db = source;
                    final var pool = DatabaseFactory.class.getDeclaredField("DATABASE_POOL");
                    pool.setAccessible(true);
                    pool.set(null, source);
                    verifyBoundedAdmission();
                }
            } finally {
                ddl.executeUpdate("DROP DATABASE " + schema);
            }
        }
    }

    private static void verifyBoundedAdmission() throws Exception {
        CTransaction.verifySchema();
        try (var c = db.getConnection();
             var seed = c.prepareStatement("INSERT INTO bench_owner_state VALUES (?, 0) ON DUPLICATE KEY UPDATE balance=0")) {
            for (int owner = OWNER; owner <= PROBE; ++owner) {
                seed.setInt(1, owner);
                seed.addBatch();
            }
            seed.executeBatch();
        }

        final CacheD cache = CacheD.getInstance();
        try {
            check(cache.flushAll(5_000L), "transaction schema validation drained");
            CTransaction.persistEconomy(UUID.randomUUID().toString(), OWNER, 0,
                    new CTransaction.Mutation[]{ L1PendingCredit.prepare(OWNER, 7, "AUCTION_REFUND") });
            final long before = deadlocks();
            final var firstStarted = new CountDownLatch(1);
            final var primaryWrites = new AtomicInteger();
            final var disjointWrites = new AtomicInteger();
            final var failure = new AtomicReference<Throwable>();
            long releaseAt;
            try (var held = db.getConnection()) {
                held.setAutoCommit(false);
                // Equality on existing primary keys holds only the five test rows.
                try (var lock = held.prepareStatement("SELECT balance FROM bench_owner_state WHERE owner_id=? FOR UPDATE")) {
                    for (int owner = OWNER; owner < PROBE; ++owner) {
                        lock.setInt(1, owner);
                        try (var row = lock.executeQuery()) {
                            check(row.next() && row.getInt(1) == 0, "synthetic SQL owner has initial balance");
                        }
                    }
                }
                for (int sequence = 0; sequence < SHARED_CAP; sequence++) {
                    submit(cache, OWNER, sequence, firstStarted, null, primaryWrites, failure);
                }
                check(firstStarted.await(2L, TimeUnit.SECONDS), "first accepted job reached the DB worker");
                check(primaryWrites.get() == 0 && pending(cache) == SHARED_CAP, "original row lock holds 2,044 jobs");
                rejected(() -> cache.submitDbJobOrdered(OWNER, () -> {}), "free slots reserved for disjoint owners");
                rejected(() -> cache.submitDbJobOrdered(PROBE, OWNER, () -> {}), "busy secondary owner may not consume reserve");
                rejected(() -> cache.submitDbJob(() -> {}), "unidentified job may not consume reserve");
                var recipient = new L1PcInstance();
                recipient.setId(OWNER);
                recipient.setName("RefundFixture");
                CWorld world = CWorld.getInstance();
                world.storeObject(recipient);
                try {
                    L1PendingCredit.notifyOnline(OWNER);
                    check(pending(cache) == SHARED_CAP, "optional refund notification deferred");
                } finally {
                    world.removeObject(recipient);
                }

                final var progressed = new CountDownLatch(1);
                submit(cache, PROBE, 0, null, progressed, disjointWrites, failure);
                check(progressed.await(2L, TimeUnit.SECONDS) && cache.flushDbOwner(PROBE, 2_000L),
                        "disjoint job durably completed while original lock was held");
                check(durableBalance(PROBE) == 1 && primaryWrites.get() == 0 && pending(cache) == SHARED_CAP,
                        "disjoint progress leaves original locked queue intact");

                for (int owner = OWNER + 1; owner < PROBE; ++owner) {
                    submit(cache, owner, 0, null, null, disjointWrites, failure);
                }
                check(disjointWrites.get() == 1 && pending(cache) == HARD_CAP, "four protected slots filled while their rows are locked");
                rejected(() -> cache.submitDbJobOrdered(PROBE + 1, () -> {}), "hard cap rejects a further disjoint owner");

                releaseAt = System.nanoTime();
                held.rollback();
            }
            check(cache.flushAll(20_000L), "accepted work did not drain after releasing the SQL locks");
            final double drain = (System.nanoTime() - releaseAt) / 1_000_000.0;
            check(failure.get() == null, "accepted SQL job failed: " + failure.get());
            check(primaryWrites.get() == SHARED_CAP && durableBalance(OWNER) == SHARED_CAP,
                    "all 2,044 accepted shared-owner updates ran in SQL order");
            check(disjointWrites.get() == 5, "witness plus reserve all executed exactly once");
            for (int owner = OWNER + 1; owner <= PROBE; ++owner)
                check(durableBalance(owner) == 1, "each independent SQL row changed once");
            verifyNoReadyRetention(cache);

            final CountDownLatch recovery = new CountDownLatch(1);
            submit(cache, OWNER, SHARED_CAP, null, recovery, primaryWrites, failure);
            check(recovery.await(5L, TimeUnit.SECONDS) && cache.flushAll(5_000L), "old owner can submit again after drain");
            check(durableBalance(OWNER) == SHARED_CAP + 1 && failure.get() == null, "post-drain SQL durable");
            verifyNoReadyRetention(cache);
            L1PendingCredit.recoverBeforeLoad(OWNER);
            L1PendingCredit.recoverBeforeLoad(OWNER);
            try (var c = db.getConnection(); var stmt = c.createStatement();
                 var rows = stmt.executeQuery("SELECT count,(SELECT COUNT(*) FROM character_pending_credits) FROM character_items WHERE id=111")) {
                check(rows.next() && rows.getInt(1) == 17 && rows.getInt(2) == 0,
                        "deferred auction credit paid exactly once on login");
            }
            check(deadlocks() == before, "no added InnoDB deadlock");
            check(ManagementFactory.getThreadMXBean().findDeadlockedThreads() == null, "no JVM deadlock");
            System.out.printf(java.util.Locale.ROOT,
                    "CAP shared=%d protected=%d global=%d hard_rejected=1 witness_durable_while_held=1 "
                    + "durable_shared_after_recovery=%d drain_ms=%.1f deferred_credit=7 pending=0 deadlocks=0%n",
                    SHARED_CAP, HARD_CAP-SHARED_CAP, HARD_CAP, SHARED_CAP+1, drain);
        } finally {
            check(cache.shutdown(10_000L), "workers stopped");
        }
    }

    private static void submit(CacheD cache, int owner, int expected, CountDownLatch started, CountDownLatch finished,
            AtomicInteger writes, AtomicReference<Throwable> failure) {
        cache.submitDbJobOrdered(owner, () -> {
            if (started != null) started.countDown();
            try (var c = db.getConnection();
                 var write = c.prepareStatement("UPDATE bench_owner_state SET balance=balance+1 WHERE owner_id=? AND balance=?")) {
                write.setInt(1, owner);
                write.setInt(2, expected);
                check(write.executeUpdate() == 1, "ordered SQL did not run at its expected balance");
                writes.incrementAndGet();
                if (finished != null) finished.countDown();
            } catch (Throwable error) {
                failure.compareAndSet(null, error);
            }
        });
    }

    private static int pending(CacheD cache) throws ReflectiveOperationException {
        synchronized (field(cache, "lock")) {
            return (int) field(cache, "pendingDbJobs");
        }
    }

    private static long durableBalance(int owner) throws Exception {
        try (var con = db.getConnection();
             var read = con.prepareStatement("SELECT balance FROM bench_owner_state WHERE owner_id=?")) {
            read.setInt(1, owner);
            try (var row = read.executeQuery()) {
                check(row.next(), "synthetic owner row exists");
                return row.getInt(1);
            }
        }
    }

    private static long deadlocks() throws Exception {
        try (var con = db.getConnection(); var state = con.createStatement();
             var row = state.executeQuery("SHOW GLOBAL STATUS LIKE 'Innodb_deadlocks'")) {
            check(row.next(), "database exposes its deadlock counter");
            return row.getLong(2);
        }
    }

    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        final var field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static void verifyNoReadyRetention(CacheD cache) throws ReflectiveOperationException {
        synchronized (field(cache, "lock")) {
            check(pending(cache) == 0 && (int) field(cache, "activeDbJobs") == 0, "unfinished DB work");
            check(((Map<?, ?>) field(cache, "dbJobsByOwner")).isEmpty(), "retained job-owner entries");
            final Object ready = field(cache, "ready");
            check((int) field(ready, "count") == 0 && field(ready, "head") == null && field(ready, "tail") == null,
                    "retained ready nodes");
            for (String name : List.of("owners", "itemsById")) {
                check(((Map<?, ?>) field(ready, name)).isEmpty(), "retained lane entries");
            }
            for (String name : List.of("readyJobs", "readyOther", "sleeping")) {
                check(((Collection<?>) field(ready, name)).isEmpty(), "retained scheduling candidates");
            }
        }
    }

    private static void rejected(Runnable admission, String description) {
        try {
            admission.run();
            throw new AssertionError("Job should not have been admitted: " + description);
        } catch (RejectedExecutionException expected) {
        }
    }

    private static void check(boolean success, String message) {
        if (!success) throw new AssertionError(message);
    }
}
