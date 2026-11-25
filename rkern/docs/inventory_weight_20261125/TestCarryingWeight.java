package l1j.server.server.model;

import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import l1j.server.Config;
import l1j.server.server.model.Instance.CItemList;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.templates.L1EtcItem;

public final class TestCarryingWeight {
    private static int checks;
    private static void check(long expected, long actual) {
        checks++;
        if (expected != actual) throw new AssertionError("expected=" + expected + " actual=" + actual);
    }
    private static L1EtcItem template(int id, int weight) {
        L1EtcItem t = new L1EtcItem();
        t.setItemId(id); t.setWeight(weight); t.set_stackable(true);
        return t;
    }
    private static long reference(CItemList list) {
        long total = 0;
        for (L1ItemInstance i : list.snapshot()) {
            int w = i.getItem().getWeight();
            total += w == 0 ? 0L : Math.clamp((long)i.getCount()*w/1000L,1L,Integer.MAX_VALUE);
        }
        return total;
    }
    private static void checkLifetime(int id, boolean ordinary, int type, long durationMs) {
        L1EtcItem t = template(id, 0);
        t.setType2(0); t.setType(type); t.setMaxUseTime(123);
        L1ItemInstance item = new L1ItemInstance(t, 1);
        java.sql.Timestamp sentinel = new java.sql.Timestamp(1234);
        item.setEndTime(sentinel);
        long before = System.currentTimeMillis();
        L1ItemInstance.InitializeNewItemLifetimeAndFuel(item, ordinary);
        long after = System.currentTimeMillis();
        if (durationMs < 0) {
            check(1234, item.getEndTime().getTime());
        } else {
            long expiration = item.getEndTime().getTime();
            check(1, expiration >= before + durationMs && expiration <= after + durationMs ? 1 : 0);
        }
        check(type == 2 ? t.getLightFuel() : 123, item.getRemainingTime());
    }
    private static void checkNewItemInitialization() {
        checkLifetime(80500, true, 0, 120L * 60000);
        checkLifetime(80500, false, 0, -1);
        checkLifetime(3000048, true, 0, 1439L * 60000);
        checkLifetime(3000048, false, 0, 1439L * 60000);
        checkLifetime(7000137, true, 0, 1440L * 20000);
        checkLifetime(7000137, false, 0, -1);
        checkLifetime(40001, true, 2, -1);
        checkLifetime(40001, false, 2, -1);
        checkLifetime(1, true, 0, -1);
        checkLifetime(1, false, 0, -1);
        System.out.println("PASS new-item lifetime and fuel checks");
    }
    private static void checkWarehouseDispatch() {
        CInventory nonPlayer = new CInventory();
        int[] ids = {1, 2}, counts = {1, 1};
        check(0, nonPlayer.tradeWarehouseDepositBatch(ids, counts, null, null) ? 1 : 0);
        check(0, nonPlayer.tradeWarehouseWithdrawalBatch(ids, counts, null, 0, 0, null, null) ? 1 : 0);
        check(0, nonPlayer.tradeWarehouseDepositBatch(null, null, null, null) ? 1 : 0);
        check(0, nonPlayer.tradeWarehouseWithdrawalBatch(null, null, null, 0, 0, null, null) ? 1 : 0);
        check(1, nonPlayer.tradeItem(1, 1, (l1j.server.server.model.Warehouse.Warehouse) null) == null ? 1 : 0);
        check(1, nonPlayer.tradeItem((L1ItemInstance) null, 1, (l1j.server.server.model.Warehouse.Warehouse) null) == null ? 1 : 0);
        check(0, nonPlayer.getSize());
        System.out.println("PASS warehouse invalid-request delegation checks");
    }
    private static void checkTransferAdmission() {
        int[] amounts = {Integer.MIN_VALUE, -1, 0, 1, 2, 3, CInventory.MAX_AMOUNT, Integer.MAX_VALUE};
        for (boolean stackable : new boolean[]{false, true}) {
            L1EtcItem t = template(1, 0);
            t.set_stackable(stackable);
            for (boolean equipped : new boolean[]{false, true}) {
                L1ItemInstance item = new L1ItemInstance(t, 1);
                item.setEquipped(equipped);
                for (int available : amounts) {
                    item.setCount(available);
                    for (int count : amounts) {
                        boolean old = !(item.getCount() <= 0 || count <= 0 || count > item.getCount()
                                || item.isEquipped() || (!item.isStackable() && count != 1));
                        check(old ? 1 : 0, item.IsTransferableAmount(count) ? 1 : 0);
                        check(available, item.getCount());
                    }
                }
            }
        }
        System.out.println("PASS source transfer admission equivalence");
    }
    private static void checkChargeClassification() {
        int[] refill = {40006,40007,40008,140006,140008,41401,810006,810007};
        for (int id = 0; id <= 810010; id++) {
            boolean expected = false;
            for (int value : refill) if (value == id) { expected = true; break; }
            check(expected ? 1 : 0,
                l1j.server.server.model.Warehouse.Warehouse.chargeMergeItem(id) ? 1 : 0);
        }
        L1EtcItem t = template(20383, 0);
        L1ItemInstance item = new L1ItemInstance(t, 1);
        check(50, item.GetInitialChargeCount(t.getItemId()));
        item.setItemId(40006);
        check(t.getMaxChargeCount(), item.GetInitialChargeCount(item.getItemId()));
        check(50, item.GetInitialChargeCount(t.getItemId()));
        check(0, item.getChargeCount());
        System.out.println("PASS refill classification and new item charge initialization");
    }
    private static void checkItemCopyAttributes() {
        L1EtcItem t = template(123, 1000);
        L1ItemInstance src = new L1ItemInstance(t, 7);
        src.setId(10); src.setEquipped(true); src.setEnchantLevel(4);
        src.setIdentified(true); src.set_durability(17); src.setChargeCount(9);
        src.setRemainingTime(11); src.setBless(2); src.setAttrEnchantLevel(6);
        src.setSpecialEnchant(0x12345678); src.setPackage(true);
        src.set_bless_level(3); src.set_bless_level2(4); src.set_item_level(5);
        src.setHotel_Town("test-town"); src.setKeyId(77);
        java.sql.Timestamp used = java.sql.Timestamp.valueOf("2026-01-02 03:04:05.123456789");
        java.sql.Timestamp expiry = java.sql.Timestamp.valueOf("2027-01-02 03:04:05.987654321");
        src.setLastUsed(used); src.setEndTime(expiry);
        for (int mode = 0; mode < 8; mode++) {
            boolean pack = (mode & 1) != 0, end = (mode & 2) != 0, snapshot = (mode & 4) != 0;
            L1ItemInstance dst = new L1ItemInstance(t, 1);
            dst.setId(11); dst.setKeyId(88);
            java.sql.Timestamp oldEnd = new java.sql.Timestamp(123);
            dst.setEndTime(oldEnd);
            dst.CopyItemAttributesFrom(src, 3, 12, pack, end, snapshot);
            check(3, dst.getCount()); check(12, dst.getChargeCount());
            check(4, dst.getEnchantLevel()); check(1, dst.isIdentified() ? 1 : 0);
            check(17, dst.get_durability()); check(11, dst.getRemainingTime());
            check(2, dst.getBless()); check(6, dst.getAttrEnchantLevel());
            check(0x12345678, dst.getSpecialEnchant()); check(pack ? 1 : 0, dst.isPackage() ? 1 : 0);
            check(3, dst.get_bless_level()); check(4, dst.get_bless_level2());
            check(5, dst.get_item_level()); check(1, "test-town".equals(dst.getHotel_Town()) ? 1 : 0);
            check(1, used.equals(dst.getLastUsed()) ? 1 : 0);
            check(snapshot ? 0 : 1, dst.getLastUsed() == used ? 1 : 0);
            check(1, dst.getEndTime() == (end ? expiry : oldEnd) ? 1 : 0);
            check(11, dst.getId()); check(88, dst.getKeyId()); check(0, dst.isEquipped() ? 1 : 0);
        }
        L1ItemInstance detached = l1j.server.server.model.Warehouse.Warehouse.transferCopy(src, 12, 2, 8);
        check(12, detached.getId()); check(2, detached.getCount()); check(8, detached.getChargeCount());
        check(77, detached.getKeyId()); check(0, detached.isEquipped() ? 1 : 0);
        check(1, expiry.equals(detached.getEndTime()) && expiry != detached.getEndTime() ? 1 : 0);
        check(1, used.equals(detached.getLastUsed()) && used != detached.getLastUsed() ? 1 : 0);
        check(1, L1ItemInstance.CopyTimestamp(null) == null ? 1 : 0);
        check(7, src.getCount()); check(9, src.getChargeCount());
        System.out.println("PASS persistent item copy and timestamp policy");
    }
    private static void checkSearchRows(L1ItemInstance[] actual, ArrayList<L1ItemInstance> expected) {
        check(expected.size(), actual.length);
        for (int i = 0; i < actual.length; i++) check(1, actual[i] == expected.get(i) ? 1 : 0);
    }
    private static void checkLinkedSearch() {
        CInventory inv = new CInventory();
        Random random = new Random(20261125L);
        for (int n : new int[]{0, 1, 2, 3, 4, 5, 8, 9, 16, 17, 180}) {
            inv._items.clear();
            for (int j = 0; j < n; j++) {
                L1EtcItem t = template(100 + random.nextInt(3), 0);
                t.setItemDescId(200 + random.nextInt(3));
                t.set_stackable(random.nextBoolean());
                L1ItemInstance item = new L1ItemInstance(t, random.nextInt(5));
                item.setEquipped(random.nextBoolean());
                item.setEnchantLevel(random.nextInt(3));
                inv._items.Push(item);
            }
            for (int id = 99; id <= 103; id++) {
                ArrayList<L1ItemInstance> byClass = new ArrayList<>();
                ArrayList<L1ItemInstance> unequipped = new ArrayList<>();
                long total = 0;
                for (L1ItemInstance item : inv._items.snapshot()) {
                    if (item.getItemId() != id) continue;
                    byClass.add(item);
                    if (!item.isEquipped()) {
                        unequipped.add(item);
                        if (item.getCount() > 0) total += item.getCount();
                    }
                }
                checkSearchRows(inv.GetItemListByClassID(id), byClass);
                checkSearchRows(inv.GetUnequippedItemListByClassID(id), unequipped);
                check(total, inv.GetRecipeMaterialAmount(id));
                for (int desc = 199; desc <= 203; desc++) {
                    ArrayList<L1ItemInstance> byDesc = new ArrayList<>();
                    ArrayList<L1ItemInstance> both = new ArrayList<>();
                    for (L1ItemInstance item : inv._items.snapshot()) {
                        if (item.getItem().getItemDescId() != desc) continue;
                        byDesc.add(item);
                        if (item.getItemId() == id) both.add(item);
                    }
                    checkSearchRows(inv.GetItemListByDescID(desc), byDesc);
                    checkSearchRows(inv.GetItemListByClassID(id, desc), both);
                    check(1, inv.GetFirstItemByDescID(desc) == (byDesc.isEmpty() ? null : byDesc.get(0)) ? 1 : 0);
                }
                for (int enchant = 0; enchant < 3; enchant++) {
                    long quantity = 0, instances = 0;
                    for (L1ItemInstance item : unequipped) {
                        if (item.getEnchantLevel() != enchant || item.getCount() <= 0) continue;
                        quantity += item.getCount();
                        instances += item.isStackable() ? item.getCount() : 1;
                    }
                    check(quantity, inv.GetRecipeMaterialAmount(id, enchant));
                    check(instances, inv._items.GetRecipeMaterialAmount(id, enchant, true, true));
                }
            }
        }
        inv._items.clear();
        System.out.println("PASS linked selection, order and recipe quantity regression");
    }
    private static void checkDeleteAmount() {
        CInventory inv = new CInventory();
        L1EtcItem t = template(123, 1000);
        t.setItemDescId(456);
        int[] amounts = {Integer.MIN_VALUE, -1, 0, 1, 2, 3, 4, 5, Integer.MAX_VALUE};
        for (int available = 1; available <= 4; available++) {
            for (int requested : amounts) {
                for (int descMode = 0; descMode < 3; descMode++) {
                    inv._items.clear();
                    L1ItemInstance item = new L1ItemInstance(t, available);
                    // Avoid the world unregister path in this isolated test:
                    // full deletion needs a world-registration integration test.
                    if (requested >= available && descMode != 2) continue;
                    item.setId(12345);
                    inv._items.Push(item);
                    int expected = requested <= 0 || descMode == 2 ? 0 : requested;
                    int actual = descMode == 0 ? inv.DeleteItemAmount(item, requested)
                            : inv.DeleteItemAmount(item, requested, descMode == 1 ? 456 : 457);
                    check(expected, actual);
                    check(available - expected, item.getCount());
                    check(1, inv._items.Have(item) ? 1 : 0);
                }
            }
        }
        inv._items.clear();
        System.out.println("PASS partial deletion and desc-ID guards");
    }
    public static void main(String[] args) throws Exception {
        checkDeleteAmount();
        checkLinkedSearch();
        checkItemCopyAttributes();
        checkChargeClassification();
        checkTransferAdmission();
        checkWarehouseDispatch();
        checkNewItemInitialization();
        Random rng = new Random(20261125L);
        L1EtcItem[] templates = {template(1,0),template(2,1),template(3,999),
            template(4,1000),template(5,1001),template(6,Integer.MAX_VALUE)};
        CInventory a = new CInventory(), b = new CInventory();
        CItemList x = a._items, y = b._items;
        Config.RATE_WEIGHT_LIMIT=1.0;
        check(0,x.GetCarryingWeight());
        ArrayList<L1ItemInstance> detached=new ArrayList<>();
        for (int i=0;i<180;i++) {
            L1ItemInstance it=new L1ItemInstance(templates[i%templates.length],1+i);
            it.setId(i+1); detached.add(it);
        }
        for(int n=0;n<60000;n++) {
            CItemList src=rng.nextBoolean()?x:y, dst=src==x?y:x;
            switch(rng.nextInt(10)) {
            case 0: if(!detached.isEmpty()) src.Push(detached.remove(detached.size()-1)); break;
            case 1: if(!src.isEmpty()){L1ItemInstance it=src.get(rng.nextInt(src.size()));src.Pop(it);detached.add(it);} break;
            case 2: if(!src.isEmpty()){L1ItemInstance it=src.get(rng.nextInt(src.size()));src.Pop(it);dst.Push(it);} break;
            case 3: if(!src.isEmpty())src.get(rng.nextInt(src.size())).setCount(rng.nextInt(2_000_000_001));break;
            case 4: if(!src.isEmpty())src.get(rng.nextInt(src.size())).setItem(templates[rng.nextInt(templates.length)]);break;
            case 5: if(!src.isEmpty()){
                L1ItemInstance it=src.get(rng.nextInt(src.size()));int old=it.getCount();
                int change=rng.nextInt(2001)-1000;
                boolean ok=(long)old+change>=0&&(long)old+change<=CInventory.MAX_AMOUNT;
                check(ok?1:0,it.AddAmount(change)?1:0);check(ok?(long)old+change:old,it.getCount());
                it.setCount(old); // rollback/replay path
            }break;
            case 6: templates[rng.nextInt(templates.length)].setWeight(rng.nextInt(Integer.MAX_VALUE));break;
            case 7: src.shuffle();break;
            case 8: if(n%32==0){detached.addAll(src.snapshot());src.clear();}break;
            case 9: if(n%16==0 && !detached.isEmpty()){
                ArrayList<L1ItemInstance> rows=new ArrayList<>();
                for(int j=0, count=Math.min(4,detached.size());j<count;j++) rows.add(detached.remove(detached.size()-1));
                detached.addAll(src.replaceDetached(rows));
            }break;
            }
            long rx=reference(x),ry=reference(y);
            check(rx,x.GetCarryingWeight());check(ry,y.GetCarryingWeight());
            Config.RATE_WEIGHT_LIMIT=(n%3==0?0.5:n%3==1?1.0:2.75);
            check((int)(rx/Config.RATE_WEIGHT_LIMIT),a.GetCarryingWeight());
            check((int)(ry/Config.RATE_WEIGHT_LIMIT),b.GetCarryingWeight());
            if(n%64==0){check(rx,x.ValidateCarringWeight());check(ry,y.ValidateCarringWeight());}
        }
        // Batch claim rejection must leave both cached totals untouched.
        L1ItemInstance owned=new L1ItemInstance(templates[0],1);
        x.Push(owned); long beforeX=reference(x),beforeY=reference(y);
        try{y.addAll(java.util.List.of(new L1ItemInstance(templates[0],2),owned));throw new AssertionError("double claim");}
        catch(IllegalStateException expected){}
        check(beforeX,x.GetCarryingWeight());check(beforeY,y.GetCarryingWeight());
        x.clear();y.clear();
        // Stress a detached setter racing membership publication. Final
        // quiescent totals must remain exact; no cross-list lock acquisition.
        L1ItemInstance racing=new L1ItemInstance(templates[1],1);
        AtomicReference<Throwable> failure=new AtomicReference<>();
        Thread setter=new Thread(()->{try{for(int n=0;n<100000;n++)racing.setCount(n);}catch(Throwable t){failure.set(t);}});
        setter.start();
        for(int n=0;n<20000;n++){
            x.Push(racing); x.GetCarryingWeight(); x.Pop(racing);
            y.Push(racing); y.GetCarryingWeight(); y.Pop(racing);
        }
        x.Push(racing); setter.join(10000);
        if(setter.isAlive())throw new AssertionError("setter deadlock");
        if(failure.get()!=null)throw new AssertionError(failure.get());
        check(reference(x),x.GetCarryingWeight());check(0,y.GetCarryingWeight());
        x.Pop(racing);check(0,x.GetCarryingWeight());
        System.out.println("PASS actual production carrying-weight regression checks="+checks);
    }
}
