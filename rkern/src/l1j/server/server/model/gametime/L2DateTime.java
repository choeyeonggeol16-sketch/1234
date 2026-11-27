package l1j.server.server.model.gametime;

import java.time.LocalDate;
import java.util.Calendar;
import java.util.TimeZone;

/** Immutable calendar fields for one real-world or game-world second. */
public final class L2DateTime {
    static final int SECONDS_PER_DAY = 86_400;
    static final int KST_OFFSET = 9 * 3600;
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");
    private static final TimeZone KST = TimeZone.getTimeZone("GMT+09:00");

    private final long seconds;
    private final int offset;
    private final int secondOfDay;
    private final LocalDate date;

    L2DateTime(long seconds, int offset) {
        this.seconds = seconds;
        this.offset = offset;
        long localSeconds = seconds + offset;
        secondOfDay = Math.floorMod(localSeconds, SECONDS_PER_DAY);
        date = LocalDate.ofEpochDay(Math.floorDiv(localSeconds, SECONDS_PER_DAY));
    }

    public long getSeconds() { return seconds; }

    public int get(int field) {
        return switch (field) {
            case Calendar.YEAR -> date.getYear();
            case Calendar.MONTH -> date.getMonthValue() - 1;
            case Calendar.DAY_OF_MONTH -> date.getDayOfMonth();
            case Calendar.DAY_OF_YEAR -> date.getDayOfYear();
            case Calendar.DAY_OF_WEEK -> date.getDayOfWeek().getValue() % 7 + Calendar.SUNDAY;
            case Calendar.HOUR_OF_DAY -> secondOfDay / 3600;
            case Calendar.MINUTE -> secondOfDay / 60 % 60;
            case Calendar.SECOND -> secondOfDay % 60;
            default -> getCalendar().get(field);
        };
    }

    public Calendar getCalendar() {
        return calendarAt(seconds * 1000L, offset == KST_OFFSET ? KST : UTC);
    }

    public boolean matches(int hour, int minute, int second) {
        return get(Calendar.HOUR_OF_DAY) == hour && get(Calendar.MINUTE) == minute && get(Calendar.SECOND) == second;
    }

    static Calendar kstCalendarAt(long millis) {
        return calendarAt(millis, KST);
    }

    private static Calendar calendarAt(long millis, TimeZone zone) {
        Calendar calendar = Calendar.getInstance((TimeZone) zone.clone());
        calendar.setTimeInMillis(millis);
        return calendar;
    }
}
