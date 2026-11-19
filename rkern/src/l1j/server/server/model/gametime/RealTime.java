// ===== RealTime.java =====
package l1j.server.server.model.gametime;

import java.util.Calendar;
import java.util.TimeZone;

public class RealTime extends BaseTime {

    private static final TimeZone KST = TimeZone.getTimeZone("GMT+9");

    public RealTime() {
        super();
    }

    public RealTime(long timeMillis) {
        super(timeMillis);
    }

    @Override
    protected int makeTime(long timeMillis) {
        final long t1 = timeMillis - getBaseTimeInMil();
        if (t1 < 0) {
            throw new IllegalArgumentException();
        }
        return (int) (t1 / 1000L);
    }

    @Override
    protected long getBaseTimeInMil() {
        return 0L;
    }

    @Override
    protected Calendar makeCalendar(int time) {
        final Calendar cal = Calendar.getInstance(KST);
        cal.setTimeInMillis(0);
        cal.add(Calendar.SECOND, time);
        return cal;
    }
}