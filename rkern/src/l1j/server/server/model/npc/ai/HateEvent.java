package l1j.server.server.model.npc.ai;

import org.jctools.queues.MpmcArrayQueue;

import l1j.server.server.model.L1Character;

public final class HateEvent {

	public L1Character cha;
	public int hate;

	private static final int POOL_CAPACITY = 131072;
	private static final int PREALLOC_COUNT = 65536;

	private static final MpmcArrayQueue<HateEvent> POOL = new MpmcArrayQueue<>(POOL_CAPACITY);

	static {
		for (int i = 0; i < PREALLOC_COUNT; i++) {
			POOL.offer(new HateEvent());
		}
	}

	private HateEvent() {
	}


	public static HateEvent obtain(L1Character cha, int hate) {
		HateEvent e = POOL.poll();

		if (e == null) {
			e = new HateEvent();
		}

		e.cha = cha;
		e.hate = hate;

		return e;
	}


	public static void release(HateEvent e) {
		if (e == null) {
			return;
		}

		e.cha = null;
		e.hate = 0;
		POOL.offer(e);
	}
}