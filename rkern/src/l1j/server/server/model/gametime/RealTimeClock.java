// ===== RealTimeClock.java =====
package l1j.server.server.model.gametime;

import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.model.network.thread.CServerTimer;
import l1j.server.server.model.world.CWorldWork;
import l1j.server.server.model.world.instance.CInstantZoneManager;

public class RealTimeClock {
    public static final int DAY_TO_HOURS = 24;
    public static final int YEAR_TO_HOURS = 365 * DAY_TO_HOURS;

    private static final Logger _log = Logger.getLogger(RealTimeClock.class.getName());
    private static final TimeZone KST = TimeZone.getTimeZone("GMT+9");

    private static final long TICK_MS = 500L;

    private static volatile RealTimeClock _instance;
    private volatile CServerTimer _future;

    private volatile RealTime _currentTime = new RealTime();
    private RealTime _previousTime = null;
    private final HashMap<Integer, List<TimeListener>> _listeners;

    private RealTimeClock() {
        _listeners = new HashMap<>(8);
        _listeners.put(Calendar.MONTH, new CopyOnWriteArrayList<>());
        _listeners.put(Calendar.DAY_OF_MONTH, new CopyOnWriteArrayList<>());
        _listeners.put(Calendar.HOUR_OF_DAY, new CopyOnWriteArrayList<>());
        _listeners.put(Calendar.MINUTE, new CopyOnWriteArrayList<>());
        _listeners.put(Calendar.SECOND, new CopyOnWriteArrayList<>());
    }

    public void print() {
        for (List<TimeListener> listeners : _listeners.values()) {
            for (TimeListener listener : listeners) {
                System.out.println(listener);
            }
        }
    }

    private class TimeUpdater implements Runnable {
        @Override
        public void run() {
            if (_instance != RealTimeClock.this) return;
            try {
                final RealTime prev = _currentTime; 
                final RealTime next = new RealTime();
                _previousTime = prev;
                _currentTime = next;
                notifyChanged(prev, next);
            } catch (Throwable t) {
                _log.log(Level.SEVERE, "RealTimeClock tick error", t);
            }
        }
    }

    private boolean isFieldChanged(int field, RealTime prev, RealTime cur) {
        return prev.get(field) != cur.get(field);
    }

    private void notifyChanged(RealTime prev, RealTime cur) {
        if (isFieldChanged(Calendar.MONTH, prev, cur)) {
            final List<TimeListener> list = _listeners.get(Calendar.MONTH);
            for (TimeListener listener : list) {
                try {
                    listener.onMonthChanged(cur);
                } catch (Throwable t) {
                    _log.log(Level.SEVERE, "[RealTimeClock] listener error (onMonthChanged)", t);
                }
            }
        }
        if (isFieldChanged(Calendar.DAY_OF_MONTH, prev, cur)) {
            final List<TimeListener> list = _listeners.get(Calendar.DAY_OF_MONTH);
            for (TimeListener listener : list) {
                try {
                    listener.onDayChanged(cur);
                } catch (Throwable t) {
                    _log.log(Level.SEVERE, "[RealTimeClock] listener error (onDayChanged)", t);
                }
            }
        }
        if (isFieldChanged(Calendar.HOUR_OF_DAY, prev, cur)) {
            final List<TimeListener> list = _listeners.get(Calendar.HOUR_OF_DAY);
            for (TimeListener listener : list) {
                try {
                    listener.onHourChanged(cur);
                } catch (Throwable t) {
                    _log.log(Level.SEVERE, "[RealTimeClock] listener error (onHourChanged)", t);
                }
            }
        }
        if (isFieldChanged(Calendar.MINUTE, prev, cur)) {
            final List<TimeListener> list = _listeners.get(Calendar.MINUTE);
            for (TimeListener listener : list) {
                try {
                    listener.onMinuteChanged(cur);
                } catch (Throwable t) {
                    _log.log(Level.SEVERE, "[RealTimeClock] listener error (onMinuteChanged)", t);
                }
            }
        }
        if (isFieldChanged(Calendar.SECOND, prev, cur)) {
            CInstantZoneManager.reapWorld();
            final List<TimeListener> list = _listeners.get(Calendar.SECOND);
            for (TimeListener listener : list) {
                try {
                    listener.onSecondChanged(cur);
                } catch (Throwable t) {
                    _log.log(Level.SEVERE, "[RealTimeClock] listener error (onSecondChanged)", t);
                }
            }
        }
    }
    public static synchronized void prepare() {
        if (_instance == null) _instance = new RealTimeClock();
    }

    public static synchronized void start() {
        prepare();
        final RealTimeClock clock = _instance;
        if (clock._future != null) return;
        clock._currentTime = new RealTime();
        clock._future = CWorldWork.scheduleWithFixedDelay(
                clock.new TimeUpdater(), 0L, TICK_MS);
        if (clock._future == null) throw new IllegalStateException("Real-time clock timer rejected");
    }

    public static synchronized void stop() {
        final RealTimeClock clock = _instance;
        _instance = null;
        if (clock != null) {
            final CServerTimer future = clock._future;
            if (future != null) future.cancel();
            for (List<TimeListener> listeners : clock._listeners.values()) listeners.clear();
        }
    }

    public static RealTimeClock getInstance() {
        return _instance;
    }

    public RealTime getRealTime() {
        return _future == null ? new RealTime() : _currentTime;
    }

    public void addListener(TimeListener listener, int type) {
        final List<TimeListener> list = _listeners.get(type);
        if (list == null) {
            throw new IllegalArgumentException(String.format("invalid listener type...%d", type));
        }
        list.add(listener);
    }

    public void removeListener(TimeListener listener, int type) {
        final List<TimeListener> list = _listeners.get(type);
        if (list == null) {
            throw new IllegalArgumentException(String.format("invalid listener type...%d", type));
        }
        list.remove(listener);
    }

    public Calendar getRealTimeCalendar() {
        return Calendar.getInstance(KST); 
    }
}
