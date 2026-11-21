package l1j.server.server.model.character.classinfo.stat;

import l1j.server.server.model.Instance.L1PcInstance;

public class MainDexStat implements ClassMainStat{

	@Override
	public void addStat(L1PcInstance pc, int i) {
		pc.getAbility().addAddedDex(i);
	}
}
