import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.DriverManager;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import l1j.server.DatabaseFactory;
import l1j.server.server.storage.CacheD;
import l1j.server.server.storage.cached.CAccount;

/** Run in a fresh JVM with the same database variables as CacheDNetMixedLoad. */
public final class CacheDAccountIdentityRegression {
    private static final String LOGIN = "cached_identity";
    private static final String[] TABLES = {
        "accounts", "cached_transaction_receipts", "character_items", "character_warehouse",
        "clan_warehouse", "character_elf_warehouse", "character_special_warehouse",
        "character_supplementary_service", "characters", "character_quests", "character_level_gifts"
    };

    public static void main(String[] args) throws Exception {
        String url = Objects.requireNonNull(System.getenv("RKERN_BENCH_JDBC"));
        String user = Objects.requireNonNull(System.getenv("RKERN_BENCH_USER"));
        String password = System.getenv().getOrDefault("RKERN_BENCH_PASSWORD", "");
        String reference = Objects.requireNonNull(System.getenv("RKERN_BENCH_REFERENCE_SCHEMA"));
        check(reference.matches("[0-9A-Za-z_]+"), "invalid reference schema");
        String schema = "rkern_account_" + UUID.randomUUID().toString().substring(0, 8);
        try (var admin = DriverManager.getConnection(url, user, password); var ddl = admin.createStatement()) {
            ddl.execute("CREATE DATABASE `" + schema + "`");
            try {
                for (String table : TABLES) {
                    ddl.execute("CREATE TABLE `" + schema + "`.`" + table
                            + "` LIKE `" + reference + "`.`" + table + "`");
                }
                var config = new HikariConfig();
                config.setJdbcUrl(url);
                config.setUsername(user);
                config.setPassword(password);
                config.setCatalog(schema);
                config.addDataSourceProperty("useAffectedRows", "true");
                try (var db = new HikariDataSource(config)) {
                    var pool = DatabaseFactory.class.getDeclaredField("DATABASE_POOL");
                    pool.setAccessible(true);
                    pool.set(null, db);
                    var cache = CacheD.getInstance();
                    try {
                        check(cache.flushAll(5000), "receipt schema check drained");
                        exercise(cache, db);
                    } finally {
                        check(cache.shutdown(5000), "CacheD drained cleanly");
                    }
                }
            } finally {
                ddl.execute("DROP DATABASE `" + schema + "`");
            }
        }
        System.out.println("PASS CacheD account identity and changed-row acknowledgement");
    }

    private static void exercise(CacheD cache, HikariDataSource db) throws Exception {
        int oldId = createAccount(db, 42);
        cache.registerLoadedCharacter(LOGIN, "fixture-active", 21001);
        try (var con = db.getConnection(); var sql = con.createStatement()) {
            check(sql.executeUpdate("UPDATE accounts SET bless_of_ain=bless_of_ain") == 0,
                    "driver must report zero for a matched unchanged row");
        }
        cache.saveBlessOfAin(LOGIN, oldId, 42);
        check(cache.flushAccount(LOGIN, 5000), "matched unchanged account must acknowledge");
        check(cache.lastFailure() == null, "unchanged row is not an account failure");

        CountDownLatch laneBusy = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        int lane = CacheD.accountOrderKey(LOGIN);
        cache.submitDbJobOrdered(lane, () -> {
            laneBusy.countDown();
            try {
                check(release.await(10, TimeUnit.SECONDS), "fixture releases account lane");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interrupted);
            }
        });
        int newId;
        try {
            check(laneBusy.await(5, TimeUnit.SECONDS), "account lane blocker is running");
            cache.saveBlessOfAin(LOGIN, oldId, 99);
            try (var con = db.getConnection(); var sql = con.createStatement()) {
                check(sql.executeUpdate("DELETE FROM accounts") == 1, "old exact account removed");
            }
            newId = createAccount(db, 7);
            check(newId != oldId, "new row must have a distinct ID");
            try {
                cache.saveBlessOfAin(LOGIN, newId, 123);
                throw new AssertionError("distinct IDs cannot merge into one dirty image");
            } catch (RejectedExecutionException expected) {
                check(cache.pendingAccounts() == 1, "older pending generation must still own the lane");
            }
            var flush = new FutureTask<>(() -> cache.flushAccount(LOGIN, 5000));
            Thread waiter = new Thread(flush, "account-identity-flush-fixture");
            waiter.start();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (waiter.getState() != Thread.State.TIMED_WAITING && System.nanoTime() < deadline) {
                Thread.sleep(1);
            }
            check(waiter.getState() == Thread.State.TIMED_WAITING, "flush observed the old pending image");
            release.countDown();
            check(!flush.get(3, TimeUnit.SECONDS), "missing exact identity must fail its waiting flush");
        } finally {
            release.countDown();
        }
        check(cache.lastFailure() instanceof CAccount.MissingAccountException, "retirement cause is visible");
        check(cache.pendingAccounts() == 0 && cache.flushAccount(LOGIN, 1000), "later empty barrier drains");
        check(blessOfAin(db, newId) == 7, "old snapshot did not write to recreated account");
        cache.saveBlessOfAin(LOGIN, newId, 9);
        check(cache.flushAccount(LOGIN, 5000) && blessOfAin(db, newId) == 9,
                "recreated account can save through the same FIFO lane");
    }

    private static int createAccount(HikariDataSource db, int bless) throws Exception {
        try (var con = db.getConnection();
             var insert = con.prepareStatement("INSERT INTO accounts(login,bless_of_ain,charslot,gamepassword,"
                     + "Shop_open_count) VALUES(?,?,8,0,0)");
             var select = con.prepareStatement("SELECT id FROM accounts WHERE login=?")) {
            insert.setString(1, LOGIN);
            insert.setInt(2, bless);
            check(insert.executeUpdate() == 1, "insert fixture account");
            select.setString(1, LOGIN);
            try (var rows = select.executeQuery()) {
                check(rows.next(), "read fixture account ID");
                return rows.getInt(1);
            }
        }
    }

    private static int blessOfAin(HikariDataSource db, int id) throws Exception {
        try (var con = db.getConnection(); var select = con.prepareStatement("SELECT bless_of_ain FROM accounts WHERE id=?")) {
            select.setInt(1, id);
            try (var rows = select.executeQuery()) {
                check(rows.next(), "read fixture account blessing");
                return rows.getInt(1);
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
