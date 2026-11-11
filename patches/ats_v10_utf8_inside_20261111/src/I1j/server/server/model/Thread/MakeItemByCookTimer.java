package I1j.server.server.model.Thread;

import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.Cooking;
import l1j.server.server.serverpackets.S_ServerMessage;

public class MakeItemByCookTimer extends TimerTask {
	private static Logger _log = Logger.getLogger(MakeItemByCookTimer.class.getName());
	private final L1PcInstance _pc;
	private final Cooking _cook;

	public MakeItemByCookTimer(L1PcInstance pc, Cooking cook) {
		this._pc = pc;
		this._cook = cook;
	}

	@Override
	public void run() {
		try {
			if (this._pc == null) {

				return;
			}
			if (this._pc.isDead()) {
				return;
			}
			makeItem();
		} catch (Throwable e) {
			_log.log(Level.WARNING, e.getLocalizedMessage(), e);
		}
	}

	public void makeItem() {
		ItemInstance temp = ItemTable.getInstance().createItem(this._cook.getEffect().getMakeItemId());
		if (temp != null && this._pc.getInventory().checkAddItem(temp, 1) == 0) {
			ItemInstance item = this._pc.getInventory().storeItem(temp.getItemId(), 1);

			this._pc.sendPackets(new S_ServerMessage(403, item.getItem().getName()));
		}
	}
}