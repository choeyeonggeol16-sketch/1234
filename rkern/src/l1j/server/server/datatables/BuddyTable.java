package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import l1j.server.DatabaseFactory;
import l1j.server.server.model.L1Buddy;
import l1j.server.server.storage.CacheD;

public class BuddyTable {
    private static final Logger _log = Logger.getLogger(BuddyTable.class.getName());
    private static class Holder { static final BuddyTable INSTANCE = new BuddyTable(); }
    private final TIntObjectMap<L1Buddy> _buddys = new TIntObjectHashMap<>(512);
    private final boolean loaded;

    public static BuddyTable getInstance() { return Holder.INSTANCE; }

    private BuddyTable() { loaded = loadBuddyList(); }

    private boolean loadBuddyList() {
        String sql = "SELECT char_id, buddy_id, buddy_name FROM character_buddys";
        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement pstm = con.prepareStatement(sql);
             ResultSet rs = pstm.executeQuery()) {
            while (rs.next()) {
                getBuddyTable(rs.getInt("char_id")).add(rs.getInt("buddy_id"), rs.getString("buddy_name"));
            }
            _log.config("loaded " + _buddys.size() + " character's buddylists");
            return true;
        } catch (SQLException e) {
            _buddys.clear();
            _log.log(Level.SEVERE, "BuddyTable load error", e);
            return false;
        }
    }

    public synchronized L1Buddy getBuddyTable(int charId) {
        L1Buddy buddy = _buddys.get(charId);
        if (buddy == null) {
            buddy = new L1Buddy(charId);
            _buddys.put(charId, buddy);
        }
        return buddy;
    }

    public static final int SUCCESS = 0, DUPLICATE = 1, FAILED = 2;
    public void addBuddy(int charId, int objId, String name) {
        addBuddy(charId, objId, name, null);
    }

    public void addBuddy(int charId, int objId, String name, IntConsumer completion) {
        if (!loaded || charId <= 0 || objId <= 0 || charId == objId || name == null || name.isEmpty()) {
            complete(completion, FAILED);
            return;
        }
        L1Buddy buddy = getBuddyTable(charId);
        if (!enqueue(buddy, () -> {
            if (buddy.containsId(objId) || buddy.containsName(name)) {
                complete(completion, DUPLICATE);
                return;
            }
            final String sql = "INSERT INTO character_buddys SET char_id = ?, buddy_id = ?, buddy_name = ?";
            try (Connection con = DatabaseFactory.getInstance().getConnection();
                 PreparedStatement pstm = con.prepareStatement(sql)) {
                pstm.setInt(1, charId);
                pstm.setInt(2, objId);
                pstm.setString(3, name);
                if (pstm.executeUpdate() != 1) throw new SQLException("Unexpected friend insert count");
            } catch (SQLException e) {
                _log.log(Level.SEVERE, "BuddyTable addBuddy error", e);
                complete(completion, FAILED);
                return;
            }
            buddy.add(objId, name);
            complete(completion, SUCCESS);
        })) complete(completion, FAILED);
    }

    public void removeBuddy(int charId, String buddyName) { removeBuddy(charId, buddyName, null); }

    public void removeBuddy(int charId, String buddyName, IntConsumer completion) {
        if (!loaded || charId <= 0 || buddyName == null || buddyName.isEmpty()) {
            complete(completion, FAILED);
            return;
        }
        L1Buddy buddy = getBuddyTable(charId);
        if (!enqueue(buddy, () -> {
            int id = buddy.getFriendDbId(buddyName);
            if (id == 0) {
                complete(completion, SUCCESS);
                return;
            }
            final String sql = "DELETE FROM character_buddys WHERE char_id = ? AND buddy_id = ?";
            try (Connection con = DatabaseFactory.getInstance().getConnection();
                 PreparedStatement pstm = con.prepareStatement(sql)) {
                pstm.setInt(1, charId);
                pstm.setInt(2, id);
                pstm.executeUpdate();
            } catch (SQLException e) {
                _log.log(Level.SEVERE, "BuddyTable removeBuddy error", e);
                complete(completion, FAILED);
                return;
            }
            buddy.remove(id);
            complete(completion, SUCCESS);
        })) complete(completion, FAILED);
    }

    private boolean enqueue(L1Buddy buddy, Runnable task) {
        synchronized (buddy) {
            buddy.beginMutation();
            try {
                CacheD.getInstance().submitDbJobOrdered(buddy.getCharId(), () -> {
                    try { task.run(); }
                    finally { buddy.endMutation(); }
                });
                return true;
            } catch (RejectedExecutionException e) {
                buddy.endMutation();
                _log.log(Level.WARNING, "Friend operation admission rejected", e);
                return false;
            }
        }
    }

    /** Read memory directly unless a preceding operation must complete first. */
    public void queryBuddy(int charId, Consumer<L1Buddy> response, Runnable failed) {
        if (!loaded) { failed.run(); return; }
        L1Buddy buddy = getBuddyTable(charId);
        boolean rejected = false;
        synchronized (buddy) {
            if (buddy.hasPendingMutation()) {
                if (enqueue(buddy, () -> response.accept(buddy))) return;
                rejected = true;
            }
        }
        if (rejected) failed.run();
        else response.accept(buddy);
    }

    private static void complete(IntConsumer callback, int result) {
        if (callback != null) callback.accept(result);
    }
}
