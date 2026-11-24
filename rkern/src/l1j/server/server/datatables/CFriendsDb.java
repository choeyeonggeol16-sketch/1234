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
import l1j.server.server.model.CFriends;
import l1j.server.server.storage.CacheD;

public class CFriendsDb {
    private static final Logger _log = Logger.getLogger(CFriendsDb.class.getName());
    private static class Holder { static final CFriendsDb INSTANCE = new CFriendsDb(); }
    private final TIntObjectMap<CFriends> _buddys = new TIntObjectHashMap<>(512);
    private final boolean loaded;

    public static CFriendsDb getInstance() { return Holder.INSTANCE; }

    private CFriendsDb() { loaded = LoadFriends(); }

    private boolean LoadFriends() {
        String sql = "SELECT char_id, buddy_id, buddy_name FROM character_buddys";
        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement pstm = con.prepareStatement(sql);
             ResultSet rs = pstm.executeQuery()) {
            while (rs.next()) {
                GetFriends(rs.getInt("char_id")).AddFriend(rs.getInt("buddy_id"), rs.getString("buddy_name"));
            }
            _log.config("loaded " + _buddys.size() + " character's buddylists");
            return true;
        } catch (SQLException e) {
            _buddys.clear();
            _log.log(Level.SEVERE, "CFriendsDb load error", e);
            return false;
        }
    }

    public synchronized CFriends GetFriends(int charId) {
        CFriends buddy = _buddys.get(charId);
        if (buddy == null) {
            buddy = new CFriends(charId);
            _buddys.put(charId, buddy);
        }
        return buddy;
    }

    public static final int SUCCESS = 0, DUPLICATE = 1, FAILED = 2;
    public void EstablishFriendship(int charId, int objId, String name) {
        EstablishFriendship(charId, objId, name, null);
    }

    public void EstablishFriendship(int charId, int objId, String name, IntConsumer completion) {
        if (!loaded || charId <= 0 || objId <= 0 || charId == objId || name == null || name.isEmpty()) {
            complete(completion, FAILED);
            return;
        }
        CFriends buddy = GetFriends(charId);
        if (!enqueue(buddy, () -> {
            if (buddy.HasFriend(objId) || buddy.HasFriend(name)) {
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
                _log.log(Level.SEVERE, "CFriendsDb EstablishFriendship error", e);
                complete(completion, FAILED);
                return;
            }
            buddy.AddFriend(objId, name);
            complete(completion, SUCCESS);
        })) complete(completion, FAILED);
    }

    public void BreakFriendship(int charId, String buddyName) { BreakFriendship(charId, buddyName, null); }

    public void BreakFriendship(int charId, String buddyName, IntConsumer completion) {
        if (!loaded || charId <= 0 || buddyName == null || buddyName.isEmpty()) {
            complete(completion, FAILED);
            return;
        }
        CFriends buddy = GetFriends(charId);
        if (!enqueue(buddy, () -> {
            int id = buddy.GetFriendDbId(buddyName);
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
                _log.log(Level.SEVERE, "CFriendsDb BreakFriendship error", e);
                complete(completion, FAILED);
                return;
            }
            buddy.RemoveFriend(id);
            complete(completion, SUCCESS);
        })) complete(completion, FAILED);
    }

    private boolean enqueue(CFriends buddy, Runnable task) {
        synchronized (buddy) {
            buddy.BeginMutation();
            try {
                CacheD.getInstance().submitDbJobOrdered(buddy.GetCharId(), () -> {
                    try { task.run(); }
                    finally { buddy.EndMutation(); }
                });
                return true;
            } catch (RejectedExecutionException e) {
                buddy.EndMutation();
                _log.log(Level.WARNING, "Friend operation admission rejected", e);
                return false;
            }
        }
    }

    /** Read memory directly unless a preceding operation must complete first. */
    public void MakeFriendList(int charId, Consumer<CFriends> response, Runnable failed) {
        if (!loaded) { failed.run(); return; }
        CFriends buddy = GetFriends(charId);
        boolean rejected = false;
        synchronized (buddy) {
            if (buddy.HasPendingMutation()) {
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
