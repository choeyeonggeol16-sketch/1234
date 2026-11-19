package l1j.server.MJTemplate.Teleport;

import l1j.server.server.model.Instance.L1PcInstance;

public interface MJTeleportFilter {
	public boolean isFilter(L1PcInstance pc);
}
