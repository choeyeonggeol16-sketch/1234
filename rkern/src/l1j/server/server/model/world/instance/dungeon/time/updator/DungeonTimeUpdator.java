package l1j.server.server.model.world.instance.dungeon.time.updator;

import java.util.Calendar;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import gnu.trove.set.hash.TIntHashSet;
import l1j.server.Config;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.gametime.CDateTime;
import l1j.server.server.model.gametime.CGameTime;
import l1j.server.server.model.gametime.CTimeListener;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1WorldMap;
import l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer;
import l1j.server.server.model.network.core.broadcast.CSharedBroadcast;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.world.instance.CInstanceWork;
import l1j.server.server.model.world.instance.dungeon.time.loader.DungeonTimeInformationLoader;
import l1j.server.server.model.world.instance.dungeon.time.loader.DungeonTimeProgressLoader;

public final class DungeonTimeUpdator implements CTimeListener {
    private static final Logger LOG = Logger.getLogger(DungeonTimeUpdator.class.getName());
    private static final DungeonTimeUpdator INSTANCE = new DungeonTimeUpdator();
    private static final int KST_OFFSET_SECONDS = 9 * 3600;
    private static final int DAY_SECONDS = 24 * 3600;

    private final Object progression = new Object();
    private volatile DungeonTimeConsumer consumer;
    private boolean running;
    private boolean resetInProgress;
    private long previousSecond = Long.MIN_VALUE;
    private long lastScheduledReset = Long.MIN_VALUE;
    private Calendar pendingScheduledReset;
    private long resetRetryAfterNanos;

    private DungeonTimeUpdator() { }

    public static DungeonTimeUpdator getInstance() { return INSTANCE; }

    public static void release() { INSTANCE.dispose(); }

    public static void reload() {
        DungeonTimeInformationLoader.reload();
    }

    public synchronized void run() {
        if (running) return;
        // Startup storage recovery is handled by DungeonTimeLoadManager. A live
        // subscription considers only boundaries it observes after starting.
        previousSecond = CGameTime.getDateTime().getSeconds();
        CGameTime.addRealListener(this, Calendar.SECOND);
        consumer = new DungeonTimeConsumer();
        running = true;
    }

    public synchronized void dispose() {
        CGameTime.removeRealListener(this, Calendar.SECOND);
        running = false;
        consumer = null;
    }

    public synchronized boolean is_running() { return running; }

    public synchronized boolean do_initialize(Calendar time) {
        return submitReset((Calendar) time.clone(), false);
    }

    private boolean submitReset(Calendar time, boolean scheduled) {
        if (resetInProgress || (scheduled && resetRetryAfterNanos != 0
                && System.nanoTime() - resetRetryAfterNanos < 0)) return false;
        resetInProgress = true;
        if (running) consumer = new DungeonTimeConsumer();
        try {
            CInstanceWork.execute(() -> {
                synchronized (progression) { applyReset(time, scheduled); }
            });
            return true;
        } catch (RejectedExecutionException failure) {
            resetInProgress = false;
            LOG.log(Level.WARNING, "Dungeon reset admission rejected", failure);
            return false;
        }
    }

    private void applyReset(Calendar time, boolean scheduled) {
        boolean done = false;
        try {
            final boolean changed;
            try {
                changed = DungeonTimeProgressLoader.resetAll(time);
            } catch (RuntimeException failure) {
                LOG.log(Level.WARNING, "Dungeon reset failed before notifying players", failure);
                return;
            }
            done = true;
            if (!changed) return;
            CWorld world = CWorld.getInstance();
            for (L1PcInstance pc : world.getAllPlayers()) {
                if (pc.getNetConnection() == null) continue;
                try {
                    SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "전체 던전 시간이 초기화되었습니다.");
                    SC_SystemMessage.send(pc, "[NOTICE] All dungeon timers have been reset.");
                } catch (RuntimeException failure) {
                    LOG.log(Level.WARNING, "Dungeon reset notification failed for " + pc.getId(), failure);
                }
            }
            try {
                if (scheduled) teleportAfterReset(world);
            } catch (RuntimeException failure) {
                LOG.log(Level.WARNING, "Dungeon reset relocation failed", failure);
            }
        } finally {
            synchronized (this) {
                if (scheduled) resetRetryAfterNanos = done ? 0 : System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                if (done && scheduled) {
                    lastScheduledReset = time.getTimeInMillis() / 1000L;
                    if (pendingScheduledReset == time) pendingScheduledReset = null;
                    Config.네쥬던전초기화 = time.get(Calendar.DAY_OF_WEEK);
                }
                resetInProgress = false;
            }
        }
    }

    @Override
    public synchronized void onSecondChanged(CDateTime time) {
        if (!running) return;
        long now = time.getSeconds();
        int hour = Config.DungeonResetHour, minute = Config.DungeonResetMinute;
        if (now > previousSecond && hour >= 0 && hour < 24 && minute >= 0 && minute < 60) {
            long boundary = resetBoundary(now, hour, minute);
            if (previousSecond < boundary && boundary > lastScheduledReset) {
                Calendar due = time.getCalendar();
                due.setTimeInMillis(boundary * 1000L);
                pendingScheduledReset = due;
            }
        }
        if (now > previousSecond) previousSecond = now;
        if (pendingScheduledReset != null) submitReset(pendingScheduledReset, true);
        else if (!resetInProgress) queueTick();
    }

    // With several missed days, only the most recent scheduled reset is relevant.
    public static long resetBoundary(long now, int hour, int minute) {
        long midnight = Math.floorDiv(now + KST_OFFSET_SECONDS, DAY_SECONDS) * DAY_SECONDS - KST_OFFSET_SECONDS;
        long boundary = midnight + hour * 3600L + minute * 60L;
        return boundary > now ? boundary - DAY_SECONDS : boundary;
    }

    private void teleportAfterReset(CWorld world) {
        final String message = "던전 초기화 시간으로, 더 이용하시기 위해서는 재입장 해주십시오.";
        try (var shared = new CAsyncSendConstBuffer(SC_PacketBox.nativeGreenWriter(message),
                Integer.MIN_VALUE, Integer.MAX_VALUE)) {
            for (L1PcInstance pc : world.getAllPlayers())
                CSharedBroadcast.sendRecipientBestEffort(shared, pc);
        }
        for (int pass = 0; pass < 2; pass++) {
            for (int mapId = 53; mapId <= 54; mapId++) {
                L1Map terrain = L1WorldMap.getInstance().findMap(mapId);
                if (terrain == null) continue;
                for (L1PcInstance pc : world.getVisiblePlayers(terrain)) {
                    int stamp = pc.getSessionStamp();
                    if (!eligible(world, pc, terrain, stamp)) continue;
                    if (pass == 0) pc.start_teleport(33430, 32823, 4, pc.getHeading(), 169, true, false);
                    else pc.do_simple_teleport(33436, 32812, 4);
                    SC_SkillSound.send(pc, pc.getId(), 491);
                }
            }
        }
    }

    private static boolean eligible(CWorld world, L1PcInstance pc, L1Map terrain, int stamp) {
        var session = pc.getNetConnection();
        return session != null && session.isConnected() && session.getActiveChar() == pc && !pc.isLogoutStarted()
                && pc.getSessionStamp() == stamp && pc.getMap() == terrain && pc.isPublishedIn(terrain)
                && world.findObject(pc.getId()) == pc;
    }

    // Coalesce missed seconds. Charging all of them against today's map roster would misbill arrivals.
    private void queueTick() {
        DungeonTimeConsumer current = consumer;
        current.pending = true;
        if (current.queued) return;
        current.queued = true;
        submitTick(current);
    }

    private void submitTick(DungeonTimeConsumer current) {
        try {
            CInstanceWork.execute(current);
        } catch (RejectedExecutionException refused) {
            current.queued = false;
            LOG.log(Level.WARNING, "Dungeon time tick admission rejected", refused);
        }
    }

    private final class DungeonTimeConsumer implements Runnable {
        private final TIntHashSet visited = new TIntHashSet();
        private boolean pending;
        private boolean queued;

        @Override
        public void run() {
            synchronized (DungeonTimeUpdator.this) {
                if (consumer != this) return;
                pending = false;
            }
            try {
                synchronized (progression) {
                    if (consumer == this) update();
                }
            } finally {
                visited.clear();
                synchronized (DungeonTimeUpdator.this) {
                    queued = false;
                    if (consumer == this && pending && !resetInProgress) {
                        queued = true;
                        submitTick(this);
                    }
                }
            }
        }

        private void update() {
            var loader = DungeonTimeInformationLoader.getInstance();
            var world = CWorld.getInstance();
            for (int i = 0; i < loader.mapCount() && consumer == this; i++) {
                int mapId = loader.mapIdAt(i);
                var terrain = L1WorldMap.getInstance().findMap(mapId);
                if (terrain == null) continue;
                var info = loader.from_map_id(mapId);
                for (L1PcInstance pc : world.getVisiblePlayers(terrain)) {
                    if (consumer != this) return;
                    int stamp = pc.getSessionStamp();
                    if (eligible(world, pc, terrain, stamp) && visited.add(pc.getId())) {
                        try {
                            pc.dec_dungeon_progress(info);
                        } catch (RuntimeException failure) {
                            LOG.log(Level.WARNING, "Dungeon time update failed for " + pc.getId(), failure);
                        }
                    }
                }
            }
        }
    }
}
