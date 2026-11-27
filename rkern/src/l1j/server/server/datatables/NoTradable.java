package l1j.server.server.datatables;

import java.util.logging.Logger;

public class NoTradable {

	private static final Logger _log = Logger.getLogger(NoTradable.class.getName());

	private static volatile NoTradable _instance;

	private final ItemRestrictionTable restrictions =
			new ItemRestrictionTable("notradable", "거래 불가", _log);

	public static NoTradable getInstance() {
		if (_instance == null) {
			synchronized (NoTradable.class) {
				if (_instance == null) {
					_instance = new NoTradable();
				}
			}
		}
		return _instance;
	}

	private NoTradable() {}

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

    /** Operator override for player-to-player transfers; template rules are checked separately. */
    public static boolean blocksPlayerTransfer(boolean gameMaster, int itemId) {
        return !gameMaster && getInstance().isNoTradable(itemId);
    }

	public boolean isNoTradable(int itemId) {
		return restrictions.contains(itemId);
	}
}
