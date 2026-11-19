package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.DatabaseFactory;

/**
 * Weapon-specific hit/damage bonuses from the dedicated weapon_damage table.
 * JDBC runs only at startup/reload. Each attack performs one allocation-free
 * lookup on the published immutable, primitive-only hash table.
 */
public final class WeaponAddDamage {
    private static final Logger LOG = Logger.getLogger(WeaponAddDamage.class.getName());
    private static volatile WeaponAddDamage instance;
    private static final String QUERY =
            "SELECT item_id, addHit, addDamage FROM weapon_damage";

    private final int[] itemIds;
    private final long[] packedBonuses;
    private final int mask;
    private final int entryCount;
    private final int hitCount;
    private final int damageCount;

    private static long pack(int hit, int damage) {
        return ((long) hit << 32) | (damage & 0xffffffffL);
    }

    private static int slot(int itemId, int mask) {
        int hash = itemId * 0x9e3779b9;
        hash ^= hash >>> 16;
        return hash & mask;
    }

    private WeaponAddDamage() throws SQLException {
        int[] loadedIds = new int[128];
        long[] loadedBonuses = new long[128];
        int count = 0;
        int hits = 0;
        int damages = 0;
        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement stmt = con.prepareStatement(QUERY);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                final int id = rs.getInt("item_id");
                if (id <= 0) {
                    throw new SQLException("Invalid weapon_damage.item_id=" + id);
                }
                final int hit = rs.getInt("addHit");
                final int damage = rs.getInt("addDamage");
                if (count == loadedIds.length) {
                    loadedIds = Arrays.copyOf(loadedIds, count << 1);
                    loadedBonuses = Arrays.copyOf(loadedBonuses, count << 1);
                }
                loadedIds[count] = id;
                loadedBonuses[count] = pack(hit, damage);
                count++;
                if (hit != 0) hits++;
                if (damage != 0) damages++;
            }
        }

        int capacity = 2;
        while (capacity <= count * 2) {
            capacity <<= 1;
        }
        final int[] ids = new int[capacity];
        final long[] bonuses = new long[capacity];
        final int hashMask = capacity - 1;
        for (int n = 0; n < count; n++) {
            final int id = loadedIds[n];
            int index = slot(id, hashMask);
            while (ids[index] != 0) {
                if (ids[index] == id) {
                    throw new SQLException("Duplicate item_id in weapon_damage: " + id);
                }
                index = (index + 1) & hashMask;
            }
            ids[index] = id;
            bonuses[index] = loadedBonuses[n];
        }
        itemIds = ids;
        packedBonuses = bonuses;
        mask = hashMask;
        entryCount = count;
        hitCount = hits;
        damageCount = damages;
        LOG.info("weapon_damage loaded: " + count + " weapons, hit bonuses="
                + hits + ", damage bonuses=" + damages);
    }

    public static WeaponAddDamage getInstance() {
        WeaponAddDamage current = instance;
        if (current == null) {
            synchronized (WeaponAddDamage.class) {
                current = instance;
                if (current == null) {
                    try {
                        current = new WeaponAddDamage();
                    } catch (SQLException e) {
                        throw new IllegalStateException(
                                "Cannot load weapon_damage; run sql/weapon_damage.sql on the game DB", e);
                    }
                    instance = current;
                }
            }
        }
        return current;
    }

    /** All-or-nothing reload: readers see the old or the new table, never partial data. */
    public static boolean reload() {
        try {
            final WeaponAddDamage next = new WeaponAddDamage();
            instance = next;
            return true;
        } catch (SQLException | RuntimeException e) {
            LOG.log(Level.SEVERE, "weapon_damage reload failed; previous snapshot retained", e);
            return false;
        }
    }

    /** High 32 bits = signed hit bonus, low 32 bits = signed damage bonus. */
    public long getBonusPacked(int itemId) {
        if (itemId <= 0) {
            return 0L;
        }
        int index = slot(itemId, mask);
        while (true) {
            final int id = itemIds[index];
            if (id == itemId) {
                return packedBonuses[index];
            }
            if (id == 0) {
                return 0L;
            }
            index = (index + 1) & mask;
        }
    }

    public int getWeaponAddHit(int itemId) {
        return (int) (getBonusPacked(itemId) >> 32);
    }

    public double getWeaponAddDamage(int itemId) {
        return (int) getBonusPacked(itemId);
    }

    public int getEntryCount() {
        return entryCount;
    }

    public int getNonZeroHitCount() {
        return hitCount;
    }

    public int getNonZeroDamageCount() {
        return damageCount;
    }
}
