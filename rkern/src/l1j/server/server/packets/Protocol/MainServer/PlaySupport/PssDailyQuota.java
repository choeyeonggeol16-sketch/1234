package l1j.server.server.packets.Protocol.MainServer.PlaySupport;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.ThreadLocalRandom;
import l1j.server.DatabaseFactory;

/** Persisted per-character PSS allowance. Called on the existing CacheD DB lane only. */
final class PssDailyQuota {
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    record Grant(long token, long dayStartMs, long startMs, long expiresMs, long resetAtMs) {}

    private PssDailyQuota() {}

    /** Configurable reset boundary in Korea time; all persisted timestamps are epoch milliseconds. */
    static long dayStart(long epochMs) {
        return dayStart(epochMs, PssConfig.get().resetMinuteOfDay());
    }

    static long dayStart(long epochMs, int resetMinuteOfDay) {
        var local = Instant.ofEpochMilli(epochMs).atZone(KOREA);
        LocalDate day = local.toLocalDate();
        if (local.getHour() * 60 + local.getMinute() < resetMinuteOfDay) day = day.minusDays(1);
        return day.atTime(resetMinuteOfDay / 60, resetMinuteOfDay % 60)
                .atZone(KOREA).toInstant().toEpochMilli();
    }

    static long nextReset(long startMs) {
        return Instant.ofEpochMilli(startMs).atZone(KOREA).plusDays(1)
                .toInstant().toEpochMilli();
    }

    /** Lock and rotate one row; never grant a lease before the DB COMMIT. */
    static Grant claim(int charId, long nowMs) throws SQLException {
        PssConfig.Settings settings = PssConfig.get();
        return claim(charId, nowMs, settings.dailySeconds(), settings.resetMinuteOfDay(), settings.resetNotBeforeMs());
    }

    static Grant claim(int charId, long nowMs, int dailySeconds) throws SQLException {
        return claim(charId, nowMs, dailySeconds, PssConfig.get().resetMinuteOfDay());
    }

    static Grant claim(int charId, long nowMs, int dailySeconds, int resetMinuteOfDay) throws SQLException {
        return claim(charId, nowMs, dailySeconds, resetMinuteOfDay, 0L);
    }

    static Grant claim(int charId, long nowMs, int dailySeconds, int resetMinuteOfDay,
            long resetNotBeforeMs) throws SQLException {
        final long limitMs = dailySeconds * 1000L;
        final long day = dayStart(nowMs, resetMinuteOfDay);
        try (Connection con = DatabaseFactory.getInstance().getConnection()) {
            con.setAutoCommit(false);
            try {
                ensureRow(con, charId, day);
                Row row = lock(con, charId);
                // A reset-time change must never rewind the persisted window or refund used time.
                // Only a reset boundary strictly later than the last persisted boundary rotates it.
                boolean rotate = day > row.day && day > resetNotBeforeMs;
                long persistedDay = rotate ? day : row.day;
                long used = rotate ? 0L : effectiveUsed(row, nowMs);
                long available = Math.min(limitMs - used, nextReset(day) - nowMs);
                if (available <= 0L) {
                    write(con, charId, persistedDay, used, 0L, 0L, 0L);
                    con.commit();
                    return null;
                }
                long token = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
                long stopMs = nowMs + available;
                write(con, charId, persistedDay, used, token, nowMs, stopMs);
                con.commit();
                return new Grant(token, persistedDay, nowMs, stopMs, nextReset(day));
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        }
    }

    /** Close only the matching lease; stale logout/timer callbacks cannot debit a newer lease. */
    static int finish(int charId, long day, long token, long nowMs) throws SQLException {
        PssConfig.Settings settings = PssConfig.get();
        return finish(charId, day, token, nowMs, settings.dailySeconds(), settings.resetMinuteOfDay(), settings.resetNotBeforeMs());
    }

    static int finish(int charId, long day, long token, long nowMs, int dailySeconds) throws SQLException {
        return finish(charId, day, token, nowMs, dailySeconds, PssConfig.get().resetMinuteOfDay());
    }

    static int finish(int charId, long day, long token, long nowMs,
            int dailySeconds, int resetMinuteOfDay) throws SQLException {
        return finish(charId, day, token, nowMs, dailySeconds, resetMinuteOfDay, 0L);
    }

    static int finish(int charId, long day, long token, long nowMs,
            int dailySeconds, int resetMinuteOfDay, long resetNotBeforeMs) throws SQLException {
        final long limitMs = dailySeconds * 1000L;
        try (Connection con = DatabaseFactory.getInstance().getConnection()) {
            con.setAutoCommit(false);
            try {
                ensureRow(con, charId, dayStart(nowMs, resetMinuteOfDay));
                Row row = lock(con, charId);
                if (row.day == day && token != 0L && row.token == token) {
                    long used = effectiveUsed(row, nowMs);
                    write(con, charId, day, used, 0L, 0L, 0L);
                    con.commit();
                    return secondsLeft(used, limitMs);
                }
                con.commit();
                long requestedDay = dayStart(nowMs, resetMinuteOfDay);
                return secondsLeft(requestedDay > row.day && requestedDay > resetNotBeforeMs
                        ? 0L : effectiveUsed(row, nowMs), limitMs);
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        }
    }

    static int remaining(int charId, long nowMs) throws SQLException {
        PssConfig.Settings settings = PssConfig.get();
        return remaining(charId, nowMs, settings.dailySeconds(), settings.resetMinuteOfDay(), settings.resetNotBeforeMs());
    }

    static int remaining(int charId, long nowMs, int dailySeconds) throws SQLException {
        return remaining(charId, nowMs, dailySeconds, PssConfig.get().resetMinuteOfDay());
    }

    static int remaining(int charId, long nowMs, int dailySeconds, int resetMinuteOfDay) throws SQLException {
        return remaining(charId, nowMs, dailySeconds, resetMinuteOfDay, 0L);
    }

    static int remaining(int charId, long nowMs, int dailySeconds, int resetMinuteOfDay,
            long resetNotBeforeMs) throws SQLException {
        final long limitMs = dailySeconds * 1000L;
        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT window_start_ms,used_ms,lease_token,lease_start_ms,lease_end_ms "
                     + "FROM pss_daily_usage WHERE char_id=?")) {
            ps.setInt(1, charId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return dailySeconds;
                Row row = new Row(rs.getLong(1), rs.getLong(2), rs.getLong(3),
                        rs.getLong(4), rs.getLong(5));
                long requestedDay = dayStart(nowMs, resetMinuteOfDay);
                return secondsLeft(requestedDay > row.day && requestedDay > resetNotBeforeMs
                        ? 0L : effectiveUsed(row, nowMs), limitMs);
            }
        }
    }

    /** Atomically renew a still-owned lease at reset or after a config reload. */
    static Grant renew(int charId, long oldDay, long oldToken, long nowMs,
            int dailySeconds, int resetMinuteOfDay, long resetNotBeforeMs) throws SQLException {
        final long day = dayStart(nowMs, resetMinuteOfDay);
        final long limitMs = dailySeconds * 1000L;
        try (Connection con = DatabaseFactory.getInstance().getConnection()) {
            con.setAutoCommit(false);
            try {
                Row row = lock(con, charId);
                if (oldToken == 0L || row.token != oldToken || row.day != oldDay) {
                    con.commit();
                    return null;
                }
                boolean rotate = day > row.day && day > resetNotBeforeMs;
                // An expired quota before the new reset must never become free play.
                if (rotate && row.expiresMs < day) {
                    con.commit();
                    return null;
                }
                long used = rotate
                        ? Math.max(0L, nowMs - Math.max(row.startMs, day))
                        : effectiveUsed(row, nowMs);
                long persistedDay = rotate ? day : row.day;
                long available = Math.min(limitMs - used, nextReset(day) - nowMs);
                if (available <= 0L) {
                    write(con, charId, persistedDay, used, 0L, 0L, 0L);
                    con.commit();
                    return null;
                }
                long token = ThreadLocalRandom.current().nextLong(1L, Long.MAX_VALUE);
                long expiresMs = nowMs + available;
                write(con, charId, persistedDay, used, token, nowMs, expiresMs);
                con.commit();
                return new Grant(token, persistedDay, nowMs, expiresMs, nextReset(day));
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        }
    }

    private static int secondsLeft(long usedMs, long limitMs) {
        long left = Math.max(0L, limitMs - usedMs);
        return (int) ((left + 999L) / 1000L);
    }

    private record Row(long day, long used, long token, long startMs, long expiresMs) {}

    private static long effectiveUsed(Row row, long nowMs) {
        if (row.token == 0L) return Math.max(0L, row.used);
        long elapsed = Math.max(0L, Math.min(row.expiresMs, nowMs) - row.startMs);
        return Math.max(0L, row.used) + elapsed;
    }

    private static void ensureRow(Connection con, int charId, long day) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO pss_daily_usage (char_id,window_start_ms,used_ms,lease_token,lease_start_ms,lease_end_ms) "
                + "VALUES (?, ?, 0, 0, 0, 0) ON DUPLICATE KEY UPDATE char_id=char_id")) {
            ps.setInt(1, charId);
            ps.setLong(2, day);
            ps.executeUpdate();
        }
    }

    private static Row lock(Connection con, int charId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT window_start_ms,used_ms,lease_token,lease_start_ms,lease_end_ms "
                + "FROM pss_daily_usage WHERE char_id=? FOR UPDATE")) {
            ps.setInt(1, charId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("Missing PSS quota row: " + charId);
                return new Row(rs.getLong(1), rs.getLong(2), rs.getLong(3),
                        rs.getLong(4), rs.getLong(5));
            }
        }
    }

    private static void write(Connection con, int charId, long day, long used,
            long token, long startMs, long expiresMs) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE pss_daily_usage SET window_start_ms=?,used_ms=?,lease_token=?,"
                + "lease_start_ms=?,lease_end_ms=? WHERE char_id=?")) {
            ps.setLong(1, day);
            ps.setLong(2, used);
            ps.setLong(3, token);
            ps.setLong(4, startMs);
            ps.setLong(5, expiresMs);
            ps.setInt(6, charId);
            if (ps.executeUpdate() != 1) throw new SQLException("PSS quota update failed: " + charId);
        }
    }
}
