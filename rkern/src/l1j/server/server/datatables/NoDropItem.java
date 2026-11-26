package l1j.server.server.datatables;

import java.util.logging.Logger;

public class NoDropItem {

	private static final Logger _log = Logger.getLogger(NoDropItem.class.getName());

	private static volatile NoDropItem _instance;

	private final ItemRestrictionTable restrictions =
			new ItemRestrictionTable("NoDropItem", "드랍 불가", _log);

	public static NoDropItem getInstance() {
		if (_instance == null) {
			synchronized (NoDropItem.class) {
				if (_instance == null) {
					_instance = new NoDropItem();
				}
			}
		}
		return _instance;
	}

	private NoDropItem() {}

	public void storeId(int itemId) {
		restrictions.storeId(itemId);
	}

	public void deleteId(int itemId) {
		restrictions.deleteId(itemId);
	}

	public void reload() {
		restrictions.reload();
	}

	public int[] getIdList() {
		return restrictions.getIdList();
	}

	public boolean isNoDropItem(int itemId) {
		return restrictions.contains(itemId);
	}
}
