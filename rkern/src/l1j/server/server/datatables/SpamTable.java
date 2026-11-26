package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import l1j.server.DatabaseFactory;
import l1j.server.server.model.L1ExcludingList;

public class SpamTable {

    private static final SpamTable INSTANCE = new SpamTable();

    private final Map<Integer, L1ExcludingList> _excludes = new ConcurrentHashMap<Integer, L1ExcludingList>();

    public static SpamTable getInstance() {
        return INSTANCE;
    }

    public void addExclude(int charId, int objId, String name) {
        String sql = "INSERT INTO character_exclude SET char_id=?, exclude_id=?, exclude_name=?";

        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement pstm = con.prepareStatement(sql)) {

            pstm.setInt(1, charId);
            pstm.setInt(2, objId);
            pstm.setString(3, name);
            pstm.execute();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean ExcludeList(int charId, String spamname) {
        String sql = "SELECT * FROM character_exclude WHERE char_id = ? AND exclude_name = ?";

        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement pstm = con.prepareStatement(sql);
             ResultSet rs = pstm.executeQuery()) {

            return rs.next();

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }
    
    public L1ExcludingList getExcludeTable(int charId) {
        L1ExcludingList exclude = _excludes.get(charId);
        if (exclude == null) {
            exclude = _excludes.computeIfAbsent(charId, k -> new L1ExcludingList(charId));
        }
        return exclude;
    }

    /** Reading a broadcast audience must not create or retain empty lists. */
    public boolean excludesChat(int charId, String name) {
        final L1ExcludingList exclude = _excludes.get(charId);
        return exclude != null && exclude.contains(0, name);
    }

}
