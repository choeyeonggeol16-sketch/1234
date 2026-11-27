package l1j.server.server.model.world.instance;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1V1Map;
import l1j.server.server.model.map.L1WorldMap;

public final class InstantZoneShutdownRegression {
    private static final int TEMPLATE = 730;
    private static final int UNBOUND = 20010;
    private static final int PREPARED = 20011;
    private static final int SLOW = 20012;
    private static final long TIMEOUT_SECONDS = 5L;

    public static void main(String[] args) throws Exception {
        borrowedTerrainWaitsForReservedLegacyWriter();
        shutdownReclaimsPreparedMaps();
    }

    private static void borrowedTerrainWaitsForReservedLegacyWriter() throws Exception {
        final L1Map original = newMap(TEMPLATE);
        final L1Map replacement = newMap(TEMPLATE);
        final var writing = new CountDownLatch(1);
        final var finishWrite = new CountDownLatch(1);
        final var borrowing = new CountDownLatch(1);
        final var constructor = L1WorldMap.class.getDeclaredConstructor(Map.class);
        constructor.setAccessible(true);
        final L1WorldMap worldMaps = constructor.newInstance(Map.of());
        final var backing = new ConcurrentHashMap<Integer, L1Map>() {
            @Override public L1Map put(Integer id, L1Map map) {
                if (map == replacement) {
                    writing.countDown();
                    await(finishWrite);
                }
                return super.put(id, map);
            }
        };
        backing.put(TEMPLATE, original);
        final var mapsField = L1WorldMap.class.getDeclaredField("_maps");
        mapsField.setAccessible(true);
        mapsField.set(worldMaps, backing);

        final var manager = new CInstantZoneManager<String>(1, new CInstantZoneManager.Maps() {
            @Override public L1Map borrow(int id) {
                borrowing.countDown();
                return worldMaps.findMapForZoneBorrow(id);
            }
            @Override public L1Map clone(int template, int id) { throw new AssertionError("Unexpected clone"); }
            @Override public void release(L1Map map) { throw new AssertionError("Borrowed terrain must stay"); }
            @Override public boolean occupied(L1Map map) { return false; }
        });
        final var replace = new FutureTask<>(() -> { worldMaps.addMap(replacement); return null; });
        final var open = new FutureTask<>(() -> manager.tryOpen(TEMPLATE, TEMPLATE, UNBOUND, UNBOUND));
        final Thread writer = Thread.ofPlatform().daemon().start(replace);
        Thread reader = null;
        try {
            await(writing);
            reader = Thread.ofPlatform().daemon().start(open);
            await(borrowing);
            final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
            while (reader.getState() != Thread.State.BLOCKED && !open.isDone() && System.nanoTime() < deadline)
                Thread.sleep(1);
            check(reader.getState() == Thread.State.BLOCKED && !open.isDone(),
                    "Borrowing must wait for a map writer that passed the earlier owner check");
            finishWrite.countDown();
            replace.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            final var zone = open.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            check(zone != null && zone.map() == replacement && worldMaps.findMap(TEMPLATE) == replacement,
                    "Borrowing publishes the newly registered terrain generation");
            zone.close(() -> {});
            check(manager.size() == 0 && worldMaps.findMap(TEMPLATE) == replacement,
                    "Closing a borrowed lease preserves its original map registration");
        } finally {
            finishWrite.countDown();
            writer.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
            if (reader != null) reader.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
        }
    }

    private static void shutdownReclaimsPreparedMaps() throws Exception {
        final Maps terrain = new Maps();
        final CInstantZoneManager<String> manager = new CInstantZoneManager<>(3, terrain);
        final CInstantZone<String> unbound = manager.tryOpen(TEMPLATE, -1, UNBOUND, UNBOUND);
        final CInstantZone<String> prepared = manager.tryOpen(TEMPLATE, -1, PREPARED, PREPARED);
        check(unbound != null && prepared != null, "fixture opened before shutdown");
        final AtomicInteger cleanup = new AtomicInteger();
        prepared.bind("prepared encounter");
        prepared.onClose(cleanup::incrementAndGet);

        final var pending = new FutureTask<>(() -> manager.tryOpen(TEMPLATE, -1, SLOW, SLOW));
        final Thread copier = Thread.ofVirtual().start(pending);
        final boolean unboundRemoved;
        final boolean cutoffHeld;
        try {
            await(terrain.enteredCopy);
            check(CInstantZoneManager.destination(SLOW) != null && terrain.byId.get(SLOW) == null,
                    "fixture copied outside the publication gate");
            CInstantZoneManager.stopAccepting();
            CInstantZoneManager.closePrepared();
            CInstantZoneManager.closePrepared();
            unboundRemoved = CInstantZoneManager.destination(UNBOUND) == null
                    && unbound.map() == null && terrain.byId.get(UNBOUND) == null;
            check(prepared.map() == null && cleanup.get() == 1 && terrain.byId.get(PREPARED) == null,
                    "prepared callback runs exactly once");
            terrain.finishCopy.countDown();
            cutoffHeld = pending.get(TIMEOUT_SECONDS, TimeUnit.SECONDS) == null
                    && CInstantZoneManager.destination(SLOW) == null && terrain.byId.get(SLOW) == null;
        } finally {
            terrain.finishCopy.countDown();
            copier.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
        }
        check(unboundRemoved && cutoffHeld,
                "shutdown reclaimed unbound prepared terrain=" + unboundRemoved
                        + ", rejected late terrain publication=" + cutoffHeld);
        check(manager.size() == 0 && terrain.byId.size() == 1 && terrain.releases.get() == 3,
                "all three clone generations were released exactly once");
        System.out.println("PASS instant-zone lifecycle: borrowed writer fence, unbound and prepared shutdown, in-flight clone rollback");
    }

    private static final class Maps implements CInstantZoneManager.Maps {
        final ConcurrentHashMap<Integer, L1Map> byId = new ConcurrentHashMap<>();
        final CountDownLatch enteredCopy = new CountDownLatch(1);
        final CountDownLatch finishCopy = new CountDownLatch(1);
        final AtomicInteger releases = new AtomicInteger();
        final L1V1Map template = newMap(TEMPLATE);

        Maps() { byId.put(TEMPLATE, template); }

        @Override public L1Map borrow(int id) { return byId.get(id); }

        @Override public L1Map clone(int templateId, int id) {
            if (id == SLOW) {
                enteredCopy.countDown();
                await(finishCopy);
            }
            final L1Map clone = template.copyMap(id);
            return byId.putIfAbsent(id, clone) == null ? clone : null;
        }

        @Override public void release(L1Map map) {
            check(byId.remove(map.getId(), map), "exact generation release");
            releases.incrementAndGet();
        }

        @Override public boolean occupied(L1Map map) { return false; }
    }

    private static L1V1Map newMap(int id) {
        return new L1V1Map(id, new byte[4][4], 0, 0,
                false, true, true, true, true, true, true, true, true, true, true, false);
    }

    private static void await(CountDownLatch latch) {
        try { check(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "latch timed out"); }
        catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failure);
        }
    }

    private static void check(boolean condition, String detail) {
        if (!condition) throw new AssertionError(detail);
    }
}
