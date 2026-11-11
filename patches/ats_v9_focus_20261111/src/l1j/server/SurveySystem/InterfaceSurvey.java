package l1j.server.SurveySystem;

import l1j.server.server.model.Instance.L1PcInstance;

public interface InterfaceSurvey {
	public void survey(L1PcInstance pc, int num, boolean isYes);
}
