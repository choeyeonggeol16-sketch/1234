package l1j.server.server.model.npc.ai.action;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import l1j.server.server.ThreadPool;
import l1j.server.server.model.npc.ai.CNPCServer;
import l1j.server.server.model.network.thread.CIOThreadCallback;

/** Test-only entry: close real singleton workers, never force successful JVM exit. */
public final class TestNpcSuiteMain {
    public static void main(String[] args) throws Throwable {
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
        }
    }
}
