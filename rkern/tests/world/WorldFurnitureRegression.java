package l1j.server.server.model;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import l1j.server.server.datatables.MapsTable;
import l1j.server.server.model.Instance.L1FurnitureInstance;
import l1j.server.server.model.character.animation.NpcSpeedData;
import l1j.server.server.templates.L1Npc;
import sun.misc.Unsafe;

public final class WorldFurnitureRegression {
    public static void main(String[] args) throws Exception {
        var unsafe = (Unsafe) field(Unsafe.class, "theUnsafe").get(null);
        var maps = (MapsTable) unsafe.allocateInstance(MapsTable.class);
        field(MapsTable.class, "_maps").set(maps, Map.of());
        field(MapsTable.class, "_instance").set(null, maps);
        field(NpcSpeedData.class, "m_npc_speed_data").set(null, new HashMap<>());

        CWorld world = CWorld.getInstance();
        L1Npc template = new L1Npc();
        template.set_name("Furniture");
        template.set_nameid("Furniture");
        template.set_npcId(80109);
        var late = new L1FurnitureInstance(template);
        late.setId(-1);
        var early = new L1FurnitureInstance(template);
        early.setId(207);
        early.setItemObjId(300);
        var replacement = new L1FurnitureInstance(template);
        replacement.setId(207);
        replacement.setItemObjId(400);

        check(world.findFurnitureByItem(300).isEmpty(), "unpublished furniture");
        world.storeObject(late);
        world.storeObject(early);
        late.setItemObjId(300);
        world.storeObject(early);
        var before = world.findFurnitureByItem(300);
        check(before.equals(List.of(early, late)), "unsigned object ID order and repeated registration");
        check(world.findFurnitureByItem(0).isEmpty(), "default item key removed after assignment");
        early.setItemObjId(400);
        check(world.findFurnitureByItem(300).equals(List.of(late)), "rekey removes only owned entry");
        check(before.equals(List.of(early, late)), "iteration snapshot survives mutation");

        world.storeObject(replacement);
        world.removeObject(early);
        early.setItemObjId(500);
        check(world.findFurnitureByItem(400).equals(List.of(replacement)), "stale removal preserves replacement");
        check(world.findFurnitureByItem(500).isEmpty(), "retired furniture cannot reappear on rekey");
        world.removeObject(replacement);
        world.removeObject(late);
        check(world.findFurnitureByItem(300).isEmpty() && world.findFurnitureByItem(400).isEmpty(),
                "last object removes each bucket");
        check(world.objectCount() == 0, "world drained");
        System.out.println("PASS furniture install/rekey, unsigned first/all duplicates, stable snapshot and retirement");
    }

    private static Field field(Class<?> type, String name) throws ReflectiveOperationException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
