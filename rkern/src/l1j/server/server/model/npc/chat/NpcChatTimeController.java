package l1j.server.server.model.npc.chat;

import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.Config;
import l1j.server.server.model.world.CWorldWork;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.gametime.L2DateTime;
import l1j.server.server.model.gametime.CL2Time;
import l1j.server.server.model.gametime.L2TimeListener;

public final class NpcChatTimeController implements Runnable, L2TimeListener {
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
        final CL2Time clock = CL2Time.getInstance();
        if (clock == null) throw new IllegalStateException("CL2Time not initialized");
        ++generation;
        started = true;
        completedMinute = -1L;
        // Existing clock drives retry signals; world iteration is never run on it.
        CL2Time.addRealListener(this, Calendar.SECOND);
        onSecondChanged(CL2Time.getDateTime());
    }

    public synchronized void stop() {
        if (!started) return;
        started = false;
        ++generation;
        CL2Time.removeRealListener(this, Calendar.SECOND);
    }

    @Override
    public void onSecondChanged(L2DateTime time) {
        if (!started) return;
        final long minute = System.currentTimeMillis() / 60_000L;
        if (completedMinute == minute || !inFlight.compareAndSet(false, true)) return;
        final int epoch = generation;
        try {
            CWorldWork.execute(() -> {
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


            for (L1NpcInstance npc : CWorld.getInstance().getNpcsByTemplateId(npcId)) {
                npc.startChat(
                        L1NpcInstance.CHAT_TIMING_GAME_TIME);
            }
        }
    }
}