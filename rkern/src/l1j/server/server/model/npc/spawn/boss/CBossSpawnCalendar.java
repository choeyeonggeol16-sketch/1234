package l1j.server.server.model.npc.spawn.boss;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.BitSet;
import java.util.HexFormat;
import java.util.Objects;

import l1j.server.server.shared.GameRandom;

/** Resolve a boss's configured HH:mm bases independently of jitter, timers and persistence. */
public final class CBossSpawnCalendar {
    private static final long MINUTE_MS = 60_000L;
    private static final long RECOVERY_GRACE_MS = 60L * MINUTE_MS;
    private static final int ALL_DAYS = 0b111_1111;

    private CBossSpawnCalendar() {}

    /** Canonical schedule identity; the definition's database key is tracked separately. */
    public static String fingerprint(CNpcSpawnDefine definition, ZoneId zone) {
        final Schedule schedule = Schedule.of(definition);
        final String encoded = "boss-calendar-v1|" + Objects.requireNonNull(zone).normalized().getId()
                + "|" + schedule.days + "|" + schedule.jitterMax + "|" + schedule.minutes;
        try {
            final byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(encoded.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    /** Return the earliest configured base strictly after the given time, or -1. */
    public static long nextSlot(CNpcSpawnDefine definition, long after, ZoneId zone) {
        return nextSlot(Schedule.of(definition), after, Objects.requireNonNull(zone));
    }

    private static long nextSlot(Schedule schedule, long after, ZoneId zone) {
        if (schedule.days == 0 || schedule.minutes.isEmpty()) return -1L;

        final Instant threshold = Instant.ofEpochMilli(after);
        LocalDate date = threshold.atZone(zone).toLocalDate();
        for (int examined = 0; examined < 8; date = date.plusDays(1)) {
            // A civil date may be skipped entirely when a zone crosses the date line.
            if (!date.atStartOfDay(zone).toLocalDate().equals(date)) continue;
            examined++;
            if (!schedule.includes(date)) continue;

            Instant best = null;
            for (int minute = schedule.minutes.nextSetBit(0); minute >= 0;
                    minute = schedule.minutes.nextSetBit(minute + 1)) {
                final Instant target = atSlot(date, minute, zone);
                if (target.isAfter(threshold) && (best == null || target.isBefore(best))) best = target;
            }
            if (best != null) {
                try { return best.toEpochMilli(); }
                catch (ArithmeticException outsideEpochMillis) { return -1L; }
            }
        }
        return -1L;
    }

    /** Roll once for a new slot. Persistence owns the rolled timestamp thereafter. */
    public static long due(CNpcSpawnDefine definition, long slot) {
        if (definition == null || slot == -1L) return -1L;
        final long max = jitterMax(definition);
        if (max == 0L) return slot;
        if (slot > Long.MAX_VALUE - max) return -1L;
        return slot + GameRandom.nextLong(1L, max + 1L);
    }

    /** Recover a future roll, or an overdue roll before the next base after its due time and within one hour. */
    public static boolean isRecoverable(CNpcSpawnDefine definition, long slot, long due, long now, ZoneId zone) {
        if (slot == -1L || due < slot) return false;
        final Schedule schedule = Schedule.of(definition);
        final long offset = due - slot;
        if (offset < 0L || offset > schedule.jitterMax || (offset == 0L && schedule.jitterMax != 0L)
                || !isConfiguredSlot(schedule, Instant.ofEpochMilli(slot), Objects.requireNonNull(zone))) return false;
        if (due >= now) return true;
        final long lateness = now - due;
        if (lateness < 0L || lateness > RECOVERY_GRACE_MS) return false;
        final long following = nextSlot(schedule, due, zone);
        return following == -1L || now < following;
    }

    private static boolean isConfiguredSlot(Schedule schedule, Instant base, ZoneId zone) {
        final LocalDate date = base.atZone(zone).toLocalDate();
        if (matches(schedule, base, date, zone)) return true;
        // A forward shift near midnight can resolve a previous day's wall
        // time on the following civil date without changing its weekday rule.
        return matches(schedule, base, date.minusDays(1), zone);
    }

    private static boolean matches(Schedule schedule, Instant base, LocalDate date, ZoneId zone) {
        if (!schedule.includes(date) || !date.atStartOfDay(zone).toLocalDate().equals(date)) return false;
        for (int minute = schedule.minutes.nextSetBit(0); minute >= 0;
                minute = schedule.minutes.nextSetBit(minute + 1)) {
            if (atSlot(date, minute, zone).equals(base)) return true;
        }
        return false;
    }

    private static Instant atSlot(LocalDate date, int minute, ZoneId zone) {
        // Match Calendar's later offset on overlap and shift through gaps.
        return date.atTime(minute / 60, minute % 60).atZone(zone).withLaterOffsetAtOverlap().toInstant();
    }

    private static long jitterMax(CNpcSpawnDefine definition) {
        return Math.max(0, definition.getnonespawntime()) * MINUTE_MS;
    }

    private record Schedule(int days, BitSet minutes, long jitterMax) {
        boolean includes(LocalDate date) {
            final int day = 1 << (date.getDayOfWeek().getValue() - 1);
            return (days & day) != 0;
        }

        static Schedule of(CNpcSpawnDefine definition) {
            final BitSet minutes = new BitSet(24 * 60);
            if (definition == null) return new Schedule(0, minutes, 0L);
            final int[][] configured = definition.getTime();
            if (configured != null) {
                for (int[] time : configured) {
                    if (time == null || time.length < 2) continue;
                    final int hour = time[0], minute = time[1];
                    if (hour >= 0 && hour < 24 && minute >= 0 && minute < 60)
                        minutes.set(hour * 60 + minute);
                }
            }
            return new Schedule(weekdays(definition.getYoil()), minutes, CBossSpawnCalendar.jitterMax(definition));
        }
    }

    private static int weekdays(String[] tokens) {
        if (tokens == null) return 0;
        int mask = 0;
        for (String token : tokens) {
            if (token == null) continue;
            mask |= switch (token.trim()) {
                case "월" -> 0b000_0001;
                case "화" -> 0b000_0010;
                case "수" -> 0b000_0100;
                case "목" -> 0b000_1000;
                case "금" -> 0b001_0000;
                case "토" -> 0b010_0000;
                case "일" -> 0b100_0000;
                case "전체" -> ALL_DAYS;
                default -> 0;
            };
        }
        return mask;
    }
}
