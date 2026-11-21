package l1j.server.server.model.item.teleport.filter;

import l1j.server.server.model.Instance.L1PcInstance;

public interface TeleportFilter {
	public boolean isFilter(L1PcInstance pc);
}
