package l1j.server.server.model.world.instance;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import l1j.server.server.datatables.MapsTable;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1V1Map;
import l1j.server.server.model.world.instance.raid.*;
import l1j.server.server.model.world.instance.raid.loader.*;
import l1j.server.server.model.world.instance.raid.spawn.RaidNpcSpawn;
import sun.misc.Unsafe;

/** Isolated script and map data exercise the real dragon raid creation and disposal paths. */
public final class RaidLifecycleRegression {
    static final RaidSpace space = RaidSpace.getInstance();
    static final AtomicInteger released = new AtomicInteger();

    public static void main(String[] args) throws Exception {
        installEmptyTable(MapsTable.class, "_maps");
        installEmptyTable(RaidBossSkillLoader.class, "_skills", "_combos");
        installEmptyTable(RaidCompensatorLoader.class, "_compensators");
        var maps = new CInstantZoneManager.Maps() {
            public L1Map borrow(int id) { throw new AssertionError(); }
            public boolean occupied(L1Map map) { return false; }
            public void release(L1Map map) { released.incrementAndGet(); }
            public L1Map clone(int template, int id) {
                return new L1V1Map(id, new byte[4], 2, 2, 0, 0, false, true, true, true,
                        true, true, true, true, true, true, true, false);
            }
        };
        field(RaidSpace.class, "rooms").set(space, new CInstantZoneManager<RaidObject>(1, maps));
        RaidLoadManager.MRS_COPYMAP_START_ID = 17888;
        RaidLoadManager.MRS_COPYMAP_SIZE = 1;
        var type = new RaidType() {
            @Override public int getMaxUser() { return 1; }
            @Override public void move(L1PcInstance player, int mapId) {}
            @Override public void inputRaid(L1PcInstance player, int mapId) {}
        };
        var failed = new TestRaid(type);
        check(!space.startInstance(failed, () -> false) && failed.disposed.get() == 1 && released.get() == 1,
                "a refused key must close the initialized raid");
        var raid = new TestRaid(type);
        var debit = new AtomicInteger();
        check(space.startInstance(raid, () -> debit.incrementAndGet() > 0), "opening failed");
        var full = new TestRaid(type);
        check(!space.startInstance(full, () -> debit.incrementAndGet() > 0) && debit.get() == 1,
                "a full raid must not debit another key");
        var pc = new L1PcInstance();
        pc.setId(321);
        pc.setMap(raid.getCopyMap());
        raid.addUser(pc);
        raid.doorMove(pc);
        check(raid.started.await(5, TimeUnit.SECONDS), "door did not start the event");
        RaidSpace.reload();
        check(raid.interrupted.await(5, TimeUnit.SECONDS) && raid.disposed.get() == 0,
                "reload disposed a running script");
        check(!space.startInstance(full, () -> true), "map reused while event remained in callback");
        raid.allowExit.countDown();
        check(raid.finished.await(5, TimeUnit.SECONDS), "event did not retire");
        check(CInstanceEvents.awaitTermination(5, TimeUnit.SECONDS), "event still pending");
        check(space.getOpenSize() == 0 && released.get() == 2, "room or terrain leaked after reload");
        check(space.startInstance(full, () -> true), "map not reusable after retirement");
        raid.failRaid();
        check(space.getOpenSize() == 1 && full.disposed.get() == 0, "stale close affected replacement");
        full.failRaid();
        check(released.get() == 3 && full.disposed.get() == 1, "replacement leaked");
        var oldSkills = RaidBossSkillLoader.getInstance();
        var oldRewards = RaidCompensatorLoader.getInstance();
        RaidBossSkillLoader.release();
        RaidCompensatorLoader.release();
        check(oldSkills.get(1) == null && oldRewards.get(1) == null, "release invalidated an in-flight reader");
        System.out.println("PASS dragon raid key admission, door event, reload, quiescent disposal and reused ID");
    }

    static final class TestRaid extends RaidObject {
        final CountDownLatch started = new CountDownLatch(1), interrupted = new CountDownLatch(1),
                allowExit = new CountDownLatch(1), finished = new CountDownLatch(1);
        final AtomicInteger disposed = new AtomicInteger();
        TestRaid(RaidType type) { super(null, type); }
        protected void init() {}
        protected void readyRaid() {
            started.countDown();
            try {
                allowExit.await();
            } catch (InterruptedException expected) {
                interrupted.countDown();
                try { allowExit.await(); } catch (InterruptedException e) { throw new AssertionError(e); }
            }
        }
        protected void dispose() { super.dispose(); disposed.incrementAndGet(); finished.countDown(); }
        protected void runRaid() {}
        protected void setNormalSpawn(RaidNpcSpawn unused) {}
        protected void setRectSpawn(RaidNpcSpawn unused) {}
        protected void setCircleSpawn(RaidNpcSpawn unused) {}
        protected void setBossSpawn(RaidNpcSpawn unused) {}
    }

    static void installEmptyTable(Class<?> type, String... names) throws Exception {
        Unsafe unsafe = (Unsafe) field(Unsafe.class, "theUnsafe").get(null);
        Object table = unsafe.allocateInstance(type);
        for (String name : names) field(type, name).set(table, new HashMap<>());
        field(type, "_instance").set(null, table);
    }
    static Field field(Class<?> type, String name) throws Exception {
        Field value = type.getDeclaredField(name);
        value.setAccessible(true);
        return value;
    }
    static void check(boolean success, String message) {
        if (!success) throw new AssertionError(message);
    }
}
