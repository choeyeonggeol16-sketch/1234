import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import l1j.server.server.ThreadPool;
import l1j.server.server.model.L1UltimateBattle;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.network.thread.CServerTimer;

public final class ServerTimerRegression {
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

    private static void cancelDistant(CIOThreadCallback cio) throws Exception {
        final Object shard = ((Object[]) field(cio, "shards"))[0];
        final Object wheel = field(shard, "threadLocalTimer");
        final ArrayList<CServerTimer> timers = new ArrayList<>();
        final Random order = new Random(1901L);
        for (int i = 0; i < 128; i++) {
            timers.add(CServerTimer.onceRequired(() -> {
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

    private static void retryOnSaturation(ThreadPool workers) throws Exception {
        final ThreadPoolExecutor pool = (ThreadPoolExecutor) field(workers, "_instantPool");
        pool.setCorePoolSize(1);
        pool.setMaximumPoolSize(1);
        final CountDownLatch occupied = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch entered = new CountDownLatch(1);
        final AtomicInteger executions = new AtomicInteger();
        final AtomicInteger callbackShard = new AtomicInteger(-2);
        workers.execute(() -> {
            occupied.countDown();
            try { release.await(); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        });
        try {
            check(occupied.await(2L, TimeUnit.SECONDS), "instant worker occupied");
            final Runnable unused = () -> {};
            while (pool.getQueue().offer(unused)) { }
            final CountDownLatch signals = new CountDownLatch(3);
            final CServerTimer signal = CServerTimer.fixedDelaySignal(signals::countDown, 1L, 50L);
            check(signal != null, "CIO signal registered");
            final CServerTimer timer = CServerTimer.onceRequired(() -> {
                callbackShard.set(CIOThreadCallback.currentShardIndex());
                executions.incrementAndGet();
                entered.countDown();
            }, 1L);
            check(signals.await(3L, TimeUnit.SECONDS), "CIO keeps ticking while workers are saturated");
            check(executions.get() == 0 && !timer.isCancelled(), "saturation leaves the deadline pending");
            signal.cancel();
        } finally {
            pool.getQueue().clear();
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

    public static void main(String[] args) throws Exception {
        final CIOThreadCallback cio = CIOThreadCallback.getInstance();
        final ThreadPool workers = ThreadPool.getInstance();
        try {
            cancelDistant(cio);
            retryOnSaturation(workers);
            cancelArenaCountdown();
        } finally {
            cio.shutdown();
            try { check(cio.awaitTermination(5L, TimeUnit.SECONDS), "CIO timer owners terminated"); }
            finally { workers.shutdown(); }
        }
        System.out.println("PASS Server timer regression (" + checks + " checks)");
    }
}
