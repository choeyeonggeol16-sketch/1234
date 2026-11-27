package l1j.server.server.storage.cached;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.UUID;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import l1j.server.DatabaseFactory;
import l1j.server.server.storage.mysql.MySqlCharactersItemStorage;
import l1j.server.server.storage.mysql.MySqlCharactersItemStorage.ItemSnapshot;

/**
 * SQL settlement half of a transaction. Game state remains owned by the game
 * executor, and CacheD schedules this on its existing ordered DB workers.
 */
public final class CTransaction {
    private static final Logger LOG = Logger.getLogger(CTransaction.class.getName());
    private static final Pattern TABLE_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

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

    private static final class ReceiptIdentityCollision extends SQLException {
        private static final long serialVersionUID = 1L;

        ReceiptIdentityCollision(String txId) { super("CacheD receipt identity collision: " + txId); }
    }

    private static void logReceipt(Level level, String message, Throwable failure) {
        try { LOG.log(level, message, failure); }
        catch (RuntimeException ignored) { /* A logging handler cannot change the transaction outcome. */ }
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
                                      int firstOwner, int secondOwner, boolean readCurrent)
            throws SQLException {
        boolean confirmed = false;
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT kind,owner_a,owner_b FROM cached_transaction_receipts WHERE tx_id=?"
                        + (readCurrent ? " LOCK IN SHARE MODE" : ""))) {
            ps.setString(1, txId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return false;
                if (!kind.equals(rs.getString(1)) || firstOwner != rs.getInt(2)
                        || secondOwner != rs.getInt(3)) {
                    throw new ReceiptIdentityCollision(txId);
                }
                confirmed = true;
            }
        } catch (SQLException | RuntimeException cleanup) {
            if (!confirmed) throw cleanup;
            logReceipt(Level.WARNING, "Confirmed receipt read cleanup failed: " + txId, cleanup);
        }
        return true;
    }

    private static void insertReceipt(Connection con, String txId, String kind,
                                      int firstOwner, int secondOwner) throws SQLException {
        // Guard the receipt engine and claim the unique ID in one statement.
        // No gameplay operation executes if the receipt cannot join its commit.
        try (PreparedStatement ps = con.prepareStatement("""
                INSERT INTO cached_transaction_receipts (tx_id,kind,owner_a,owner_b)
                SELECT ?,?,?,? FROM information_schema.TABLES
                WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='cached_transaction_receipts'
                  AND ENGINE='InnoDB'
                """)) {
            ps.setString(1, txId);
            ps.setString(2, kind);
            ps.setInt(3, firstOwner);
            ps.setInt(4, secondOwner);
            if (ps.executeUpdate() != 1)
                throw new SQLException("InnoDB transaction receipt was not inserted: " + txId);
        }
    }

    private static boolean claimReceipt(Connection con, String txId, String kind,
                                        int firstOwner, int secondOwner) throws SQLException {
        // SELECT FOR UPDATE on a missing unique key locks its gap. Two
        // unrelated receipts in the same gap could then deadlock on INSERT.
        try {
            insertReceipt(con, txId, kind, firstOwner, secondOwner);
            return true;
        } catch (SQLException failure) {
            // INSERT has resolved the unique-key conflict. A shared current
            // read verifies the committed owner without a lock upgrade or a
            // stale repeatable-read snapshot.
            if (failure.getErrorCode() == 1062) {
                try {
                    if (hasReceipt(con, txId, kind, firstOwner, secondOwner, true)) return false;
                } catch (ReceiptIdentityCollision collision) {
                    throw collision;
                } catch (SQLException | RuntimeException readFailure) {
                    // Our INSERT lost. Rolling back this attempt cannot undo an
                    // earlier receipt whose identity we could not finish reading.
                    readFailure.addSuppressed(failure);
                    throw new OutcomeUnknownException("Duplicate receipt identity unreadable: " + txId, readFailure);
                }
            }
            throw failure;
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
                logReceipt(Level.WARNING, "Confirmed receipt connection close failed", verificationFailure);
            } else uncertain.addSuppressed(verificationFailure);
        }
        if (confirmed) {
            logReceipt(Level.WARNING, "Recovered confirmed CacheD COMMIT using receipt " + txId, null);
            return true;
        }
        logReceipt(Level.SEVERE, "CacheD transaction result unresolved, owner frozen; receipt=" + txId,
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
        try (Connection con = DatabaseFactory.getInstance().getConnection()) {
            requireTransactionalTables(con, TRANSACTION_TABLES);
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
        persistAtomic(txId, "ECONOMY", firstOwner, secondOwner, operations);
    }

    public static void requireTransactionalTables(Connection con, String... tables) throws SQLException {
        if (tables.length == 0) return;
        for (String table : tables) {
            if (table == null || !TABLE_NAME.matcher(table).matches())
                throw new SQLException("Invalid transaction table name");
        }

        // Check the current engines on this connection. Keep the check live
        // so a schema change cannot stay hidden behind a cached result.
        final String query = "SELECT TABLE_NAME, ENGINE FROM information_schema.TABLES "
                + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ("
                + "?,".repeat(tables.length - 1) + "?)";
        final boolean[] found = new boolean[tables.length];
        try (PreparedStatement ps = con.prepareStatement(query)) {
            for (int i = 0; i < tables.length; i++) ps.setString(i + 1, tables[i]);
            try (ResultSet rows = ps.executeQuery()) {
                while (rows.next()) {
                    final String table = rows.getString(1);
                    if (!"InnoDB".equalsIgnoreCase(rows.getString(2)))
                        throw new SQLException("Atomic economy requires InnoDB table: " + table);
                    for (int i = 0; i < tables.length; i++) {
                        if (tables[i].equals(table)) found[i] = true;
                    }
                }
            }
        }
        for (int i = 0; i < tables.length; i++) {
            if (!found[i]) throw new SQLException("Atomic economy requires InnoDB table: " + tables[i]);
        }
    }

    /** One exact primary-key read for each sorted existing row. Prepare once for a multi-item receipt. */
    public static PreparedStatement prepareItemPreimageCheck(Connection con) throws SQLException {
        return con.prepareStatement("SELECT char_id,item_id,count,is_equipped,enchantlvl,is_id,durability,"
                + "charge_count,remaining_time,bless,attr_enchantlvl,special_enchant,package,bless_level,"
                + "bless_level2,item_level,last_used,end_time,Hotel_Town FROM character_items WHERE id=? FOR UPDATE");
    }

    public static void verifyItemPreimage(Connection con, ItemSnapshot v) throws SQLException {
        try (PreparedStatement ps = prepareItemPreimageCheck(con)) { verifyItemPreimage(ps, v); }
    }

    /** Rebind the statement from prepareItemPreimageCheck only after the previous row verified. */
    public static void verifyItemPreimage(PreparedStatement ps, ItemSnapshot v) throws SQLException {
        ps.setInt(1, v.id);
        try (ResultSet r = ps.executeQuery()) {
            // Keep the strict owner/type/count check: a mismatch here is
            // never an invitation to replay a debit or overwrite SQL.
            if (!r.next()) throw preimageFailure(v.id, "missing row", "absent", "present");
            requirePreimageInt(v.id, "char_id", r.getInt(1), v.ownerId);
            requirePreimageInt(v.id, "item_id", r.getInt(2), v.itemId);
            requirePreimageInt(v.id, "count", r.getInt(3), v.count);
            requirePreimageInt(v.id, "is_equipped", r.getInt(4), v.equipped);
            requirePreimageInt(v.id, "enchantlvl", r.getInt(5), v.enchant);
            requirePreimageInt(v.id, "is_id", r.getInt(6), v.identified);
            requirePreimageInt(v.id, "durability", r.getInt(7), v.durability);
            requirePreimageInt(v.id, "charge_count", r.getInt(8), v.charge);
            requirePreimageInt(v.id, "remaining_time", r.getInt(9), v.remaining);
            requirePreimageInt(v.id, "bless", r.getInt(10), v.bless);
            requirePreimageInt(v.id, "attr_enchantlvl", r.getInt(11), v.attr);
            requirePreimageInt(v.id, "special_enchant", r.getInt(12), v.special);
            requirePreimageInt(v.id, "package", r.getInt(13), v.pack);
            requirePreimageInt(v.id, "bless_level", r.getInt(14), v.blessLevel);
            requirePreimageInt(v.id, "bless_level2", r.getInt(15), v.blessLevel2);
            requirePreimageInt(v.id, "item_level", r.getInt(16), v.itemLevel);
            requirePreimageObject(v.id, "Hotel_Town", r.getString(19), v.hotelTown);
            verifyItemTimestamps(ps, r, v.id, 17, v.lastUsed, 18, v.endTime);
        }
    }

    /** Final check on a selected row. Releases its result set before a native conversion.
     * A zero endIndex means that the table does not store an end time. */
    public static void verifyItemTimestamps(PreparedStatement select, ResultSet rows, int id,
            int lastIndex, Timestamp lastUsed, int endIndex, Timestamp endTime) throws SQLException {
        Timestamp storedLast = rows.getTimestamp(lastIndex);
        Timestamp storedEnd = endIndex == 0 ? null : rows.getTimestamp(endIndex);
        int lastScale = timestampScale(rows, lastIndex, id, "last_used", storedLast, lastUsed);
        int endScale = endIndex == 0 ? -1 : timestampScale(rows, endIndex, id, "end_time", storedEnd, endTime);
        if (lastScale < 0 && endScale < 0) return;
        // Servers can round or truncate, including across a second boundary.
        // Reuse the original JDBC session and binding. Never accept a tolerance window.
        rows.close();
        String sql = "SELECT CAST(? AS DATETIME(" + Math.max(0, lastScale)
                + ")), CAST(? AS DATETIME(" + Math.max(0, endScale) + "))";
        try (PreparedStatement cast = select.getConnection().prepareStatement(sql)) {
            cast.setTimestamp(1, lastScale < 0 ? null : lastUsed);
            cast.setTimestamp(2, endScale < 0 ? null : endTime);
            try (ResultSet normalized = cast.executeQuery()) {
                if (!normalized.next()) throw new SQLException("Timestamp conversion failed for item " + id);
                if (lastScale >= 0) requirePreimageObject(id, "last_used", storedLast, normalized.getTimestamp(1));
                if (endScale >= 0) requirePreimageObject(id, "end_time", storedEnd, normalized.getTimestamp(2));
            }
        }
    }

    private static int timestampScale(ResultSet rows, int index, int id, String field,
            Timestamp stored, Timestamp expected) throws SQLException {
        if (java.util.Objects.equals(stored, expected)) return -1;
        if (stored != null && expected != null && expected.getNanos() != 0) {
            ResultSetMetaData meta = rows.getMetaData();
            String type = meta.getColumnTypeName(index);
            int precision = meta.getScale(index);
            if (("DATETIME".equalsIgnoreCase(type) || "TIMESTAMP".equalsIgnoreCase(type))
                    && precision >= 0 && precision <= 6) return precision;
        }
        throw preimageFailure(id, field, stored, expected);
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

    private static OutcomeUnknownException abandonTransaction(Connection con, String txId,
            String boundary, Throwable failure) {
        try { DatabaseFactory.discardConnection(con); }
        catch (SQLException | RuntimeException | Error discard) { failure.addSuppressed(discard); }
        return new OutcomeUnknownException(boundary + " uncertain receipt=" + txId, failure);
    }

    private static void persistAtomic(String txId, String kind, int firstOwner, int secondOwner,
                                      Mutation[] operations) throws SQLException {
        if (txId == null || txId.length() != 36)
            throw new SQLException("Invalid durable transaction ID");
        boolean committed = false;
        boolean commitAttempted = false;
        try (Connection con = DatabaseFactory.getInstance().getConnection()) {
            final boolean autoCommit = con.getAutoCommit();
            if (autoCommit) con.setAutoCommit(false);
            try {
                if (!claimReceipt(con, txId, kind, firstOwner, secondOwner)) {
                    // The matching durable receipt proves the original commit.
                    // The current connection owns only the receipt read lock.
                    committed = true;
                    try {
                        con.rollback();
                        if (autoCommit) con.setAutoCommit(true);
                    } catch (SQLException | RuntimeException cleanup) {
                        // Do not restore auto-commit after a failed rollback.
                        // Connection close/pool cleanup owns this read-only scope.
                        logReceipt(Level.WARNING, "Confirmed receipt connection cleanup failed", cleanup);
                    }
                    return;
                }
                for (Mutation operation : operations) operation.apply(con);
                commitAttempted = true;
                // JDBC commits the active transaction when auto-commit changes
                // from false to true. This also restores the pooled connection.
                if (autoCommit) con.setAutoCommit(true);
                else con.commit();
                committed = true;
            } catch (SQLException | RuntimeException | Error failure) {
                if (commitAttempted)
                    throw abandonTransaction(con, txId, "COMMIT response", failure);
                try { con.rollback(); }
                catch (SQLException | RuntimeException | Error rollback) {
                    failure.addSuppressed(rollback);
                    throw abandonTransaction(con, txId, "ROLLBACK", failure);
                }
                throw failure;
            }
        } catch (OutcomeUnknownException uncertain) {
            // ROLLBACK failures are never presumed safe. An existing receipt
            // still proves COMMIT, but a missing one keeps the owner quarantined.
            if (resolveConfirmedCommit(txId, kind, firstOwner, secondOwner, uncertain)) return;
            throw uncertain;
        } catch (SQLException | RuntimeException failure) {
            if (!committed) throw failure;
            logReceipt(Level.WARNING, "Committed connection close failed", failure);
        }
    }

    public static void writeCharacterMoveRow(Connection con, ItemSnapshot snapshot,
                                             boolean insert, int previousCount) throws SQLException {
        if (insert) {
            // A plain INSERT verifies absence through the primary key. A
            // locking read of the missing row would take a gap lock and can
            // deadlock with an unrelated move inserting in the same gap.
            MySqlCharactersItemStorage.insertTradeSnapshot(con, snapshot);
            return;
        }
        MySqlCharactersItemStorage.updateWarehouseSnapshot(con, snapshot, previousCount);
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
