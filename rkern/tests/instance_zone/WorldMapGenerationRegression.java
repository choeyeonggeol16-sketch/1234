package l1j.server.server.model.map;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import l1j.server.MapReader;

public final class WorldMapGenerationRegression {
    public static void main(String[] args) throws Exception {
        failedStartup();
        var entered = new CountDownLatch(1);
        var proceed = new CountDownLatch(1);
        final L1V1Map template = new L1V1Map(10, new byte[4][4], 0, 0, false, false, false,
                false, false, false, false, false, false, true, true, false) {
            @Override public L1V1Map copyMap(int id) {
                if (id == 22) throw new IllegalArgumentException("fixture invalid terrain");
                if (id == 20) {
                    entered.countDown();
                    try {
                        if (!proceed.await(5, TimeUnit.SECONDS)) throw new AssertionError("copy blocked");
                    } catch (InterruptedException e) { throw new AssertionError(e); }
                }
                return super.copyMap(id);
            }
        };
        L1WorldMap maps = new L1WorldMap(Map.of(10, template));
        var first = new FutureTask<>(() -> maps.tryCloneMap(10, 20));
        Thread worker = Thread.ofPlatform().daemon().start(first);
        try {
            check(entered.await(5, TimeUnit.SECONDS), "copy in progress");
            check(maps.findMap(20) == null && maps.tryCloneMap(10, 20) == null, "exclusive reservation");
            maps.removeMap(20);
            check(maps.tryCloneMap(10, 21) != null, "another ID progresses");
        } finally { proceed.countDown(); }
        var a = first.get(5, TimeUnit.SECONDS);
        worker.join();
        maps.removeMap(20);
        check(maps.findMap(20) == a && !maps.removeMap(template), "legacy cleanup and template protected");
        boolean rejected = false;
        try { maps.cloneMap(10, 20); } catch (IllegalStateException expected) { rejected = true; }
        check(rejected && maps.findMap(20) == a, "legacy clone cannot replace a managed zone");
        check(maps.removeMap(a), "first generation released");
        var b = maps.tryCloneMap(10, 20);
        check(b != a && !maps.removeMap(a) && maps.findMap(20) == b, "late cleanup preserves replacement");
        try { maps.tryCloneMap(10, 22); throw new AssertionError("expected copy failure"); }
        catch (IllegalArgumentException expected) { }
        var next = maps.tryCloneMap(21, 22);
        check(next != null && maps.removeMap(next), "failed copy releases reservation");
        System.out.println("PASS world map reservation, concurrent copies, rollback and identity removal");
    }

    private static void failedStartup() {
        final IOException cause = new IOException("unreadable map file");
        MapReader reader = new MapReader() {
            @Override public Map<Integer, L1Map> read() throws IOException { throw cause; }
            @Override public L1Map read(int id) { throw new AssertionError("single map lookup"); }
        };
        try {
            new L1WorldMap(reader);
            throw new AssertionError("failed map read was accepted");
        } catch (IllegalStateException expected) {
            check(expected.getCause() == cause, "startup lost the map read error");
        }
    }

    private static void check(boolean result, String message) {
        if (!result) throw new AssertionError(message);
    }
}
