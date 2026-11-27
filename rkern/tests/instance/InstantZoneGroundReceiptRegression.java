package l1j.server.server.model.world.instance;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import l1j.server.Config;
import l1j.server.TextMapReader;
import l1j.server.server.datatables.MapsTable;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.character.animation.NpcSpeedData;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Location;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.inventory.L1GroundInventory;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1V1Map;
import l1j.server.server.model.map.L1WorldMap;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.npc.ai.CNPCServer;
import l1j.server.server.model.world.CWorldWork;
import l1j.server.server.templates.L1EtcItem;
import l1j.server.server.templates.L1Npc;
import sun.misc.Unsafe;

/** Fresh JVM. Exercises production map retirement, world indices and ground-reservation state. */
public final class InstantZoneGroundReceiptRegression {
    private static final int TEMPLATE = 2600;
    private static final short ROOM = 2601;
    private static final int X = 32770;
    private static final int Y = 32770;

    public static void main(String[] args) throws Exception {
        installTestTerrain();
        Config.ALT_ITEM_DELETION_TYPE = "disabled";
        try {
            run();
            retiredGroundAndConcurrentPublication();
            partyDropPreventsReuse();
        } finally {
            CInstantZoneManager.stopAccepting();
            CInstanceWork.stopAccepting();
            CWorldWork.stopAccepting();
            CNPCServer.getInstance().shutdown();
            CIOThreadCallback.getInstance().shutdown();
            require(CInstanceWork.awaitDrained(5, TimeUnit.SECONDS), "instance work drained");
            require(CWorldWork.awaitDrained(5, TimeUnit.SECONDS), "world work drained");
            require(CIOThreadCallback.getInstance().awaitTermination(5, TimeUnit.SECONDS), "CIO stopped");
        }
        System.out.println("PASS world room retirement waits for ground and party-drop settlement, then releases exact NPC, map and tile");
    }

    private static void run() {
        final var manager = new CInstantZoneManager<String>(1);
        final var zone = manager.tryOpen(TEMPLATE, -1, ROOM, ROOM);
        require(zone != null, "real map clone available");
        zone.bind("original");
        final L1Map terrain = zone.map();
        final CWorld world = CWorld.getInstance();
        final L1PcInstance occupant = new L1PcInstance();
        occupant.setId(2_000_401_001);
        occupant.setName("ZoneGroundFixture");
        occupant.setMap(terrain);
        occupant.setX(X);
        occupant.setY(Y);
        world.storeAndEnterWorld(occupant);
        require(world.getVisiblePlayers(terrain).contains(occupant), "world sees exact room occupant");

        final L1GroundInventory tile = world.getGroundInventory(X, Y, ROOM);
        final var ordinary = drop(world, tile, terrain, 2_000_401_002);
        final var pending = drop(world, tile, terrain, 2_000_401_003);
        final var receipt = new Object();
        require(pending.reserveGroundPickup(receipt), "ground pickup reservation acquired");

        final var swept = drop(world, tile, terrain, 2_000_401_004);
        require(world.deleteGroundItem(swept, terrain) && world.findObject(swept.getId()) == null
                && tile.GetByServerID(swept.getId()) == null, "administrative sweep removes a settled ground stack");
        require(!world.deleteGroundItem(pending, terrain) && pending.hasGroundPickupReservation(receipt)
                && world.findObject(pending.getId()) == pending && tile.GetByServerID(pending.getId()) == pending,
                "administrative sweep preserves an active pickup and its canonical world identity");

        zone.close(() -> {});
        require(world.findObject(ordinary.getId()) == ordinary && world.findObject(pending.getId()) == pending,
                "occupied closing room keeps its existing ground items");
        world.leaveAndRemoveObject(occupant);
        manager.reap();

        require(world.findObject(ordinary.getId()) == null, "ordinary ground item removed after departure");
        require(world.findObject(pending.getId()) == pending && tile.GetByServerID(pending.getId()) == pending,
                "receipt-owned ground item and its original tile remain published");
        require(world.findGroundInventory(X, Y, ROOM) == tile, "pending room keeps the world tile");
        require(L1WorldMap.getInstance().findMap(ROOM) == terrain
                && manager.tryOpen(TEMPLATE, -1, ROOM, ROOM) == null, "pending receipt prevents map ID reuse");

        pending.releaseGroundPickup(new Object());
        manager.reap();
        require(pending.hasGroundPickupReservation(receipt) && L1WorldMap.getInstance().findMap(ROOM) == terrain,
                "unrelated completion cannot release the pending room");
        // The receipt settlement boundary releases this exact token, as it
        // does when a pickup is rolled back with the original item intact.
        pending.releaseGroundPickup(receipt);
        manager.reap();
        require(world.findObject(pending.getId()) == null && world.findGroundInventory(X, Y, ROOM) == null,
                "settled item and tile both retire");
        require(CInstantZoneManager.destination(ROOM) == null && L1WorldMap.getInstance().findMap(ROOM) == null,
                "retirement releases the room ID and terrain together");
        final var next = manager.tryOpen(TEMPLATE, -1, ROOM, ROOM);
        require(next != null && next.map() != terrain, "same ID accepts a fresh terrain generation");
        next.bind("replacement");
        next.close(() -> {});
    }

    private static void partyDropPreventsReuse() throws Exception {
        final var world = CWorld.getInstance();
        final var manager = new CInstantZoneManager<String>(1);
        final var zone = manager.tryOpen(TEMPLATE, -1, ROOM, ROOM);
        require(zone != null, "party-drop room opened");
        zone.bind("pending party drop");
        final L1Map terrain = zone.map();

        field(NpcSpeedData.class, "m_npc_speed_data").set(null, new HashMap<>());
        final var template = new L1Npc();
        template.set_npcId(810001);
        template.set_name("Party drop fixture");
        template.set_nameid("Party drop fixture");
        template.set_hp(100);
        final var corpse = new L1MonsterInstance(template);
        final int npcId = 2_000_401_004;
        corpse.setId(npcId);
        corpse.setMap(terrain);
        corpse.setX(X);
        corpse.setY(Y);
        world.storeAndEnterWorld(corpse);
        corpse.setDead(true);

        // Hold the actual admitted settlement after distribution and before the
        // owner clears it. Its empty source keeps this scenario independent of loot tables.
        final int epoch = 17;
        final Field pending = field(L1MonsterInstance.class, "_pendingPartyDrop");
        final Class<?> jobType = pending.getType();
        final var constructor = jobType.getDeclaredConstructor(L1MonsterInstance.class, L1Character.class, int.class);
        constructor.setAccessible(true);
        final Object job = constructor.newInstance(corpse, null, epoch);
        field(jobType, "source").set(job, corpse.getInventory());
        field(jobType, "routed").setBoolean(job, true);
        pending.set(corpse, job);

        zone.close(corpse::deleteMe);
        corpse.DispatchPartyDrop(epoch + 1);
        manager.reap();
        require(corpse.hasPendingPartyDrop() && world.findObject(npcId) == corpse && zone.map() == terrain
                && manager.tryOpen(TEMPLATE, -1, ROOM, ROOM) == null,
                "stale death ticket and removal request cannot bypass a pending drop or reuse the map");

        corpse.DispatchPartyDrop(epoch);
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (world.findObject(npcId) != null && System.nanoTime() - deadline < 0) LockSupport.parkNanos(100_000L);
        require(!corpse.hasPendingPartyDrop() && world.findObject(npcId) == null,
                "the exact settlement completes the deferred NPC removal");
        manager.reap();
        require(zone.map() == null && L1WorldMap.getInstance().findMap(ROOM) == null,
                "settled party drop releases the retired clone");
        final var next = manager.tryOpen(TEMPLATE, -1, ROOM, ROOM);
        require(next != null && next.map() != terrain, "settled numeric ID can host a fresh zone");
        next.close(() -> {});
    }

    private static void retiredGroundAndConcurrentPublication() throws Exception {
        final short id = ROOM + 10;
        final var maps = L1WorldMap.getInstance();
        final var world = CWorld.getInstance();
        final var original = maps.cloneMapAndGet(TEMPLATE, id);
        final var retired = world.getGroundInventory(X, Y, id);
        final var item = drop(world, retired, original, 2_000_401_017);
        final Object token = new Object();
        require(item.reserveGroundPickup(token), "original receipt holds the ground item");
        // Emulate an older administrative map importer that swaps terrain independently of CInstantZone.
        final var current = maps.cloneMapAndGet(TEMPLATE, id);
        require(world.findGroundInventory(X, Y, id) == null
                && world.getInventory(new L1Location(X, Y, original)) == null
                && world.getInventory(new L1Location(X, Y, current)) == null,
                "neither generation can acquire the retired, still reserved tile");
        retired.clearItems();
        require(retired.GetByServerID(item.getId()) == item && item.hasGroundPickupReservation(token),
                "rejected creation preserves original item and its exact receipt");
        item.releaseGroundPickup(token);
        require(!world.deleteGroundItem(item, original) && !world.deleteGroundItem(item, current)
                && retired.GetByServerID(item.getId()) == item, "administrative sweep cannot cross recycled terrain");
        retired.clearItems();
        require(retired.getSize() == 0 && world.findObject(item.getId()) == null, "original cleanup completes");

        final CountDownLatch start = new CountDownLatch(1);
        final ArrayList<Future<L1GroundInventory>> results = new ArrayList<>();
        final L1GroundInventory replacement;
        try (var pool = Executors.newFixedThreadPool(8)) {
            for (int i = 0; i < 8; i++) results.add(pool.submit(() -> {
                start.await();
                return world.getGroundInventory(X, Y, id);
            }));
            start.countDown();
            replacement = results.get(0).get(5, TimeUnit.SECONDS);
            for (var result : results) require(result.get(5, TimeUnit.SECONDS) == replacement,
                    "all concurrent creators receive the one published replacement");
        }
        world.removeVisibleObject(retired);
        require(replacement != retired && replacement.getMap() == current
                && world.findGroundInventory(X, Y, id) == replacement
                && world.getMapObjects(id).size() == 1, "late old leave preserves the current tile");
        require(world.getInventory(new L1Location(X, Y, original)) == null, "old location cannot select new terrain");
        require(world.getInventory(new L1Location(X + 524288, Y, current)) == null, "outside packed coordinate bounds");
        // Legacy tile IDs are not injective for arbitrarily spaced coordinates.
        require(world.getInventory(new L1Location(X + 1, Y - 10000, current)) == null,
                "equal numeric tile key with different coordinates cannot return another bucket");
        world.removeVisibleObject(replacement);

        final var mapOnly = new L1Object();
        mapOnly.setId(42_801);
        mapOnly.getLocation().set(X, Y, original);
        world.addVisibleObject(mapOnly);
        final var actor = new L1Object();
        actor.setId(mapOnly.getId());
        actor.getLocation().set(X + 1, Y, current);
        world.storeAndEnterWorld(actor);
        world.addVisibleObject(mapOnly);
        require(world.findObject(actor.getId()) == actor && mapOnly.getClusterMapId() < 0
                && world.getVisibleObjects(id).get(actor.getId()) == actor,
                "map-only prior owner cannot reclaim a replacement's visible ID");
        world.removeObject(actor);
        require(world.getMapObjects(id).isEmpty(), "direct identity removal also retires map membership");
        // Hold the former object's monitor so retirement happens after the same map-only
        // instance was legitimately admitted again following removal of the intermediate owner.
        world.addVisibleObject(mapOnly);
        final Thread installer;
        synchronized (mapOnly) {
            installer = Thread.ofPlatform().daemon().start(() -> world.storeObject(actor));
            final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (world.findObject(actor.getId()) != actor && System.nanoTime() < deadline)
                LockSupport.parkNanos(100_000);
            require(world.findObject(actor.getId()) == actor, "replacement registered before delayed retirement");
            world.removeObject(actor);
            world.addVisibleObject(mapOnly);
        }
        installer.join(5_000);
        require(!installer.isAlive() && world.getVisibleObjects(id).get(mapOnly.getId()) == mapOnly
                && mapOnly.getClusterMapId() == id, "delayed cleanup preserves the re-admitted map-only identity");
        world.removeVisibleObject(mapOnly);

        final var occupied = world.getGroundInventory(29_999, 30_000, id);
        final short sourceId = (short) (id + 1);
        final var source = maps.cloneMapAndGet(TEMPLATE, sourceId);
        final var mover = new L1Object();
        mover.setId(occupied.getId()); // The numeric tile key is positive at these coordinates.
        mover.getLocation().set(X, Y, source);
        world.storeAndEnterWorld(mover);
        maps.cloneMapAndGet(TEMPLATE, sourceId);
        require(!world.relocateVisibleObject(mover, X, Y, current) && mover.getMap() == source,
                "destination ID collision cannot mutate the actor or displace the ground bucket");
        mover.getLocation().set(X + 2, Y + 3, current);
        world.moveTo(mover, sourceId, X, Y);
        require(mover.getMap() == source && mover.getX() == X && mover.getY() == Y
                && world.getVisibleObjects(sourceId).get(mover.getId()) == mover
                && world.findGroundInventory(29_999, 30_000, id) == occupied,
                "already-mutated move rolls back to exact published terrain across ID reuse");
        world.removeObject(mover);
        world.removeVisibleObject(occupied);
        maps.removeMap(sourceId);
        maps.removeMap(id);
        System.out.println("PASS ground receipt across terrain replacement, 8 concurrent creators, aliased key and map-only retirement");
    }

    private static L1ItemInstance drop(CWorld world, L1GroundInventory tile, L1Map map, int objectId) {
        final var template = new L1EtcItem();
        template.setItemId(40017);
        template.setName("Ground receipt fixture");
        final var item = new L1ItemInstance(template, 1);
        item.setId(objectId);
        item.setX(X);
        item.setY(Y);
        item.setMap(map);
        world.storeObject(item);
        require(tile.CreateOrAddItem(item) == item, "item inserted through the production ground tile");
        return item;
    }

    private static void installTestTerrain() throws Exception {
        final Unsafe unsafe = (Unsafe) field(Unsafe.class, "theUnsafe").get(null);
        final MapsTable metadata = (MapsTable) unsafe.allocateInstance(MapsTable.class);
        field(MapsTable.class, "_maps").set(metadata, Map.of());
        field(MapsTable.class, "_instance").set(null, metadata);
        // The test supplies its only terrain instead of reading game map files.
        Arrays.fill((int[][]) field(TextMapReader.class, "MAP_INFO").get(null), null);
        Config.CACHE_MAP_FILES = false;
        L1WorldMap.getInstance().addMap(new L1V1Map(TEMPLATE, new byte[16][16], X - 3, Y - 3,
                false, false, false, false, false, false, false, false, false, true, true, false));
    }

    private static Field field(Class<?> owner, String name) throws ReflectiveOperationException {
        final Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
