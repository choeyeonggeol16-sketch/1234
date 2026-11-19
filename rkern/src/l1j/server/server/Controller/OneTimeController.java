package l1j.server.server.Controller;

import java.util.Calendar;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.IndunSystem.ClanDungeon.AzmodanSystem;
import l1j.server.server.ThreadPool;
import l1j.server.server.datatables.ClanTable;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1World;
import l1j.server.server.model.gametime.BaseTime;
import l1j.server.server.model.gametime.RealTimeClock;
import l1j.server.server.model.gametime.TimeListener;

public final class OneTimeController implements TimeListener {
    private static final Logger LOG = Logger.getLogger(OneTimeController.class.getName());
    private static final OneTimeController INSTANCE = new OneTimeController();
    private final AtomicBoolean inFlight = new AtomicBoolean();

    private volatile boolean started;
    private volatile int epoch;
    private volatile long pendingDay = -1L;
    private volatile long completedDay = -1L;
    private volatile long retryAfterMs;

    public static void start() {
        INSTANCE.startInternal();
    }

    public static void stop() {
        INSTANCE.stopInternal();
    }

    private synchronized void startInternal() {
        if (started) return;
        final RealTimeClock clock = RealTimeClock.getInstance();
        if (clock == null) throw new IllegalStateException("RealTimeClock not initialized");
        ++epoch;
        started = true;
        clock.addListener(this, Calendar.SECOND);
        // A server started during the midnight hour still owes the day's reset.
        onSecondChanged(clock.getRealTime());
    }

    private synchronized void stopInternal() {
        if (!started) return;
        started = false;
        ++epoch;
        RealTimeClock.getInstance().removeListener(this, Calendar.SECOND);
    }

    @Override
    public void onSecondChanged(BaseTime time) {
        if (!started || time == null) return;
        final long day = (((long) time.get(Calendar.YEAR)) << 9)
                | time.get(Calendar.DAY_OF_YEAR);
        if (time.get(Calendar.HOUR_OF_DAY) == 0 && completedDay != day) {
            pendingDay = day;
        }
        final long requested = pendingDay;
        if (requested < 0L || requested == completedDay
                || System.currentTimeMillis() < retryAfterMs
                || !inFlight.compareAndSet(false, true)) return;
        final int scheduledEpoch = epoch;
        try {
            ThreadPool.getInstance().execute(() -> {
                try {
                    if (!started || epoch != scheduledEpoch || pendingDay != requested) return;
                    resetUnderDungeon();
                    completedDay = requested;
                    if (pendingDay == requested) pendingDay = -1L;
                    retryAfterMs = 0L;
                } catch (Throwable error) {
                    // The reset is idempotent; a partial reset can be retried.
                    retryAfterMs = System.currentTimeMillis() + 60_000L;
                    LOG.log(Level.SEVERE, "Clan dungeon midnight reset failed", error);
                } finally {
                    inFlight.set(false);
                }
            });
        } catch (RejectedExecutionException rejected) {
            inFlight.set(false);
            // Keep pendingDay: the existing clock retries on its next signal.
            LOG.log(Level.WARNING, "Clan midnight reset admission rejected", rejected);
        }
    }

    private void resetUnderDungeon() throws java.sql.SQLException {
        for (L1Clan clan : L1World.getInstance().getAllClans()) {
            // Persist first: a JDBC failure must leave the clan retryable.
            ClanTable.getInstance().updateUnderDungeonOrThrow(clan.getClanId(), 0);
            clan.setUnderDungeon(0);
            clan.setUnderMapid(0);
            AzmodanSystem.getInstance().removeClan(clan.getClanId());
        }
    }

    @Override public void onMonthChanged(BaseTime time) { }
    @Override public void onDayChanged(BaseTime time) { }
    @Override public void onHourChanged(BaseTime time) { }
    @Override public void onMinuteChanged(BaseTime time) { }
}
