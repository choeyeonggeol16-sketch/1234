package l1j.server.server.model.character.slot;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.RandomAccess;
import l1j.server.server.model.inventory.CUserInventory;
import l1j.server.server.model.inventory.L1EquipmentSlot;
import l1j.server.server.model.inventory.L1PolyMorph;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.inventory.EquipRules;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

/**
 * SlotChange 관리 전용.
 */
public final class SwapSlotItems {

    private static final int SLOT0 = 0;
    private static final int SLOT1 = 1;

    private static final int MAX_BUFFER = 32;
    private static final int COUNT_MASK = 63;
    private static final L1ItemInstance[] EMPTY_ITEMS = {};
    private static final int[] EMPTY_IDS = {};

    private final L1PcInstance owner;

    private final IntIdList slot0 = new IntIdList(0);
    private final IntIdList slot1 = new IntIdList(0);

    private final List<Integer> slot0View = new IntIdListView(slot0);
    private final List<Integer> slot1View = new IntIdListView(slot1);

    private int slotNumber = 0;

    private L1ItemInstance[] unequipBuffer = EMPTY_ITEMS;
    private L1ItemInstance[] equipBuffer = EMPTY_ITEMS;
    private L1ItemInstance[] plannedBuffer = EMPTY_ITEMS;
    private L1ItemInstance[] originalBuffer = EMPTY_ITEMS;

    /** applySlot 중첩 실행 방지  */
    private boolean swapping = false;

    public SwapSlotItems(L1PcInstance owner) {
        this.owner = owner;
    }

    public int getSlotNumber() {
        return slotNumber;
    }

    public void setSlotNumber(int i) {
        this.slotNumber = i;
    }

    public void addSlotItem(int slotNum, int itemObjId, boolean snapshotEquipped) {
        final IntIdList list = getList(slotNum);
        if (list == null) return;

        if (snapshotEquipped) {
            final CUserInventory inventory = owner.getInventory();
            if (!inventory.tryBeginEquipmentChange()) return;
            try {
                if (swapping) return;
                try {
                    final int count = collectEquipped(inventory);
                    if (count < 0) return;
                    list.clear();
                    for (int i = 0; i < count; i++) list.add(originalBuffer[i].getId());
                    list.sortUnique();
                } finally {
                    clearPlan();
                }
            } finally {
                inventory.endEquipmentChange();
            }
            return;
        }

        list.add(itemObjId);
        list.sortUnique();
    }

    /** 유효하지 않은 슬롯이면 빈 리스트 반환  */
    public List<Integer> getSlotItems(int slotNum) {
    	switch (slotNum) {
    	case SLOT0: return slot0View;
    	case SLOT1: return slot1View;
    	default: return Collections.<Integer>emptyList();
    	}
    }

    /**
     * 내부 리스트 직접 노출용.
     */
    public IntIdList getSlotRaw(int slotNum) {
        return getList(slotNum);
    }

    public void applySlot(int slotNum) {
        final IntIdList desired = getList(slotNum);
        if (desired == null) return;
        final CUserInventory inventory = owner.getInventory();
        if (!inventory.tryBeginEquipmentChange()) return;
        try {
            if (!swapping) applySlot(slotNum, desired, inventory);
        } finally {
            inventory.endEquipmentChange();
        }
    }

    private void applySlot(int slotNum, IntIdList desired, CUserInventory inventory) {
        final int spriteId = owner.getCurrentSpriteId();
        final L1ItemInstance originalPrimary = owner.getWeapon();
        int unequipCount = 0, equipCount = 0, plannedCount = 0, originalCount = 0;
        boolean mutated = false, completed = false;
        swapping = true;
        try {
            desired.sortUnique();
            final int counts = planFromSlots(desired, inventory, spriteId);
            if (counts < 0) return;
            originalCount = (counts >>> 18) & COUNT_MASK;
            plannedCount = (counts >>> 12) & COUNT_MASK;
            equipCount = (counts >>> 6) & COUNT_MASK;
            unequipCount = counts & COUNT_MASK;
            for (int i = 0; i < unequipCount; i++) {
                final L1ItemInstance item = unequipBuffer[i];
                if (item.getItem().getType2() == 1 && item.getItem().getBless() == 2) {
                    SC_ServerMessage.send(owner, 150);
                    return;
                }
            }

            // Validate all target equipment together, including future rings and
            // second weapons, before touching the currently equipped state.
            if (!EquipRules.canEquipPreset(owner, inventory, plannedBuffer, plannedCount)) return;
            for (int i = 0; i < equipCount; i++) {
                if (!equipBuffer[i].startEquipmentTimer(owner)) return;
            }

            mutated = true;
            for (int i = 0; i < unequipCount; i++) {
                owner.UnequipItem(unequipBuffer[i]);
                if (unequipBuffer[i].isEquipped()) return;
            }
            for (int i = 0; i < equipCount; i++) {
                final L1ItemInstance item = equipBuffer[i];
                if (inventory.GetRegisteredItemByServerID(item.getId()) != item || !canEquipNow(inventory, item)) return;
                owner.EquipItem(item);
                if (!item.isEquipped()) return;
            }
            for (int i = 0; i < plannedCount; i++) {
                if (!plannedBuffer[i].isEquipped()) return;
            }
            // Do not publish the new preset number on an incomplete switch.
            slotNumber = slotNum;
            completed = true;
        } finally {
            try {
                if (mutated && !completed)
                    restoreEquipment(inventory, originalCount, equipCount, originalPrimary);
            } finally {
                try {
                    for (int i = 0; i < equipCount; i++) {
                        final L1ItemInstance item = equipBuffer[i];
                        if (item != null && !item.isEquipped()) item.stopEquipmentTimer();
                    }
                } finally {
                    clearPlan();
                    swapping = false;
                }
            }
        }
    }

    /** Read only currently applied weapon and armor references. A disagreement
     * with the equipped index rejects the request before any mutation. */
    private int collectEquipped(CUserInventory inventory) {
        final L1EquipmentSlot slots = owner.getEquipSlot();
        final List<L1ItemInstance> armors = slots.getArmors();
        int count = 0;
        for (int i = -2, n = armors.size(); i < n; i++) {
            final L1ItemInstance item = i == -2 ? slots.getWeapon()
                    : i == -1 ? slots.getSecondWeapon() : armors.get(i);
            if (item == null && i < 0) continue;
            if (item == null || item.getItem() == null || !item.isEquipped()
                    || count == MAX_BUFFER
                    || inventory.GetRegisteredItemByServerID(item.getId()) != item
                    || containsIdentity(originalBuffer, count, item)) return -1;
            if (count == originalBuffer.length) originalBuffer = grow(originalBuffer);
            originalBuffer[count++] = item;
        }
        return inventory.GetEquippedObjectCount() == count ? count : -1;
    }

    /** Resolve desired IDs directly; sort only the bounded selected rows in
     * legacy inventory order to retain weapon and accessory assignment. */
    private int planFromSlots(IntIdList desired, CUserInventory inventory, int spriteId) {
        final int original = collectEquipped(inventory);
        if (original < 0 || !inventory.SortSelectedByInventoryOrder(originalBuffer, original))
            return -1;
        int planned = 0, equip = 0, unequip = 0;
        for (int i = 0, n = desired.size(); i < n; i++) {
            final L1ItemInstance item = inventory.GetRegisteredItemByServerID(desired.get(i));
            if (item == null || item.getItem() == null || !canEquipWithPoly(item, spriteId)) continue;
            if (planned == MAX_BUFFER) return -1;
            if (planned == plannedBuffer.length) plannedBuffer = grow(plannedBuffer);
            plannedBuffer[planned++] = item;
            if (item.isEquipped()) {
                if (!containsIdentity(originalBuffer, original, item)) return -1;
            }
        }
        if (!inventory.SortSelectedByInventoryOrder(plannedBuffer, planned)) return -1;
        for (int i = 0; i < planned; i++) {
            final L1ItemInstance item = plannedBuffer[i];
            if (item.isEquipped()) continue;
            if (equip == equipBuffer.length) equipBuffer = grow(equipBuffer);
            equipBuffer[equip++] = item;
        }
        for (int i = 0; i < original; i++) {
            final L1ItemInstance item = originalBuffer[i];
            if (containsIdentity(plannedBuffer, planned, item)) continue;
            if (unequip == unequipBuffer.length) unequipBuffer = grow(unequipBuffer);
            unequipBuffer[unequip++] = item;
        }
        return (original << 18) | (planned << 12) | (equip << 6) | unequip;
    }

    private static boolean containsIdentity(L1ItemInstance[] items, int count, L1ItemInstance item) {
        for (int i = 0; i < count; i++) {
            if (items[i] == item) return true;
        }
        return false;
    }

    private void clearPlan() {
        Arrays.fill(unequipBuffer, null);
        Arrays.fill(equipBuffer, null);
        Arrays.fill(plannedBuffer, null);
        Arrays.fill(originalBuffer, null);
    }

    private void restoreEquipment(CUserInventory inventory, int originalCount,
            int equipCount, L1ItemInstance originalPrimary) {
        // Runtime rejection after preflight: restore the original worn image
        // using normal inventory persistence, without a separate DB transaction.
        for (int i = 0; i < equipCount; i++) {
            final L1ItemInstance item = equipBuffer[i];
            if (item == null || !item.isEquipped()) continue;
            try {
                owner.UnequipItem(item);
            } catch (RuntimeException failure) {
                java.util.logging.Logger.getLogger(SwapSlotItems.class.getName())
                        .log(java.util.logging.Level.SEVERE, "Preset rollback remove failed", failure);
            }
        }
        // Preserve the original primary position when restoring dual weapons.
        if (originalPrimary != null && !originalPrimary.isEquipped()
                && inventory.GetRegisteredItemByServerID(originalPrimary.getId()) == originalPrimary) {
            try {
                owner.EquipItem(originalPrimary);
            } catch (RuntimeException failure) {
                java.util.logging.Logger.getLogger(SwapSlotItems.class.getName())
                        .log(java.util.logging.Level.SEVERE, "Preset rollback primary failed", failure);
            }
        }
        for (int i = 0; i < originalCount; i++) {
            final L1ItemInstance item = originalBuffer[i];
            if (item == null || item.isEquipped() || inventory.GetRegisteredItemByServerID(item.getId()) != item) continue;
            try {
                owner.EquipItem(item);
                if (!item.isEquipped()) {
                    java.util.logging.Logger.getLogger(SwapSlotItems.class.getName())
                            .severe("Preset rollback restore refused: item=" + item.getId());
                }
            } catch (RuntimeException failure) {
                java.util.logging.Logger.getLogger(SwapSlotItems.class.getName())
                        .log(java.util.logging.Level.SEVERE, "Preset rollback restore failed", failure);
            }
        }
    }

    private static L1ItemInstance[] grow(L1ItemInstance[] buffer) {
        return Arrays.copyOf(buffer, Math.min(MAX_BUFFER, Math.max(4, buffer.length * 2)));
    }

    private boolean canEquipNow(CUserInventory inventory, L1ItemInstance item) {
        if (item.getItem().getType2() == 2) return EquipRules.canEquipArmor(owner, inventory, item).ok;
        if (item.getItem().getType2() != 1) return true;
        if (!EquipRules.canEquipWeaponBasic(owner, inventory, item).ok) return false;
        final L1ItemInstance primary = owner.getWeapon();
        return primary == null || EquipRules.canEquipAsSecondWeaponSlayer(owner, inventory, primary, item).ok;
    }

    /** 변신 상태에서 장착 가능 여부 (무기/방어구 모두 체크) */
    private static boolean canEquipWithPoly(L1ItemInstance item, int spriteId) {
    	if (item.getItem() == null) return true;
    	final int type2 = item.getItem().getType2();
    	switch (type2) {
    	case 1: // 무기
    		return L1PolyMorph.isEquipableWeapon(spriteId, item.getItem().getType());
    	case 2: // 방어구
    		return L1PolyMorph.isEquipableArmor(spriteId, item.getItem().getType());
    	default:
    		return true;
    	}
    }

    private IntIdList getList(int slotNum) {
        if (slotNum == SLOT0) return slot0;
        if (slotNum == SLOT1) return slot1;
        return null;
    }

    // =========================
    // int[] 기반 리스트
    // =========================
    public static final class IntIdList {
        private int[] a;
        private int size;

        public IntIdList(int cap) { this.a = cap > 0 ? new int[Math.max(4, cap)] : EMPTY_IDS; }

        public int size() { return size; }
        public boolean isEmpty() { return size == 0; }
        public void clear() { size = 0; }

        public int get(int idx) {
            if (idx < 0 || idx >= size) {
                throw new IndexOutOfBoundsException("idx=" + idx + ", size=" + size);
            }
            return a[idx];
        }

        public void add(int v) {
            if (size >= a.length) {
                a = Arrays.copyOf(a, a.length + (a.length >> 1) + 8);
            }
            a[size++] = v;
        }

        /** 오름차순 정렬 + 중복 제거. contains()는 이게 선행되어야 정확함 */
        public void sortUnique() {
            if (size <= 1) return;
            Arrays.sort(a, 0, size);
            int w = 1;
            int prev = a[0];
            for (int i = 1; i < size; i++) {
                final int cur = a[i];
                if (cur != prev) {
                    a[w++] = cur;
                    prev = cur;
                }
            }
            size = w;
        }

        /** 정렬된 상태에서만 정확함. sortUnique() 이후에 사용할 것 */
        public boolean contains(int v) {
            if (size == 0) return false;
            return Arrays.binarySearch(a, 0, size, v) >= 0;
        }
    }

    private static final class IntIdListView extends AbstractList<Integer> implements RandomAccess {
        private final IntIdList src;

        IntIdListView(IntIdList src) { this.src = src; }

        @Override public Integer get(int index) {
            return Integer.valueOf(src.get(index));
        }

        @Override public int size() { return src.size(); }
    }
    
    public void clear() {
        if (slot0 != null) slot0.clear();
        if (slot1 != null) slot1.clear();
        
        Arrays.fill(unequipBuffer, null);
        Arrays.fill(equipBuffer, null);
        Arrays.fill(plannedBuffer, null);
        Arrays.fill(originalBuffer, null);
    }
}
