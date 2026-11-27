package l1j.server.server.model.world.instance;

import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1V1Map;
import l1j.server.server.model.world.instance.dungeon.valakasroom.ValakasStart;

/** Exercise the raid's flag checks across a player move while the old script remains active. */
public final class ValakasBossDialogueRegression {
    private static final int TEMPLATE = 2600;
    private static final int FIRST = TEMPLATE + 1;
    private static final int SECOND = TEMPLATE + 2;
    private static final Runnable NO_CLEANUP = () -> {};

    public static void main(String[] args) throws Exception {
        final L1Map terrain = new L1V1Map(TEMPLATE, new byte[4][4], 0, 0,
                false, true, true, true, true, true, true, true, true, true, true, false);
        final CInstantZoneManager<ValakasStart> rooms = new CInstantZoneManager<>(2, new CInstantZoneManager.Maps() {
            @Override public L1Map borrow(int id) { throw new AssertionError("unexpected borrow"); }
            @Override public L1Map clone(int template, int id) { return terrain.copyMap(id); }
            @Override public void release(L1Map map) {}
            @Override public boolean occupied(L1Map map) { return false; }
        });
        final var first = rooms.tryOpen(TEMPLATE, -1, FIRST, FIRST);
        final var second = rooms.tryOpen(TEMPLATE, -1, SECOND, SECOND);
        final L1Map former = first.map();
        final var pc = new L1PcInstance();
        final var ctor = ValakasStart.class.getDeclaredConstructor(CInstantZone.class, L1PcInstance.class);
        ctor.setAccessible(true);
        final ValakasStart oldRaid = ctor.newInstance(first, pc);
        final ValakasStart newRaid = ctor.newInstance(second, pc);
        first.bind(oldRaid);
        second.bind(newRaid);
        final Method hasTrigger = method("hasBossTrigger");
        final Method clearTrigger = method("clearBossTrigger");
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch moved = new CountDownLatch(1);
        enterAndTrigger(pc, former);
        final var oldScript = first.start(() -> {
            require((boolean) call(hasTrigger, oldRaid), "first room sees its own trigger");
            started.countDown();
            await(moved);
            require(!(boolean) call(hasTrigger, oldRaid), "old room started a scene using the new room's trigger");
            call(clearTrigger, oldRaid);
            require(pc.isInValakasBoss, "old room cleared the active room's trigger");
        }, NO_CLEANUP);
        try {
            await(started);
            require(CInstantZoneManager.destination(FIRST) == first, "running script retains its exact map owner");
            require(rooms.tryOpen(TEMPLATE, -1, FIRST, FIRST) == null, "no ID reuse while old scene waits");
            enterAndTrigger(pc, second.map());
            moved.countDown();
            oldScript.get(5L, TimeUnit.SECONDS);
            require((boolean) call(hasTrigger, newRaid), "new room still sees its own trigger");
            call(clearTrigger, newRaid);
            require(!pc.isInValakasBoss && !(boolean) call(hasTrigger, newRaid), "normal dialogue clears its trigger");
            require(CInstanceEvents.awaitTermination(5L, TimeUnit.SECONDS), "old script did not retire");
            final var next = rooms.tryOpen(TEMPLATE, -1, FIRST, FIRST);
            require(next != null && next != first && next.map() != former, "retired ID can be reused for a new generation");
            next.close(NO_CLEANUP);
        } finally {
            moved.countDown();
            first.close(NO_CLEANUP);
            second.close(NO_CLEANUP);
        }
        System.out.println("PASS Valakas trigger isolation across running scenes and map-ID reuse after retirement");
    }

    private static void enterAndTrigger(L1PcInstance pc, L1Map map) {
        synchronized (pc) {
            pc.setMap(map);
            pc.isInValakasBoss = true;
        }
    }

    private static Method method(String name) throws ReflectiveOperationException {
        final Method method = ValakasStart.class.getDeclaredMethod(name);
        method.setAccessible(true);
        return method;
    }

    private static Object call(Method method, ValakasStart raid) {
        try { return method.invoke(raid); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private static void await(CountDownLatch latch) {
        try { require(latch.await(5L, TimeUnit.SECONDS), "fixture latch timed out"); }
        catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failure);
        }
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
