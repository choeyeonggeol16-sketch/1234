package l1j.server.server.model.Attendance;

import java.util.Calendar;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.gametime.CDateTime;
import l1j.server.server.model.gametime.CGameTime;
import l1j.server.server.model.gametime.CTimeListener;
import l1j.server.server.model.network.thread.CServerTimer;
import l1j.server.server.model.world.CWorldWork;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_ATTENDANCE_USER_DATA_EXTEND;

public final class AttendanceScheduler implements CTimeListener {
    private static final Logger LOG = Logger.getLogger(AttendanceScheduler.class.getName());
    private static final AttendanceScheduler INSTANCE = new AttendanceScheduler();
    private static final long SECOND = TimeUnit.SECONDS.toNanos(1);

    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
    private final Set<Session> retries = ConcurrentHashMap.newKeySet();
    private volatile Window window;
    private volatile boolean running;
    private boolean dayQueued;
    private boolean retryQueued;

    private AttendanceScheduler() {}

    public static AttendanceScheduler getInstance() { return INSTANCE; }

    public synchronized void run() {
        if (running) return;
        window = nextWindow(System.currentTimeMillis(), System.nanoTime());
        running = true;
        CGameTime.addRealListener(this, Calendar.SECOND);
    }

    public synchronized void dispose() {
        if (!running) return;
        running = false;
        CGameTime.removeRealListener(this, Calendar.SECOND);
        sessions.forEach((account, session) -> session.cancelTimer());
        sessions.clear();
        retries.clear();
    }

    private record Window(long day, long cutoffNanos) {}

    private static Window nextWindow(long wall, long nano) {
        final long day = nextResetMillis(wall, AttendanceLoadManager.ATTEN_RESET_PERIOD_SECOND);
        return new Window(day, nano + TimeUnit.MILLISECONDS.toNanos(day - wall));
    }

    static long nextResetMillis(long wall, int resetSeconds) {
        final Calendar local = CGameTime.getRealTimeCalendar();
        local.setTimeInMillis(wall);
        final long period = resetSeconds * 1000L;
        if (period <= 0L) throw new IllegalStateException("Invalid attendance reset period");
        final long elapsed = ((local.get(Calendar.HOUR_OF_DAY) * 60L + local.get(Calendar.MINUTE)) * 60L
                + local.get(Calendar.SECOND)) * 1000L + local.get(Calendar.MILLISECOND);
        return wall + period - elapsed % period;
    }

    @Override
    public synchronized void onSecondChanged(CDateTime time) {
        if (!running) return;
        final Window expected = window;
        if (!dayQueued && (AttendanceLoadManager.attendanceDayMillis() == 0L
                || time.getSeconds() * 1000L >= expected.day())) {
            dayQueued = CWorldWork.tryExecute(() -> advanceDay(expected));
        }
        if (!retryQueued && !retries.isEmpty()) retryQueued = CWorldWork.tryExecute(this::retrySessions);
    }

    private void advanceDay(Window expected) {
        try {
            if (!running || window != expected) return;
            if (AttendanceLoadManager.attendanceDayMillis() == 0L) {
                AttendanceLoadManager.loadAttendanceStartupCalendar();
            }
            final long now = System.currentTimeMillis();
            if (now < expected.day()) return;
            if (AttendanceLoadManager.attendanceDayMillis() < expected.day()) {
                AttendanceLoadManager.updateServerStartupInfo(expected.day());
            }
            // The old cutoff stays available to sessions until they have credited both sides.
            window = nextWindow(System.currentTimeMillis(), System.nanoTime());
            sessions.forEach((account, session) -> refresh(session));
        } catch (RuntimeException failure) {
            LOG.log(Level.WARNING, "Attendance day update failed; retrying", failure);
        } finally {
            synchronized (this) { dayQueued = false; }
        }
    }

    void refreshConfiguration() {
        sessions.forEach((account, session) -> refresh(session));
    }

    private void retrySessions() {
        try {
            retries.forEach(this::refresh);
        } finally {
            synchronized (this) { retryQueued = false; }
        }
    }

    public boolean register(L1PcInstance pc, SC_ATTENDANCE_USER_DATA_EXTEND data) {
        final String account = pc.getAccountName();
        if (!running || account == null || account.isEmpty()) return false;
        synchronized (data) {
            if (!running || pc.getAttendanceData() != data || pc.isLogoutStarted()) return false;
            final Session session = new Session(account, pc, data);
            final Session previous = sessions.put(account, session);
            if (previous != null) {
                previous.cancelTimer();
                retries.remove(previous);
            }
            refresh(session);
            return true;
        }
    }

    public void unregister(L1PcInstance pc, SC_ATTENDANCE_USER_DATA_EXTEND data) {
        final String account = pc.getAccountName();
        if (account == null) return;
        final Session session = sessions.get(account);
        if (session != null && session.pc == pc && session.data == data) {
            synchronized (data) {
                sessions.remove(account, session);
                retries.remove(session);
                session.cancelTimer();
            }
        }
    }

    // Call before and after an eligibility change so neither side borrows the other's time.
    public void settle(L1PcInstance pc) {
        final String account = pc.getAccountName();
        if (account == null) return;
        final Session session = sessions.get(account);
        if (session != null && session.pc == pc) refresh(session);
    }

    private void refresh(Session session) {
        synchronized (session.data) {
            if (!session.current()) {
                sessions.remove(session.account, session);
                retries.remove(session);
                session.cancelTimer();
                return;
            }
            try {
                session.credit(System.nanoTime());
                session.captureEligibility();
                if (session.arm()) retries.remove(session);
                else retries.add(session);
            } catch (RuntimeException failure) {
                session.captureEligibility();
                session.cancelTimer();
                retries.add(session);
                LOG.log(Level.WARNING, "Attendance update failed for character " + session.pc.getId(), failure);
            }
        }
    }

    private final class Session {
        private final String account;
        private final L1PcInstance pc;
        private final SC_ATTENDANCE_USER_DATA_EXTEND data;
        private final int stamp;
        private final long epoch;
        private Window period = window;
        private long checkpoint = System.nanoTime();
        private long normal;
        private long cafe;
        private long afterNormal;
        private long afterCafe;
        private boolean eligible;
        private boolean pcCafe;
        private volatile CServerTimer timer;
        private long due;
        private long generation;

        Session(String account, L1PcInstance pc, SC_ATTENDANCE_USER_DATA_EXTEND data) {
            this.account = account;
            this.pc = pc;
            this.data = data;
            stamp = pc.getSessionStamp();
            epoch = pc.getCacheDSessionEpoch();
            captureEligibility();
        }

        boolean current() {
            return running && sessions.get(account) == this && pc.getAttendanceData() == data
                    && pc.getSessionStamp() == stamp && pc.getCacheDSessionEpoch() == epoch;
        }

        void captureEligibility() {
            eligible = !pc.isLogoutStarted() && !pc.is무인상점() && pc.getOnlineStatus() == 1;
            pcCafe = pc.PC방_버프;
        }

        private void accrue(long until) {
            final long elapsed = Math.max(0L, until - checkpoint);
            final long before = Math.min(elapsed, Math.max(0L, period.cutoffNanos() - checkpoint));
            checkpoint = until;
            if (!eligible) return;
            normal += before;
            afterNormal += elapsed - before;
            if (pcCafe) {
                cafe += before;
                afterCafe += elapsed - before;
            }
        }

        void credit(long now) {
            final Window latest = window;
            accrue(now);
            if (period != latest) {
                flushTime();
                data.advanceElapsedDay(pc, AttendanceLoadManager.attendanceDayMillis());
                period = latest;
                normal = afterNormal;
                cafe = afterCafe;
                afterNormal = afterCafe = 0L;
            }
            flushTime();
        }

        private void flushTime() {
            final int normalSeconds = (int) Math.min(Integer.MAX_VALUE, normal / SECOND);
            final int cafeSeconds = (int) Math.min(Integer.MAX_VALUE, cafe / SECOND);
            normal -= normalSeconds * SECOND;
            cafe -= cafeSeconds * SECOND;
            data.applyElapsedTime(pc, normalSeconds, cafeSeconds);
        }

        boolean arm() {
            final long normalDelay = eligible ? delay(data.secondsUntilCompletion(false), normal) : Long.MAX_VALUE;
            final long cafeDelay = eligible && pcCafe ? delay(data.secondsUntilCompletion(true), cafe) : Long.MAX_VALUE;
            final long delay = Math.min(normalDelay, cafeDelay);
            final long target = delay == Long.MAX_VALUE ? 0L : checkpoint + delay;
            if (target == 0L || target > period.cutoffNanos()) {
                cancelTimer();
                return true;
            }
            if (timer != null && !timer.isCancelled() && due == target) return true;
            cancelTimer();
            due = target;
            final long token = ++generation;
            timer = CWorldWork.schedule(() -> {
                synchronized (data) {
                    if (token != generation) return;
                    timer = null;
                    refresh(this);
                }
            }, Math.max(1L, TimeUnit.NANOSECONDS.toMillis(target - System.nanoTime() + 999_999L)));
            return timer != null;
        }

        void cancelTimer() {
            final CServerTimer old = timer;
            if (old != null) old.cancel();
        }
    }

    private static long delay(int seconds, long remainder) {
        return seconds < 0 ? Long.MAX_VALUE : Math.max(0L, seconds * SECOND - remainder);
    }
}
