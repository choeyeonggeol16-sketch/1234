package l1j.server.server.model.gametime;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;

import l1j.server.server.utils.IntRange;

public abstract class BaseTime {
	protected final int _time;

	protected final Calendar _calendar;

	public BaseTime() {
		this(System.currentTimeMillis());
	}

	public BaseTime(long timeMillis) {
		_time = makeTime(timeMillis);
		_calendar = makeCalendar(_time);
	}

	protected Calendar makeCalendar(int time) {
		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
		cal.setTimeInMillis(0);
		cal.add(Calendar.SECOND, _time);
		return cal;
	}

	protected abstract int makeTime(long timeMillis);

	protected abstract long getBaseTimeInMil();

	public int get(int field) {
		return _calendar.get(field);
	}

	public int getSeconds() {
		return _time;
	}

	public Calendar getCalendar() {
		return (Calendar) _calendar.clone();
	}

	public boolean isNight() {
		int hour = _calendar.get(Calendar.HOUR_OF_DAY);
		return !IntRange.includes(hour, 6, 17); // 6:00-17:59, 낮이 아니면 true
	}

	public String toString() {
		SimpleDateFormat f = new SimpleDateFormat(
				"yyyy.MM.dd G 'at' HH:mm:ss z");
		f.setTimeZone(_calendar.getTimeZone());
		return f.format(_calendar.getTime()) + "(" + getSeconds() + ")";
	}
	
	public boolean equals_time(int hour, int minute, int second){
		return _calendar.get(Calendar.HOUR_OF_DAY) == hour &&
				_calendar.get(Calendar.MINUTE) == minute &&
				_calendar.get(Calendar.SECOND) == second;
	}
	
	public boolean equals_day_time(int day, int hour, int minute, int second){
		//1일요일 2월요일 3화요일 4수요일 5목요일 6금요일 7토요일
		return _calendar.get(Calendar.DAY_OF_WEEK) == day &&
				_calendar.get(Calendar.HOUR_OF_DAY) == hour &&
				_calendar.get(Calendar.MINUTE) == minute &&
				_calendar.get(Calendar.SECOND) == second;
		
	}
}