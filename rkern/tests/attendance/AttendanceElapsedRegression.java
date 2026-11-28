package l1j.server.server.model.Attendance;

import java.time.Instant;
import java.util.concurrent.TimeUnit;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_ATTENDANCE_USER_DATA_EXTEND;

import l1j.server.server.packets.Protocol.MainServer.Proto.Attend.AttendanceGroupType;
import l1j.server.server.packets.Protocol.MainServer.Proto.Attend.AttendanceUserFlag;
import l1j.server.server.packets.Protocol.MainServer.Proto.Attend.UserAttendanceDataExtend;
import l1j.server.server.packets.Protocol.MainServer.Proto.Attend.UserAttendanceDataGroup;
import l1j.server.server.packets.Protocol.MainServer.Proto.Attend.UserAttendanceState;
import l1j.server.server.packets.Protocol.MainServer.Proto.Attend.UserAttendanceTimeStatus;

public final class AttendanceElapsedRegression {
    public static void main(String[] args) throws Exception {
        AttendanceLoadManager.publishSettings(new AttendanceLoadManager.Settings(1800, 86400, 1, 1, 60));
        long midnight = Instant.parse("2026-11-29T15:00:00Z").toEpochMilli();
        check(AttendanceScheduler.nextResetMillis(midnight - 250, 86_400) == midnight, "KST boundary");
        check(AttendanceScheduler.nextResetMillis(midnight, 86_400) == midnight + 86_400_000L,
                "strictly following KST reset");

        var normal = group(AttendanceGroupType.NORMAL);
        check(normal.secondsUntilCompletion() == 60, "first threshold");
        check(!normal.creditEligibleTime(37) && normal.secondsUntilCompletion() == 23, "partial interval");
        check(normal.creditEligibleTime(25), "delayed expiry crosses threshold");
        check(normal.get_groupData().get(0).get_state() == UserAttendanceState.COMPLETE, "first reward");
        check(normal.secondsUntilCompletion() == -1 && !normal.creditEligibleTime(1_000), "daily cap");
        normal.onUpdateDay(false);
        check(normal.secondsUntilCompletion() == 60, "rearm on next day");
        check(!normal.creditEligibleTime(3) && normal.secondsUntilCompletion() == 57, "post-reset time");
        check(normal.get_groupData().get(0).get_state() == UserAttendanceState.COMPLETE, "retain unclaimed reward");

        var cafe = group(AttendanceGroupType.PC_CAFE);
        check(cafe.secondsUntilCompletion() == -1, "inactive cafe");
        check(!cafe.creditEligibleTime(17), "settle time accrued before eligibility changed");
        check(cafe.secondsUntilCompletion() == -1 && cafe.get_attendanceTime() == 17, "paused cafe keeps partial time");
        cafe.set_resultCode(UserAttendanceTimeStatus.ATTENDANCE_NORMAL);
        check(cafe.secondsUntilCompletion() == 43 && cafe.creditEligibleTime(43), "resume eligible cafe");
        delayedRollover(midnight);
        settingsReanchor();
        System.out.println("PASS attendance threshold, cap, KST reset, missed periods and cafe eligibility");
    }

    private static void delayedRollover(long midnight) throws Exception {
        final long dayMillis = TimeUnit.DAYS.toMillis(1);
        final long dayNanos = TimeUnit.DAYS.toNanos(1);
        final long sec = TimeUnit.SECONDS.toNanos(1);
        final var old = new AttendanceScheduler.Window(midnight, 100 * sec, dayMillis, null);
        final long sample = old.cutoffNanos() + 2 * dayNanos + 15 * sec;
        final var due = old.latestDue(midnight + 2 * dayMillis + 15_000, sample);
        check(due.day() == midnight + 2 * dayMillis && due.nanos() == sample - 15 * sec,
                "delayed worker chooses the latest elapsed boundary");
        check(old.latestDue(midnight - 1, old.cutoffNanos()) == null, "wall clock must reach the boundary");
        check(old.latestDue(midnight, old.cutoffNanos() - sec).nanos() == old.cutoffNanos() - sec,
                "forward clock jump cannot place the effective cutoff in the future");

        final var ctor = AttendanceScheduler.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        final var scheduler = ctor.newInstance();
        final var window = AttendanceScheduler.class.getDeclaredField("window");
        window.setAccessible(true);
        window.set(scheduler, old);

        final var pc = new L1PcInstance();
        pc.setOnlineStatus(1);
        final var packet = SC_ATTENDANCE_USER_DATA_EXTEND.newInstance();
        final var normal = group(AttendanceGroupType.NORMAL);
        packet.add_groups(normal);
        packet.set_userFlag(AttendanceUserFlag.USER_FLAG_NORMAL);
        final var attendanceDay = SC_ATTENDANCE_USER_DATA_EXTEND.class.getDeclaredField("attendanceDay");
        attendanceDay.setAccessible(true);
        attendanceDay.setLong(packet, due.day());
        pc.setAttendanceData(packet);

        final var sessionClass = Class.forName(AttendanceScheduler.class.getName() + "$Session");
        final var sessionCtor = sessionClass.getDeclaredConstructor(AttendanceScheduler.class,
                String.class, L1PcInstance.class, SC_ATTENDANCE_USER_DATA_EXTEND.class);
        sessionCtor.setAccessible(true);
        final var session = sessionCtor.newInstance(scheduler, "fixture", pc, packet);
        final var checkpoint = sessionClass.getDeclaredField("checkpoint");
        checkpoint.setAccessible(true);
        checkpoint.setLong(session, old.cutoffNanos());
        final var accrue = sessionClass.getDeclaredMethod("accrue", long.class, AttendanceScheduler.Boundary.class);
        accrue.setAccessible(true);
        accrue.invoke(session, old.cutoffNanos() + 25 * sec,
                new AttendanceScheduler.Boundary(old.day(), old.cutoffNanos()));

        window.set(scheduler, new AttendanceScheduler.Window(due.day() + dayMillis,
                due.nanos() + dayNanos, dayMillis, due));
        final var credit = sessionClass.getDeclaredMethod("credit", long.class, boolean.class);
        credit.setAccessible(true);
        credit.invoke(session, sample, false);
        check(normal.get_attendanceTime() == 15 && normal.get_groupData().get(0).get_time() == 15,
                "uncommitted progress from missed days cannot spill into the current day");
    }

    private static void settingsReanchor() throws Exception {
        final var ctor = AttendanceScheduler.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        final var scheduler = ctor.newInstance();
        final var window = AttendanceScheduler.class.getDeclaredField("window");
        window.setAccessible(true);
        final var running = AttendanceScheduler.class.getDeclaredField("running");
        running.setAccessible(true);
        final long wall = System.currentTimeMillis();
        final long nano = System.nanoTime();
        final var old = new AttendanceScheduler.Window(wall + 28_800_000L, nano + TimeUnit.HOURS.toNanos(8),
                86_400_000L, null);
        window.set(scheduler, old);
        running.setBoolean(scheduler, true);
        final var shorter = new AttendanceLoadManager.Settings(1800, 1800, 2, 1, 30);
        check(scheduler.reconfigure(shorter), "accept settings reload");
        final var next = (AttendanceScheduler.Window) window.get(scheduler);
        check(next.opened() == old.opened() && next.day() < old.day() && next.intervalMillis() == 1_800_000L,
                "shorter setting reanchors next boundary without a fake reset");
        try {
            new AttendanceLoadManager.Settings(1800, 0, 2, 1, 30);
            throw new AssertionError("nonpositive reset period accepted");
        } catch (IllegalArgumentException expected) {
            check(AttendanceLoadManager.settings() == shorter, "invalid reload preserves active settings");
        }
        final var longer = new AttendanceLoadManager.Settings(1800, 86400, 2, 1, 45);
        check(scheduler.reconfigure(longer), "accept larger period");
        final var last = (AttendanceScheduler.Window) window.get(scheduler);
        check(last.day() >= next.day() && last.opened() == next.opened(), "longer period stays in the current day");
    }

    private static UserAttendanceDataGroup group(AttendanceGroupType type) {
        var group = new UserAttendanceDataGroup();
        group.set_groupType(type);
        group.add_groupData(UserAttendanceDataExtend.newInstance(1, UserAttendanceState.INIT, 0));
        group.add_groupData(UserAttendanceDataExtend.newInstance(2, UserAttendanceState.INIT, 0));
        group.onUpdateDay(false);
        return group;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
