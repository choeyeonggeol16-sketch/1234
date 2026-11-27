package l1j.server.server.model.world.instance;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.character.combat.arena.games.BattleTournamentArea;
import l1j.server.server.model.character.combat.arena.games.BattleTournamentObserver;
import l1j.server.server.model.character.combat.arena.loader.CombatLoadManager;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1V1Map;
import l1j.server.server.shared.EStatus;
import l1j.server.server.shared.builder.DropItemBuilder;
import l1j.server.server.shared.builder.MonsterSpawnBuilder;
import l1j.server.server.shared.geometry.GameRectangle;
import l1j.server.server.shared.listener.MonsterDeathHandler;
import l1j.server.server.shared.observer.AbstractCopyMapObserver;
import l1j.server.server.shared.observer.CopyMapObservable;

/** Race a delayed arena action against retirement and numeric map-ID reuse. */
public final class ArenaDelayedMutationRegression {
    private static final int TEMPLATE = 750;
    private static final int MAP_ID = 30000;
    private static final Runnable NO_CLEANUP = () -> {};

    public static void main(String[] args) throws Exception {
        final L1Map template = new L1V1Map(TEMPLATE, new byte[4][4], 0, 0,
                false, true, true, true, true, true, true, true, true, true, true, false);
        final var terrainById = new ConcurrentHashMap<Integer, L1Map>();
        final var rooms = new CInstantZoneManager<BattleTournamentObserver>(2, new CInstantZoneManager.Maps() {
            @Override public L1Map borrow(int id) { throw new AssertionError("unexpected borrowed map"); }
            @Override public L1Map clone(int source, int id) {
                final L1Map terrain = template.copyMap(id);
                return terrainById.putIfAbsent(id, terrain) == null ? terrain : null;
            }
            @Override public void release(L1Map map) { require(terrainById.remove(map.getId(), map), "wrong release"); }
            @Override public boolean occupied(L1Map map) { return false; }
        });
        final var boss = method(BattleTournamentArea.class, "spawnBoss", int[].class);
        final var drops = method(BattleTournamentArea.class, "dropBonus");
        final var finishBoss = method(BattleTournamentArea.class, "finishBossDeath", L1MonsterInstance.class);
        final var activeSpawn = new CountDownLatch(1);
        final var finishSpawn = new CountDownLatch(1);
        final var delayed = new CountDownLatch(1);
        final var resumeDelayed = new CountDownLatch(1);
        final var old = rooms.tryOpen(TEMPLATE, -1, MAP_ID, MAP_ID);
        final L1Map original = old.map();
        final var observed = new ArrayList<L1Map>();
        final var oldObserver = BattleTournamentObserver.newInstance();
        final var oldArea = register(old, oldObserver, terrainById, observed, () -> {
            activeSpawn.countDown();
            await(finishSpawn);
        });
        CombatLoadManager.COMBAT_BT_CLEAR_BONUS_AMOUNT = 1;
        oldObserver.set_status(EStatus.DISPOSING);
        require(!call(drops, oldArea) && observed.isEmpty(), "work entered the arena during its disposal grace period");
        oldObserver.set_status(EStatus.RUNNING);

        final var queued = CInstanceEvents.start(() -> {
            delayed.countDown();
            await(resumeDelayed);
            require(!call(boss, oldArea, (Object) null) && !call(drops, oldArea)
                    && !call(finishBoss, oldArea, (Object) null), "stale callback attempted a world mutation");
        });
        final var spawning = CInstanceEvents.start(() -> require(call(boss, oldArea, (Object) null), "live spawn skipped"));
        try {
            await(delayed);
            await(activeSpawn);
            old.close(NO_CLEANUP);
            require(CInstantZoneManager.destination(MAP_ID) == old
                    && rooms.tryOpen(TEMPLATE, -1, MAP_ID, MAP_ID) == null,
                    "in-flight mutation did not pin the original map");
            finishSpawn.countDown();
            spawning.get(5L, TimeUnit.SECONDS);
            final var next = rooms.tryOpen(TEMPLATE, -1, MAP_ID, MAP_ID);
            require(next != null && next.map() != original, "fixture did not reuse the retired numeric map ID");
            try {
                final var newArea = register(next, BattleTournamentObserver.newInstance(), terrainById, observed, () -> {});
                resumeDelayed.countDown();
                queued.get(5L, TimeUnit.SECONDS);
                require(call(boss, newArea, (Object) null) && call(drops, newArea), "replacement's own mutations were blocked");
                require(observed.equals(List.of(original, next.map(), next.map())), "wrong terrain received spawn or drop");
            } finally { next.close(NO_CLEANUP); }
        } finally {
            finishSpawn.countDown();
            resumeDelayed.countDown();
            old.close(NO_CLEANUP);
        }
        require(CInstanceEvents.awaitTermination(5L, TimeUnit.SECONDS), "fixture scripts did not retire");
        System.out.println("PASS arena callbacks pin original terrain and reject stale spawn/key/bonus work after ID reuse");
    }

    private static BattleTournamentArea register(CInstantZone<BattleTournamentObserver> zone,
            BattleTournamentObserver observer, ConcurrentHashMap<Integer, L1Map> terrainById,
            List<L1Map> observed, Runnable beforeSpawn) throws ReflectiveOperationException {
        method(AbstractCopyMapObserver.class, "attach", CopyMapObservable.class, short.class, CInstantZone.class)
                .invoke(observer, CopyMapObservable.getInstance(), (short) TEMPLATE, zone);
        zone.bind(observer);
        observer.set_status(EStatus.RUNNING);
        final var area = BattleTournamentArea.newInstance()
                .set_area(GameRectangle.newInstance(1, 1, 3, 3))
                .set_boss_sbuilder(new MonsterSpawnBuilder() {
                    @Override public L1MonsterInstance build(int x, int y, int range, short id, MonsterDeathHandler handler) {
                        beforeSpawn.run();
                        observed.add(terrainById.get((int) id));
                        return null;
                    }
                }).set_drop_builders(new ArrayList<>(List.of(new DropItemBuilder() {
                    @Override public L1ItemInstance build(int left, int top, int right, int bottom, short id) {
                        observed.add(terrainById.get((int) id));
                        return null;
                    }
                })));
        method(BattleTournamentArea.class, "set_terrain", L1Map.class, BattleTournamentObserver.class)
                .invoke(area, zone.map(), observer);
        return area;
    }

    private static Method method(Class<?> owner, String name, Class<?>... parameterTypes) throws ReflectiveOperationException {
        final Method method = owner.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method;
    }

    private static boolean call(Method method, Object receiver, Object... args) {
        try { return (boolean) method.invoke(receiver, args); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private static void await(CountDownLatch latch) {
        try { require(latch.await(5L, TimeUnit.SECONDS), "fixture latch timed out"); }
        catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failure);
        }
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
