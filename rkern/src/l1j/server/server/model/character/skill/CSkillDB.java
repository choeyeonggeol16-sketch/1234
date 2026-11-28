package l1j.server.server.model.character.skill;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import gnu.trove.map.hash.TIntObjectHashMap;
import l1j.server.DatabaseFactory;
import l1j.server.server.model.character.skill.acquire.CSkillAcquireDB;
import l1j.server.server.templates.L1Skills;

/** Shared immutable skill definitions and the acquisition index built from the same rows. */
public final class CSkillDB {
    private static final Logger LOG = Logger.getLogger(CSkillDB.class.getName());

    private final TIntObjectHashMap<L1Skills> skills;
    private final CSkillAcquireDB acquisition;

    private CSkillDB(TIntObjectHashMap<L1Skills> skills, Connection con) throws SQLException {
        this.skills = skills;
        this.acquisition = new CSkillAcquireDB(this, con);
    }

    private static final class Holder {
        private static volatile CSkillDB instance = load();
    }

    public static CSkillDB getInstance() {
        return Holder.instance;
    }

    public static synchronized boolean reload() {
        CSkillDB current = Holder.instance;
        try {
            Holder.instance = load();
            return true;
        } catch (IllegalStateException failure) {
            LOG.log(Level.SEVERE, "Skill reload failed; retaining " + current.skills.size() + " definitions", failure);
            return false;
        }
    }

    private static CSkillDB load() {
        TIntObjectHashMap<L1Skills> definitions = new TIntObjectHashMap<>(1024);
        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement query = con.prepareStatement("SELECT * FROM skills")) {
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    L1Skills skill = new L1Skills(rows);
                    int id = skill.getSkillId();
                    // Existing skill ids and protocol levels may have gaps or special values.
                    if (id <= 0 || skill.getName() == null || definitions.putIfAbsent(id, skill) != null)
                        throw new SQLException("Invalid or duplicate skill definition: " + id);
                }
            }
            if (definitions.isEmpty()) throw new SQLException("Empty skill definitions");
            return new CSkillDB(definitions, con);
        } catch (SQLException failure) {
            throw new IllegalStateException("Could not load skill definitions", failure);
        }
    }

    public L1Skills getTemplate(int skillId) {
        return skills.get(skillId);
    }

    public CSkillAcquireDB acquireDB() {
        return acquisition;
    }
}
