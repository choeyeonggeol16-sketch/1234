import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.storage.cached.CTransaction;
import l1j.server.server.storage.mysql.MySqlCharactersItemStorage.ItemSnapshot;
import l1j.server.server.templates.L1EtcItem;

/** Integration fixture for an isolated MySQL/MariaDB server.
 * Arguments: JDBC server URL, DB user with CREATE/DROP DATABASE privileges.
 * Set RKERN_STORAGE_TEST_PASSWORD if required. Only a fresh random schema
 * is created, modified, and removed.
 */
public final class CacheDWarehouseInsertConcurrency {
    private static final int ITEM_ID = 40001;
    private final String url;
    private final String user;
    private final String password = System.getenv("RKERN_STORAGE_TEST_PASSWORD");
    private final String schema = "rkern_warehouse_insert_" + UUID.randomUUID().toString().replace("-", "");

    private CacheDWarehouseInsertConcurrency(String url, String user) {
        this.url = url;
        this.user = user;
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(url, user, password == null ? "" : password);
    }

    private Connection openTransaction() throws SQLException {
        final Connection con = connect();
        try {
            con.setCatalog(schema);
            con.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            execute(con, "SET SESSION innodb_lock_wait_timeout=5");
            con.setAutoCommit(false);
            return con;
        } catch (SQLException failure) {
            try { con.close(); }
            catch (SQLException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    private static void execute(Connection con, String sql) throws SQLException {
        try (var statement = con.createStatement()) {
            statement.setQueryTimeout(5);
            statement.executeUpdate(sql);
        }
    }

    private static int query(Connection con, String sql) throws SQLException {
        try (var statement = con.createStatement()) {
            statement.setQueryTimeout(5);
            try (var result = statement.executeQuery(sql)) {
                if (!result.next()) throw new AssertionError("Fixture row missing");
                return result.getInt(1);
            }
        }
    }

    private static ItemSnapshot snapshot(int id, int owner, int count) {
        final L1EtcItem template = new L1EtcItem();
        template.setItemId(ITEM_ID);
        template.setName("fixture potion");
        final L1ItemInstance item = new L1ItemInstance(template, count);
        item.setId(id);
        return ItemSnapshot.capture(item, owner);
    }

    private static Connection waitBeforeInsert(Connection con, CyclicBarrier gate) {
        return (Connection) Proxy.newProxyInstance(CacheDWarehouseInsertConcurrency.class.getClassLoader(),
                new Class<?>[] { Connection.class }, (proxy, method, args) -> {
                    // Both transactions reach INSERT together. In the old
                    // code both had already acquired a lock on the same gap.
                    if (method.getName().equals("prepareStatement")
                            && ((String) args[0]).startsWith("INSERT INTO character_items ")) {
                        try { gate.await(5, TimeUnit.SECONDS); }
                        catch (InterruptedException stopped) {
                            Thread.currentThread().interrupt();
                            throw new SQLException("Fixture interrupted at INSERT", stopped);
                        } catch (Exception failure) {
                            throw new SQLException("Fixture barrier was not reached", failure);
                        }
                    }
                    try { return method.invoke(con, args); }
                    catch (InvocationTargetException failure) { throw failure.getCause(); }
                });
    }

    private void withdraw(int sourceId, int destinationId, int owner, CyclicBarrier gate) throws SQLException {
        try (Connection con = openTransaction()) {
            try {
                execute(con, "INSERT INTO cached_transaction_receipts VALUES (" + owner + ")");
                execute(con, "UPDATE character_warehouse SET count=count-2 WHERE id=" + sourceId);
                final Connection insert = gate == null ? con : waitBeforeInsert(con, gate);
                CTransaction.writeCharacterMoveRow(insert, snapshot(destinationId, owner, 2), true, 0);
                con.commit();
            } catch (SQLException failure) {
                try { con.rollback(); }
                catch (SQLException cleanup) { failure.addSuppressed(cleanup); }
                throw failure;
            }
        }
    }

    private void checkDuplicateRollbackAndConservation(Connection con) throws SQLException {
        try {
            withdraw(10, 100, 102, null);
            throw new AssertionError("Existing item ID was overwritten");
        } catch (SQLException duplicate) {
            if (duplicate.getErrorCode() != 1062) throw duplicate;
        }
        if (query(con, "SELECT count FROM character_warehouse WHERE id=10") != 3
                || query(con, "SELECT char_id FROM character_items WHERE id=100") != 900
                || query(con, "SELECT COUNT(*) FROM cached_transaction_receipts") != 2
                || query(con, "SELECT SUM(count) FROM character_items")
                    + query(con, "SELECT SUM(count) FROM character_warehouse") != 14)
            throw new AssertionError("Concurrent transfer or duplicate rollback changed the item/receipt totals");
    }

    private void run() throws Exception {
        try (Connection admin = connect()) {
            execute(admin, "CREATE DATABASE " + schema);
            try {
                admin.setCatalog(schema);
                execute(admin, """
                        CREATE TABLE character_items (
                            id INT PRIMARY KEY, item_id INT, char_id INT, item_name VARCHAR(64),
                            count INT, is_equipped INT, enchantlvl INT, is_id INT, durability INT,
                            charge_count INT, remaining_time INT, last_used DATETIME, bless INT,
                            attr_enchantlvl INT, special_enchant INT, end_time DATETIME, buy_time DATETIME,
                            package INT, bless_level INT, bless_level2 INT, item_level INT, Hotel_Town VARCHAR(64),
                            KEY owner_lookup (char_id)
                        ) ENGINE=InnoDB
                        """);
                execute(admin, "CREATE TABLE character_warehouse (id INT PRIMARY KEY, count INT) ENGINE=InnoDB");
                execute(admin, "CREATE TABLE cached_transaction_receipts (fixture_owner INT PRIMARY KEY) ENGINE=InnoDB");
                execute(admin, "INSERT INTO character_warehouse VALUES (10,5),(11,7)");
                execute(admin, "INSERT INTO character_items(id,item_id,char_id,count) VALUES "
                        + "(100," + ITEM_ID + ",900,1),(1000," + ITEM_ID + ",900,1)");
                final CyclicBarrier gate = new CyclicBarrier(2);
                try (var workers = Executors.newFixedThreadPool(2)) {
                    var first = workers.submit(() -> { withdraw(10, 220, 100, gate); return null; });
                    var second = workers.submit(() -> { withdraw(11, 221, 101, gate); return null; });
                    first.get(12, TimeUnit.SECONDS);
                    second.get(12, TimeUnit.SECONDS);
                }
                checkDuplicateRollbackAndConservation(admin);
            } finally {
                execute(admin, "DROP DATABASE " + schema);
            }
        }
        System.out.println("PASS independent warehouse inserts in one key gap and duplicate rollback");
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 2)
            throw new IllegalArgumentException("Pass the isolated server JDBC URL and user");
        DriverManager.setLoginTimeout(5);
        new CacheDWarehouseInsertConcurrency(arguments[0], arguments[1]).run();
    }
}
