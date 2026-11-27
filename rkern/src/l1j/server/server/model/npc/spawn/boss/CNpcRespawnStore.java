package l1j.server.server.model.npc.spawn.boss;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;

import l1j.server.DatabaseFactory;

/** Blocking checkpoint I/O for server startup and the database worker. */
final class CNpcRespawnStore {
    record Entry(String fingerprint, long slot, long due) {}

    private static final String CREATE = """
            CREATE TABLE IF NOT EXISTS boss_respawn_state (
                npcid INT NOT NULL, spawn_x INT NOT NULL, spawn_y INT NOT NULL, spawn_map INT NOT NULL,
                fingerprint VARBINARY(64) NOT NULL, slot_ms BIGINT NOT NULL, due_ms BIGINT NOT NULL,
                PRIMARY KEY (npcid, spawn_x, spawn_y, spawn_map)
            ) ENGINE=InnoDB
            """;
    private static final String METADATA = """
            SELECT t.ENGINE, GROUP_CONCAT(s.COLUMN_NAME ORDER BY s.SEQ_IN_INDEX)
            FROM information_schema.TABLES t LEFT JOIN information_schema.STATISTICS s
              ON s.TABLE_SCHEMA=t.TABLE_SCHEMA AND s.TABLE_NAME=t.TABLE_NAME AND s.INDEX_NAME='PRIMARY'
            WHERE t.TABLE_SCHEMA=DATABASE() AND t.TABLE_NAME='boss_respawn_state' GROUP BY t.ENGINE
            """;
    private static final String COLUMNS = "npcid,spawn_x,spawn_y,spawn_map,fingerprint,slot_ms,due_ms";
    private static final String INSERT = "INSERT INTO boss_respawn_state (" + COLUMNS + ") VALUES (?,?,?,?,?,?,?)";
    private static final String GUARD =
            " WHERE npcid=? AND spawn_x=? AND spawn_y=? AND spawn_map=? AND BINARY fingerprint=? AND slot_ms=? AND due_ms=?";
    private static final String UPDATE = "UPDATE boss_respawn_state SET fingerprint=?,slot_ms=?,due_ms=?" + GUARD;
    private static final String DELETE = "DELETE FROM boss_respawn_state" + GUARD;

    private CNpcRespawnStore() {}

    static Map<CBossSpawnKey, Entry> loadAll() throws SQLException {
        try (Connection con = DatabaseFactory.getConnectionInstance(); var stmt = con.createStatement()) {
            if (!hasSchema(con)) {
                stmt.executeUpdate(CREATE);
                if (!hasSchema(con)) throw new SQLException("Could not create boss_respawn_state");
            }
            var entries = new HashMap<CBossSpawnKey, Entry>();
            try (ResultSet rows = stmt.executeQuery("SELECT " + COLUMNS + " FROM boss_respawn_state")) {
                while (rows.next()) {
                    var key = new CBossSpawnKey(rows.getInt(1), rows.getInt(2), rows.getInt(3), rows.getInt(4));
                    entries.put(key, new Entry(rows.getString(5), rows.getLong(6), rows.getLong(7)));
                }
            }
            return entries;
        }
    }

    private static boolean hasSchema(Connection con) throws SQLException {
        try (var query = con.createStatement(); ResultSet rows = query.executeQuery(METADATA)) {
            if (!rows.next()) return false;
            if (!"InnoDB".equalsIgnoreCase(rows.getString(1))
                    || !"npcid,spawn_x,spawn_y,spawn_map".equals(rows.getString(2))) {
                throw new SQLException("boss_respawn_state requires InnoDB and its four-column placement primary key");
            }
            return true;
        }
    }

    /** A false result means the placement has already changed. An I/O failure has an unknown outcome. */
    static boolean advance(CBossSpawnKey key, Entry expected, Entry next) throws SQLException {
        try (Connection con = DatabaseFactory.getConnectionInstance()) {
            return advance(con, key, expected, next);
        }
    }

    /** Publishes the world occurrence only while owning its exact SQL checkpoint. */
    static boolean dispatch(CBossSpawnKey key, Entry expected, Entry next, BooleanSupplier publish) throws SQLException {
        Objects.requireNonNull(expected);
        Objects.requireNonNull(publish);
        return atomic(con -> advance(con, key, expected, next) && publish.getAsBoolean());
    }

    private static boolean advance(Connection con, CBossSpawnKey key, Entry expected, Entry next) throws SQLException {
        validate(next);
        if (next.equals(expected)) throw new IllegalArgumentException("Advancing a boss requires a new checkpoint");
        try (var stmt = con.prepareStatement(expected == null ? INSERT : UPDATE)) {
            if (expected == null) {
                bindKey(stmt, 1, key);
                bindEntry(stmt, 5, next);
            } else {
                bindUpdate(stmt, key, expected, next);
            }
            try { return stmt.executeUpdate() == 1; }
            catch (SQLException failure) {
                if (expected == null && duplicateKey(failure)) return false;
                throw failure;
            }
        }
    }

    /**
     * Applies only the supplied replacements as a single transaction. Missing expected rows
     * must be absent in SQL. A null replacement removes the exact previous receipt.
     */
    static boolean reconcile(Map<CBossSpawnKey, Entry> expected, Map<CBossSpawnKey, Entry> replacements)
            throws SQLException {
        if (replacements.isEmpty()) return true;
        return atomic(con -> writeChanges(con, expected, replacements));
    }

    @FunctionalInterface
    private interface SqlWork {
        boolean apply(Connection con) throws SQLException;
    }

    private static boolean atomic(SqlWork work) throws SQLException {
        try (Connection con = DatabaseFactory.getConnectionInstance()) {
            con.setAutoCommit(false);
            boolean committing = false;
            try {
                boolean matched = work.apply(con);
                if (!matched) {
                    con.rollback();
                    return false;
                }
                committing = true;
                con.commit();
                return true;
            } catch (SQLException | RuntimeException | Error failure) {
                if (!committing) {
                    try { con.rollback(); }
                    catch (SQLException | RuntimeException rollback) {
                        failure.addSuppressed(rollback);
                        discard(con, failure);
                    }
                } else {
                    discard(con, failure);
                }
                throw failure;
            }
        }
    }

    private static boolean writeChanges(Connection con, Map<CBossSpawnKey, Entry> expected,
                                        Map<CBossSpawnKey, Entry> replacements) throws SQLException {
        try (var insert = con.prepareStatement(INSERT); var update = con.prepareStatement(UPDATE);
             var delete = con.prepareStatement(DELETE)) {
            int inserts = 0;
            for (var change : replacements.entrySet()) {
                CBossSpawnKey key = change.getKey();
                Entry before = expected.get(key);
                Entry after = change.getValue();
                if (Objects.equals(before, after)) continue;
                if (after != null) validate(after);
                if (before == null) {
                    bindKey(insert, 1, key);
                    bindEntry(insert, 5, after);
                    insert.addBatch();
                    inserts++;
                } else if (after == null) {
                    bindKey(delete, 1, key);
                    bindEntry(delete, 5, before);
                    if (delete.executeUpdate() != 1) return false;
                } else {
                    bindUpdate(update, key, before, after);
                    if (update.executeUpdate() != 1) return false;
                }
            }
            if (inserts > 0) {
                try { insert.executeBatch(); }
                catch (SQLException failure) {
                    if (duplicateKey(failure)) return false;
                    throw failure;
                }
            }
            return true;
        }
    }

    private static boolean duplicateKey(SQLException failure) {
        return failure.getErrorCode() == 1062;
    }

    // Existing rows are read verbatim so the calendar can replace a malformed time under its exact CAS guard.
    private static void validate(Entry entry) {
        String hash = entry.fingerprint();
        if (hash == null || hash.isEmpty() || hash.length() > 64 || entry.due() < entry.slot()
                || !hash.chars().allMatch(c -> c >= '!' && c <= '~')) {
            throw new IllegalArgumentException("Invalid boss respawn checkpoint");
        }
    }

    private static void bindUpdate(PreparedStatement stmt, CBossSpawnKey key, Entry before, Entry after)
            throws SQLException {
        bindEntry(stmt, 1, after);
        bindKey(stmt, 4, key);
        bindEntry(stmt, 8, before);
    }

    private static void bindKey(PreparedStatement stmt, int at, CBossSpawnKey key) throws SQLException {
        stmt.setInt(at, key.npcId());
        stmt.setInt(at + 1, key.x());
        stmt.setInt(at + 2, key.y());
        stmt.setInt(at + 3, key.mapId());
    }

    private static void bindEntry(PreparedStatement stmt, int at, Entry entry) throws SQLException {
        stmt.setString(at, entry.fingerprint());
        stmt.setLong(at + 1, entry.slot());
        stmt.setLong(at + 2, entry.due());
    }

    private static void discard(Connection con, Throwable failure) {
        try { DatabaseFactory.discardConnection(con); }
        catch (SQLException | RuntimeException discard) { failure.addSuppressed(discard); }
    }
}
