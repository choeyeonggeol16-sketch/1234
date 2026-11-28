package l1j.server.server.model.Attendance;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Timestamp;
import java.util.UUID;

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
                    try { exercise(admin); }
                    finally { field.set(null, null); }
                }
            } finally {
                admin.setCatalog("mysql");
                ddl.executeUpdate("DROP DATABASE " + schema);
            }
        }
        System.out.println("PASS attendance durable progress, rollback, claim dedup and stale snapshot replay");
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
        check(group.creditEligibleTime(60), "first threshold");
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
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
