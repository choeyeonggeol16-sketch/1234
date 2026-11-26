package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.DatabaseFactory;
import l1j.server.server.templates.L1Item;

/**
 * Weapon-specific hit/damage bonuses from the installed weapon bonus schema.
 * JDBC runs only at startup/reload. Each attack performs one allocation-free
 * lookup on the published immutable, primitive-only hash table.
 */
public final class WeaponAddDamage {
    private static final Logger LOG = Logger.getLogger(WeaponAddDamage.class.getName());
    private static volatile WeaponAddDamage instance;
    private static final String QUERY =
            "SELECT item_id, addHit, addDamage, 1 AS damage_variants FROM weapon_damage";
    private static final String LEGACY_QUERY =
            "SELECT item_id, 0 AS addHit, COALESCE(MAX(NULLIF(addDamege, 0)), 0) AS addDamage, "
            + "COUNT(DISTINCT NULLIF(addDamege, 0)) AS damage_variants FROM weapon_damege GROUP BY item_id";

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
             PreparedStatement stmt = con.prepareStatement(queryForSchema(con));
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                final int id = rs.getInt("item_id");
                if (id <= 0) {
                    throw new SQLException("Invalid weapon bonus item_id=" + id);
                }
                if (rs.getInt("damage_variants") > 1)
                    throw new SQLException("Conflicting legacy weapon damage bonuses for item " + id);
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
        LOG.info("Weapon bonuses loaded: " + count + " weapons, hit bonuses="
                + hits + ", damage bonuses=" + damages);
    }

    private static String queryForSchema(Connection con) throws SQLException {
        final String sql = "SELECT 1 FROM information_schema.TABLES "
                + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='weapon_damage'";
        try (PreparedStatement stmt = con.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            // The legacy key includes the name, so an item may have both a zero
            // placeholder and a configured bonus. Coalesce those by item ID.
            return rs.next() ? QUERY : LEGACY_QUERY;
        }
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
                        throw new IllegalStateException("Cannot load weapon bonus table", e);
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

    /** Total hit and flat damage before per-instance blessing and enchant. */
    public long getTemplateStatsPacked(L1Item template) {
        final long extra = getBonusPacked(template.getItemId());
        return pack(template.getHitModifier() + (int) (extra >> 32),
                template.getDmgModifier() + (int) extra);
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
