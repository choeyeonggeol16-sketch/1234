package l1j.server.server.model;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import l1j.server.server.model.Instance.L1PcInstance;

/** CFriends role: one character's committed friend relationships. */
public class CFriends {
    private final int _charId;
    private final HashMap<Integer, String> _buddys = new HashMap<>();
    private TreeMap<String, Integer> names;
    private int pending;

    public CFriends(int charId) { _charId = charId; }
    public int GetCharId() { return _charId; }

    public synchronized boolean AddFriend(int objId, String name) {
        if (objId <= 0 || name == null || _buddys.containsKey(objId) || HasFriend(name))
            return false;
        if (names == null) names = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        _buddys.put(objId, name);
        names.put(name, objId);
        return true;
    }

    public synchronized boolean RemoveFriend(int objId) {
        String name = _buddys.remove(objId);
        if (name == null) return false;
        names.remove(name);
        if (names.isEmpty()) names = null;
        return true;
    }

    public synchronized boolean RemoveFriend(String name) {
        return RemoveFriend(GetFriendDbId(name));
    }

    public synchronized int GetFriendDbId(String name) {
        if (name == null || names == null) return 0;
        Integer id = names.get(name);
        return id == null ? 0 : id;
    }

    public synchronized boolean HasFriend(int objId) { return _buddys.containsKey(objId); }
    public synchronized boolean HasFriend(String name) { return GetFriendDbId(name) != 0; }
    public synchronized int GetFriendCount() { return _buddys.size(); }

    /** Compatibility accessor; no mutable collection escapes the friend-list lock. */
    public synchronized HashMap<Integer, String> GetFriendMap() { return new HashMap<>(_buddys); }

    public synchronized String GetLastFriendName() {
        String result = "";
        for (String name : _buddys.values()) result = name;
        return result;
    }

    public record FriendSnapshot(int[] ids, String[] names) {}
    private static final FriendSnapshot EMPTY = new FriendSnapshot(new int[0], new String[0]);

    public synchronized FriendSnapshot Snapshot() {
        int n = _buddys.size();
        if (n == 0) return EMPTY;
        int[] ids = new int[n];
        String[] values = new String[n];
        int i = 0;
        for (Map.Entry<Integer, String> entry : _buddys.entrySet()) {
            ids[i] = entry.getKey();
            values[i++] = entry.getValue();
        }
        return new FriendSnapshot(ids, values);
    }

    /** Resolve by object ID, then confirm membership in the existing online index. */
    public static L1PcInstance FindOnlineFriend(int id) {
        L1World world = L1World.getInstance();
        if (world.findObject(id) instanceof L1PcInstance pc && world.getPlayer(pc.getName()) == pc)
            return pc;
        return null;
    }

    public String MakeOnlineFriendList() {
        FriendSnapshot friends = Snapshot();
        if (friends.ids().length == 0) return "";
        StringBuilder result = new StringBuilder();
        for (int id : friends.ids()) {
            L1PcInstance pc = FindOnlineFriend(id);
            if (pc != null) result.append(pc.getName()).append(' ');
        }
        return result.toString();
    }

    // Admission and query ordering use this same monitor. No JDBC runs under it.
    public synchronized void BeginMutation() { ++pending; }
    public synchronized void EndMutation() { --pending; }
    public synchronized boolean HasPendingMutation() { return pending != 0; }
}
