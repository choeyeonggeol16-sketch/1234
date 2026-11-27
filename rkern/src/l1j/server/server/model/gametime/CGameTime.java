package l1j.server.server.model.gametime;

import java.time.LocalDate;
import java.util.Calendar;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.model.network.thread.CServerTimer;
import l1j.server.server.model.world.CWorldWork;
import l1j.server.server.model.world.instance.CInstantZoneManager;
import l1j.server.server.model.world.trap.L1WorldTraps;

/** Owns the game timeline and dispatches game and real calendar boundaries. */
public final class CGameTime {
    private static final Logger LOG = Logger.getLogger(CGameTime.class.getName());
    static final long GAME_ORIGIN_MS = 1_057_233_600_000L;
    private static final long LEGACY_ERA_SECONDS = 1L << 32;
    private static final long STEP_MS = 500L;
    private static final long STEP_NS = TimeUnit.MILLISECONDS.toNanos(STEP_MS);
    private static final int SECONDS_PER_STEP = 3;

    private static volatile CGameTime instance;

    private final long anchorNanos;
    private final long anchorGameSeconds;
    private final long phaseNanos;
    private final ClockEvents gameEvents = new ClockEvents(0);
    private final ClockEvents realEvents = new ClockEvents(CDateTime.KST_OFFSET);

    private CServerTimer timer;
    private volatile boolean retired;
    private long previousGame;
    private long previousReal;
    private long previousMaintenance;

    CGameTime(long wallMillis, long monotonicNanos) {
        anchorNanos = monotonicNanos;
        anchorGameSeconds = gameSecondsAt(wallMillis);
        phaseNanos = TimeUnit.MILLISECONDS.toNanos(Math.floorMod(wallMillis - GAME_ORIGIN_MS, STEP_MS));
    }

    static long gameSecondsAt(long wallMillis) {
        // Keep the active wire-clock era as the calendar phase. Unfold it as a
        // long so future sign changes on the wire cannot shift gameplay hours.
        return Math.floorDiv(wallMillis - GAME_ORIGIN_MS, STEP_MS) * SECONDS_PER_STEP - LEGACY_ERA_SECONDS;
    }

    long timeAt(long nowNanos) {
        long elapsed = Math.max(0, nowNanos - anchorNanos);
        long steps = elapsed / STEP_NS + (elapsed % STEP_NS + phaseNanos) / STEP_NS;
        return anchorGameSeconds + steps * SECONDS_PER_STEP;
    }

    public static long getTime() {
        CGameTime clock = instance;
        return clock == null ? gameSecondsAt(System.currentTimeMillis()) : clock.timeAt(System.nanoTime());
    }

    public static int getWireTime() { return (int) getTime(); }

    public static int getSecondOfDay() { return Math.floorMod(getTime(), CDateTime.SECONDS_PER_DAY); }

    public static boolean isNight() { return isNight(getSecondOfDay()); }

    static boolean isNight(int secondOfDay) {
        return secondOfDay < 6 * 3600 || secondOfDay >= 18 * 3600;
    }

    public static CDateTime getGameDateTime() { return new CDateTime(getTime(), 0); }

    public static CDateTime getDateTime() {
        return new CDateTime(Math.floorDiv(System.currentTimeMillis(), 1000), CDateTime.KST_OFFSET);
    }

    public static Calendar getRealTimeCalendar() {
        return CDateTime.kstCalendarAt(System.currentTimeMillis());
    }

    public static CGameTime getInstance() { return instance; }

    public static synchronized void prepare() {
        if (instance == null) instance = new CGameTime(System.currentTimeMillis(), System.nanoTime());
    }

    public static synchronized void start() {
        prepare();
        CGameTime clock = instance;
        if (clock.timer != null) return;
        long now = System.nanoTime();
        clock.previousGame = clock.timeAt(now);
        clock.previousMaintenance = now;
        clock.previousReal = Math.floorDiv(System.currentTimeMillis(), 1000);
        clock.timer = CWorldWork.scheduleWithFixedDelay(clock::timerExpired, STEP_MS, STEP_MS);
        if (clock.timer == null) throw new IllegalStateException("World clock timer rejected");
    }

    public static synchronized void stop() {
        CGameTime clock = instance;
        if (clock == null) return;
        clock.retired = true;
        instance = null;
        if (clock.timer != null) clock.timer.cancel();
        clock.timer = null;
        clock.gameEvents.clear();
        clock.realEvents.clear();
    }

    public static void addRealListener(CTimeListener listener, int field) {
        CGameTime clock = instance;
        if (clock == null) throw new IllegalStateException("World clock has not been prepared");
        clock.addListener(clock.realEvents, listener, field);
    }

    public static void removeRealListener(CTimeListener listener, int field) {
        CGameTime clock = instance;
        if (clock != null) clock.realEvents.listeners(field).remove(listener);
    }

    public void addGameListener(CTimeListener listener, int field) {
        addListener(gameEvents, listener, field);
    }

    public void removeGameListener(CTimeListener listener, int field) {
        gameEvents.listeners(field).remove(listener);
    }

    private void addListener(ClockEvents events, CTimeListener listener, int field) {
        synchronized (CGameTime.class) {
            if (retired) return;
            events.listeners(field).addIfAbsent(Objects.requireNonNull(listener));
        }
    }

    private void timerExpired() {
        if (retired) return;
        try {
            long now = System.nanoTime();
            long game = timeAt(now);
            long real = Math.floorDiv(System.currentTimeMillis(), 1000);
            long beforeGame = previousGame;
            long beforeReal = previousReal;
            previousGame = game;
            gameEvents.advance(beforeGame, game);
            if (!retired && now - previousMaintenance >= TimeUnit.SECONDS.toNanos(1)) {
                previousMaintenance = now;
                maintainWorld();
            }
            if (real > beforeReal) {
                previousReal = real;
                realEvents.advance(beforeReal, real);
            }
        } catch (RuntimeException | LinkageError failure) {
            LOG.log(Level.SEVERE, "World clock timer failed", failure);
        }
    }

    private static void maintainWorld() {
        try {
            CInstantZoneManager.reapWorld();
        } catch (RuntimeException | LinkageError failure) {
            LOG.log(Level.WARNING, "Instant zone cleanup tick failed", failure);
        }
        try {
            L1WorldTraps.retryRespawns();
        } catch (RuntimeException | LinkageError failure) {
            LOG.log(Level.WARNING, "Trap timer retry failed", failure);
        }
    }

    final class ClockEvents {
        private final int offset;
        private final CopyOnWriteArrayList<CTimeListener> months = new CopyOnWriteArrayList<>();
        private final CopyOnWriteArrayList<CTimeListener> days = new CopyOnWriteArrayList<>();
        private final CopyOnWriteArrayList<CTimeListener> hours = new CopyOnWriteArrayList<>();
        private final CopyOnWriteArrayList<CTimeListener> minutes = new CopyOnWriteArrayList<>();
        private final CopyOnWriteArrayList<CTimeListener> seconds = new CopyOnWriteArrayList<>();

        ClockEvents(int offset) { this.offset = offset; }

        CopyOnWriteArrayList<CTimeListener> listeners(int field) {
            return switch (field) {
                case Calendar.MONTH -> months;
                case Calendar.DAY_OF_MONTH -> days;
                case Calendar.HOUR_OF_DAY -> hours;
                case Calendar.MINUTE -> minutes;
                case Calendar.SECOND -> seconds;
                default -> throw new IllegalArgumentException("Unsupported clock field " + field);
            };
        }

        void advance(long previous, long current) {
            if (retired || current <= previous) return;
            long before = previous + offset;
            long after = current + offset;
            long previousDay = Math.floorDiv(before, CDateTime.SECONDS_PER_DAY);
            long currentDay = Math.floorDiv(after, CDateTime.SECONDS_PER_DAY);
            boolean day = currentDay != previousDay && !days.isEmpty();
            boolean month = currentDay != previousDay && !months.isEmpty() && differentMonth(previousDay, currentDay);
            boolean hour = !hours.isEmpty() && Math.floorDiv(after, 3600) != Math.floorDiv(before, 3600);
            boolean minute = !minutes.isEmpty() && Math.floorDiv(after, 60) != Math.floorDiv(before, 60);
            boolean second = !seconds.isEmpty();
            CDateTime time = month || day || hour || minute || second ? new CDateTime(current, offset) : null;
            if (month) notify(months, time, Calendar.MONTH);
            if (day) notify(days, time, Calendar.DAY_OF_MONTH);
            if (hour) notify(hours, time, Calendar.HOUR_OF_DAY);
            if (minute) notify(minutes, time, Calendar.MINUTE);
            if (second) notify(seconds, time, Calendar.SECOND);
        }

        private void notify(CopyOnWriteArrayList<CTimeListener> group, CDateTime time, int field) {
            for (CTimeListener listener : group) {
                if (retired) return;
                try {
                    switch (field) {
                        case Calendar.MONTH -> listener.onMonthChanged(time);
                        case Calendar.DAY_OF_MONTH -> listener.onDayChanged(time);
                        case Calendar.HOUR_OF_DAY -> listener.onHourChanged(time);
                        case Calendar.MINUTE -> listener.onMinuteChanged(time);
                        case Calendar.SECOND -> listener.onSecondChanged(time);
                        default -> throw new AssertionError(field);
                    }
                } catch (RuntimeException | LinkageError failure) {
                    LOG.log(Level.SEVERE, "World clock listener failed for field " + field, failure);
                }
            }
        }

        private void clear() {
            months.clear();
            days.clear();
            hours.clear();
            minutes.clear();
            seconds.clear();
        }
    }

    private static boolean differentMonth(long before, long after) {
        LocalDate previous = LocalDate.ofEpochDay(before);
        LocalDate current = LocalDate.ofEpochDay(after);
        return previous.getYear() != current.getYear() || previous.getMonthValue() != current.getMonthValue();
    }
}
