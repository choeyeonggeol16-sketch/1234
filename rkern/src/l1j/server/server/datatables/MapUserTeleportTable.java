package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.DatabaseFactory;
import l1j.server.server.utils.SQLUtil;

/**
 * map_user_teleport(name -> x,y,map,need_item) cache
 * - O(1) lookup
 * - reload() atomic swap
 * - immutable snapshot map (safe for reads)
 */
public final class MapUserTeleportTable {

    private static final Logger LOG = Logger.getLogger(MapUserTeleportTable.class.getName());
    private static volatile MapUserTeleportTable _instance;

    /** Immutable snapshot for lock-free hot-path reads */
    private volatile Map<String, TeleportLoc> teleportByName;

    private static final String SQL =
            "SELECT name, locX, locY, locMap, need_item FROM map_user_teleport";

    private MapUserTeleportTable() {
        this.teleportByName = load();
    }

    public static MapUserTeleportTable getInstance() {
        MapUserTeleportTable inst = _instance;
        if (inst == null) {
            synchronized (MapUserTeleportTable.class) {
                inst = _instance;
                if (inst == null) {
                    inst = new MapUserTeleportTable();
                    _instance = inst;
                }
            }
        }
        return inst;
    }

    /** Hot reload (atomic swap) */
    public static void reload() {
        final MapUserTeleportTable fresh = new MapUserTeleportTable();
        _instance = fresh;
        LOG.info("[ManagerUserTeleportTable] reloaded. size=" + fresh.teleportByName.size());
    }

    /** null if not found */
    public TeleportLoc getTeleportLoc(String name) {
        if (name == null) return null;
        return teleportByName.get(normalizeKey(name));
    }

    /** Optional: for admin/debug */
    public int size() {
        return teleportByName.size();
    }

    // ---------------- internal ----------------

    private static String normalizeKey(String s) {
        return s.trim();
    }

    private Map<String, TeleportLoc> load() {
        final HashMap<String, TeleportLoc> map = new HashMap<>(256);

        Connection con = null;
        PreparedStatement pstm = null;
        ResultSet rs = null;

        try {
            con = DatabaseFactory.getInstance().getConnection();
            pstm = con.prepareStatement(SQL);
            rs = pstm.executeQuery();

            while (rs.next()) {
                final String keyRaw = rs.getString(1); // name
                if (keyRaw == null) continue;

                final String key = normalizeKey(keyRaw);
                if (key.isEmpty()) continue;

                final int x = rs.getInt(2);
                final int y = rs.getInt(3);
                final int mapId = rs.getInt(4);
                final int needItem = rs.getInt(5);

                // 마지막 값 우선(중복 name 있으면 덮어씀)
                map.put(key, new TeleportLoc(x, y, mapId, needItem));
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "[map_user_teleport] load failed", e);
        } finally {
            SQLUtil.close(rs, pstm, con);
        }

        return Collections.unmodifiableMap(map);
    }

    /** Immutable DTO */
    public static final class TeleportLoc {
        private final int x;
        private final int y;
        private final int mapId;
        private final int needItem;

        public TeleportLoc(int x, int y, int mapId, int needItem) {
            this.x = x;
            this.y = y;
            this.mapId = mapId;
            this.needItem = needItem;
        }

        public int getX() { return x; }
        public int getY() { return y; }
        public int getMapId() { return mapId; }
        public int getNeedItem() { return needItem; }
    }
}