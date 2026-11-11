package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import l1j.server.L1DatabaseFactory;
import l1j.server.server.utils.IntRange;
import l1j.server.server.utils.SQLUtil;

public class ExpTable {
	public static ExpTable _instance;
	public static final int MAX_LEVEL = 127;
	public static final int MAX_EXP = 2147483647;
	public static final IntRange EXP_RANGE = new IntRange(0, 2147483647);

	private static final int _expTable[] = new int[127];
	private static final int _expPenalty[] = new int[127];

	public static Map<Integer, Integer> _bonusList = new HashMap<Integer, Integer>();
	public static Map<Integer, Integer> _levelList = new HashMap<Integer, Integer>();

	Random _Random = new Random(System.nanoTime());

	public static ExpTable getInstance() {
		if (_instance == null) {
			_instance = new ExpTable();
		}
		return _instance;
	}

	public void loadExp(boolean reload) {
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		try {
			if (reload) {
				_bonusList.clear();
				_levelList.clear();
			}
			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("SELECT * FROM exp");
			rs = pstm.executeQuery();
			while (rs.next()) {
				int level = rs.getInt("레벨");
				int exp = (int) rs.getLong("경험치");
				int penalty = (int) rs.getLong("패널티");
				int bonus = rs.getInt("보너스");

				_expTable[level - 1] = exp;
				_expPenalty[level - 1] = penalty;
				_bonusList.put(level, bonus);
				_levelList.put(exp, level);
			}
		} catch (Exception e) {
			e.getStackTrace();
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}

	public static int getExpByLevel(int level) {
		return _expTable[level - 1];
	}

	/**
	 * 다음의 레벨이 되는데 필요한 경험치를 요구한다.
	 * @param level 현재의 레벨
	 * @return 필요한 경험치
	 */
	public static int getNeedExpNextLevel(int level) {
		return getExpByLevel(level + 1) - getExpByLevel(level);
	}

	/**
	 * 누적 경험치로부터 레벨을 요구한다.
	 * @param exp 누적 경험치
	 * @return 요구된 레벨
	 */
	public static int getLevelByExp(int exp) {
		int level;
		for (level = 1; level < _expTable.length; level++) {
			if (exp < _expTable[level]) {
				break;
			}
		}
		return Math.min(level, MAX_LEVEL);
	}

	public static int getExpPercentage(int level, int exp) {
		return (int) (100.0 * ((double) (exp - getExpByLevel(level)) / (double) getNeedExpNextLevel(level)));
	}

	public static double getExpPercentagedouble(int level, int exp) {
		return 100.0D * ((exp - getExpByLevel(level)) / getNeedExpNextLevel(level));
	}

	/**
	 * 현재의 레벨로부터, 경험치의 페널티 레이트를 요구한다
	 * 
	 * @param level 현재의 레벨
	 * @return 요구된 경험치의 페널티 레이트
	 */
	public static double getPenaltyRate(int level) {
		if (level < 50) {
			return 1.0;
		}
		double expPenalty = 1.0;
		expPenalty = 1.0 / _expPenalty[level - 1];

		return expPenalty;
	}

	public static int getExpByQuest(int level, int standardLevel, double percent) {
		double totalPer = percent * 0.01D;
		if (level >= 82) {
			totalPer *= 1.4D;
		} else if (level >= 75) {
			totalPer *= 1.2D;
		} else if (level >= 65) {
			totalPer *= 1.1D;
		}

		if (level >= 79) {
			totalPer /= 16 * (level - 78);
		}
		else if (level >= 75) {
			totalPer /= 8.0D;
		}
		else if (level >= 70) {
			totalPer /= 4.0D;
		}
		else if (level >= 65) {
			totalPer /= 2.0D;
		}

		return (int) (getNeedExpNextLevel(standardLevel) * totalPer);
	}
}