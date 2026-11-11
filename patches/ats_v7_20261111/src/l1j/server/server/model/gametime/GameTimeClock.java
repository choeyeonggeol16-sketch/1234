package l1j.server.server.model.gametime;

import java.util.Calendar;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import l1j.server.server.GeneralThreadPool;
import l1j.server.server.utils.IntRange;

public class GameTimeClock {
	private static GameTimeClock _instance;
	private volatile GameTime _currentTime = new GameTime();
	private GameTime _previousTime = null;
	private List<TimeListener> _listeners = new CopyOnWriteArrayList<TimeListener>();
	private List<IDayAndNightHandler> m_day_and_night_listeners = new CopyOnWriteArrayList<IDayAndNightHandler>();
	private class TimeUpdater implements Runnable {
		@Override
		public void run() {
			try {
				_previousTime = null;
				_previousTime = _currentTime;
				boolean is_previous_night = is_night();
				_currentTime = new GameTime();
				notifyChanged();
				boolean is_current_night = is_night();
				if(is_previous_night != is_current_night) {
					GeneralThreadPool.getInstance().execute(new DayAndNightListener(is_current_night));
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			GeneralThreadPool.getInstance().schedule(this, 500);
		}
	}

	private boolean isFieldChanged(int field) {
		return _previousTime.get(field) != _currentTime.get(field);
	}

	private void notifyChanged() {
		if (isFieldChanged(Calendar.MONTH)) {
			for (TimeListener listener : _listeners) {
				listener.onMonthChanged(_currentTime);
			}
		}
		if (isFieldChanged(Calendar.DAY_OF_MONTH)) {
			for (TimeListener listener : _listeners) {
				listener.onDayChanged(_currentTime);
			}
		}
		if (isFieldChanged(Calendar.HOUR_OF_DAY)) {
			for (TimeListener listener : _listeners) {
				listener.onHourChanged(_currentTime);
			}
		}
		if (isFieldChanged(Calendar.MINUTE)) {
			for (TimeListener listener : _listeners) {
				listener.onMinuteChanged(_currentTime);
			}
		}
	}

	private GameTimeClock() {
		GeneralThreadPool.getInstance().execute(new TimeUpdater());
	}

	public static void init() {
		_instance = new GameTimeClock();
	}

	public static GameTimeClock getInstance() {
		return _instance;
	}

	public GameTime getGameTime() {
		return _currentTime;
	}

	public void addListener(TimeListener listener) {
		_listeners.add(listener);
	}

	public void removeListener(TimeListener listener) {
		_listeners.remove(listener);
	}
	
	public void add_days_listener(IDayAndNightHandler listener) {
		m_day_and_night_listeners.add(listener);
	}
	
	public void remove_days_listener(IDayAndNightHandler listener) {
		m_day_and_night_listeners.remove(listener);
	}
	
	public boolean is_night() {
		return !is_day();
	}
	
	public boolean is_day() {
		return IntRange.includes(get_gametime_total_seconds(), 21600, 72000);
	}
	
	public int get_gametime_total_seconds() {
		GameTime time = _currentTime;
		return (time.get(Calendar.HOUR_OF_DAY) * 3600) +
				(time.get(Calendar.MINUTE) * 60) +
				time.get(Calendar.SECOND);
	}
	
	class DayAndNightListener implements Runnable {
		private boolean m_is_night;
		DayAndNightListener(boolean is_night) {
			m_is_night = is_night;
		}
		@Override
		public void run() {
			try {
				if(m_is_night) {
					on_night();
				} else {
					on_day();
				}
			}catch(Exception e) {
				e.printStackTrace();
			}
		}
		
		private void on_night() {
			for(IDayAndNightHandler listener : m_day_and_night_listeners) {
				listener.on_night();
			}		
		}
		
		private void on_day() {
			for(IDayAndNightHandler listener : m_day_and_night_listeners) {
				listener.on_day();
			}
		}
	}
}
