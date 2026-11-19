package l1j.server.server.Controller;

import l1j.server.server.ThreadPool;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;

public class GameEffectActionController implements Runnable {

	private L1NpcInstance _npc;
	private int _actId;
	private int _count;
	private int _delay;

	public GameEffectActionController(L1NpcInstance npc, int actId, int count, int delay) {
		_npc = npc;
		_actId = actId;
		_count = count;
		_delay = delay;
	}

    // Delayed scheduling owns only the deadline; broadcasts stay on the instant
    // worker so a large AOI send cannot block the shared timer scheduler.
    private boolean started;
    private final Runnable nextTick = () -> {
        if (_npc == null || _count <= 0) return;
        try {
            ThreadPool.getInstance().execute(this);
        } catch (java.util.concurrent.RejectedExecutionException rejected) {
            finish();
        }
    };

    public synchronized void start() {
        if (started || _count <= 0 || _npc == null) return;
        started = true;
        try {
            ThreadPool.getInstance().execute(this);
        } catch (java.util.concurrent.RejectedExecutionException rejected) {
            finish();
        }
    }

    @Override
    public void run() {
        final L1NpcInstance npc = _npc;
        if (npc == null || _count <= 0) return;
        try {
            SC_DoActionGFX.broadcast(npc, _actId);
        } catch (RuntimeException e) {
            java.util.logging.Logger.getLogger(GameEffectActionController.class.getName())
                    .log(java.util.logging.Level.WARNING, "NPC repeated effect failed", e);
        }
        if (--_count > 0 && ThreadPool.getInstance().schedule(nextTick, Math.max(0L, (long) _delay)) != null) {
            return;
        }
        finish();
    }

    private void finish() {
        _npc = null;
        _count = 0;
    }
}
