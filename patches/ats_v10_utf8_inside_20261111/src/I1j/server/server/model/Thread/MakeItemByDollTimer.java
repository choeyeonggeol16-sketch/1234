package I1j.server.server.model.Thread;

import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.Instance.DollInstance;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_ServerMessage;

public class MakeItemByDollTimer extends TimerTask {
	private static Logger _log = Logger.getLogger(MakeItemByDollTimer.class.getName());
	private final L1PcInstance _pc;
	private final DollInstance _doll;

	public MakeItemByDollTimer(L1PcInstance pc, DollInstance doll) {
		_pc = pc;
		_doll = doll;
	}

	public void run() {
		try {
			if (_pc == null) {
				if (_doll != null) {
					_doll.deleteDoll();
				}
				return;
			}
			if (_pc.isDead()) {
				if (_doll != null) {
					_doll.deleteDoll();
				}
				return;
			}
			makeItem();
		} catch (Throwable e) {
			_log.log(Level.WARNING, e.getLocalizedMessage(), e);
		}
	}

	public void makeItem() {
		ItemInstance temp = ItemTable.getInstance().createItem(_doll.getDoll().getEffect().getMakeItemId());
		if ((temp != null) && (_pc.getInventory().checkAddItem(temp, 1) == 0)) {
			ItemInstance item = _pc.getInventory().storeItem(temp.getItemId(), 1);
			_pc.sendPackets(new S_ServerMessage(403, item.getItem().getName()));
		}
	}
}