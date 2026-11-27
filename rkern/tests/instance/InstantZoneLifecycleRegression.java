package l1j.server.server.model.world.instance;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1V1Map;
import l1j.server.server.model.network.thread.CIOThreadCallback;

public final class InstantZoneLifecycleRegression {
    private static final int TEMPLATE = 731;
    private static final int FIRST_CLONE = 20000;
    private static final long TIMEOUT_SECONDS = 5L;

    public static void main(String[] args) throws Exception {
        try {
            simultaneousAdmissionAndRelease();
            failedMapAdmission();
            closeIdentityAndOccupancy();
            pinnedTeleportEntry();
            ordinaryTeleportPinsDestination(true);
            ordinaryTeleportPinsDestination(false);
            scriptFailureAndCancellation();
            occupiedBorrowedMap();
            stopAndRejectEvents();
            System.out.println("PASS instant zones: concurrent capacity, failed startup, occupied retirement, stale close and teleport, event cleanup");
        } finally {
            CInstanceEvents.stop();
            check(CInstanceEvents.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS), "event termination");
            CIOThreadCallback.getInstance().shutdown();
        }
    }

    private static void simultaneousAdmissionAndRelease() throws Exception {
        var maps = new Maps();
        var manager = new CInstantZoneManager<String>(6, maps);
        var other = new CInstantZoneManager<String>(6, maps);
        var ready = new CountDownLatch(64);
        var start = new CountDownLatch(1);
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            var requests = new ArrayList<Future<CInstantZone<String>>>();
            for (int i = 0; i < 64; i++) {
                String encounter = "room-" + i;
                var owner = i % 2 == 0 ? manager : other;
                requests.add(executor.submit(() -> {
                    ready.countDown();
                    await(start);
                    var zone = owner.tryOpen(TEMPLATE, TEMPLATE, FIRST_CLONE, FIRST_CLONE + 23);
                    if (zone != null) zone.bind(encounter);
                    return zone;
                }));
            }
            await(ready);
            start.countDown();

            var opened = new ArrayList<CInstantZone<String>>();
            var ids = new HashSet<Integer>();
            for (var request : requests) {
                var zone = request.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (zone == null) continue;
                opened.add(zone);
                check(ids.add(zone.mapId()), "each admitted zone owns a unique map ID");
                check((manager.get(zone.mapId()) != null || other.get(zone.mapId()) != null)
                        && maps.byId.get(zone.mapId()) == zone.map(),
                        "map registry and encounter lookup match the lease");
            }
            check(opened.size() == 12 && manager.size() == 6 && other.size() == 6 && ids.contains(TEMPLATE),
                    "each manager respects its capacity and both share one reservation for the original terrain");
            check(maps.byId.size() == 12, "rejected concurrent opens did not allocate maps");

            var cleanup = new AtomicInteger();
            var closers = new ArrayList<Future<?>>();
            for (var zone : opened) closers.add(executor.submit(() -> zone.close(cleanup::incrementAndGet)));
            for (var close : closers) close.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            check(cleanup.get() == 12 && manager.size() == 0 && other.size() == 0 && maps.byId.size() == 1
                    && maps.byId.get(TEMPLATE) == maps.template && maps.released.get() == 11,
                    "concurrent close disposes clones exactly once and preserves the borrowed map");
            for (int id : ids) check(manager.get(id) == null && other.get(id) == null,
                    "no retired encounter remains reachable in either manager");
            for (var zone : opened) check(zone.map() == null, "closed handle releases its map");
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private static void failedMapAdmission() {
        var maps = new Maps();
        var manager = new CInstantZoneManager<String>(1, maps);
        L1Map existing = newMap(FIRST_CLONE);
        maps.byId.put(FIRST_CLONE, existing);
        check(manager.tryOpen(TEMPLATE, -1, FIRST_CLONE, FIRST_CLONE) == null
                && manager.size() == 0 && maps.byId.get(FIRST_CLONE) == existing,
                "a pre-existing map refuses lease admission and stays registered");
        maps.byId.remove(FIRST_CLONE, existing);

        maps.failNextClone = true;
        try {
            check(manager.tryOpen(TEMPLATE, -1, FIRST_CLONE, FIRST_CLONE) == null,
                    "failed map clone cannot publish a lease");
        } catch (IllegalStateException failure) {
            check(failure == maps.cloneFailure, "only the injected clone failure may propagate");
        }
        check(manager.size() == 0 && maps.byId.size() == 1, "failure released the map range and owner slot");
        var recovered = open(manager);
        recovered.close(() -> {});
        check(manager.size() == 0 && maps.byId.size() == 1, "subsequent opens recover the same range");
    }

    private static void closeIdentityAndOccupancy() throws Exception {
        var maps = new Maps();
        var manager = new CInstantZoneManager<String>(2, maps);
        var old = open(manager);
        old.bind("old");
        int reused = old.mapId();
        L1Map retiredMap = old.map();
        var cleanup = new AtomicInteger();
        var cleaning = new CountDownLatch(1);
        var permitCleanup = new CountDownLatch(1);
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            maps.occupants.add(retiredMap);
            var close = executor.submit(() -> old.close(() -> {
                cleanup.incrementAndGet();
                cleaning.countDown();
                await(permitCleanup);
            }));
            await(cleaning);
            check(manager.tryOpen(TEMPLATE, -1, reused, reused) == null && maps.byId.get(reused) == retiredMap,
                    "map ID remains owned while cleanup is running");
            permitCleanup.countDown();
            close.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            manager.reap();
            check(manager.size() == 1 && manager.tryOpen(TEMPLATE, -1, reused, reused) == null
                    && maps.byId.get(reused) == retiredMap, "registered occupants defer ID reuse");

            maps.occupants.remove(retiredMap);
            maps.busyGround.add(retiredMap);
            manager.reap();
            check(manager.size() == 1 && maps.byId.get(reused) == retiredMap,
                    "busy ground inventory also defers ID reuse after the last player leaves");
            maps.busyGround.remove(retiredMap);
            manager.reap();
            check(manager.get(reused) == null && maps.byId.get(reused) == null && manager.size() == 0
                    && old.map() == null, "drain releases map and encounter references");
            var current = open(manager);
            current.bind("current");
            check(current.mapId() == reused && current.map() != retiredMap, "reused ID has a new map identity");
            old.close(cleanup::incrementAndGet);
            manager.reap();
            check(cleanup.get() == 1 && "current".equals(manager.get(reused))
                    && maps.byId.get(reused) == current.map(), "repeated stale close cannot remove the new owner");
            current.close(cleanup::incrementAndGet);
            check(cleanup.get() == 2 && manager.size() == 0 && maps.released.get() == 2,
                    "each lifecycle releases exactly one map and cleanup");
        } finally {
            permitCleanup.countDown();
            executor.shutdownNow();
        }
    }

    private static void pinnedTeleportEntry() throws Exception {
        var maps = new Maps();
        var manager = new CInstantZoneManager<String>(1, maps);
        var zone = open(manager);
        zone.bind("destination");
        int id = zone.mapId();
        var destination = CInstantZoneManager.destination(id);
        check(destination == zone, "teleport snapshots the exact destination owner");
        var entered = new CountDownLatch(1);
        var finishEntry = new CountDownLatch(1);
        var cleanup = new AtomicInteger();
        var committed = new AtomicInteger();
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            Future<Boolean> entry = executor.submit(() -> destination.enter(() -> {
                entered.countDown();
                await(finishEntry);
                committed.incrementAndGet();
            }));
            await(entered);
            var close = executor.submit(() -> zone.close(cleanup::incrementAndGet));
            awaitCondition(() -> !destination.enter(() -> {}), "closing destination");
            check(cleanup.get() == 0 && manager.size() == 1 && maps.byId.get(id) != null
                    && manager.tryOpen(TEMPLATE, -1, id, id) == null,
                    "cleanup and map reuse wait for the pinned world entry");
            check(!destination.enter(committed::incrementAndGet), "new entry is refused while closing");
            finishEntry.countDown();
            check(entry.get(TIMEOUT_SECONDS, TimeUnit.SECONDS), "admitted entry commits");
            close.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            awaitRetired(manager, maps);
            var next = open(manager);
            next.bind("new destination");
            check(CInstantZoneManager.destination(id) == next && !destination.enter(committed::incrementAndGet)
                    && committed.get() == 1 && cleanup.get() == 1, "retired teleport token cannot enter replacement zone");
            next.close(() -> {});
        } finally {
            finishEntry.countDown();
            executor.shutdownNow();
        }
    }

    private static void ordinaryTeleportPinsDestination(boolean borrowed) throws Exception {
        var maps = new Maps();
        var manager = new CInstantZoneManager<String>(1, maps);
        int mapId = borrowed ? TEMPLATE : FIRST_CLONE;
        int base = borrowed ? mapId : -1;
        var entered = new CountDownLatch(1);
        var releaseEntry = new CountDownLatch(1);
        var commits = new AtomicInteger();
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            var ordinary = executor.submit(() -> CInstantZoneManager.enterDestination(mapId, null, () -> {
                entered.countDown();
                await(releaseEntry);
                commits.incrementAndGet();
            }));
            await(entered);
            var alternative = manager.tryOpen(TEMPLATE, base, FIRST_CLONE, FIRST_CLONE);
            if (borrowed) {
                check(alternative != null && alternative.mapId() == FIRST_CLONE,
                        "borrowed original map waits for the ordinary teleport; clone remains available");
                alternative.close(() -> {});
            } else {
                check(alternative == null && maps.byId.get(mapId) == null,
                        "clone allocator cannot publish an ID while an ordinary teleport owns that ID");
            }
            check(CInstantZoneManager.destination(mapId) == null && manager.size() == 0,
                    "contending admission retains no reservation");
            releaseEntry.countDown();
            check(ordinary.get(TIMEOUT_SECONDS, TimeUnit.SECONDS), "ordinary teleport completes its pinned admission");
            IllegalStateException failedTransfer = new IllegalStateException("fixture ordinary transfer failure");
            try {
                CInstantZoneManager.enterDestination(mapId, null, () -> { throw failedTransfer; });
                throw new AssertionError("failed movement must propagate");
            } catch (IllegalStateException failure) {
                check(failure == failedTransfer, "exact ordinary transfer failure");
            }
            var zone = manager.tryOpen(TEMPLATE, base, FIRST_CLONE, FIRST_CLONE);
            check(zone != null && zone.mapId() == mapId, "same ID becomes available when ordinary entry retires");
            zone.bind("new encounter");
            check(!CInstantZoneManager.enterDestination(mapId, null, commits::incrementAndGet)
                    && commits.get() == 1, "stale ordinary teleport cannot enter a freshly claimed managed map");
            check(CInstantZoneManager.enterDestination(mapId, zone, commits::incrementAndGet)
                    && commits.get() == 2, "the exact managed generation still accepts entry");
            zone.close(() -> {});
            check(manager.size() == 0, "teleport and zone pins both released");
        } finally {
            releaseEntry.countDown();
            executor.shutdownNow();
        }
    }

    private static void scriptFailureAndCancellation() throws Exception {
        var maps = new Maps();
        var manager = new CInstantZoneManager<String>(1, maps);
        var cleanup = new AtomicInteger();
        var failed = open(manager);
        failed.bind("failed startup");
        IllegalStateException scriptFailure = new IllegalStateException("fixture failed startup");
        Future<?> exception = failed.start(() -> { throw scriptFailure; }, cleanup::incrementAndGet);
        try {
            exception.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            throw new AssertionError("startup script failure was swallowed");
        } catch (ExecutionException e) {
            check(e.getCause() == scriptFailure, "future retains its script failure");
        }
        awaitRetired(manager, maps);
        check(cleanup.get() == 1 && failed.map() == null, "failed startup cleans up once");

        var pending = open(manager);
        pending.bind("cancelled before launch");
        var scriptRuns = new AtomicInteger();
        Future<?> delay = pending.start(scriptRuns::incrementAndGet, cleanup::incrementAndGet, 60_000L);
        check(delay.cancel(false), "pending script can be cancelled");
        awaitRetired(manager, maps);
        check(scriptRuns.get() == 0 && cleanup.get() == 2 && pending.map() == null,
                "delayed cancel owns the same cleanup without running script");

        var running = open(manager);
        running.bind("cancelled while running");
        var entered = new CountDownLatch(1);
        var interrupted = new CountDownLatch(1);
        var releaseScript = new CountDownLatch(1);
        Future<?> inProgress = running.start(() -> {
            entered.countDown();
            try {
                releaseScript.await();
            } catch (InterruptedException stop) {
                interrupted.countDown();
                await(releaseScript);
            }
        }, cleanup::incrementAndGet);
        try {
            await(entered);
            check(inProgress.cancel(true), "running script can be interrupted");
            await(interrupted);
            check(cleanup.get() == 2 && maps.byId.get(running.mapId()) == running.map()
                    && manager.tryOpen(TEMPLATE, -1, FIRST_CLONE, FIRST_CLONE) == null,
                    "running script retains ownership until its body returns");
        } finally {
            releaseScript.countDown();
        }
        awaitRetired(manager, maps);
        check(cleanup.get() == 3 && maps.released.get() == 3 && manager.get(FIRST_CLONE) == null
                && running.map() == null, "all cancelled and failed encounters and maps are released");
    }

    private static void stopAndRejectEvents() throws Exception {
        var maps = new Maps();
        var manager = new CInstantZoneManager<String>(3, maps);
        var scriptRuns = new AtomicInteger();
        var cleanup = new AtomicInteger();
        var futures = new ArrayList<Future<?>>();
        for (int i = 0; i < 3; i++) {
            var waiting = manager.tryOpen(TEMPLATE, -1, FIRST_CLONE, FIRST_CLONE + 2);
            check(waiting != null, "delayed shutdown test room opened");
            waiting.bind("delayed shutdown script-" + i);
            futures.add(waiting.start(scriptRuns::incrementAndGet, () -> {
                cleanup.incrementAndGet();
                throw new IllegalStateException("fixture retirement cleanup failure");
            }, 60_000L));
        }
        CInstanceEvents.stop();
        check(CInstanceEvents.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS), "all admitted event bodies retired");
        check(futures.stream().allMatch(Future::isCancelled) && CInstanceEvents.pendingEvents() == 0
                && scriptRuns.get() == 0 && cleanup.get() == 3 && manager.size() == 0 && maps.byId.size() == 1,
                "shutdown retires every delayed event despite each throwing cleanup");

        var zone = open(manager);
        zone.bind("refused by stopped event owner");
        try {
            Future<?> refused = zone.start(scriptRuns::incrementAndGet, cleanup::incrementAndGet);
            try {
                refused.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                throw new AssertionError("stopped owner admitted a script");
            } catch (ExecutionException failure) {
                check(failure.getCause() instanceof RejectedExecutionException, "future reports admission rejection");
            }
        } catch (RejectedExecutionException expected) {
            // Admission can fail before there is a Future to return.
        }
        awaitRetired(manager, maps);
        check(scriptRuns.get() == 0 && cleanup.get() == 4 && manager.get(FIRST_CLONE) == null
                && zone.map() == null, "rejected startup releases the encounter, callback, and map");

        var prepared = open(manager);
        prepared.bind("prepared room without an event");
        prepared.onClose(cleanup::incrementAndGet);
        CInstantZoneManager.stopAccepting();
        check(manager.tryOpen(TEMPLATE, -1, FIRST_CLONE + 1, FIRST_CLONE + 1) == null,
                "stopping manager rejects new reservations");
        CInstantZoneManager.closePrepared();
        check(prepared.map() == null && cleanup.get() == 5 && manager.size() == 0 && maps.byId.size() == 1,
                "shutdown clears prepared zones and runs their registered cleanup");
    }

    private static void occupiedBorrowedMap() {
        var maps = new Maps();
        var manager = new CInstantZoneManager<String>(1, maps);
        maps.occupants.add(maps.template);
        var zone = manager.tryOpen(TEMPLATE, TEMPLATE, FIRST_CLONE, FIRST_CLONE);
        check(zone != null && zone.mapId() == FIRST_CLONE, "occupied original terrain is bypassed in favor of a clone");
        zone.bind("clone alongside occupied original terrain");
        zone.close(() -> {});
        check(maps.byId.get(TEMPLATE) == maps.template && maps.occupied(maps.template) && maps.released.get() == 1,
                "instance close leaves existing terrain and its occupant alone");
        maps.occupants.remove(maps.template);
        var borrowed = manager.tryOpen(TEMPLATE, TEMPLATE, FIRST_CLONE, FIRST_CLONE);
        check(borrowed != null && borrowed.map() == maps.template, "unoccupied original terrain is borrowable again");
        borrowed.close(() -> {});
        check(maps.byId.get(TEMPLATE) == maps.template && maps.released.get() == 1 && manager.size() == 0,
                "a borrowed original is never removed from the map registry");
    }

    private static CInstantZone<String> open(CInstantZoneManager<String> manager) {
        var zone = manager.tryOpen(TEMPLATE, -1, FIRST_CLONE, FIRST_CLONE);
        check(zone != null, "test clone admitted");
        return zone;
    }

    private static void awaitRetired(CInstantZoneManager<String> manager, Maps maps) {
        awaitCondition(() -> manager.size() == 0 && maps.byId.size() == 1, "map and encounter retirement");
    }

    private static void awaitCondition(BooleanSupplier done, String operation) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (!done.getAsBoolean() && System.nanoTime() - deadline < 0L) LockSupport.parkNanos(100_000L);
        check(done.getAsBoolean(), "timed out waiting for " + operation);
    }

    private static void await(CountDownLatch latch) {
        try { check(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "test latch deadline"); }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static L1Map newMap(int id) {
        return new L1V1Map(id, new byte[2][2], 96, 96, false, false, false, false,
                false, false, false, false, false, true, true, false);
    }

    private static final class Maps implements CInstantZoneManager.Maps {
        final L1Map template = newMap(TEMPLATE);
        final Map<Integer, L1Map> byId = new ConcurrentHashMap<>(Map.of(TEMPLATE, template));
        final Set<L1Map> occupants = ConcurrentHashMap.newKeySet();
        final Set<L1Map> busyGround = ConcurrentHashMap.newKeySet();
        final AtomicInteger released = new AtomicInteger();
        final IllegalStateException cloneFailure = new IllegalStateException("fixture clone failure");
        boolean failNextClone;

        @Override public L1Map borrow(int id) { return byId.get(id); }

        @Override public L1Map clone(int templateId, int id) {
            if (failNextClone) {
                failNextClone = false;
                throw cloneFailure;
            }
            L1Map source = byId.get(templateId);
            if (source == null) return null;
            L1Map clone = source.copyMap(id);
            return byId.putIfAbsent(id, clone) == null ? clone : null;
        }

        @Override public void release(L1Map map) {
            check(map != template && byId.remove(map.getId(), map), "exact clone identity released once");
            released.incrementAndGet();
        }

        @Override public boolean occupied(L1Map map) { return occupants.contains(map); }

        @Override public boolean drainGround(L1Map map) { return !busyGround.contains(map); }
    }
}
