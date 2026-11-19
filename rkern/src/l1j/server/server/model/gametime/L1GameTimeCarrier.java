package l1j.server.server.model.gametime;


import l1j.server.server.model.network.core.sc.packet.SC_GameTime;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.ThreadPool;
import l1j.server.server.model.Instance.L1PcInstance;

public class L1GameTimeCarrier implements Runnable {
    private static final Logger _log = Logger.getLogger(L1GameTimeCarrier.class.getName());

    private static final int SYNC_INTERVAL_SECONDS = 300;
    private static final long POLL_INTERVAL_MS = 500;

    private final L1PcInstance _pc;
    private volatile boolean _on = true;
    private ScheduledFuture<?> _future;
    private long _lastSentBucket = Long.MIN_VALUE;

    public L1GameTimeCarrier(L1PcInstance pc) {
        _pc = pc;
    }

    @Override
    public void run() {
        if (!_on || _pc == null || _pc.getNetConnection() == null) {
            return;
        }

        try {
            final long serverTime = L1GameTimeClock.getInstance().getGameTime().getSeconds();

            final long bucket = serverTime / SYNC_INTERVAL_SECONDS;
            if (bucket != _lastSentBucket) {
                _lastSentBucket = bucket;
                SC_GameTime.send(_pc, serverTime);
            }
        } catch (Exception e) {
            _log.log(Level.WARNING, "[GameTimeCarrier] send error - charName: " + _pc.getName(), e);
        }

    }

    public synchronized void start() {
        if (!_on || _future != null) return;
        // One retained periodic task per connected PC; no fresh scheduled
        // Future / RunnableWrapper allocation every 500 ms.
        final ScheduledFuture<?> future = ThreadPool.getInstance()
                .scheduleWithFixedDelay(this, 0L, POLL_INTERVAL_MS);
        if (future == null) throw new RejectedExecutionException("Game-time carrier not admitted");
        _future = future;
    }

    public synchronized void stop() {
        _on = false;
        final ScheduledFuture<?> future = _future;
        _future = null;
        if (future != null) future.cancel(false);
    }
}