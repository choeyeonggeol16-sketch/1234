package l1j.server.server.model;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import l1j.server.server.model.Instance.L1PcInstance;

/** CFriends role: one character's committed friend relationships. */
public class L1Buddy {
    private final int _charId;
    private final HashMap<Integer, String> _buddys = new HashMap<>();
    private TreeMap<String, Integer> names;
    private int pending;

    public L1Buddy(int charId) { _charId = charId; }
    public int getCharId() { return _charId; }

    public synchronized boolean add(int objId, String name) {
        if (objId <= 0 || name == null || _buddys.containsKey(objId) || containsName(name))
            return false;
        if (names == null) names = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        _buddys.put(objId, name);
        names.put(name, objId);
        return true;
    }

    public synchronized boolean remove(int objId) {
        String name = _buddys.remove(objId);
        if (name == null) return false;
        names.remove(name);
        if (names.isEmpty()) names = null;
        return true;
    }

    public synchronized boolean remove(String name) {
        return remove(getFriendDbId(name));
    }

    public synchronized int getFriendDbId(String name) {
        if (name == null || names == null) return 0;
        Integer id = names.get(name);
        return id == null ? 0 : id;
    }

    public synchronized boolean containsId(int objId) { return _buddys.containsKey(objId); }
    public synchronized boolean containsName(String name) { return getFriendDbId(name) != 0; }
    public synchronized int size() { return _buddys.size(); }

    /** Compatibility accessor; no mutable collection escapes the friend-list lock. */
    public synchronized HashMap<Integer, String> getBuddy() { return new HashMap<>(_buddys); }

    public synchronized String getName() {
        String result = "";
        for (String name : _buddys.values()) result = name;
        return result;
    }

    public record FriendSnapshot(int[] ids, String[] names) {}
    private static final FriendSnapshot EMPTY = new FriendSnapshot(new int[0], new String[0]);

    public synchronized FriendSnapshot snapshot() {
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
    public static L1PcInstance findOnlineFriend(int id) {
        L1World world = L1World.getInstance();
        if (world.findObject(id) instanceof L1PcInstance pc && world.getPlayer(pc.getName()) == pc)
            return pc;
        return null;
    }

    public String getOnlineBuddyListString() {
        FriendSnapshot friends = snapshot();
        if (friends.ids().length == 0) return "";
        StringBuilder result = new StringBuilder();
        for (int id : friends.ids()) {
            L1PcInstance pc = findOnlineFriend(id);
            if (pc != null) result.append(pc.getName()).append(' ');
        }
        return result.toString();
    }

    // Admission and query ordering use this same monitor. No JDBC runs under it.
    public synchronized void beginMutation() { ++pending; }
    public synchronized void endMutation() { --pending; }
    public synchronized boolean hasPendingMutation() { return pending != 0; }
}
