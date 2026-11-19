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
import l1j.server.server.templates.L1MobSkill;

public class MobSkillTable {

	private static final Logger _log = Logger.getLogger(MobSkillTable.class.getName());
	private final boolean _initialized;

	private final TIntObjectMap<L1MobSkill> _mobskills = new TIntObjectHashMap<L1MobSkill>(512);

	private static class SingletonHolder {
		private static MobSkillTable INSTANCE = new MobSkillTable();
	}

	public static MobSkillTable getInstance() {
		return SingletonHolder.INSTANCE;
	}

	public boolean isInitialized() {
		return _initialized;
	}

	private MobSkillTable() {
		loadMobSkillData();
		_initialized = true;
	}

	public static void reload() {
		SingletonHolder.INSTANCE = new MobSkillTable();
	}

	private void loadMobSkillData() {
		// 몬스터별 스킬 개수를 산출
		TIntObjectMap<Integer> skillCounts = new TIntObjectHashMap<Integer>();
		String sqlCount = "SELECT mobid, COUNT(*) as cnt FROM mobskill GROUP BY mobid";

		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sqlCount);
			 ResultSet rs = pstm.executeQuery()) {

			while (rs.next()) {
				skillCounts.put(rs.getInt("mobid"), rs.getInt("cnt"));
			}
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "MobSkillTable count load error", e);
			return;
		}

		// 단 1번의 쿼리로 전체 몬스터 스킬 데이터 한 번에 로드 
		String sqlData = "SELECT * FROM mobskill ORDER BY mobid, actNo";

		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sqlData);
			 ResultSet rs = pstm.executeQuery()) {

			while (rs.next()) {
				int mobid = rs.getInt("mobid");
				int actNo = rs.getInt("actNo");

				L1MobSkill mobskill = _mobskills.get(mobid);
				if (mobskill == null) {
					int count = skillCounts.get(mobid);
					mobskill = new L1MobSkill(count);
					mobskill.set_mobid(mobid);
					_mobskills.put(mobid, mobskill);
				}

				mobskill.setMobName(rs.getString("mobname"));
				mobskill.setType(actNo, rs.getInt("type"));
				mobskill.setTriggerRandom(actNo, rs.getInt("TriRnd"));
				mobskill.setTriggerHp(actNo, rs.getInt("TriHp"));
				mobskill.setTriggerCompanionHp(actNo, rs.getInt("TriCompanionHp"));
				mobskill.setTriggerRange(actNo, rs.getInt("TriRange"));
				mobskill.setTriggerCount(actNo, rs.getInt("TriCount"));
				mobskill.setChangeTarget(actNo, rs.getInt("ChangeTarget"));
				mobskill.setRange(actNo, rs.getInt("Range"));
				mobskill.setAreaWidth(actNo, rs2Int(rs, "AreaWidth"));
				mobskill.setAreaHeight(actNo, rs2Int(rs, "AreaHeight"));
				mobskill.setLeverage(actNo, rs.getInt("Leverage"));
				mobskill.setSkillId(actNo, rs.getInt("SkillId"));
				mobskill.setGfxid(actNo, rs.getInt("Gfxid"));
				mobskill.setActid(actNo, rs.getInt("Actid"));
				mobskill.setSummon(actNo, rs.getInt("SummonId"));
				mobskill.setSummonMin(actNo, rs.getInt("SummonMin"));
				mobskill.setSummonMax(actNo, rs.getInt("SummonMax"));
				mobskill.setPolyId(actNo, rs.getInt("PolyId"));
			}

			_log.config("몬스터 스킬 " + _mobskills.size() + "건 로드 완료");

		} catch (SQLException e) {
			_log.log(Level.SEVERE, "MobSkillTable data load error", e);
		}
	}

	private int rs2Int(ResultSet rs, String columnName) throws SQLException {
		return rs.getInt(columnName);
	}

	public L1MobSkill getTemplate(int id) {
		return _mobskills.get(id);
	}

	public TIntObjectMap<L1MobSkill> getMobSkills() {
		return _mobskills;
	}
}