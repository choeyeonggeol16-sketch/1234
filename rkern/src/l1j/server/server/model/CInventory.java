
package l1j.server.server.model;

import l1j.server.server.datatables.CObjectDB;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import l1j.server.server.model.Instance.CItemList;

import l1j.server.Config;
import l1j.server.server.IdFactory;
import l1j.server.server.datatables.FurnitureSpawnTable;
import l1j.server.server.datatables.LetterTable;
import l1j.server.server.datatables.PetTable;
import l1j.server.server.model.Instance.L1FurnitureInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Warehouse.Warehouse;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.item.search.inventory.InventoryFindItemFilter;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.world.instance.inn.InnHelper;
import l1j.server.server.packets.Protocol.MainServer.Proto.CS_ALCHEMY_MAKE_REQ;
import l1j.server.server.packets.Protocol.MainServer.Proto.Craft.CraftInputItemSlotInfo;
import l1j.server.server.shared.L1Type;
import l1j.server.server.storage.CacheD;
import l1j.server.server.templates.L1Item;

public class CInventory extends L1Object {
	private static final long serialVersionUID = 1L;

	protected final CItemList _items = new CItemList();
	
	public static final int MAX_AMOUNT = 2000000000; // 2G
	public static final int MAX_WEIGHT = 1500;
	public static final int OK = 0;
	public static final int SIZE_OVER = 1;
	public static final int WEIGHT_OVER = 2; 
	public static final int AMOUNT_OVER = 3;
	public static final int WAREHOUSE_TYPE_PERSONAL = 0;
	public static final int WAREHOUSE_TYPE_CLAN = 1;

	public CInventory() {
	}
	
	/**
	 * PC 인벤토리는 CUserInventory에서 캐시를 사용한다.
	 * 일반/NPC 인벤토리는 별도 ConcurrentHashMap을 만들지 않고 필요 시 목록에서 계산한다.
	 */
	public int GetItemAmount(int itemId) {
		return _items.GetItemCountByClassID(itemId);
	}

	/** PC 인벤토리 캐시 갱신 훅. 일반 인벤토리는 아무것도 하지 않는다. */
	protected void addItemCountCache(int itemId, int amount) {
	}

	/** PC 인벤토리 캐시 갱신 훅. 일반 인벤토리는 아무것도 하지 않는다. */
	protected void removeItemCountCache(int itemId, int amount) {
	}

	/** 아이템 존재 여부만 확인 */
	public boolean HaveItemAmount(int itemId) {
		   return HaveItemAmount(itemId, 1);
	}
	
	public boolean HaveAnyItemByClassId(int... itemIds) {
	    for (int id : itemIds) {
	        if (GetItemAmount(id) > 0) {
	            return true;
	        }
	    }
	    return false;
	}

	public boolean HaveItemAmount(int itemId, int count) {
	    if (count <= 0) {
	        return true; 
	    }
	    return GetItemAmount(itemId) >= count;
	}
	
	public boolean IsEquippedByClassIDCached(int itemId) {
		return _items.IsEquippedByClassID(itemId);
	}

	public boolean isEquippedAny(int... itemIds) {
	    for (int id : itemIds) {
	        if (IsEquippedByClassIDCached(id)) return true;
	    }
	    return false;
	}
	
	public int getTypeAndItemIdEquipped(int type2, int type, int ItemId) {
		// itemId도추가검색
		int equipeCount = 0;
		L1ItemInstance item = null;
		for (Object itemObject : _items) {
			item = (L1ItemInstance) itemObject;
			if (item.getItem().getType2() == type2 // 0 etc아템 1 무기 2아머
					&& item.getItem().getType() == type // type2 의 세부항목들
					&& item.getItem().getItemId() == ItemId// 아이템의 아이디
					&& item.isEquipped()) { // 착용
				equipeCount++;
			}
		}
		return equipeCount;
	}
	public boolean EnchantEquipped(int itemid, int enchant) {
		L1ItemInstance item = null;
		for (Object itemObject : _items) {
			if (itemObject == null)
				continue;
			item = (L1ItemInstance) itemObject;
			if (item.getItem().getItemId() == itemid && item.isEquipped() && item.getEnchantLevel() == enchant) {
				return true;
			}
		}
		return false;
	}
	public int getTypeEquipped(int type, int type2) {
		// itemId도추가검색
		int equipeCount = 0;
		L1ItemInstance item = null;
		for (Object itemObject : _items) {
			item = (L1ItemInstance) itemObject;
			if (item.getItem().getType2() == type // 0 etc아템 1 무기 2아머
					&& item.getItem().getType() == type2 // type2 의 세부항목들
					&& item.isEquipped()) { // 착용
				equipeCount++;
			}
		}
		return equipeCount;
	}

	public int getTypeAndGradeEquipped(int type2, int type, int Grade) {
		int equipeCount = 0;
		L1ItemInstance item = null;
		for (Object itemObject : _items) {
			item = (L1ItemInstance) itemObject;
			if (item.getItem().getType2() == type2 // 0 etc아템 1 무기 2아머
					&& item.getItem().getType() == type // type2 의 세부항목들
					&& item.getItem().getGrade() == Grade && item.isEquipped()) { // 착용
				equipeCount++;
			}
		}
		return equipeCount;
	}
	

	public int getSize() {
		return _items.size();
	}

	public List<L1ItemInstance> getItems() {
		return _items;
	}
	
	public List<L1ItemInstance> getItems(InventoryFindItemFilter filter){
		ArrayList<L1ItemInstance> itemList = new ArrayList<L1ItemInstance>(_items.size());
		for (L1ItemInstance item : _items) {
			if (item == null)
				continue;
			
			if(!filter.isFilter(item))
				itemList.add(item);
		}
		return itemList;
	}

	public int GetCarryingWeight() {
        return (int) (_items.GetCarryingWeight() / Config.RATE_WEIGHT_LIMIT);
	}

    /** OFF-style explicit aggregate validation; L1 rate remains a read-time conversion. */
    public int ValidateCarringWeight() {
        return (int) (_items.ValidateCarringWeight() / Config.RATE_WEIGHT_LIMIT);
    }

	public int CheckAddableAdena(int count){
		if(count < 0 || count > MAX_AMOUNT)
			return AMOUNT_OVER;
		
		if(!HaveItemByClassId(L1ItemId.ADENA) && getSize() >= 180)
			return SIZE_OVER;
		
		return OK;
	}
	
	
	
	public int CheckAddable(L1ItemInstance item, int count) {
		if (item == null) {
			return -1;
		}
		if (count < 0 || count > MAX_AMOUNT) {
			return AMOUNT_OVER;
		}

		if (item.getCount() <= 0 || count <= 0) {
			return -1;
		}
		if (getSize() > Config.MAX_NPC_ITEM || (getSize() == Config.MAX_NPC_ITEM && (!item.isStackable() || findMergeItem(item) == null))) {
			return SIZE_OVER;
		}

		final long addedWeight = (long) item.getItem().getWeight() * count / 1000L;
		final long weight = GetCarryingWeight() + addedWeight + 1L;
		if (weight < 0 || weight > Integer.MAX_VALUE || addedWeight < 0) {
			return WEIGHT_OVER;
		}
		if (weight > (MAX_WEIGHT * Config.RATE_WEIGHT_LIMIT_PET)) {
			return WEIGHT_OVER;
		}

		L1ItemInstance itemExist = findMergeItem(item);
		if (itemExist != null && !itemExist.IsAddableAmount(count)) {
			return AMOUNT_OVER;
		}

		return OK;
	}

	public int CheckAddableToWarehouse(L1ItemInstance item, int count, int type) {
		if (item == null) {
			return -1;
		}
		if (item.getCount() <= 0 || count <= 0) {
			return -1;
		}
		int maxSize = 100;
		if (type == WAREHOUSE_TYPE_PERSONAL) {
			maxSize = Config.MAX_PERSONAL_WAREHOUSE_ITEM;
		} else if (type == WAREHOUSE_TYPE_CLAN) {
			maxSize = Config.MAX_CLAN_WAREHOUSE_ITEM;
		}
		if (getSize() > maxSize
				|| (getSize() == maxSize && (!item.isStackable() || !HaveItemByClassId(item.getItem().getItemId())))) {
			return SIZE_OVER;
		}

		return OK;
	}

	public synchronized L1ItemInstance CreateOrAddItem(int id, int count, int enchant) {
		return CreateOrAddItem(id, count, true, true, enchant);
	}

	public L1ItemInstance CreateOrAddItem(int id, int count) {
		return CreateOrAddItem(id, count, true);
	}
	
	public synchronized L1ItemInstance CreateOrAddItem(int id, int count, boolean isIden) {
		return CreateOrAddItem(id, count, false, isIden, 0);
	}

	/** Preserve legacy constructor, identification and enchant ordering for each overload. */
	private L1ItemInstance CreateOrAddItem(int id, int count, boolean withEnchant, boolean identified, int enchant) {
		if (rejectPendingItemTransfer())
			throw new IllegalStateException("Item mutation refused while CacheD transfer pending");
		if (count <= 0 || count > MAX_AMOUNT) return null;
		final L1Item template = CObjectDB.GetItem(id);
		if (template == null) return null;
        if (this instanceof CUserInventory && (long) GetItemAmount(id) + count > MAX_AMOUNT) return null;
		if (template.isStackable()) {
			final L1ItemInstance item = new L1ItemInstance(template, count);
			if (!withEnchant) item.setIdentified(identified);
            // Initialize before matching. Allocate an ID only for a new row.
			return AddItemToInventory(item, false, false, 0, true);
		}
        final int limit = this instanceof CUserInventory ? CUserInventory.MAX_SIZE : Config.MAX_NPC_ITEM;
        // Charge-based objects can merge into an existing row; do not apply
        // a per-instance slot limit to those legacy refill items.
        if (count > limit || (!Warehouse.chargeMergeItem(id) && (long) getSize() + count > limit)) return null;
		L1ItemInstance result = null;
		for (int i = 0; i < count; i++) {
			final L1ItemInstance item = new L1ItemInstance(template, 1);
			if (withEnchant) {
				item.setId(IdFactory.getInstance().nextId());
				item.setEnchantLevel(enchant);
				item.setIdentified(true);
			} else {
				item.setIdentified(identified);
				item.setId(IdFactory.getInstance().nextId());
			}
			result = registerCreatedItem(item);
		}
		return result;
	}

	/** The newly allocated object belongs to this creation path until insertion succeeds. */
	private L1ItemInstance registerCreatedItem(L1ItemInstance item) {
		final L1World world = L1World.getInstance();
		try {
			world.storeObject(item);
			final L1ItemInstance stored = CreateOrAddItem(item);
			if (stored != item && !_items.Have(item)) world.removeObject(item);
			return stored;
		} catch (RuntimeException failure) {
			if (!_items.Have(item)) {
				try { world.removeObject(item); }
				catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
			}
			throw failure;
		}
	}

	/** Only the four existing registration paths use this rollback contract. */
	private void insertLinkedItem(L1ItemInstance item) {
		_items.Push(item);
		try {
			insertItem(item);
		} catch (RuntimeException failure) {
			_items.Pop(item);
			throw failure;
		}
	}
	
    /** Revert item and count cache together if persistence admission fails. */
    private void changeStackCount(L1ItemInstance item, int change) {
        final int before = item.getCount();
        final long after = (long) before + change;
        if (after <= 0 || !item.IsAddableAmount(change)) throw new IllegalArgumentException("Invalid item count");
        if (change > 0) addItemCountCache(item.getItemId(), change);
        else removeItemCountCache(item.getItemId(), -change);
        try {
            item.setCount((int) after);
            updateItem(item);
        } catch (RuntimeException failure) {
            item.setCount(before);
            try {
                if (change > 0) removeItemCountCache(item.getItemId(), change);
                else addItemCountCache(item.getItemId(), -change);
            } catch (RuntimeException rollback) { failure.addSuppressed(rollback); }
            throw failure;
        }
    }

    protected void updateStoredCharge(L1ItemInstance item) { updateItem(item); }

    private void addStackCharge(L1ItemInstance item, int amount) {
        final int before = item.getChargeCount();
        final long after = (long) before + amount;
        if (after < 0 || after > Integer.MAX_VALUE) throw new IllegalArgumentException("Invalid item charge");
        item.setChargeCount((int) after);
        try { updateStoredCharge(item); }
        catch (RuntimeException failure) {
            item.setChargeCount(before);
            throw failure;
        }
    }

	public synchronized L1ItemInstance AddTreasureItemToInventory(L1ItemInstance item) {
		return AddItemToInventory(item, true, false, 0);
	}

	public synchronized L1ItemInstance CreateOrAddItem(L1ItemInstance item) {
		return AddItemToInventory(item, false, false, 0);
	}

	public synchronized L1ItemInstance CreateOrAddItem(L1ItemInstance item, int bless) {
		return AddItemToInventory(item, false, true, bless);
	}

	private L1ItemInstance AddItemToInventory(L1ItemInstance item, boolean treasure, boolean explicitBless, int bless) {
        return AddItemToInventory(item, treasure, explicitBless, bless, false);
    }

    private L1ItemInstance AddItemToInventory(L1ItemInstance item, boolean treasure, boolean explicitBless, int bless, boolean localNew) {
        if (!ValidateIncomingItem(item)) return null;
		int itemId = item.getItem().getItemId();
        if (this instanceof CUserInventory && !Warehouse.chargeMergeItem(itemId)
                && (long) GetItemAmount(itemId) + item.getCount() > MAX_AMOUNT) return null;
		if (!treasure && item.get_item_level() > 0) {
			item.set_item_level(item.get_item_level());
		}

		
		item.setX(getX());
		item.setY(getY());
		item.setMap(getMapId());
		final int chargeCount = item.GetInitialChargeCount(itemId);
		if (!treasure && Warehouse.chargeMergeItem(itemId)) {
			L1ItemInstance findItem = findMergeItem(item);
			if (findItem != null) {
				addStackCharge(findItem, chargeCount - java.util.concurrent.ThreadLocalRandom.current().nextInt(5));
                return findItem;
			}
		}

		item.setChargeCount(chargeCount);

		L1ItemInstance.InitializeNewItemLifetimeAndFuel(item, !treasure);

		if (!treasure) {
			item.setBless(explicitBless ? bless : item.getItem().getBless());
			item.setIdentified(true);
			item.set_bless_level(item.get_bless_level());
			item.set_item_level(item.get_item_level());
		} else {
			item.setBless(item.getBless());
		}
        if (item.isStackable()) {
            final L1ItemInstance target = findMergeItem(item);
            if (target != null) {
                changeStackCount(target, item.getCount());
                return target;
            }
        }
        if (localNew) {
            item.setId(IdFactory.getInstance().nextId());
            final L1World world = L1World.getInstance();
            try {
                world.storeObject(item);
                insertLinkedItem(item);
            } catch (RuntimeException failure) {
                if (!_items.Have(item)) world.removeObject(item);
                throw failure;
            }
        } else insertLinkedItem(item);
		return item;
	}

    protected final boolean rejectPendingItemTransfer() {
        if (!(this instanceof CUserInventory)) return false;
        final CUserInventory inventory = (CUserInventory) this;
        if (!inventory.getOwner().isItemTransferBusy()) return false;
        // A synchronous equipment scope holds the same CAS reservation, so a
        // persistent transfer cannot start. Its permit belongs to one thread;
        // other threads and genuinely pending transfers remain blocked.
        if (inventory.isEquipmentChangeOwner()) return false;
        return !CacheD.isItemTransferReplaying();
    }

	protected void persistTransferCount(L1ItemInstance item) {
	}

	protected void persistTransferCharge(L1ItemInstance item) {
	}

    /** Shared admission only; each caller retains its own merge and initialization rules. */
    private boolean ValidateIncomingItem(L1ItemInstance item) {
        if (rejectPendingItemTransfer())
            throw new IllegalStateException("Item mutation refused while CacheD transfer pending");
        if (item == null || item.getCount() <= 0 || item.getCount() > MAX_AMOUNT
                || (!item.isStackable() && item.getCount() != 1)) return false;
        CItemList.requireDetached(item);
        return true;
    }

	public synchronized L1ItemInstance storeTradeItem(L1ItemInstance item) {
        if (!ValidateIncomingItem(item)) return null;
        if (this instanceof CUserInventory && !Warehouse.chargeMergeItem(item.getItemId())
                && (long) GetItemAmount(item.getItemId()) + item.getCount() > MAX_AMOUNT) return null;
		if (item.isStackable()) {
			L1ItemInstance findItem = findMergeItem(item);
			if (findItem != null) {
                if (!findItem.IsAddableAmount(item.getCount())) return null;
                changeStackCount(findItem, item.getCount());
				findItem.setGiveItem(item.isGiveItem());
				return findItem;
			}
		}
		if (Warehouse.chargeMergeItem(item.getItem().getItemId())) {
            final L1ItemInstance findItem = findMergeItem(item);
			if (findItem != null) {
                final int chargeCount = item.getChargeCount();
                if (chargeCount < 0 || findItem.getChargeCount() < 0
                        || (long) findItem.getChargeCount() + chargeCount > Integer.MAX_VALUE) return null;
                addStackCharge(findItem, chargeCount);
				return findItem;
			}
		}
		item.setX(getX());
		item.setY(getY());
		item.setMap(getMapId());
		insertLinkedItem(item);

		return item;
	}

	public synchronized boolean DeleteItemInInventory(int itemid, int count) {
        if (count <= 0) return false;
        final L1Item template = CObjectDB.GetItem(itemid);
        if (template == null) return false;
        if (template.isStackable()) {
            final L1ItemInstance item = GetFirstItemByClassID(itemid);
            return item != null && item.getCount() >= count && DeleteItemAmount(item, count) == count;
        }
        final L1ItemInstance[] items = GetItemListByClassID(itemid);
        return DeleteSelectedItems(items, count, true, false, 0);
	}
	
	public synchronized boolean DeleteItemByDescID(int descid, int count) {
        if (count <= 0) return false;
        final L1ItemInstance[] items = GetItemListByDescID(descid);
        return DeleteSelectedItems(items, count, true, false, 0);
	}
	
	public synchronized boolean DeleteItemInInventory(int itemid, int count, int descid) {
        if (count <= 0) return false;
        final L1Item template = CObjectDB.GetItem(itemid);
        if (template == null) return false;
        if (template.isStackable()) {
            final L1ItemInstance item = GetFirstItemByClassID(itemid);
            return item != null && item.getCount() >= count
                    && item.getItem().getItemDescId() == descid
                    && DeleteItemAmount(item, count, descid) == count;
        }
        final L1ItemInstance[] items = GetItemListByClassID(itemid, descid);
        return DeleteSelectedItems(items, count, true, true, descid);
	}
	
	public synchronized boolean DeleteItemInInventory(int itemid) {
        final L1ItemInstance item = GetFirstItemByClassID(itemid);
        if (item == null || item.getCount() <= 0) return false;
        final int count = item.getCount();
        return DeleteItemAmount(item, count) == count;
	}

    private static final java.util.Comparator<L1ItemInstance> ITEM_ENCHANT_ORDER =
            (left, right) -> Integer.compare(left.getEnchantLevel(), right.getEnchantLevel());

    /** Select before debiting. Retain stable membership order on equal enchant.
     * The caller holds the inventory monitor. No per-request comparator object.
     * This is a sequential removal path, not a multi-row CacheD receipt.
     */
    private boolean DeleteSelectedItems(L1ItemInstance[] items, int count,
            boolean preferLowEnchant, boolean matchDescId, int descId) {
        if (count <= 0 || items.length < count) return false;
        if (preferLowEnchant && items.length > count) Arrays.sort(items, ITEM_ENCHANT_ORDER);
        for (int i = 0; i < count; i++) {
            final L1ItemInstance item = items[i];
            if (DeleteItemAmountInternal(item, 1, matchDescId, descId) != 1) return false;
        }
        return true;
    }

	public synchronized int DeleteItemAmount(int objectId) {
		L1ItemInstance item = GetByServerID(objectId);
		if (item == null) return 0;
		return DeleteItemAmount(item, item.getCount());
	}

	public synchronized int DeleteItemAmount(int objectId, int count) {
		L1ItemInstance item = GetByServerID(objectId);
		return DeleteItemAmount(item, count);
	}

	public synchronized int DeleteItemAmount(L1ItemInstance item) {
		if (item == null) return 0;
		return DeleteItemAmount(item, item.getCount());
	}

	public synchronized int DeleteItemAmount(L1ItemInstance item, int count) {
		return DeleteItemAmountInternal(item, count, false, 0);
	}
	
	public synchronized int DeleteItemAmount(L1ItemInstance item, int count, int descid) {
		return DeleteItemAmountInternal(item, count, true, descid);
	}

	/** Single removal path; retain caller-held inventory monitor and persistence order. */
	private int DeleteItemAmountInternal(L1ItemInstance item, int count, boolean matchDescId, int descId) {
        if (rejectPendingItemTransfer())
            throw new IllegalStateException("Item mutation refused while CacheD transfer pending");
		if (item == null || GetByServerID(item.getId()) != item) {
			return 0;
		}
		if (this instanceof L1GroundInventory && (item.isGroundPickupPending() || item.isGroundDropUnsettled())) return 0;
		if (matchDescId && item.getItem().getItemDescId() != descId) return 0;
		if (item.getCount() <= 0 || count <= 0) {
			return 0;
		}
		if (item.getCount() < count) {
			count = item.getCount();
		}
		if (item.getCount() == count) {
			int itemId = item.getItem().getItemId();
			if (itemId == 40314 || itemId == 40316) {
                // PC pet record + collar row are one CacheD receipt.
                if (!(this instanceof CUserInventory))
                    PetTable.getInstance().deletePet(item.getId());
			} else if (itemId >= 49016 && itemId <= 49025) {
                // PC inventory persistence deletes the letter and item in one receipt.
                // Ground/other inventories have no character_items row to couple.
                if (!(this instanceof CUserInventory))
                    LetterTable.getInstance().deleteLetter(item.getId());
			} else if (itemId >= 41383 && itemId <= 41400) {
				L1FurnitureInstance furniture = null;
				for (L1Object l1object : L1World.getInstance().getObject()) {
					if (l1object == null)
						continue;
					if (l1object instanceof L1FurnitureInstance) {
						furniture = (L1FurnitureInstance) l1object;
						if (furniture.getItemObjId() == item.getId()) {
							FurnitureSpawnTable.getInstance().deleteFurniture(furniture);
						}
					}
				}
			}
			Pop(item);
			L1World.getInstance().removeObject(item);
		} else {
			changeStackCount(item, -count);
		}
		return count;
	}

	/** Detach an inventory row. The transfer or destruction caller owns world identity. */
	public synchronized void Pop(L1ItemInstance item) {
		_items.Pop(item);
	}

    /**
     * Fee currency for a warehouse withdrawal is changed inside the SAME
     * WarehouseMove scope as the item transfer. No independent CacheD job.
     * Only pre-validated currency stacks are accepted here.
     */
    public synchronized boolean debitWarehouseFee(L1ItemInstance fee, int amount) {
        if (rejectPendingItemTransfer())
            throw new IllegalStateException("Item mutation refused while CacheD transfer pending");
        if (fee == null || amount <= 0 || GetByServerID(fee.getId()) != fee
                || fee.getCount() < amount || !fee.isStackable()) return false;
        if (fee.getCount() == amount) {
            Pop(fee);
            L1World.getInstance().removeObject(fee);
        } else {
            removeItemCountCache(fee.getItemId(), amount);
            fee.setCount(fee.getCount() - amount);
            persistTransferCount(fee);
            updateItem(fee);
        }
        return true;
    }

    /** Only for restoring a fee when WarehouseMove SQL was NOT submitted. */
    public synchronized void restoreWarehouseFee(L1ItemInstance fee, int original) {
        if (fee == null || original <= 0) throw new IllegalArgumentException("Invalid fee rollback");
        if (GetByServerID(fee.getId()) == fee) {
            final int now = fee.getCount();
            if (now > original) throw new IllegalStateException("Fee changed during rollback");
            fee.setCount(original);
            addItemCountCache(fee.getItemId(), original - now);
            persistTransferCount(fee);
            updateItem(fee);
        } else if (GetByServerID(fee.getId()) == null) {
            CItemList.requireDetached(fee);
            final int previousCount = fee.getCount();
            _items.Push(fee);
            try {
                fee.setCount(original);
                insertItem(fee);
            } catch (RuntimeException e) {
                fee.setCount(previousCount);
                _items.Pop(fee);
                throw e;
            }
            L1World.getInstance().storeObject(fee);
        } else {
            throw new IllegalStateException("Fee item identity changed during rollback");
        }
    }

    /** Persist the selected deposits in one CacheD receipt. */
    public synchronized boolean tradeWarehouseDepositBatch(int[] ids, int[] counts,
            Warehouse dest, Runnable onCommitted) {
        return CWareHouseDeposit.RequestBatch(this, ids, counts, dest, onCommitted);
    }

    /** One durable receipt for a selected warehouse withdrawal. */
    public synchronized boolean tradeWarehouseWithdrawalBatch(int[] ids, int[] counts,
            Warehouse source, int feeItemId, int feeEach, java.sql.Timestamp[] deadlines,
            Runnable onCommitted) {
        return CWareHouseWithdraw.RequestBatch(this, ids, counts, source, feeItemId, feeEach,
                deadlines, onCommitted);
    }

	public L1ItemInstance tradeItem(int objectId, int count, Warehouse inventory) {
        return tradeItem(objectId, count, inventory, null);
	}

    public L1ItemInstance tradeItem(int objectId, int count, Warehouse inventory,
            Runnable onCommitted) {
        synchronized (this) {
            final L1ItemInstance item = GetByServerID(objectId);
            return tradeWarehouseItemLocked(item, count, inventory, onCommitted);
        }
    }

	public L1ItemInstance tradeItem(int objectId, int count, CInventory inventory) {
		L1ItemInstance item = GetByServerID(objectId);
		return tradeItem(item, count, inventory);
	}

	public L1ItemInstance tradeItem(L1ItemInstance item, int count, Warehouse inventory) {
        return tradeItem(item, count, inventory, null);
    }

    public L1ItemInstance tradeItem(L1ItemInstance item, int count,
            Warehouse inventory, Runnable onCommitted) {
        synchronized (this) {
            return tradeWarehouseItemLocked(item, count, inventory, onCommitted);
        }
    }

    /** The caller already holds this inventory monitor; warehouse is second. */
    private L1ItemInstance tradeWarehouseItemLocked(L1ItemInstance item, int count,
            Warehouse inventory, Runnable onCommitted) {
        assert Thread.holdsLock(this);
        if (inventory == null || item == null) {
            if (rejectPendingItemTransfer())
                throw new IllegalStateException("Item mutation refused while CacheD transfer pending");
            return null;
        }
        synchronized (inventory) {
            return CWareHouseDeposit.RequestLocked(this, item, count, inventory, onCommitted);
        }
    }

    /*
     * Every inventory-to-inventory transfer locks BOTH owners in one order.
     * Ground is always first: character pickup already owns its tile monitor.
     * Non-ground pairs use a stable identity order. Collision tie lock is
     * entered only on the extremely rare identityHashCode collision.
     *
     * Do not synchronize this entry method: doing so would acquire source
     * before the destination, reversing pickup (ground -> character) and
     * drop (character -> ground) or two opposite character transfers.
     */
    private static final Object ITEM_TRANSFER_TIE_LOCK = new Object();

    public L1ItemInstance tradeItem(L1ItemInstance item, int count, CInventory inventory) {
        if (item == null || inventory == null || inventory == this) return null;
        try {
            return tradeItemOrdered(item, count, inventory);
        } catch (TransferRecoveryFailure failure) {
            // Both inventory monitors and synchronous permits have been released.
            // Do not continue accepting gameplay mutations after failed compensation.
            java.util.logging.Logger.getLogger(CInventory.class.getName())
                    .log(java.util.logging.Level.SEVERE, "Item transfer recovery failed: item="
                            + item.getId() + ", count=" + count, failure);
            closeTransferSession(this, failure);
            closeTransferSession(inventory, failure);
            throw failure;
        }
    }

    private static void closeTransferSession(CInventory inventory, RuntimeException failure) {
        if (!(inventory instanceof CUserInventory pcInventory)) return;
        final var pc = pcInventory.getOwner();
        final CUserSocket session = pc.getNetConnection();
        if (session != null && session.getActiveChar() == pc && session.isConnected()) {
            try { session.close(); }
            catch (RuntimeException closeFailure) { failure.addSuppressed(closeFailure); }
        }
    }

    private static final class TransferRecoveryFailure extends IllegalStateException {
        private static final long serialVersionUID = 1L;
        TransferRecoveryFailure(RuntimeException recovery, RuntimeException transfer) {
            super("Item transfer compensation failed", recovery);
            if (transfer != null && transfer != recovery) addSuppressed(transfer);
        }
    }

    private L1ItemInstance tradeItemOrdered(L1ItemInstance item, int count, CInventory inventory) {

        final CInventory first;
        if (this instanceof L1GroundInventory && !(inventory instanceof L1GroundInventory)) {
            first = this;
        } else if (inventory instanceof L1GroundInventory && !(this instanceof L1GroundInventory)) {
            first = inventory;
        } else {
            final int sourceOrder = System.identityHashCode(this);
            final int targetOrder = System.identityHashCode(inventory);
            if (sourceOrder == targetOrder) {
                synchronized (ITEM_TRANSFER_TIE_LOCK) {
                    synchronized (this) {
                        synchronized (inventory) {
                            return tradeItemLocked(item, count, inventory);
                        }
                    }
                }
            }
            first = sourceOrder < targetOrder ? this : inventory;
        }

        final CInventory second = first == this ? inventory : this;
        synchronized (first) {
            synchronized (second) {
                return tradeItemLocked(item, count, inventory);
            }
        }
    }

    /** Both owner monitors must be held before any ownership/count mutation. */
    private L1ItemInstance tradeItemLocked(L1ItemInstance item, int count, CInventory inventory) {
        if (CacheD.isWarehouseApplying()) return transferWithPermits(item, count, inventory);
        final CUserInventory from = this instanceof CUserInventory pc ? pc : null;
        final CUserInventory to = inventory instanceof CUserInventory pc ? pc : null;
        // Existing CAS gate, non-blocking; no additional worker or lock order.
        if (from != null && !from.tryBeginEquipmentChange()) return null;
        try {
            if (to != null && !to.tryBeginEquipmentChange()) return null;
            try { return transferWithPermits(item, count, inventory); }
            finally { if (to != null) to.endEquipmentChange(); }
        } finally {
            if (from != null) from.endEquipmentChange();
        }
    }

    private L1ItemInstance transferWithPermits(L1ItemInstance item, int count, CInventory inventory) {
        if (rejectPendingItemTransfer())
            throw new IllegalStateException("Item mutation refused while CacheD transfer pending");
		if (item == null || inventory == null || inventory == this) {
			return null;
		}

        // A destination gate rejection must happen before a source delete,
        // split, cache decrement or independent persistence command.
        if (inventory.rejectPendingItemTransfer()) return null;

		// source inventory가 현재 이 exact object를 소유하는지 다시 확인한다.
		final L1ItemInstance owned = GetByServerID(item.getId());
		if (owned != item) {
			return null;
		}

        if (!item.IsTransferableAmount(count)) return null;
        if (this instanceof L1GroundInventory && (item.isGroundPickupPending() || item.isGroundDropUnsettled())) return null;
        // Ordinary Pop on a character issues an entity-destruction command
        // for pet collars and physical letters. A transfer is not destruction.
        // Leave these rows to the dedicated transaction paths.
        if (this instanceof CUserInventory && !CacheD.isWarehouseApplying()) {
            final int template = item.getItemId();
            if (template == 40314 || template == 40316
                    || (template >= 49016 && template <= 49025)) return null;
        }
        // A pickup may retire the tile between lookup and lock acquisition.
        if (this instanceof L1GroundInventory
                && L1World.getInstance().findGroundInventory(getX(), getY(), getMapId()) != this)
            return null;
        if (inventory instanceof L1GroundInventory
                && L1World.getInstance().findGroundInventory(inventory.getX(), inventory.getY(),
                        inventory.getMapId()) != inventory) return null;
        if (inventory instanceof CUserInventory) {
            final boolean chargeMerge = !item.isStackable() && Warehouse.chargeMergeItem(item.getItemId())
                    && inventory.GetFirstItemByClassID(item.getItemId()) != null;
            if ((long) inventory.GetItemAmount(item.getItemId()) + (chargeMerge ? 0 : count) > MAX_AMOUNT)
                return null;
        }

		// Validate against the same merge target used by ordinary transfer.
        final int admission = inventory instanceof CUserInventory pcInventory && !CacheD.isWarehouseApplying()
                ? pcInventory.CheckAddableTransfer(item, count) : inventory.CheckAddable(item, count);
		if (admission != OK) return null;

        if (this instanceof CUserInventory pc && inventory instanceof L1GroundInventory ground
                && !CacheD.isWarehouseApplying())
            return dropWithStagedGround(pc, ground, item, count);

        final int oldX = item.getX(), oldY = item.getY();
        final short oldMap = item.getMapId();
		L1ItemInstance carryItem;
		if (count == item.getCount()) {
            if (this instanceof L1GroundInventory ground) {
                if (!ground.popForTransfer(item)) return null;
            } else Pop(item);
			carryItem = item;
		} else {
            // Use the existing transfer snapshot so split items retain all
            // persistent attributes, including expiry, package and key data.
            carryItem = allocateTransferCarry(item, count, inventory instanceof L1GroundInventory);
            final L1World world = L1World.getInstance();
            try {
                world.storeObject(carryItem);
                changeStackCount(item, -count);
            } catch (RuntimeException failure) {
                try { world.removeObject(carryItem); }
                catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
                throw failure;
            }
		}

        L1ItemInstance stored = null;
        RuntimeException transferFailure = null;
        try {
            stored = inventory.storeTradeItem(carryItem);
        } catch (RuntimeException e) {
            transferFailure = e;
        }
		if (stored != null) {
		    if (!(inventory instanceof L1GroundInventory) && carryItem.GetContributeData() != null) {
                carryItem.SetContributeData(null);
            }
            if (inventory instanceof CUserInventory) {
                stored.setItemOwner(null);
            }
            if (inventory instanceof L1GroundInventory) {
                if (this instanceof CUserInventory) stored.markGroundDropSource(item.getId());
                stored._cha = null;
            }
            if (carryItem == item && this instanceof L1GroundInventory ground)
                ground.finishGroundTransfer(item);
            // Merged carry has no inventory owner; retire its world ID.
            if (stored != carryItem) L1World.getInstance().removeObject(carryItem);
			return stored;
		}

        // Recover the original owner on destination admission failure.
        // If compensation itself fails, preserve both errors and fail closed.
        try {
            if (carryItem == item) {
                item.setX(oldX);
                item.setY(oldY);
                item.setMap(oldMap);
                insertLinkedItem(item);
            } else {
                try {
                    changeStackCount(item, count);
                } finally {
                    L1World.getInstance().removeObject(carryItem);
                }
            }
        } catch (RuntimeException recovery) {
            // A detached full-transfer object must not remain in the world index.
            if (carryItem == item && !_items.Have(item)) {
                try { L1World.getInstance().removeObject(item); }
                catch (RuntimeException cleanup) { recovery.addSuppressed(cleanup); }
            }
            throw new TransferRecoveryFailure(recovery, transferFailure);
        }
        if (transferFailure != null) throw transferFailure;
		return null;
	}

    /** Preserve loot rights only for a split destined for a ground tile. */
    private static L1ItemInstance allocateTransferCarry(L1ItemInstance item, int count, boolean toGround) {
        final L1ItemInstance carry = Warehouse.allocateDetachedTransfer(item, count);
        if (toGround && item.GetContributeData() != null)
            carry.SetContributeData(item.GetContributeData());
        return carry;
    }

    /**
     * Ground has no durable row. Stage its fallible registration BEFORE accepting
     * a character DELETE/UPDATE. A rejected admission then needs only memory
     * cleanup, not a compensating CacheD write. Both owner monitors and the
     * existing character permit remain held throughout.
     */
    private L1ItemInstance dropWithStagedGround(CUserInventory pc, L1GroundInventory ground,
            L1ItemInstance item, int count) {
        assert Thread.holdsLock(this) && Thread.holdsLock(ground);
        final CInventory dest = ground;
        final L1World world = L1World.getInstance();
        final boolean full = count == item.getCount();
        final int oldX = item.getX(), oldY = item.getY();
        final short oldMap = item.getMapId();
        final L1ItemInstance carry = full ? item : allocateTransferCarry(item, count, true);
        final boolean stack = item.isStackable();
        final L1ItemInstance merge = stack || Warehouse.chargeMergeItem(item.getItemId())
                ? dest.findMergeItem(carry) : null;
        // A merge requires no new world registration. Delay mutation of the
        // destination until the source admission succeeds.
        if (merge != null) {
            if (stack) {
                if (!merge.IsAddableAmount(count)) return null;
            } else if (merge.getChargeCount() < 0 || carry.getChargeCount() < 0
                    || (long) merge.getChargeCount() + carry.getChargeCount() > Integer.MAX_VALUE) {
                return null;
            }
        }

        boolean detached = false;
        boolean registered = false;
        try {
            if (full) {
                if (!_items.Pop(item)) return null;
                detached = true; // source count index is not changed yet
            }
            if (merge == null) {
                carry.setX(ground.getX());
                carry.setY(ground.getY());
                carry.setMap(ground.getMapId());
                if (!full) {
                    world.storeObject(carry);
                    registered = true;
                }
                dest.insertLinkedItem(carry);
            }
            // These are the only source persistence admissions. Rejection
            // leaves the persistent source row unchanged.
            if (full) pc.persistOwnedItemDelete(item);
            else changeStackCount(item, -count);
        } catch (RuntimeException failure) {
            RuntimeException recovery = null;
            try {
                if (merge == null && dest._items.Have(carry))
                    ground.popForTransfer(carry);
            } catch (RuntimeException e) { recovery = e; }
            try {
                if (!full && !dest._items.Have(carry)
                        && (registered || world.findObject(carry.getId()) == carry))
                    world.removeObject(carry);
                if (detached && !dest._items.Have(item)) {
                    item.setX(oldX);
                    item.setY(oldY);
                    item.setMap(oldMap);
                    _items.Push(item);
                }
            } catch (RuntimeException e) {
                if (recovery == null) recovery = e;
                else recovery.addSuppressed(e);
            }
            try { ground.finishGroundTransfer(carry); }
            catch (RuntimeException e) {
                if (recovery == null) recovery = e;
                else recovery.addSuppressed(e);
            }
            if (recovery != null) throw new TransferRecoveryFailure(recovery, failure);
            throw failure;
        }

        // The legacy drop is visible only under the tile lock until this
        // fence is set. All pickup paths now wait for its source DB row.
        (merge == null ? carry : merge).markGroundDropSource(item.getId());
        // No compensating persistent write is allowed after admission.
        // Complete the in-memory source/destination state under the same locks.
        try {
            if (full) pc.finishOwnedItemDeletion(item);
            if (merge != null) {
                if (stack) {
                    merge.setCount(merge.getCount() + count);
                    merge.setGiveItem(carry.isGiveItem());
                } else merge.setChargeCount(merge.getChargeCount() + carry.getChargeCount());
                if (full) world.removeObject(item);
                try { ground.updateItem(merge); }
                catch (RuntimeException notice) {
                    java.util.logging.Logger.getLogger(CInventory.class.getName())
                            .log(java.util.logging.Level.WARNING, "Accepted ground merge notice failed", notice);
                }
                merge._cha = null;
                return merge;
            }
            carry._cha = null;
            return carry;
        } catch (RuntimeException failure) {
            throw new TransferRecoveryFailure(failure, null);
        }
    }

	public L1ItemInstance receiveDamage(int objectId) {
		L1ItemInstance item = GetByServerID(objectId);
		return receiveDamage(item);
	}

	public L1ItemInstance receiveDamage(L1ItemInstance item) {
		return receiveDamage(item, 1);
	}

	public L1ItemInstance receiveDamage(L1ItemInstance item, int count) {
		if (item == null) {
			return null;
		}
		int itemType = item.getItem().getType2();
		int currentDurability = item.get_durability();
		if ((currentDurability == 0 && itemType == 0) || currentDurability < 0) {
			item.set_durability(0);
			return null;
		}
		if (itemType == 0) {
			int minDurability = (item.getEnchantLevel() + 5) * -1;
			int durability = currentDurability - count;
			if (durability < minDurability) {
				durability = minDurability;
			}
			if (currentDurability > durability) {
				item.set_durability(durability);
			}
		} else {
			int maxDurability = item.getEnchantLevel() + 5;
			int durability = currentDurability + count;
			if (durability > maxDurability) {
				durability = maxDurability;
			}
			if (currentDurability < durability) {
				item.set_durability(durability);
			}
		}

		updateDurability(item, currentDurability);
		return item;
	}

	public L1ItemInstance recoveryDamage(L1ItemInstance item) {
		if (item == null) {
			return null;
		}
		int itemType = item.getItem().getType2();
		int durability = item.get_durability();

		if ((durability == 0 && itemType != 0) || durability < 0) {
			item.set_durability(0);
			return null;
		}

		if (itemType == 0) {
			item.set_durability(durability + 1);
		} else {
			item.set_durability(durability - 1);
		}

		updateDurability(item, durability);
		return item;
	}

	protected void updateDurability(L1ItemInstance item, int previousDurability) {
		updateItem(item, CUserInventory.COL_DURABILITY);
	}

	public L1ItemInstance GetFirstEquippedItemByClassID(int classId) {
		return _items.GetFirstEquippedItemByClassID(classId);
	}

	public L1ItemInstance GetFirstItemByDescID(int descId) {
		for (L1ItemInstance item : _items) {
			if (item.getItem().getItemDescId() == descId) return item;
		}
		return null;
	}
	protected final L1ItemInstance findMergeItem(L1ItemInstance incoming) {
        final boolean ground = this instanceof L1GroundInventory;
        if (!ground) {
            if (incoming.isStackable() && !CacheD.isWarehouseApplying())
                return _items.GetMergeableStack(incoming, false);
            return GetFirstItemByClassID(incoming.getItemId());
        }
        // Ground membership is already protected by the tile monitor in
        // transfer/creation. Do not take CacheD's lock inside CItemList.
        for (L1ItemInstance candidate : _items) {
            if (candidate == incoming || candidate.isGroundPickupPending()
                    || candidate.getItemId() != incoming.getItemId()
                    || !candidate.SameGroundLootRights(incoming)
                    || candidate.isGroundDropUnsettled()) continue;
            if (incoming.isStackable() && !CacheD.isWarehouseApplying()
                    && !L1ItemInstance.CanMergeStack(candidate, incoming)) continue;
            return candidate;
        }
        return null;
    }

    public L1ItemInstance GetFirstItemByClassID(int id) {
		return _items.GetFirstItemByClassID(id);
	}

	public L1ItemInstance[] GetItemListByClassID(int id) {
		ArrayList<L1ItemInstance> itemList = new ArrayList<L1ItemInstance>();
		for (L1ItemInstance item : _items) {
			if (item == null)
				continue;
			if (item.getItemId() == id) {
				itemList.add(item);
			}
		}
		return itemList.toArray(new L1ItemInstance[] {});
	}
	
	public L1ItemInstance[] GetItemListByDescID(int descid) {
		ArrayList<L1ItemInstance> itemList = new ArrayList<L1ItemInstance>();
		for (L1ItemInstance item : _items) {
			if (item == null)
				continue;
			if (item.getItem().getItemDescId() == descid) {
				itemList.add(item);
			}
		}
		return itemList.toArray(new L1ItemInstance[] {});
	}
	
	public L1ItemInstance[] GetItemListByClassID(int id, int descid) {
		ArrayList<L1ItemInstance> itemList = new ArrayList<L1ItemInstance>();
		for (L1ItemInstance item : _items) {
			if (item == null)
				continue;
			if (item.getItemId() == id && item.getItem().getItemDescId() == descid) {
				itemList.add(item);
			}
		}
		return itemList.toArray(new L1ItemInstance[] {});
	}

	public L1ItemInstance[] GetUnequippedItemListByClassID(int id) {
		ArrayList<L1ItemInstance> itemList = new ArrayList<L1ItemInstance>();
		for (L1ItemInstance item : _items) {
			if (item == null)
				continue;
			if (item.getItemId() == id) {
				if (!item.isEquipped()) {
					itemList.add(item);
				}
			}
		}
		return itemList.toArray(new L1ItemInstance[] {});
	}
	
	public L1ItemInstance GetByServerID(int objectId) {
		return _items.GetByServerID(objectId);
	}

	public boolean HaveItemByClassId(int id) {
		return HaveItemByClassId(id, 1);
	}
	
	

	public boolean HaveItemByClassId(int id, int count) {
		if (count < 0) {
			return false;
		}

		if (count == 0) {
			return true;
		}
		L1Item l1item = CObjectDB.GetItem(id);
		if (l1item == null) return false;
		if (l1item.isStackable()) {
			L1ItemInstance item = GetFirstItemByClassID(id);
			if (item != null && item.getCount() >= count) {
				return true;
			}
		} else {
			return _items.HasInstancesByClassID(id, count);
		}
		return false;
	}

    /** NC CRecipeInfo::IsEnoughItem: count eligible, unequipped material amounts.
     * XML and HTML recipes use quantities; the exact legacy equipment check
     * counts one per nonstackable instance.
     */
    public long GetRecipeMaterialAmount(int classId) {
        return GetRecipeMaterialAmount(classId, 0, false, false);
    }

    public long GetRecipeMaterialAmount(int classId, int enchant) {
        return GetRecipeMaterialAmount(classId, enchant, true, false);
    }

    private long GetRecipeMaterialAmount(int classId, int enchant,
            boolean matchEnchant, boolean countInstances) {
        final List<L1ItemInstance> rows = _items.snapshot();
        long total = 0L;
        for (int i = 0, n = rows.size(); i < n; i++) {
            final L1ItemInstance item = rows.get(i);
            if (item == null || item.isEquipped() || item.getItemId() != classId
                    || (matchEnchant && item.getEnchantLevel() != enchant)) continue;
            final int count = item.getCount();
            if (count > 0) total += countInstances && !item.isStackable() ? 1 : count;
        }
        return total;
    }

    public boolean HaveUnequippedItemByClassIdEnchanted(int id, int enchant, int count) {
        return count > 0 && GetRecipeMaterialAmount(id, enchant, true, true) >= count;
    }

	public boolean HaveUnequippedItemByClassId(int id, int count) {
		if (count < 0) return false;
		return count == 0 || count <= GetUnequippedItemAmount(id);
	}

	public boolean HaveItemByClassId(int[] ids) {
		if (ids == null) return false;
		for (int id : ids) {
			if (!HaveItemByClassId(id, 1)) return false;
		}
		return true;
	}

	public boolean HaveItemByClassId(int[] ids, int[] counts) {
		if (ids == null || counts == null || ids.length != counts.length) return false;
		for (int i = 0; i < ids.length; i++) {
			if (!HaveItemByClassId(ids[i], counts[i])) {
				return false;
			}
		}
		return true;
	}

	public int GetUnequippedItemAmount(int id) {
		final L1Item template = CObjectDB.GetItem(id);
		if (template == null) return 0;
		if (template.isStackable()) {
			final L1ItemInstance item = GetFirstItemByClassID(id);
			return item == null ? 0 : item.getCount();
		}
		return _items.CountUnequippedInstancesByClassID(id);
	}

	public void shuffle() {
		_items.shuffle();
	}

	public void clearItems() {
		L1ItemInstance item = null;
		for (Object itemObject : _items) {
			item = (L1ItemInstance) itemObject;
			if (item == null || item.getItemId() == InnHelper.INN_KEYID)
				continue;
			L1World.getInstance().removeObject(item);
		}
		_items.clear();
	}

	public void loadItems() {
	}

	public void insertItem(L1ItemInstance item) {
	}

	public void updateItem(L1ItemInstance item) {
	}

	public void updateItem(L1ItemInstance item, int colmn) {
	}
	
    /** Remove matching inventory items, then unregister their world identities.
     * Unlike Pop, this is final destruction rather than transfer.
     */
    public synchronized void DeleteAllItemsByClassID(int id) {
        for (L1ItemInstance item : _items) {
            if (item != null && item.getItemId() == id && _items.Have(item)) {
                Pop(item);
                L1World.getInstance().removeObject(item);
            }
        }
    }

    public synchronized void DeleteAllItemsByClassID(int[] ids) {
        if (ids == null) return;
        for (L1ItemInstance item : _items) {
            if (item == null || !_items.Have(item)) continue;
            for (int id : ids) {
                if (item.getItemId() == id) {
                    Pop(item);
                    L1World.getInstance().removeObject(item);
                    break;
                }
            }
        }
    }

	private static int _instanceType = -1;
	@Override
	public int getL1Type(){
		return _instanceType == -1 ? _instanceType = super.getL1Type() | L1Type.L1TYPE_INVENTORY : _instanceType;		
	}

	public int CheckAddable(int itemId, int count){
		L1Item item = CObjectDB.GetItem(itemId);		
		return CheckAddable(item, count);
	}
	
	public int CheckAddable(L1Item item, int count) {
		if (item == null) {
			return -1;
		}

		if (count < 0 || count > MAX_AMOUNT) {
			return AMOUNT_OVER;
		}

		if (getSize() > 180 || (getSize() == 180 && (!item.isStackable() || !HaveItemByClassId(item.getItemId()))))
			return SIZE_OVER;

		final long addedWeight = (long) item.getWeight() * count / 1000L;
		final long weight = GetCarryingWeight() + addedWeight + 1L;
		if (weight < 0 || weight > Integer.MAX_VALUE || addedWeight < 0)
			return WEIGHT_OVER;

		L1ItemInstance itemExist = GetFirstItemByClassID(item.getItemId());
		if (itemExist != null && !itemExist.IsAddableAmount(count))
			return AMOUNT_OVER;
		
		return OK;
	}
	
	
	public int fillCraftInputItem(LinkedList<CraftInputItemSlotInfo> slotInfo){
		int fillCount = 0;
		int sSize = slotInfo.size();
		for(L1ItemInstance item : getItems()){
			if(fillCount >= sSize)
				break;

			if(item == null)
				continue;

			for(CraftInputItemSlotInfo sInfo : slotInfo){
				if(sInfo.hasRemoved())
					continue;

				if(sInfo.isInput(item)){
					sInfo.addTemporaryRemovedItems(item, item.isStackable() ? sInfo.get_count() : 1);
					if(sInfo.hasRemoved()){	
						++fillCount;
					}
					break;
				}
			}
		}
		return fillCount;
	}
	
	public int fillAlchemyInputItem(LinkedList<CS_ALCHEMY_MAKE_REQ.Input> inputs){
		int fillCount = 0;
		int size = inputs.size();
		for(L1ItemInstance item : getItems()){
			if(fillCount >= size)
				break;
			
			if(item == null)
				continue;
			
			for(CS_ALCHEMY_MAKE_REQ.Input input : inputs){
				if(input.hasRemoved())
					continue;
				
				if(input.isInput(item)){
					input.setTemporaryRemovedItems(item);
					++fillCount;
					break;
				}
			}
		}
		
		return fillCount;
	}
}

