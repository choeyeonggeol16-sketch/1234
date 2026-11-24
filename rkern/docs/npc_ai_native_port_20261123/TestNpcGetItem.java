package l1j.server.server.model.npc.ai.action;

import sun.misc.Unsafe;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1PetInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.templates.L1Item;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1Location;
import l1j.server.server.model.L1World;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.L1GroundInventory;
import l1j.server.server.model.L1IntRegistry;
import l1j.server.server.model.LocalWorldMap;
import l1j.server.server.model.Instance.CItemList;
import l1j.server.Config;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1V1Map;
import l1j.server.server.model.npc.ai.CDesireFactory;
import l1j.server.server.model.npc.ai.CDesirePQ;
import l1j.server.server.templates.L1EtcItem;

/**
 * NC dGetItem/GetItemFinished regression against production classes.
 * No MySQL, network, DB writes or synthetic inventory transactions.
 * CacheD's existing locked transfer stays in L1Inventory.tradeItem.
 */
public final class TestNpcGetItem {
    private TestNpcGetItem() {
    }

    // Constructor is never run: Unsafe creates a test-only NPC with an
    // observing onGetItem() callback but no game world or database.
    private static final class PickupProbe extends L1NpcInstance {
        int completions;
        L1ItemInstance expectedTransfer;
        L1ItemInstance selectAfterCommit;

        private PickupProbe() {
            super(null);
        }

        @Override
        public void onGetItem(final L1ItemInstance item) {
            completions++;
            expectedTransfer = item;
            if (selectAfterCommit != null) {
                _targetItem = selectAfterCommit;
            }
        }
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    @SuppressWarnings("unchecked")
    private static <T> T instance(final Unsafe unsafe, final Class<T> type)
            throws InstantiationException {
        return (T) unsafe.allocateInstance(type);
    }

    public static void main(String[] args) throws Exception {
        final Field uf = Unsafe.class.getDeclaredField("theUnsafe");
        uf.setAccessible(true);
        final Unsafe unsafe = (Unsafe) uf.get(null);

        final L1MonsterInstance npc = instance(unsafe, L1MonsterInstance.class);
        npc.setId(971001);
        npc.setPickupItem(true);

        final L1Item template = instance(unsafe, L1EtcItem.class);
        final L1ItemInstance itemA = instance(unsafe, L1ItemInstance.class);
        final L1ItemInstance itemB = instance(unsafe, L1ItemInstance.class);
        itemA.setId(971002);
        itemB.setId(971002); // deliberate object-ID replacement
        itemA.setItem(template);
        itemB.setItem(template);

        npc._targetItem = itemB;
        dGetItem.GetItemFinished(npc, itemA, null);
        check(npc._targetItem == itemB,
                "stale GetItemFinished(A) must preserve replacement B of the same ID");
        check(npc.getSleepTime() == 500L,
                "native failed GetItemFinished uses 500 ms retry");
        CGetItemAction.pickup(npc, itemA);
        check(npc._targetItem == itemB,
                "stale pickup A must not transfer or clear B");

        dGetItem.GetItemFinished(npc, itemB, null);
        check(npc._targetItem == null, "failed current item must be retired");
        check(CGetItemAction.onTick(npc) == CCreatureController.RESULT_DONE,
                "no item is a finished GET_ITEM action");

        // Leaving the L1 pet/companion collect command explicitly clears
        // the desire key; direct NPC GET_ITEM (not auto scan) must still work.
        npc._targetItem = null;
        npc.setPickupItem(false);
        check(CGetItemAction.onTick(npc) == CCreatureController.RESULT_DONE,
                "a cleared pickup command cannot execute a stale item");
        npc.setPickupItem(true);
        npc.setDead(true);
        npc._targetItem = itemA;
        check(dGetItem.dGetItem(npc, itemA) == CCreatureController.RESULT_INVALID,
                "dead NPC must never attempt ground transfer");
        check(npc._targetItem == null,
                "dead NPC must retire the stale GET_ITEM request");
        npc.setDead(false);

        // Normal pet item permissions are the original L1 restriction.
        final L1PetInstance pet = instance(unsafe, L1PetInstance.class);
        template.setUseHighPet(true);
        check(!CGetItemAction.isAllowedItem(pet, itemA),
                "L1 high-pet-only item cannot be collected as ordinary pet loot");
        template.setUseHighPet(false);
        check(CGetItemAction.isAllowedItem(pet, itemA),
                "ordinary pet loot remains eligible");

        // Companion may loot unowned or current-master-owned items only.
        final L1CompanionInstance companion =
                instance(unsafe, L1CompanionInstance.class);
        companion.setId(971003);
        final L1PcInstance master = instance(unsafe, L1PcInstance.class);
        final L1PcInstance outsider = instance(unsafe, L1PcInstance.class);
        master.setId(971004);
        outsider.setId(971005);
        companion.set_master(master);
        itemA.setItemOwner(outsider);
        check(!CGetItemAction.isAllowedItem(companion, itemA),
                "companion may not collect another player's reserved ground item");
        itemA.setItemOwner(master);
        check(CGetItemAction.isAllowedItem(companion, itemA),
                "master-owned ground item must remain eligible");
        itemA.setItemOwner(null);
        check(CGetItemAction.isAllowedItem(companion, itemA),
                "unowned ground item must remain eligible");

        // With constructor-bypassing fixture, the real companion callback
        // returns early on null master; this still checks that a successful
        // GetItemFinished clears its exact captured object and has 1 s cadence.
        companion.set_master(null);
        companion._targetItem = itemA;
        dGetItem.GetItemFinished(companion, itemA, itemA);
        check(companion._targetItem == null,
                "successful GetItemFinished retires only the transferred item");
        check(companion.getSleepTime() == 1000L,
                "successful GetItemFinished retains 1000 ms NC pickup cadence");

        // Completion callbacks run only after a successful transfer, and
        // a callback may immediately publish a new request. Completing A
        // must not erase its successor B even if the object IDs are reused.
        final PickupProbe probe = instance(unsafe, PickupProbe.class);
        probe.setId(971006);
        probe._targetItem = itemA;
        probe.selectAfterCommit = itemB;
        dGetItem.GetItemFinished(probe, itemA, itemA);
        check(probe.completions == 1
                && probe.expectedTransfer == itemA,
                "successful GET_ITEM callback must receive the transferred item");
        check(probe._targetItem == itemB,
                "GetItemFinished(A) must preserve B selected inside the callback");

        probe.selectAfterCommit = null;
        dGetItem.GetItemFinished(probe, itemB, null);
        check(probe.completions == 1,
                "failed GET_ITEM must not fire the successful pickup callback");
        check(probe._targetItem == null,
                "failed successor B must retire exactly B");

        // Every live L1 pickup type must override its radius-10 search hook.
        final Method petSearch = L1PetInstance.class
                .getDeclaredMethod("searchTargetItem");
        final Method companionSearch = L1CompanionInstance.class
                .getDeclaredMethod("searchTargetItem");
        check(petSearch.getDeclaringClass() == L1PetInstance.class
                && companionSearch.getDeclaringClass() == L1CompanionInstance.class,
                "pet and companion must select GET_ITEM on their own CIO action");

        // Real primitive desire arbitration with exact identity (no world scan).
        final Field hp = L1Character.class.getDeclaredField("_currentHp");
        hp.setAccessible(true);
        hp.setInt(npc, 100);
        final byte[][] tiles = new byte[64][64];
        for (byte[] column : tiles) java.util.Arrays.fill(column, (byte) 0x03);
        final L1Map map = new L1V1Map(996, tiles, 0, 0,
                false, true, true, true, true, true,
                true, true, true, true, true, false);
        final Field loc = L1Object.class.getDeclaredField("_loc");
        loc.setAccessible(true);
        loc.set(npc, new L1Location(20, 20, map));
        loc.set(itemA, new L1Location(22, 20, map));
        loc.set(itemB, new L1Location(23, 20, map));
        itemA.setCount(1); itemB.setCount(1);
        final long now = System.currentTimeMillis();
        check(CDesireFactory.MakeGetItem(npc, itemA, 6.0f, now), "register item A");
        check(CDesirePQ.topState(npc, now) == CDesirePQ.PS_GET_ITEM,
                "GET_ITEM wins over idle MOVE_AROUND(5)");
        CDesirePQ.SetAsGetItem(npc, itemA);
        npc._target = master;
        check(CDesirePQ.topState(npc, now) == CDesirePQ.PS_MOVE_AROUND,
                "active combat target suppresses GET_ITEM");
        npc._target = null;
        check(CDesirePQ.topState(npc, now) == CDesirePQ.PS_GET_ITEM,
                "item remains selectable after combat target clears");
        check(CDesireFactory.MakeGetItem(npc, itemB, 6.0f, now), "same-ID successor B");
        check(CDesirePQ.getItemAction(npc) == itemA
                && CDesirePQ.getItemDesire(npc) == itemB, "action captures exact original item");
        dGetItem.GetItemFinished(npc, itemA, null);
        check(npc._targetItem == itemB && CDesirePQ.getItemDesire(npc) == itemB,
                "old completion does not erase replacement desire");
        dGetItem.GetItemFinished(npc, itemB, null);
        check(npc._targetItem == null && CDesirePQ.getItemDesire(npc) == null
                && CDesirePQ.topState(npc, now) == CDesirePQ.PS_MOVE_AROUND,
                "retire exact item key and resume wandering");
        System.out.println("PASS NC MakeGetItem heap selection and exact captured action identity");
        testGroundDiscovery(unsafe, npc, loc, map, template, now);
        System.out.println("PASS NC dGetItem/GetItemFinished: same-ID replacement, "
                + "stale pickup rejection, exact-key failure and success, "
                + "pet high-item gate, companion owner restriction, "
                + "callback successor handoff, CIO search overrides, 500/1000 ms cadence");
    }
    private static Field field(final Class<?> type, final String name) throws Exception {
        final Field f = type.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    @SuppressWarnings("unchecked")
    private static void testGroundDiscovery(final Unsafe unsafe, final L1MonsterInstance npc,
            final Field loc, final L1Map map, final L1Item template, final long now) throws Exception {
        // Real CWorld ground registry + LocalWorldMap query, without DB bootstrap.
        final Field singleton = field(L1World.class, "_instance");
        final Object oldWorld = singleton.get(null);
        final L1World world = instance(unsafe, L1World.class);
        final Class<?> worldType = Class.forName("l1j.server.server.model.CWorld");
        final java.lang.reflect.Constructor<?> constructor =
                worldType.getDeclaredConstructor(L1World.class);
        constructor.setAccessible(true);
        field(L1World.class, "_worldExecutor").set(world, constructor.newInstance(world));
        final L1IntRegistry<L1Object>[] visible = (L1IntRegistry<L1Object>[]) new L1IntRegistry<?>[32769];
        final L1IntRegistry<L1Object> registry = new L1IntRegistry<>();
        visible[map.getId()] = registry;
        field(L1World.class, "_visibleObjects").set(world, visible);
        final int oldMax = Config.MAX_NPC_ITEM;
        final double oldWeight = Config.RATE_WEIGHT_LIMIT, oldPetWeight = Config.RATE_WEIGHT_LIMIT_PET;
        Config.MAX_NPC_ITEM = 180;
        Config.RATE_WEIGHT_LIMIT = Config.RATE_WEIGHT_LIMIT_PET = 1.0;
        singleton.set(null, world);
        try {
            field(L1NpcInstance.class, "_inventory").set(npc, new L1Inventory());
            npc._targetItem = null;
            npc._itemSearchCooldown = 0;
            npc.setPickupItem(true);
            final LocalWorldMap grid = world.getClusterGrid();
            final Method enter = LocalWorldMap.class.getDeclaredMethod("enterSilently", L1Object.class);
            enter.setAccessible(true);
            L1ItemInstance nearest = null;
            for (int dx : new int[] { 9, 3, 16 }) {
                final int x = 20 + dx, y = 20;
                final L1GroundInventory ground = instance(unsafe, L1GroundInventory.class);
                ground.setId(-((x - 30000) * 10000 + (y - 30000)));
                loc.set(ground, new L1Location(x, y, map));
                ground.setClusterMapId(-1); ground.setClusterPos(-1, -1);
                final CItemList items = new CItemList();
                field(L1Inventory.class, "_items").set(ground, items);
                final L1ItemInstance item = instance(unsafe, L1ItemInstance.class);
                item.setId(971100 + dx);
                item.setItem(template); item.setCount(1);
                loc.set(item, new L1Location(x, y, map));
                items.add(item);
                registry.put(ground.getId(), ground);
                enter.invoke(grid, ground);
                if (dx == 3) nearest = item;
            }
            check(CGetItemAction.prepareMonsterItem(npc, now), "real ground discovery admits GET_ITEM");
            check(npc._targetItem == nearest && CDesirePQ.getItemDesire(npc) == nearest,
                    "nearest valid ground object wins over farther and out-of-range objects");
            check(CMoveAroundAction.onTick(npc) == CCreatureController.RESULT_DONE,
                    "roaming yields to the item desire instead of bypassing discovery");
            registry.remove(-((23 - 30000) * 10000 + (20 - 30000)));
            check(!CGetItemAction.prepareMonsterItem(npc, now),
                    "disappeared ground object is rejected");
            check(npc._targetItem == null && CDesirePQ.getItemDesire(npc) == null,
                    "disappeared exact item key is retired");
            npc._itemSearchCooldown = 0;
            npc.setPickupItem(false);
            check(!CGetItemAction.prepareMonsterItem(npc, now)
                    && npc._itemSearchCooldown == 0 && npc._targetItem == null,
                    "non-pickup monster does not enter the ground scan");
            System.out.println("PASS live ground discovery: nearest in range, roam handoff, "
                    + "disappeared item retirement, non-pickup scan bypass");
        } finally {
            singleton.set(null, oldWorld);
            Config.MAX_NPC_ITEM = oldMax;
            Config.RATE_WEIGHT_LIMIT = oldWeight;
            Config.RATE_WEIGHT_LIMIT_PET = oldPetWeight;
        }
    }

}
