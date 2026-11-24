package l1j.server.server.model.character.bookmark;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.IdFactory;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.L1HouseLocation;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1WorldMap;
import l1j.server.server.model.network.core.sc.packet.SC_BookMarkLoad;
import l1j.server.server.model.network.core.sc.packet.SC_Bookmarks;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.storage.CacheD;
import l1j.server.server.storage.cached.CTransaction;
import l1j.server.server.templates.L1BookMark;
import l1j.server.server.templates.L1ItemBookMark;

/**
 * User-owned NC CUserBookmark role adapted to the L1 wire protocol.
 * Stable database row IDs, session-local wire indices, persistent display order,
 * and five shortcut positions are distinct. CacheD's existing owner receipt
 * serializes mutations with orb/slot expansion and replays only after COMMIT.
 * No new executor, queue, or separate per-slot container.
 */
public final class CUserBookmark {
    private static final Logger LOG = Logger.getLogger(CUserBookmark.class.getName());
    public static final int MAX_SLOT = 100, SHORTCUTS = 5;
    private final ArrayList<L1BookMark> slots = new ArrayList<>();
    private final ArrayList<L1BookMark> shortcuts = new ArrayList<>(SHORTCUTS);

    // Compatibility views are the actual owner lists, not copied state.
    public ArrayList<L1BookMark> slots() { return slots; }
    public ArrayList<L1BookMark> shortcuts() { return shortcuts; }

    public L1BookMark FindSlot(String name) {
        if (name == null) return null;
        for (int i = 0, n = slots.size(); i < n; i++) {
            L1BookMark b = slots.get(i);
            if (b != null && b.getName() != null && b.getName().equalsIgnoreCase(name)) return b;
        }
        return null;
    }

    public L1BookMark FindById(int id) {
        for (int i = 0, n = slots.size(); i < n; i++) {
            L1BookMark b = slots.get(i);
            if (b != null && b.getId() == id) return b;
        }
        return null;
    }

    public L1BookMark FindWireSlot(int id) {
        if (id < 0 || id >= slots.size()) return null;
        L1BookMark b = slots.get(id);
        return b != null && b.getNumId() == id ? b : null;
    }

    public void OnLoadList(ArrayList<L1BookMark> rows) {
        if (rows == slots) throw new IllegalArgumentException("Detached bookmark snapshot required");
        slots.clear();
        shortcuts.clear();
        slots.addAll(rows);
        slots.sort(Comparator.comparingInt(L1BookMark::getTemp_id).thenComparingInt(L1BookMark::getId));
        reindex();
    }

    public void AppendCommittedSlot(L1BookMark b) {
        if (b == null) return;
        b.setNumId(slots.size());
        slots.add(b);
        if (b.getSpeed_id() >= 0 && b.getSpeed_id() < SHORTCUTS) rebuildShortcuts();
    }

    public void RemoveCommittedSlot(L1BookMark b) {
        if (!slots.remove(b)) return;
        reindex();
    }

    private void reindex() {
        for (int i = 0; i < slots.size(); i++) slots.get(i).setNumId(i);
        rebuildShortcuts();
    }

    private void rebuildShortcuts() {
        shortcuts.clear();
        int used = 0;
        for (int s = 0; s < SHORTCUTS; s++) {
            for (int i = 0, n = slots.size(); i < n; i++) {
                L1BookMark b = slots.get(i);
                if (b.getSpeed_id() == s) {
                    if ((used & (1 << s)) == 0) {
                        shortcuts.add(b);
                        used |= 1 << s;
                    } else b.setSpeed_id(-1);
                }
            }
        }
    }

    private static boolean active(L1PcInstance pc) {
        return pc != null && !pc.isGhost() && !pc.isLogoutStarted()
                && pc.getNetConnection() != null && pc.getNetConnection().getActiveChar() == pc;
    }

    private boolean ready(L1PcInstance pc) {
        if (!active(pc)) return false;
        if (pc.isItemTransferBusy() || pc.getTradeID() != 0 || pc.isTradeResolving()) {
            SC_SystemMessage.send(pc, "기억 장소 처리 중입니다. 잠시 후 다시 시도해 주세요.");
            return false;
        }
        return true;
    }

    public static boolean validTitle(String s) {
        return s != null && !s.isEmpty() && s.length() <= 255 && s.indexOf('\0') < 0;
    }

    public static boolean CanSaveBookmark(L1PcInstance pc) {
        return active(pc) && !pc.isDead() && pc.getMap() != null
                && (pc.isGm() || pc.getMap().isMarkable() && !isRestrictedArea(pc));
    }

    public static boolean IsValidLocation(int x, int y, short mapId) {
        final L1Map map = L1WorldMap.getInstance().getMap(mapId);
        return map != null && map.getId() == mapId && map.isInMap(x, y) && map.isPassable(x, y);
    }

    /** L1 requests use the coordinate triple, not the L2 slot-ID packet.
     * Match the owned row first, then validate the current geodata. */
    public boolean CanUseBookmark(L1PcInstance pc, int x, int y, short mapId) {
        if (pc == null || pc.isDead() || pc.isGhost() || pc.isLogoutStarted()) return false;
        for (int i = 0, n = slots.size(); i < n; i++) {
            L1BookMark b = slots.get(i);
            if (b.getLocX() == x && b.getLocY() == y && b.getMapId() == mapId)
                return IsValidLocation(x, y, mapId);
        }
        return false;
    }

    public void SaveSlot(L1PcInstance pc, String name) {
        if (!ready(pc) || !validTitle(name)) return;
        if (!CanSaveBookmark(pc)) {
            SC_ServerMessage.send(pc, 214);
            return;
        }
        final int x = pc.getX(), y = pc.getY();
        final short mapId = pc.getMapId();
        if (!IsValidLocation(x, y, mapId)) {
            SC_ServerMessage.send(pc, 214);
            return;
        }
        save(pc, name, x, y, mapId, 0, 0);
    }

    public void SaveItemSlot(L1PcInstance pc, L1ItemBookMark item) {
        if (!ready(pc) || !validTitle(item.getName())) return;
        save(pc, item.getName(), item.getLocX(), item.getLocY(),
                item.getMapId(), item.getRandomX(), item.getRandomY());
    }

    private void save(L1PcInstance pc, String name, int x, int y, short mapId, int rx, int ry) {
        final int limit = Math.clamp(pc.getMark_count(), 0, MAX_SLOT);
        final int count = slots.size();
        if (count >= limit) {
            SC_ServerMessage.send(pc, limit == MAX_SLOT ? 2930 : 676);
            return;
        }
        if (FindSlot(name) != null) {
            SC_ServerMessage.send(pc, 1655);
            return;
        }
        // NC allocates the first vacant slot. L1 uses dense wire IDs but its
        // persistent ordering can be sparse. Choose a free display position.
        int next = 0;
        for (int i = 0; i < count; i++) {
            int pos = slots.get(i).getTemp_id();
            if (pos >= 0 && pos < MAX_SLOT) next = Math.max(next, pos + 1);
        }
        if (next >= MAX_SLOT) {
            next = 0;
            while (next < MAX_SLOT && hasOrder(next)) next++;
        }
        if (next >= MAX_SLOT) return;
        final int owner = pc.getId(), position = next;
        final L1BookMark b = new L1BookMark();
        b.setId(IdFactory.getInstance().nextId());
        b.setCharId(owner); b.setNumId(count); b.setTemp_id(position);
        b.setSpeed_id(-1); b.setName(name); b.setLocX(x); b.setLocY(y);
        b.setMapId(mapId); b.setRandomX(rx); b.setRandomY(ry);
        mutate(pc, con -> {
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO character_teleport(id,char_id,num_id,speed_id,name,locx,locy,mapid,randomX,randomY) VALUES(?,?,?,?,?,?,?,?,?,?)")) {
                ps.setInt(1, b.getId()); ps.setInt(2, owner);
                ps.setInt(3, position); ps.setInt(4, -1);
                ps.setString(5, name); ps.setInt(6, x); ps.setInt(7, y);
                ps.setShort(8, mapId); ps.setInt(9, rx); ps.setInt(10, ry);
                if (ps.executeUpdate() != 1) throw new SQLException("Bookmark insert affected != 1");
            }
        }, () -> {
            AppendCommittedSlot(b);
            if (!SC_Bookmarks.send(pc, name, mapId, x, y, b.getNumId())) resync(pc);
        });
    }

    private boolean hasOrder(int pos) {
        for (int i = 0, n = slots.size(); i < n; i++)
            if (slots.get(i).getTemp_id() == pos) return true;
        return false;
    }

    public void DeleteSlot(L1PcInstance pc, String name) {
        if (!ready(pc)) return;
        final L1BookMark b = FindSlot(name);
        if (b == null) return;
        final int owner = pc.getId(), id = b.getId();
        mutate(pc, con -> {
            try (PreparedStatement ps = con.prepareStatement(
                    "DELETE FROM character_teleport WHERE id=? AND char_id=?")) {
                ps.setInt(1, id); ps.setInt(2, owner);
                if (ps.executeUpdate() != 1) throw new SQLException("Stale bookmark deletion");
            }
        }, () -> {
            RemoveCommittedSlot(b);
            slots.sort(Comparator.comparingInt(L1BookMark::getTemp_id).thenComparingInt(L1BookMark::getId));
            reindex();
            resync(pc);
        });
    }

    public void DeleteAllSlots(L1PcInstance pc) {
        if (!ready(pc) || slots.isEmpty()) return;
        final int owner = pc.getId(), count = slots.size();
        mutate(pc, con -> {
            try (PreparedStatement ps = con.prepareStatement(
                    "DELETE FROM character_teleport WHERE char_id=?")) {
                ps.setInt(1, owner);
                if (ps.executeUpdate() != count) throw new SQLException("Bookmark list changed");
            }
        }, () -> { slots.clear(); shortcuts.clear(); resync(pc); });
    }

    public void ModifySlots(L1PcInstance pc, int[] wireIds, String[] names) {
        if (!ready(pc) || wireIds == null || names == null
                || wireIds.length != names.length || wireIds.length == 0
                || wireIds.length > slots.size() || wireIds.length > MAX_SLOT) return;
        final int n = wireIds.length, owner = pc.getId();
        final L1BookMark[] targets = new L1BookMark[n];
        final String[] titles = names.clone();
        final boolean[] seen = new boolean[slots.size()];
        boolean changed = false;
        for (int i = 0; i < n; i++) {
            final int index = wireIds[i];
            final L1BookMark b = FindWireSlot(index);
            if (b == null || seen[index] || !validTitle(titles[i])) { resync(pc); return; }
            seen[index] = true; targets[i] = b;
            changed |= !titles[i].equals(b.getName());
        }
        if (!changed) return;
        mutate(pc, con -> {
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE character_teleport SET name=? WHERE id=? AND char_id=?")) {
                for (int i = 0; i < n; i++) {
                    ps.setString(1, titles[i]); ps.setInt(2, targets[i].getId()); ps.setInt(3, owner);
                    ps.addBatch();
                }
                checkBatch(ps.executeBatch(), n);
            }
        }, () -> {
            for (int i = 0; i < n; i++) targets[i].setName(titles[i]);
        });
    }

    /** Parse every field before touching any live row. Keep wire IDs stable
     * until a full list resync; num_id persists display order, not wire ID. */
    public void ChangeSlot(L1PcInstance pc, byte[] packet) {
        if (!ready(pc)) return;
        final int n = slots.size(), limit = Math.clamp(pc.getMark_count(), 0, MAX_SLOT);
        final int[] layout = DecodeLayout(packet, n, limit);
        if (layout == null) { resync(pc); return; }
        if (n == 0) return;
        final int[] rows = new int[n];
        final int[] oldOrder = new int[n];
        boolean changed = false;
        for (int i = 0; i < n; i++) {
            L1BookMark b = slots.get(i);
            rows[i] = b.getId(); oldOrder[i] = b.getTemp_id();
            changed |= oldOrder[i] != layout[i] || b.getSpeed_id() != layout[n+i];
        }
        if (!changed) return;
        final int owner = pc.getId();
        mutate(pc, con -> writeLayout(con, owner, rows, oldOrder, layout), () -> {
            for (int i = 0; i < n; i++) {
                L1BookMark b = slots.get(i);
                b.setTemp_id(layout[i]);
                b.setSpeed_id(layout[n+i]);
            }
            rebuildShortcuts();
        });
    }

    /** Returns display orders then shortcut indices (-1 if absent).
     * The existing client sends one header, N positions and five references.
     * 0 is valid, 255 is an empty shortcut, never an early return. */
    public static int[] DecodeLayout(byte[] packet, int count, int capacity) {
        if (packet == null || count < 0 || count > MAX_SLOT
                || capacity < count || capacity > MAX_SLOT
                || packet.length < 1 + count + SHORTCUTS) return null;
        final int[] result = new int[count * 2];
        final boolean[] seen = new boolean[capacity];
        for (int i = 0; i < count; i++) {
            int v = packet[1+i] & 255;
            if (v >= capacity || seen[v]) return null;
            seen[v] = true;
            result[i] = v; result[count+i] = -1;
        }
        for (int i = 0; i < SHORTCUTS; i++) {
            int v = packet[1+count+i] & 255;
            if (v == 255) continue;
            if (v >= count || result[count+v] != -1) return null;
            result[count+v] = i;
        }
        return result;
    }

    private static void writeLayout(Connection con, int owner, int[] rows, int[] previous, int[] layout)
            throws SQLException {
        // Move rows to disjoint temporary display positions before swapping.
        // Both steps are in the same receipt, including slot zero.
        int max = MAX_SLOT;
        for (int v : previous) max = Math.max(max, v);
        if (max > Integer.MAX_VALUE - rows.length - 1) throw new SQLException("Invalid persisted bookmark order");
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE character_teleport SET num_id=?,speed_id=? WHERE id=? AND char_id=?")) {
            for (int i = 0; i < rows.length; i++) {
                ps.setInt(1, max + 1 + i); ps.setInt(2, -1);
                ps.setInt(3, rows[i]); ps.setInt(4, owner); ps.addBatch();
            }
            checkBatch(ps.executeBatch(), rows.length);
            ps.clearBatch();
            for (int i = 0; i < rows.length; i++) {
                ps.setInt(1, layout[i]); ps.setInt(2, layout[rows.length+i]);
                ps.setInt(3, rows[i]); ps.setInt(4, owner); ps.addBatch();
            }
            checkBatch(ps.executeBatch(), rows.length);
        }
    }

    private static void checkBatch(int[] counts, int n) throws SQLException {
        if (counts.length != n) throw new SQLException("Incomplete bookmark batch");
        for (int count : counts) {
            // MySQL can report zero for an unchanged value; accept that only
            // after the owner/ID preimage is checked below by the transaction.
            if (count < 0 && count != java.sql.Statement.SUCCESS_NO_INFO || count > 1)
                throw new SQLException("Invalid bookmark update count");
        }
    }

    private static void requireOwner(Connection con, int owner, int expected, int limit, boolean adding)
            throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT Mark_Count FROM characters WHERE objid=? FOR UPDATE")) {
            ps.setInt(1, owner);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || adding && expected >= Math.min(limit, rs.getInt(1)))
                    throw new SQLException("Bookmark capacity/owner changed");
            }
        }
        int actual = 0;
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT id FROM character_teleport WHERE char_id=? FOR UPDATE")) {
            ps.setInt(1, owner);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) actual++;
            }
        }
        if (actual != expected) throw new SQLException("Bookmark preimage changed");
    }

    private void mutate(L1PcInstance pc, CTransaction.Mutation work, Runnable apply) {
        final int owner = pc.getId(), count = slots.size(), limit = pc.getMark_count();
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc,
                CacheD.accountOrderKey("bookmark:" + owner))) {
            tx.sql(con -> {
                CTransaction.requireTransactionalTables(con, "characters", "character_teleport");
                requireOwner(con, owner, count, limit, false);
                work.apply(con);
            });
            tx.onFailure(() -> {
                SC_SystemMessage.send(pc, "기억 장소 저장에 실패했습니다. 다시 시도해 주세요.");
                resync(pc);
            });
            tx.commit(apply);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Bookmark request not admitted: " + owner, e);
            SC_SystemMessage.send(pc, "기억 장소를 처리할 수 없습니다. 잠시 후 다시 시도해 주세요.");
            resync(pc);
        }
    }

    private static void resync(L1PcInstance pc) {
        if (!active(pc)) return;
        if (!SC_BookMarkLoad.send(pc)) {
            // Fail closed rather than retain stale client slot indices.
            pc.getNetConnection().close();
        }
    }

    private static boolean isRestrictedArea(
            L1PcInstance pc) {

        final int x =
                pc.getX();

        final int y =
                pc.getY();

        final int mapId =
                pc.getMapId();

        if (L1CastleLocation.checkInAllWarArea(
                x,
                y,
                (short) mapId)) {

            return true;
        }

        if (L1HouseLocation.isInHouse(
                x,
                y,
                (short) mapId)) {

            return true;
        }

        /*
         * map 4 전용 지역.
         * mapId 비교를 매 조건마다 반복하지 않는다.
         */
        if (mapId == 4) {

            if (inRect(
                    x, y,
                    33514, 33809,
                    32216, 32457)) {

                return true;
            }

            if (inRect(
                    x, y,
                    34280, 34287,
                    33103, 33492)) {

                return true;
            }

            if (inRect(
                    x, y,
                    33464, 33532,
                    32839, 32878)) {

                return true;
            }

            if (inRect(
                    x, y,
                    34207, 34287,
                    33097, 33515)) {

                return true;
            }

            // 용뼈
            if (inRect(
                    x, y,
                    33261, 33266,
                    32397, 32405)) {

                return true;
            }

            // 용뼈
            if (inRect(
                    x, y,
                    33331, 33339,
                    32432, 32440)) {

                return true;
            }

            // 용뼈
            if (inRect(
                    x, y,
                    33388, 33396,
                    32340, 32348)) {

                return true;
            }

            // 한국민
            if (inRect(
                    x, y,
                    33449, 33473,
                    32324, 32347)) {

                return true;
            }

            // 악어섬
            if (inRect(
                    x, y,
                    33470, 33530,
                    33177, 33231)) {

                return true;
            }

            // 샌드웜
            if (inRect(
                    x, y,
                    32707, 32826,
                    33117, 33229)) {

                return true;
            }
        }

        // 정무
        return mapId == 430
                || mapId == 15430;
    }

    private static boolean inRect(
            int x,
            int y,
            int minX,
            int maxX,
            int minY,
            int maxY) {

        return x >= minX
                && x <= maxX
                && y >= minY
                && y <= maxY;
    }
}
