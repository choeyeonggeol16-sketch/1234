package l1j.server.server.model.npc.spawn.boss;

import java.sql.SQLException;
import java.time.Clock;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BooleanSupplier;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.model.npc.spawn.boss.CNpcRespawnStore.Entry;

/** One durable calendar cursor and at most one armed world timer per boss placement. */
final class CRespawnTimer {
    interface Store {
        Map<CBossSpawnKey, Entry> loadAll() throws SQLException;
        boolean reconcile(Map<CBossSpawnKey, Entry> previous, Map<CBossSpawnKey, Entry> changes) throws SQLException;
        boolean dispatch(CBossSpawnKey key, Entry previous, Entry next, BooleanSupplier publish) throws SQLException;
    }

    interface Timers {
        Runnable schedule(Runnable callback, long delayMillis);
    }

    private static final Logger LOG = Logger.getLogger(CRespawnTimer.class.getName());
    private static final long DB_RETRY_MS = 60_000L;
    private static final long SPAWN_RETRY_MS = 600_000L;
    private final Clock clock;
    private final ZoneId zone;
    private final Store store;
    private final Timers timers;
    private final Function<CNpcSpawnDefine, Runnable> publish;
    private final BiConsumer<CNpcSpawnDefine, Long> jitterNotice;
    private final ReentrantReadWriteLock lifecycle = new ReentrantReadWriteLock(true);
    private final AtomicBoolean retryArming = new AtomicBoolean();
    private Map<CBossSpawnKey, Slot> slots = Map.of();
    // Kept across reloads if the socket to the DB failed at COMMIT after world entry.
    private final Map<CBossSpawnKey, Published> unconfirmed = new ConcurrentHashMap<>();

    private record Published(Entry previous, Entry next, Runnable announcement) {}

    CRespawnTimer(Clock clock, Store store, Timers timers, Function<CNpcSpawnDefine, Runnable> publish) {
        this(clock, store, timers, publish, (boss, delay) -> {});
    }

    CRespawnTimer(Clock clock, Store store, Timers timers, Function<CNpcSpawnDefine, Runnable> publish,
            BiConsumer<CNpcSpawnDefine, Long> jitterNotice) {
        this.jitterNotice = jitterNotice;
        this.clock = clock;
        this.zone = clock.getZone();
        this.store = store;
        this.timers = timers;
        this.publish = publish;
    }

    void replace(List<CNpcSpawnDefine> definitions, Runnable installCatalog) throws SQLException {
        var recovered = new ArrayList<Published>();
        var notices = new ArrayList<Runnable>();
        lifecycle.writeLock().lock();
        Map<CBossSpawnKey, Slot> staged = new HashMap<>();
        try {
            Map<CBossSpawnKey, Entry> saved = store.loadAll();
            Map<CBossSpawnKey, Entry> changes = new HashMap<>();
            long now = clock.millis();
            var defined = new HashSet<CBossSpawnKey>();
            for (CNpcSpawnDefine definition : definitions) {
                var slot = new Slot(definition);
                if (!defined.add(slot.key))
                    throw new IllegalArgumentException("Duplicate boss placement " + slot.key);
                Entry previous = saved.get(slot.key);
                Published pending = unconfirmed.get(slot.key);
                boolean awaitingCommit = pending != null && pending.previous.equals(previous);
                if (previous != null && previous.fingerprint().equals(slot.signature)
                        && (awaitingCommit || CBossSpawnCalendar.isRecoverable(definition, previous.slot(), previous.due(), now, zone))) {
                    slot.entry = previous;
                } else {
                    long base = CBossSpawnCalendar.nextSlot(definition, now, zone);
                    if (base < 0) {
                        if (slots.containsKey(slot.key))
                            throw new IllegalArgumentException("Invalid replacement calendar for " + slot.key);
                        LOG.warning("Boss placement has no valid day/time; waiting for GM reload: " + slot.key);
                        continue;
                    }
                    slot.entry = slot.roll(base);
                    changes.put(slot.key, slot.entry);
                }
                staged.put(slot.key, slot);
                slot.arm(slot.entry.due() - now);
            }
            for (CBossSpawnKey key : saved.keySet())
                if (!defined.contains(key)) changes.put(key, null);
            if (!store.reconcile(saved, changes))
                throw new SQLException("Boss schedules changed concurrently; retry reload");
            installCatalog.run();
            Map<CBossSpawnKey, Slot> retired = slots;
            slots = staged;
            staged = retired;
            for (Slot slot : slots.values()) {
                Runnable notice = slot.notice();
                if (notice != null) notices.add(notice);
            }
            for (var item : unconfirmed.entrySet()) {
                Slot slot = slots.get(item.getKey());
                Published prior = item.getValue();
                if (slot != null && prior.previous.equals(slot.entry)) continue;
                if (unconfirmed.remove(item.getKey(), prior) && slot != null && prior.next.equals(saved.get(item.getKey())))
                    recovered.add(prior);
            }
        } finally {
            try { for (Slot slot : staged.values()) slot.cancel(); }
            finally { lifecycle.writeLock().unlock(); }
        }
        recovered.forEach(CRespawnTimer::announce);
        notices.forEach(Runnable::run);
    }

    void stop() {
        lifecycle.writeLock().lock();
        try {
            for (Slot slot : slots.values()) slot.cancel();
            slots = Map.of();
            // Releases captured monster/session presentation state while retaining only
            // the cursor needed to resolve a possible lost commit on a same-JVM restart.
            unconfirmed.replaceAll((key, result) -> new Published(result.previous, result.next, () -> {}));
        } finally { lifecycle.writeLock().unlock(); }
    }

    // The existing world timer domain provides one recovery tick for the whole catalog.
    // Normally it only checks this bit. A saturated wheel must not strand a durable row.
    void repairUnarmed() {
        if (!retryArming.getAndSet(false)) return;
        lifecycle.readLock().lock();
        try {
            for (Slot slot : slots.values()) {
                synchronized (slot) {
                    if (!slot.retired && slot.alarm == null)
                        slot.tryArm(Math.max(1L, slot.retryArmAt - clock.millis()));
                }
            }
        } finally { lifecycle.readLock().unlock(); }
    }

    private static void announce(Published result) {
        if (result == null) return;
        try { result.announcement.run(); }
        catch (RuntimeException e) { LOG.log(Level.WARNING, "Boss announcement failed after publication", e); }
    }

    private final class Slot {
        final CNpcSpawnDefine definition;
        final CBossSpawnKey key;
        final String signature;
        Entry entry;
        Alarm alarm;
        boolean retired;
        long retryArmAt;

        Slot(CNpcSpawnDefine definition) {
            this.definition = definition;
            key = CBossSpawnKey.of(definition);
            signature = CBossSpawnCalendar.fingerprint(definition, zone);
        }

        Entry next(long after) {
            long base = CBossSpawnCalendar.nextSlot(definition, after, zone);
            if (base < 0L) throw new IllegalArgumentException("Invalid boss calendar for " + key);
            return roll(base);
        }

        Entry roll(long base) {
            return new Entry(signature, base, CBossSpawnCalendar.due(definition, base));
        }

        void arm(long delay) {
            Alarm nextAlarm = new Alarm(this);
            nextAlarm.cancel = timers.schedule(nextAlarm, Math.max(1L, delay));
            alarm = nextAlarm;
        }

        void tryArm(long delay) {
            try { arm(delay); }
            catch (java.util.concurrent.RejectedExecutionException failure) {
                retryArmAt = clock.millis() + Math.max(1L, delay);
                retryArming.set(true);
                LOG.log(Level.WARNING, "World timer busy for boss " + key + "; maintenance will retry", failure);
            }
        }

        Runnable notice() {
            if (entry.due() <= entry.slot()) return null;
            long due = entry.due();
            return () -> {
                try { jitterNotice.accept(definition, due - clock.millis()); }
                catch (RuntimeException e) { LOG.log(Level.WARNING, "GM boss schedule notification failed", e); }
            };
        }

        void cancel() {
            if (alarm != null) {
                alarm.cancel.run();
                alarm = null;
            }
        }

        void fire(Alarm fired) {
            if (alarm != fired) return;
            alarm = null;
            // The one-shot normally returns its permit after the callback. Release it
            // before booking its successor, including when this delivery was early.
            fired.cancel.run();
            long now = clock.millis();
            if (entry.due() > now) {
                tryArm(entry.due() - now);
                return;
            }

            try {
                Published prior = unconfirmed.get(key);
                Entry following = prior != null && prior.previous.equals(entry) ? prior.next : next(now);
                boolean expired = !CBossSpawnCalendar.isRecoverable(definition, entry.slot(), entry.due(), now, zone);
                boolean[] matched = {false};
                boolean committed = store.dispatch(key, entry, following, () -> {
                    matched[0] = true;
                    if (prior != null && prior.previous.equals(entry)) return true;
                    if (expired) return true;
                    Runnable announcement = publish.apply(definition);
                    if (announcement == null) return false;
                    unconfirmed.put(key, new Published(entry, following, announcement));
                    return true;
                });
                if (committed) {
                    Published result = unconfirmed.remove(key);
                    entry = following;
                    fired.completed = result;
                    fired.notice = notice();
                    tryArm(entry.due() - clock.millis());
                } else if (matched[0]) {
                    tryArm(SPAWN_RETRY_MS);
                } else {
                    // Another writer or a lost commit acknowledgement. Only the durable winner is authoritative.
                    Entry actual = store.loadAll().get(key);
                    if (actual == null || !signature.equals(actual.fingerprint())) {
                        retired = true;
                        unconfirmed.remove(key);
                        LOG.warning("Boss definition superseded in database: " + key);
                        return;
                    }
                    Published result = unconfirmed.remove(key);
                    entry = actual;
                    if (result != null && result.next.equals(actual)) fired.completed = result;
                    tryArm(entry.due() - clock.millis());
                }
            } catch (SQLException | RuntimeException failure) {
                LOG.log(Level.WARNING, "Unable to complete boss occurrence " + key + "; retrying", failure);
                tryArm(DB_RETRY_MS);
            }
        }
    }

    private final class Alarm implements Runnable {
        private final Slot slot;
        Runnable cancel;
        Published completed;
        Runnable notice;

        Alarm(Slot slot) { this.slot = slot; }

        @Override public void run() {
            Published result;
            Runnable notify;
            lifecycle.readLock().lock();
            try {
                if (slots.get(slot.key) != slot) return;
                synchronized (slot) {
                    if (slot.alarm != this) return;
                    slot.fire(this);
                    result = completed;
                    notify = notice;
                    completed = null;
                    notice = null;
                }
            } finally { lifecycle.readLock().unlock(); }
            announce(result);
            if (notify != null) notify.run();
        }
    }
}
