package l1j.server.server.model;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import l1j.server.Config;
import l1j.server.TextMapReader;
import l1j.server.server.datatables.MapsTable;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1V1Map;
import l1j.server.server.model.map.L1WorldMap;
import sun.misc.Unsafe;

/** Runs the real caller-drained serializer. Each blocked owner is released by the test thread. */
public final class WorldLifecycleRegression {
    public static void main(String[] args) throws Exception {
        final var unsafe = (Unsafe) field(Unsafe.class, "theUnsafe").get(null);
        final var metadata = (MapsTable) unsafe.allocateInstance(MapsTable.class);
        field(MapsTable.class, "_maps").set(metadata, Map.of());
        field(MapsTable.class, "_instance").set(null, metadata);
        Arrays.fill((int[][]) field(TextMapReader.class, "MAP_INFO").get(null), null);
        Config.CACHE_MAP_FILES = false;
        var maps = L1WorldMap.getInstance();
        var home = terrain(31000);
        var away = terrain(31001);
        maps.addMap(home);
        maps.addMap(away);
        var world = CWorld.getInstance();
        try {
            queuedReplacements(world, home, away);
            reinstallationAtMutation(world, home);
            releaseDrainedPages(world, home);
            check(world.objectCount() == 0 && world.ExecutableBacklog() == 0, "world drained");
            System.out.println("PASS queued store/move generation, same-instance remove/leave/enter/move at mutation, FIFO dependency, page/argument retirement");
        } finally {
            maps.removeMap(home.getId());
            maps.removeMap(away.getId());
        }
    }

    private static void queuedReplacements(CWorld world, L1Map home, L1Map away) throws Exception {
        var gate = actor(201, home);
        var moved = actor(202, home);
        var old = actor(203, home);
        var replacement = actor(203, home);
        var fresh = actor(204, home);
        world.storeAndEnterWorld(gate);
        world.storeAndEnterWorld(moved);
        world.storeAndEnterWorld(old);
        try {
            Thread drain;
            synchronized (gate) {
                drain = blockedIn("removeVisibleObject", () -> world.AsyncLeaveWorld(gate));
                check(world.AsyncMove(moved, 113, 114, away.getId()), "queue move");
                check(world.AsyncStoreObject(old) && world.AsyncStoreAndEnterWorld(old), "queue registered store");
                check(world.AsyncStoreObject(fresh) && world.AsyncEnterWorld(fresh)
                        && world.AsyncMove(fresh, 108, 109, home.getId()), "queue dependents of first store");
                L1WorldMap.getInstance().addMap(terrain(away.getId()));
                world.storeAndEnterWorld(replacement);
            }
            joined(drain);
            check(moved.getMap() == home && moved.getX() == 100, "queued move cannot enter recycled destination");
            check(world.findObject(old.getId()) == replacement && old.getClusterMapId() < 0,
                    "queued re-store cannot resurrect previous generation");
            check(world.findObject(fresh.getId()) == fresh && fresh.getClusterMapId() == home.getId()
                    && fresh.getX() == 108 && fresh.getY() == 109, "fresh FIFO registration enters and moves");
            for (Object page : (Object[]) field(CWorld.class, "executionPages").get(world)) {
                if (page != null) {
                    for (String name : new String[]{"objects", "destinations"})
                        for (Object argument : (Object[]) field(page.getClass(), name).get(page))
                            check(argument == null, "completed world argument retained: " + name);
                }
            }
        } finally {
            for (var actor : new L1Object[]{gate, moved, old, replacement, fresh}) world.removeObject(actor);
        }
    }

    private static void reinstallationAtMutation(CWorld world, L1Map home) throws Exception {
        for (String operation : new String[]{"remove", "leave", "enter", "move"}) {
            var same = actor(401, home);
            world.storeAndEnterWorld(same);
            Thread drain;
            synchronized (same) {
                drain = switch (operation) {
                    case "remove" -> blockedIn("leaveAndRemoveObject", () -> world.AsyncRemoveObject(same));
                    case "leave" -> blockedIn("removeVisibleObject", () -> world.AsyncLeaveWorld(same));
                    case "enter" -> blockedIn("addVisibleObject0", () -> world.AsyncEnterWorld(same));
                    case "move" -> blockedIn("relocateVisibleObject", () -> world.AsyncMove(same, 112, 113, home.getId()));
                    default -> throw new AssertionError();
                };
                long before = world.smartIdOf(same);
                world.removeObject(same);
                if (operation.equals("enter")) world.storeObject(same);
                else world.storeAndEnterWorld(same);
                check(world.smartIdOf(same) != before, "same instance acquires fresh registration");
            }
            joined(drain);
            check(world.findObject(same.getId()) == same
                    && (same.getClusterMapId() >= 0) == !operation.equals("enter")
                    && same.getX() == 100, "stale " + operation + " changed reinstalled actor");
            world.removeObject(same);
        }
    }

    private static void releaseDrainedPages(CWorld world, L1Map home) throws Exception {
        var gate = actor(210, home);
        var detached = actor(211, home);
        world.storeAndEnterWorld(gate);
        Field count = field(CWorld.class, "executionAllocatedPages");
        for (int i = 0; i < 66_000; i++) check(world.AsyncLeaveWorld(detached), "initial page rotation");
        Thread drain;
        synchronized (gate) {
            drain = blockedIn("removeVisibleObject", () -> world.AsyncLeaveWorld(gate));
            for (int i = 0; i < 264_000; i++) check(world.AsyncLeaveWorld(detached), "queued burst");
            check(count.getInt(world) >= 256, "large burst populated at least 256 real FIFO pages");
        }
        joined(drain);
        check(count.getInt(world) == 0, "drain frees second large burst while first trim still on cooldown");
        check(world.AsyncRemoveObject(gate) && world.findObject(gate.getId()) == null, "admission after trim");
    }

    private static Thread blockedIn(String method, Runnable action) {
        var thread = Thread.ofPlatform().daemon().start(action);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (thread.getState() == Thread.State.BLOCKED)
                for (var frame : thread.getStackTrace())
                    if (frame.getClassName().equals(CWorld.class.getName()) && frame.getMethodName().equals(method))
                        return thread;
            LockSupport.parkNanos(100_000);
        }
        throw new AssertionError("blocked world mutation did not reach " + method);
    }

    private static void joined(Thread thread) throws Exception {
        thread.join(10_000);
        check(!thread.isAlive(), "drainer did not finish");
    }

    private static L1Map terrain(int id) {
        return new L1V1Map(id, new byte[64][64], 80, 80, false, false, false,
                false, false, false, false, false, false, false, false, false);
    }

    private static L1Object actor(int id, L1Map map) {
        var actor = new L1Object();
        actor.setId(id);
        actor.getLocation().set(100, 101, map);
        return actor;
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void check(boolean passed, String message) {
        if (!passed) throw new AssertionError(message);
    }
}
