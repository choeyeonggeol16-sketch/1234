package l1j.server.AutoHuntSystem;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

public final class NativeStepCadenceTest {
    private static final long MS = 1_000_000L;
    private static final class Clock implements LongSupplier {
        long now = 10_000 * MS;
        public long getAsLong() { return now; }
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static long approve(NativeStepSession s, Clock c, int x) {
        check(s.offerMove(x, 100, 4, 2, c.now), "initial offer");
        check(!s.offerMove(x, 100, 4, 3, c.now + MS), "pending offer cannot redirect");
        check(s.consumeMoveStatus(x, 100, 4, x, 100, 2, c.now + 2 * MS)
                == NativeStepSession.MOVE_APPROVED, "strict packet approval");
        return s.currentApprovedSerial();
    }
    private static void cadence(long interval) {
        Clock c = new Clock();
        List<String> commands = new ArrayList<>();
        NativeStepSession s = new NativeStepSession(commands::add, c);
        s.begin();
        long serial = approve(s, c, 100);
        check(commands.get(commands.size() - 1).contains("CANCEL"), "ack cancels input");
        c.now += 15 * MS;
        s.completeMove(true, serial, interval);
        check(s.appliedStepSequence() == 1, "only applied packet advances route");
        long due = c.now + interval * MS;
        check(!s.offerMove(101, 100, 4, 2, due - 1), "too early at interval " + interval);
        check(s.offerMove(101, 100, 4, 2, due), "missed normal sprite deadline " + interval);
        check(!s.offerMove(101, 100, 4, 2, due + MS), "one offer per step");
        s.end();
        check(!s.offerMove(101, 100, 4, 2, due + interval * MS), "no input after stop");
    }
    public static void main(String[] args) {
        for (long interval : new long[]{100, 180, 200, 333, 600, 1000, 1500}) cadence(interval);

        Clock c = new Clock();
        NativeStepSession s = new NativeStepSession(command -> {}, c);
        s.begin();
        long serial = approve(s, c, 100);
        c.now += 15 * MS;
        s.completeMove(false, serial, 180);
        check(s.appliedStepSequence() == 0, "failed move cannot advance route");
        check(s.takeResyncRequest(), "failed movement still requests resync");
        check(!s.offerMove(100, 100, 4, 2, c.now + 599 * MS), "failure quiet interval kept");
        check(s.offerMove(100, 100, 4, 2, c.now + 600 * MS), "failure recovery deadline");

        s.end();s.begin();
        long stale = approve(s, c, 100);
        s.end();s.begin();
        long current = approve(s, c, 100);
        s.completeMove(true, stale, 180);
        check(s.isCurrentApprovedSerial(current) && s.hasOutstandingMove(c.now), "stale completion unlocked new session");
        check(s.appliedStepSequence() == 0, "stale completion advanced route");
        s.completeMove(true, current, 0);
        check(!s.offerMove(101, 100, 4, 2, c.now + 599 * MS), "invalid interval safe fallback");
        check(s.offerMove(101, 100, 4, 2, c.now + 600 * MS), "invalid interval fallback deadline");

        s.end();s.begin();
        check(s.offerMove(100, 100, 4, 2, c.now), "wrong heading fixture");
        check(s.consumeMoveStatus(100, 100, 4, 100, 100, 3, c.now + MS)
                == NativeStepSession.MOVE_HEADING, "heading check weakened");
        check(s.takeResyncRequest(), "wrong heading resync retained");
        s.end();s.begin();
        check(s.offerMove(100, 100, 4, 2, c.now), "wrong origin fixture");
        check(s.consumeMoveStatus(100, 100, 4, 99, 100, 2, c.now + MS)
                == NativeStepSession.MOVE_CLIENT_ORIGIN, "origin check weakened");

        s.end();s.begin();
        check(s.offerMove(100, 100, 4, 2, c.now), "silent timeout fixture");
        s.refreshPending(c.now + 900 * MS);
        check(!s.hasOutstandingMove(c.now + 900 * MS), "silent offer expires");
        check(s.retrySilentMove(100, 100, 4, c.now + 1499 * MS), "silent recovery wait");
        check(!s.hasOutstandingMove(c.now + 1499 * MS), "silent retry was too early");
        check(s.retrySilentMove(100, 100, 4, c.now + 1500 * MS), "silent retry at original deadline");
        check(s.hasOutstandingMove(c.now + 1500 * MS), "silent retry not dispatched");
        System.out.println("PASS: 7 sprite intervals, exact deadlines, one-step approval, stale session, stop, strict origin/heading, failure and silent-retry delays");
    }
}
