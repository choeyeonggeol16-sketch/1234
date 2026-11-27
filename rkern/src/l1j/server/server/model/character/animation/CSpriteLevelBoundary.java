package l1j.server.server.model.character.animation;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import gnu.trove.map.TIntIntMap;
import gnu.trove.map.hash.TIntIntHashMap;
import l1j.server.DatabaseFactory;

public final class CSpriteLevelBoundary {
    private static final Logger LOG = Logger.getLogger(CSpriteLevelBoundary.class.getName());
    private static final int NO_OVERRIDE = Integer.MIN_VALUE;
    private static volatile TIntIntMap levels = new TIntIntHashMap(16, 0.5f, 0, NO_OVERRIDE);

    private CSpriteLevelBoundary() {}

    public static synchronized boolean reload() {
        TIntIntMap loaded = new TIntIntHashMap(16, 0.5f, 0, NO_OVERRIDE);
        String query = "SELECT sprite_id, boundary_level FROM spr_boundary";
        try (Connection connection = DatabaseFactory.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(query);
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                loaded.put(rows.getInt("sprite_id"), rows.getInt("boundary_level"));
            }
            levels = loaded;
            return true;
        } catch (Exception error) {
            LOG.log(Level.SEVERE, "Could not load sprite level boundaries; keeping the previous table", error);
            return false;
        }
    }

    static int effectiveLevel(int spriteId, int level) {
        int override = levels.get(spriteId);
        return override == NO_OVERRIDE ? level : override;
    }
}
