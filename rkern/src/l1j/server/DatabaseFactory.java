package l1j.server;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * HikariCP를 커넥션 풀로 사용하는 데이터베이스 팩토리 클래스
 */
public class DatabaseFactory {
    private static final Logger LOGGER = Logger.getLogger(DatabaseFactory.class.getName());

    private static HikariDataSource DATABASE_POOL;

    private static final DatabaseFactory INSTANCE = new DatabaseFactory();

    // 조사 중에만 활성화. -Dl1j.jdbc.trace=false 로 재컴파일 없이 끌 수 있다.
    private static final boolean JDBC_TRACE = Boolean.parseBoolean(
            System.getProperty("l1j.jdbc.trace", "false"));
    // 기본값은 호출 위치 + SQL 조합별 최초 1회 및 느린/실패 호출만 출력한다.
    // -Dl1j.jdbc.trace.all=true 설정 시 모든 SQL 실행을 출력한다.
    private static final boolean JDBC_TRACE_ALL = Boolean.getBoolean("l1j.jdbc.trace.all");
    private static final long JDBC_SLOW_NS = Math.max(0L,
            Long.getLong("l1j.jdbc.trace.slowMs", 20L)) * 1_000_000L;
    private static final int JDBC_MAX_SITES = 16384;
    private static final ConcurrentHashMap<String, Boolean> JDBC_LOGGED_SITES =
            new ConcurrentHashMap<>();

    private DatabaseFactory() {}

    public static DatabaseFactory getInstance() {
        return INSTANCE;
    }

    public static synchronized void init() {
        if ((DATABASE_POOL != null) && !DATABASE_POOL.isClosed()) {
            LOGGER.warning("데이터베이스: 커넥션 풀이 이미 초기화되어 있습니다.");
            return;
        }

        DatabaseConfig.load();

        try {
            final HikariConfig config = new HikariConfig();
            config.setDriverClassName(DatabaseConfig.DATABASE_DRIVER);
            config.setJdbcUrl(DatabaseConfig.DATABASE_URL);
            config.setUsername(DatabaseConfig.DATABASE_LOGIN);
            config.setPassword(DatabaseConfig.DATABASE_PASSWORD);

            config.setMaximumPoolSize(determineMaxPoolSize(DatabaseConfig.DATABASE_MAX_CONNECTIONS));
            config.setMinimumIdle(determineMinimumIdle(DatabaseConfig.DATABASE_MAX_CONNECTIONS));

            config.setConnectionTimeout(30000);
            config.setIdleTimeout(300000);
            config.setMaxLifetime(1800000);
            config.setLeakDetectionThreshold(30000);
            config.setPoolName("HikariPool");
            config.setRegisterMbeans(true);
            config.setInitializationFailTimeout(-1);
            config.setValidationTimeout(5000);
            configureDriverTimeouts(config, DatabaseConfig.DATABASE_URL);

            // MySQL 5.7.18 서버사이드 프리페어의 BLOB 처리 크래시 우회 설정 유지.
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

            DATABASE_POOL = new HikariDataSource(config);

            LOGGER.fine("데이터베이스: HikariCP 풀이 성공적으로 초기화되었습니다.");
            if (JDBC_TRACE) {
                LOGGER.warning("[JDBC-TRACE] 진단 활성화: 최초 SQL 호출 위치, CIO 여부, "
                        + "SQL 실행 시간 및 풀 획득 시간 기록. 실제 운영 시 비활성화 권장.");
            }

            final boolean connected = DatabaseConfig.DATABASE_TEST_CONNECTIONS
                    ? testDatabaseConnections()
                    : testSingleConnection();
            if (connected) {
                System.out.println("데이터베이스 서버 연결이 완료되었습니다.");
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "데이터베이스: HikariCP 풀 초기화에 실패했습니다.", e);
        }
    }

    private static void configureDriverTimeouts(HikariConfig config, String jdbcUrl) {
        if (!jdbcUrl.startsWith("jdbc:mysql:") && !jdbcUrl.startsWith("jdbc:mysql+srv:")
                && !jdbcUrl.startsWith("jdbc:mariadb:")) return;
        // Both drivers support milliseconds. Supply defaults only: driver
        // precedence between URL settings and Properties is not portable.
        if (!containsUrlOption(jdbcUrl, "connectTimeout")) {
            config.addDataSourceProperty("connectTimeout", DatabaseConfig.DATABASE_CONNECT_TIMEOUT_MS);
        }
        if (!containsUrlOption(jdbcUrl, "socketTimeout")) {
            config.addDataSourceProperty("socketTimeout", DatabaseConfig.DATABASE_SOCKET_TIMEOUT_MS);
        }
    }

    private static boolean containsUrlOption(String jdbcUrl, String name) {
        final int queryStart = jdbcUrl.indexOf('?');
        if (queryStart < 0) return false;
        for (String option : jdbcUrl.substring(queryStart + 1).split("&")) {
            final int separator = option.indexOf('=');
            if (separator <= 0) continue;
            final String key = option.substring(0, separator).trim();
            if (name.equalsIgnoreCase(key)) return true;
            if (key.indexOf('%') >= 0) {
                try {
                    if (name.equalsIgnoreCase(URLDecoder.decode(key, StandardCharsets.UTF_8))) return true;
                } catch (IllegalArgumentException malformedKey) {
                    // Leave unrelated URL validation to the configured driver.
                }
            }
        }
        return false;
    }

    public static void testLeakDetection() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                System.out.println("[PoolMonitor] 5분간 커넥션 상태 모니터링을 시작합니다. (5초 간격)");
                try {
                    for (int i = 1; i <= 60; i++) {
                        Thread.sleep(5000);

                        if (DATABASE_POOL != null && DATABASE_POOL.getHikariPoolMXBean() != null) {
                            int active = DATABASE_POOL.getHikariPoolMXBean().getActiveConnections();
                            int idle = DATABASE_POOL.getHikariPoolMXBean().getIdleConnections();
                            int total = DATABASE_POOL.getHikariPoolMXBean().getTotalConnections();
                            int waiting = DATABASE_POOL.getHikariPoolMXBean().getThreadsAwaitingConnection();

                            System.out.println(String.format(
                                    "[PoolMonitor] %3d초 경과 | 활성(사용중): %d개 | 유휴(대기): %d개 | 대기스레드: %d개",
                                    (i * 5), active, idle, waiting));
                        }
                    }
                    System.out.println("[PoolMonitor] 5분 모니터링이 완료되었습니다.");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, "Hikari-Pool-Monitor").start();
    }

    private static int determineMaxPoolSize(int configuredMax) {
        return Math.min(Math.max(configuredMax, 4), 1000);
    }

    private static int determineMinimumIdle(int configuredMax) {
        return Math.max(determineMaxPoolSize(configuredMax) / 10, 2);
    }

    private static boolean testDatabaseConnections() {
        final List<Connection> connections = new ArrayList<>();
        int successfulConnections = 0;

        try {
            LOGGER.info("데이터베이스: 데이터베이스 연결 테스트 중...");
            for (int i = 0; i < DATABASE_POOL.getMaximumPoolSize(); i++) {
                Connection connection = null;
                try {
                    connection = DATABASE_POOL.getConnection();
                    connections.add(connection);
                    successfulConnections++;
                    LOGGER.info("데이터베이스: 연결 성공적으로 열림: " + connection.toString() + ".");
                } catch (SQLException e) {
                    LOGGER.log(Level.SEVERE, "데이터베이스: " + (i + 1) + "번째 연결 열기 실패!", e);
                    break;
                }
            }

            if (successfulConnections == DATABASE_POOL.getMaximumPoolSize()) {
                LOGGER.info("데이터베이스: 총 " + successfulConnections + "개의 연결로 초기화되었습니다.");
            } else {
                LOGGER.warning("데이터베이스: 설정된 " + DATABASE_POOL.getMaximumPoolSize()
                        + "개 중 " + successfulConnections + "개의 연결만 성공했습니다.");
                adjustPoolSize(successfulConnections);
            }
        } finally {
            for (Connection connection : connections) {
                if (connection != null) {
                    try {
                        connection.close();
                    } catch (SQLException e) {
                        LOGGER.log(Level.SEVERE, "데이터베이스: 연결을 닫는 중 오류가 발생했습니다.", e);
                    }
                }
            }
        }
        return successfulConnections > 0;
    }

    private static void adjustPoolSize(int successfulConnections) {
        LOGGER.warning("데이터베이스: 성공한 연결을 바탕으로 풀 크기를 조정하는 중입니다.");

        int newConnectionCount = successfulConnections;
        if (successfulConnections > 100) {
            newConnectionCount = (successfulConnections / 100) * 100;
        } else if (successfulConnections > 50) {
            newConnectionCount = (successfulConnections / 50) * 50;
        }

        newConnectionCount = Math.max(newConnectionCount, 20);

        try {
            DATABASE_POOL.setMaximumPoolSize(newConnectionCount);
            DATABASE_POOL.setMinimumIdle(determineMinimumIdle(newConnectionCount));
            LOGGER.info("데이터베이스: 풀 크기가 다음 값으로 재초기화되었습니다: " + newConnectionCount + ".");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "데이터베이스: 풀 크기 조정에 실패했습니다.", e);
        }
    }

    private static boolean testSingleConnection() {
        try (Connection connection = DATABASE_POOL.getConnection()) {
            if (connection.isValid(5)) {
                LOGGER.fine("데이터베이스: 유효한 연결로 초기화되었습니다.");
                return true;
            }
            LOGGER.warning("데이터베이스: 연결이 유효하지 않습니다.");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "데이터베이스: 커넥션 풀 초기화 중 문제가 발생했습니다.", e);
        }
        return false;
    }

    public static Connection getConnectionInstance() {
        final long startNs = JDBC_TRACE ? System.nanoTime() : 0L;
        try {
            if (DATABASE_POOL == null || DATABASE_POOL.isClosed()) {
                throw new SQLException("HikariDataSource is closed or not initialized.");
            }

            final Connection con = DATABASE_POOL.getConnection();
            if (!JDBC_TRACE) {
                return con;
            }

            traceJdbc("CONNECTION", "getConnection()", System.nanoTime() - startNs, null);
            return (Connection) Proxy.newProxyInstance(
                    DatabaseFactory.class.getClassLoader(),
                    new Class<?>[] {Connection.class},
                    new ConnectionTrace(con));
        } catch (SQLException e) {
            if (JDBC_TRACE) {
                traceJdbc("CONNECTION", "getConnection()", System.nanoTime() - startNs, e);
            }
            LOGGER.log(Level.SEVERE, "데이터베이스: 연결을 가져올 수 없습니다.", e);
            throw new RuntimeException("데이터베이스 연결을 획득할 수 없습니다.", e);
        }
    }

    public Connection getConnection() {
        return getConnectionInstance();
    }

    /** An uncertain commit or rollback must never leave a reusable transaction. */
    public static void discardConnection(Connection connection) throws SQLException {
        if (Proxy.isProxyClass(connection.getClass())
                && Proxy.getInvocationHandler(connection) instanceof ConnectionTrace trace) {
            connection = trace.connection;
        }
        HikariDataSource pool = DATABASE_POOL;
        try {
            if (pool != null) pool.evictConnection(connection);
        } finally {
            connection.abort(Runnable::run);
        }
    }

    public static synchronized void close() {
        if ((DATABASE_POOL != null) && !DATABASE_POOL.isClosed()) {
            try {
                DATABASE_POOL.close();
                Server.printShutdownStatus("데이터베이스 서버가 종료되었습니다.");
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "데이터베이스: 데이터 소스를 닫는 중 문제가 발생했습니다.", e);
            }
        }
    }

    public void shutdown() {
        close();
    }

    // 실제 SQL 실행 지점에서 기록한다. 커넥션을 얻는 것과 SQL 실행은 다른 이벤트다.
    private static void traceJdbc(String operation, String sql, long ns, Throwable failure) {
        final Thread current = Thread.currentThread();
        final String thread = current.getName();
        final boolean cio = thread.startsWith("CIOThread-");
        final String caller = findGameCaller();
        final String normalizedSql = oneLine(sql);
        final String key = operation + '\n' + caller + '\n' + normalizedSql + '\n' + cio;
        boolean first = false;

        if (JDBC_LOGGED_SITES.containsKey(key)) {
            // 중복 호출은 느린 쿼리/오류이거나 전체 추적 모드일 때만 아래서 출력한다.
        } else if (JDBC_LOGGED_SITES.size() < JDBC_MAX_SITES) {
            first = JDBC_LOGGED_SITES.putIfAbsent(key, Boolean.TRUE) == null;
        }

        final boolean slow = ns >= JDBC_SLOW_NS;
        if (!first && !JDBC_TRACE_ALL && !slow && failure == null) {
            return;
        }

        final String message = "[JDBC-" + (cio ? "CIO" : "OTHER") + "] "
                + operation
                + " | " + (ns / 1_000_000L) + "ms"
                + " | thread=" + thread
                + " | at=" + caller
                + " | sql=" + normalizedSql
                + (first ? " | FIRST" : "");

        if (failure != null) {
            LOGGER.log(Level.SEVERE, message, failure);
        } else if (cio || slow) {
            LOGGER.warning(message + (slow ? " | SLOW" : ""));
        } else {
            LOGGER.info(message);
        }
    }

    private static String findGameCaller() {
        final StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        final String self = DatabaseFactory.class.getName();
        for (int i = 0; i < stack.length; i++) {
            final StackTraceElement e = stack[i];
            final String c = e.getClassName();
            if (c.startsWith("l1j.") && !c.equals(self) && !c.startsWith(self + "$")) {
                return c + '.' + e.getMethodName() + ':' + e.getLineNumber();
            }
        }
        return "unknown";
    }

    private static String oneLine(String sql) {
        if (sql == null) return "(null)";
        final int n = Math.min(sql.length(), 600);
        final StringBuilder b = new StringBuilder(n);
        boolean whitespace = false;
        for (int i = 0; i < n; i++) {
            final char c = sql.charAt(i);
            if (c <= ' ') {
                if (!whitespace) b.append(' ');
                whitespace = true;
            } else {
                b.append(c);
                whitespace = false;
            }
        }
        if (sql.length() > n) b.append(" ...[truncated]");
        return b.toString();
    }

    private static Object invokeJdbc(Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private static Object objectMethod(Object proxy, Object target, Method method, Object[] args) {
        final String methodName = method.getName();
        if ("equals".equals(methodName)) return proxy == args[0];
        if ("hashCode".equals(methodName)) return System.identityHashCode(proxy);
        return target.toString();
    }

    private static final class ConnectionTrace implements InvocationHandler {
        private final Connection connection;

        ConnectionTrace(Connection connection) {
            this.connection = connection;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return objectMethod(proxy, connection, method, args);
            }

            final String name = method.getName();
            if ("prepareStatement".equals(name) || "prepareCall".equals(name)
                    || "createStatement".equals(name)) {
                final Object stmt = invokeJdbc(connection, method, args);
                if (!(stmt instanceof Statement)) return stmt;

                final Class<?> iface = "prepareCall".equals(name)
                        ? CallableStatement.class
                        : "prepareStatement".equals(name)
                                ? PreparedStatement.class : Statement.class;
                final String sql = args != null && args.length > 0 && args[0] instanceof String
                        ? (String) args[0] : null;

                return Proxy.newProxyInstance(
                        DatabaseFactory.class.getClassLoader(),
                        new Class<?>[] {iface},
                        new StatementTrace((Statement) stmt, proxy, sql));
            }

            if ("commit".equals(name) || "rollback".equals(name)
                    || "setAutoCommit".equals(name)) {
                final long startNs = System.nanoTime();
                Throwable failure = null;
                try {
                    return invokeJdbc(connection, method, args);
                } catch (Throwable t) {
                    failure = t;
                    throw t;
                } finally {
                    final String detail = "setAutoCommit".equals(name) && args != null
                            ? "setAutoCommit(" + args[0] + ')' : name + "()";
                    traceJdbc("TRANSACTION", detail, System.nanoTime() - startNs, failure);
                }
            }

            return invokeJdbc(connection, method, args);
        }
    }

    private static final class StatementTrace implements InvocationHandler {
        private final Statement stmt;
        private final Object connectionProxy;
        private final String preparedSql;
        private int batchCount;
        private String batchSql;

        StatementTrace(Statement stmt, Object connectionProxy, String preparedSql) {
            this.stmt = stmt;
            this.connectionProxy = connectionProxy;
            this.preparedSql = preparedSql;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return objectMethod(proxy, stmt, method, args);
            }

            final String name = method.getName();
            if ("getConnection".equals(name)) {
                return connectionProxy;
            }
            if ("clearBatch".equals(name)) {
                final Object result = invokeJdbc(stmt, method, args);
                batchCount = 0;
                batchSql = null;
                return result;
            }
            if ("addBatch".equals(name)) {
                final Object result = invokeJdbc(stmt, method, args);
                batchCount++;
                if (args != null && args.length > 0 && args[0] instanceof String && batchSql == null) {
                    batchSql = (String) args[0];
                }
                return result;
            }

            if ("execute".equals(name) || "executeQuery".equals(name)
                    || "executeUpdate".equals(name) || "executeLargeUpdate".equals(name)
                    || "executeBatch".equals(name) || "executeLargeBatch".equals(name)) {

                String sql = preparedSql;
                if (args != null && args.length > 0 && args[0] instanceof String) {
                    sql = (String) args[0];
                }
                final boolean batch = "executeBatch".equals(name) || "executeLargeBatch".equals(name);
                if (batch) {
                    if (sql == null) sql = batchSql;
                    sql = (sql == null ? "(batch SQL unavailable)" : sql)
                            + " [batchCount=" + batchCount + "]";
                }

                final long startNs = System.nanoTime();
                Throwable failure = null;
                try {
                    return invokeJdbc(stmt, method, args);
                } catch (Throwable t) {
                    failure = t;
                    throw t;
                } finally {
                    traceJdbc(name, sql, System.nanoTime() - startNs, failure);
                    if (batch) {
                        batchCount = 0;
                        batchSql = null;
                    }
                }
            }

            return invokeJdbc(stmt, method, args);
        }
    }
}
