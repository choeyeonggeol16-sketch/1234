package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;

import l1j.server.DatabaseFactory;
import l1j.server.server.storage.CacheD;
import l1j.server.server.model.L1Object;
import l1j.server.server.SkillCheck;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.templates.L1Skills;

public class SkillsTable {

	private static final Logger _log = Logger.getLogger(SkillsTable.class.getName());

	private final TIntObjectMap<L1Skills> _skills = new TIntObjectHashMap<L1Skills>(256);

	private final boolean _initialized;

	private static class SingletonHolder {
		private static SkillsTable INSTANCE = new SkillsTable();
	}

	public static SkillsTable getInstance() {
		return SingletonHolder.INSTANCE;
	}

	private SkillsTable() {
		RestoreSkills();
		_initialized = true;
	}

	public static void reload() {
		SingletonHolder.INSTANCE = new SkillsTable();
	}

	private void RestoreSkills() {
		String sql = "SELECT * FROM skills";

		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql);
			 ResultSet rs = pstm.executeQuery()) {

			FillSkillsTable(rs);

		} catch (SQLException e) {
			_log.log(Level.SEVERE, "error while creating skills table", e);
		}
	}

	private void FillSkillsTable(ResultSet rs) throws SQLException {
		while (rs.next()) {
			L1Skills l1skills = new L1Skills();
			int skill_id = rs.getInt("skill_id");
			l1skills.setSkillId(skill_id);
			l1skills.setName(rs.getString("name"));
			l1skills.setSkillLevel(rs.getInt("skill_level"));
			l1skills.setSkillNumber(rs.getInt("skill_number"));
			l1skills.setMpConsume(rs.getInt("mpConsume"));
			l1skills.setHpConsume(rs.getInt("hpConsume"));
			l1skills.setItemConsumeId(rs.getInt("itemConsumeId"));
			l1skills.setItemConsumeCount(rs.getInt("itemConsumeCount"));
			l1skills.setReuseDelay(rs.getInt("reuseDelay"));
			l1skills.setBuffDuration(rs.getInt("buffDuration"));
			l1skills.setTarget(rs.getString("target"));
			l1skills.setTargetTo(rs.getInt("target_to"));
			l1skills.setDamageValue(rs.getInt("damage_value"));
			l1skills.setDamageDice(rs.getInt("damage_dice"));
			l1skills.setDamageDiceCount(rs.getInt("damage_dice_count"));
			l1skills.setProbabilityValue(rs.getInt("probability_value"));
			l1skills.setProbabilityDice(rs.getInt("probability_dice"));
			l1skills.setAttr(rs.getInt("attr"));
			l1skills.setType(rs.getInt("type"));
			l1skills.setLawful(rs.getInt("lawful"));
			l1skills.setRanged(rs.getInt("ranged"));
			l1skills.setArea(rs.getInt("area"));
			l1skills.setIsThrough(rs.getInt("through"));
			l1skills.setId(rs.getInt("id"));
			l1skills.setNameId(rs.getString("nameid"));
			l1skills.setActionId(rs.getInt("action_id"));
			l1skills.setActionId2(rs.getInt("action_id2"));
			l1skills.setActionId3(rs.getInt("action_id3"));
			l1skills.setCastGfx(rs.getInt("castgfx"));
			l1skills.setCastGfx2(rs.getInt("castgfx2"));
			l1skills.setCastGfx3(rs.getInt("castgfx3"));
			l1skills.setSysmsgIdHappen(rs.getInt("sysmsgID_happen"));
			l1skills.setSysmsgIdStop(rs.getInt("sysmsgID_stop"));
			l1skills.setSysmsgIdFail(rs.getInt("sysmsgID_fail"));

			_skills.put(skill_id, l1skills);
		}
		_log.config("스킬 " + _skills.size() + "건 로드 완료");
	}

	/**
	 * Admit persistence before publishing skill mastery. CacheD executes the
	 * lookup and JDBC mutation on its existing DB workers, never on CIO.
	 * Each character's DB requests share its ordered owner lane.
	 */
	public void spellMastery(final int playerobjid, final int skillid,
			final String skillname, final int active, final int time) {
		CacheD.getInstance().submitDbJobRetryOrdered(playerobjid, () -> {
			try (Connection con = DatabaseFactory.getInstance().getConnection();
				 PreparedStatement check = con.prepareStatement(
					 "SELECT 1 FROM character_skills WHERE char_obj_id=? AND skill_id=?")) {
				check.setInt(1, playerobjid);
				check.setInt(2, skillid);
				try (ResultSet rs = check.executeQuery()) {
					if (rs.next()) return;
				}
				try (PreparedStatement insert = con.prepareStatement(
						"INSERT INTO character_skills SET char_obj_id=?, skill_id=?, skill_name=?, is_active=?, activetimeleft=?")) {
					insert.setInt(1, playerobjid);
					insert.setInt(2, skillid);
					insert.setString(3, skillname);
					insert.setInt(4, active);
					insert.setInt(5, time);
					insert.executeUpdate();
				}
			} catch (SQLException e) {
				throw new IllegalStateException("Skill mastery persistence failed: char="
						+ playerobjid + " skill=" + skillid, e);
			}
		});
		// No in-memory grant on admission refusal; the caller may retry.
		L1Object obj = CWorld.getInstance().findObject(playerobjid);
		if (obj instanceof L1PcInstance) ((L1PcInstance) obj).setSkillMastery(skillid);
		SkillCheck.getInstance().AddSkill(playerobjid, skillid);
	}

	public void spellLost(final int playerobjid, final int skillid) {
		CacheD.getInstance().submitDbJobRetryOrdered(playerobjid, () -> {
			try (Connection con = DatabaseFactory.getInstance().getConnection();
				 PreparedStatement ps = con.prepareStatement(
						"DELETE FROM character_skills WHERE char_obj_id=? AND skill_id=?")) {
				ps.setInt(1, playerobjid);
				ps.setInt(2, skillid);
				ps.executeUpdate();
			} catch (SQLException e) {
				throw new IllegalStateException("Skill loss persistence failed: char="
						+ playerobjid + " skill=" + skillid, e);
			}
		});
		// Do not remove skill from memory when the write was not admitted.
		L1Object obj = CWorld.getInstance().findObject(playerobjid);
		if (obj instanceof L1PcInstance) ((L1PcInstance) obj).removeSkillMastery(skillid);
		SkillCheck.getInstance().DelSkill(playerobjid, skillid);
	}

	public boolean spellCheck(int playerobjid, int skillid) {
		// Online characters already own the authoritative loaded mastery list.
		// A frequent packet-side skill check must not block an IOCP/NIO.2 worker
		// waiting on a JDBC SELECT. Offline/GM lookups retain the DB fallback.
		L1Object obj = CWorld.getInstance().findObject(playerobjid);
		if (obj instanceof L1PcInstance)
			return ((L1PcInstance) obj).isSkillMastery(skillid);
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement ps = con.prepareStatement(
					"SELECT 1 FROM character_skills WHERE char_obj_id=? AND skill_id=?")) {
			ps.setInt(1, playerobjid);
			ps.setInt(2, skillid);
			try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "spellCheck DB error - char=" + playerobjid + " skill=" + skillid, e);
			return false;
		}
	}

	public boolean isInitialized() {
		return _initialized;
	}

	public L1Skills getTemplate(int i) {
		return _skills.get(i);
	}

	public L1Skills getTemplateByItem(int itemid) {
		int skillid = 0;
		switch (itemid) {
			case 210121: skillid = 225; break;
			case 210122: skillid = 226; break;
			case 210123: skillid = 228; break;
			case 210124: skillid = 229; break;
			case 210125: skillid = 230; break;
			case 3000094: skillid = 231; break;
			case 210127: skillid = 234; break;
			case 210128: skillid = 235; break;
			case 210126: skillid = 236; break;
			case 210129: skillid = 237; break;
			case 210130: skillid = 238; break;
			case 210131: skillid = 239; break;
			case 210132: skillid = 240; break;
			default: return null;
		}
		return _skills.get(skillid);
	}

	public L1Skills findBySkillId(int skillId) {
		return _skills.get(skillId);
	}
}