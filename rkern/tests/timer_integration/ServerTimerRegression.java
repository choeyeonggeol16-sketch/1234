import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import l1j.server.server.model.L1UltimateBattle;
import l1j.server.server.model.gametime.CL2Time;
import l1j.server.server.model.gametime.L2DateTime;
import l1j.server.server.model.gametime.L2TimeListener;
import l1j.server.server.model.world.CWorldWork;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.network.thread.CServerTimer;
import l1j.server.server.task.COwnedWork;

public final class ServerTimerRegression {
    private static final int WORKERS = 2;
    private static final int QUEUE_LIMIT = 16;
    private static int checks;

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }

    private static Object field(Object target, String name) throws ReflectiveOperationException {
        final Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void cancelDistant(CIOThreadCallback cio, COwnedWork world) throws Exception {
        final Object shard = ((Object[]) field(cio, "shards"))[0];
        final Object wheel = field(shard, "threadLocalTimer");
        final ArrayList<CServerTimer> timers = new ArrayList<>();
        final Random order = new Random(1901L);
        for (int i = 0; i < 128; i++) {
            timers.add(world.scheduleRequired(() -> {
                throw new AssertionError("Long deadline fired early");
            }, 60_000L + order.nextInt(60_000)));
        }
        Collections.shuffle(timers, order);
        for (int i = 0; i < timers.size(); i++) {
            timers.get(i).cancel();
            synchronized (wheel) {
                final int count = (int) field(wheel, "distantSize");
                check(count == timers.size() - i - 1, "distant cancellation removes the node immediately");
                final Object[] heap = (Object[]) field(wheel, "distant");
                for (int j = 0; j < count; j++) {
                    final Object entry = heap[j];
                    check((int) field(field(entry, "payload"), "distantIndex") == j, "indexed timer position");
                    if (j > 0) {
                        final long child = (long) field(entry, "dueMs");
                        final long parent = (long) field(heap[(j - 1) >>> 1], "dueMs");
                        check(child >= parent, "min-heap order after arbitrary cancellation");
                    }
                }
            }
        }
    }

    private static void retryOnSaturation(COwnedWork world, COwnedWork session, COwnedWork persistence) throws Exception {
        final CountDownLatch occupied = new CountDownLatch(WORKERS);
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch entered = new CountDownLatch(1);
        final AtomicInteger executions = new AtomicInteger();
        final AtomicInteger callbackShard = new AtomicInteger(-2);
        final Runnable pinned = () -> {
            occupied.countDown();
            try { release.await(); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        };
        for (int i = 0; i < WORKERS; i++) world.execute(pinned);
        try {
            check(occupied.await(2L, TimeUnit.SECONDS), "instant worker occupied");
            final Runnable unused = () -> {};
            for (int i = 0; i < QUEUE_LIMIT; i++) check(world.tryExecute(unused), "bounded queue accepts capacity");
            check(!world.tryExecute(unused), "full world queue rejects");
            final CountDownLatch isolated = new CountDownLatch(2);
            session.execute(isolated::countDown);
            persistence.execute(isolated::countDown);
            check(isolated.await(2L, TimeUnit.SECONDS), "world saturation leaves session and storage responsive");
            final CountDownLatch signals = new CountDownLatch(3);
            final CServerTimer signal = CServerTimer.fixedDelaySignal(signals::countDown, 1L, 50L);
            check(signal != null, "CIO signal registered");
            try {
                final CServerTimer timer = world.scheduleRequired(() -> {
                    callbackShard.set(CIOThreadCallback.currentShardIndex());
                    executions.incrementAndGet();
                    entered.countDown();
                }, 1L);
                check(signals.await(3L, TimeUnit.SECONDS), "CIO keeps ticking while workers are saturated");
                check(executions.get() == 0 && !timer.isCancelled(), "saturation leaves the deadline pending");
            } finally {
                signal.cancel();
            }
        } finally {
            release.countDown();
        }
        check(entered.await(3L, TimeUnit.SECONDS), "deadline retried after capacity becomes available");
        check(callbackShard.get() == -1 && executions.get() == 1, "world callback runs once off CIO");
    }

    private static void cancelArenaCountdown() throws Exception {
        final L1UltimateBattle battle = new L1UltimateBattle();
        battle.setUbId(4);
        battle.start();
        final Object countdown = field(battle, "battleTimer");
        check(battle.isActive() && !battle.isNowUb() && countdown != null, "arena opens before the first round");
        battle.start();
        check(field(battle, "battleTimer") == countdown, "arena rejects duplicate countdowns");
        final CServerTimer deadline = (CServerTimer) field(countdown, "deadline");
        battle.stop();
        check(!battle.isActive() && deadline.isCancelled(), "arena stop cancels the pending phase");
        ((Runnable) countdown).run();
        check(field(battle, "battleTimer") == null && !battle.isNowUb(), "stale arena phase cannot rearm");
    }

    private static void worldClockLifecycle() throws Exception {
        CL2Time.prepare();
        CL2Time initial = CL2Time.getInstance();
        var firstTick = new CountDownLatch(1);
        var oldCalls = new AtomicInteger();
        CL2Time.addRealListener(new L2TimeListener() {
            @Override public void onSecondChanged(L2DateTime time) {
                oldCalls.incrementAndGet();
                firstTick.countDown();
            }
        }, Calendar.SECOND);
        CL2Time.start();
        CServerTimer initialTimer = (CServerTimer) field(initial, "timer");
        CL2Time.start();
        check(field(initial, "timer") == initialTimer, "duplicate clock start keeps one timer");
        check(firstTick.await(4L, TimeUnit.SECONDS), "clock runs through the actual server timer");
        CL2Time.stop();
        check(initialTimer.isCancelled() && CL2Time.getInstance() == null, "stop cancels and retires the clock");
        int stopped = oldCalls.get();

        CL2Time.prepare();
        var restartedTick = new CountDownLatch(1);
        CL2Time.addRealListener(new L2TimeListener() {
            @Override public void onSecondChanged(L2DateTime time) { restartedTick.countDown(); }
        }, Calendar.SECOND);
        CL2Time.start();
        check(restartedTick.await(4L, TimeUnit.SECONDS), "new clock dispatches after restart");
        check(oldCalls.get() == stopped, "restart drops prior clock listeners");
        CL2Time.stop();
    }

    public static void main(String[] args) throws Exception {
        final CIOThreadCallback cio = CIOThreadCallback.getInstance();
        final COwnedWork world = new COwnedWork("TimerRegressionWorld", WORKERS, QUEUE_LIMIT, 128);
        final COwnedWork session = new COwnedWork("TimerRegressionSession", 1, 4, 4);
        final COwnedWork persistence = new COwnedWork("TimerRegressionPersistence", 1, 4, 4);
        final COwnedWork[] owners = {world, session, persistence};
        try {
            cancelDistant(cio, world);
            retryOnSaturation(world, session, persistence);
            cancelArenaCountdown();
            worldClockLifecycle();
        } finally {
            CL2Time.stop();
            CWorldWork.stopAccepting();
            for (COwnedWork owner : owners) owner.stopAccepting();
            cio.shutdown();
            check(cio.awaitTermination(5L, TimeUnit.SECONDS), "CIO timer owners terminated");
            check(CWorldWork.awaitDrained(5L, TimeUnit.SECONDS), "world clock workers drained");
            for (COwnedWork owner : owners)
                check(owner.awaitDrained(5L, TimeUnit.SECONDS), "accepted work drained");
        }
        System.out.println("PASS Server timer regression (" + checks + " checks)");
    }
}
