package l1j.server.server.Controller;

import l1j.server.server.ThreadPool;
import l1j.server.server.model.Instance.L1NpcInstance;

import l1j.server.server.model.network.core.sc.packet.SC_Sound;
public class GameSoundController implements Runnable {

	private L1NpcInstance _npc;
	private int _soundNum;
	private int _count;
	private int _delay;

	public GameSoundController(L1NpcInstance npc, int soundNum, int count, int delay) {
		_npc = npc;
		_soundNum = soundNum;
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
            SC_Sound.broadcast(npc, _soundNum);
        } catch (RuntimeException e) {
            java.util.logging.Logger.getLogger(GameSoundController.class.getName())
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
