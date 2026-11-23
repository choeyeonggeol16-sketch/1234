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

        System.out.println("PASS NC dGetItem/GetItemFinished: same-ID replacement, "
                + "stale pickup rejection, exact-key failure and success, "
                + "pet high-item gate, companion owner restriction, "
                + "callback successor handoff, CIO search overrides, 500/1000 ms cadence");
    }
}
