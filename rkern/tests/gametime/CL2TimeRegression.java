package l1j.server.server.model.gametime;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public final class CL2TimeRegression {
    public static void main(String[] args) {
        long wall = Instant.parse("2026-11-27T12:34:56.789Z").toEpochMilli();
        long scaled = (wall - CL2Time.GAME_ORIGIN_MS) * 6 / 1_000;
        int oldWire = (int) (scaled - scaled % 3);
        var clock = new CL2Time(wall, 100_000_000L);
        check(clock.timeAt(100_000_000L) == oldWire, "deployed calendar and packet value");
        check(clock.timeAt(310_999_999L) == oldWire, "retains wall phase below boundary");
        check(clock.timeAt(311_000_000L) == oldWire + 3L, "monotonic clock advances at the wall anchor phase");
        check(clock.timeAt(5_100_000_000L) == oldWire + 30L, "3 seconds of game time per half second");

        long rollover = CL2Time.GAME_ORIGIN_MS + ((1L << 31) + (1L << 32)) / 3 * 500;
        long before = CL2Time.gameSecondsAt(rollover - 500);
        long after = CL2Time.gameSecondsAt(rollover);
        check((int) before >= 0 && (int) after < 0 && after - before == 3L,
                "wire sign wrap leaves server time continuous");
        check(CL2Time.isNight(21_599) && !CL2Time.isNight(21_600)
                && !CL2Time.isNight(64_799) && CL2Time.isNight(64_800), "legacy sunrise and sunset");

        long midnight = Instant.parse("2039-01-31T15:00:00Z").getEpochSecond();
        var kst = new L2DateTime(midnight, L2DateTime.KST_OFFSET);
        check(kst.getSeconds() == midnight && kst.get(Calendar.MONTH) == Calendar.FEBRUARY
                && kst.get(Calendar.DAY_OF_MONTH) == 1 && kst.matches(0, 0, 0), "KST midnight past 2038");
        Calendar clone = kst.getCalendar();
        clone.set(Calendar.HOUR_OF_DAY, 12);
        check(kst.matches(0, 0, 0) && kst.getCalendar().get(Calendar.HOUR_OF_DAY) == 0, "calendar copies are independent");

        CL2Time.prepare();
        try {
            CL2Time current = CL2Time.getInstance();
            var events = current.new ClockEvents(L2DateTime.KST_OFFSET);
            var seen = new ArrayList<String>();
            var listener = new L2TimeListener() {
                @Override public void onMonthChanged(L2DateTime time) { seen.add("month"); }
                @Override public void onDayChanged(L2DateTime time) { seen.add("day"); }
                @Override public void onHourChanged(L2DateTime time) { seen.add("hour"); }
                @Override public void onMinuteChanged(L2DateTime time) { seen.add("minute"); }
                @Override public void onSecondChanged(L2DateTime time) { seen.add("second"); }
            };
            for (int field : new int[]{Calendar.MONTH, Calendar.DAY_OF_MONTH,
                    Calendar.HOUR_OF_DAY, Calendar.MINUTE, Calendar.SECOND})
                events.listeners(field).add(listener);
            events.advance(midnight - 86_400, midnight);
            check(seen.equals(List.of("month", "day", "hour", "minute", "second")),
                    "absolute boundaries detected despite identical time-of-day fields");
            events.advance(midnight, midnight - 10);
            check(seen.size() == 5, "backward wall correction does not redispatch");
            events.listeners(Calendar.SECOND).remove(listener);
            events.advance(midnight, midnight + 1);
            check(seen.size() == 5, "removed listener receives no second event");
            CL2Time.stop();
            events.advance(midnight, midnight + 3_600);
            check(seen.size() == 5, "retired clock cannot dispatch");
        } finally {
            CL2Time.stop();
        }
        System.out.println("PASS CL2Time legacy packet, monotonic phase, int wrap, KST and listener lifecycle");
    }

    private static void check(boolean condition, String detail) {
        if (!condition) throw new AssertionError(detail);
    }
}
