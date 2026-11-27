package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.DatabaseFactory;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.templates.L1Town;

public final class TownTable {
    private static final Logger LOG = Logger.getLogger(TownTable.class.getName());
    private volatile Map<Integer, L1Town> towns = Map.of();

    private static final class Holder {
        private static final TownTable INSTANCE = new TownTable();
    }

    private TownTable() {
        try (Connection con = DatabaseFactory.getInstance().getConnection()) {
            publish(read(con, false));
        } catch (SQLException failure) {
            LOG.log(Level.SEVERE, "Cannot load town catalog", failure);
        }
    }

    public static TownTable getInstance() { return Holder.INSTANCE; }

    public L1Town getTownTable(int id) { return towns.get(id); }

    public boolean isLeader(L1PcInstance pc, int townId) {
        L1Town town = getTownTable(townId);
        return pc != null && town != null && town.leaderId() == pc.getId();
    }

    public static Map<Integer, L1Town> read(Connection con, boolean lockRows) throws SQLException {
        String sql = "SELECT town_id,name,leader_id,leader_name,tax_rate,town_fix_tax FROM town ORDER BY town_id";
        Map<Integer, L1Town> result = new HashMap<>();
        try (var ps = con.prepareStatement(lockRows ? sql + " FOR UPDATE" : sql);
             var rows = ps.executeQuery()) {
            while (rows.next()) {
                int id = rows.getInt(1);
                var town = new L1Town(id, rows.getString(2), rows.getInt(3), rows.getString(4),
                        rows.getInt(5), rows.getLong(6));
                if (result.putIfAbsent(id, town) != null) throw new SQLException("Duplicate town " + id);
            }
        }
        return Map.copyOf(result);
    }

    public void publish(Map<Integer, L1Town> snapshot) {
        towns = Map.copyOf(snapshot);
    }
}
