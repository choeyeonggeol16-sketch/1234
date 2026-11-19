package l1j.server.server.model.character.slot;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.RandomAccess;
import l1j.server.server.model.L1PcInventory;
import l1j.server.server.model.L1PolyMorph;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;

/**
 * SlotChange 관리 전용.
 */
public final class SlotChangeManager {

  //  private static final Logger _log = Logger.getLogger(SlotChangeManager.class.getName());

    private static final int SLOT0 = 0;
    private static final int SLOT1 = 1;

    /** 버퍼 상한. 이 값을 넘는 분류는 로그 남기고 스킵 */
    private static final int MAX_BUFFER = 32;

    private final L1PcInstance owner;

    private final IntIdList slot0 = new IntIdList(MAX_BUFFER);
    private final IntIdList slot1 = new IntIdList(MAX_BUFFER);

    private final List<Integer> slot0View = new IntIdListView(slot0);
    private final List<Integer> slot1View = new IntIdListView(slot1);

    private int slotNumber = 0;

    /** 재사용 버퍼*/
    private final L1ItemInstance[] unequipBuffer = new L1ItemInstance[MAX_BUFFER];
    private final L1ItemInstance[] equipBuffer = new L1ItemInstance[MAX_BUFFER];

    /** applySlot 중첩 실행 방지  */
    private boolean swapping = false;

    public SlotChangeManager(L1PcInstance owner) {
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
            list.clear();
            final Collection<L1ItemInstance> items = owner.getInventory().getItems();
            for (L1ItemInstance it : items) {
                if (it != null && it.isEquipped()) list.add(it.getId());
            }
            list.sortUnique();
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

    /** 지정 슬롯 장비 일괄 적용: 벗을 것/입을 것을 한 번에 처리 */
    public void applySlot(int slotNum) {
        final L1PcInventory inv = owner.getInventory();
        if (!inv.tryBeginEquipmentChange()) return;
        try {
    	if (swapping) {
            return;
        }

        final IntIdList desired = getList(slotNum);
       
        if (desired == null) return;

        this.slotNumber = slotNum;

        desired.sortUnique();

        final Collection<L1ItemInstance> items = inv.getItems();
        final int spriteId = owner.getCurrentSpriteId();

        int unequipCount = 0;
        int equipCount = 0;
        boolean unequipOverflowLogged = false;
        boolean equipOverflowLogged = false;

        // 인벤토리 1회 순회로 벗을 것 / 입을 것 동시 분류
        for (L1ItemInstance item : items) {
            if (item == null) continue;

            final boolean isEquipped = item.isEquipped();
            final boolean isTarget = desired.contains(item.getId());

            // 타겟도 아니고 입고 있지도 않은 아이템은 스킵
            if (!isEquipped && !isTarget) continue;

            final boolean canEquip = canEquipWithPoly(item, spriteId);

            if (isEquipped) {
                // 입고 있는데 타겟이 아니거나, 변신 때문에 못 차면 벗기
                if (!isTarget || !canEquip) {
                    if (unequipCount < MAX_BUFFER) {
                        unequipBuffer[unequipCount++] = item;
                    } else if (!unequipOverflowLogged) {
                        unequipOverflowLogged = true;
//                        _log.warning("[SlotChange] unequip 버퍼 초과(" + MAX_BUFFER
//                                + ") - 초과분 스킵됨: " + owner.getName());
                    }
                }
            } else {
                if (isTarget && canEquip) {
                    if (equipCount < MAX_BUFFER) {
                        equipBuffer[equipCount++] = item;
                    } else if (!equipOverflowLogged) {
                        equipOverflowLogged = true;
//                        _log.warning("[SlotChange] equip 버퍼 초과(" + MAX_BUFFER
//                                + ") - 초과분 스킵됨: " + owner.getName());
                    }
                }
            }
        }

        if (unequipCount == 0 && equipCount == 0) return;

        // 일괄 처리 (패킷/DB/스탯 재계산 스팸 방지)
        swapping = true;
        try {
            for (int i = 0; i < unequipCount; i++) {
                inv.setEquipped(unequipBuffer[i], false);
            }
            for (int i = 0; i < equipCount; i++) {
                inv.setEquipped(equipBuffer[i], true);
            }
        } finally {
            Arrays.fill(unequipBuffer, 0, unequipCount, null);
            Arrays.fill(equipBuffer, 0, equipCount, null);
            swapping = false;
        }
        } finally {
            inv.endEquipmentChange();
        }
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

        public IntIdList(int cap) { this.a = new int[Math.max(4, cap)]; }

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
    }
}
