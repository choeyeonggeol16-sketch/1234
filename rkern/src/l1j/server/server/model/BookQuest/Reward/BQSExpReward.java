package l1j.server.server.model.BookQuest.Reward;

import java.sql.ResultSet;
import java.sql.SQLException;

import l1j.server.server.datatables.ExpTable;
import l1j.server.server.model.Instance.L1PcInstance;

public class BQSExpReward extends BQSAbstractReward{
	public static BQSExpReward newInstance(ResultSet rs) throws SQLException{
		return newInstance(rs.getInt("reward_grade"), rs.getInt("reward_exp"));
	}
	
	public static BQSExpReward newInstance(int grade, int exp){
		BQSExpReward reward = new BQSExpReward();
		reward.setGrade(grade);
		reward.setExp(exp);
		return reward;
	}
	
	private int _exp;
	private BQSExpReward(){}
	
	public void setExp(int exp){
		_exp = exp;
	}
	
	public int getExp(){
		return _exp;
	}
	
	@Override
	public void doReward(L1PcInstance pc) {
		double exppenalty = ExpTable.getPenaltyRate(pc.getLevel());
		pc.addExp((int) (_exp * exppenalty));
	}

}
