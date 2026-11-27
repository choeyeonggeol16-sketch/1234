package l1j.server.server.model;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import l1j.server.Config;
import l1j.server.TextMapReader;
import l1j.server.server.datatables.MapsTable;
import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1NpcShopInstance;
import l1j.server.server.model.Instance.L1SummonInstance;
import l1j.server.server.model.character.animation.NpcSpeedData;
import l1j.server.server.templates.L1Npc;
import sun.misc.Unsafe;

public final class WorldNpcLookupRegression {
    private static final class MorphNpc extends L1NpcInstance {
        MorphNpc(L1Npc template) { super(template); }
        void changeTemplate(L1Npc template) { CopyFrom(template); }
    }

    public static void main(String[] args) throws Exception {
        final Unsafe unsafe = (Unsafe) field(Unsafe.class, "theUnsafe").get(null);
        final MapsTable table = (MapsTable) unsafe.allocateInstance(MapsTable.class);
        field(MapsTable.class, "_maps").set(table, Map.of());
        field(MapsTable.class, "_instance").set(null, table);
        Arrays.fill((int[][]) field(TextMapReader.class, "MAP_INFO").get(null), null);
        Config.CACHE_MAP_FILES = false;
        field(NpcSpeedData.class, "m_npc_speed_data").set(null, new HashMap<>());
        typedViews(unsafe);
        final CWorld world = CWorld.getInstance();
        final var allNpcs = world.getAllNpc();
        final var allShops = world.getAllShopNpc();
        final L1Npc first = prototype(101, "K");
        final L1Npc other = prototype(101, "k");
        final MorphNpc npc = new MorphNpc(first);
        final L1NpcShopInstance shop = new L1NpcShopInstance(first);
        final L1NpcShopInstance negative = new L1NpcShopInstance(other);
        enter(world, negative, -7);
        enter(world, shop, 20);
        enter(world, npc, 10);
        check(world.findNpc("K") == npc && world.getShopNpc("K") == shop, "Unicode name and shop selection");
        check(List.copyOf(allNpcs).equals(List.of(npc, shop, negative)) && List.copyOf(allShops).equals(List.of(shop, negative)),
                "already-held world views retain unsigned iteration order");
        final List<L1NpcInstance> snapshot = world.getNpcsByTemplateId(101);
        check(snapshot.equals(List.of(npc, shop, negative)) && world.findNpc(101) == npc, "unsigned ID order");
        check(snapshot == world.getNpcsByTemplateId(101), "reuse unmodified snapshot");
        shop.setNameId("display only");
        check(world.getShopNpc("display only") == null && world.getShopNpc("K") == shop, "display name ID independent");
        shop.setName("İ");
        check(world.getShopNpc("i") == shop && world.getShopNpc("k") == negative, "renamed shop");
        npc.setName("\uD801\uDC00");
        check(world.findNpc("\uD801\uDC28") == npc, "supplementary Unicode");
        npc.setName("ß");
        check(world.findNpc("ss") == null && world.findNpc("ẞ") == npc, "no full case expansion");
        first.set_npcId(202);
        check(world.getNpcsByTemplateId(202).equals(List.of(npc, shop))
                && world.getNpcsByTemplateId(101).equals(List.of(negative)), "shared mutable prototype");
        check(snapshot.equals(List.of(npc, shop, negative)), "old snapshot stable after rekey");
        npc.changeTemplate(other);
        check(world.findNpcByNpcId(202) == shop && world.findNpcByNpcId(101) == npc
                && world.findNpc("k") == npc && world.findNpc("ß") == null, "live morph");
        final MorphNpc replacement = new MorphNpc(first);
        replacement.setName("renamed before registration");
        enter(world, replacement, npc.getId());
        npc.setName("retired");
        world.removeObject(npc);
        check(world.findNpc("retired") == null && world.findNpc("renamed before registration") == replacement
                && world.getNpcsByTemplateId(202).equals(List.of(replacement, shop)), "ID replacement and stale cleanup");
        churn(world, replacement, first, shop);
        world.removeObject(replacement);
        world.removeObject(shop);
        world.removeObject(negative);
        check(world.getAllNpc().isEmpty() && world.getAllShopNpc().isEmpty() && world.getShopNpc("i") == null
                && world.findNpc("k") == null && world.getNpcsByTemplateId(101).isEmpty()
                && world.getNpcsByTemplateId(202).isEmpty(), "retirement removes all secondary entries");
        System.out.println("PASS NPC template/name/shop lookup, unsigned order, Unicode, live mutation and replacement");
    }

    private static void typedViews(Unsafe unsafe) throws Exception {
        final var directory = new CWorldNpcIndex();
        final var companions = directory.allCompanions;
        final var summons = directory.allSummons;
        // These constructors start unrelated pet/owner gameplay. Only their registry type and IDs are needed here.
        final var companion = (L1CompanionInstance) unsafe.allocateInstance(L1CompanionInstance.class);
        final var summon = (L1SummonInstance) unsafe.allocateInstance(L1SummonInstance.class);
        companion.setId(42);
        summon.setId(46);
        directory.add(companion);
        directory.add(summon);
        check(List.copyOf(companions).equals(List.of(companion)) && List.copyOf(summons).equals(List.of(summon))
                && directory.containsCompanion(42) && !directory.containsCompanion(46), "typed membership");
        try {
            companions.clear();
            throw new AssertionError("writable typed view");
        } catch (UnsupportedOperationException expected) {
            // All typed views expose registry iteration only.
        }
        directory.remove(companion);
        directory.remove(summon);
        check(companions.isEmpty() && summons.isEmpty() && directory.all.isEmpty(), "live typed views after removal");
    }

    private static void churn(CWorld world, MorphNpc npc, L1Npc prototype, L1NpcShopInstance shop) throws Exception {
        final var pool = Executors.newFixedThreadPool(3);
        final CountDownLatch start = new CountDownLatch(1);
        try {
            var names = pool.submit(() -> {
                start.await();
                for (int i = 0; i < 1_000; i++) npc.setName((i & 1) == 0 ? "Alpha" : "Beta");
                return null;
            });
            var ids = pool.submit(() -> {
                start.await();
                for (int i = 0; i < 1_000; i++) prototype.set_npcId((i & 1) == 0 ? 303 : 202);
                return null;
            });
            var readers = pool.submit(() -> {
                start.await();
                for (int i = 0; i < 2_000; i++) {
                    List<L1NpcInstance> found = world.getNpcsByTemplateId((i & 1) == 0 ? 202 : 303);
                    check(found.stream().allMatch(value -> value == npc || value == shop), "unrelated target");
                    if (found.size() == 2) check(found.get(0) == npc && found.get(1) == shop, "concurrent order");
                }
                return null;
            });
            start.countDown();
            for (var work : List.of(names, ids, readers)) work.get(10, TimeUnit.SECONDS);
            check(world.findNpc("alpha") == null && world.findNpc("BETA") == npc
                    && world.getNpcsByTemplateId(303).isEmpty() && world.getNpcsByTemplateId(202).equals(List.of(npc, shop)),
                    "final rename and rekey");
        } finally {
            pool.shutdownNow();
        }
    }

    private static L1Npc prototype(int id, String name) {
        final L1Npc value = new L1Npc();
        value.set_npcId(id);
        value.set_name(name);
        value.set_nameid(name);
        value.set_hp(100);
        return value;
    }

    private static void enter(CWorld world, L1NpcInstance npc, int id) {
        npc.setId(id);
        world.storeObject(npc);
    }

    private static Field field(Class<?> type, String name) throws ReflectiveOperationException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
