"""Run the warehouse withdrawal planner with a controllable CacheD receipt."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[2] / "src/l1j/server/server/model"
source = (root / "L1Inventory.java").read_text(encoding="utf-8")
method = source[source.index("    /** Persist selected private, clan or elf warehouse deposits"):
                source.index("    private L1ItemInstance prepareWarehouseDeposit")]
method = method.replace("l1j.server.server.model.Instance.", "").replace("l1j.server.server.model.Warehouse.", "")
warehouse = (root / "Warehouse/Warehouse.java").read_text(encoding="utf-8")
charge = warehouse[warehouse.index("    public static boolean chargeMergeItem"):
                   warehouse.index("    private L1ItemInstance prepareWarehouseWithdrawal")]
clan = (root / "Warehouse/ClanWarehouse.java").read_text(encoding="utf-8")
lease = clan[clan.index("\tpublic synchronized boolean releaseWarehouseUser"):clan.rfind("}")]

program = r'''
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public final class WarehouseCheck {
    static int checks;
    static void check(boolean value) {
        checks++;
        if (!value) throw new AssertionError("check " + checks);
    }

    static final class Config {
        static double RATE_WEIGHT_LIMIT = 1;
        static int MAX_CLAN_WAREHOUSE_ITEM = 150, MAX_PERSONAL_WAREHOUSE_ITEM = 150;
    }
    static final class L1ItemId { static final int ADENA = 40308; }

    record Template(int getWeight) {}
    static final class L1ItemInstance {
        final int id, type;
        final boolean stackable;
        final Template template;
        int count, charge;
        Timestamp deadline;

        L1ItemInstance(int id, int type, int count, int charge, boolean stackable) {
            this.id = id; this.type = type; this.count = count; this.charge = charge;
            this.stackable = stackable;
            template = new Template(1);
        }

        int getId() { return id; }
        int getCount() { return count; }
        int getItemId() { return type; }
        int getChargeCount() { return charge; }
        Template getItem() { return template; }
        boolean isEquipped() { return false; }
        boolean isStackable() { return stackable; }
        void setEndTime(Timestamp end) { deadline = end; }
    }

    static class L1Inventory {
        static final int MAX_AMOUNT = 2_000_000_000;
        final Map<Integer, L1ItemInstance> rows = new HashMap<>();
        L1ItemInstance tradeItem(L1ItemInstance item, int count, Warehouse dest) {
            L1ItemInstance carry = item;
            if (item.count == count) rows.remove(item.id);
            else {
                item.count -= count;
                carry = CacheD.pending.replayCarry(item.id);
            }
            var merged = item.stackable ? dest.findItemId(item.type) : null;
            if (merged == null) dest.rows.put(carry.id, carry);
            else merged.count += carry.count;
            return merged == null ? carry : merged;
        }
        boolean rejectPendingItemTransfer() { return ((L1PcInventory)this).owner.busy; }
        L1ItemInstance getItem(int id) { return rows.get(id); }
        L1ItemInstance findItemId(int id) {
            for (L1ItemInstance row : rows.values()) if (row.type == id) return row;
            return null;
        }
        int getSize() { return rows.size(); }
        int getWeight() { return 0; }
''' + method + r'''
    }

    static final class L1PcInventory extends L1Inventory {
        static final int MAX_SIZE = 180;
        final L1PcInstance owner = new L1PcInstance();
        L1PcInstance getOwner() { return owner; }
    }

    static final class L1PcInstance {
        final CIOClient client = new CIOClient();
        boolean busy;
        int getId() { return 11; }
        int getSessionStamp() { return 1; }
        int getMaxWeight() { return 100; }
        L1PcInstance getLight() { return this; }
        void turnOnOffLight() {}
        CIOClient getNetConnection() { return client; }
        boolean tryBeginItemTransfer() { if (busy) return false; busy = true; return true; }
        void endItemTransfer() { busy = false; }
    }
    static final class CIOClient {
        boolean isConnected() { return true; }
        void kick() {}
    }
    static final class CIOThreadCallback {
        static CIOThreadCallback getInstance() { return new CIOThreadCallback(); }
        void completeSession(CIOClient client, Runnable action) { action.run(); }
        void completeWarehouseMove(L1PcInstance pc, int stamp, Runnable action, Consumer<Throwable> finished) {
            action.run();
            finished.accept(null);
        }
    }
    static final class L1World {
        static L1World getInstance() { return new L1World(); }
        void storeObject(L1ItemInstance item) { check(item.id > 0); }
    }

    static class Warehouse {
        final Map<Integer, L1ItemInstance> rows = new HashMap<>();
        int updates;
        L1ItemInstance getItem(int id) { return rows.get(id); }
        L1ItemInstance findItemId(int type) {
            for (var item : rows.values()) if (item.type == type) return item;
            return null;
        }
        int getSize() { return rows.size(); }
        void insertItem(L1ItemInstance item) { CacheD.pending.warehouseWrites.put(item.id, item); }
        int transferOrderKey() { return 420; }
        void requireWithdrawalTable(CacheD.WarehouseMove move) { move.tableChecked = true; }
        void prepareTransferDelete(L1ItemInstance item) { CacheD.pending.sourceWrites.add(item.id); }
        void updateItem(L1ItemInstance item) {
            CacheD.pending.warehouseWrites.put(item.id, item);
            CacheD.pending.sourceWrites.add(item.id);
        }
        static void recordCommittedMove(L1PcInstance pc, Warehouse wh, boolean deposit, L1ItemInstance item, int count) {
            wh.updates++;
        }
        static L1ItemInstance allocateDetachedTransfer(L1ItemInstance item, int count) {
            return transferCopy(item, item.id + 1000, count, item.charge);
        }
        static L1ItemInstance transferCopy(L1ItemInstance source, int id, int count, int charge) {
            return new L1ItemInstance(id, source.type, count, charge, source.stackable);
        }
        static L1ItemInstance characterTransferCopy(L1ItemInstance source, int id, int count, int charge) {
            return transferCopy(source, id, count, charge);
        }
        L1ItemInstance tradeSupplementaryItem(L1ItemInstance item, int count, L1Inventory dest,
                Timestamp deadline, Runnable committed) {
            L1ItemInstance applied = tradeItem(item, count, dest, 0, 0);
            applied.deadline = deadline;
            return applied;
        }
        L1ItemInstance tradeItem(L1ItemInstance item, int count, L1Inventory dest, int feeItem, int feeEach) {
            L1ItemInstance result;
            if (count == item.count) {
                rows.remove(item.id);
                result = item;
            } else {
                item.count -= count;
                result = CacheD.pending.replayCarry(item.id);
            }
            L1ItemInstance existing = dest.findItemId(item.type);
            if (existing != null && (item.stackable || chargeMergeItem(item.type))) {
                if (item.stackable) existing.count += count;
                else existing.charge += item.charge;
                result = existing;
            } else {
                dest.rows.put(result.id, result);
            }
            if (feeEach > 0) {
                L1ItemInstance fee = dest.findItemId(feeItem);
                if ((fee.count -= feeEach) == 0) dest.rows.remove(fee.id);
            }
            return result;
        }
''' + charge + r'''
    }

    static final class SupplementaryService extends Warehouse {}
    static final class PrivateWarehouse extends Warehouse {}
    static final class ElfWarehouse extends Warehouse {}

    static final class ClanWarehouse extends Warehouse {
        private int _warehouse;
''' + lease + r'''
    }

    static final class CacheD {
        static WarehouseMove pending;
        static WarehouseMove beginWarehouseMove(int pc, int key) { return pending = new WarehouseMove(); }
        static void applyCommittedWarehouseMove(WarehouseMove move, Runnable action) { action.run(); }

        static final class WarehouseMove implements AutoCloseable {
            final Map<Integer, L1ItemInstance> writes = new HashMap<>(), carried = new HashMap<>();
            final Map<Integer, L1ItemInstance> warehouseWrites = new HashMap<>();
            final ArrayList<Integer> sourceWrites = new ArrayList<>();
            boolean tableChecked;
            Runnable replay;

            void onFailure(Runnable action) {}
            void expectOriginal(L1ItemInstance item) {}
            void prepareCarry(int id, L1ItemInstance image) { carried.put(id, image); }
            L1ItemInstance replayCarry(int id) { return carried.get(id); }
            void prepareCharacterDelete(L1ItemInstance item) { writes.remove(item.id); }
            void prepareCharacterWrite(L1ItemInstance image, boolean insert) { writes.put(image.id, image); }
            void commit(Runnable action) { replay = action; }
            void finishOwnerApply(Throwable failure) { check(failure == null); }
            @Override public void close() {}
        }
    }

    static L1ItemInstance add(Map<Integer, L1ItemInstance> rows,
            int id, int type, int count, int charge, boolean stackable) {
        L1ItemInstance item = new L1ItemInstance(id, type, count, charge, stackable);
        rows.put(id, item);
        return item;
    }

    static void checkCharges(boolean existing) {
        var inv = new L1PcInventory();
        var vault = new Warehouse();
        add(vault.rows, 1, 40006, 1, 11, false);
        add(vault.rows, 2, 40006, 1, 22, false);
        if (existing) add(inv.rows, 98, 40006, 1, 5, false);
        int id = existing ? 98 : 1;
        check(inv.tradeWarehouseWithdrawalBatch(new int[]{1, 2}, new int[]{1, 1},
                vault, 0, 0, null, null));
        var receipt = CacheD.pending;
        check(receipt.tableChecked && receipt.writes.get(id).charge == (existing ? 38 : 33));
        check(receipt.writes.get(id).count == 1 && receipt.sourceWrites.equals(java.util.List.of(1, 2)));
        check(vault.rows.size() == 2 && inv.owner.busy && vault.updates == 0);
        receipt.replay.run();
        check(vault.rows.isEmpty() && !inv.owner.busy && vault.updates == 2
                && inv.rows.get(id).charge == (existing ? 38 : 33));
    }

    static void checkDeposit() {
        var inv = new L1PcInventory();
        var vault = new PrivateWarehouse();
        add(vault.rows, 99, 5100, 40, 0, true);
        add(inv.rows, 1, 5100, 5, 0, true);
        add(inv.rows, 2, 5100, 15, 0, true);
        add(inv.rows, 3, 5250, 1, 0, false);
        CacheD.pending = null;
        check(!inv.tradeWarehouseDepositBatch(new int[]{1, 3}, new int[]{5, 2}, vault, null));
        check(CacheD.pending == null && !inv.owner.busy && vault.rows.get(99).count == 40);
        check(inv.tradeWarehouseDepositBatch(new int[]{1, 2, 3}, new int[]{5, 10, 1}, vault, null));
        var receipt = CacheD.pending;
        check(receipt.warehouseWrites.get(99).count == 55 && receipt.warehouseWrites.get(3).count == 1);
        check(inv.rows.size() == 3 && vault.rows.size() == 1 && vault.rows.get(99).count == 40);
        receipt.replay.run();
        check(inv.rows.size() == 1 && inv.getItem(2).count == 5 && vault.rows.get(99).count == 55);
        check(vault.rows.containsKey(3) && !inv.owner.busy);
    }

    public static void main(String[] args) {
        checkDeposit();
        checkCharges(false);
        checkCharges(true);

        var inv = new L1PcInventory();
        var vault = new Warehouse();
        var wallet = add(inv.rows, 9, L1ItemId.ADENA, 100, 0, true);
        add(vault.rows, 1, L1ItemId.ADENA, 500, 0, true);
        add(vault.rows, 2, 4914, 5, 0, true);
        check(inv.tradeWarehouseWithdrawalBatch(new int[]{1, 2}, new int[]{300, 4},
                vault, L1ItemId.ADENA, 30, null, null));
        var receipt = CacheD.pending;
        check(receipt.writes.get(9).count == 340 && receipt.writes.get(1002).count == 4);
        check(wallet.count == 100 && vault.rows.get(1).count == 500 && vault.rows.get(2).count == 5);
        receipt.replay.run();
        check(wallet.count == 340 && vault.rows.get(1).count == 200 && vault.rows.get(2).count == 1);

        var service = new SupplementaryService();
        add(service.rows, 1, 3000214, 1, 0, false);
        add(service.rows, 2, 3000213, 1, 0, false);
        Timestamp end1 = new Timestamp(5_000_000L), end2 = new Timestamp(6_000_000L);
        check(inv.tradeWarehouseWithdrawalBatch(new int[]{1, 2}, new int[]{1, 1},
                service, 0, 0, new Timestamp[]{end1, end2}, null));
        receipt = CacheD.pending;
        check(receipt.writes.get(1).deadline.equals(end1) && receipt.writes.get(2).deadline.equals(end2));
        check(service.rows.size() == 2 && inv.getItem(1) == null);
        receipt.replay.run();
        check(service.rows.isEmpty() && inv.getItem(1).deadline.equals(end1) && inv.getItem(2).deadline.equals(end2));

        add(vault.rows, 11, 40006, 1, Integer.MAX_VALUE, false);
        add(vault.rows, 12, 40006, 1, 1, false);
        CacheD.pending = null;
        check(!inv.tradeWarehouseWithdrawalBatch(new int[]{11, 12}, new int[]{1, 1},
                vault, 0, 0, null, null));
        check(CacheD.pending == null && vault.rows.get(11).charge == Integer.MAX_VALUE && !inv.owner.busy);
        check(!inv.tradeWarehouseWithdrawalBatch(new int[]{11, 11}, new int[]{1, 1},
                vault, 0, 0, null, null));
        check(CacheD.pending == null && !inv.owner.busy);

        var clan = new ClanWarehouse();
        check(clan.setWarehouseUsingChar(123, 0));
        check(!clan.releaseWarehouseUser(321));
        check(clan.releaseWarehouseUser(123));
        check(!clan.releaseWarehouseUser(123));
        System.out.println("PASS warehouse deposit, withdrawal and lease checks=" + checks);
    }
}
'''
with tempfile.TemporaryDirectory() as path:
    file = Path(path, "WarehouseCheck.java")
    file.write_text(program, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", path, str(file)], check=True)
    subprocess.run(["java", "-cp", path, "WarehouseCheck"], check=True)
