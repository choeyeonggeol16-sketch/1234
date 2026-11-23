package l1j.server.server.model.npc.ai;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 20 concurrent NPC owners vying for the eight L1 melee ring cells.
 *
 * Exercises the actual TileOccupancyNpc.MapContext StampedLock and packed-TTL
 * claim implementation, without starting MySQL, world maps, or networking.
 * This is a physical claim-contention regression, NOT an in-game 20-mob soak.
 */
public final class TestNpcTwentyWayReservation {
    private static final int NPCS = 20;
    private static final int RING = 8;
    private static final int BASE_ID = 928100;
    private static final long TTL_MS = 20_000L;

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static long pos(int x, int y) {
        return ((long) x << 32) | (y & 0xffffffffL);
    }

    public static void main(String[] args) throws Exception {
        final Class<?> contextType = Class.forName(
                "l1j.server.server.model.character.TileOccupancy.TileOccupancyNpc$MapContext");
        final Constructor<?> constructor =
                contextType.getDeclaredConstructor(int.class);
        constructor.setAccessible(true);
        final Object context = constructor.newInstance(64);
        final Method reserve = contextType.getDeclaredMethod(
                "tryReserve", long.class, int.class, long.class, long.class);
        final Method cancel = contextType.getDeclaredMethod(
                "releaseReserve", long.class, int.class);
        final Method owner = contextType.getDeclaredMethod(
                "getReservedBy", long.class, long.class);
        reserve.setAccessible(true);
        cancel.setAccessible(true);
        owner.setAccessible(true);

        final long[] ring = {
            pos(30000, 29999), pos(30001, 29999), pos(30001, 30000),
            pos(30001, 30001), pos(30000, 30001), pos(29999, 30001),
            pos(29999, 30000), pos(29999, 29999)
        };
        final int[] chosen = new int[NPCS];
        final long now = System.currentTimeMillis();

        final ExecutorService pool = Executors.newFixedThreadPool(NPCS);
        try {
            for (int round = 0; round < 10; round++) {
                java.util.Arrays.fill(chosen, -1);
                final CountDownLatch ready = new CountDownLatch(NPCS);
                final CountDownLatch go = new CountDownLatch(1);
                @SuppressWarnings("unchecked")
                final Future<?>[] futures = new Future[NPCS];
                final int iteration = round;
                for (int actor = 0; actor < NPCS; actor++) {
                    final int index = actor;
                    futures[actor] = pool.submit(() -> {
                        ready.countDown();
                        try {
                            if (!go.await(5, TimeUnit.SECONDS)) {
                                throw new AssertionError("start barrier timeout");
                            }
                            for (int step = 0; step < RING; step++) {
                                final int slot = (index + iteration + step) % RING;
                                if ((boolean) reserve.invoke(context,
                                        ring[slot], BASE_ID + index,
                                        TTL_MS, now + iteration)) {
                                    chosen[index] = slot;
                                    break;
                                }
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new AssertionError(e);
                        } catch (Exception e) {
                            throw new AssertionError(e);
                        }
                    });
                }
                check(ready.await(5, TimeUnit.SECONDS), "20 contenders did not start");
                go.countDown();
                for (Future<?> future : futures) {
                    future.get(12, TimeUnit.SECONDS);
                }

                final boolean[] used = new boolean[RING];
                int admitted = 0;
                for (int i = 0; i < NPCS; i++) {
                    final int slot = chosen[i];
                    if (slot < 0) continue;
                    check(!used[slot], "double claimed melee slot at round " + round);
                    used[slot] = true;
                    admitted++;
                    final int actualOwner = (int) owner.invoke(context, ring[slot],
                            System.currentTimeMillis());
                    check(actualOwner == BASE_ID + i, "claim ownership corrupted");
                    // A stale target A cannot retire the new target B's claim.
                    cancel.invoke(context, ring[slot], BASE_ID + i + 10000);
                    check((int) owner.invoke(context, ring[slot],
                            System.currentTimeMillis()) == BASE_ID + i,
                            "non-owner canceled a live claim");
                }
                check(admitted == RING, "expected 8/20 admitted, got " + admitted);

                // Simulate attack finish, target death, and NPC stop:
                // each of the eight owners must return exactly its claim.
                for (int i = 0; i < NPCS; i++) {
                    if (chosen[i] >= 0) {
                        cancel.invoke(context, ring[chosen[i]], BASE_ID + i);
                    }
                }
                for (long key : ring) {
                    check((int) owner.invoke(context, key,
                            System.currentTimeMillis()) == 0,
                            "ghost slot after target removal/cancel");
                }
            }
            System.out.println("PASS 20-NPC melee ring contention: 10 rounds, "
                    + "8 exclusive claims, 12 denied, foreign cancel rejected, "
                    + "100% reservation cleanup");
        } finally {
            pool.shutdownNow();
            check(pool.awaitTermination(5, TimeUnit.SECONDS),
                    "reservation test thread pool did not terminate");
        }
    }
}
