package l1j.server.server.model.gametime;

import java.util.Calendar;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.ThreadPool;

public class L1GameTimeClock {
    private static final Logger _log = Logger.getLogger(L1GameTimeClock.class.getName());
    private static final int TIMER_INTERVAL = 1000;

    private static volatile L1GameTimeClock _instance;

    private final ScheduledFuture<?> _future;

    private volatile L1GameTime _currentTime = new L1GameTime();

    private final List<L1GameTimeListener> _listeners = new CopyOnWriteArrayList<>();

    private class TimeUpdater implements Runnable {
        @Override
        public void run() {
            if (_instance != L1GameTimeClock.this) return;
            try {
                final L1GameTime prev = _currentTime;
                final L1GameTime next = new L1GameTime();
                _currentTime = next;
                notifyChanged(prev, next);
            } catch (Throwable t) {
                _log.log(Level.SEVERE, "[GameTimeClock] tick error", t);
            }
        }
    }

    private boolean isFieldChanged(int field, L1GameTime prev, L1GameTime cur) {
        return prev.get(field) != cur.get(field);
    }

    private void notifyChanged(L1GameTime prev, L1GameTime cur) {
        if (isFieldChanged(Calendar.MONTH, prev, cur)) {
            for (L1GameTimeListener listener : _listeners) {
                try {
                    listener.onMonthChanged(cur);
                } catch (Throwable t) {
                    _log.log(Level.SEVERE, "[GameTimeClock] listener error (onMonthChanged)", t);
                }
            }
        }
        if (isFieldChanged(Calendar.DAY_OF_MONTH, prev, cur)) {
            for (L1GameTimeListener listener : _listeners) {
                try {
                    listener.onDayChanged(cur);
                } catch (Throwable t) {
                    _log.log(Level.SEVERE, "[GameTimeClock] listener error (onDayChanged)", t);
                }
            }
        }
        if (isFieldChanged(Calendar.HOUR_OF_DAY, prev, cur)) {
            for (L1GameTimeListener listener : _listeners) {
                try {
                    listener.onHourChanged(cur);
                } catch (Throwable t) {
                    _log.log(Level.SEVERE, "[GameTimeClock] listener error (onHourChanged)", t);
                }
            }
        }
        if (isFieldChanged(Calendar.MINUTE, prev, cur)) {
            for (L1GameTimeListener listener : _listeners) {
                try {
                    listener.onMinuteChanged(cur);
                } catch (Throwable t) {
                    _log.log(Level.SEVERE, "[GameTimeClock] listener error (onMinuteChanged)", t);
                }
            }
        }
    }

    private L1GameTimeClock() {
        final ScheduledFuture<?> future = ThreadPool.getInstance().scheduleAtFixedRate(
                new TimeUpdater(), 0L, TIMER_INTERVAL);
        if (future == null) {
            throw new IllegalStateException("L1GameTimeClock timer admission failed");
        }
        _future = future;
    }

    public static synchronized void init() {
        if (_instance == null) _instance = new L1GameTimeClock();
    }

    public static synchronized void stop() {
        final L1GameTimeClock clock = _instance;
        _instance = null;
        if (clock != null) {
            clock._future.cancel(false);
            clock._listeners.clear();
        }
    }

    public static L1GameTimeClock getInstance() {
        return _instance;
    }

    public L1GameTime getGameTime() {
        return _currentTime;
    }

    public void addListener(L1GameTimeListener listener) {
        _listeners.add(listener);
    }

    public void removeListener(L1GameTimeListener listener) {
        _listeners.remove(listener);
    }
}