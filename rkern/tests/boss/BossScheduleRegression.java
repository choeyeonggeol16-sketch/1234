package l1j.server.server.model.npc.spawn.boss;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BooleanSupplier;

import l1j.server.server.model.npc.spawn.boss.CNpcRespawnStore.Entry;

public final class BossScheduleRegression {
    private static final int BOSS_ID = 81101;
    private static final String MORNING = "2026-11-23T10:00:00Z";
    private static final String EVENING = "2026-11-23T18:00:00Z";
    private static final String TOMORROW = "2026-11-24T10:00:00Z";

    public static void main(String[] args) throws SQLException {
        schedulingAndReload();
        dormantInvalidCalendar();
        rearmAtTimerCapacity();
        repairRejectedRearm();
        restartCatchUp();
        failedPublicationAndDatabaseRetry();
        lostCommitAcknowledgement(false, false);
        lostCommitAcknowledgement(true, false);
        lostCommitAcknowledgement(false, true);
        lostCommitAcknowledgement(true, true);
        System.out.println("PASS boss scheduling, dormant calendars, timer capacity, reload/stop, restart catch-up/expiry, spawn/SQL retries, and single publication after ambiguous commit");
    }

    private static void rearmAtTimerCapacity() throws SQLException {
        var fixture = new Fixture();
        fixture.timers.limit = 1;
        fixture.install(define("boss", 10, 18));
        fixture.timers.only().deliver();
        require(fixture.timers.only().deadline == time(MORNING), "early callback can rearm with no spare timer permit");
        fixture.timers.advance(MORNING);
        require(fixture.timers.only().deadline == time(EVENING) && fixture.announcements == 1,
                "successful callback can arm the next occurrence with no spare timer permit");
    }

    private static void dormantInvalidCalendar() throws SQLException {
        var fixture = new Fixture();
        CNpcSpawnDefine valid = define("boss", 10, 18);
        CNpcSpawnDefine invalid = define("boss with invalid calendar", 28);
        CBossSpawnKey key = CBossSpawnKey.of(valid);
        fixture.install(valid);
        ManualTimers.Task active = fixture.timers.only();
        Entry existing = fixture.store.loadAll().get(key);
        expectFailure(IllegalArgumentException.class, () -> fixture.install(invalid));
        require(fixture.timers.only() == active && fixture.catalogInstalls == 1
                && existing.equals(fixture.store.loadAll().get(key)), "hot reload keeps the active definition on malformed replacement");

        fixture.scheduler.stop();
        fixture.scheduler = fixture.newScheduler();
        CNpcSpawnDefine other = define("other placement", 13);
        other.setX(33435);
        fixture.install(invalid, other);
        require(fixture.timers.only().deadline == time("2026-11-23T13:00:00Z")
                && existing.equals(fixture.store.loadAll().get(key)), "startup runs valid neighbors and keeps the dormant checkpoint");

        fixture.install(valid, other);
        require(fixture.timers.pendingCount() == 2 && existing.equals(fixture.store.loadAll().get(key)),
                "correcting an invalid calendar restores its preserved cursor");
        fixture.timers.advance(MORNING);
        require(fixture.spawnAttempts == 1 && fixture.announcements == 1, "restored occurrence publishes once");
    }

    private static void repairRejectedRearm() throws SQLException {
        var fixture = new Fixture();
        fixture.install(define("boss", 10, 18));
        ManualTimers.Task fired = fixture.timers.only();
        Map<CBossSpawnKey, Entry> initial = fixture.store.loadAll();
        fixture.failSpawn = true;
        fixture.timers.limit = 0;
        fixture.timers.advance(MORNING);
        fixture.scheduler.repairUnarmed();
        require(fixture.timers.pendingCount() == 0 && fixture.spawnAttempts == 1
                && initial.equals(fixture.store.loadAll()), "wheel rejection retains the retryable occurrence");

        fixture.timers.advance("2026-11-23T10:01:00Z");
        fixture.timers.limit = 1;
        fixture.scheduler.repairUnarmed();
        ManualTimers.Task retry = fixture.timers.only();
        fixture.scheduler.repairUnarmed();
        fired.callback.run();
        require(fixture.timers.only() == retry && retry.deadline == time("2026-11-23T10:10:00Z")
                && fixture.spawnAttempts == 1, "wheel repair rearms exactly once and preserves the failed-spawn backoff");
        fixture.timers.advance("2026-11-23T10:10:00Z");
        require(fixture.spawnAttempts == 2 && fixture.announcements == 1 && fixture.timers.only().deadline == time(EVENING),
                "repaired occurrence publishes once and returns to its calendar");
    }

    private static void schedulingAndReload() throws SQLException {
        var fixture = new Fixture();
        fixture.install(define("original", 18, 10));
        ManualTimers.Task first = fixture.timers.only();
        Map<CBossSpawnKey, Entry> initial = fixture.store.loadAll();
        require(first.deadline == time(MORNING), "calendar chooses earliest unsorted occurrence");

        first.deliver();
        ManualTimers.Task rearmed = fixture.timers.only();
        require(rearmed != first && rearmed.deadline == first.deadline, "early delivery must rearm the same occurrence");
        require(initial.equals(fixture.store.loadAll()) && fixture.spawnAttempts == 0, "early delivery must not consume a cursor");

        fixture.store.failLoad = true;
        expectFailure(SQLException.class, () -> fixture.install(define("new schedule", 11)));
        fixture.store.failReconcile = true;
        expectFailure(SQLException.class, () -> fixture.install(define("new schedule", 11)));
        require(fixture.timers.only() == rearmed && fixture.catalogInstalls == 1
                && initial.equals(fixture.store.loadAll()), "both failed reloads must leave catalog, timer, and cursor intact");

        CNpcSpawnDefine secondPlacement = define("second placement", 13);
        secondPlacement.setX(33435);
        fixture.timers.registrationsUntilReject = 1;
        expectFailure(RejectedExecutionException.class,
                () -> fixture.install(define("new schedule", 11), secondPlacement));
        expectFailure(IllegalArgumentException.class,
                () -> fixture.install(define("new schedule", 11), define("duplicate placement", 13)));
        require(fixture.timers.only() == rearmed && fixture.catalogInstalls == 1
                && initial.equals(fixture.store.loadAll()), "staged alarms must be cancelled when reload admission fails");

        fixture.install(define("renamed presentation", 10, 18));
        require(fixture.timers.only().deadline == time(MORNING) && initial.equals(fixture.store.loadAll()),
                "presentation and slot order changes preserve the durable occurrence");
        require(rearmed.cancelled, "replacement retires the prior alarm");
        ManualTimers.Task renamed = fixture.timers.only();

        fixture.install(define("new schedule", 11));
        require(fixture.timers.only().deadline == time("2026-11-23T11:00:00Z") && renamed.cancelled,
                "changed calendar must replace the occurrence");
        require(fixture.catalogInstalls == 3 && !initial.equals(fixture.store.loadAll()),
                "only successful catalogs install with the new durable cursor");
        fixture.timers.advance(MORNING);
        renamed.callback.run();
        require(fixture.spawnAttempts == 0, "late delivery of a retired occurrence must be ignored");
        fixture.timers.advance("2026-11-23T11:00:00Z");
        require(fixture.announcements == 1 && fixture.spawnAttempts == 1
                && fixture.timers.only().deadline == time("2026-11-24T11:00:00Z"),
                "replacement publishes once and arms its next daily occurrence");
        ManualTimers.Task pending = fixture.timers.only();
        fixture.scheduler.stop();
        pending.callback.run();
        require(pending.cancelled && fixture.timers.pendingCount() == 0 && fixture.spawnAttempts == 1,
                "stop cancels the next occurrence and rejects late delivery");
    }

    private static void restartCatchUp() throws SQLException {
        var fixture = new Fixture();
        CNpcSpawnDefine definition = define("boss", 10, 18);
        fixture.install(definition);
        ManualTimers.Task old = fixture.timers.only();

        fixture.restartAt("2026-11-23T10:05:00Z", definition);
        old.callback.run();
        fixture.timers.advance("2026-11-23T10:05:01Z");
        require(fixture.announcements == 1 && fixture.spawnAttempts == 1
                && fixture.timers.only().deadline == time(EVENING),
                "restart recovers a recent occurrence and durably arms the next");
        Map<CBossSpawnKey, Entry> next = fixture.store.loadAll();

        fixture.restartAt("2026-11-23T10:06:00Z", definition);
        require(next.equals(fixture.store.loadAll()) && fixture.timers.only().deadline == time(EVENING),
                "a second restart preserves the upcoming cursor");
        fixture.restartAt("2026-11-23T19:05:00Z", definition);
        require(fixture.spawnAttempts == 1 && fixture.timers.only().deadline == time(TOMORROW),
                "restart skips an overdue occurrence past the grace period");

        fixture.timers.advance(TOMORROW);
        require(fixture.spawnAttempts == 2 && fixture.announcements == 2, "regular spawning continues after catch-up expiry");
    }

    private static void failedPublicationAndDatabaseRetry() throws SQLException {
        var fixture = new Fixture();
        fixture.install(define("boss", 10, 18));
        Map<CBossSpawnKey, Entry> initial = fixture.store.loadAll();
        fixture.failSpawn = true;
        fixture.timers.advance(MORNING);
        require(fixture.spawnAttempts == 1 && fixture.announcements == 0
                && fixture.timers.only().deadline == time("2026-11-23T10:10:00Z")
                && initial.equals(fixture.store.loadAll()), "failed registration preserves cursor and retries in 10 minutes");

        fixture.store.failDispatch = true;
        fixture.timers.advance("2026-11-23T10:10:00Z");
        require(fixture.spawnAttempts == 1 && fixture.announcements == 0
                && fixture.timers.only().deadline == time("2026-11-23T10:11:00Z")
                && initial.equals(fixture.store.loadAll()), "SQL failure before publication preserves cursor and retries in one minute");

        fixture.timers.advance("2026-11-23T10:11:00Z");
        require(fixture.spawnAttempts == 2 && fixture.announcements == 1
                && fixture.timers.only().deadline == time(EVENING)
                && !initial.equals(fixture.store.loadAll()), "retry succeeds once and advances the durable cursor");
    }

    private static void lostCommitAcknowledgement(boolean committed, boolean reload) throws SQLException {
        var fixture = new Fixture();
        fixture.install(define("boss", 10, 18));
        Map<CBossSpawnKey, Entry> initial = fixture.store.loadAll();
        fixture.store.loseCommit = committed ? CommitLoss.PERSISTED : CommitLoss.ROLLED_BACK;
        fixture.timers.advance(MORNING);
        require(fixture.spawnAttempts == 1 && fixture.announcements == 0
                && fixture.timers.only().deadline == time("2026-11-23T10:01:00Z"),
                "world entry is retained but the announcement waits for durable confirmation");
        require(initial.equals(fixture.store.loadAll()) != committed, "exercise both durable outcomes of lost acknowledgement");

        if (reload) {
            ManualTimers.Task retry = fixture.timers.only();
            fixture.install(define("same calendar with new presentation", 18, 10));
            retry.callback.run();
            require(fixture.spawnAttempts == 1 && fixture.announcements == (committed ? 1 : 0),
                    "reload confirms only the actual durable winner and retires the prior retry");
        }
        fixture.timers.advance("2026-11-23T10:01:00Z");
        require(fixture.spawnAttempts == 1 && fixture.announcements == 1
                && fixture.timers.only().deadline == time(EVENING)
                && !initial.equals(fixture.store.loadAll()), "retry confirms exactly one entry and announcement");
    }

    private static CNpcSpawnDefine define(String name, int... hours) {
        CNpcSpawnDefine definition = new CNpcSpawnDefine();
        definition.setNpcId(BOSS_ID);
        definition.setX(33430);
        definition.setY(32810);
        definition.setMap(4);
        definition.setMapName("fixture map");
        definition.setMonName(name);
        definition.setYoil(new String[] {"전체"});
        int[][] configured = new int[hours.length][2];
        for (int i = 0; i < hours.length; i++) configured[i][0] = hours[i];
        definition.setTime(configured);
        return definition;
    }

    private static long time(String iso) {
        return Instant.parse(iso).toEpochMilli();
    }

    private static void require(boolean expected, String message) {
        if (!expected) throw new AssertionError(message);
    }

    private static void expectFailure(Class<? extends Exception> kind, SqlAction action) throws SQLException {
        try { action.run(); }
        catch (SQLException | RuntimeException failure) {
            if (kind.isInstance(failure)) return;
            throw failure;
        }
        throw new AssertionError("reload must report " + kind.getSimpleName());
    }

    private interface SqlAction {
        void run() throws SQLException;
    }

    private static final class Fixture {
        final TestClock clock = new TestClock();
        final MemoryStore store = new MemoryStore();
        final ManualTimers timers = new ManualTimers(clock);
        CRespawnTimer scheduler = newScheduler();
        int catalogInstalls;
        int spawnAttempts;
        int announcements;
        boolean failSpawn;

        CRespawnTimer newScheduler() {
            return new CRespawnTimer(clock, store, timers, definition -> {
                require(definition.getNpcId() == BOSS_ID, "scheduled spawn uses configured template");
                spawnAttempts++;
                if (failSpawn) {
                    failSpawn = false;
                    return null;
                }
                return () -> announcements++;
            });
        }

        void install(CNpcSpawnDefine... definitions) throws SQLException {
            scheduler.replace(List.of(definitions), () -> catalogInstalls++);
        }

        void restartAt(String timestamp, CNpcSpawnDefine definition) throws SQLException {
            scheduler.stop();
            clock.now = time(timestamp);
            scheduler = newScheduler();
            install(definition);
        }
    }

    private static final class TestClock extends Clock {
        long now = time("2026-11-23T09:59:50Z");

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) {
            if (ZoneOffset.UTC.equals(zone)) return this;
            return new Clock() {
                @Override public ZoneId getZone() { return zone; }
                @Override public Clock withZone(ZoneId nextZone) { return TestClock.this.withZone(nextZone); }
                @Override public Instant instant() { return TestClock.this.instant(); }
            };
        }
        @Override public Instant instant() { return Instant.ofEpochMilli(now); }
        @Override public long millis() { return now; }
    }

    private static final class ManualTimers implements CRespawnTimer.Timers {
        private final TestClock clock;
        private final List<Task> tasks = new ArrayList<>();
        int registrationsUntilReject = -1;
        int limit = Integer.MAX_VALUE;

        ManualTimers(TestClock clock) { this.clock = clock; }

        private static final class Task {
            final long deadline;
            final Runnable callback;
            boolean cancelled;
            boolean fired;
            boolean finished;

            Task(long deadline, Runnable callback) {
                this.deadline = deadline;
                this.callback = callback;
            }

            boolean pending() { return !cancelled && !fired; }

            void deliver() {
                require(pending(), "only a pending alarm can be delivered");
                fired = true;
                try { callback.run(); }
                finally { finished = true; }
            }
        }

        @Override public Runnable schedule(Runnable callback, long delayMillis) {
            require(delayMillis > 0, "timer API requires a positive delay");
            // Actual CIO one-shots retain the permit until callback return or cancellation.
            if (tasks.stream().filter(task -> !task.cancelled && !task.finished).count() >= limit)
                throw new RejectedExecutionException("fixture timer capacity");
            if (registrationsUntilReject == 0) {
                registrationsUntilReject = -1;
                throw new RejectedExecutionException("fixture timer capacity");
            }
            if (registrationsUntilReject > 0) registrationsUntilReject--;
            var task = new Task(clock.now + delayMillis, callback);
            tasks.add(task);
            return () -> task.cancelled = true;
        }

        private List<Task> pending() { return tasks.stream().filter(Task::pending).toList(); }
        int pendingCount() { return pending().size(); }

        Task only() {
            List<Task> pending = pending();
            require(pending.size() == 1, "expected one live alarm, found " + pending.size());
            return pending.getFirst();
        }

        void advance(String timestamp) {
            long target = time(timestamp);
            require(target >= clock.now, "virtual clock can only advance");
            for (int delivered = 0; delivered < 32; delivered++) {
                Task next = pending().stream().filter(task -> task.deadline <= target)
                        .min(Comparator.comparingLong(task -> task.deadline)).orElse(null);
                if (next == null) {
                    clock.now = target;
                    return;
                }
                clock.now = Math.max(clock.now, next.deadline);
                next.deliver();
            }
            throw new AssertionError("timer advancement exceeded callback limit");
        }
    }

    private enum CommitLoss {
        NONE, PERSISTED, ROLLED_BACK
    }

    private static final class MemoryStore implements CRespawnTimer.Store {
        private final Map<CBossSpawnKey, Entry> rows = new HashMap<>();
        boolean failLoad;
        boolean failReconcile;
        boolean failDispatch;
        CommitLoss loseCommit = CommitLoss.NONE;

        @Override public Map<CBossSpawnKey, Entry> loadAll() throws SQLException {
            if (failLoad) {
                failLoad = false;
                throw new SQLException("fixture load failure");
            }
            return Map.copyOf(rows);
        }

        @Override public boolean reconcile(Map<CBossSpawnKey, Entry> previous, Map<CBossSpawnKey, Entry> changes)
                throws SQLException {
            if (failReconcile) {
                failReconcile = false;
                throw new SQLException("fixture reconcile failure");
            }
            if (!rows.equals(previous)) return false;
            for (var change : changes.entrySet()) {
                if (change.getValue() == null) rows.remove(change.getKey());
                else rows.put(change.getKey(), change.getValue());
            }
            return true;
        }

        @Override public boolean dispatch(CBossSpawnKey key, Entry previous, Entry next, BooleanSupplier publish)
                throws SQLException {
            if (failDispatch) {
                failDispatch = false;
                throw new SQLException("fixture dispatch failure");
            }
            if (!Objects.equals(rows.get(key), previous) || !publish.getAsBoolean()) return false;
            CommitLoss outcome = loseCommit;
            loseCommit = CommitLoss.NONE;
            if (outcome != CommitLoss.ROLLED_BACK) rows.put(key, next);
            if (outcome != CommitLoss.NONE) throw new SQLException("fixture lost COMMIT response");
            return true;
        }
    }
}
