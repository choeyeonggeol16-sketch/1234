package l1j.server.server.model.inventory.warehouse;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

import l1j.server.server.datatables.KeyTable;
import l1j.server.server.model.Instance.L1ItemInstance;

/** Run against a disposable JDBC URL with a selected schema; tables are connection-local. */
public final class WarehouseMoveSqlRegression {
    private static void execute(Connection con, String sql) throws SQLException {
        try (var ps = con.createStatement()) { ps.executeUpdate(sql); }
    }

    private static void check(boolean valid, String message) {
        if (!valid) throw new AssertionError(message);
    }

    @FunctionalInterface
    private interface SqlAction { void run() throws SQLException; }

    private static void expectStale(SqlAction action) throws SQLException {
        try { action.run(); }
        catch (SQLException expected) {
            if (expected.getSQLState() != null) throw expected;
            return;
        }
        throw new AssertionError("Stale warehouse operation was accepted");
    }

    private static Connection countStatements(Connection connection, AtomicInteger count) {
        return (Connection) Proxy.newProxyInstance(WarehouseMoveSqlRegression.class.getClassLoader(),
                new Class<?>[] { Connection.class }, (proxy, method, args) -> {
                    if (method.getName().equals("prepareStatement")) count.incrementAndGet();
                    try { return method.invoke(connection, args); }
                    catch (InvocationTargetException failure) { throw failure.getCause(); }
                });
    }

    private static void warehouse(Connection con, int type, String table, String ownerColumn) throws SQLException {
        execute(con, "CREATE TEMPORARY TABLE " + table + "(id INT PRIMARY KEY, " + ownerColumn
                + " VARCHAR(64), item_id INT, count INT) ENGINE=InnoDB");
        execute(con, "INSERT INTO " + table + " VALUES (1,'owner',401,12)");
        AtomicInteger statements = new AtomicInteger();
        Connection counted = countStatements(con, statements);
        Warehouse.updateWarehouseMove(counted, type, "owner", 1, 401, 12, 10);
        expectStale(() -> Warehouse.updateWarehouseMove(counted, type, "owner", 1, 401, 12, 8));
        expectStale(() -> Warehouse.updateWarehouseMove(counted, type, "other", 1, 401, 10, 8));
        expectStale(() -> Warehouse.updateWarehouseMove(counted, type, "owner", 1, 402, 10, 8));
        expectStale(() -> Warehouse.deleteWarehouseMove(counted, type, "owner", 1, 401, 12));
        expectStale(() -> Warehouse.deleteWarehouseMove(counted, type, "other", 1, 401, 10));
        expectStale(() -> Warehouse.deleteWarehouseMove(counted, type, "owner", 1, 402, 10));
        Warehouse.deleteWarehouseMove(counted, type, "owner", 1, 401, 10);
        expectStale(() -> Warehouse.deleteWarehouseMove(counted, type, "owner", 1, 401, 10));
        check(statements.get() == 9, table + " did not use one guarded statement per move");
    }

    private static void bossKeys(Connection con) throws SQLException {
        execute(con, "CREATE TEMPORARY TABLE character_items (id INT PRIMARY KEY, item_id INT, char_id INT, KEY (char_id))");
        execute(con, "CREATE TEMPORARY TABLE item_key_boss (item_obj_id INT, key_id INT, PRIMARY KEY(item_obj_id,key_id))");
        execute(con, "INSERT INTO character_items VALUES (1,80500,80),(2,80500,80),(3,80500,80),(4,80500,90),(5,40001,80)");
        execute(con, "INSERT INTO item_key_boss VALUES (1,77),(1,33),(2,44),(4,88),(5,99)");
        var keys = new HashMap<Integer, L1ItemInstance>();
        for (int id = 1; id <= 5; id++) keys.put(id, new L1ItemInstance());
        int missing = keys.get(3).getKeyId();
        AtomicInteger statements = new AtomicInteger();
        KeyTable.loadInventoryKeys(countStatements(con, statements), 80, keys);
        check(statements.get() == 1, "boss keys should use one read on the inventory connection");
        check(keys.get(1).getKeyId() == 33 && keys.get(2).getKeyId() == 44, "boss key mappings");
        check(keys.get(3).getKeyId() == missing && keys.get(4).getKeyId() == missing
                && keys.get(5).getKeyId() == missing, "missing, other owner or non-key object");
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Pass a disposable JDBC schema URL and user");
        String password = System.getenv().getOrDefault("RKERN_STORAGE_TEST_PASSWORD", "");
        try (Connection con = DriverManager.getConnection(args[0], args[1], password)) {
            warehouse(con, Warehouse.PRIVATE_ROWS, "character_warehouse", "account_name");
            warehouse(con, Warehouse.CLAN_ROWS, "clan_warehouse", "clan_name");
            warehouse(con, Warehouse.ELF_ROWS, "character_elf_warehouse", "account_name");
            warehouse(con, Warehouse.SPECIAL_ROWS, "character_special_warehouse", "char_name");
            warehouse(con, Warehouse.SUPPLEMENT_ROWS, "character_supplementary_service", "account_name");
            warehouse(con, Warehouse.PACKAGE_ROWS, "character_package_warehouse", "account_name");
            bossKeys(con);
        }
        System.out.println("PASS six warehouse row guards and batched inventory key mappings");
    }
}
