import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.network.thread.CServerTimer;
import l1j.server.server.Shutdown;
import l1j.server.server.task.TaskLane;

public final class TaskLaneLifecycleRegression {
    private static void check(boolean result, String message) {
        if (!result) throw new AssertionError(message);
    }

    private static void await(CountDownLatch latch) {
        try { check(latch.await(5, TimeUnit.SECONDS), "Timed out waiting for work"); }
        catch (InterruptedException failure) { throw new AssertionError(failure); }
    }

    private static void replaceCountdown() throws Exception {
        final var start = Shutdown.class.getDeclaredMethod("startCountdown", int.class, boolean.class);
        final var cancel = Shutdown.class.getDeclaredMethod("cancelCountdown");
        final var current = Shutdown.class.getDeclaredField("_counterInstance");
        start.setAccessible(true);
        cancel.setAccessible(true);
        current.setAccessible(true);
        start.invoke(null, 3600, false);
        final Thread old = (Thread) current.get(null);
        try {
            start.invoke(null, 3600, true);
            final Thread replacement = (Thread) current.get(null);
            check(replacement != old, "Countdown replacement");
            old.join(3000);
            check(!old.isAlive(), "Replaced countdown still running");
            cancel.invoke(null);
            replacement.join(3000);
            check(!replacement.isAlive() && current.get(null) == null, "Countdown released");
        } finally {
            cancel.invoke(null);
        }
    }

    public static void main(String[] args) throws Exception {
        final CIOThreadCallback cio = CIOThreadCallback.getInstance();
        final AtomicInteger unexpected = new AtomicInteger();
        final AtomicInteger executed = new AtomicInteger();
        final int shard = cio.shardCount() - 1;
        final Runnable mustNotRun = unexpected::incrementAndGet;
        final CountDownLatch allowDrain = new CountDownLatch(1);
        final CountDownLatch draining = new CountDownLatch(1);
        final CountDownLatch eventStarted = new CountDownLatch(1);
        final CountDownLatch interrupted = new CountDownLatch(1);
        try {
            replaceCountdown();
            final int before = TaskLane.SESSION.pendingTimers();
            final ArrayList<CServerTimer> timers = new ArrayList<>(4096);
            for (int i = 0; i < 4096; i++) {
                final CServerTimer timer = CServerTimer.onceOnShard(TaskLane.SESSION, shard, mustNotRun, 60_000 + i);
                check(timer != null, "Long deadline admission");
                timers.add(timer);
            }
            check(TaskLane.SESSION.pendingTimers() == before + 4096, "Timer budget tracks heap size");
            for (int i = timers.size() - 1; i >= 0; i--) {
                check(timers.get(i).cancel(), "Timer cancelled");
                check(!timers.get(i).cancel(), "Timer cancelled twice");
            }
            check(TaskLane.SESSION.pendingTimers() == before, "All cancelled timers returned their budget");

            final CountDownLatch ranOnOwner = new CountDownLatch(1);
            check(CServerTimer.onceOnShard(TaskLane.SESSION, shard, () -> {
                if (CIOThreadCallback.currentShardIndex() >= 0 || !TaskLane.isWorkerThread()) unexpected.incrementAndGet();
                ranOnOwner.countDown();
            }, 1) != null, "Separate timer and callback ownership");
            await(ranOnOwner);

            final Future<?> event = TaskLane.INSTANCE.startEvent(() -> {
                eventStarted.countDown();
                try { Thread.sleep(60_000); }
                catch (InterruptedException stop) { interrupted.countDown(); }
            });
            await(eventStarted);
            check(event.cancel(false), "Logical cancellation succeeds");
            check(TaskLane.INSTANCE.pendingWork() == 1, "A live event still owns its slot");
            final Future<?> pendingEvent = TaskLane.INSTANCE.scheduleBlocking(mustNotRun, 60_000);
            for (int i = 0; i < 512; i++) TaskLane.WORLD.execute(executed::incrementAndGet);
            TaskLane.PERSISTENCE.execute(() -> {
                try {
                    TaskLane.awaitAll(1, TimeUnit.MILLISECONDS);
                    unexpected.incrementAndGet();
                } catch (IllegalStateException expected) {
                    draining.countDown();
                } catch (InterruptedException failure) {
                    unexpected.incrementAndGet();
                }
                await(allowDrain);
            });
            await(draining);

            final CountDownLatch published = new CountDownLatch(8);
            final CountDownLatch raceStart = new CountDownLatch(1);
            final Thread[] producers = new Thread[8];
            for (int i = 0; i < producers.length; i++) {
                producers[i] = Thread.ofPlatform().daemon().start(() -> {
                    for (int j = 0; j < 256; j++) {
                        if (j == 1) {
                            published.countDown();
                            await(raceStart);
                        }
                        final CServerTimer timer = CServerTimer.onceOnShard(TaskLane.PERSISTENCE, shard, mustNotRun, 60_000);
                        if (timer != null && (j & 1) != 0) timer.cancel();
                    }
                });
            }
            await(published);
            check(TaskLane.PERSISTENCE.pendingTimers() >= producers.length, "Live deadlines before stop barrier");
            raceStart.countDown();
            TaskLane.stopAll();
            check(!TaskLane.PERSISTENCE.tryExecute(mustNotRun), "Closed lane rejects new work");
            check(TaskLane.WORLD.schedule(mustNotRun, 1) == null, "Closed lane rejects deadlines");
            check(!TaskLane.awaitAll(10, TimeUnit.MILLISECONDS), "Shutdown accounts for live callback body");
            await(interrupted);
            check(pendingEvent.isCancelled(), "Pending event cancelled on shutdown");
            allowDrain.countDown();
            for (Thread producer : producers) {
                producer.join(3000);
                check(!producer.isAlive(), "Timer registration and stop deadlocked");
            }
        } finally {
            allowDrain.countDown();
            TaskLane.stopAll();
            cio.shutdown();
            check(cio.awaitTermination(5, TimeUnit.SECONDS), "CIO did not terminate");
            check(TaskLane.awaitAll(5, TimeUnit.SECONDS), "Task lanes did not terminate");
        }
        check(executed.get() == 512 && unexpected.get() == 0, "Exactly accepted work completed");
        for (TaskLane lane : TaskLane.values()) {
            check(lane.pendingWork() == 0, lane + " still retains work");
            check(lane.pendingTimers() == 0, lane + " still retains timers");
        }
        check(ManagementFactory.getThreadMXBean().findDeadlockedThreads() == null, "JVM deadlock");
        System.out.println("PASS 4096 distant cancellations, 2048 stop races, 512 accepted jobs and zero retained work");
    }
}
