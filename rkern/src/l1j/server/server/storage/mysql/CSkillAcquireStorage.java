package l1j.server.server.storage.mysql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;

import l1j.server.server.model.character.skill.passive.PassiveInfo;
import l1j.server.server.templates.L1Skills;

/** SQL for permanent skills. Mutations are staged in the caller's CacheD receipt. */
public final class CSkillAcquireStorage {
    private static final int[] EMPTY = {};
    private static final String INSERT = "INSERT INTO character_skills "
            + "(char_obj_id,skill_id,skill_name,is_active,activetimeleft) VALUES (?,?,?,0,0)";
    private static final String IDEMPOTENT_INSERT = INSERT + " ON DUPLICATE KEY UPDATE skill_id=skill_id";

    private CSkillAcquireStorage() {}

    public static int[] load(Connection con, int charId) throws SQLException {
        try (PreparedStatement query = con.prepareStatement(
                "SELECT skill_id FROM character_skills WHERE char_obj_id=? ORDER BY skill_id")) {
            query.setInt(1, charId);
            try (ResultSet rows = query.executeQuery()) {
                if (!rows.next()) return EMPTY;
                int[] ids = new int[16];
                int count = 0;
                do {
                    if (count == ids.length) ids = Arrays.copyOf(ids, count << 1);
                    ids[count++] = rows.getInt("skill_id");
                } while (rows.next());
                return count == ids.length ? ids : Arrays.copyOf(ids, count);
            }
        }
    }

    public static void insert(Connection con, int charId, L1Skills skill) throws SQLException {
        try (PreparedStatement insert = con.prepareStatement(INSERT)) {
            bind(insert, charId, skill);
            if (insert.executeUpdate() != 1)
                throw new SQLException("Skill insert failed: " + charId + "/" + skill.getSkillId());
        }
    }

    public static void insertBatch(Connection con, int charId, List<L1Skills> skills, boolean idempotent)
            throws SQLException {
        if (skills.isEmpty()) return;
        if (skills.size() == 1 && !idempotent) {
            insert(con, charId, skills.getFirst());
            return;
        }
        try (PreparedStatement insert = con.prepareStatement(idempotent ? IDEMPOTENT_INSERT : INSERT)) {
            for (L1Skills skill : skills) {
                bind(insert, charId, skill);
                insert.addBatch();
            }
            int[] counts = insert.executeBatch();
            if (counts.length != skills.size()) throw new SQLException("Incomplete skill batch: " + charId);
            for (int count : counts) {
                if (count == Statement.EXECUTE_FAILED || (!idempotent && count == 0))
                    throw new SQLException("Skill batch insert failed: " + charId);
            }
        }
    }

    private static void bind(PreparedStatement insert, int charId, L1Skills skill) throws SQLException {
        insert.setInt(1, charId);
        insert.setInt(2, skill.getSkillId());
        insert.setString(3, skill.getName());
    }

    public static void insertPassives(Connection con, int charId, List<PassiveInfo> passives) throws SQLException {
        if (passives.isEmpty()) return;
        try (PreparedStatement insert = con.prepareStatement(
                "INSERT INTO passive_user_info (character_id,passive_id,passive_name) VALUES (?,?,?) "
                + "ON DUPLICATE KEY UPDATE passive_id=passive_id")) {
            for (PassiveInfo passive : passives) {
                insert.setInt(1, charId);
                insert.setInt(2, passive.getPassiveId());
                insert.setString(3, passive.getPassiveName());
                insert.addBatch();
            }
            for (int count : insert.executeBatch())
                if (count == Statement.EXECUTE_FAILED) throw new SQLException("Passive batch insert failed: " + charId);
        }
    }

    public static void insertPermanentEffects(Connection con, int charId, int[] effects) throws SQLException {
        if (effects.length == 0) return;
        try (PreparedStatement insert = con.prepareStatement(
                "INSERT INTO character_buff (char_obj_id,skill_id,remaining_time,poly_id) VALUES (?,?,0,0) "
                + "ON DUPLICATE KEY UPDATE remaining_time=0,poly_id=0")) {
            for (int effect : effects) {
                insert.setInt(1, charId);
                insert.setInt(2, effect);
                insert.addBatch();
            }
            for (int count : insert.executeBatch())
                if (count == Statement.EXECUTE_FAILED) throw new SQLException("Permanent effect insert failed: " + charId);
        }
    }

    public static void deletePermanentEffects(Connection con, int charId, int... effects) throws SQLException {
        String placeholders = String.join(",", java.util.Collections.nCopies(effects.length, "?"));
        try (PreparedStatement delete = con.prepareStatement(
                "DELETE FROM character_buff WHERE char_obj_id=? AND skill_id IN (" + placeholders + ")")) {
            delete.setInt(1, charId);
            for (int i = 0; i < effects.length; i++) delete.setInt(i + 2, effects[i]);
            delete.executeUpdate();
        }
    }

    public static void changeClass(Connection con, int charId, int oldType, int type, int gfx, int returnStat)
            throws SQLException {
        try (PreparedStatement change = con.prepareStatement(
                "UPDATE characters SET Type=?, Class=?, ReturnStat=? WHERE objid=? AND Type=?")) {
            change.setInt(1, type);
            change.setInt(2, gfx);
            change.setInt(3, returnStat);
            change.setInt(4, charId);
            change.setInt(5, oldType);
            if (change.executeUpdate() != 1) throw new SQLException("Stale class change: " + charId);
        }
        deleteAll(con, charId);
        deleteAllPassives(con, charId);
    }

    public static void deleteAll(Connection con, int charId) throws SQLException {
        try (PreparedStatement delete = con.prepareStatement("DELETE FROM character_skills WHERE char_obj_id=?")) {
            delete.setInt(1, charId);
            delete.executeUpdate();
        }
    }

    public static void deleteAllPassives(Connection con, int charId) throws SQLException {
        try (PreparedStatement delete = con.prepareStatement("DELETE FROM passive_user_info WHERE character_id=?")) {
            delete.setInt(1, charId);
            delete.executeUpdate();
        }
    }

    /** Used by the existing exclusive maintenance reset. */
    public static void resetAllCharacters(Connection con) throws SQLException {
        try (Statement delete = con.createStatement()) {
            delete.executeUpdate("DELETE FROM character_skills");
            delete.executeUpdate("DELETE FROM passive_user_info");
        }
    }
}
