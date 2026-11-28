package l1j.server.server.model.Attendance;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Timestamp;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import l1j.server.DatabaseFactory;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_ATTENDANCE_USER_DATA_EXTEND;
import l1j.server.server.packets.Protocol.MainServer.Proto.Attend.AttendanceGroupType;
import l1j.server.server.packets.Protocol.MainServer.Proto.Attend.AttendanceUserFlag;
import l1j.server.server.packets.Protocol.MainServer.Proto.Attend.UserAttendanceDataExtend;
import l1j.server.server.packets.Protocol.MainServer.Proto.Attend.UserAttendanceDataGroup;
import l1j.server.server.packets.Protocol.MainServer.Proto.Attend.UserAttendanceState;

public final class AttendanceStorageRegression {
    public static void main(String[] args) throws Exception {
        final String password = System.getenv().getOrDefault("RKERN_STORAGE_TEST_PASSWORD", "");
        final String schema = "rkern_attendance_" + UUID.randomUUID().toString().replace("-", "");
        try (var admin = DriverManager.getConnection(args[0], args[1], password);
                var ddl = admin.createStatement()) {
            ddl.executeUpdate("CREATE DATABASE " + schema);
            try {
                admin.setCatalog(schema);
                ddl.executeUpdate("""
                        CREATE TABLE attendance_userinfo (
                            account VARCHAR(40) PRIMARY KEY, attendance_data LONGBLOB,
                            attendance_indices VARCHAR(128), attendance_calendar DATETIME
                        ) ENGINE=InnoDB
                        """);
                ddl.executeUpdate("CREATE TABLE attendance_startup (id INT PRIMARY KEY, startup_info DATETIME) ENGINE=InnoDB");
                final var config = new HikariConfig();
                config.setJdbcUrl(args[0]);
                config.setUsername(args[1]);
                config.setPassword(password);
                config.setCatalog(schema);
                config.setMaximumPoolSize(2);
                try (var pool = new HikariDataSource(config)) {
                    final var field = DatabaseFactory.class.getDeclaredField("DATABASE_POOL");
                    field.setAccessible(true);
                    field.set(null, pool);
                    try {
                        exercise(admin);
                        durableRollover(admin);
                    } finally {
                        field.set(null, null);
                    }
                }
            } finally {
                admin.setCatalog("mysql");
                ddl.executeUpdate("DROP DATABASE " + schema);
            }
        }
        System.out.println("PASS attendance durable progress, rollback, claim dedup, stale replay and missed KST days");
    }

    private static void exercise(Connection admin) throws Exception {
        AttendanceLoadManager.ATTEN_TOTAL_TIMESECOND = 60;
        AttendanceLoadManager.ATTEN_DAILY_MAX_COUNT = 1;
        final String account = "schedule_fixture";
        final long day = 1_795_795_200_000L;
        final var packet = SC_ATTENDANCE_USER_DATA_EXTEND.newInstance();
        final var group = new UserAttendanceDataGroup();
        group.set_groupType(AttendanceGroupType.NORMAL);
        group.add_groupData(UserAttendanceDataExtend.newInstance(1, UserAttendanceState.INIT, 0));
        group.add_groupData(UserAttendanceDataExtend.newInstance(2, UserAttendanceState.INIT, 0));
        group.onUpdateDay(false);
        packet.add_groups(group);
        packet.set_userFlag(AttendanceUserFlag.USER_FLAG_NORMAL);
        try (var insert = admin.prepareStatement("INSERT INTO attendance_userinfo VALUES (?,?,?,?)")) {
            insert.setString(1, account);
            insert.setBytes(2, packet.bodyBytes());
            insert.setString(3, packet.createIndices());
            insert.setTimestamp(4, new Timestamp(day));
            insert.executeUpdate();
        }

        final var save = SC_ATTENDANCE_USER_DATA_EXTEND.class.getDeclaredMethod("saveSnapshot",
                String.class, byte[].class, String.class, long.class);
        save.setAccessible(true);
        check(!group.creditEligibleTime(12), "first partial snapshot");
        final byte[] stale = packet.bodyBytes();
        final String partialIndices = packet.createIndices();
        check(!group.creditEligibleTime(25), "newer partial snapshot");
        save.invoke(null, account, packet.bodyBytes(), partialIndices, day);
        save.invoke(null, account, stale, partialIndices, day);
        final var saved = SC_ATTENDANCE_USER_DATA_EXTEND.readLoginSnapshot(account);
        final var decoded = SC_ATTENDANCE_USER_DATA_EXTEND.newInstance();
        decoded.readFrom(saved.data);
        final var savedGroup = AttendanceRewardClaims.findGroup(decoded, AttendanceGroupType.NORMAL);
        check(savedGroup.get_attendanceTime() == 37 && savedGroup.get_groupData().get(0).get_time() == 37,
                "stale same-day snapshot erased partial progress");

        check(group.creditEligibleTime(23), "first threshold");
        final byte[] completed = packet.bodyBytes();
        final String indices = packet.createIndices();
        save.invoke(null, account, completed, indices, day);

        admin.setAutoCommit(false);
        check(SC_ATTENDANCE_USER_DATA_EXTEND.stageRewardClaim(admin, account, AttendanceGroupType.NORMAL, 0),
                "complete slot is claimable");
        admin.rollback();
        check(SC_ATTENDANCE_USER_DATA_EXTEND.stageRewardClaim(admin, account, AttendanceGroupType.NORMAL, 0),
                "rollback preserves claim eligibility");
        admin.commit();
        admin.setAutoCommit(true);

        save.invoke(null, account, completed, indices, day);
        check(!SC_ATTENDANCE_USER_DATA_EXTEND.stageRewardClaim(admin, account, AttendanceGroupType.NORMAL, 0),
                "snapshot replay cannot resurrect a claimed slot");
        group.onUpdateDay(false);
        check(!group.creditEligibleTime(7), "new day has distinct progress");
        save.invoke(null, account, packet.bodyBytes(), packet.createIndices(), day + 86_400_000L);
        save.invoke(null, account, completed, indices, day);
        final var next = SC_ATTENDANCE_USER_DATA_EXTEND.newInstance();
        next.readFrom(SC_ATTENDANCE_USER_DATA_EXTEND.readLoginSnapshot(account).data);
        final var nextGroup = AttendanceRewardClaims.findGroup(next, AttendanceGroupType.NORMAL);
        check(nextGroup.get_attendanceTime() == 7
                && nextGroup.get_groupData().get(0).get_state() == UserAttendanceState.CLEAR,
                "prior-day replay cannot roll back the new day");
    }

    private static void durableRollover(Connection admin) throws Exception {
        final int daySeconds = 86_400;
        final long dayMillis = TimeUnit.SECONDS.toMillis(daySeconds);
        AttendanceLoadManager.ATTEN_RESET_PERIOD_SECOND = daySeconds;
        final long wall = System.currentTimeMillis();
        final long today = AttendanceScheduler.nextResetMillis(wall, daySeconds) - dayMillis;
        final long missed = today - 2 * dayMillis;
        final long oldCutoff = System.nanoTime() - TimeUnit.MILLISECONDS.toNanos(wall - missed);
        final var calendar = l1j.server.server.model.gametime.CGameTime.getRealTimeCalendar();
        calendar.setTimeInMillis(missed - dayMillis);
        AttendanceLoadManager.ATTEN_STARTUP_CALENDAR = calendar;

        final var ctor = AttendanceScheduler.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        final var scheduler = ctor.newInstance();
        final var expected = new AttendanceScheduler.Window(missed, oldCutoff, dayMillis, null);
        final var window = AttendanceScheduler.class.getDeclaredField("window");
        window.setAccessible(true);
        window.set(scheduler, expected);
        final var running = AttendanceScheduler.class.getDeclaredField("running");
        running.setAccessible(true);
        running.setBoolean(scheduler, true);
        final var advance = AttendanceScheduler.class.getDeclaredMethod("advanceDay", AttendanceScheduler.Window.class);
        advance.setAccessible(true);
        advance.invoke(scheduler, expected);

        try (var sql = admin.createStatement();
                var rows = sql.executeQuery("SELECT startup_info FROM attendance_startup WHERE id=1")) {
            check(rows.next(), "write the recovered attendance boundary");
            final long stored = rows.getTimestamp(1).getTime();
            check(stored >= today && stored == AttendanceLoadManager.attendanceDayMillis(),
                    "persist the latest KST boundary after missing multiple days");
            final var next = (AttendanceScheduler.Window) window.get(scheduler);
            check(next.day() == stored + dayMillis, "next deadline follows the committed boundary");
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
