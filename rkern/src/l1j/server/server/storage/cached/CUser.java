package l1j.server.server.storage.cached;

import l1j.server.server.storage.mysql.MySqlCharacterStorage.CharacterSnapshot;

/** CacheD-side character persistence ownership, distinct from L1PcInstance. */
public final class CUser extends CPendingState {
    public final String name;
    public int objectId;
    public long sessionEpoch;
    public int karmaReceiptRevision;
    public String accountName;
    public CharacterSnapshot latest;
    // Keep only the last SQL-committed snapshot while this owner is active.
    // It lets unchanged periodic saves skip JDBC entirely (no hash collisions).
    public CharacterSnapshot persisted;

    public CUser(String name) { this.name = name; }

    /** Called under the CacheD lock. */
    public void bind(String login, int id) {
        if (login == null || login.isEmpty() || id <= 0)
            throw new IllegalArgumentException("Character owner");
        if (objectId != 0 && objectId != id)
            throw new IllegalStateException("Character ID changed: " + name);
        if (accountName != null && !accountName.equals(login))
            throw new IllegalStateException("Character account changed: " + name);
        accountName = login;
        objectId = id;
    }

    /** Called under the CacheD lock. */
    public void capture(CharacterSnapshot snapshot) {
        if (objectId != 0 && objectId != snapshot.objectId)
            throw new IllegalStateException("Character ID changed: " + name);
        objectId = snapshot.objectId;
        latest = snapshot;
        version++;
    }
    public static void persist(CharacterSnapshot snapshot) throws java.sql.SQLException {
        snapshot.write();
    }
}
