package l1j.server.server.model;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import l1j.server.Config;
import l1j.server.TextMapReader;
import l1j.server.server.datatables.MapsTable;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.ai.core.WorldCollisionHash;
import l1j.server.server.model.character.animation.NpcSpeedData;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1V1Map;
import l1j.server.server.model.map.L1WorldMap;
import l1j.server.server.templates.L1Npc;
import sun.misc.Unsafe;

public final class WorldVisibilityRegression {
    public static void main(String[] args) throws Exception {
        installMapMetadata();
        radiusScreenAndGenerations();
        mapPlayerMembership();
        fastOrderingAndLimits();
        publishedMovementAndCleanup();
        movingAndReplacedIdsAppearOnce();
        System.out.println("PASS world visibility geometry, unsigned/first-seen order, terrain generation, map player roster and rollback, entered players, nested scopes and concurrent identities");
    }

    private static void radiusScreenAndGenerations() {
        L1Map terrain = map(3100);
        L1Map reusedNumber = map(3100);
        L1Map elsewhere = map(3101);
        try (var fixture = new WorldFixture(terrain, elsewhere)) {
            var origin = fixture.object(terrain, 55005, 100, 100);
            var west = fixture.object(terrain, 55009, 97, 100);
            var unsignedLast = fixture.object(terrain, -1280, 103, 100);
            var same = fixture.object(terrain, 55003, 100, 100);
            var diagonal = fixture.object(terrain, 55001, 103, 103);
            var screenEdge = fixture.object(terrain, 55012, 90, 110);
            var pastScreen = fixture.object(terrain, 55011, 120, 100); // same Manhattan distance, different screen shape
            var far = fixture.object(terrain, 55013, 150, 150);
            var current = fixture.player(terrain, 55014, 101, 100, true);
            var ghost = fixture.player(terrain, 55015, 102, 100, false);
            var recycled = fixture.player(reusedNumber, 55016, 101, 100, true);
            fixture.object(elsewhere, 55017, 100, 100);
            fixture.object(terrain, 55019, 100 + 524_288, 100); // wraps the cell-key bit width if admitted

            CWorld world = fixture.world;
            var near = unsigned(west, unsignedLast, same, diagonal, current);
            check(world.getVisibleObjects(origin, 3).equals(near), "radius uses inclusive Chebyshev boundaries and unsigned IDs");
            check(world.getVisibleObjects(origin, 0).equals(List.of(same)), "same tile excludes origin");
            check(world.getVisibleObjects(origin).equals(unsigned(west, unsignedLast, same, diagonal, screenEdge, current)),
                    "screen's asymmetric edge remains unchanged");
            check(world.getVisibleObjects(origin, -2).isEmpty(), "legacy invalid object radius is empty");
            check(world.getVisiblePoint(origin.getLocation(), 0).equals(unsigned(origin, same)), "standalone point includes its tile's source");
            check(world.getVisiblePoint(origin.getLocation(), 3).equals(unsigned(origin, west, unsignedLast, same, diagonal, current)),
                    "point queries exclude the other terrain generation");
            check(world.getVisiblePoint(origin.getLocation(), -1).isEmpty(), "negative point radius returns empty");
            check(world.getVisibleObjects(origin, Integer.MAX_VALUE).equals(
                    unsigned(west, unsignedLast, same, diagonal, screenEdge, pastScreen, far, current)),
                    "max-radius query scans occupied cells without overflow or wrapped coordinates");
            check(world.getVisiblePlayer(origin, 3, 0).equals(near) && world.getVisiblePlayer(origin, -1, 0).isEmpty(),
                    "legacy three-argument overload keeps its radius and negative contract");

            check(world.findObject(ghost.getId()) == ghost, "pre-login test player really exists in identity registry");
            check(world.getVisiblePlayer(origin, 3).equals(List.of(current)), "only players who entered the same terrain are visible");
            check(world.getVisiblePlayer(origin, -2).isEmpty()
                    && world.getClusterGrid().queryNearbyPlayers(origin, -2).equals(List.of(current)),
                    "historical and fast negative radius contracts stay distinct");
            check(world.getClusterGrid().hasNearbyPlayer(origin, 3)
                    && !world.getClusterGrid().hasNearbyPlayer(origin, 0), "fast existence follows player and radius gates");
            var destination = new ArrayList<>(List.of(ghost, recycled));
            world.collectVisiblePlayers(origin, 3, destination);
            check(destination.equals(List.of(current)), "ordinary player collector replaces caller result");

            var outer = world.borrowVisiblePlayerFast(origin, 3);
            var frames = new ArrayList<List<L1PcInstance>>();
            var alternate = object(reusedNumber, 55018, 100, 100);
            try {
                check(outer.equals(List.of(current)), "outer fast snapshot");
                for (int depth = 0; depth < 5; depth++) {
                    var frame = world.borrowVisiblePlayerFast(alternate, 3);
                    frames.add(frame);
                    check(frame != outer && frame.equals(List.of(recycled)), "nested snapshots keep their own terrain and arrays");
                }
                check(outer.equals(List.of(current)), "nested scopes leave outer audience intact");
            } finally {
                for (int depth = frames.size() - 1; depth >= 0; depth--) world.releaseVisiblePlayerFast(frames.get(depth));
                world.releaseVisiblePlayerFast(outer);
            }
            check(outer.isEmpty(), "released worker snapshot no longer retains actors");
        }
    }

    private static void mapPlayerMembership() throws Exception {
        final var first = map(3120);
        final var recycled = map(3120);
        final var other = map(3121);
        try (var fixture = new WorldFixture(first, other)) {
            final var world = fixture.world;
            final var high = fixture.player(first, -101, 100, 101, true);
            final var low = fixture.player(first, 601, 103, 101, true);
            final var staged = fixture.player(first, 602, 105, 101, false);
            final var ghost = fixture.player(recycled, 603, 109, 101, true);
            for (int i = 0; i < 48; ++i) fixture.object(first, 621 + i, 150, 150);
            check(world.getVisiblePlayers(first).equals(List.of(low, high))
                            && world.getVisiblePlayers(first.getId()).equals(List.of(low, high))
                            && world.getVisiblePlayers(recycled).equals(List.of(ghost)),
                    "map-wide roster is unsigned ID ordered, terrain scoped and limited to published players");
            check(world.hasOtherMapPlayer(first, high) && !world.hasOtherMapPlayer(recycled, ghost)
                            && !world.hasOtherMapPlayer(other, null), "other-player query respects terrain and identity");
            check(world.relocateVisibleObject(low, 105, 106, first)
                    && world.getVisiblePlayers(first).equals(List.of(low, high)), "same-map step keeps the roster");
            check(world.relocateVisibleObject(low, 110, 111, other) && world.getVisiblePlayers(other).equals(List.of(low))
                    && !world.hasOtherMapPlayer(first, high), "cross-map commit updates both rosters");
            world.addVisibleObject(staged);
            check(world.getVisiblePlayers(first).equals(List.of(staged, high)), "enter after registration");
            field(NpcSpeedData.class, "m_npc_speed_data")
                    .set(null, new HashMap<>());
            var template = new L1Npc();
            template.set_npcId(810001);
            template.set_name("map roster fixture");
            template.set_nameid("map roster fixture");
            var npc = new L1NpcInstance(template);
            locate(npc, first, high.getId(), 143, 144);
            fixture.enter(npc);
            world.removeVisibleObject(high);
            check(world.getVisiblePlayers(first).equals(List.of(staged)), "replacement by a nonplayer and late leave");
            final var again = fixture.player(first, high.getId(), 111, 109, true);
            check(world.getVisiblePlayers(first).equals(List.of(staged, again)), "nonplayer replaced by player");
            world.removeVisibleObject(staged);
            check(!world.hasOtherMapPlayer(first, again), "leaving retires roster entry before identity removal");
            final var ground = world.getGroundInventory(29_999, 30_000, (short) other.getId());
            try {
                var moving = fixture.player(first, ground.getId(), 114, 115, true);
                check(!world.relocateVisibleObject(moving, 114, 115, other)
                                && world.getVisiblePlayers(first).equals(List.of(moving, again))
                                && world.getVisiblePlayers(other).equals(List.of(low)),
                        "collision rolls back without adding or removing either map roster entry");
                world.removeObject(moving);
            } finally {
                world.removeVisibleObject(ground);
            }
            world.removeObject(again);
            world.removeObject(ghost);
            check(world.getVisiblePlayers(first).isEmpty() && world.getVisiblePlayers(recycled).isEmpty(),
                    "last leave clears player membership while other map objects remain");
        }
    }

    private static void fastOrderingAndLimits() {
        L1Map terrain = map(3104);
        try (var fixture = new WorldFixture(terrain)) {
            var origin = fixture.object(terrain, 57001, 100, 101);
            var right = fixture.object(terrain, 57002, 109, 101);
            var middle = fixture.object(terrain, 57003, 100, 101);
            var left = fixture.object(terrain, 57004, 92, 101);
            var world = fixture.world;
            var grid = world.getClusterGrid();
            check(grid.queryNearby(origin, 10).equals(List.of(left, middle, right)), "fast list keeps first-seen cell order");
            check(world.getVisibleObjects(origin, 10).equals(List.of(right, middle, left)), "historical list keeps unsigned ID order");

            final var output = new ArrayList<L1Object>(List.of(origin));
            world.fillVisibleObjectsLimit(origin, 10, 2, output);
            check(output.equals(List.of(left, middle)), "limit takes the first matches in existing fast order");
            world.fillVisibleObjectsLimit(origin, 10, 0, output);
            check(output.isEmpty(), "zero limit clears result");
            world.fillVisibleObjectsLimit(origin, 0, 4, output);
            check(output.equals(List.of(middle)), "radius zero and count cap remain independent");

            var outer = grid.borrowNearby(origin, 10);
            var inner = grid.borrowNearby(origin, 0);
            try {
                check(outer != inner && inner.equals(List.of(middle)) && outer.equals(List.of(left, middle, right)),
                        "same-thread nested object query has its own result");
            } finally {
                grid.releaseNearby(inner);
                grid.releaseNearby(outer);
            }
            check(outer.isEmpty() && inner.isEmpty(), "released result buffers no longer retain objects");

            var callbackResult = new ArrayList<L1Object>() {
                @Override public boolean add(L1Object candidate) {
                    if (candidate == middle) {
                        world.leaveAndRemoveObject(candidate);
                        var nested = grid.borrowNearby(origin, 0);
                        try { check(nested.isEmpty(), "collector callback may change and query its own cell"); }
                        finally { grid.releaseNearby(nested); }
                    }
                    return super.add(candidate);
                }
            };
            grid.queryNearby(origin, 10, callbackResult);
            check(callbackResult.equals(List.of(left, middle, right))
                    && grid.queryNearby(origin, 10).equals(List.of(left, right)),
                    "callback sees a materialized snapshot without holding the spatial locks");
        }
    }

    private static void movingAndReplacedIdsAppearOnce() throws Exception {
        L1Map terrain = map(1);
        try (var fixture = new WorldFixture(terrain)) {
            var world = fixture.world;
            var origin = fixture.object(terrain, 58001, 101, 101);
            var moving = fixture.object(terrain, 58002, 94, 101);
            var replacement = object(terrain, moving.getId(), 108, 101);
            fixture.owned.add(replacement);

            var barrier = new AtomicReference<ScanBarrier>();
            Thread reader = Thread.currentThread();
            final L1Object marker = new L1Object() {
                @Override public int getX() {
                    final ScanBarrier active = Thread.currentThread() == reader ? barrier.getAndSet(null) : null;
                    if (active != null) {
                        active.scanning.countDown();
                        await(active.changed, "mutation while scanning middle cell");
                    }
                    return super.getX();
                }
            };
            locate(marker, terrain, 58003, 100, 101);
            fixture.enter(marker);

            var moved = scanWhileMutating(world.getClusterGrid(), origin, barrier,
                    () -> world.relocateVisibleObject(moving, 108, 101, terrain.getId()));
            check(moved.equals(List.of(moving, marker)), "crossing actor appears once at first-seen position");
            world.relocateVisibleObject(moving, 94, 101, terrain.getId());

            var replaced = scanWhileMutating(world.getClusterGrid(), origin, barrier,
                    () -> world.storeAndEnterWorld(replacement));
            check(replaced.equals(List.of(replacement, marker)),
                    "replacement wins its ID at the first-seen position without duplicating it");

            // These late operations retain the original Java instance and numerical ID.
            world.relocateVisibleObject(moving, 110, 101, terrain.getId());
            world.leaveAndRemoveObject(moving);
            check(world.findObject(moving.getId()) == replacement
                    && world.getVisibleObjects(origin, 16).equals(unsigned(replacement, marker)),
                    "completed replacement and stale operations leave exactly the current actor");
        }
    }

    private static void publishedMovementAndCleanup() {
        final L1Map template = map(3105);
        final var maps = L1WorldMap.getInstance();
        try (var fixture = new WorldFixture(template)) {
            final var world = fixture.world;
            final var retired = maps.tryCloneMap(template.getId(), 3106);
            check(retired != null && maps.removeMap(retired), "managed terrain can retire");
            final var current = maps.tryCloneMap(template.getId(), 3106);
            check(current != null && current != retired, "same map number has a new terrain generation");
            try {
                var mover = fixture.object(template, 58501, 101, 101);
                check(!world.relocateVisibleObject(mover, 109, 109, retired) && mover.getMap() == template,
                        "delayed teleport holding retired terrain cannot enter its replacement");
                check(world.relocateVisibleObject(mover, 109, 109, current)
                        && world.getVisiblePoint(new L1Location(109, 109, current), 0).equals(List.of(mover)),
                        "current terrain accepts exact identity relocation");
                check(world.getVisiblePoint(new L1Location(101, 101, template), 0).isEmpty(),
                        "relocation removes the old published cell");
                mover.getLocation().set(127, 127, template);
                world.moveTo(mover, 12, 1, 1); // A delayed caller supplies an older map and tile.
                check(world.getVisiblePoint(new L1Location(109, 109, current), 0).isEmpty()
                        && world.snapshotZoneOccupants(current).isEmpty()
                        && world.getVisiblePoint(new L1Location(127, 127, template), 0).equals(List.of(mover)),
                        "movement resolves both indices from the last published location");

                var player = fixture.player(template, 58502, 145, 145, true);
                var collision = WorldCollisionHash.getInstance();
                check(collision.getPcOccupier((short) template.getId(), 145, 145) == player.getId(),
                        "fixture player occupies its canonical tile");
                int previousCells = world.getClusterGrid().getCellCount();
                player.getLocation().set(172, 172, current);
                world.leaveAndRemoveObject(player);
                check(world.findObject(player.getId()) == null
                        && collision.getPcOccupier((short) template.getId(), 145, 145) == 0
                        && world.getClusterGrid().getCellCount() == previousCells - 1,
                        "leave clears old occupancy and releases the old cell after a live location changes");
            } finally {
                maps.removeMap(current);
            }
        }
    }

    private static List<L1Object> scanWhileMutating(LocalWorldMap grid, L1Object origin,
            AtomicReference<ScanBarrier> barrier, Runnable mutation) throws Exception {
        final var armed = new ScanBarrier();
        final var failure = new AtomicReference<Throwable>();
        barrier.set(armed);
        final Thread mover = Thread.ofPlatform().daemon().start(() -> {
            try {
                await(armed.scanning, "query entered the middle cell");
                mutation.run();
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                armed.changed.countDown();
            }
        });
        final var result = grid.queryNearby(origin, 16);
        mover.join(TimeUnit.SECONDS.toMillis(5));
        check(!mover.isAlive() && armed.scanning.getCount() == 0 && failure.get() == null,
                "fixture reached the intended interleaving: " + failure.get());
        return result;
    }

    private static final class ScanBarrier {
        final CountDownLatch scanning = new CountDownLatch(1);
        final CountDownLatch changed = new CountDownLatch(1);
    }

    private static List<L1Object> unsigned(L1Object... objects) {
        final var result = new ArrayList<>(List.of(objects));
        result.sort((left, right) -> Integer.compareUnsigned(left.getId(), right.getId()));
        return result;
    }

    private static void await(CountDownLatch latch, String message) {
        try { check(latch.await(5, TimeUnit.SECONDS), message); }
        catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failure);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static L1Map map(int id) {
        return new L1V1Map(id, new byte[128][128], 80, 80,
                false, true, true, true, true, true, true, true, true, true, true, false);
    }

    private static L1Object object(L1Map terrain, int id, int x, int y) {
        final var object = new L1Object();
        locate(object, terrain, id, x, y);
        return object;
    }

    private static void locate(L1Object object, L1Map terrain, int id, int x, int y) {
        object.setId(id);
        object.setMap(terrain);
        object.setX(x);
        object.setY(y);
    }

    private static void installMapMetadata() throws Exception {
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

    private static final class WorldFixture implements AutoCloseable {
        final CWorld world = CWorld.getInstance();
        final List<L1Object> owned = new ArrayList<>();
        final L1Map[] terrain;

        WorldFixture(L1Map... terrain) {
            this.terrain = terrain;
            for (L1Map map : terrain) L1WorldMap.getInstance().addMap(map);
        }

        L1Object object(L1Map map, int id, int x, int y) {
            final var object = WorldVisibilityRegression.object(map, id, x, y);
            enter(object);
            return object;
        }

        L1PcInstance player(L1Map map, int id, int x, int y, boolean entered) {
            final var actor = new L1PcInstance();
            locate(actor, map, id, x, y);
            actor.setName("visibility-fixture-" + id);
            if (entered) enter(actor);
            else {
                owned.add(actor);
                world.storeObject(actor);
            }
            return actor;
        }

        void enter(L1Object object) {
            owned.add(object);
            world.storeAndEnterWorld(object);
        }

        @Override public void close() {
            for (L1Object object : owned) world.leaveAndRemoveObject(object);
            final var maps = L1WorldMap.getInstance();
            for (L1Map map : terrain) if (maps.findMap(map.getId()) == map) maps.removeMap(map.getId());
        }
    }
}
