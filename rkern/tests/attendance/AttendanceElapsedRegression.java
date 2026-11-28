package l1j.server.server.model.Attendance;

import java.time.Instant;

import l1j.server.server.packets.Protocol.MainServer.Proto.Attend.AttendanceGroupType;
import l1j.server.server.packets.Protocol.MainServer.Proto.Attend.UserAttendanceDataExtend;
import l1j.server.server.packets.Protocol.MainServer.Proto.Attend.UserAttendanceDataGroup;
import l1j.server.server.packets.Protocol.MainServer.Proto.Attend.UserAttendanceState;
import l1j.server.server.packets.Protocol.MainServer.Proto.Attend.UserAttendanceTimeStatus;

public final class AttendanceElapsedRegression {
    public static void main(String[] args) {
        AttendanceLoadManager.ATTEN_TOTAL_TIMESECOND = 60;
        AttendanceLoadManager.ATTEN_DAILY_MAX_COUNT = 1;
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
        System.out.println("PASS attendance threshold, cap, KST reset and cafe eligibility");
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
