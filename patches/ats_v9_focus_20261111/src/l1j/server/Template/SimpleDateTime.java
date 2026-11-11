package l1j.server.Template;

import java.util.Calendar;

import l1j.server.server.model.gametime.RealTimeClock;

public class SimpleDateTime {
	public static SimpleDateTime newInstance(){
		return new SimpleDateTime();
	}
	public static SimpleDateTime newInstance(int hour, int minute, int second){
		SimpleDateTime time = newInstance();
		time.hour = hour;
		time.minute = minute;
		time.second = second;
		return time;
	}
	
	public static SimpleDateTime newInstance(Calendar cal){
		return newInstance(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), cal.get(Calendar.SECOND));
	}
	public static SimpleDateTime newInstance(long millis){
		Calendar cal = RealTimeClock.getInstance().getRealTimeCalendar();
		cal.setTimeInMillis(millis);
		return newInstance(cal);
	}
	public int hour;
	public int minute;
	public int second;
	private SimpleDateTime(){};
	
	public Calendar to_calendar(){
		Calendar cal = RealTimeClock.getInstance().getRealTimeCalendar();
		cal.set(Calendar.HOUR_OF_DAY, hour);
		cal.set(Calendar.MINUTE, minute);
		cal.set(Calendar.SECOND, second);
		return cal;
	}
	
	public boolean after(Calendar cal){
		return hour >= cal.get(Calendar.HOUR_OF_DAY) && minute >= cal.get(Calendar.MINUTE) && second > cal.get(Calendar.SECOND);
	}
	
	public boolean before(Calendar cal){
		return !after(cal);
	}
}
