import com.zaxxer.hikari.HikariDataSource;
import l1j.server.DatabaseFactory;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.storage.CacheD;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public class CacheDOwnerDrainRegression {
    public static void main(String[] args) throws Exception {
        final CountDownLatch retry = new CountDownLatch(1);
        final CountDownLatch allowAck = new CountDownLatch(1);
        final CountDownLatch allowTail = new CountDownLatch(1);
        final AtomicInteger probes = new AtomicInteger();
        final Logger logger = Logger.getLogger(CacheD.class.getName());
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            public void publish(LogRecord log) {
                if (log.getMessage().startsWith("CacheD save failed")) retry.countDown();
            }
            public void flush() {}
            public void close() {}
        });
        field(DatabaseFactory.class, "DATABASE_POOL").set(null, new HikariDataSource() {
            @Override public Connection getConnection() throws SQLException {
                probes.incrementAndGet();
                throw new SQLException("fixture metadata unavailable");
            }
        });
        final CacheD cache = CacheD.getInstance();
        await(retry, "initial metadata retry");
        final CIOThreadCallback cio = CIOThreadCallback.getInstance();
        try {
            final CountDownLatch ownerEntered = new CountDownLatch(1);
            final AtomicReference<Object> job = new AtomicReference<>();
            final AtomicReference<Throwable> callbackFailure = new AtomicReference<>();
            final AtomicInteger postAckWrites = new AtomicInteger();
            final Object lock = field(CacheD.class, "lock").get(cache);
            final Class<?> jobType = Class.forName(CacheD.class.getName() + "$DbJob");
            final Method submit = method(CacheD.class, "submitDbJobInternal", int.class, int.class,
                    Runnable.class, boolean.class, boolean.class);
            final Method require = method(CacheD.class, "requireOwnerApply", jobType);
            final Method finish = method(CacheD.class, "finishOwnerApply", jobType, Throwable.class);
            synchronized (lock) {
                Runnable scheduleReplay = () -> {
                    try { field(jobType, "commitConfirmed").setBoolean(job.get(), true); }
                    catch (Exception e) { throw new AssertionError(e); }
                    check(cio.completeOnShard(0, 75L, () -> {
                        check(CIOThreadCallback.currentShardIndex() == 0, "owner really running on CIO shard");
                        ownerEntered.countDown();
                        await(allowAck, "allow durable reply to finish");
                    }, failure -> {
                        try {
                            finish.invoke(cache, job.get(), failure);
                            check(failure == null, "owner reply succeeded");
                            await(allowTail, "allow post-ack callback to enqueue");
                            cache.submitDbJobOrdered(1202, postAckWrites::incrementAndGet);
                        } catch (Throwable t) { callbackFailure.set(t); }
                    }), "owner callback admitted");
                };
                job.set(submit.invoke(cache, 1201, 0, scheduleReplay, false, false));
                require.invoke(cache, job.get());
            }
            FutureTask<Boolean> drain = new FutureTask<>(() -> cache.drainBeforeOwnerShutdown(2000));
            Thread.ofPlatform().daemon().start(drain);
            await(ownerEntered, "delayed receipt reached owner");
            check(!drain.isDone() && cache.pendingDbJobs() >= 1, "drain waits for owner acknowledgement");
            FutureTask<Boolean> interruptedDrain = new FutureTask<>(() -> {
                Thread.currentThread().interrupt();
                return !cache.drainBeforeOwnerShutdown(2000) && Thread.currentThread().isInterrupted();
            });
            Thread.ofPlatform().daemon().start(interruptedDrain);
            check(interruptedDrain.get(1, TimeUnit.SECONDS), "interrupted drain returns and preserves interrupt");
            allowAck.countDown();
            check(drain.get(2, TimeUnit.SECONDS), "actual owner ack releases first drain");
            final int probesAfterDrain = probes.get();
            cio.shutdown();
            allowTail.countDown();
            check(cio.awaitTermination(2, TimeUnit.SECONDS), "owner callback tail stopped");
            check(callbackFailure.get() == null, "callback retained CacheD admission: " + callbackFailure.get());
            check(cache.shutdown(2000) && postAckWrites.get() == 1 && cache.pendingDbJobs() == 0,
                    "hard shutdown preserves post-ack write");
            check(probes.get() == probesAfterDrain, "disabled startup probe does not retry");
            System.out.println("PASS delayed real CIO owner ack, post-ack DB write, and unavailable schema");
        } finally {
            allowAck.countDown();
            allowTail.countDown();
            cio.shutdown();
            cio.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Method method(Class<?> type, String name, Class<?>... args) throws Exception {
        Method method = type.getDeclaredMethod(name, args);
        method.setAccessible(true);
        return method;
    }

    private static void await(CountDownLatch latch, String label) {
        try { check(latch.await(3, TimeUnit.SECONDS), label); }
        catch (InterruptedException e) { throw new AssertionError(e); }
    }

    private static void check(boolean ok, String label) { if (!ok) throw new AssertionError(label); }
}
