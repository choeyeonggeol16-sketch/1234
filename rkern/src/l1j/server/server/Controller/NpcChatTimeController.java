package l1j.server.server.Controller;

import java.util.Calendar;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.TimeZone;

import l1j.server.Config;
import l1j.server.server.ThreadPool;
import l1j.server.server.model.gametime.BaseTime;
import l1j.server.server.model.gametime.RealTimeClock;
import l1j.server.server.model.gametime.TimeListener;
import l1j.server.server.datatables.NpcChatTable;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.templates.L1NpcChat;

public final class NpcChatTimeController implements Runnable, TimeListener {
    private static final Logger LOG = Logger.getLogger(NpcChatTimeController.class.getName());
    private final AtomicBoolean inFlight = new AtomicBoolean();
    private volatile boolean started;
    private volatile int generation;
    private volatile long completedMinute = -1L;

    public static final int SLEEP_TIME = 60000;

    private static NpcChatTimeController _instance;

    public static synchronized NpcChatTimeController getInstance() {
        if (_instance == null) _instance = new NpcChatTimeController();
        return _instance;
    }

    public synchronized void start() {
        if (started) return;
        final RealTimeClock clock = RealTimeClock.getInstance();
        if (clock == null) throw new IllegalStateException("RealTimeClock not initialized");
        ++generation;
        started = true;
        completedMinute = -1L;
        // Existing clock drives retry signals; world iteration is never run on it.
        clock.addListener(this, Calendar.SECOND);
        onSecondChanged(clock.getRealTime());
    }

    public synchronized void stop() {
        if (!started) return;
        started = false;
        ++generation;
        RealTimeClock.getInstance().removeListener(this, Calendar.SECOND);
    }

    @Override
    public void onSecondChanged(BaseTime time) {
        if (!started) return;
        final long minute = System.currentTimeMillis() / 60_000L;
        if (completedMinute == minute || !inFlight.compareAndSet(false, true)) return;
        final int epoch = generation;
        try {
            ThreadPool.getInstance().execute(() -> {
                try {
                    if (!started || generation != epoch) return;
                    run();
                    // A world scan may cross the minute boundary; finish only
                    // the minute that admitted it, so the next minute is not lost.
                    completedMinute = minute;
                } finally {
                    inFlight.set(false);
                }
            });
        } catch (RejectedExecutionException rejected) {
            inFlight.set(false);
            // Retain the unfinished minute; retry on the next clock second.
            LOG.log(Level.WARNING, "Npc chat tick admission rejected", rejected);
        }
    }

    @Override public void onMonthChanged(BaseTime time) { }
    @Override public void onDayChanged(BaseTime time) { }
    @Override public void onHourChanged(BaseTime time) { }
    @Override public void onMinuteChanged(BaseTime time) { }

    @Override
    public void run() {
        try {
            checkNpcChatTime();
        } catch (Throwable e) {
            LOG.log(Level.SEVERE, "Npc chat timer failed", e);
            throw e instanceof RuntimeException ? (RuntimeException) e : new IllegalStateException(e);
        }
    }

    private void checkNpcChatTime() {

        final Calendar now =
                Calendar.getInstance(
                        TimeZone.getTimeZone(Config.TIME_ZONE));

        final int nowTime =
                now.get(Calendar.HOUR_OF_DAY) * 100
                + now.get(Calendar.MINUTE);

        for (L1NpcChat npcChat
                : NpcChatTable.getInstance().getAllGameTime()) {

            if (npcChat == null) {
                continue;
            }

            if (npcChat.getGameTime() != nowTime) {
                continue;
            }

            final int npcId =
                    npcChat.getNpcId();


            for (L1Object obj
                    : L1World.getInstance().getObject()) {

                if (!(obj instanceof L1NpcInstance)) {
                    continue;
                }

                final L1NpcInstance npc =
                        (L1NpcInstance) obj;

                if (npc.getNpcTemplate().get_npcId() != npcId) {
                    continue;
                }

                npc.startChat(
                        L1NpcInstance.CHAT_TIMING_GAME_TIME);
            }
        }
    }
}