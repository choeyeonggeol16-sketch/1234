package l1j.server.server.model.item.search.inventory;

import l1j.server.server.model.Instance.L1ItemInstance;

public interface InventoryFindItemFilter {
	public boolean isFilter(L1ItemInstance item);
}
