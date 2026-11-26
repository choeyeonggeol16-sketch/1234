package l1j.server.server.model.Instance;

import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.task.TaskLane;
import l1j.server.server.model.L1World;
import l1j.server.server.model.network.core.sc.packet.SC_WorldPutObject;
import l1j.server.server.templates.L1Npc;
import l1j.server.server.utils.IntRange;
import l1j.server.server.utils.L1SpawnUtil;

public class L1MobTrapInstance extends L1NpcInstance {

	private static final long serialVersionUID = 1L;

	private static Logger _log = Logger.getLogger(L1MobTrapInstance.class.getName());

	public L1MobTrapInstance(L1Npc template) {
		super(template);
	}

	private static final int TRAP_TICK_MS = 500;
	private boolean AIStart;

	/** 유저가 트랩을 발견시 **/
	@Override
	public void onPerceive(L1PcInstance perceivedFrom) {
		if (perceivedFrom == null) return;
		
		if (SC_WorldPutObject.get(perceivedFrom.getNetConnection(), this)) {
			perceivedFrom.addKnownObject(this);
		}
		startTrapAI();
	}

	private synchronized void startTrapAI() {
		if (AIStart || isDestroyed()) return;
		AIStart = true;
		// A trap must not hold an instant-pool worker sleeping indefinitely.
		// The existing scheduled executor is bounded and reports admission failure.
		if (TaskLane.WORLD.schedule(new TrapAITick(), TRAP_TICK_MS) == null) {
			AIStart = false;
			_log.warning("Trap AI schedule rejected npc=" + getId());
		}
	}

	private final class TrapAITick implements Runnable {
		@Override
		public void run() {
			synchronized (L1MobTrapInstance.this) {
				if (!AIStart || isDestroyed()) {
					AIStart = false;
					return;
				}
			}
			boolean finished = true;
			try {
				finished = onTrapAi();
			} catch (RuntimeException e) {
				_log.log(Level.WARNING, "Trap AI failed npc=" + getId(), e);
			}
			synchronized (L1MobTrapInstance.this) {
				if (finished || isDestroyed()) {
					AIStart = false;
				} else if (TaskLane.WORLD.schedule(this, TRAP_TICK_MS) == null) {
					AIStart = false;
					_log.warning("Trap AI re-schedule rejected npc=" + getId());
				}
			}
		}
	}

	private boolean onTrapAi() {
		if (L1World.getInstance().getRecognizePlayer(this).size() == 0) {
			return true;
		}
		for (L1PcInstance pc : L1World.getInstance().getVisiblePlayer(this, 0)) {
			if (pc != null) {
				int count = IntRange.random(1) + 3;// 불러낼수치
				int[] moblist = { 7000056, 7000057, 7000058, 7000059, 7000060 };// 나올몹종류
				for (int i = 0; i < count; i++) {
				L1SpawnUtil.spawn5(getX(), getY(), getMapId(), 5, moblist[IntRange.random(moblist.length)], 4,false);
				}
				deleteMe();
				return true;
			}
		}
		return false;
	}
}
