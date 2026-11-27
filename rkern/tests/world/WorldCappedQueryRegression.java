package l1j.server.server.model;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import com.sun.management.ThreadMXBean;
import l1j.server.Config;
import l1j.server.TextMapReader;
import l1j.server.server.datatables.MapsTable;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1V1Map;
import l1j.server.server.model.map.L1WorldMap;
import sun.misc.Unsafe;

public final class WorldCappedQueryRegression {
    private static final int DENSE = 1_024;
    private static final int RUNS = 2_000;
    private static int candidateVisits;

    public static void main(String[] args) throws Exception {
        initializeMaps();
        final boolean measureBaseline = args.length != 0 && args[0].equals("baseline");
        densePrefixAndCost(measureBaseline);
        mapRadiusAndReferenceIdentity();
        replacementKeepsFirstSlot();
        System.out.println("PASS capped query order, validation, nested output and concurrent replacement");
    }

    private static void densePrefixAndCost(boolean measureBaseline) {
        final L1Map map = terrain(3120);
        final var maps = L1WorldMap.getInstance();
        final var world = CWorld.getInstance();
        final var objects = new ArrayList<L1Object>(DENSE);
        maps.addMap(map);
        final L1Object origin = at(new L1Object(), map, 30_000, 100);
        try {
            for (int i = 0; i < DENSE; i++) {
                final L1Object object = at(new L1Object() {
                    @Override public int getX() {
                        candidateVisits++;
                        return super.getX();
                    }
                }, map, 30_001 + i, 100);
                objects.add(object);
                world.storeAndEnterWorld(object);
            }
            final var result = new ArrayList<L1Object>(64);
            for (int i = 0; i < RUNS; i++) world.fillVisibleObjectsLimit(origin, 0, 64, result);
            final var bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
            final long tid = Thread.currentThread().threadId();
            final boolean countBytes = bean.isThreadAllocatedMemorySupported() && bean.isThreadAllocatedMemoryEnabled();
            for (int cap : new int[] {48, 64}) {
                candidateVisits = 0;
                final long before = countBytes ? bean.getThreadAllocatedBytes(tid) : 0L;
                for (int i = 0; i < RUNS; i++) world.fillVisibleObjectsLimit(origin, 0, cap, result);
                final long bytes = countBytes ? bean.getThreadAllocatedBytes(tid) - before : -1L;
                check(result.equals(objects.subList(0, cap)), "cap uses first dense objects in original bucket order");
                check(candidateVisits == (measureBaseline ? DENSE : cap) * RUNS, "dense visit count does not exceed prefix");
                System.out.printf("%s cap=%d dense=%d visits/op=%d allocated B/op=%.1f%n",
                        measureBaseline ? "Baseline" : "Candidate", cap, DENSE, candidateVisits / RUNS,
                        bytes < 0L ? -1.0 : (double) bytes / RUNS);
            }

            final var nested = new ArrayList<L1Object>() {
                @Override public boolean add(L1Object object) {
                    if (isEmpty()) {
                        final var grid = world.getClusterGrid();
                        final var nearby = grid.borrowNearby(origin, 0);
                        try { check(nearby.size() == DENSE, "custom output can make a separate nearby query"); }
                        finally { grid.releaseNearby(nearby); }
                    }
                    return super.add(object);
                }
            };
            world.fillVisibleObjectsLimit(origin, 0, 1, nested);
            check(nested.equals(List.of(objects.get(0))), "nested output preserves the capped prefix");
        } finally {
            for (L1Object object : objects) world.leaveAndRemoveObject(object);
            maps.removeMap(map);
        }
    }

    private static void mapRadiusAndReferenceIdentity() {
        final var world = CWorld.getInstance();
        final LocalWorldMap grid = world.getClusterGrid();
        final L1Map map = terrain(3121), otherGeneration = terrain(3121);
        final L1Object origin = at(new L1Object(), map, 32_000, 100);
        final L1Object a = at(new L1Object(), map, -221, 94);
        final L1Object b = at(new L1Object(), map, -221, 108);
        grid.enterSilently(a);
        grid.enterSilently(b);
        final L1Object foreign = at(new L1Object(), otherGeneration, 32_002, 95);
        grid.enterSilently(foreign);
        try {
            final var result = new ArrayList<L1Object>();
            for (int radius : new int[] {-2, -1, 0, 6, 8, Integer.MAX_VALUE}) {
                final var full = grid.queryNearby(origin, radius);
                for (int cap : new int[] {0, 1, 2, 3, Integer.MAX_VALUE}) {
                    world.fillVisibleObjectsLimit(origin, radius, cap, result);
                    check(result.equals(full.subList(0, Math.min(cap, full.size()))), "cap preserves radius and map identity");
                }
            }
            check(result.equals(List.of(a, b)), "distinct objects with the same negative ID are kept by reference");
            world.fillVisibleObjectsLimit(null, 0, 2, result);
            check(result.isEmpty(), "null origin clears caller buffer");
            world.fillVisibleObjectsLimit(origin, 0, 2, null);
        } finally {
            grid.leaveSilently(a);
            grid.leaveSilently(b);
            grid.leaveSilently(foreign);
        }
    }

    private static void replacementKeepsFirstSlot() throws Exception {
        final L1Map map = terrain(3122);
        final var world = CWorld.getInstance();
        final var maps = L1WorldMap.getInstance();
        maps.addMap(map);
        final var origin = at(new L1Object(), map, 34_000, 101);
        final var old = at(new L1Object(), map, 34_001, 94);
        final var filler = at(new L1Object(), map, 34_002, 107);
        final var current = at(new L1Object(), map, old.getId(), 108);
        final var scanning = new CountDownLatch(1);
        final var swapped = new CountDownLatch(1);
        final Thread reader = Thread.currentThread();
        final var marker = at(new L1Object() {
            boolean armed;
            @Override public int getX() {
                if (armed && Thread.currentThread() == reader) {
                    armed = false;
                    scanning.countDown();
                    await(swapped);
                }
                return super.getX();
            }
        }, map, 34_003, 100);
        try {
            for (L1Object object : List.of(old, marker, filler)) world.storeAndEnterWorld(object);
            final var swap = new FutureTask<>(() -> {
                try {
                    await(scanning);
                    world.storeAndEnterWorld(current);
                    return null;
                } finally { swapped.countDown(); }
            });
            Thread.ofPlatform().daemon().start(swap);
            marker.armed = true;
            final var results = new ArrayList<L1Object>();
            world.fillVisibleObjectsLimit(origin, 16, 2, results);
            swap.get(5, TimeUnit.SECONDS);
            check(results.equals(List.of(current, marker)), "selected ID keeps its first slot when its object is replaced");
        } finally {
            for (L1Object object : List.of(old, marker, filler, current)) world.leaveAndRemoveObject(object);
            maps.removeMap(map);
        }
    }

    private static <T extends L1Object> T at(T object, L1Map map, int id, int x) {
        object.setId(id);
        object.getLocation().set(x, 101, map);
        return object;
    }

    private static L1Map terrain(int id) {
        return new L1V1Map(id, new byte[128][128], 80, 80,
                false, true, true, true, true, true, true, true, true, true, true, false);
    }

    private static void initializeMaps() throws ReflectiveOperationException {
        final Unsafe unsafe = (Unsafe) field(Unsafe.class, "theUnsafe").get(null);
        final MapsTable table = (MapsTable) unsafe.allocateInstance(MapsTable.class);
        field(MapsTable.class, "_maps").set(table, Map.of());
        field(MapsTable.class, "_instance").set(null, table);
        Arrays.fill((int[][]) field(TextMapReader.class, "MAP_INFO").get(null), null);
        Config.CACHE_MAP_FILES = false;
    }

    private static Field field(Class<?> owner, String name) throws ReflectiveOperationException {
        final Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void await(CountDownLatch latch) {
        try { check(latch.await(5, TimeUnit.SECONDS), "test interleaving timed out"); }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
