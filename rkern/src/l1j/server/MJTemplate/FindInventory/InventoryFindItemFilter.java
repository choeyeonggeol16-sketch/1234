package l1j.server.MJTemplate.FindInventory;

import l1j.server.server.model.Instance.L1ItemInstance;

public interface InventoryFindItemFilter {
	public boolean isFilter(L1ItemInstance item);
}
