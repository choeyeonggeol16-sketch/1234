package l1j.server.server.model.BookQuest.Reward;

import l1j.server.server.model.Instance.L1PcInstance;

public abstract class BQSAbstractReward {
	private int _grade;
	public int getGrade(){
		return _grade;
	}
	
	public void setGrade(int grade){
		_grade = grade;
	}
	
	public abstract void doReward(L1PcInstance pc);
}
