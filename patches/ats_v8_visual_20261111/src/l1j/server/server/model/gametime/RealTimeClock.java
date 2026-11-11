package l1j.server.server.model.gametime;

import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.GeneralThreadPool;


public class RealTimeClock {
	public static final int DAY_TO_HOURS = 24;
	public static final int YEAR_TO_HOURS = 365 * DAY_TO_HOURS;
	
	public static final int MINUTE_SECONDS = 60;
	public static final int HOUR_SECONDS = 60 * MINUTE_SECONDS;
	public static final int DAYS_SECONDS = HOUR_SECONDS * 24;
		
	private static Logger 				_log 			= Logger.getLogger(RealTimeClock.class.getName());
	private static RealTimeClock 		_instance;
	private volatile RealTime 			_currentTime 	= new RealTime();
	private RealTime 					_previousTime 	= null;
	private HashMap<Integer, List<TimeListener>>	_listeners;
	
	private RealTimeClock() {
		_listeners = new HashMap<Integer, List<TimeListener>>(8);
		_listeners.put(Calendar.MONTH, 			new CopyOnWriteArrayList<TimeListener>());
		_listeners.put(Calendar.DAY_OF_MONTH, 	new CopyOnWriteArrayList<TimeListener>());
		_listeners.put(Calendar.HOUR_OF_DAY, 	new CopyOnWriteArrayList<TimeListener>());
		_listeners.put(Calendar.MINUTE, 		new CopyOnWriteArrayList<TimeListener>());
		_listeners.put(Calendar.SECOND, 		new CopyOnWriteArrayList<TimeListener>());
		GeneralThreadPool.getInstance().execute(new TimeUpdater());
	}
	
	private class TimeUpdater implements Runnable {
		public void run() {
			long cur = System.currentTimeMillis();
			try {
				_previousTime = null;
				_previousTime = _currentTime;
				_currentTime = new RealTime();
				notifyChanged();
			} catch (Exception e) {
				_log.log(Level.SEVERE, "RealTimeClock[]Error", e);
			} finally {
				cur = System.currentTimeMillis() - cur;
				if(cur < 500) {
					GeneralThreadPool.getInstance().schedule(this, 500L - cur);
				} else {
					GeneralThreadPool.getInstance().execute(this);
				}
			}
		}
	}

	private boolean isFieldChanged(int field) {
		return _previousTime.get(field) != _currentTime.get(field);
	}

	private void notifyChanged() {
		List<TimeListener> list = null;
		if (isFieldChanged(Calendar.MONTH)) {
			list = _listeners.get(Calendar.MONTH);
			for(TimeListener listener : list) {
				listener.onMonthChanged(_currentTime);
			}
		}
		if (isFieldChanged(Calendar.DAY_OF_MONTH)) {
			list = _listeners.get(Calendar.DAY_OF_MONTH);
			for(TimeListener listener : list) {
				listener.onDayChanged(_currentTime);
			}
		}
		if (isFieldChanged(Calendar.HOUR_OF_DAY)) {
			list = _listeners.get(Calendar.HOUR_OF_DAY);
			for(TimeListener listener : list) {
				listener.onHourChanged(_currentTime);
			}
		}
		if (isFieldChanged(Calendar.MINUTE)) {
			list = _listeners.get(Calendar.MINUTE);
			for(TimeListener listener : list) {
				listener.onMinuteChanged(_currentTime);
			}
		}
		if(isFieldChanged(Calendar.SECOND)){
			list = _listeners.get(Calendar.SECOND);
			for(TimeListener listener : list) {
				listener.onSecondChanged(_currentTime);
			}
		}
	}

	public static void init() {
		_instance = new RealTimeClock();
	}

	public static RealTimeClock getInstance() {
		return _instance;
	}

	public RealTime getRealTime() {
		return _currentTime;
	}

	public void addListener(TimeListener listener, int type) {
		List<TimeListener> list = _listeners.get(type);
		if(list == null) {
			throw new IllegalArgumentException(String.format("invalid listener type...%d", type));
		}	
		list.add(listener);
	}

	public void removeListener(TimeListener listener, int type) {
		List<TimeListener> list = _listeners.get(type);
		if(list == null) {
			throw new IllegalArgumentException(String.format("invalid listener type...%d", type));
		}
		list.remove(listener);
	}

	public Calendar getRealTimeCalendar() {
		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT+9")); // 한국 시간
		return cal;
	}
}
