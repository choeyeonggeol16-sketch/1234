package l1j.server.server.model;

import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.configure.Config;
import l1j.server.server.GeneralThreadPool;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_DropItem;
import l1j.server.server.serverpackets.S_RemoveObject;

public class L1GroundInventory extends L1Inventory {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private class DeletionTimer implements Runnable {
		private final ItemInstance _item;

		public DeletionTimer(ItemInstance item) {
			_item = item;
		}

		@Override
		public void run() {
			try {
				synchronized (L1GroundInventory.this) {
					if (! _items.contains(_item)) {// 주워진 타이밍에 따라서는 이 조건을 채울 수 있다
						return; // 이미 주워지고 있다
					}
					removeItem(_item);
				}
			} catch (Throwable t) {
				_log.log(Level.SEVERE, t.getLocalizedMessage(), t);
			}
		}
	}
	
	private void setTimer(ItemInstance item) {
		if (!Config.ALT_ITEM_DELETION_TYPE.equalsIgnoreCase("std")) {
			return;
		}
		if (item.getItemId() == 40515) { // 정령의 돌
			return;
		}

		GeneralThreadPool.getInstance().schedule(new DeletionTimer(item), Config.ALT_ITEM_DELETION_TIME * 60 * 1000);
	}

	public L1GroundInventory(int objectId, int x, int y, short map) {
		setId(objectId);
		setX(x);
		setY(y);
		setMap(map);
		L1World.getInstance().addVisibleObject(this);
	}

	@Override
	public void onPerceive(L1PcInstance perceivedFrom) {
		for (ItemInstance item : getItems()) {
			if (!perceivedFrom.knownsObject(item)) {
				perceivedFrom.addKnownObject(item);
				perceivedFrom.sendPackets(new S_DropItem(item)); // 플레이어에 DROPITEM 정보를 통지
			}
		}
	}

	// 인식 범위내에 있는 플레이어에 오브젝트 송신
	@Override
	public void insertItem(ItemInstance item) {
		setTimer(item);

		for (L1PcInstance pc : L1World.getInstance().getRecognizePlayer(item)) {
			pc.sendPackets(new S_DropItem(item));
			pc.addKnownObject(item);
		}
	}

	// 보이는 범위내에 있는 플레이어의 오브젝트 갱신
	@Override
	public void updateItem(ItemInstance item) {
		for (L1PcInstance pc : L1World.getInstance().getRecognizePlayer(item)) {
			pc.sendPackets(new S_DropItem(item));
		}
	}

	// 하늘 인벤트리 파기 및 보이는 범위내에 있는 플레이어의 오브젝트 삭제
	@Override
	public void deleteItem(ItemInstance item) {
		for (L1PcInstance pc : L1World.getInstance().getRecognizePlayer(item)) {
			pc.sendPackets(S_RemoveObject.get(item));
			pc.removeKnownObject(item);
		}
 
		_items.remove(item);
		if (_items.size() == 0) {
			L1World.getInstance().removeVisibleObject(this);
		}
	}

	private static Logger _log = Logger .getLogger(L1PcInventory.class.getName());
}
