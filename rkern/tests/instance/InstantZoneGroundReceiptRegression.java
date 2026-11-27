package l1j.server.server.model.world.instance;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
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
