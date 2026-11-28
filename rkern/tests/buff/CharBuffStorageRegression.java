package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.UUID;

import gnu.trove.list.array.TIntArrayList;

public final class CharBuffStorageRegression {
    public static void main(String[] args) throws Exception {
        final String schema = "rkern_buffs_" + UUID.randomUUID().toString().replace("-", "");
        final String password = System.getenv().getOrDefault("RKERN_STORAGE_TEST_PASSWORD", "");
        try (var con = DriverManager.getConnection(args[0], args[1], password); var sql = con.createStatement()) {
            sql.executeUpdate("CREATE DATABASE " + schema);
            try {
                con.setCatalog(schema);
                sql.executeUpdate("""
                        CREATE TABLE character_buff (seq INT AUTO_INCREMENT PRIMARY KEY,
                            char_obj_id INT,skill_id INT,remaining_time INT,poly_id INT,KEY owner_lookup (char_obj_id))
                        ENGINE=InnoDB
                        """);
                sql.executeUpdate("CREATE TABLE changes (kind CHAR(1)) ENGINE=InnoDB");
                sql.executeUpdate("CREATE TRIGGER buff_insert AFTER INSERT ON character_buff FOR EACH ROW INSERT INTO changes VALUES ('I')");
                sql.executeUpdate("CREATE TRIGGER buff_update AFTER UPDATE ON character_buff FOR EACH ROW INSERT INTO changes VALUES ('U')");
                sql.executeUpdate("CREATE TRIGGER buff_delete AFTER DELETE ON character_buff FOR EACH ROW INSERT INTO changes VALUES ('D')");
                final int[] initial = {67, 40, 99, 3, 20, 0, 2, 0, 0};
                final int[] advanced = {67, 39, 100, 3, 20, 0, 2, 0, 0};
                save(con, initial, true);
                sql.executeUpdate("DELETE FROM changes");
                save(con, initial, true);
                check(writes(con) == 0, "same image avoids all row writes");
                save(con, advanced, true);
                check(writes(con) == 1 && Arrays.equals(read(con), advanced), "one changed duration and sprite update");
                save(con, initial, false);
                check(writes(con) == 1 && Arrays.equals(read(con), advanced), "rolled-back update leaves original rows");
                final int[] duplicate = {67, 38, 100, 67, 9, 101, 2, 0, 0};
                save(con, duplicate, true);
                check(Arrays.equals(read(con), duplicate), "shape change preserves ordered duplicate rows");
                sql.executeUpdate("DELETE FROM changes");
                save(con, duplicate, true);
                check(writes(con) == 0, "same duplicate image is also a no-op");
                save(con, new int[0], true);
                check(read(con).length == 0, "removal clears only this character");
                System.out.println("PASS buff unchanged: 6 to 0 writes; one changed effect: 6 to 1; rollback and duplicate order");
            } finally {
                con.setCatalog("mysql");
                sql.executeUpdate("DROP DATABASE " + schema);
            }
        }
    }

    private static void save(Connection con, int[] rows, boolean commit) throws SQLException {
        con.setAutoCommit(false);
        try {
            CharBuffTable.persistRows(con, 10, rows);
            if (commit) con.commit();
            else con.rollback();
        } finally {
            con.rollback();
            con.setAutoCommit(true);
        }
    }

    private static int[] read(Connection con) throws SQLException {
        final var result = new TIntArrayList();
        try (var sql = con.createStatement(); var rows = sql.executeQuery(
                "SELECT skill_id,remaining_time,poly_id FROM character_buff WHERE char_obj_id=10")) {
            while (rows.next()) for (int column = 1; column <= 3; column++) result.add(rows.getInt(column));
        }
        return result.toArray();
    }

    private static int writes(Connection con) throws SQLException {
        try (var sql = con.createStatement(); var rows = sql.executeQuery("SELECT COUNT(*) FROM changes")) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
