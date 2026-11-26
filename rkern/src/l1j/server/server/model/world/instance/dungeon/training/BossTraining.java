package l1j.server.server.model.world.instance.dungeon.training;

import java.util.TimerTask;
import l1j.server.server.model.network.thread.CServerTimer;

import l1j.server.server.model.L1Object;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;

public class BossTraining extends TimerTask {
	final int mapId;
	volatile boolean cancelled;
	L1NpcInstance keeper;
	CServerTimer expiry;
	private volatile int[] activeKeys;

	public BossTraining(int mapId) {
		this.mapId = mapId;
	}

	public int mapId() {
		return mapId;
	}

	public void activate(int[] keyIds) {
		activeKeys = keyIds;
	}

	public boolean isActive() {
		return activeKeys != null;
	}

	boolean acceptsKey(int id) {
		int[] keys = activeKeys;
		if (keys == null || cancelled) return false;
		for (int key : keys) if (key == id) return true;
		return false;
	}

	void closeEntry() {
		activeKeys = null;
	}

	@Override
	public void run() {
		for (L1Object obj : CWorld.getInstance().getVisibleObjects(mapId).values()) {
			if (obj instanceof L1NpcInstance npc) {
				npc.deleteMe();
			} else if (obj instanceof L1PcInstance pc) {
				l1j.server.server.model.network.thread.CIOThreadCallback.getInstance()
						.completeSession(pc.getNetConnection(), () -> {
							if (pc.getMapId() == mapId) pc.start_teleport(34065, 32313, 4, 0, 169, true, false);
						});
			}
		}
	}
}
