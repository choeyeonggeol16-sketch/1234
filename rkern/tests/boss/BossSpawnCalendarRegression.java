import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import l1j.server.server.model.npc.spawn.boss.CBossSpawnCalendar;
import l1j.server.server.model.npc.spawn.boss.CNpcSpawnDefine;

public final class BossSpawnCalendarRegression {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    private static final long MINUTE = 60_000L;

    private BossSpawnCalendarRegression() {}

    public static void main(String[] args) {
        checkDates();
        checkFingerprint();
        checkJitterAndRecovery();
        System.out.println("PASS boss calendar: dates, DST, fingerprint, jitter, and recovery");
    }

    private static void checkDates() {
        final CNpcSpawnDefine morning = boss(new String[]{" 월 ", "월", "wrong"}, new int[][]{
                {10, 15}, {9, 30}, {10, 15}, null, {-2, 10}, {17, 60}, {12}
        }, 0);
        eq(at("2026-11-23T00:30:00Z"), next(morning, "2026-11-23T00:29:59.999Z", SEOUL));
        eq(at("2026-11-23T01:15:00Z"), next(morning, "2026-11-23T00:30:00Z", SEOUL));
        eq(at("2026-11-30T00:30:00Z"), next(morning, "2026-11-23T01:15:00Z", SEOUL));
        eq(at("2026-11-23T14:30:00Z"), next(morning, "2026-11-23T00:30:00Z", NEW_YORK));
        eq(-1L, next(boss(new String[]{"Mon"}, new int[][]{{8, 0}}, 0),
                "2026-11-23T00:30:00Z", SEOUL));
        eq(-1L, next(boss(new String[]{"전체"}, new int[][]{{24, 0}, {12, -1}}, 0),
                "2026-11-23T00:30:00Z", SEOUL));

        final CNpcSpawnDefine gap = boss(new String[]{"일"}, new int[][]{{2, 30}, {3, 12}}, 0);
        eq(at("2026-03-08T07:12:00Z"), next(gap, "2026-03-08T06:00:00Z", NEW_YORK));
        eq(at("2026-03-08T07:30:00Z"), next(gap, "2026-03-08T07:12:00Z", NEW_YORK));
        final CNpcSpawnDefine overlap = boss(new String[]{"일"}, new int[][]{{1, 30}}, 0);
        eq(at("2026-11-01T06:30:00Z"), next(overlap, "2026-11-01T05:45:00Z", NEW_YORK));
        final ZoneId nuuk = ZoneId.of("America/Nuuk");
        final CNpcSpawnDefine midnightGap = boss(new String[]{"토"}, new int[][]{{23, 30}}, 0);
        final long movedBase = at("2026-03-29T01:30:00Z");
        eq(movedBase, next(midnightGap, "2026-03-29T00:00:00Z", nuuk));
        if (!CBossSpawnCalendar.isRecoverable(midnightGap, movedBase, movedBase, movedBase + 1, nuuk))
            throw new AssertionError("A gap-normalized base must remain recoverable across midnight");
        final CNpcSpawnDefine skipped = boss(new String[]{"금"}, new int[][]{{12, 0}}, 0);
        eq(at("2012-01-05T22:00:00Z"), next(skipped, "2011-12-29T12:00:00Z", ZoneId.of("Pacific/Apia")));
    }

    private static void checkFingerprint() {
        final CNpcSpawnDefine canonical = boss(new String[]{"전체"}, new int[][]{{2, 5}, {12, 0}}, 0);
        final CNpcSpawnDefine equivalent = boss(new String[]{"토", "일", "월", "수", "수", "금", "화", "목"},
                new int[][]{{12, 0}, {2, 5}, {12, 0}, {24, 0}}, -1);
        equivalent.setNpcId(31415);
        equivalent.setMap(42);
        equivalent.setX(100);
        equivalent.setY(200);
        equivalent.setMentMessage("new presentation");
        final String hash = CBossSpawnCalendar.fingerprint(canonical, SEOUL);
        if (hash.length() != 64 || !hash.equals(CBossSpawnCalendar.fingerprint(equivalent, SEOUL)))
            throw new AssertionError("Equivalent schedule fingerprint changed");
        if (!CBossSpawnCalendar.fingerprint(canonical, ZoneOffset.UTC)
                .equals(CBossSpawnCalendar.fingerprint(equivalent, ZoneId.of("UTC"))))
            throw new AssertionError("Equivalent fixed zones should normalize");
        changed(hash, canonical, NEW_YORK);
        equivalent.setnonespawntime(1);
        changed(hash, equivalent, SEOUL);
        equivalent.setnonespawntime(0);
        equivalent.setTime(new int[][]{{2, 5}, {12, 1}});
        changed(hash, equivalent, SEOUL);
        equivalent.setTime(canonical.getTime());
        equivalent.setYoil(new String[]{"월"});
        changed(hash, equivalent, SEOUL);
    }

    private static void checkJitterAndRecovery() {
        final long slot = at("2026-11-23T01:00:00Z");
        final CNpcSpawnDefine daily = boss(new String[]{"전체"}, new int[][]{{10, 0}}, 0);
        eq(slot, CBossSpawnCalendar.due(daily, slot));
        recover(daily, slot, slot, slot - 1L, true);
        recover(daily, slot, slot, slot + 60L * MINUTE, true);
        recover(daily, slot, slot, slot + 60L * MINUTE + 1L, false);
        recover(daily, slot + 1L, slot + 1L, slot + 2L, false);
        recover(daily, slot, slot + 1L, slot, false);

        daily.setnonespawntime(2);
        for (int roll = 0; roll < 1024; roll++) {
            final long due = CBossSpawnCalendar.due(daily, slot);
            if (due <= slot || due > slot + 2L * MINUTE) throw new AssertionError("Jitter outside 1..max");
            recover(daily, slot, due, due, true);
        }
        recover(daily, slot, slot, slot, false);
        recover(daily, slot, slot + 2L * MINUTE + 1L, slot, false);
        daily.setnonespawntime(120);
        daily.setTime(new int[][]{{10, 0}, {10, 30}});
        recover(daily, slot, slot + MINUTE, slot + 30L * MINUTE - 1L, true);
        recover(daily, slot, slot + MINUTE, slot + 30L * MINUTE, false);
        recover(daily, slot, slot + 60L * MINUTE, slot + 40L * MINUTE, true);
        recover(daily, slot, slot + 60L * MINUTE, slot + 61L * MINUTE, true);
        recover(daily, slot, slot + 60L * MINUTE, slot + 120L * MINUTE + 1L, false);

        final CNpcSpawnDefine midnight = boss(new String[]{"전체"}, new int[][]{{0, 0}}, 1440);
        final long midnightBase = at("2026-11-22T15:00:00Z");
        final long followingMidnight = midnightBase + 1440L * MINUTE;
        recover(midnight, midnightBase, followingMidnight, followingMidnight + 1L, true);
    }

    private static void changed(String hash, CNpcSpawnDefine definition, ZoneId zone) {
        if (hash.equals(CBossSpawnCalendar.fingerprint(definition, zone)))
            throw new AssertionError("Relevant schedule change did not alter fingerprint");
    }

    private static void recover(CNpcSpawnDefine boss, long slot, long due, long now, boolean expected) {
        if (CBossSpawnCalendar.isRecoverable(boss, slot, due, now, SEOUL) != expected)
            throw new AssertionError("Incorrect recovery at " + Instant.ofEpochMilli(now));
    }

    private static long next(CNpcSpawnDefine boss, String after, ZoneId zone) {
        return CBossSpawnCalendar.nextSlot(boss, at(after), zone);
    }

    private static long at(String text) {
        return Instant.parse(text).toEpochMilli();
    }

    private static CNpcSpawnDefine boss(String[] days, int[][] times, int jitterMinutes) {
        final CNpcSpawnDefine boss = new CNpcSpawnDefine();
        boss.setYoil(days);
        boss.setTime(times);
        boss.setnonespawntime(jitterMinutes);
        return boss;
    }

    private static void eq(long expected, long actual) {
        if (actual != expected) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
}
