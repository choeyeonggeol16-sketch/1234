
package l1j.server.server.model;

import l1j.server.server.datatables.CObjectDB;
import java.sql.Timestamp;
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
		long weight = 0;
		for (L1ItemInstance item : _items) weight += item.getWeight();
		return (int) (weight / Config.RATE_WEIGHT_LIMIT);
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
		if (count <= 0) return null;
		final L1Item template = CObjectDB.GetItem(id);
		if (template == null) return null;
		if (template.isStackable()) {
			final L1ItemInstance item = new L1ItemInstance(template, count);
			if (!withEnchant) item.setIdentified(identified);
			if (findMergeItem(item) == null) {
				item.setId(IdFactory.getInstance().nextId());
				return registerCreatedItem(item);
			}
			return CreateOrAddItem(item);
		}
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
        if (rejectPendingItemTransfer())
            throw new IllegalStateException("Item mutation refused while CacheD transfer pending");
		if (item == null || item.getCount() <= 0) {
			return null;
		}
		int itemId = item.getItem().getItemId();
		if (item.isStackable()) {
			L1ItemInstance findItem = findMergeItem(item);
			
			if (findItem != null) {
                changeStackCount(findItem, item.getCount());
                return findItem;
			}
		}
		if (!treasure && item.get_item_level() > 0) {
			item.set_item_level(item.get_item_level());
		}

		
		item.setX(getX());
		item.setY(getY());
		item.setMap(getMapId());
		int chargeCount = item.getItem().getMaxChargeCount();
		if (!treasure) switch (itemId) {
		case 40006:
		case 40007:
		case 40008:
		case 140006:
		case 140008:
		case 41401:
		case 810006:
		case 810007:
			L1ItemInstance findItem = findMergeItem(item);
			if (findItem != null) {
				chargeCount -= java.util.concurrent.ThreadLocalRandom.current().nextInt(5);
                addStackCharge(findItem, chargeCount);
                return findItem;
			}
			break;
		}

		if (itemId == 20383) {
			chargeCount = 50;
		}

		item.setChargeCount(chargeCount);

		// 시간제 아이템
		switch (itemId) {
		// 호랑이/진돗개
		case 3000048: // 꼬마 요정의 마음
			SetDeleteTime(item, 1439); // 24시간 -1분
			break;
		case L1ItemId.KILLTON_CONTRACT:
		case L1ItemId.MERIN_CONTRACT:
			SetDeleteTime(item, 60);
			break;
		default:
			if (!treasure) SetOrdinaryItemDeleteTime(item, itemId);
			break;
		}

		if (item.getItem().getType2() == 0 && item.getItem().getType() == 2) { // light
			item.setRemainingTime(item.getItem().getLightFuel());
		} else {
			item.setRemainingTime(item.getItem().getMaxUseTime());
		}
		
		if (!treasure) {
			item.setBless(explicitBless ? bless : item.getItem().getBless());
			item.setIdentified(true);
			item.set_bless_level(item.get_bless_level());
			item.set_item_level(item.get_item_level());
		} else {
			item.setBless(item.getBless());
		}
		insertLinkedItem(item);
		return item;
	}

	private void SetOrdinaryItemDeleteTime(L1ItemInstance item, int itemId) {
		switch (itemId) {
		case 80500:
			SetDeleteTime(item, 120);
			break;
		case 555000:
		case 555001: // 마법인형: 레데그
		case 555002: // 마법인형: 엘레그
		case 555003: // 마법인형: 그레그
			SetDeleteTime(item, Config.영웅유물); // 5시간
			break;
		// case L1ItemId.MERIN_CONTRACT:
		// SetDeleteTime(item, 60);
		// break;
		case 100036:
		case 500210: // 테베,쿠쿨 제단 열쇠
		case L1ItemId.DRAGON_KEY: // 드래곤키
		case 490012:
		case 490013:
		case 490014:
		case 40312:
			SetDeleteTime(item, 180); // 3시간
			break;
		case 30022: // 마법인형: 블레그
		case 30023: // 마법인형: 레데그
		case 30024: // 마법인형: 엘레그
		case 30025: // 마법인형: 그레그
			SetDeleteTime(item, 300); // 5시간
			break;
		case 22328: // 베테르랑 방어구
		case 22329:
		case 22330:
		case 22331:
		case 22332:
		case 22333:
		case 22334:
		case 22335:
		case 1126: // 베테르랑 무기
		case 1127:
		case 1128:
		case 1129:
		case 1130:
		case 1131:
		case 1132:
		case 1133:
			SetDeleteTime(item, 4320); // 3일
			break;
		case 20282: // 수련자의 방어구
		case 7: // 수련자의 무기
		case 105:
		case 120:
		case 156:
		case 174:
		case 224:
		case 203012:
			SetDeleteTime(item, 4320); // 3일
			break;
		case 3000213:// 자동사냥 3일
			SetDeleteTime(item, 4320);// 자동사냥 3일
			break;
		case 45453: // 마법인형: 그레그
			SetDeleteTime(item, 360); // 5시간
			break;
		case 45451: // 마법인형: 그레그
			SetDeleteTime(item, 1440); // 5시간
			break;
		case 45452: // 마법인형: 그레그
			SetDeleteTime(item, 4320); // 5시간
			break;
		case 41922:
		case 41923:
		case 41924:
		case 41925:
		case 3000209: // 자동사냥 1일
		case 775:
		case 774:
		case 773:
		case 772:
			SetDeleteTime(item, 1440); // 1일
			break;
		case 3000214:// 자동사냥 7일
			SetDeleteTime(item, 8640); // 7일
			break;
		case 210095:
		case 2100950:
		case 2100951:
		case 2100952:
		case 2100953:// 쫄사냥
			SetDeleteTime(item, 180); // 3일
			break;
		/** 신규 초보자 무기 **/
		case 7000137:
		case 7000138:
		case 7000139:
		case 7000140:
		case 7000141:
		case 7000142:
		case 7000143:
		case 7000144:
		case 505001:
		case 505002:
		case 505003:
		case 505004:
		case 505005:
		case 505006:
		case 505007:
		case 505008:
			SetDeleteTime1(item, 1440); // 3일
			break;
		default:
			break;
		}
	}

	private void SetDeleteTime(L1ItemInstance item, int minute) {
		Timestamp deleteTime = null;
		deleteTime = new Timestamp(System.currentTimeMillis() + (60000 * minute));
		item.setEndTime(deleteTime);
	}

	private void SetDeleteTime1(L1ItemInstance item, int minute) {
		Timestamp deleteTime = null;
		deleteTime = new Timestamp(System.currentTimeMillis() + (20000 * minute));
		item.setEndTime(deleteTime);
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

	public synchronized L1ItemInstance storeTradeItem(L1ItemInstance item) {
        if (rejectPendingItemTransfer())
            throw new IllegalStateException("Item mutation refused while CacheD transfer pending");
		if (item == null || item.getCount() <= 0) return null;
        CItemList.requireDetached(item);
		if (item.isStackable()) {	
			L1ItemInstance findItem = findMergeItem(item);
			
			int itemId = item.getItem().getItemId();
			if (findItem != null) {
				final int before = findItem.getCount();
				if (!findItem.AddAmount(item.getCount())) return null;
				boolean submitted = false;
				try {
					persistTransferCount(findItem);
					submitted = true;
					updateItem(findItem);
				} catch (RuntimeException e) {
					findItem.setCount(before);
					if (submitted) {
						try {
							persistTransferCount(findItem);
						} catch (RuntimeException recovery) {
							e.addSuppressed(recovery);
						}
					}
					throw e;
				}
				addItemCountCache(itemId, item.getCount());
				findItem.setGiveItem(item.isGiveItem());
				return findItem;
			}
		}
		L1ItemInstance findItem = findMergeItem(item);


		switch (item.getItem().getItemId()) {
		case 40006:
		case 40007:
		case 40008:
		case 140006:
		case 140008:
		case 41401:
		case 810006:
		case 810007:

			if (findItem != null) {
				int chargeCount = item.getChargeCount();
				final int before = findItem.getChargeCount();
				if (chargeCount < 0 || before < 0 || (long) before + chargeCount > Integer.MAX_VALUE) return null;
				findItem.setChargeCount(before + chargeCount);
				boolean submitted = false;
				try {
					persistTransferCharge(findItem);
					submitted = true;
					updateItem(findItem);
				} catch (RuntimeException e) {
					findItem.setChargeCount(before);
					if (submitted) {
						try {
							persistTransferCharge(findItem);
						} catch (RuntimeException recovery) {
							e.addSuppressed(recovery);
						}
					}
					throw e;
				}
				return findItem;
			}
			break;
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

    /** Persist selected private, clan or elf warehouse deposits in one CacheD receipt.
     * Memory is published only after the complete DB transaction commits.
     */
    public synchronized boolean tradeWarehouseDepositBatch(int[] ids, int[] counts,
            Warehouse dest, Runnable onCommitted) {
        if (!(this instanceof CUserInventory) || ids == null || counts == null
                || ids.length <= 1 || ids.length != counts.length || ids.length > 100
                || dest == null || !(dest instanceof l1j.server.server.model.Warehouse.PrivateWarehouse
                    || dest instanceof l1j.server.server.model.Warehouse.ClanWarehouse
                    || dest instanceof l1j.server.server.model.Warehouse.ElfWarehouse)
                || rejectPendingItemTransfer()) return false;
        final l1j.server.server.model.Instance.L1PcInstance pc =
                ((CUserInventory) this).getOwner();
        final CUserSocket session = pc.getNetConnection();
        if (session == null || !session.isConnected() || !pc.tryBeginItemTransfer()) return false;
        boolean submitted = false;
        try {
            final int size = ids.length;
            final L1ItemInstance[] sources = new L1ItemInstance[size];
            final int[] sourceCounts = new int[size];
            final int[] group = new int[size];
            final long[] groupTotals = new long[size];
            final L1ItemInstance[] destinations = new L1ItemInstance[size];
            final int[] destinationCounts = new int[size];
            final L1ItemInstance[] carry = new L1ItemInstance[size];
            final int stamp = pc.getSessionStamp();
            synchronized (dest) {
                int newSlots = 0;
                for (int i = 0; i < size; i++) {
                    final L1ItemInstance item = GetByServerID(ids[i]);
                    final int count = counts[i];
                    if (item == null || item.getId() != ids[i] || item.getCount() <= 0
                            || count <= 0 || count > item.getCount() || item.isEquipped()
                            || (!item.isStackable() && count != 1)) return false;
                    for (int j = 0; j < i; j++) if (ids[j] == ids[i]) return false;
                    sources[i] = item;
                    sourceCounts[i] = item.getCount();
                    int g = i;
                    if (item.isStackable()) {
                        for (int j = 0; j < i; j++) {
                            if (sources[j].isStackable() && sources[j].getItemId() == item.getItemId()) {
                                g = group[j];
                                break;
                            }
                        }
                    }
                    group[i] = g;
                    if (g == i) {
                        destinations[i] = item.isStackable() ? dest.GetFirstItemByClassID(item.getItemId()) : null;
                        destinationCounts[i] = destinations[i] == null ? 0 : destinations[i].getCount();
                        if (destinations[i] == null) newSlots++;
                    }
                    groupTotals[g] += count;
                    if (groupTotals[g] + destinationCounts[g] > MAX_AMOUNT) return false;
                }
                final int maxSlots = dest instanceof l1j.server.server.model.Warehouse.ClanWarehouse
                        ? Config.MAX_CLAN_WAREHOUSE_ITEM : Config.MAX_PERSONAL_WAREHOUSE_ITEM;
                if (dest.getSize() + newSlots > maxSlots) return false;
                final CacheD.WarehouseMove reserved;
                try { reserved = CacheD.beginWarehouseMove(pc.getId(), dest.transferOrderKey()); }
                catch (java.util.concurrent.RejectedExecutionException busy) { return false; }
                try (CacheD.WarehouseMove move = reserved) {
                    move.onFailure(() -> CIOThreadCallback.getInstance().completeSession(session, session::kick));
                    for (int i = 0; i < size; i++) {
                        final L1ItemInstance item = sources[i];
                        final int count = counts[i];
                        move.expectOriginal(item);
                        if (count == sourceCounts[i]) {
                            carry[i] = item;
                            move.prepareCharacterDelete(item);
                        } else {
                            carry[i] = Warehouse.allocateDetachedTransfer(item, count);
                            move.prepareCarry(item.getId(), carry[i]);
                            move.prepareCharacterWrite(Warehouse.transferCopy(item, item.getId(),
                                    sourceCounts[i] - count, item.getChargeCount()), false);
                        }
                    }
                    for (int i = 0; i < size; i++) {
                        if (group[i] != i) continue;
                        final int newCount = (int) (groupTotals[i] + destinationCounts[i]);
                        if (destinations[i] != null) {
                            move.expectOriginal(destinations[i]);
                            dest.updateItem(Warehouse.transferCopy(destinations[i],
                                    destinations[i].getId(), newCount,
                                    destinations[i].getChargeCount()));
                        } else {
                            dest.insertItem(Warehouse.transferCopy(carry[i], carry[i].getId(),
                                    newCount, carry[i].getChargeCount()));
                        }
                    }
                    move.commit(() -> CIOThreadCallback.getInstance().completeWarehouseMove(pc,
                            stamp, () -> {
                        synchronized (session) {
                            synchronized (CInventory.this) {
                                synchronized (dest) {
                                    if (!session.isConnected() || pc.getSessionStamp() != stamp
                                            || pc.getNetConnection() != session)
                                        throw new IllegalStateException("Stale warehouse batch session");
                                    for (int i = 0; i < size; i++) {
                                        if (GetByServerID(ids[i]) != sources[i]
                                                || sources[i].getCount() != sourceCounts[i])
                                            throw new IllegalStateException("Warehouse batch source changed after COMMIT");
                                        if (group[i] == i && destinations[i] != null
                                                && (dest.GetByServerID(destinations[i].getId()) != destinations[i]
                                                || destinations[i].getCount() != destinationCounts[i]))
                                            throw new IllegalStateException("Warehouse batch destination changed after COMMIT");
                                    }
                                    CacheD.applyCommittedWarehouseMove(move, () -> {
                                        for (int i = 0; i < size; i++) {
                                            L1ItemInstance result = tradeWarehouseDepositLocked(sources[i], counts[i], dest, null);
                                            if (result == null)
                                                throw new IllegalStateException("Warehouse batch memory replay failed item=" + ids[i]);
                                            if (move.replayCarry(ids[i]) == result)
                                                L1World.getInstance().storeObject(result);
                                        }
                                    });
                                    for (int i = 0; i < size; i++)
                                        Warehouse.recordCommittedMove(pc, dest, true, sources[i], counts[i]);
                                    pc.endItemTransfer();
                                    if (onCommitted != null) {
                                        try { onCommitted.run(); }
                                        catch (RuntimeException auditFailure) {
                                            java.util.logging.Logger.getLogger(CInventory.class.getName())
                                                    .log(java.util.logging.Level.WARNING,
                                                         "Committed warehouse deposit audit dispatch failed", auditFailure);
                                        }
                                    }
                                    try { pc.getLight().turnOnOffLight(); }
                                    catch (RuntimeException lightFailure) {
                                        java.util.logging.Logger.getLogger(CInventory.class.getName())
                                                .log(java.util.logging.Level.WARNING,
                                                     "Warehouse batch light refresh failed", lightFailure);
                                    }
                                }
                            }
                        }
                    }, move::finishOwnerApply));
                    submitted = true;
                    return true;
                }
            }
        } finally {
            if (!submitted) pc.endItemTransfer();
        }
    }

    /** One durable receipt for a selected warehouse withdrawal. */
    public synchronized boolean tradeWarehouseWithdrawalBatch(int[] ids, int[] counts,
            Warehouse source, int feeItemId, int feeEach, java.sql.Timestamp[] deadlines,
            Runnable onCommitted) {
        if (!(this instanceof CUserInventory) || ids == null || counts == null
                || ids.length <= 1 || ids.length > 100 || ids.length != counts.length
                || source == null || (deadlines != null && (deadlines.length != ids.length
                        || !(source instanceof l1j.server.server.model.Warehouse.SupplementaryService)))
                || (feeItemId != 0 && feeItemId != L1ItemId.ADENA && feeItemId != 40494)
                || (feeItemId == 0) != (feeEach == 0) || feeEach < 0
                || rejectPendingItemTransfer()) return false;
        final l1j.server.server.model.Instance.L1PcInstance pc =
                ((CUserInventory) this).getOwner();
        final CUserSocket session = pc.getNetConnection();
        if (session == null || !session.isConnected() || !pc.tryBeginItemTransfer()) return false;
        boolean submitted = false;
        try {
            final int size = ids.length, stamp = pc.getSessionStamp();
            final long totalFee = (long) feeEach * size;
            final L1ItemInstance[] sources = new L1ItemInstance[size];
            final int[] sourceCounts = new int[size];
            final int[] group = new int[size];
            final long[] groupTotals = new long[size];
            final L1ItemInstance[] destinations = new L1ItemInstance[size];
            final int[] destCounts = new int[size];
            long[] chargeTotals = null;
            int[] destCharges = null;
            final L1ItemInstance[] carries = new L1ItemInstance[size];
            synchronized (source) {
                final L1ItemInstance fee = feeEach == 0 ? null : GetFirstItemByClassID(feeItemId);
                if (feeEach != 0 && (fee == null || !fee.isStackable() || fee.getCount() < totalFee))
                    return false;
                final int feeCount = fee == null ? 0 : fee.getCount();
                int slots = getSize();
                long additionalWeight = 0L;
                int feeGroup = -1;
                for (int i = 0; i < size; i++) {
                    final L1ItemInstance item = source.GetByServerID(ids[i]);
                    final int count = counts[i];
                    if (item == null || item.getId() != ids[i] || item.getCount() <= 0
                            || count <= 0 || count > item.getCount() || item.isEquipped()
                            || (!item.isStackable() && count != 1)) return false;
                    for (int j = 0; j < i; j++) if (ids[j] == ids[i]) return false;
                    sources[i] = item;
                    sourceCounts[i] = item.getCount();
                    long weightProduct = (long) item.getItem().getWeight() * count;
                    if (weightProduct < 0) return false;
                    additionalWeight += weightProduct / 1000L + 1L;
                    if (additionalWeight > Integer.MAX_VALUE) return false;

                    final boolean stackable = item.isStackable();
                    final boolean mergeCharge = !stackable && Warehouse.chargeMergeItem(item.getItemId());
                    int g = i;
                    if (stackable || mergeCharge) {
                        for (int j = 0; j < i; j++) {
                            if (sources[j].getItemId() == item.getItemId()) {
                                g = group[j];
                                break;
                            }
                        }
                    }
                    group[i] = g;
                    if (!stackable && slots >= CUserInventory.MAX_SIZE) return false;
                    if (g == i) {
                        destinations[i] = stackable || mergeCharge ? GetFirstItemByClassID(item.getItemId()) : null;
                        destCounts[i] = destinations[i] == null ? 0 : destinations[i].getCount();
                        if (destinations[i] == null && ++slots > CUserInventory.MAX_SIZE) return false;
                        if (fee != null && destinations[i] == fee) feeGroup = i;
                    }
                    if (deadlines != null && deadlines[i] != null
                            && (g != i || destinations[i] != null)) return false;
                    if (mergeCharge) {
                        if (chargeTotals == null) {
                            chargeTotals = new long[size];
                            destCharges = new int[size];
                        }
                        if (g == i && destinations[i] != null) destCharges[i] = destinations[i].getChargeCount();
                        final int charge = item.getChargeCount();
                        if (charge < 0 || destCharges[g] < 0
                                || (chargeTotals[g] += charge) + destCharges[g] > Integer.MAX_VALUE) return false;
                        if (g == i && destinations[i] == null) groupTotals[g] = 1;
                    } else {
                        groupTotals[g] += count;
                        if (groupTotals[g] + destCounts[g] > 1_800_000_000L) return false;
                    }
                }
                // The inventory's weight is rate-scaled. Conservatively
                // account for the configured rate and per-stack rounding so
                // committed replay cannot hit a later weight rejection.
                final double weightRate = Config.RATE_WEIGHT_LIMIT;
                if (!Double.isFinite(weightRate) || weightRate <= 0.0
                        || pc.getMaxWeight() <= 0) return false;
                final double projectedWeight = GetCarryingWeight()
                        + (additionalWeight + size) / Math.min(1.0, weightRate) + size;
                if (projectedWeight < 0.0
                        || projectedWeight >= (double) pc.getMaxWeight() * 2.4
                        || projectedWeight >= Integer.MAX_VALUE / 100.0)
                    return false;
                // When taking out the fee currency itself, the final count of
                // that destination stack is (old + received - all fees).
                if (feeGroup >= 0 && (long) feeCount + groupTotals[feeGroup] - totalFee < 0L)
                    return false;
                final CacheD.WarehouseMove reserved;
                try { reserved = CacheD.beginWarehouseMove(pc.getId(), source.transferOrderKey()); }
                catch (java.util.concurrent.RejectedExecutionException busy) { return false; }
                try (CacheD.WarehouseMove move = reserved) {
                    source.requireWithdrawalTable(move);
                    final int[] expectedCharges = destCharges;
                    move.onFailure(() -> CIOThreadCallback.getInstance().completeSession(session, session::kick));
                    for (int i = 0; i < size; i++) {
                        final L1ItemInstance item = sources[i];
                        final int count = counts[i];
                        move.expectOriginal(item);
                        if (count == sourceCounts[i]) {
                            carries[i] = item;
                            source.prepareTransferDelete(item);
                        } else {
                            carries[i] = l1j.server.server.model.Warehouse.Warehouse
                                    .allocateDetachedTransfer(item, count);
                            move.prepareCarry(item.getId(), carries[i]);
                            source.updateItem(l1j.server.server.model.Warehouse.Warehouse.transferCopy(
                                    item, item.getId(), sourceCounts[i] - count, item.getChargeCount()));
                        }
                    }
                    for (int i = 0; i < size; i++) {
                        if (group[i] != i) continue;
                        final L1ItemInstance dest = destinations[i];
                        int finalCount = (int) (groupTotals[i] + destCounts[i]);
                        if (dest != null && dest == fee) finalCount -= (int) totalFee;
                        final boolean mergeCharge = !sources[i].isStackable()
                                && Warehouse.chargeMergeItem(sources[i].getItemId());
                        final int finalCharge = mergeCharge ? (int) (chargeTotals[i] + destCharges[i])
                                : dest == null ? carries[i].getChargeCount() : dest.getChargeCount();
                        if (dest != null) {
                            move.expectOriginal(dest);
                            if (finalCount == 0) move.prepareCharacterDelete(dest);
                            else move.prepareCharacterWrite(Warehouse.transferCopy(
                                    dest, dest.getId(), finalCount, finalCharge), false);
                        } else {
                            L1ItemInstance row = Warehouse.characterTransferCopy(
                                    carries[i], carries[i].getId(), finalCount, finalCharge);
                            if (deadlines != null && deadlines[i] != null) row.setEndTime(deadlines[i]);
                            move.prepareCharacterWrite(row, true);
                        }
                    }
                    if (fee != null && feeGroup < 0) {
                        move.expectOriginal(fee);
                        if (feeCount == totalFee) move.prepareCharacterDelete(fee);
                        else move.prepareCharacterWrite(l1j.server.server.model.Warehouse.Warehouse
                                .transferCopy(fee, fee.getId(), (int) (feeCount - totalFee),
                                        fee.getChargeCount()), false);
                    }
                    move.commit(() -> CIOThreadCallback.getInstance().completeWarehouseMove(pc,
                            stamp, () -> {
                        synchronized (session) {
                            synchronized (CInventory.this) {
                                synchronized (source) {
                                    if (!session.isConnected() || pc.getSessionStamp() != stamp
                                            || pc.getNetConnection() != session)
                                        throw new IllegalStateException("Stale warehouse withdrawal batch session");
                                    if (fee != null && (GetByServerID(fee.getId()) != fee || fee.getCount() != feeCount))
                                        throw new IllegalStateException("Withdrawal batch fee preimage changed");
                                    for (int i = 0; i < size; i++) {
                                        if (source.GetByServerID(ids[i]) != sources[i]
                                                || sources[i].getCount() != sourceCounts[i])
                                            throw new IllegalStateException("Withdrawal source changed after COMMIT");
                                        if (group[i] == i && destinations[i] != null
                                                && (GetByServerID(destinations[i].getId()) != destinations[i]
                                                        || destinations[i].getCount() != destCounts[i]
                                                        || (expectedCharges != null
                                                            && !sources[i].isStackable()
                                                            && Warehouse.chargeMergeItem(sources[i].getItemId())
                                                            && destinations[i].getChargeCount() != expectedCharges[i])))
                                            throw new IllegalStateException("Withdrawal destination changed after COMMIT");
                                    }
                                    CacheD.applyCommittedWarehouseMove(move, () -> {
                                        for (int i = 0; i < size; i++) {
                                            final L1ItemInstance result = deadlines != null && deadlines[i] != null
                                                    ? source.tradeSupplementaryItem(sources[i], counts[i],
                                                            CInventory.this, deadlines[i], null)
                                                    : source.tradeItem(sources[i], counts[i],
                                                            CInventory.this, feeItemId, feeEach);
                                            if (result == null)
                                                throw new IllegalStateException("Withdrawal replay failed id=" + ids[i]);
                                            if (move.replayCarry(ids[i]) == result)
                                                L1World.getInstance().storeObject(result);
                                        }
                                    });
                                    for (int i = 0; i < size; i++)
                                        l1j.server.server.model.Warehouse.Warehouse.recordCommittedMove(
                                                pc, source, false, sources[i], counts[i]);
                                    pc.endItemTransfer();
                                    if (onCommitted != null) {
                                        try { onCommitted.run(); }
                                        catch (RuntimeException auditFailure) {
                                            java.util.logging.Logger.getLogger(CInventory.class.getName())
                                                    .log(java.util.logging.Level.WARNING,
                                                            "Committed warehouse withdrawal audit failed", auditFailure);
                                        }
                                    }
                                }
                            }
                        }
                    }, move::finishOwnerApply));
                    submitted = true;
                    return true;
                }
            }
        } finally {
            if (!submitted) pc.endItemTransfer();
        }
    }

    private L1ItemInstance prepareWarehouseDeposit(L1ItemInstance item, int count, Warehouse dest,
            Runnable onCommitted) {
        if (!(this instanceof CUserInventory)) return null;
        final CUserInventory pcInventory = (CUserInventory) this;
        final l1j.server.server.model.Instance.L1PcInstance pc = pcInventory.getOwner();
        final CUserSocket session = pc.getNetConnection();
        if (session == null || !session.isConnected()) return null;
        final int sessionStamp = pc.getSessionStamp();
        if (!pc.tryBeginItemTransfer()) return null;
        boolean submitted = false;
        try {
        final int sourceCount = item.getCount();
        final L1ItemInstance destStack = item.isStackable() ? dest.GetFirstItemByClassID(item.getItemId()) : null;
        final int destCount = destStack == null ? 0 : destStack.getCount();
        if (destStack != null && !destStack.IsAddableAmount(count)) return null;

        final CacheD.WarehouseMove reserved;
        try { reserved = CacheD.beginWarehouseMove(pc.getId(), dest.transferOrderKey()); }
        catch (java.util.concurrent.RejectedExecutionException busy) { return null; }
        try (CacheD.WarehouseMove move = reserved) {
            move.onFailure(() -> CIOThreadCallback.getInstance().completeSession(session, session::kick));
            move.expectOriginal(item);
            if (destStack != null) move.expectOriginal(destStack);
            final L1ItemInstance carry;
            if (sourceCount == count) {
                carry = item;
                move.prepareCharacterDelete(item);
            } else {
                carry = Warehouse.allocateDetachedTransfer(item, count);
                move.prepareCarry(carry);
                move.prepareCharacterWrite(Warehouse.transferCopy(item, item.getId(),
                        sourceCount - count, item.getChargeCount()), false);
            }
            if (destStack != null) {
                dest.updateItem(Warehouse.transferCopy(destStack, destStack.getId(),
                        destCount + count, destStack.getChargeCount()));
            } else {
                dest.insertItem(carry);
            }
            move.commit(() -> CIOThreadCallback.getInstance().completeWarehouseMove(pc,
                    sessionStamp, () -> {
                // RECV packet handling uses this same session monitor.
                synchronized (session) {
                    synchronized (CInventory.this) {
                        synchronized (dest) {
                            if (!session.isConnected() || pc.getSessionStamp() != sessionStamp
                                    || pc.getNetConnection() != session || GetByServerID(item.getId()) != item
                                    || item.getCount() != sourceCount
                                    || (destStack != null && (dest.GetByServerID(destStack.getId()) != destStack
                                            || destStack.getCount() != destCount)))
                                throw new IllegalStateException("Warehouse deposit preimage changed after DB COMMIT");
                            CacheD.applyCommittedWarehouseMove(move, () -> {
                                L1ItemInstance applied = tradeWarehouseDepositLocked(item, count, dest, null);
                                if (applied == null)
                                    throw new IllegalStateException("Warehouse deposit replay failed");
                                if (move.replayCarry() == applied)
                                    L1World.getInstance().storeObject(applied);
                            });
                        }
                    }
                    Warehouse.recordCommittedMove(pc, dest, true, item, count);
                    pc.endItemTransfer();
                    try { pc.getLight().turnOnOffLight(); }
                    catch (RuntimeException lightFailure) {
                        java.util.logging.Logger.getLogger(CInventory.class.getName())
                                .log(java.util.logging.Level.WARNING, "Warehouse light refresh failed", lightFailure);
                    }
                    if (onCommitted != null) onCommitted.run();
                }
            }, move::finishOwnerApply));
            submitted = true;
            return item; // accepted; game memory changes ONLY in post-COMMIT callback
        }
        } finally {
            if (!submitted) pc.endItemTransfer();
        }
    }

	public L1ItemInstance tradeItem(int objectId, int count, Warehouse inventory) {
        return tradeItem(objectId, count, inventory, null);
	}

    public L1ItemInstance tradeItem(int objectId, int count, Warehouse inventory,
            Runnable onCommitted) {
        synchronized (this) {
            final L1ItemInstance item = GetByServerID(objectId);
            if (inventory == null || item == null) {
                if (rejectPendingItemTransfer())
                    throw new IllegalStateException("Item mutation refused while CacheD transfer pending");
                return null;
            }
            synchronized (inventory) {
                return tradeWarehouseDepositLocked(item, count, inventory, onCommitted);
            }
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
            if (inventory == null || item == null) {
                if (rejectPendingItemTransfer())
                    throw new IllegalStateException("Item mutation refused while CacheD transfer pending");
                return null;
            }
            synchronized (inventory) {
                return tradeWarehouseDepositLocked(item, count, inventory, onCommitted);
            }
        }
    }

    // Both monitors are already held in inventory -> warehouse order.
    private L1ItemInstance tradeWarehouseDepositLocked(L1ItemInstance item, int count,
            Warehouse inventory, Runnable onCommitted) {
        assert Thread.holdsLock(this) && Thread.holdsLock(inventory);
        if (rejectPendingItemTransfer())
            throw new IllegalStateException("Item mutation refused while CacheD transfer pending");
		if (item == null || inventory == null) {
			return null;
		}

		// caller가 들고 온 stale object를 믿지 않는다.
		final L1ItemInstance owned = GetByServerID(item.getId());
		if (owned != item) {
			return null;
		}

		if (item.getCount() <= 0 || count <= 0 || count > item.getCount()) {
			return null;
		}
		if (item.isEquipped()) {
			return null;
		}
        if (!item.isStackable() && count != 1) return null;
		if (inventory.CheckAddableToWarehouse(item, count) != CInventory.OK) {
			return null;
		}

        if (!CacheD.isWarehouseApplying()) {
            return prepareWarehouseDeposit(item, count, inventory, onCommitted);
        }

        // Warehouse transfers must reserve BOTH DB owner lanes and persist
        // source and destination rows in the same JDBC transaction.
        if (!(this instanceof CUserInventory)) return null;
        final int characterId = ((CUserInventory) this).getOwner().getId();
        final CacheD.WarehouseMove reservation;
        try { reservation = CacheD.beginWarehouseMove(characterId, inventory.transferOrderKey()); }
        catch (java.util.concurrent.RejectedExecutionException busy) { return null; }
        try (CacheD.WarehouseMove move = reservation) {
        // If the SQL transaction fails, do not let the game client continue
        // mutating memory that is no longer known to match the authoritative DB.
        final CUserSocket session = ((CUserInventory) this).getOwner().getNetConnection();
        if (session != null) move.onFailure(() ->
                CIOThreadCallback.getInstance().completeSession(session, session::kick));
        move.expectOriginal(item);
        L1ItemInstance destStack = inventory.GetFirstItemByClassID(item.getItemId());
        if (destStack != null) move.expectOriginal(destStack);
        final int originalCount = item.getCount();
        final int destinationCount = destStack == null ? 0 : destStack.getCount();
        L1ItemInstance carryItem = null;
        try {
		if (count == item.getCount()) {
			Pop(item);
			carryItem = item;
		} else {
			carryItem = move.replayCarry(item.getId());
            if (carryItem == null)
                throw new IllegalStateException("Warehouse transfer missing committed item ID");
			if (carryItem == null) {
				return null;
			}

			carryItem.setCount(count);
			carryItem.setEnchantLevel(item.getEnchantLevel());
			carryItem.setIdentified(item.isIdentified());
			carryItem.set_durability(item.get_durability());
			carryItem.setChargeCount(item.getChargeCount());
			carryItem.setRemainingTime(item.getRemainingTime());
			carryItem.setLastUsed(item.getLastUsed());
			carryItem.setBless(item.getBless());
			carryItem.setAttrEnchantLevel(item.getAttrEnchantLevel());
			carryItem.setSpecialEnchant(item.getSpecialEnchant());
			carryItem.set_bless_level(item.get_bless_level());
            carryItem.set_bless_level2(item.get_bless_level2());
			/**특수 인챈트 시스템**/
			carryItem.set_item_level(item.get_item_level());
			/**여관 리뉴얼**/
			carryItem.setHotel_Town(item.getHotel_Town());

			removeItemCountCache(item.getItemId(), count);
			item.setCount(item.getCount() - count);
			persistTransferCount(item);
			updateItem(item);
		}
        L1ItemInstance stored = null;
        RuntimeException transferFailure = null;
        try {
            stored = inventory.storeTradeItem(carryItem);
        } catch (RuntimeException e) {
            transferFailure = e;
        }
        if (stored != null) {
            move.commit();
            if (carryItem == item && stored != item) L1World.getInstance().removeObject(item);
            return stored;
        }

		// The warehouse rejected the transfer after the source was changed.
		// Restore the source rather than completing a one-sided move.
		if (carryItem == item) {
			_items.Push(item);
			insertItem(item);
		} else {
			item.setCount(item.getCount() + count);
			persistTransferCount(item);
			addItemCountCache(item.getItemId(), count);
			updateItem(item);
		}
        if (transferFailure != null) throw transferFailure;
		return null;
        } catch (RuntimeException failure) {
            // SQL was NEVER submitted. Restore the game state before the
            // reserved FIFO slot is cancelled by WarehouseMove.close().
            try {
                if (carryItem != null && inventory.GetByServerID(carryItem.getId()) == carryItem) {
                    inventory.deleteItem(carryItem);
                } else if (destStack != null && destStack.getCount() != destinationCount) {
                    destStack.setCount(destinationCount);
                    inventory.updateItem(destStack);
                }
                if (GetByServerID(item.getId()) != item) {
                    item.setCount(originalCount);
                    _items.Push(item);
                    insertItem(item);
                } else if (item.getCount() != originalCount) {
                    int delta = originalCount - item.getCount();
                    item.setCount(originalCount);
                    addItemCountCache(item.getItemId(), delta);
                    persistTransferCount(item);
                    updateItem(item);
                }
            } catch (RuntimeException recovery) {
                failure.addSuppressed(recovery);
            }
            throw failure;
        }
        } // warehouse move scope

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

		if (item.getCount() <= 0 || count <= 0 || count > item.getCount()) {
			return null;
		}
		if (item.isEquipped()) {
			return null;
		}

		// destination 상태도 transfer 직전에 다시 검증한다.
		if (inventory.CheckAddable(item, count) != OK) {
			return null;
		}

		L1ItemInstance carryItem;
		if (count == item.getCount()) {
			Pop(item);
			carryItem = item;
		} else {
			carryItem = CObjectDB.CreateItem(item.getItem().getItemId());
			if (carryItem == null) {
				return null;
			}

			carryItem.setCount(count);
			carryItem.setEnchantLevel(item.getEnchantLevel());
			carryItem.setIdentified(item.isIdentified());
			carryItem.set_durability(item.get_durability());
			carryItem.setChargeCount(item.getChargeCount());
			carryItem.setRemainingTime(item.getRemainingTime());
			carryItem.setLastUsed(item.getLastUsed());
			carryItem.setBless(item.getBless());
			carryItem.setAttrEnchantLevel(item.getAttrEnchantLevel());
			carryItem.setSpecialEnchant(item.getSpecialEnchant());
			carryItem.set_bless_level(item.get_bless_level());
			/**특수 인챈트 시스템**/
			carryItem.set_item_level(item.get_item_level());
			/**여관 리뉴얼**/
			carryItem.setHotel_Town(item.getHotel_Town());
            if (inventory instanceof L1GroundInventory && item.GetContributeData() != null) {
                carryItem.SetContributeData(item.GetContributeData());
            }

			removeItemCountCache(item.getItemId(), count);
			item.setCount(item.getCount() - count);
			persistTransferCount(item);
			updateItem(item);
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
			return stored;
		}

		// destination 저장 실패 시 source를 복구한다.
		if (carryItem == item) {
			_items.Push(item);
			insertItem(item);
		} else {
			item.setCount(item.getCount() + count);
			persistTransferCount(item);
			addItemCountCache(item.getItemId(), count);
			updateItem(item);
			L1World.getInstance().removeObject(carryItem);
		}
        if (transferFailure != null) throw transferFailure;
		return null;
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
	private L1ItemInstance findMergeItem(L1ItemInstance incoming) {
        if (!(this instanceof L1GroundInventory)) return GetFirstItemByClassID(incoming.getItemId());
        for (L1ItemInstance candidate : _items) {
            if (candidate.getItemId() == incoming.getItemId() && candidate.SameGroundLootRights(incoming)) return candidate;
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

