import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.network.thread.CServerTimer;
import l1j.server.server.model.world.instance.CInstanceEvents;
import l1j.server.server.Shutdown;
import l1j.server.server.task.COwnedWork;

public final class OwnedWorkLifecycleRegression {
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

    private static void instanceEvents(AtomicInteger unexpected) throws Exception {
        final Runnable mustNotRun = unexpected::incrementAndGet;
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch interrupted = new CountDownLatch(1);
        final CountDownLatch finishInterruptedEvent = new CountDownLatch(1);

        boolean stopped = false;
        try {
            CInstanceEvents.schedule(() -> {
                check(CInstanceEvents.isEventThread() && CIOThreadCallback.currentShardIndex() < 0,
                        "Delayed event runs under its dedicated owner");
                try {
                    CInstanceEvents.awaitTermination(10L, TimeUnit.MILLISECONDS);
                    throw new AssertionError("Event owner attempted to await itself");
                } catch (IllegalStateException expected) {
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(failure);
                }
            }, 1L).get(5L, TimeUnit.SECONDS);
            check(CInstanceEvents.awaitTermination(5L, TimeUnit.SECONDS), "Completed event released its slot");

            final Future<?> cancelledDeadline = CInstanceEvents.schedule(mustNotRun, 60_000L);
            check(cancelledDeadline.cancel(false), "Pending event deadline can be cancelled");
            final Future<?> event = CInstanceEvents.start(() -> {
                if (!CInstanceEvents.isEventThread() || CIOThreadCallback.currentShardIndex() >= 0)
                    unexpected.incrementAndGet();
                started.countDown();
                try {
                    Thread.sleep(60_000L);
                } catch (InterruptedException stop) {
                    interrupted.countDown();
                    await(finishInterruptedEvent);
                }
            });
            await(started);
            check(event.cancel(false), "Logical event cancellation succeeds");
            check(interrupted.getCount() == 1L, "Logical cancellation does not interrupt the event");
            check(CInstanceEvents.pendingEvents() == 1, "A live event still owns its slot");
            final Future<?> pendingEvent = CInstanceEvents.schedule(mustNotRun, 60_000L);

            final CountDownLatch cioTicks = new CountDownLatch(2);
            final CServerTimer signal = CServerTimer.fixedDelaySignal(() -> {
                if (CIOThreadCallback.currentShardIndex() != 0) unexpected.incrementAndGet();
                cioTicks.countDown();
            }, 1L, 50L);
            check(signal != null, "Direct CIO signal was admitted");
            try {
                await(cioTicks);
                check(interrupted.getCount() == 1L, "Sleeping event did not block the CIO timer");
            } finally {
                signal.cancel();
            }

            CInstanceEvents.stop();
            stopped = true;
            await(interrupted);
            check(cancelledDeadline.isCancelled() && pendingEvent.isCancelled(), "Stop cancels pending event deadlines");
            check(!CInstanceEvents.awaitTermination(10L, TimeUnit.MILLISECONDS),
                    "Termination waits for the interrupted event body to exit");
            try {
                CInstanceEvents.start(mustNotRun);
                throw new AssertionError("Stopped event owner admitted new work");
            } catch (RejectedExecutionException expected) {
            }
        } finally {
            finishInterruptedEvent.countDown();
            if (!stopped) CInstanceEvents.stop();
            check(CInstanceEvents.awaitTermination(5L, TimeUnit.SECONDS), "Instance events did not terminate");
        }
        check(CInstanceEvents.pendingEvents() == 0, "Instance event owner still retains work");
    }

    private static void reuseWorker(COwnedWork owner) throws InterruptedException {
        final AtomicReference<Thread> worker = new AtomicReference<>();
        Thread firstWorker = null;
        for (int burst = 0; burst < 8; burst++) {
            final CountDownLatch ran = new CountDownLatch(1);
            owner.execute(() -> {
                worker.set(Thread.currentThread());
                ran.countDown();
            });
            await(ran);
            final long idleBy = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L);
            while (owner.pendingWork() != 0 && System.nanoTime() - idleBy < 0L)
                Thread.sleep(1L);
            check(owner.pendingWork() == 0, "Worker drained its burst");
            if (burst == 0) firstWorker = worker.get();
            else check(worker.get() == firstWorker, "Short idle gaps reuse the same worker");
            Thread.sleep(25L);
        }
        owner.stopAccepting();
        check(owner.awaitDrained(5L, TimeUnit.SECONDS), "Stopped worker drained");
        firstWorker.join(5_000L);
        check(!firstWorker.isAlive(), "Idle worker exits when its owner stops");
    }

    public static void main(String[] args) throws Exception {
        final CIOThreadCallback cio = CIOThreadCallback.getInstance();
        final AtomicInteger unexpected = new AtomicInteger();
        final AtomicInteger executed = new AtomicInteger();
        final COwnedWork burstOwner = new COwnedWork("LifecycleReuse", 1, 16, 16);
        final COwnedWork session = new COwnedWork("LifecycleSession", 1, 16, 4096);
        final COwnedWork world = new COwnedWork("LifecycleWorld", 2, 512, 16);
        final COwnedWork persistence = new COwnedWork("LifecyclePersistence", 1, 16, 4096);
        final COwnedWork[] owners = {burstOwner, session, world, persistence};
        final int shard = cio.shardCount() - 1;
        final Runnable mustNotRun = unexpected::incrementAndGet;
        final CountDownLatch allowDrain = new CountDownLatch(1);
        final CountDownLatch draining = new CountDownLatch(1);
        final CountDownLatch raceStart = new CountDownLatch(1);
        try {
            replaceCountdown();
            instanceEvents(unexpected);
            reuseWorker(burstOwner);
            final int before = session.pendingTimers();
            final ArrayList<CServerTimer> timers = new ArrayList<>(4096);
            for (int i = 0; i < 4096; i++) {
                final CServerTimer timer = CServerTimer.onceOnShard(session, shard, mustNotRun, 60_000 + i);
                check(timer != null, "Long deadline admission");
                timers.add(timer);
            }
            check(session.pendingTimers() == before + 4096, "Timer budget tracks heap size");
            for (int i = timers.size() - 1; i >= 0; i--) {
                check(timers.get(i).cancel(), "Timer cancelled");
                check(!timers.get(i).cancel(), "Timer cancelled twice");
            }
            check(session.pendingTimers() == before, "All cancelled timers returned their budget");

            final CountDownLatch ranOnOwner = new CountDownLatch(1);
            check(CServerTimer.onceOnShard(session, shard, () -> {
                if (CIOThreadCallback.currentShardIndex() >= 0 || !session.isWorkerThread()) unexpected.incrementAndGet();
                ranOnOwner.countDown();
            }, 1) != null, "Separate timer and callback ownership");
            await(ranOnOwner);

            for (int i = 0; i < 512; i++) world.execute(executed::incrementAndGet);
            persistence.execute(() -> {
                try {
                    persistence.awaitDrained(1, TimeUnit.MILLISECONDS);
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
            final Thread[] producers = new Thread[8];
            for (int i = 0; i < producers.length; i++) {
                producers[i] = Thread.ofPlatform().daemon().start(() -> {
                    for (int j = 0; j < 256; j++) {
                        if (j == 1) {
                            published.countDown();
                            await(raceStart);
                        }
                        final CServerTimer timer = CServerTimer.onceOnShard(persistence, shard, mustNotRun, 60_000);
                        if (timer != null && (j & 1) != 0) timer.cancel();
                    }
                });
            }
            await(published);
            check(persistence.pendingTimers() >= producers.length, "Live deadlines before stop barrier");
            raceStart.countDown();
            for (COwnedWork owner : owners) owner.stopAccepting();
            check(!persistence.tryExecute(mustNotRun), "Closed owner rejects new work");
            check(world.schedule(mustNotRun, 1) == null, "Closed owner rejects deadlines");
            check(!persistence.awaitDrained(10, TimeUnit.MILLISECONDS), "Shutdown accounts for live callback body");
            allowDrain.countDown();
            for (Thread producer : producers) {
                producer.join(3000);
                check(!producer.isAlive(), "Timer registration and stop deadlocked");
            }
        } finally {
            raceStart.countDown();
            allowDrain.countDown();
            for (COwnedWork owner : owners) owner.stopAccepting();
            cio.shutdown();
            check(cio.awaitTermination(5, TimeUnit.SECONDS), "CIO did not terminate");
            for (COwnedWork owner : owners)
                check(owner.awaitDrained(5, TimeUnit.SECONDS), "Owned work did not drain");
        }
        check(executed.get() == 512 && unexpected.get() == 0, "Exactly accepted work completed");
        for (COwnedWork owner : owners) {
            check(owner.pendingWork() == 0, "Stopped owner still retains work");
            check(owner.pendingTimers() == 0, "Stopped owner still retains timers");
        }
        check(ManagementFactory.getThreadMXBean().findDeadlockedThreads() == null, "JVM deadlock");
        System.out.println("PASS worker reuse, instance event lifecycle, direct CIO signals, 4096 distant cancellations, "
                + "2048 stop races, 512 accepted jobs and zero retained work");
    }
}
