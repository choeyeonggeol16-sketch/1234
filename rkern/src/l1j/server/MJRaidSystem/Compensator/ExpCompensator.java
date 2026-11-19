package l1j.server.MJRaidSystem.Compensator;

import l1j.server.Config;
import l1j.server.server.datatables.ExpTable;
import l1j.server.server.model.Instance.L1PcInstance;

public class ExpCompensator implements CompensatorElement{
	private int _exp;
	
	public ExpCompensator(int exp){
		_exp = exp;
	}

	@Override
	public void compensate(Object obj) {
		if(_exp <= 0)
			return;

		try{
			L1PcInstance pc = (L1PcInstance)obj;
			double penalty	= ExpTable.getPenaltyRate(pc.getLevel());
			pc.addExp((int)(_exp * Config.RATE_XP * penalty));
			pc.save();
			pc.refresh();
		}catch(Exception e){
			e.printStackTrace();
		}
	}
}
