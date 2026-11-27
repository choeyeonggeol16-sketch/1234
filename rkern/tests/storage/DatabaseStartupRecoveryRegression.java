import com.zaxxer.hikari.HikariDataSource;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import l1j.server.DatabaseFactory;

/** Run in an empty directory with a rejected catalog URL and a working test catalog URL. */
public final class DatabaseStartupRecoveryRegression {
    private static final Path CONFIG = Path.of("config", "Database.ini");

    private static void configure(String url) throws Exception {
        var properties = new Properties();
        properties.setProperty("Driver", "org.mariadb.jdbc.Driver");
        properties.setProperty("URL", url);
        properties.setProperty("Login", System.getenv("RKERN_BENCH_USER"));
        properties.setProperty("Password", System.getenv().getOrDefault("RKERN_BENCH_PASSWORD", ""));
        properties.setProperty("MaximumDatabaseConnections", "4");
        properties.setProperty("ConnectTimeoutMillis", "1500");
        properties.setProperty("SocketTimeoutMillis", "3000");
        properties.setProperty("TestDatabaseConnections", "true");
        try (var writer = Files.newBufferedWriter(CONFIG)) { properties.store(writer, "synthetic DB fixture"); }
    }

    private static void failedBorrowers() {
        var emitted = new AtomicInteger();
        var log = Logger.getLogger(DatabaseFactory.class.getName());
        var sink = new Handler() {
            public void publish(LogRecord entry) { if (entry.getThrown() instanceof SQLException) emitted.incrementAndGet(); }
            public void flush() {}
            public void close() {}
        };
        log.addHandler(sink);
        try {
            for (int i = 0; i < 256; i++) {
                try {
                    DatabaseFactory.getConnectionInstance();
                    throw new AssertionError("Unavailable DB admitted a borrower");
                } catch (RuntimeException expected) {
                    check(expected.getCause() instanceof SQLException, "SQL acquisition failure propagated");
                }
            }
        } finally {
            log.removeHandler(sink);
        }
        check(emitted.get() == 1, "one central stack trace for 256 failed borrowers");
    }

    public static void main(String[] args) throws Exception {
        Files.createDirectory(CONFIG.getParent());
        Field pool = DatabaseFactory.class.getDeclaredField("DATABASE_POOL");
        pool.setAccessible(true);
        try {
            configure(System.getenv("RKERN_REJECTED_JDBC"));
            try {
                DatabaseFactory.init();
                throw new AssertionError("Startup continued without any usable connection");
            } catch (IllegalStateException expected) {
                check(expected.getCause() instanceof SQLException, "startup failed on the DB probe");
            }
            check(pool.get(null) == null, "failed pool was not published");
            failedBorrowers();
            configure(System.getenv("RKERN_BENCH_JDBC"));
            DatabaseFactory.init();
            var live = (HikariDataSource) pool.get(null);
            try (var c = DatabaseFactory.getConnectionInstance(); var statement = c.createStatement();
                 var row = statement.executeQuery("SELECT 1")) {
                check(row.next() && row.getInt(1) == 1, "same process recovered a working pool");
            }
            // Exercise the optional partial warm-up without changing DB-wide limits.
            try (var partial = new HikariDataSource() {
                int calls;
                @Override public Connection getConnection() throws SQLException {
                    if (++calls == 3) throw new SQLException("scripted third acquisition failure");
                    return live.getConnection();
                }
            }) {
                partial.setMaximumPoolSize(4);
                var warm = DatabaseFactory.class.getDeclaredMethod("testDatabaseConnections", HikariDataSource.class);
                warm.setAccessible(true);
                warm.invoke(null, partial);
                check(partial.calls == 3 && partial.getMaximumPoolSize() == 4, "operator limit preserved");
                check(live.getHikariPoolMXBean().getActiveConnections() == 0, "all warm-up leases returned");
            }
            System.out.println("PASS rejected startup, bounded outage logging, same-process recovery, partial warm-up");
        } finally {
            DatabaseFactory.close();
            Files.deleteIfExists(CONFIG);
            Files.deleteIfExists(CONFIG.getParent());
        }
    }

    private static void check(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
    }
}
