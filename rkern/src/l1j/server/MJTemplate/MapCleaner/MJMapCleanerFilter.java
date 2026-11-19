package l1j.server.MJTemplate.MapCleaner;

import l1j.server.server.model.L1Object;

public interface MJMapCleanerFilter {
	public boolean isFilter(L1Object obj);
}
