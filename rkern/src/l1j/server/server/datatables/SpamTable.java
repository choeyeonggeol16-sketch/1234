package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import l1j.server.DatabaseFactory;
import l1j.server.server.model.L1ExcludingList;
import l1j.server.server.utils.SQLUtil;

public class SpamTable {

    private final static Logger _log = Logger.getLogger(SpamTable.class.getName());

    private static SpamTable _instance;

    private final Map<Integer, L1ExcludingList> _excludes = new ConcurrentHashMap<Integer, L1ExcludingList>();

    public static SpamTable getInstance() {
        if (_instance == null) {
            _instance = new SpamTable();
        }
        return _instance;
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


}
