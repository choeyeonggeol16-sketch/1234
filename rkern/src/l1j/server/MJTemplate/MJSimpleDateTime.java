package l1j.server.MJTemplate;

import java.util.Calendar;

import l1j.server.server.model.gametime.RealTimeClock;

public class MJSimpleDateTime {
	public static MJSimpleDateTime newInstance(){
		return new MJSimpleDateTime();
	}
	public static MJSimpleDateTime newInstance(int hour, int minute, int second){
		MJSimpleDateTime time = newInstance();
		time.hour = hour;
		time.minute = minute;
		time.second = second;
		return time;
	}
	
	public static MJSimpleDateTime newInstance(Calendar cal){
		return newInstance(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), cal.get(Calendar.SECOND));
	}
	public static MJSimpleDateTime newInstance(long millis){
		Calendar cal = RealTimeClock.getInstance().getRealTimeCalendar();
		cal.setTimeInMillis(millis);
		return newInstance(cal);
	}
	public int hour;
	public int minute;
	public int second;
	private MJSimpleDateTime(){};
	
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
