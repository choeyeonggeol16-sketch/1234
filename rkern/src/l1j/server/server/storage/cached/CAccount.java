package l1j.server.server.storage.cached;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import l1j.server.DatabaseFactory;

/** Account-side cached persistence state; not a second copy of the game Account. */
public final class CAccount extends CPendingState {
    public static final int LAST_ACTIVE = 1;
    public static final int LAST_LOGOUT = 1 << 1;
    public static final int BLESS_OF_AIN = 1 << 2;
    public static final int GRANG_KIN = 1 << 3;

    public final String login;
    public String ip;
    public long lastActive;
    public long lastLogout;
    public int blessOfAin;
    public int grangKin;
    public int dirty;
    // Only CacheD-owned lightweight CUser records, never L1PcInstance copies.
    // Mutated under the existing CacheD lock.
    private final Int2ObjectOpenHashMap<CUser> characters = new Int2ObjectOpenHashMap<>();

    public void setCharacter(CUser user) {
        if (user == null || user.objectId <= 0 || !login.equals(user.accountName))
            throw new IllegalArgumentException("Invalid account character");
        CUser old = characters.get(user.objectId);
        if (old != null && old != user)
            throw new IllegalStateException("Character ID already cached: " + user.objectId);
        characters.put(user.objectId, user);
    }

    public boolean deleteCharacter(int objectId, CUser expected) {
        if (expected == null || characters.get(objectId) != expected) return false;
        characters.remove(objectId);
        return true;
    }

    public CUser character(int objectId) { return characters.get(objectId); }
    public boolean hasCharacters() { return !characters.isEmpty(); }
    public int characterCount() { return characters.size(); }

    public CAccount(String login) { this.login = login; }

    /** Called under the CacheD lock. */
    public void changed(int mask) {
        dirty |= mask;
        version++;
    }

    /** Immutable DB write image; later account updates remain in the owner. */
    public static final class Snapshot {
        public final CAccount owner;
        public final int mask;
        public final long version;
        public final String ip;
        public final long lastActive;
        public final long lastLogout;
        public final int blessOfAin;
        public final int grangKin;

        public Snapshot(CAccount owner) {
            this.owner = owner;
            this.mask = owner.dirty;
            this.version = owner.version;
            this.ip = owner.ip;
            this.lastActive = owner.lastActive;
            this.lastLogout = owner.lastLogout;
            this.blessOfAin = owner.blessOfAin;
            this.grangKin = owner.grangKin;
        }
    }

    public static void persist(Snapshot snapshot) throws SQLException {
        int mask = snapshot.mask;
        if (mask == 0) return;

        StringBuilder sql = new StringBuilder(160);
        sql.append("UPDATE accounts SET ");
        if ((mask & CAccount.LAST_ACTIVE) != 0) {
            sql.append("lastactive=?,ip=?,");
        }
        if ((mask & CAccount.LAST_LOGOUT) != 0) {
            sql.append("last_log_out=?,");
        }
        if ((mask & CAccount.BLESS_OF_AIN) != 0) {
            sql.append("bless_of_ain=?,");
        }
        if ((mask & CAccount.GRANG_KIN) != 0) {
            sql.append("GrangKinAngerTime=?,");
        }
        sql.setLength(sql.length() - 1);
        sql.append(" WHERE login=?");

        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {
            int idx = 1;
            if ((mask & CAccount.LAST_ACTIVE) != 0) {
                ps.setTimestamp(idx++, new Timestamp(snapshot.lastActive));
                ps.setString(idx++, snapshot.ip);
            }
            if ((mask & CAccount.LAST_LOGOUT) != 0) {
                ps.setTimestamp(idx++, new Timestamp(snapshot.lastLogout));
            }
            if ((mask & CAccount.BLESS_OF_AIN) != 0) {
                ps.setInt(idx++, snapshot.blessOfAin);
            }
            if ((mask & CAccount.GRANG_KIN) != 0) {
                ps.setInt(idx++, snapshot.grangKin);
            }
            ps.setString(idx, snapshot.owner.login);
            ps.executeUpdate();
        }
    }

}
