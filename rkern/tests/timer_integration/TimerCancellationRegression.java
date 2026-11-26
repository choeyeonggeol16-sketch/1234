package l1j.server.server.model.network.thread;

import java.lang.management.ManagementFactory;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import l1j.server.server.model.network.thread.CIOThreadCallback.CIOThread;
import l1j.server.server.model.network.thread.CIOThreadCallback.PacketTask;
import l1j.server.server.model.network.thread.CIOThreadCallback.TaskHandle;
import l1j.server.server.model.network.thread.CIOThreadCallback.WheelTask;

public final class TimerCancellationRegression {
    private static final WheelTask NEVER_DUE = new WheelTask() {
        @Override public void run() { throw new AssertionError("Distant timer ran early"); }
        @Override public boolean isCancelled() { return false; }
    };

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static void await(CountDownLatch latch) {
        try { check(latch.await(3, TimeUnit.SECONDS), "Timed out: " + latch); }
        catch (InterruptedException failure) { throw new AssertionError(failure); }
    }

    private static CIOThread firstShard(CIOThreadCallback cio) throws Exception {
        var field = CIOThreadCallback.class.getDeclaredField("shards");
        field.setAccessible(true);
        return ((CIOThread[]) field.get(cio))[0];
    }

    private static void cancelDistant(CIOThread shard) throws Exception {
        final CThreadLocalTimer wheel = shard.threadLocalTimer;
        final var size = CThreadLocalTimer.class.getDeclaredField("distantSize");
        size.setAccessible(true);
        final AtomicInteger cancelled = new AtomicInteger();
        final TaskHandle[] handles = new TaskHandle[1024];
        final Random order = new Random(917);
        for (int i = 0; i < handles.length; i++) {
            handles[i] = new TaskHandle(cancelled::incrementAndGet);
            wheel.schedule(60_000L + order.nextInt(40_000), NEVER_DUE, handles[i]);
        }
        for (int i = handles.length - 1; i >= 0; i--) {
            final int index = order.nextInt(i + 1);
            check(handles[index].cancel(), "Timer cancelled once");
            check(!handles[index].cancel(), "Duplicate cancel changed timer");
            handles[index] = handles[i];
            synchronized (wheel) {
                check(size.getInt(wheel) == i, "Cancelled long deadline remained in the wheel");
            }
        }
        check(cancelled.get() == handles.length, "Cancellation count");

        final TaskHandle[] racing = new TaskHandle[2048];
        for (int i = 0; i < racing.length; i++) racing[i] = new TaskHandle();
        final CountDownLatch start = new CountDownLatch(1);
        final Thread canceller = Thread.ofPlatform().daemon().start(() -> {
            await(start);
            for (TaskHandle handle : racing) handle.cancel();
        });
        start.countDown();
        for (TaskHandle handle : racing) wheel.schedule(60_000L, NEVER_DUE, handle);
        canceller.join(3000);
        check(!canceller.isAlive(), "Cancel and timer publication did not finish");
        synchronized (wheel) {
            check(size.getInt(wheel) == 0, "Cancel-before-publication left a heap entry");
        }
    }

    private static void cancelOnShutdown(CIOThreadCallback cio) throws Exception {
        final Object persistenceGate = new Object();
        final CountDownLatch occupied = new CountDownLatch(1);
        final CountDownLatch releaseOwner = new CountDownLatch(1);
        final CountDownLatch cancelling = new CountDownLatch(1);
        final CountDownLatch completed = new CountDownLatch(1);
        final AtomicReference<Throwable> unexpected = new AtomicReference<>();
        final AtomicInteger callbacks = new AtomicInteger();
        final PacketTask blocker = new PacketTask() {
            @Override public void run() {
                occupied.countDown();
                await(releaseOwner);
            }
            @Override public void cancel() {}
        };
        check(cio.enqueuePacketImmediate(0, blocker), "Owner work admission");
        await(occupied);
        check(cio.completeOnShard(0, () -> unexpected.set(new AssertionError("Stopped timer ran")), failure -> {
            cancelling.countDown();
            synchronized (persistenceGate) {
                callbacks.incrementAndGet();
                if (failure == null) unexpected.set(new AssertionError("Cancellation reported success"));
                completed.countDown();
            }
        }), "Completion admission");

        final Thread producer = Thread.ofPlatform().daemon().start(() -> {
            try {
                synchronized (persistenceGate) {
                    cio.shutdown();
                    releaseOwner.countDown();
                    await(cancelling);
                    check(!cio.enqueuePacketImmediate(0, blocker), "Stopped owner admitted work");
                }
            } catch (Throwable failure) {
                unexpected.set(failure);
            }
        });
        producer.join(3500);
        check(!producer.isAlive(), "Shutdown cancellation deadlocked with a producer");
        await(completed);
        check(cio.awaitTermination(3, TimeUnit.SECONDS), "Shard did not terminate");
        check(callbacks.get() == 1 && unexpected.get() == null, "Cancellation outcome: " + unexpected.get());
        check(ManagementFactory.getThreadMXBean().findDeadlockedThreads() == null, "JVM deadlock detected");
    }

    public static void main(String[] args) throws Exception {
        final CIOThreadCallback cio = CIOThreadCallback.getInstance();
        try {
            if (args.length == 0) cancelDistant(firstShard(cio));
            cancelOnShutdown(cio);
            System.out.println("PASS long timer cancellation and concurrent shard shutdown");
        } finally {
            // Let the process report a reproducible failure even if the old owner cannot exit.
            if (args.length == 0) cio.shutdown();
        }
    }
}
