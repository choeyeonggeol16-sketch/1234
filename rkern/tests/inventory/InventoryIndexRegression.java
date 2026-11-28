package l1j.server.server.model.inventory;

import java.lang.reflect.Field;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import l1j.server.server.datatables.MapsTable;
import l1j.server.server.model.Instance.CItemList;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.templates.L1EtcItem;
import l1j.server.server.storage.mysql.MySqlCharactersItemStorage.ItemSnapshot;
import sun.misc.Unsafe;

public final class InventoryIndexRegression {
    private static final int LANTERN = 40002;
    private static final int CANDLE = 7005;
    private static final int LIGHT_DESCRIPTION = 700;

    private InventoryIndexRegression() {}

    public static void main(String[] args) throws ReflectiveOperationException {
        installEmptyWorldMapFixture();
        checkPendingItemSave();
        if (CItemList.AUDIT_PERSISTENCE) dirtyAuditMutations();

        CInventory inventory = new CInventory();
        CInventory other = new CInventory();
        L1ItemInstance candle = item(90001, CANDLE, LIGHT_DESCRIPTION, 0);
        L1ItemInstance firstLantern = item(90002, LANTERN, LIGHT_DESCRIPTION, 1);
        L1ItemInstance secondLantern = item(90003, LANTERN, LIGHT_DESCRIPTION + 1, 2);
        candle.getItem().setBless(2);
        firstLantern.getItem().setBless(2);
        secondLantern.setBless(2);
        firstLantern.setEquipped(true);
        inventory._items.addAll(List.of(candle, firstLantern, secondLantern));
        for (int i = 0; i < 32; i++) {
            L1ItemInstance row = item(91000 + i, 100 + i, 800 + i, 1);
            row.setEquipped(i % 7 == 0);
            inventory._items.add(row);
        }
        same(candle, inventory.GetFirstItemByClassIDs(LANTERN, CANDLE), "first alternative");
        same(secondLantern, inventory.GetFirstUnequippedItemByClassID(LANTERN), "first eligible lantern");
        same(null, inventory.GetFirstUnequippedItemByClassID(CANDLE), "zero quantity");
        verifySelection(inventory);

        L1ItemInstance[] oldEquipped = inventory.GetEquippedItems();
        candle.setCount(1);
        L1EtcItem changedCandle = template(LANTERN, LIGHT_DESCRIPTION + 1);
        changedCandle.setBless(2);
        candle.setItem(changedCandle);
        secondLantern.setEquipped(true);
        firstLantern.setEquipped(false);
        firstLantern.setItem(template(CANDLE, LIGHT_DESCRIPTION + 1));
        L1EtcItem cursedLantern = template(LANTERN, LIGHT_DESCRIPTION + 1);
        cursedLantern.setBless(2);
        secondLantern.setItem(cursedLantern);
        int oldObjectId = secondLantern.getId();
        secondLantern.setId(90004);
        same(null, inventory.GetByServerID(oldObjectId), "old object ID after rekey");
        same(secondLantern, inventory.GetByServerID(90004), "new object ID after rekey");
        firstLantern.setItemId(42);
        same(firstLantern, inventory.GetFirstItemByClassID(CANDLE), "legacy template ID lookup");
        check(inventory.GetItemListByClassID(CANDLE).length == 0, "instance ID lookup after rekey");
        same(firstLantern, oldEquipped[0], "original equipment snapshot");
        verifySelection(inventory);
        inventory._items.shuffle();
        verifySelection(inventory);

        check(inventory._items.Pop(firstLantern), "detach before transfer");
        other._items.Push(firstLantern);
        same(null, inventory.GetByServerID(firstLantern.getId()), "old inventory identity");
        same(firstLantern, other.GetByServerID(firstLantern.getId()), "new inventory identity");
        verifySelection(inventory);
        verifySelection(other);

        L1ItemInstance loaded = item(candle.getId(), LANTERN, LIGHT_DESCRIPTION, 1);
        inventory._items.replaceDetached(List.of(loaded));
        same(loaded, inventory.GetByServerID(candle.getId()), "replacement identity");
        same(null, inventory.GetByServerID(secondLantern.getId()), "retired identity");
        check(!inventory._items.Have(candle), "old reconnect row detached");
        verifySelection(inventory);
        inventory.clearItems();
        same(null, inventory.GetByServerID(loaded.getId()), "cleared identity");
        verifySelection(inventory);
        System.out.println("PASS inventory index order, eligibility, rekey, transfer, reconnect and clear");
    }

    private static void dirtyAuditMutations() {
        var list = new CItemList();
        list.enablePersistenceTracking();
        var row = item(3002, CANDLE, LIGHT_DESCRIPTION, 10);
        list.Push(row);
        List<Runnable> writes = List.of(
                () -> row.setCount(9), () -> row.setItemId(42), () -> row.setEquipped(true),
                () -> row.setEnchantLevel(8), () -> row.set_durability(1), () -> row.setChargeCount(2),
                () -> row.setLastUsed(Timestamp.valueOf("2026-01-01 12:00:00")),
                () -> row.setIdentified(true), () -> row.setRemainingTime(3), () -> row.setBless(4),
                () -> row.setAttrEnchantLevel(5), () -> row.setSpecialEnchant(6),
                () -> row.set_bless_level(7), () -> row.set_bless_level2(8),
                () -> row.setEndTime(Timestamp.valueOf("2026-03-01 12:00:00")),
                () -> row.setPackage(true), () -> row.set_item_level(9),
                () -> row.setHotel_Town("fixture"));
        int fields = 0;
        for (Runnable write : writes) {
            row.setLastUsed(null);
            row.setEndTime(null);
            row.getLastStatus().updateAll();
            check(!list.hasMissedPersistenceChange(row, 0, list.persistenceRevision()), "retire clean candidate");
            check(list.dirtyPersistenceCandidates().length == 0, "clean candidate removed");
            write.run();
            check(list.dirtyPersistenceCandidates().length == 1
                    && list.dirtyPersistenceCandidates()[0] == row, "track the same live item");
            int columns = row.getRecordingColumns();
            check(Integer.bitCount(columns) == 1
                    && !list.hasMissedPersistenceChange(row, columns, list.persistenceRevision()),
                    "mark the changed persisted column " + columns);
            fields |= columns;
        }
        check(Integer.bitCount(fields) == 18, "all columns are exercised");
        check(list.Pop(row), "unlink dirty row");
        check(!list.hasMissedPersistenceChange(row, fields, list.persistenceRevision()),
                "ignore stale snapshot after unlink");
    }

    private static void checkPendingItemSave() {
        L1ItemInstance row = item(3001, CANDLE, LIGHT_DESCRIPTION, 10);
        Timestamp lastUsed = Timestamp.valueOf("2025-01-02 03:04:05.123456");
        row.getLastStatus().updateAll();

        var list = new CItemList();
        list.enablePersistenceTracking();
        list.Push(row);
        row.setCount(9);
        ItemSnapshot write = ItemSnapshot.capture(row, 77);
        row.setCount(10);
        check(row.getRecordingColumns() == 0, "live reversal matches previous SQL image");
        list.hasMissedPersistenceChange(row, 0, list.persistenceRevision());
        check(list.dirtyPersistenceCandidates().length == 0, "temporarily clean reversal");
        write.finish(2L);
        check(list.dirtyPersistenceCandidates().length == 1
                && list.dirtyPersistenceCandidates()[0] == row, "SQL acknowledgement reactivates reversal");
        row.setChargeCount(2);
        check(row.getLastStatus().count == 9, "acknowledge the committed count");
        check((row.getRecordingColumns() & CUserInventory.COL_COUNT) != 0, "reverted count still needs SQL");
        check(row.getLastStatus().chargeCount == 0, "partial acknowledgement changed another column");

        row.setLastUsed(lastUsed);
        final Timestamp endTime = Timestamp.valueOf("2026-01-01 03:00:00");
        row.setEndTime(endTime);
        row.getLastStatus().updateAll();
        list.hasMissedPersistenceChange(row, 0, list.persistenceRevision());
        check(list.dirtyPersistenceCandidates().length == 1, "retain clean row with mutable timestamps");
        lastUsed.setNanos(876543000);
        row.getEndTime().setNanos(120000);
        check((row.getRecordingColumns() & CUserInventory.COL_DELAY_EFFECT) != 0, "timestamp alias hid mutation");
        check((row.getRecordingColumns() & CUserInventory.COL_END_TIME) != 0, "timestamp getter hid mutation");
        ItemSnapshot.capture(row, 77).finish((1L << 18) - 1);
        check(row.getRecordingColumns() == 0, "latest full acknowledgement");
    }

    private static void installEmptyWorldMapFixture() throws ReflectiveOperationException {
        // Use the same database-free map fixture as TestCarryingWeight.
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        MapsTable table = (MapsTable) unsafe.allocateInstance(MapsTable.class);
        Field maps = MapsTable.class.getDeclaredField("_maps");
        maps.setAccessible(true);
        maps.set(table, Map.of());
        Field singleton = MapsTable.class.getDeclaredField("_instance");
        singleton.setAccessible(true);
        singleton.set(null, table);
    }

    private static void verifySelection(CInventory inventory) {
        List<L1ItemInstance> lights = new ArrayList<>();
        List<L1ItemInstance> lanterns = new ArrayList<>();
        List<L1ItemInstance> described = new ArrayList<>();
        List<L1ItemInstance> secondDescription = new ArrayList<>();
        List<L1ItemInstance> describedLanterns = new ArrayList<>();
        List<L1ItemInstance> equipped = new ArrayList<>();
        List<L1ItemInstance> cursed = new ArrayList<>();
        L1ItemInstance firstUnequippedLantern = null;
        for (L1ItemInstance row : inventory.getItems()) {
            same(row, inventory.GetByServerID(row.getId()), "current object identity");
            if (row.isEquipped()) equipped.add(row);
            if (row.getItem().getBless() == 2) cursed.add(row);
            if (row.getItemId() == LANTERN || row.getItemId() == CANDLE) lights.add(row);
            if (row.getItem().getItemDescId() == LIGHT_DESCRIPTION) described.add(row);
            if (row.getItem().getItemDescId() == LIGHT_DESCRIPTION + 1) secondDescription.add(row);
            if (row.getItemId() != LANTERN) continue;
            lanterns.add(row);
            if (row.getItem().getItemDescId() == LIGHT_DESCRIPTION) describedLanterns.add(row);
            if (firstUnequippedLantern == null && !row.isEquipped() && row.getCount() > 0)
                firstUnequippedLantern = row;
        }

        L1ItemInstance firstLight = lights.isEmpty() ? null : lights.getFirst();
        same(firstLight, inventory.GetFirstItemByClassIDs(LANTERN, CANDLE), "ordered alternatives");
        same(firstLight, inventory.GetFirstItemByClassIDs(CANDLE, LANTERN), "reversed alternatives");
        same(firstUnequippedLantern, inventory.GetFirstUnequippedItemByClassID(LANTERN), "eligible lantern");
        same(lanterns.isEmpty() ? null : lanterns.getFirst(), inventory.GetFirstItemByClassID(LANTERN),
                "first class match");
        same(described.isEmpty() ? null : described.getFirst(), inventory.GetFirstItemByDescID(LIGHT_DESCRIPTION),
                "first description match");
        check(equipped.equals(Arrays.asList(inventory.GetEquippedItems())), "equipment order");
        check(cursed.equals(Arrays.asList(inventory.GetCursedTemplateItems())), "template curse order");
        check(lanterns.equals(Arrays.asList(inventory.GetItemListByClassID(LANTERN))), "class membership");
        check(described.equals(Arrays.asList(inventory.GetItemListByDescID(LIGHT_DESCRIPTION))),
                "description membership");
        check(secondDescription.equals(Arrays.asList(inventory.GetItemListByDescID(LIGHT_DESCRIPTION + 1))),
                "description rekey order");
        check(describedLanterns.equals(Arrays.asList(inventory.GetItemListByClassID(LANTERN, LIGHT_DESCRIPTION))),
                "class/description intersection");
    }

    private static L1ItemInstance item(int id, int classId, int descId, int count) {
        L1ItemInstance item = new L1ItemInstance(template(classId, descId), count);
        item.setId(id);
        return item;
    }

    private static L1EtcItem template(int classId, int descId) {
        L1EtcItem template = new L1EtcItem();
        template.setItemId(classId);
        template.setItemDescId(descId);
        return template;
    }

    private static void same(Object expected, Object actual, String operation) {
        check(expected == actual, operation);
    }

    private static void check(boolean condition, String operation) {
        if (!condition) throw new AssertionError(operation);
    }
}
