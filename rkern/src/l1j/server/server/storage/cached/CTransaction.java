package l1j.server.server.storage.cached;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.UUID;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.DatabaseFactory;
import l1j.server.server.storage.mysql.MySqlCharactersItemStorage;
import l1j.server.server.storage.mysql.MySqlCharactersItemStorage.ItemSnapshot;

/**
 * SQL settlement half of a transaction. Game state remains owned by the game
 * executor, and CacheD schedules this on its existing ordered DB workers.
 */
public final class CTransaction {
    private static final Logger LOG = Logger.getLogger(CTransaction.class.getName());

    private CTransaction() {}

    /**
     * The driver cannot prove whether COMMIT took effect (or ROLLBACK succeeded).
     * Never replay such a transaction without a durable transaction receipt.
     */
    public static final class OutcomeUnknownException extends SQLException {
        private static final long serialVersionUID = 1L;
        public OutcomeUnknownException(String message, Throwable cause) {
            super(message, "08007", cause);
        }
    }

    @FunctionalInterface
    public interface Mutation {
        void apply(Connection connection) throws SQLException;
    }

    /**
     * Exactly one JDBC transaction for both character_items and warehouse
     * tables. The caller has already snapshotted each source and destination.
     * COMMIT ambiguity is deliberately NOT retried by CacheD's FIFO workers.
     */
    // A receipt is committed with the item mutations, never in a second SQL transaction.
    // The schema is deployed separately; a missing receipt table fails CLOSED.
    private static boolean hasReceipt(Connection con, String txId, String kind,
                                      int firstOwner, int secondOwner, boolean lock)
            throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT kind,owner_a,owner_b FROM cached_transaction_receipts WHERE tx_id=?"
                        + (lock ? " FOR UPDATE" : ""))) {
            ps.setString(1, txId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return false;
                if (!kind.equals(rs.getString(1)) || firstOwner != rs.getInt(2)
                        || secondOwner != rs.getInt(3)) {
                    throw new SQLException("CacheD receipt identity collision: " + txId);
                }
                return true;
            }
        }
    }

    private static void insertReceipt(Connection con, String txId, String kind,
                                      int firstOwner, int secondOwner) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO cached_transaction_receipts (tx_id,kind,owner_a,owner_b) VALUES (?,?,?,?)")) {
            ps.setString(1, txId);
            ps.setString(2, kind);
            ps.setInt(3, firstOwner);
            ps.setInt(4, secondOwner);
            if (ps.executeUpdate() != 1)
                throw new SQLException("Transaction receipt was not inserted: " + txId);
        }
    }

    /** An ambiguous COMMIT can be ACKed only when its durable receipt exists.
     * An absent receipt is NOT proof of rollback if the first connection is in doubt.
     */
    private static boolean resolveConfirmedCommit(String txId, String kind,
                                                   int firstOwner, int secondOwner,
                                                   OutcomeUnknownException uncertain) {
        boolean confirmed = false;
        try (Connection verify = DatabaseFactory.getInstance().getConnection()) {
            confirmed = hasReceipt(verify, txId, kind, firstOwner, secondOwner, false);
        } catch (SQLException | RuntimeException verificationFailure) {
            if (confirmed) {
                // Closing the verification connection cannot undo a receipt
                // that was already read from committed storage.
                LOG.log(Level.WARNING, "Confirmed receipt connection close failed", verificationFailure);
            } else uncertain.addSuppressed(verificationFailure);
        }
        if (confirmed) {
            LOG.log(Level.WARNING, "Recovered confirmed CacheD COMMIT using receipt {0}", txId);
            return true;
        }
        LOG.log(Level.SEVERE, "CacheD transaction result unresolved, owner frozen; receipt=" + txId,
                uncertain);
        return false;
    }

    private static final String[] TRANSACTION_TABLES = {
            "cached_transaction_receipts", "character_items", "character_warehouse",
            "clan_warehouse", "character_elf_warehouse",
            "character_special_warehouse", "character_supplementary_service",
            "characters", "character_quests", "character_level_gifts"
    };

    /** Verify on a DB worker, never on a game/CIO owner thread. A missing table
     * or a nontransactional storage engine would silently defeat atomic COMMIT.
     */
    public static void verifySchema() throws SQLException {
        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT TABLE_NAME, ENGINE FROM information_schema.TABLES "
                     + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN (?,?,?,?,?,?,?,?,?,?)")) {
            for (int i = 0; i < TRANSACTION_TABLES.length; i++)
                ps.setString(i + 1, TRANSACTION_TABLES[i]);
            boolean[] found = new boolean[TRANSACTION_TABLES.length];
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    final String name = rs.getString(1);
                    for (int i = 0; i < TRANSACTION_TABLES.length; i++) {
                        if (TRANSACTION_TABLES[i].equals(name)) {
                            if (!"InnoDB".equalsIgnoreCase(rs.getString(2)))
                                throw new SQLException("CacheD atomic storage requires InnoDB: " + name);
                            found[i] = true;
                        }
                    }
                }
            }
            for (int i = 0; i < found.length; i++)
                if (!found[i]) throw new SQLException("Missing atomic CacheD table: " + TRANSACTION_TABLES[i]);
            // An InnoDB table alone is not enough: without these UNIQUE keys,
            // a duplicate receipt or quest flag can defeat idempotent replay.
            requireUniqueIndex(con, "cached_transaction_receipts", "tx_id", null);
            requireUniqueIndex(con, "character_quests", "char_id", "quest_id");
            requireUniqueIndex(con, "character_level_gifts", "char_id", "gift_level");
        }
    }

    /** Require a complete, non-prefix UNIQUE index, not merely a leading
     * column of a larger composite key. Called once during CacheD startup.
     * No game-thread SQL or hot-path schema queries are introduced.
     */
    private static void requireUniqueIndex(Connection con, String table,
                                           String firstColumn, String secondColumn) throws SQLException {
        final boolean pair = secondColumn != null;
        final String query = "SELECT INDEX_NAME FROM information_schema.STATISTICS "
                + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND NON_UNIQUE=0 "
                + "GROUP BY INDEX_NAME HAVING COUNT(*)=" + (pair ? "2" : "1")
                + " AND SUM(CASE WHEN SEQ_IN_INDEX=1 AND COLUMN_NAME=? AND SUB_PART IS NULL "
                + "THEN 1 ELSE 0 END)=1"
                + (pair ? " AND SUM(CASE WHEN SEQ_IN_INDEX=2 AND COLUMN_NAME=? "
                        + "AND SUB_PART IS NULL THEN 1 ELSE 0 END)=1" : "")
                + " LIMIT 1";
        try (PreparedStatement ps = con.prepareStatement(query)) {
            ps.setString(1, table);
            ps.setString(2, firstColumn);
            if (pair) ps.setString(3, secondColumn);
            try (ResultSet rows = ps.executeQuery()) {
                if (!rows.next())
                    throw new SQLException("Missing full UNIQUE index for atomic replay: "
                            + table + " (" + firstColumn
                            + (pair ? "," + secondColumn : "") + ")");
            }
        }
    }

    public static void persistWarehouseMove(Mutation[] operations) throws SQLException {
        persistWarehouseMove(UUID.randomUUID().toString(), 0, 0, operations);
    }

    public static void persistWarehouseMove(String txId, int firstOwner, int secondOwner,
                                            Mutation[] operations) throws SQLException {
        if (operations == null || operations.length < 2)
            throw new SQLException("Incomplete warehouse movement");
        persistAtomic(txId, "WAREHOUSE", firstOwner, secondOwner, operations);
    }

    /** Same receipt engine for inventory plus authoritative auxiliary rows. */
    public static void persistEconomy(String txId, int firstOwner, int secondOwner,
                                      Mutation[] operations) throws SQLException {
        if (operations == null || operations.length == 0)
            throw new SQLException("Empty economy transaction");
        Mutation[] guarded = new Mutation[operations.length + 1];
        guarded[0] = con -> requireTransactionalTables(con, "cached_transaction_receipts");
        System.arraycopy(operations, 0, guarded, 1, operations.length);
        persistAtomic(txId, "ECONOMY", firstOwner, secondOwner, guarded);
    }

    public static void requireTransactionalTables(Connection con, String... tables) throws SQLException {
        for (String table : tables) {
            if (table == null || !table.matches("[A-Za-z_][A-Za-z0-9_]*"))
                throw new SQLException("Invalid transaction table name");
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?")) {
                ps.setString(1, table);
                try (ResultSet rows = ps.executeQuery()) {
                    if (!rows.next() || !"InnoDB".equalsIgnoreCase(rows.getString(1)))
                        throw new SQLException("Atomic economy requires InnoDB table: " + table);
                }
            }
        }
    }

    /** Compare the complete persisted source image under its row lock. */
    public static void verifyItemPreimage(Connection con, ItemSnapshot v) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT * FROM character_items WHERE id=? FOR UPDATE")) {
            ps.setInt(1, v.id);
            try (ResultSet r = ps.executeQuery()) {
                // Keep the strict owner/type/count check: a mismatch here is
                // never an invitation to replay a debit or overwrite SQL.
                if (!r.next()) throw preimageFailure(v.id, "missing row", "absent", "present");
                requirePreimageInt(v.id, "char_id", r.getInt("char_id"), v.ownerId);
                requirePreimageInt(v.id, "item_id", r.getInt("item_id"), v.itemId);
                requirePreimageInt(v.id, "count", r.getInt("count"), v.count);
                requirePreimageInt(v.id, "is_equipped", r.getInt("is_equipped"), v.equipped);
                requirePreimageInt(v.id, "enchantlvl", r.getInt("enchantlvl"), v.enchant);
                requirePreimageInt(v.id, "is_id", r.getInt("is_id"), v.identified);
                requirePreimageInt(v.id, "durability", r.getInt("durability"), v.durability);
                requirePreimageInt(v.id, "charge_count", r.getInt("charge_count"), v.charge);
                requirePreimageInt(v.id, "remaining_time", r.getInt("remaining_time"), v.remaining);
                requirePreimageInt(v.id, "bless", r.getInt("bless"), v.bless);
                requirePreimageInt(v.id, "attr_enchantlvl", r.getInt("attr_enchantlvl"), v.attr);
                requirePreimageInt(v.id, "special_enchant", r.getInt("special_enchant"), v.special);
                requirePreimageInt(v.id, "package", r.getInt("package"), v.pack);
                requirePreimageInt(v.id, "bless_level", r.getInt("bless_level"), v.blessLevel);
                requirePreimageInt(v.id, "bless_level2", r.getInt("bless_level2"), v.blessLevel2);
                requirePreimageInt(v.id, "item_level", r.getInt("item_level"), v.itemLevel);
                requirePreimageObject(v.id, "last_used", r.getTimestamp("last_used"), v.lastUsed);
                requirePreimageObject(v.id, "end_time", r.getTimestamp("end_time"), v.endTime);
                requirePreimageObject(v.id, "Hotel_Town", r.getString("Hotel_Town"), v.hotelTown);
            }
        }
    }

    private static SQLException preimageFailure(int id, String field, Object persisted, Object expected) {
        return new SQLException("Economy item preimage changed: " + id + " field=" + field
                + " db=" + persisted + " memory=" + expected);
    }

    private static void requirePreimageInt(int id, String field, int persisted, int expected)
            throws SQLException {
        if (persisted != expected) throw preimageFailure(id, field, persisted, expected);
    }

    private static void requirePreimageObject(int id, String field, Object persisted, Object expected)
            throws SQLException {
        if (!java.util.Objects.equals(persisted, expected))
            throw preimageFailure(id, field, persisted, expected);
    }

    private static void persistAtomic(String txId, String kind, int firstOwner, int secondOwner,
                                      Mutation[] operations) throws SQLException {
        if (txId == null || txId.length() != 36)
            throw new SQLException("Invalid durable transaction ID");
        boolean committed = false;
        boolean commitAttempted = false;
        boolean resetSafe = false;
        try (Connection con = DatabaseFactory.getInstance().getConnection()) {
            final boolean autoCommit = con.getAutoCommit();
            con.setAutoCommit(false);
            try {
                if (hasReceipt(con, txId, kind, firstOwner, secondOwner, true)) {
                    // The matching durable receipt proves the original commit.
                    // The current connection owns only the receipt read lock.
                    committed = true;
                    try {
                        con.rollback();
                        resetSafe = true;
                    } catch (SQLException | RuntimeException cleanup) {
                        // Do not restore auto-commit after a failed rollback.
                        // Connection close/pool cleanup owns this read-only scope.
                        LOG.log(Level.WARNING, "Confirmed receipt rollback cleanup failed", cleanup);
                    }
                    return;
                }
                for (Mutation operation : operations) operation.apply(con);
                insertReceipt(con, txId, kind, firstOwner, secondOwner);
                commitAttempted = true;
                con.commit();
                committed = true;
                resetSafe = true;
            } catch (SQLException | RuntimeException | Error failure) {
                if (commitAttempted)
                    throw new OutcomeUnknownException("COMMIT response uncertain receipt=" + txId, failure);
                try { con.rollback(); }
                catch (SQLException | RuntimeException | Error rollback) {
                    failure.addSuppressed(rollback);
                    throw new OutcomeUnknownException("ROLLBACK uncertain receipt=" + txId, failure);
                }
                throw failure;
            } finally {
                if (resetSafe) try { con.setAutoCommit(autoCommit); }
                catch (SQLException | RuntimeException reset) {
                    LOG.log(Level.WARNING, "Committed connection reset failed", reset);
                }
            }
        } catch (OutcomeUnknownException uncertain) {
            // ROLLBACK failures are never presumed safe. An existing receipt
            // still proves COMMIT, but a missing one keeps the owner quarantined.
            if (resolveConfirmedCommit(txId, kind, firstOwner, secondOwner, uncertain)) return;
            throw uncertain;
        } catch (SQLException | RuntimeException failure) {
            if (!committed) throw failure;
            LOG.log(Level.WARNING, "Committed connection close failed", failure);
        }
    }

    public static void writeCharacterMoveRow(Connection con, ItemSnapshot snapshot,
                                             boolean insert, int previousCount) throws SQLException {
        if (!insert && previousCount <= 0)
            throw new SQLException("Missing character move original count: " + snapshot.id);
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT item_id,count FROM character_items WHERE id=? AND char_id=? FOR UPDATE")) {
            ps.setInt(1, snapshot.id);
            ps.setInt(2, snapshot.ownerId);
            try (ResultSet rows = ps.executeQuery()) {
                boolean exists = rows.next();
                if (insert ? exists : !exists)
                    throw new SQLException("Character move unexpected row existence: " + snapshot.id);
                if (exists && rows.getInt(1) != snapshot.itemId)
                    throw new SQLException("Character move item type mismatch: " + snapshot.id);
                if (exists && rows.getInt(2) != previousCount)
                    throw new SQLException("Character move stale quantity: " + snapshot.id);
            }
        }
        // A row with this ID but a different owner must never be overwritten.
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT char_id FROM character_items WHERE id=? FOR UPDATE")) {
            ps.setInt(1, snapshot.id);
            try (ResultSet rows = ps.executeQuery()) {
                if (rows.next() && rows.getInt(1) != snapshot.ownerId)
                    throw new SQLException("Character move owner conflict: " + snapshot.id);
            }
        }
        if (insert)
            MySqlCharactersItemStorage.insertTradeSnapshot(con, snapshot);
        else
            MySqlCharactersItemStorage.updateTradeSnapshot(con, snapshot, snapshot.ownerId);
    }

    public static void deleteCharacterMoveRow(Connection con, int id, int ownerId,
                                              int itemId, int previousCount) throws SQLException {
        if (previousCount <= 0) throw new SQLException("Missing character source quantity: " + id);
        try (PreparedStatement ps = con.prepareStatement(
                "DELETE FROM character_items WHERE id=? AND char_id=? AND item_id=? AND count=?")) {
            ps.setInt(1, id);
            ps.setInt(2, ownerId);
            ps.setInt(3, itemId);
            ps.setInt(4, previousCount);
            if (ps.executeUpdate() != 1)
                throw new SQLException("Character move source quantity/owner changed: " + id);
        }
    }

    // A trade is allowed to modify rows owned by its TWO participants only.
    // Lock every existing item row before any UPSERT/DELETE: an upsert must
    // never silently steal a row belonging to a third character.
    private static int[] lockAndVerify(Connection con, ItemSnapshot[] writes,
                                      int[] deletes, int firstOwner, int secondOwner) throws SQLException {
        if (firstOwner <= 0 || secondOwner <= 0 || firstOwner == secondOwner)
            throw new SQLException("Invalid trade participants");
        final int[] ids = new int[writes.length + deletes.length];
        int size = 0;
        for (ItemSnapshot snapshot : writes) {
            if (snapshot == null || snapshot.id <= 0 || snapshot.itemId <= 0
                    || snapshot.count <= 0 || (snapshot.ownerId != firstOwner && snapshot.ownerId != secondOwner))
                throw new SQLException("Invalid trade item write");
            ids[size++] = snapshot.id;
        }
        for (int id : deletes) {
            if (id <= 0) throw new SQLException("Invalid trade item deletion");
            ids[size++] = id;
        }
        final int[] previous = new int[writes.length + deletes.length];
        if (size == 0) return previous;
        Arrays.sort(ids);
        for (int i = 1; i < size; i++) {
            if (ids[i] == ids[i - 1])
                throw new SQLException("Duplicate trade item operation: " + ids[i]);
        }
        StringBuilder sql = new StringBuilder(96 + size * 2);
        sql.append("SELECT id, char_id, item_id FROM character_items WHERE id IN (");
        for (int i = 0; i < size; i++) {
            if (i != 0) sql.append(',');
            sql.append('?');
        }
        sql.append(") ORDER BY id FOR UPDATE");
        try (PreparedStatement ps = con.prepareStatement(sql.toString())) {
            for (int i = 0; i < size; i++) ps.setInt(i + 1, ids[i]);
            try (ResultSet rows = ps.executeQuery()) {
                while (rows.next()) {
                    int id = rows.getInt("id");
                    int owner = rows.getInt("char_id");
                    if (Arrays.binarySearch(ids, id) < 0 || (owner != firstOwner && owner != secondOwner))
                        throw new SQLException("Trade item ownership conflict: item=" + id + " DB owner=" + owner);
                    for (int j = 0; j < writes.length; j++) {
                        ItemSnapshot snapshot = writes[j];
                        if (snapshot.id == id) {
                            if (snapshot.itemId != rows.getInt("item_id"))
                                throw new SQLException("Trade item identity conflict: item=" + id);
                            previous[j] = owner;
                        }
                    }
                    for (int j = 0; j < deletes.length; j++) {
                        if (deletes[j] == id) previous[writes.length + j] = owner;
                    }
                }
            }
        }
        // A split-stack escrow item can be created and merged entirely in
        // memory before the first SQL INSERT. Such an item has no persisted
        // row to delete; only delete rows observed under FOR UPDATE.
        return previous;
    }

    public static void persist(ItemSnapshot[] writes, int[] deletes,
                               int firstOwner, int secondOwner) throws SQLException {
        persist(UUID.randomUUID().toString(), writes, deletes, firstOwner, secondOwner);
    }

    public static void persist(String txId, ItemSnapshot[] writes, int[] deletes,
                               int firstOwner, int secondOwner) throws SQLException {
        // Lock/verify the complete trade batch on the same JDBC transaction.
        persistAtomic(txId, "TRADE", firstOwner, secondOwner, new Mutation[] { con -> {
            final int[] priorOwners = lockAndVerify(con, writes, deletes, firstOwner, secondOwner);
            for (int i = 0; i < writes.length; i++) {
                if (priorOwners[i] == 0)
                    MySqlCharactersItemStorage.insertTradeSnapshot(con, writes[i]);
                else
                    MySqlCharactersItemStorage.updateTradeSnapshot(con, writes[i], priorOwners[i]);
            }
            for (int i = 0; i < deletes.length; i++) {
                final int previousOwner = priorOwners[writes.length + i];
                if (previousOwner > 0)
                    MySqlCharactersItemStorage.deleteTradeSnapshot(con, deletes[i], previousOwner);
            }
        } });
    }
}
