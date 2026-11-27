package l1j.server.server.model;

import java.lang.management.ManagementFactory;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import l1j.server.Config;
import l1j.server.TextMapReader;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.ai.core.WorldCollisionHash;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1V1Map;
import l1j.server.server.model.map.L1WorldMap;

/** Coordinated actor churn using the production identity, map membership, spatial and PC occupancy indices. */
public final class WorldChurnRegression {
    private static final int WORKERS = 4;
    private static final int ROUNDS = 24;
    private static final int ACTORS_PER_WORKER = 8;
    private static final int WORKER_TIMEOUT_SECONDS = 8;
    private static final int BASE_ID = 3200;
    private static final int CLONE_ID = 3201;
    private static final int FIRST_ACTOR_ID = 1_600_000;
    private static final int DENSE_OBJECTS = 144;
    private static volatile byte[] heapPressure;

    public static void main(String[] args) throws Exception {
        WorldShapeRegression.installMapDefinitions();
        var definitions = TextMapReader.class.getDeclaredField("MAP_INFO");
        definitions.setAccessible(true);
        Arrays.fill((int[][]) definitions.get(null), null);
        Config.CACHE_MAP_FILES = false;

        final L1WorldMap maps = L1WorldMap.getInstance();
        final L1Map main = new L1V1Map(BASE_ID, new byte[128][128], 80, 80,
                false, true, true, true, true, true, true, true, true, true, true, false);
        maps.addMap(main);
        final CWorld world = CWorld.getInstance();
        final var pool = Executors.newFixedThreadPool(WORKERS,
                Thread.ofPlatform().name("world-churn-", 1).daemon().factory());
        final var samples = new WeakSamples();
        try {
            assertDrained(world);
            denseCellChurn(world, main, samples);
            for (int round = 0; round < ROUNDS; round++) runRound(world, maps, main, pool, samples);
            assertDrained(world);
            noDetectedDeadlocks();
            samples.report();
        } finally {
            pool.shutdownNow();
            check(pool.awaitTermination(WORKER_TIMEOUT_SECONDS, TimeUnit.SECONDS), "churn workers terminated");
            maps.removeMap(BASE_ID);
        }
        System.out.println("PASS world churn: 768 concurrent PC/object lifecycles, 144 dense entries, 24 map generations, zero retained members/cells/occupiers and no JVM-reported deadlock");
    }

    private static void runRound(CWorld world, L1WorldMap maps, L1Map main, ExecutorService pool, WeakSamples samples)
            throws Exception {
        final L1Map clone = maps.tryCloneMap(BASE_ID, CLONE_ID);
        check(clone != null, "fresh clone admitted on reused ID");
        try {
            final var round = new Round();
            final var tasks = new ArrayList<Future<?>>(WORKERS);
            for (int lane = 0; lane < WORKERS; lane++) {
                final int worker = lane;
                tasks.add(pool.submit(() -> {
                    try { churnLane(world, main, clone, worker, round, samples); }
                    catch (Exception | Error failure) {
                        round.failure.compareAndSet(null, failure);
                        round.phases.forceTermination();
                        throw failure;
                    }
                    return null;
                }));
            }
            phase(round, "enter");
            verifyActive(world, main, clone);
            phase(round, "start moving");
            phase(round, "moves completed");
            verifyActive(world, main, clone);
            phase(round, "start leaving");
            for (Future<?> task : tasks) {
                try { task.get(WORKER_TIMEOUT_SECONDS, TimeUnit.SECONDS); }
                catch (TimeoutException failure) { throw stalled("leave", failure); }
                catch (ExecutionException failure) { throw new AssertionError("churn worker failed", failure.getCause()); }
            }
            assertDrained(world);
        } finally {
            check(maps.removeMap(clone) && maps.findMap(CLONE_ID) == null, "drained terrain released from map registry");
            samples.track(clone);
        }
    }

    private static void churnLane(CWorld world, L1Map main, L1Map clone, int lane, Round round, WeakSamples samples)
            throws Exception {
        final var actors = new ArrayList<L1Object>(ACTORS_PER_WORKER);
        final int firstX = 102 + 24 * lane;
        final int firstId = FIRST_ACTOR_ID + lane * ACTORS_PER_WORKER;
        final WorldCollisionHash collision = WorldCollisionHash.getInstance();
        try {
            for (int slot = 0; slot < ACTORS_PER_WORKER; slot++) {
                final L1Object actor = slot < 2 ? new L1PcInstance() : new L1Object();
                final int id = firstId + slot;
                actor.setId(id);
                actor.getLocation().set(firstX, 101 + 2 * slot, (slot & 1) == 0 ? main : clone);
                if (actor instanceof L1PcInstance pc) pc.setName("churn-" + id);
                actors.add(actor);
                world.storeAndEnterWorld(actor);
                check(world.findObject(id) == actor, "admitted current identity");
                if (actor instanceof L1PcInstance) assertOccupier(collision, actor, id);
            }
            phase(round, "enter");
            phase(round, "start moving");
            for (L1Object actor : actors) {
                move(world, collision, actor, actor.getMap(), firstX + 1);
                move(world, collision, actor, actor.getMap(), firstX + 2);
                move(world, collision, actor, actor.getMap() == main ? clone : main, firstX + 2);
                move(world, collision, actor, actor.getMap(), firstX + 3);
                check(world.getVisiblePoint(actor.getLocation(), 0).equals(List.of(actor)), "published target tile has one actor");
                List<L1Object> nearby = world.getClusterGrid().borrowNearby(actor, 6);
                try { check(!nearby.contains(actor), "nearby scratch excludes origin during other actor moves"); }
                finally { world.getClusterGrid().releaseNearby(nearby); }
                check(nearby.isEmpty(), "worker scratch released all actor references");
            }
            phase(round, "moves completed");
            phase(round, "start leaving");
        } finally {
            for (L1Object actor : actors) {
                world.leaveAndRemoveObject(actor);
                check(world.findObject(actor.getId()) == null, "retired identity released");
                if (actor instanceof L1PcInstance) assertOccupier(collision, actor, 0);
            }
            if (actors.size() == ACTORS_PER_WORKER) {
                samples.track(actors.getFirst());
                samples.track(actors.getLast());
            }
        }
    }

    private static void move(CWorld world, WorldCollisionHash collision, L1Object actor, L1Map destination, int x) {
        final int oldMap = actor.getMapId(), oldX = actor.getX(), y = actor.getY(), id = actor.getId();
        final boolean player = actor instanceof L1PcInstance;
        if (player && oldMap == destination.getId()) {
            check(collision.reservePcMove(oldMap, x, y, id, 1_000L)
                    && collision.commitPcMove(oldMap, oldX, y, x, y, id), "ordinary PC step admitted");
        } else if (player) {
            check(collision.teleportPc(oldMap, oldX, y, destination.getId(), x, y, id), "PC map transfer admitted");
        }
        if (oldMap == destination.getId()) {
            actor.getLocation().set(x, y, destination);
            world.moveTo(actor, oldMap, oldX, y);
            check(actor.getX() == x, "CWorld published ordinary move");
        } else {
            check(world.relocateVisibleObject(actor, x, y, destination), "CWorld published map transfer");
        }
        if (player) {
            check(collision.getPcOccupier((short) oldMap, oldX, y) == 0, "PC's previous collision slot reclaimed");
            assertOccupier(collision, actor, id);
        }
    }

    private static void assertOccupier(WorldCollisionHash collision, L1Object actor, int id) {
        check(collision.getPcOccupier(actor.getMapId(), actor.getX(), actor.getY()) == id, "PC tile ownership");
    }

    private static void verifyActive(CWorld world, L1Map main, L1Map clone) {
        final int half = WORKERS * ACTORS_PER_WORKER / 2;
        check(world.objectCount() == half * 2 && world.getAllPlayersSize() == WORKERS * 2, "active global object and player counts");
        for (L1Map map : List.of(main, clone)) {
            check(world.getMapObjects(map.getId()).size() == half && world.snapshotZoneOccupants(map).size() == half,
                    "numeric and exact terrain membership both contain half the actors");
            check(world.getVisiblePlayers(map).size() == WORKERS, "each map has one player from each worker");
        }
        check(world.getClusterGrid().getCellCount() > 0, "active spatial cells are populated");
    }

    private static void denseCellChurn(CWorld world, L1Map terrain, WeakSamples samples) {
        final var actors = new ArrayList<L1Object>(DENSE_OBJECTS);
        try {
            for (int i = 0; i < DENSE_OBJECTS; i++) {
                var actor = new L1Object();
                actor.setId(FIRST_ACTOR_ID + 512 + i);
                actor.getLocation().set(120, 120, terrain);
                actors.add(actor);
                world.storeAndEnterWorld(actor);
            }
            check(world.getVisiblePoint(new L1Location(120, 120, terrain), 0).size() == DENSE_OBJECTS,
                    "dense bucket kept all actors");
            for (int i = 0; i < 128; i++) {
                check(world.relocateVisibleObject(actors.get(i), 128, 120, terrain), "dense bucket relocation");
            }
            check(world.getVisiblePoint(new L1Location(120, 120, terrain), 0).size() == 16
                    && world.getVisiblePoint(new L1Location(128, 120, terrain), 0).size() == 128,
                    "dense bucket shrinks and new cell owns all moved actors");
            samples.track(actors.getFirst());
        } finally {
            for (L1Object actor : actors) world.leaveAndRemoveObject(actor);
        }
        assertDrained(world);
    }

    private static void assertDrained(CWorld world) {
        check(world.objectCount() == 0 && world.getAllPlayersSize() == 0 && world.getAllNpc().isEmpty()
                && world.getMapObjects(BASE_ID).isEmpty() && world.getMapObjects(CLONE_ID).isEmpty()
                && world.getClusterGrid().getCellCount() == 0 && world.ExecutableBacklog() == 0,
                "no actors, named players, spatial cells, map members, or serialized world work remain");
    }

    private static void phase(Round round, String name) throws InterruptedException {
        try {
            final int next = round.phases.awaitAdvanceInterruptibly(round.phases.arrive(),
                    WORKER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (next < 0) throw new AssertionError("churn worker failed at " + name, round.failure.get());
        }
        catch (TimeoutException failure) { throw stalled(name, failure); }
    }

    private static AssertionError stalled(String phase, TimeoutException cause) {
        final long[] deadlocked = ManagementFactory.getThreadMXBean().findDeadlockedThreads();
        return new AssertionError("world churn timeout at " + phase + ", JVM deadlocked threads=" + Arrays.toString(deadlocked), cause);
    }

    private static void noDetectedDeadlocks() {
        check(ManagementFactory.getThreadMXBean().findDeadlockedThreads() == null, "JVM reports cyclic lock ownership");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class Round {
        final Phaser phases = new Phaser(WORKERS + 1);
        final AtomicReference<Throwable> failure = new AtomicReference<>();
    }

    private static final class WeakSamples {
        private final ReferenceQueue<Object> ready = new ReferenceQueue<>();
        private final ConcurrentLinkedQueue<WeakReference<Object>> refs = new ConcurrentLinkedQueue<>();

        void track(Object value) { refs.add(new WeakReference<>(value, ready)); }

        void report() {
            for (int i = 0; i < 4; i++) {
                heapPressure = new byte[1024 * 1024];
                heapPressure[0] = (byte) i;
            }
            heapPressure = null;
            System.gc();
            final long cleared = refs.stream().filter(value -> value.get() == null).count();
            int enqueued = 0;
            while (ready.poll() != null) enqueued++;
            // This is observational. The JVM is free to ignore an explicit GC request.
            System.out.printf("GC observation: cleared %d of %d sampled retired actors/maps, %d queued; workers still live%n",
                    cleared, refs.size(), enqueued);
        }
    }
}
