package l1j.server.server.model.world.residence;

import java.lang.reflect.Method;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Calendar;
import java.util.TreeSet;

import l1j.server.Config;

public final class ResidenceDeadlineRegression {
    public static void main(String[] args) throws Exception {
        final long boundary = Instant.parse("2026-11-29T03:00:00Z").toEpochMilli();
        check(CWorldAgitTimer.nextMinuteAfter(boundary - 1) == boundary, "due just before the minute");
        check(CWorldAgitTimer.nextMinuteAfter(boundary) == boundary + 60_000L, "exact deadline is excluded");
        check(CWorldAgitTimer.nextMinuteAfter(boundary + 1) == boundary + 60_000L, "due just after the minute");

        final var ctor = CWorldAgitTimer.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        final var scheduler = ctor.newInstance();
        final var kind = Class.forName(CWorldAgitTimer.class.getName() + "$Kind");
        final Object auction = kind.getEnumConstants()[0];
        final Object tax = kind.getEnumConstants()[1];
        final var add = CWorldAgitTimer.class.getDeclaredMethod(
                "replace", kind, int.class, Object.class, Calendar.class);
        add.setAccessible(true);
        final var queueField = CWorldAgitTimer.class.getDeclaredField("pending");
        queueField.setAccessible(true);
        final var queue = (TreeSet<?>) queueField.get(scheduler);
        final var initial = deadline(System.currentTimeMillis() + 120_000L);
        final var renewed = deadline(initial.getTimeInMillis() + 120_000L);
        final L1AuctionBoard board = new L1AuctionBoard();
        final L1House house = new L1House();

        add.invoke(scheduler, tax, 123, house, initial);
        add.invoke(scheduler, auction, 123, board, initial);
        final Object previous = queue.first();
        final Method row = previous.getClass().getDeclaredMethod("row");
        row.setAccessible(true);
        check(row.invoke(previous) == board, "auction comes first at equal minute");
        add.invoke(scheduler, auction, 123, board, initial);
        check(queue.first() == previous && queue.size() == 2, "unchanged deadline does not rearm");
        add.invoke(scheduler, tax, 123, house, renewed);
        check(queue.last() != previous && row.invoke(queue.last()) == house, "renewal replaces old reservation");
        final var replacement = new L1AuctionBoard();
        add.invoke(scheduler, auction, 123, replacement, initial);
        check(queue.first() != previous && row.invoke(queue.first()) == replacement,
                "reloaded object reuses ID without retaining the previous generation");
        add.invoke(scheduler, auction, 123, null, null);
        check(queue.size() == 1 && row.invoke(queue.first()) == house, "retire only the deleted kind");
        final var past = deadline(1_000L);
        add.invoke(scheduler, auction, 123, replacement, past);
        final var readyAt = queue.first().getClass().getDeclaredMethod("readyAt");
        readyAt.setAccessible(true);
        check((long) readyAt.invoke(queue.first()) > past.getTimeInMillis()
                && row.invoke(queue.first()) == replacement, "catch up overdue rows before future rows");
        Config.TIME_ZONE = "Asia/Seoul";
        final var readDeadline = HouseTable.class.getDeclaredMethod("timestampToCalendar", Timestamp.class);
        readDeadline.setAccessible(true);
        final var undatedHouse = new L1House();
        final var nullDeadline = (Calendar) readDeadline.invoke(null, (Object) null);
        final int pendingCount = queue.size();
        add.invoke(scheduler, tax, 124, undatedHouse, nullDeadline);
        check(queue.size() == pendingCount, "SQL NULL creates an endless foreclosure retry");
        System.out.println("PASS residence strict minute, catch-up, renewal, retirement, reload identity and null tax deadline");
    }

    private static Calendar deadline(long millis) {
        final var value = Calendar.getInstance();
        value.setTimeInMillis(millis);
        return value;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
