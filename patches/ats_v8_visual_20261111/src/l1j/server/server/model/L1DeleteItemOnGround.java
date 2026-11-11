
package l1j.server.server.model;

import java.util.List;
import java.util.logging.Logger;

import l1j.server.configure.Config;
import l1j.server.server.GeneralThreadPool;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_SystemMessage;


public class L1DeleteItemOnGround {
	private DeleteTimer _deleteTimer;
	public static final int EXECUTE_STATUS_NONE = 0;
	public static final int EXECUTE_STATUS_READY = 2;

	private int _executeStatus = EXECUTE_STATUS_NONE;

	private static final int INTERVAL = Config.ALT_ITEM_DELETION_TIME * 60 * 1000 - 10 * 1000;
 

	private static final Logger _log = Logger.getLogger(L1DeleteItemOnGround.class.getName());

	public L1DeleteItemOnGround() {
	}

	private class DeleteTimer implements Runnable {
		public DeleteTimer() {
		}

		@Override
		public void run() {
			switch (_executeStatus) {
			case EXECUTE_STATUS_NONE: { 
				if (Config.DeleteTimeMent != null) {
					L1World.getInstance().broadcastPacketToAll(new S_SystemMessage(Config.DeleteTimeMent));
				} 
				_executeStatus = EXECUTE_STATUS_READY;
				GeneralThreadPool.getInstance().schedule(this, 20000);
			}
				break;

			case EXECUTE_STATUS_READY: {
				deleteItem();  
				_executeStatus = EXECUTE_STATUS_NONE;
				GeneralThreadPool.getInstance().schedule(this, INTERVAL);
			}
				break;
			}
		}
	}

	public void initialize() {
		if (!Config.ALT_ITEM_DELETION_TYPE.equalsIgnoreCase("auto")) {
			return;
		}

		_deleteTimer = new DeleteTimer();
		GeneralThreadPool.getInstance().schedule(_deleteTimer, INTERVAL); // 타이머 개시
	}
  

	private void deleteItem() {
		int numOfDeleted = 0;
		ItemInstance item = null;
		List<L1PcInstance> players = null;
		L1Inventory groundInventory = null;
		for (L1Object obj : L1World.getInstance().getObject()) {
			if (!(obj instanceof ItemInstance)) {
				continue;
			}

			item = (ItemInstance) obj;
			if (item.getX() == 0 && item.getY() == 0) { // 지면상의 아이템은 아니고, 누군가의 소유물
				continue;
			}
			if (item.getItem().getItemId() == 40515) { // 정령의 돌
				continue;
			}
			if (L1HouseLocation.isInHouse(item.getX(), item.getY(), item.getMapId())) { // 아지트내
				continue;
			}

			players = L1World.getInstance().getVisiblePlayer(item, Config.ALT_ITEM_DELETION_RANGE);
			if (players.isEmpty()) { // 지정 범위내에 플레이어가 없으면 삭제
				groundInventory = L1World.getInstance().getInventory(item.getX(), item.getY(), item.getMapId());
				groundInventory.removeItem(item);
				numOfDeleted++;
			}
		}
		_log.fine("월드 맵상의 아이템을 자동 삭제. 삭제수: " + numOfDeleted);
	}
}