import com.zaxxer.hikari.HikariDataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import l1j.server.DatabaseFactory;
import l1j.server.server.storage.cached.CTransaction;

/** Uses a disposable schema on a MySQL/MariaDB server. Arguments are the JDBC
 * server URL and DB user with CREATE/DROP DATABASE privileges. Password is
 * accepted through RKERN_STORAGE_TEST_PASSWORD.
 */
public final class CacheDReceiptClaimRegression {
    private static final int LOSE_CONFIRMATION_AFTER_COMMIT = 1;
    private static final int LOSE_CONNECTION_BEFORE_COMMIT = 2;
    private static final String PASSWORD = System.getenv("RKERN_STORAGE_TEST_PASSWORD");
    static String user;
    static String url;
    static String schema;

    static final class Pool extends HikariDataSource {
        final AtomicInteger engineChecks = new AtomicInteger();
        final AtomicInteger insertClaims = new AtomicInteger();
        final AtomicInteger duplicateReads = new AtomicInteger();
        final AtomicInteger commitFailure = new AtomicInteger();

        @Override public Connection getConnection() throws SQLException {
            Connection physical = openSchema();
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[] {Connection.class}, (proxy, method, args) -> {
                        String name = method.getName();
                        if (name.equals("prepareStatement")) {
                            String sql = ((String) args[0]).strip();
                            if (sql.startsWith("SELECT") && sql.contains("information_schema.TABLES"))
                                engineChecks.incrementAndGet();
                            if (sql.startsWith("INSERT INTO cached_transaction_receipts"))
                                insertClaims.incrementAndGet();
                            if (sql.startsWith("SELECT kind,owner_a,owner_b FROM cached_transaction_receipts")
                                    && sql.endsWith("LOCK IN SHARE MODE"))
                                duplicateReads.incrementAndGet();
                        } else if (name.equals("commit")) {
                            int inject = commitFailure.getAndSet(0);
                            if (inject != 0) {
                                if (inject == LOSE_CONFIRMATION_AFTER_COMMIT) physical.commit();
                                physical.close();
                                throw new SQLException("Fixture COMMIT response lost", "08S01");
                            }
                        }
                        try { return method.invoke(physical, args); }
                        catch (InvocationTargetException failure) { throw failure.getCause(); }
                    });
        }
    }

    static Connection openSchema() throws SQLException {
        Connection c = DriverManager.getConnection(url, user, PASSWORD == null ? "" : PASSWORD);
        try {
            c.setCatalog(schema);
            c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            sql(c, "SET SESSION innodb_lock_wait_timeout=5");
            return c;
        } catch (SQLException failure) {
            try { c.close(); }
            catch (SQLException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    static void sql(Connection c, String command) throws SQLException {
        try (var statement = c.createStatement()) {
            statement.setQueryTimeout(5);
            statement.executeUpdate(command);
        }
    }

    static int number(Connection c, String query) throws SQLException {
        try (var statement = c.createStatement(); var result = statement.executeQuery(query)) {
            return result.next() ? result.getInt(1) : -1;
        }
    }

    static void check(boolean ok, String problem) { if (!ok) throw new AssertionError(problem); }

    static void apply(String tx, int owner) throws SQLException {
        CTransaction.persistEconomy(tx, owner, 0, new CTransaction.Mutation[]{ CacheDReceiptClaimRegression::increment });
    }

    static void increment(Connection c) throws SQLException {
        sql(c, "UPDATE fixture_balance SET total=total+1 WHERE id=1");
    }

    static void verify(Connection c, int total, int receipts) throws SQLException {
        check(number(c, "SELECT total FROM fixture_balance WHERE id=1") == total, "Mutation was lost or replayed");
        check(number(c, "SELECT COUNT(*) FROM cached_transaction_receipts") == receipts, "Receipt count differs");
    }

    static SQLException requireFailure(SqlAction action) throws SQLException {
        try { action.run(); }
        catch (SQLException failure) { return failure; }
        throw new AssertionError("Operation was unexpectedly allowed");
    }

    @FunctionalInterface interface SqlAction { void run() throws SQLException; }

    static void duplicateConcurrent(Pool pool) throws Exception {
        String tx = UUID.randomUUID().toString();
        var firstLocked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var exec = Executors.newFixedThreadPool(2)) {
            var first = exec.submit(() -> {
                CTransaction.persistEconomy(tx, 99, 0, new CTransaction.Mutation[]{ c -> {
                    firstLocked.countDown();
                    try {
                        if (!release.await(4, TimeUnit.SECONDS)) throw new SQLException("Fixture lock failed");
                    } catch (InterruptedException stopped) {
                        Thread.currentThread().interrupt();
                        throw new SQLException(stopped);
                    }
                    increment(c);
                } });
                return null;
            });
            try {
                check(firstLocked.await(4, TimeUnit.SECONDS), "Initial claim did not acquire receipt");
                int expected = pool.insertClaims.get() + 1;
                var second = exec.submit(() -> { apply(tx, 99); return null; });
                long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                while (pool.insertClaims.get() < expected && System.nanoTime() < until) Thread.sleep(1L);
                check(pool.insertClaims.get() == expected, "Concurrent claim was not made");
                release.countDown();
                first.get(5, TimeUnit.SECONDS);
                second.get(5, TimeUnit.SECONDS);
            } finally {
                release.countDown();
            }
        }
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 2)
            throw new IllegalArgumentException("Pass the isolated server JDBC URL and user");
        DriverManager.setLoginTimeout(5);
        url = arguments[0];
        user = arguments[1];
        schema = "rkern_receipt_claim_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection admin = DriverManager.getConnection(url, user, PASSWORD == null ? "" : PASSWORD)) {
            sql(admin, "CREATE DATABASE " + schema);
            try {
                admin.setCatalog(schema);
                sql(admin, """
                        CREATE TABLE cached_transaction_receipts (
                            tx_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
                            kind VARCHAR(16) NOT NULL,
                            owner_a INT NOT NULL,
                            owner_b INT NOT NULL,
                            PRIMARY KEY (tx_id)
                        ) ENGINE=InnoDB
                        """);
                sql(admin, "CREATE TABLE fixture_balance(id INT PRIMARY KEY,total INT) ENGINE=InnoDB");
                sql(admin, "INSERT INTO fixture_balance VALUES(1,0)");
                try (Pool pool = new Pool()) {
                    var field = DatabaseFactory.class.getDeclaredField("DATABASE_POOL");
                    field.setAccessible(true);
                    field.set(null, pool);

                    String first = UUID.randomUUID().toString();
                    apply(first, 12);
                    check(pool.engineChecks.get() == 0 && pool.insertClaims.get() == 1,
                            "Unexpected number of SQL requests on the first claim");
                    verify(admin, 1, 1);
                    apply(first, 12);
                    check(pool.duplicateReads.get() == 1, "Replay missed the current identity check");
                    requireFailure(() -> apply(first, 13));
                    verify(admin, 1, 1);
                    duplicateConcurrent(pool);
                    verify(admin, 2, 2);

                    String aborted = UUID.randomUUID().toString();
                    requireFailure(() -> CTransaction.persistEconomy(aborted, 12, 0, new CTransaction.Mutation[]{c -> {
                        increment(c);
                        throw new SQLException("Fixture staged operation aborted");
                    }}));
                    verify(admin, 2, 2);

                    pool.commitFailure.set(LOSE_CONFIRMATION_AFTER_COMMIT);
                    String confirmed = UUID.randomUUID().toString();
                    apply(confirmed, 12);
                    apply(confirmed, 12);
                    verify(admin, 3, 3);
                    pool.commitFailure.set(LOSE_CONNECTION_BEFORE_COMMIT);
                    String unresolved = UUID.randomUUID().toString();
                    check(requireFailure(() -> apply(unresolved, 12))
                            instanceof CTransaction.OutcomeUnknownException, "Disconnect was treated as success");
                    verify(admin, 3, 3);

                    sql(admin, "ALTER TABLE cached_transaction_receipts ENGINE=MyISAM");
                    requireFailure(() -> apply(UUID.randomUUID().toString(), 12));
                    requireFailure(() -> apply(first, 12));
                    verify(admin, 3, 3);
                    sql(admin, "ALTER TABLE cached_transaction_receipts ENGINE=InnoDB");
                    apply(UUID.randomUUID().toString(), 12);
                    verify(admin, 4, 4);
                    sql(admin, "DROP TABLE cached_transaction_receipts");
                    requireFailure(() -> apply(UUID.randomUUID().toString(), 12));
                    check(number(admin, "SELECT total FROM fixture_balance WHERE id=1") == 4,
                            "Missing receipt table allowed a game mutation");
                    field.set(null, null);
                }
            } finally {
                sql(admin, "DROP DATABASE " + schema);
            }
        }
        System.out.println("PASS one-statement receipt claim, concurrent dedup, rollback, "
                + "COMMIT response loss, engine drift and missing schema");
    }
}
