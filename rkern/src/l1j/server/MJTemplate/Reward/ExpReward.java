package l1j.server.MJTemplate.Reward;

import l1j.server.server.datatables.ExpTable;
import l1j.server.server.model.Instance.L1PcInstance;

public class ExpReward extends AbstractReward{
	
	public static ExpReward newInstance(){
		return new ExpReward();
	}
	
	private ExpReward(){
	}
	
	@Override
	public void do_reward(L1PcInstance pc){
		double exppenalty = ExpTable.getPenaltyRate(pc.getLevel());
		int real_exp = (int) (get_reward_amount() * exppenalty);
		double need_exp = ExpTable.getNeedExpNextLevel(pc.getLevel());
		pc.sendPackets(String.format("경험치 %.6f%%를 보상받았습니다.", ((double)real_exp / need_exp) * 100D));
		pc.addExp(real_exp);
		pc.save();
	}
}
