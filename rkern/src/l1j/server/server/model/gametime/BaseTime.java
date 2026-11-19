package l1j.server.server.model.gametime;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;

import l1j.server.server.utils.IntRange;

public abstract class BaseTime {
    protected static final TimeZone UTC = TimeZone.getTimeZone("UTC");

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
        final Calendar cal = Calendar.getInstance(UTC);
        cal.setTimeInMillis(0);
        cal.add(Calendar.SECOND, time);
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
        final int hour = _calendar.get(Calendar.HOUR_OF_DAY);
        return !IntRange.includes(hour, 6, 17);
    }

    @Override
    public String toString() {
        final SimpleDateFormat f = new SimpleDateFormat("yyyy.MM.dd G 'at' HH:mm:ss z");
        f.setTimeZone(_calendar.getTimeZone());
        return f.format(_calendar.getTime()) + "(" + getSeconds() + ")";
    }

    public boolean equals_time(int hour, int minute, int second) {
        return _calendar.get(Calendar.HOUR_OF_DAY) == hour
                && _calendar.get(Calendar.MINUTE) == minute
                && _calendar.get(Calendar.SECOND) == second;
    }
}