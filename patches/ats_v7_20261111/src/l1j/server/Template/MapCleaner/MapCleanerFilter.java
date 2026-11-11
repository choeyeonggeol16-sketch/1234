package l1j.server.Template.MapCleaner;

import l1j.server.server.model.L1Object;

public interface MapCleanerFilter {
	public boolean isFilter(L1Object obj);
}
