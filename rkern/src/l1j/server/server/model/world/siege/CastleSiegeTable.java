package l1j.server.server.model.world.siege;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;

import l1j.server.DatabaseFactory;
import l1j.server.server.model.gametime.CGameTime;
import l1j.server.server.storage.cached.CTransaction;

/** Loads the complete castle configuration before the manager publishes a replacement. */
final class CastleSiegeTable {
    record Entry(int castleId, String name, CastleSiegeSchedule schedule, int taxRate, int publicMoney) {}

    private CastleSiegeTable() {}

    static List<Entry> load() throws SQLException {
        var entries = new ArrayList<Entry>();
        var castleIds = new HashSet<Integer>();
        try (var connection = DatabaseFactory.getInstance().getConnection();
             var statement = connection.prepareStatement("SELECT castle_id,name,war_time,tax_rate,public_money FROM castle");
             var rows = statement.executeQuery()) {
            while (rows.next()) {
                int id = rows.getInt("castle_id");
                Timestamp time = rows.getTimestamp("war_time");
                if (time == null || !castleIds.add(id)) {
                    throw new SQLException("Invalid siege schedule for castle " + id);
                }
                Calendar start = CGameTime.getRealTimeCalendar();
                start.setTimeInMillis(time.getTime());
                entries.add(new Entry(id, rows.getString("name"), new CastleSiegeSchedule(start),
                        rows.getInt("tax_rate"), rows.getInt("public_money")));
            }
        }
        return entries;
    }

    static void capture(Connection con, int castleId, int oldOwner, int winner,
            String name, long startMillis, int taxRate) throws SQLException {
        CTransaction.requireTransactionalTables(con, "castle", "clan_data");
        lockCastle(con, castleId);
        requireOwner(con, castleId, oldOwner);
        if (oldOwner != 0) changeOwnership(con, oldOwner, castleId, 0);
        changeOwnership(con, winner, 0, castleId);
        writeMetadata(con, castleId, name, startMillis, taxRate);
    }

    static void assign(Connection con, int clanId, int before, int after) throws SQLException {
        CTransaction.requireTransactionalTables(con, "castle", "clan_data");
        if (before != 0) {
            lockCastle(con, before);
            requireOwner(con, before, clanId);
        }
        if (after != 0) {
            lockCastle(con, after);
            requireOwner(con, after, 0);
        }
        changeOwnership(con, clanId, before, after);
    }

    private static void lockCastle(Connection con, int id) throws SQLException {
        try (var statement = con.prepareStatement("SELECT castle_id FROM castle WHERE castle_id=? FOR UPDATE")) {
            statement.setInt(1, id);
            try (var rows = statement.executeQuery()) {
                if (!rows.next() || rows.next()) throw new SQLException("Missing or duplicate castle " + id);
            }
        }
    }

    private static void requireOwner(Connection con, int castleId, int expected) throws SQLException {
        try (var statement = con.prepareStatement("SELECT clan_id FROM clan_data WHERE hascastle=? FOR UPDATE")) {
            statement.setInt(1, castleId);
            try (var rows = statement.executeQuery()) {
                if (expected == 0 ? rows.next() : !rows.next() || rows.getInt(1) != expected || rows.next())
                    throw new SQLException("Castle owner changed " + castleId);
            }
        }
    }

    private static void changeOwnership(Connection con, int clanId, int before, int after) throws SQLException {
        try (var statement = con.prepareStatement("UPDATE clan_data SET hascastle=? WHERE clan_id=? AND hascastle=?")) {
            statement.setInt(1, after);
            statement.setInt(2, clanId);
            statement.setInt(3, before);
            if (statement.executeUpdate() != 1) throw new SQLException("Clan ownership changed " + clanId);
        }
    }

    static void saveMetadata(int castleId, String name, long startMillis, int taxRate) {
        try (var connection = DatabaseFactory.getInstance().getConnection()) {
            writeMetadata(connection, castleId, name, startMillis, taxRate);
        } catch (SQLException failure) {
            throw new IllegalStateException("Castle metadata save", failure);
        }
    }

    private static void writeMetadata(Connection con, int castleId, String name, long startMillis,
            int taxRate) throws SQLException {
        try (PreparedStatement statement = con.prepareStatement(
                     "UPDATE castle SET name=?,war_time=?,tax_rate=? WHERE castle_id=?")) {
            statement.setString(1, name);
            statement.setTimestamp(2, new Timestamp(startMillis));
            statement.setInt(3, taxRate);
            statement.setInt(4, castleId);
            if (statement.executeUpdate() > 1) throw new SQLException("Duplicate castle identity");
        }
    }
}
