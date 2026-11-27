package l1j.server.server.model;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import l1j.server.server.datatables.MapsTable;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1V1Map;

import sun.misc.Unsafe;

public final class WorldBroadcastAudienceRegression {
    public static void main(String[] args) throws Exception {
        movingPlayerIsPresentOnce();
        recycledMapIdRemainsIsolated();
        clanGazeSnapshotRemainsStable();
        failingSightUpdateDoesNotAbortNestedFanout();
        canonicalWorldKeepsIdentityAndVisibilityInSync();
        System.out.println("PASS world broadcast audience regression");
    }

    private static void movingPlayerIsPresentOnce() throws Exception {
        final LocalWorldMap grid = new LocalWorldMap();
        final L1Map map = map();
        final L1PcInstance origin = player(map, 1, 101);
        final L1PcInstance moving = player(map, 2, 94);
        final L1PcInstance marker = player(map, 3, 100);
        grid.enterSilently(moving);
        grid.enterSilently(marker);

        // These cells are visited in increasing X order and use separate stripes.
        // Wait until the audience has collected the moving player in the first cell.
        final CountDownLatch atMarker = new CountDownLatch(1);
        final CountDownLatch moved = new CountDownLatch(1);
        final Thread migration = Thread.ofPlatform().daemon().start(() -> {
            await(atMarker);
            moving.setX(108);
            grid.relocate(moving);
            moved.countDown();
        });
        final List<L1PcInstance> audience = new ArrayList<>() {
            @Override public boolean add(L1PcInstance player) {
                if (player == marker) {
                    atMarker.countDown();
                    await(moved);
                }
                return super.add(player);
            }
        };
        grid.queryNearbyPlayers(origin, 16, audience);
        migration.join(TimeUnit.SECONDS.toMillis(5));
        check(!migration.isAlive(), "movement and broadcast lookup completed");
        check(audience.equals(List.of(moving, marker)), "moving player appears once in first-seen order");
    }

    private static void recycledMapIdRemainsIsolated() {
        final LocalWorldMap grid = new LocalWorldMap();
        final L1Map previous = map();
        final L1Map current = map();
        final L1PcInstance origin = player(previous, 1, 101);
        final L1PcInstance sameRoom = player(previous, 2, 101);
        final L1PcInstance newRoom = player(current, 3, 101);
        grid.enterSilently(sameRoom);
        grid.enterSilently(newRoom);
        check(grid.queryScreenPlayers(origin).equals(List.of(sameRoom)), "nearby audience excludes recycled map");
        check(origin.canSeeObject(sameRoom) && !origin.canSeeObject(newRoom), "sight excludes recycled map");
    }

    private static void clanGazeSnapshotRemainsStable() {
        final L1Clan clan = new L1Clan();
        clan.addGazelist("One");
        clan.addGazelist("One");
        final List<String> previous = clan.getGazeList();
        clan.removeGazelist("One");
        clan.addGazelist("Two");
        check(previous.equals(List.of("One")), "queued clan audience keeps its original roster");
        check(clan.getGazeList().equals(List.of("Two")), "next clan audience sees the new roster");
    }

    private static void failingSightUpdateDoesNotAbortNestedFanout() {
        final L1Map map = map();
        final L1PcInstance trigger = player(map, 21, 100);
        final L1PcInstance failing = player(map, 22, 101);
        final L1PcInstance healthy = player(map, 23, 102);
        trigger.setClusterMapId(map.getId());
        failing.setClusterMapId(map.getId());
        healthy.setClusterMapId(map.getId());
        final AtomicBoolean processedHealthy = new AtomicBoolean();
        final L1PcInstance viewer = new L1PcInstance() {
            @Override public void reconcileSight(L1Object observed) {
                if (observed == failing) throw new IllegalStateException("simulated sight failure");
                if (observed == healthy) processedHealthy.set(true);
                if (observed == trigger) {
                    getCreatureController().ReconcileSight(failing, false);
                    getCreatureController().ReconcileSight(healthy, false);
                }
            }
        };
        viewer.setId(24);
        viewer.getLocation().set(100, 100, map);
        new LocalWorldMap().enterSilently(viewer);
        viewer.getCreatureController().ReconcileSight(trigger, false);
        check(processedHealthy.get(), "failed reentrant sight update cannot abort later work");
    }

    private static void canonicalWorldKeepsIdentityAndVisibilityInSync() throws Exception {
        // Supply only map definitions. CWorld and all its registries/serializer are real.
        final Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        final Unsafe allocator = (Unsafe) unsafeField.get(null);
        final MapsTable table = (MapsTable) allocator.allocateInstance(MapsTable.class);
        final Field maps = MapsTable.class.getDeclaredField("_maps");
        maps.setAccessible(true);
        maps.set(table, Map.of());
        final Field mapSingleton = MapsTable.class.getDeclaredField("_instance");
        mapSingleton.setAccessible(true);
        mapSingleton.set(null, table);

        final CWorld world = CWorld.getInstance();
        final L1Map room = map();
        final L1PcInstance origin = player(room, 31, 100);
        origin.setName("origin");
        final L1PcInstance peer = player(room, 32, 101);
        peer.setName("old peer");
        final L1PcInstance differentRoom = player(map(), 33, 102);
        differentRoom.setName("different room");
        final L1PcInstance replacement = player(room, 32, 103);
        replacement.setName("replacement");

        try {
            world.storeAndEnterWorld(origin);
            check(world.AsyncStoreAndEnterWorld(peer), "canonical serializer admitted the peer");
            world.storeAndEnterWorld(differentRoom);
            final long oldIdentity = world.smartIdOf(peer);
            check(oldIdentity != 0 && world.findObjectBySmartId(oldIdentity) == peer,
                    "ID and smart-ID lookup share the active registration");
            check(world.getPlayer("OLD PEER") == peer && world.getAllPlayers().contains(peer),
                    "name and player roster share the active registration");
            check(world.getVisiblePlayerFast(origin).equals(List.of(peer)),
                    "world AOI excludes a separate instance with the same map ID");

            check(world.AsyncStoreAndEnterWorld(replacement), "canonical serializer admitted the ID replacement");
            check(world.AsyncRemoveObject(peer), "stale asynchronous removal admitted harmlessly");
            check(world.ExecutableBacklog() == 0, "world serializer drained in the caller");
            check(world.findObject(peer.getId()) == replacement && world.findObjectBySmartId(oldIdentity) == null,
                    "stale identity and removal cannot affect the replacement");
            check(world.getPlayer("old peer") == null && world.getPlayer("REPLACEMENT") == replacement,
                    "ID replacement retires the original name entry");
            check(world.getVisiblePlayerFast(origin).equals(List.of(replacement)),
                    "world AOI retires the original and observes only the replacement");

            world.clear();
            final CWorld next = CWorld.getInstance();
            check(next != world && CWorld.getInstance() == next,
                    "reset publishes a different stable world for stale callback checks");
            check(next.findObject(replacement.getId()) == null && next.getAllPlayers().isEmpty()
                            && next.getVisiblePlayerFast(origin).isEmpty(),
                    "replacement world starts with empty identity and AOI registries");
        } finally {
            world.leaveAndRemoveObject(origin);
            world.leaveAndRemoveObject(replacement);
            world.leaveAndRemoveObject(differentRoom);
        }
    }

    private static L1Map map() {
        return new L1V1Map(1, new byte[32 * 32], 32, 32, 88, 88,
                false, true, true, true, true, true, true, true, true, true, true, false);
    }

    private static L1PcInstance player(L1Map map, int id, int x) {
        final L1PcInstance player = new L1PcInstance();
        player.setId(id);
        player.setMap(map);
        player.setX(x);
        player.setY(101);
        return player;
    }

    private static void await(CountDownLatch latch) {
        try {
            check(latch.await(5, TimeUnit.SECONDS), "timed out waiting for cell migration");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
