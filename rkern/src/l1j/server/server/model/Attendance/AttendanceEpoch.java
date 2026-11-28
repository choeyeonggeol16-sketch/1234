package l1j.server.server.model.Attendance;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;

import l1j.server.server.storage.cached.CTransaction;

/** The second startup row fences a manual reset independently of the daily calendar. */
public final class AttendanceEpoch {
    static final long INITIAL = 946_684_800_000L;
    private static final int DAY_ROW = 1;
    private static final int EPOCH_ROW = 2;

    private AttendanceEpoch() {}

    static void ensure(Connection con) throws SQLException {
        try (var statement = con.prepareStatement(
                "INSERT IGNORE INTO attendance_startup (id,startup_info) VALUES (?,?)")) {
            statement.setInt(1, EPOCH_ROW);
            statement.setTimestamp(2, new Timestamp(INITIAL));
            statement.executeUpdate();
        }
    }

    public static long lockShared(Connection con) throws SQLException {
        return read(con, EPOCH_ROW, " LOCK IN SHARE MODE");
    }

    static long readDay(Connection con) throws SQLException {
        return read(con, DAY_ROW, "");
    }

    private static long read(Connection con, int id, String lock) throws SQLException {
        try (var statement = con.prepareStatement(
                "SELECT startup_info FROM attendance_startup WHERE id=?" + lock)) {
            statement.setInt(1, id);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) return 0L;
                final Timestamp value = rows.getTimestamp(1);
                return value == null ? 0L : value.getTime();
            }
        }
    }

    static void writeDay(Connection con, long day) throws SQLException {
        write(con, DAY_ROW, day);
    }

    static void stageReset(Connection con, long before, long after, long day) throws SQLException {
        CTransaction.requireTransactionalTables(con, "attendance_startup", "attendance_userinfo");
        if (after != before + 1000L || read(con, EPOCH_ROW, " FOR UPDATE") != before)
            throw new SQLException("Attendance reset generation changed");
        try (var statement = con.prepareStatement("DELETE FROM attendance_userinfo")) {
            statement.executeUpdate();
        }
        writeDay(con, day);
        write(con, EPOCH_ROW, after);
    }

    private static void write(Connection con, int id, long value) throws SQLException {
        try (var statement = con.prepareStatement(
                "INSERT INTO attendance_startup (id,startup_info) VALUES (?,?) "
                + "ON DUPLICATE KEY UPDATE startup_info=VALUES(startup_info)")) {
            statement.setInt(1, id);
            statement.setTimestamp(2, new Timestamp(value));
            statement.executeUpdate();
        }
    }
}
