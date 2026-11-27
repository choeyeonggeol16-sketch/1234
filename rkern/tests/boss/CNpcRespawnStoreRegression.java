package l1j.server.server.model.npc.spawn.boss;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import l1j.server.DatabaseFactory;
import l1j.server.server.model.npc.spawn.boss.CNpcRespawnStore.Entry;

/** Point config/Database.ini at a fresh disposable schema named rkern_boss_test_*. */
public final class CNpcRespawnStoreRegression {
    private static final CBossSpawnKey A = new CBossSpawnKey(45578, 33352, 32351, 15430);
    private static final CBossSpawnKey B = new CBossSpawnKey(45578, 33388, 32430, 15430);
    private static final CBossSpawnKey C = new CBossSpawnKey(45578, 33404, 32333, 15430);
    private static final Entry FIRST = new Entry("a", 1_800_000_000_000L, 1_800_000_001_234L);
    private static final Entry NEXT = new Entry("a", FIRST.slot() + 60_000, FIRST.due() + 60_000);
    private static final Entry CHANGED = new Entry("b", NEXT.slot(), NEXT.due() + 1);

    public static void main(String[] args) throws Exception {
        DatabaseFactory.init();
        try (var con = DatabaseFactory.getConnectionInstance(); var sql = con.createStatement()) {
            check(con.getCatalog().startsWith("rkern_boss_test_"), "disposable test schema required");
            try (var tables = sql.executeQuery("SHOW TABLES")) {
                check(!tables.next(), "fresh empty schema required");
            }
            check(CNpcRespawnStore.loadAll().isEmpty(), "first boot creates an empty table");
            Map<CBossSpawnKey, Entry> seed = Map.of(A, FIRST, B, FIRST, C, FIRST);
            check(CNpcRespawnStore.reconcile(Map.of(), seed), "batch bootstrap");
            check(CNpcRespawnStore.loadAll().equals(seed), "next load retains all three placements and exact jitter");
            check(!CNpcRespawnStore.advance(A, null, CHANGED), "duplicate insert refused");
            check(!CNpcRespawnStore.advance(A, new Entry("A", FIRST.slot(), FIRST.due()), NEXT),
                    "fingerprint is case-sensitive");
            check(!CNpcRespawnStore.advance(A, new Entry("a", FIRST.slot(), FIRST.due() + 1), NEXT),
                    "jitter participates in CAS");
            check(!CNpcRespawnStore.advance(A, new Entry("a", FIRST.slot() - 1, FIRST.due()), NEXT),
                    "base slot participates in CAS");

            var mixed = new LinkedHashMap<CBossSpawnKey, Entry>();
            mixed.put(A, NEXT);
            mixed.put(B, null);
            mixed.put(C, NEXT);
            check(!CNpcRespawnStore.reconcile(Map.of(A, FIRST, B, FIRST), mixed), "late duplicate refused");
            check(CNpcRespawnStore.loadAll().equals(seed), "mixed update, delete and insert rolled back atomically");
            check(!CNpcRespawnStore.dispatch(A, FIRST, NEXT, () -> false), "unavailable world refused");
            try {
                CNpcRespawnStore.dispatch(A, FIRST, NEXT, () -> { throw new IllegalStateException("world failure"); });
                throw new AssertionError("world failure swallowed");
            } catch (IllegalStateException expected) {
                check("world failure".equals(expected.getMessage()), "wrong callback failure");
            }
            check(CNpcRespawnStore.loadAll().equals(seed), "refusal and callback exception leave schedule retryable");
            contend();
            mixed.clear();
            mixed.put(A, CHANGED);
            mixed.put(C, null);
            check(CNpcRespawnStore.reconcile(CNpcRespawnStore.loadAll(), mixed), "reload definition and remove placement");
            check(CNpcRespawnStore.loadAll().equals(Map.of(A, CHANGED, B, NEXT)), "reload persisted exactly");
            sql.executeUpdate("UPDATE boss_respawn_state SET due_ms=slot_ms-1 WHERE spawn_x=33352");
            var damaged = CNpcRespawnStore.loadAll();
            check(damaged.get(A).due() < damaged.get(A).slot(), "invalid stored time remains readable");
            check(CNpcRespawnStore.reconcile(damaged, Map.of(A, CHANGED)), "reload repairs invalid stored time by exact match");
            check(CNpcRespawnStore.loadAll().equals(Map.of(A, CHANGED, B, NEXT)), "repair affects only the damaged placement");
            sql.executeUpdate("ALTER TABLE boss_respawn_state ENGINE=MyISAM");
            try {
                CNpcRespawnStore.loadAll();
                throw new AssertionError("nontransactional table accepted");
            } catch (SQLException expected) {
                check(expected.getMessage().contains("InnoDB"), "wrong schema failure");
            }
            sql.executeUpdate("DROP TABLE boss_respawn_state");
            try {
                CNpcRespawnStore.dispatch(A, CHANGED, NEXT, () -> { throw new AssertionError("published without table"); });
                throw new AssertionError("failed DB dispatch accepted");
            } catch (SQLException expected) {
                check("42S02".equals(expected.getSQLState()), "missing table error not propagated");
            }
            System.out.println("PASS boss checkpoint bootstrap/reload, exact placement/slot CAS, concurrent dispatch, rollback and SQL failure");
        } finally {
            DatabaseFactory.close();
        }
    }

    private static void contend() throws Exception {
        var inside = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var calls = new AtomicInteger();
        try (var pool = Executors.newFixedThreadPool(2)) {
            try {
                var first = pool.submit(() -> CNpcRespawnStore.dispatch(A, FIRST, NEXT, () -> {
                    calls.incrementAndGet();
                    inside.countDown();
                    await(release);
                    return true;
                }));
                await(inside);
                var competing = pool.submit(() -> CNpcRespawnStore.dispatch(A, FIRST, CHANGED, () -> {
                    calls.incrementAndGet();
                    return true;
                }));
                check(CNpcRespawnStore.advance(B, FIRST, NEXT), "neighbor placement can advance during publication");
                check(CNpcRespawnStore.loadAll().get(A).equals(FIRST), "pending publication stays uncommitted");
                release.countDown();
                check(first.get(10, TimeUnit.SECONDS), "publishing owner committed");
                check(!competing.get(10, TimeUnit.SECONDS), "competing stale owner refused");
                check(calls.get() == 1 && CNpcRespawnStore.loadAll().get(A).equals(NEXT), "exactly one callback and next slot");
            } finally {
                release.countDown();
            }
        }
    }

    private static void await(CountDownLatch latch) {
        try { check(latch.await(10, TimeUnit.SECONDS), "timed out awaiting transaction"); }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
