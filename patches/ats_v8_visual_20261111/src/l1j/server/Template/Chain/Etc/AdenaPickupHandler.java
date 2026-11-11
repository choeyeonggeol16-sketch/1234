package l1j.server.Template.Chain.Etc;

import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;

public interface AdenaPickupHandler {
	public int do_pickup(L1PcInstance owner, ItemInstance item, int amount);
}

