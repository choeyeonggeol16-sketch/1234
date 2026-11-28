package l1j.server.server.model.world.siege;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;

import l1j.server.DatabaseFactory;
import l1j.server.server.model.gametime.CGameTime;

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

    static void saveMetadata(int castleId, String name, long startMillis, int taxRate) {
        try (var connection = DatabaseFactory.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE castle SET name=?,war_time=?,tax_rate=? WHERE castle_id=?")) {
            statement.setString(1, name);
            statement.setTimestamp(2, new Timestamp(startMillis));
            statement.setInt(3, taxRate);
            statement.setInt(4, castleId);
            if (statement.executeUpdate() > 1) throw new SQLException("Duplicate castle identity");
        } catch (SQLException failure) {
            throw new IllegalStateException("Castle metadata save", failure);
        }
    }
}
