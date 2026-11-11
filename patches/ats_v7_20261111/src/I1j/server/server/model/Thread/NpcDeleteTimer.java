package I1j.server.server.model.Thread;

import l1j.server.server.GeneralThreadPool;
import l1j.server.server.model.Instance.L1NpcInstance;

public class NpcDeleteTimer implements Runnable {

	private final L1NpcInstance _npc;
	private final int _timeMillis;
	public NpcDeleteTimer(L1NpcInstance npc, int timeMillis) {
		_npc = npc;
		_timeMillis = timeMillis;
	}
	
	@Override
	public void run() {
		_npc.deleteMe();
	}

	public void begin() {
		GeneralThreadPool.getInstance().schedule(this, _timeMillis);
	}

}
