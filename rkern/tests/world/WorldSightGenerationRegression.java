package l1j.server.server.model;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import l1j.server.Config;
import l1j.server.TextMapReader;
import l1j.server.server.datatables.MapsTable;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1V1Map;
import l1j.server.server.model.map.L1WorldMap;
import l1j.server.server.model.network.core.connection.CUserSocket;
import sun.misc.Unsafe;

public final class WorldSightGenerationRegression {
    public static void main(String[] args) throws Exception {
        installMapMetadata();
        Config.PC_RECOGNIZE_RANGE = 8;
        switch (args.length == 0 ? "all" : args[0]) {
            case "detach" -> detachedViewerCannotAcquireSight();
            case "terrain" -> reusedMapNumberStillReconcilesGround();
            case "remove" -> retiringOldIdentityPreservesReplacement();
            case "direct" -> detachedViewerCannotAcquireDirectSight();
            case "all" -> {
                detachedViewerCannotAcquireSight();
                reusedMapNumberStillReconcilesGround();
                retiringOldIdentityPreservesReplacement();
                detachedViewerCannotAcquireDirectSight();
            }
            default -> throw new IllegalArgumentException("Unknown sight test");
        }
        System.out.println("PASS queued/direct sight retirement, reused terrain and replacement removal");
    }

    private static void detachedViewerCannotAcquireSight() throws Exception {
        final var grid = new LocalWorldMap();
        final L1Map map = terrain(3109);
        final var blocker = at(new L1Object(), map, 9_001);
        final var nearby = at(new L1Object(), map, 9_002);
        final var running = new CountDownLatch(1);
        final var release = new CountDownLatch(1);
        final var viewer = at(new RecordingViewer() {
            @Override public void reconcileSight(L1Object observed) {
                if (observed == blocker) {
                    running.countDown();
                    await(release);
                } else {
                    super.reconcileSight(observed);
                }
            }
        }, map, 9_003);
        grid.enterSilently(viewer);
        grid.enterSilently(blocker);
        grid.enterSilently(nearby);
        final var first = new FutureTask<>(() -> viewer.getCreatureController().ReconcileSight(blocker, false));
        Thread.ofPlatform().daemon().start(first);
        try {
            await(running);
            check(viewer.getCreatureController().ReconcileSight(nearby, false), "earlier work admits another sight callback");
            check(viewer.getCreatureController().RefreshSight(), "full refresh is queued");
            grid.leave(viewer);
        } finally {
            release.countDown();
        }
        check(first.get(5, TimeUnit.SECONDS), "original controller callback completed");
        check(viewer.seen.isEmpty() && viewer.refreshes == 0, "detached viewer cannot perceive or refresh");
        check(viewer.removed.equals(List.of(nearby)), "older pending callback only cleans known state");
        check(grid.getCellCount() == 1, "neighbor cell survives one viewer leaving");
    }

    private static void reusedMapNumberStillReconcilesGround() {
        final var grid = new LocalWorldMap();
        final L1Map previous = terrain(3110);
        final L1Map replacement = terrain(3110);
        final var viewer = at(new RecordingViewer(), previous, 9_004);
        final var oldDrop = at(new L1ItemInstance(), previous, 9_005);
        final var newDrop = at(new L1ItemInstance(), replacement, 9_006);
        grid.enterSilently(viewer);
        grid.enterSilently(oldDrop);
        grid.enterSilently(newDrop);
        viewer.setMap(replacement);
        grid.relocate(viewer, previous.getId(), viewer.getX(), viewer.getY());
        check(viewer.seen.equals(List.of(oldDrop, newDrop)),
                "same numeric map and tile must still reconcile old and new ground generations");

        viewer.seen.clear();
        grid.relocate(viewer, replacement.getId(), viewer.getX(), viewer.getY());
        check(viewer.seen.isEmpty(), "unchanged terrain keeps the ground-only fast path");
    }

    private static void retiringOldIdentityPreservesReplacement() throws Exception {
        final L1Map map = terrain(3111);
        L1WorldMap.getInstance().addMap(map);
        final var world = CWorld.getInstance();
        final var old = at(new L1Object(), map, 9_007);
        final var replacement = at(new L1Object(), map, old.getId());
        final var viewer = at(new L1PcInstance() {
            int removeSends;
            @Override public CUserSocket getNetConnection() {
                // outOfSight obtains the socket only when it elects to send SC_RemoveObject.
                removeSends++;
                return null;
            }
        }, map, 9_008);
        world.storeAndEnterWorld(old);
        viewer.addKnownObject(old);
        world.storeAndEnterWorld(replacement);
        viewer.addKnownObject(replacement);
        try {
            viewer.outOfSight(old);
            check(!viewer.knownsObject(old) && viewer.knownsObject(replacement), "old known identity is discarded");
            check(viewer.removeSends == 0, "old removal cannot emit the replacement's wire ID");
            world.leaveAndRemoveObject(replacement);
            viewer.outOfSight(replacement);
            check(!viewer.knownsObject(replacement) && viewer.removeSends == 1,
                    "departing current identity still elects to send remove");
        } finally {
            world.leaveAndRemoveObject(replacement);
            L1WorldMap.getInstance().removeMap(map);
        }
    }

    private static void detachedViewerCannotAcquireDirectSight() {
        final L1Map map = terrain(3112);
        final var world = CWorld.getInstance();
        final var viewer = at(new RecordingViewer(), map, 9_009);
        final var nearby = at(new L1Object() {
            int perceptions;
            @Override public void onPerceive(L1PcInstance pc) { perceptions++; }
        }, map, 9_010);
        L1WorldMap.getInstance().addMap(map);
        world.storeAndEnterWorld(nearby);
        try {
            viewer.onWorldMove(map.getId(), viewer.getX(), viewer.getY());
            viewer.inSight(nearby);
            check(viewer.seen.isEmpty() && nearby.perceptions == 0, "detached direct sight does not perceive neighbors");

            world.getClusterGrid().enterSilently(viewer);
            viewer.loggingOut = true;
            viewer.onWorldMove(map.getId(), viewer.getX(), viewer.getY());
            viewer.inSight(nearby);
            check(viewer.seen.isEmpty() && nearby.perceptions == 0, "logging-out direct sight stops further acquisition");

            viewer.loggingOut = false;
            viewer.onWorldMove(map.getId(), viewer.getX(), viewer.getY());
            viewer.inSight(nearby);
            check(viewer.seen.equals(List.of(nearby)) && nearby.perceptions == 1,
                    "world-entered direct sight continues to perceive neighbors");
        } finally {
            world.getClusterGrid().leaveSilently(viewer);
            world.leaveAndRemoveObject(nearby);
            L1WorldMap.getInstance().removeMap(map);
        }
    }

    private static class RecordingViewer extends L1PcInstance {
        final List<L1Object> seen = new ArrayList<>();
        final List<L1Object> removed = new ArrayList<>();
        int refreshes;
        boolean loggingOut;

        @Override public int groundSightEpoch() { return 1; }
        @Override public boolean isLogoutStarted() { return loggingOut; }
        @Override public void reconcileSight(L1Object observed) { seen.add(observed); }
        @Override public void outOfSight(L1Object observed) { removed.add(observed); }
        @Override public void refreshSight() { refreshes++; }
    }

    private static L1Map terrain(int id) {
        return new L1V1Map(id, new byte[128][128], 80, 80,
                false, true, true, true, true, true, true, true, true, true, true, false);
    }

    private static <T extends L1Object> T at(T object, L1Map map, int id) {
        object.setId(id);
        object.getLocation().set(100, 100, map);
        return object;
    }

    private static void installMapMetadata() throws ReflectiveOperationException {
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
        try {
            check(latch.await(5, TimeUnit.SECONDS), "test barrier timed out");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failure);
        }
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
