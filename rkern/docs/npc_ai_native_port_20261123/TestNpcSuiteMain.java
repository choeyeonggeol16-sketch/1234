package l1j.server.server.model.npc.ai.action;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import l1j.server.server.ThreadPool;
import l1j.server.server.model.npc.ai.CNPCServer;
import l1j.server.server.model.network.thread.CIOThreadCallback;

/** Test-only entry: close real singleton workers, never force successful JVM exit. */
public final class TestNpcSuiteMain {
    public static void main(String[] args) throws Throwable {
        AtomicReference<Throwable> ownerFailure = new AtomicReference<>();
        Logger log = Logger.getLogger(CNPCServer.class.getName());
        Handler handler = new Handler() {
            @Override public void publish(LogRecord record) {
                if (record.getLevel().intValue() >= Level.WARNING.intValue()
                        && record.getThrown() != null) {
                    ownerFailure.compareAndSet(null, record.getThrown());
                }
            }
            @Override public void flush() { }
            @Override public void close() { }
        };
        log.addHandler(handler);
        try {
            Class.forName(args[0]).getMethod("main", String[].class)
                    .invoke(null, (Object) new String[0]);
        } catch (InvocationTargetException failure) {
            throw failure.getCause();
        } finally {
            CNPCServer.getInstance().shutdown();
            CIOThreadCallback.getInstance().shutdown();
            Field instance = ThreadPool.class.getDeclaredField("_instance");
            instance.setAccessible(true);
            ThreadPool pool = (ThreadPool) instance.get(null);
            if (pool != null) pool.shutdown();
            for (Thread thread : Thread.getAllStackTraces().keySet()) {
                if (thread.getName().startsWith("NpcAiOwner-") || thread.getName().startsWith("CIOThread-")) {
                    thread.join(5000);
                    if (thread.isAlive()) throw new AssertionError("worker did not exit: " + thread.getName());
                }
            }
            log.removeHandler(handler);
        }
        if (ownerFailure.get() != null) throw new AssertionError("NPC owner exception", ownerFailure.get());
    }
}
