
package l1j.server.server.model;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

import l1j.server.Config;
import l1j.server.MJINNSystem.MJINNHelper;
import l1j.server.MJTemplate.MJL1Type;
import l1j.server.MJTemplate.FindInventory.InventoryFindItemFilter;
import l1j.server.server.IdFactory;
import l1j.server.server.datatables.FurnitureSpawnTable;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.datatables.LetterTable;
import l1j.server.server.datatables.PetTable;
import l1j.server.server.model.Instance.L1FurnitureInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Warehouse.Warehouse;
import l1j.server.server.storage.CacheD;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.packets.Protocol.Packet.CS_AlchemyMakeReq;
import l1j.server.server.packets.Protocol.Packet.Craft.CraftInputItemSlotInfo;
import l1j.server.server.templates.L1Item;

public class L1Inventory extends L1Object {
	private static final long serialVersionUID = 1L;

	protected final List<L1ItemInstance> _items = new CopyOnWriteArrayList<L1ItemInstance>();
	
	public static final int MAX_AMOUNT = 2000000000; // 2G
	public static final int MAX_WEIGHT = 1500;
	public static final int OK = 0;
	public static final int SIZE_OVER = 1;
	public static final int WEIGHT_OVER = 2; 
	public static final int AMOUNT_OVER = 3;
	public static final int WAREHOUSE_TYPE_PERSONAL = 0;
	public static final int WAREHOUSE_TYPE_CLAN = 1;

	public L1Inventory() {
	}
	
	/**
	 * PC 인벤토리는 L1PcInventory에서 캐시를 사용한다.
	 * 일반/NPC 인벤토리는 별도 ConcurrentHashMap을 만들지 않고 필요 시 목록에서 계산한다.
	 */
	public int getItemCountDirect(int itemId) {
		int total = 0;
		for (L1ItemInstance item : _items) {
			if (item != null && item.getItemId() == itemId) {
				total += item.getCount();
			}
		}
		return total;
	}

	/** PC 인벤토리 캐시 갱신 훅. 일반 인벤토리는 아무것도 하지 않는다. */
	protected void addItemCountCache(int itemId, int amount) {
	}

	/** PC 인벤토리 캐시 갱신 훅. 일반 인벤토리는 아무것도 하지 않는다. */
	protected void removeItemCountCache(int itemId, int amount) {
	}

	/** 아이템 존재 여부만 확인 */
	public boolean hasItemDirect(int itemId) {
		   return hasItemDirect(itemId, 1);
	}
	
	public boolean hasItemAnyDirect(int... itemIds) {
	    for (int id : itemIds) {
	        if (getItemCountDirect(id) > 0) {
	            return true;
	        }
	    }
	    return false;
	}

	public boolean hasItemDirect(int itemId, int count) {
	    if (count <= 0) {
	        return true; 
	    }
	    return getItemCountDirect(itemId) >= count;
	}
	
	public boolean isEquippedDirect(int itemId) {
		for (L1ItemInstance item : _items) {
			if (item != null && item.getItemId() == itemId && item.isEquipped()) {
				return true;
			}
		}
		return false;
	}

	public boolean isEquippedAny(int... itemIds) {
	    for (int id : itemIds) {
	        if (isEquippedDirect(id)) return true;
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

	public int getWeight() {
		int weight = 0;

		for (L1ItemInstance item : _items) {
			weight += item.getWeight();
		}
		weight /= Config.RATE_WEIGHT_LIMIT;
		return weight;
	}

	public int checkAddAdena(int count){
		if(count < 0 || count > MAX_AMOUNT)
			return AMOUNT_OVER;
		
		if(!checkItem(L1ItemId.ADENA) && getSize() >= 180)
			return SIZE_OVER;
		
		return OK;
	}
	
	
	
	public int checkAddItem(L1ItemInstance item, int count) {
		if (item == null) {
			return -1;
		}
		if (count < 0 || count > MAX_AMOUNT) {
			return AMOUNT_OVER;
		}

		if (item.getCount() <= 0 || count <= 0) {
			return -1;
		}
		if (getSize() > Config.MAX_NPC_ITEM || (getSize() == Config.MAX_NPC_ITEM && (!item.isStackable() || !checkItem(item.getItem().getItemId())))) {
			return SIZE_OVER;
		}

		int weight = getWeight() + item.getItem().getWeight() * count / 1000 + 1;
		if (weight < 0 || (item.getItem().getWeight() * count / 1000) < 0) {
			return WEIGHT_OVER;
		}
		if (weight > (MAX_WEIGHT * Config.RATE_WEIGHT_LIMIT_PET)) {
			return WEIGHT_OVER;
		}

		L1ItemInstance itemExist = findItemId(item.getItemId());
		if (itemExist != null && (itemExist.getCount() + count) > MAX_AMOUNT) {
			return AMOUNT_OVER;
		}

		return OK;
	}

	public int checkAddItemToWarehouse(L1ItemInstance item, int count, int type) {
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
				|| (getSize() == maxSize && (!item.isStackable() || !checkItem(item.getItem().getItemId())))) {
			return SIZE_OVER;
		}

		return OK;
	}

	public synchronized L1ItemInstance storeItem(int id, int count, int enchant) {
        if (rejectPendingItemTransfer())
            throw new IllegalStateException("Item mutation refused while CacheD transfer pending");
		if (count <= 0) {
			return null;
		}
		L1Item temp = ItemTable.getInstance().getTemplate(id);
		if (temp == null) {
			return null;
		}

		if (temp.isStackable()) {
			L1ItemInstance item = new L1ItemInstance(temp, count);
			if (findItemId(id) == null) {
				item.setId(IdFactory.getInstance().nextId());
				L1World.getInstance().storeObject(item);
			}
			return storeItem(item);
		}

		L1ItemInstance result = null;
		L1ItemInstance item = null;
		for (int i = 0; i < count; i++) {
			item = new L1ItemInstance(temp, 1);
			item.setId(IdFactory.getInstance().nextId());
			item.setEnchantLevel(enchant);
			item.setIdentified(true);
			L1World.getInstance().storeObject(item);
			storeItem(item);
			result = item;
		}
		return result;
	}

	public L1ItemInstance storeItem(int id, int count) {
		return storeItem(id, count, true);
	}
	
	public synchronized L1ItemInstance storeItem(int id, int count, boolean isIden) {
        if (rejectPendingItemTransfer())
            throw new IllegalStateException("Item mutation refused while CacheD transfer pending");
		if (count <= 0) {
			return null;
		}
		L1Item temp = ItemTable.getInstance().getTemplate(id);
		if (temp == null) {
			return null;
		}

		if (temp.isStackable()) {
			L1ItemInstance item = new L1ItemInstance(temp, count);
			item.setIdentified(isIden);
			if (findItemId(id) == null) {
				item.setId(IdFactory.getInstance().nextId());
				L1World.getInstance().storeObject(item);
			}

			return storeItem(item);
		}

		L1ItemInstance result = null;
		L1ItemInstance item = null;
		for (int i = 0; i < count; i++) {
			item = new L1ItemInstance(temp, 1);
			item.setIdentified(isIden);
			item.setId(IdFactory.getInstance().nextId());
			L1World.getInstance().storeObject(item);
			storeItem(item);
			result = item;
		}
		return result;
	}
	
	public synchronized L1ItemInstance TalkIsland_storeItem(int id, int count) {
        if (rejectPendingItemTransfer())
            throw new IllegalStateException("Item mutation refused while CacheD transfer pending");
		if (count <= 0) {
			return null;
		}
		L1Item temp = ItemTable.getInstance().getTemplate(id);
		if (temp == null) {
			return null;
		}

		if (temp.isStackable()) {
			L1ItemInstance item = new L1ItemInstance(temp, count);

			if (findItemId(id) == null) {
				
				item.setId(IdFactory.getInstance().nextId());
				L1World.getInstance().storeObject(item);
			}

			item.setIdentified(true);

			return storeItem(item);
		}

		L1ItemInstance result = null;
		L1ItemInstance item = null;
		for (int i = 0; i < count; i++) {
			item = new L1ItemInstance(temp, 1);
			item.setId(IdFactory.getInstance().nextId());
			item.setIdentified(true);
			L1World.getInstance().storeObject(item);
			storeItem(item);
			result = item;
		}
		return result;
	}
	public synchronized L1ItemInstance storeItemTrea(L1ItemInstance item) {
        if (rejectPendingItemTransfer())
            throw new IllegalStateException("Item mutation refused while CacheD transfer pending");
		if (item.getCount() <= 0) {
			return null;
		}
		int itemId = item.getItem().getItemId();
		if (item.isStackable()) {
			L1ItemInstance findItem = findItemId(itemId);
			if (findItem != null) {
				addItemCountCache(itemId, item.getCount());
				findItem.setCount(findItem.getCount() + item.getCount());
				updateItem(findItem);
				return findItem;
			}
		}
		item.setX(getX());
		item.setY(getY());
		item.setMap(getMapId());
		int chargeCount = item.getItem().getMaxChargeCount();

		if (itemId == 20383) {
			chargeCount = 50;
		}

		item.setChargeCount(chargeCount);
		
		switch (itemId) {
		case 3000048: // 꼬마 요정의 마음
			SetDeleteTime(item, 1439); // 24시간 -1분
			break;
		case L1ItemId.KILLTON_CONTRACT:
		case L1ItemId.MERIN_CONTRACT:
			SetDeleteTime(item, 60);
			break;
		default:
			break;
		}

		if (item.getItem().getType2() == 0 && item.getItem().getType() == 2) { // light
			item.setRemainingTime(item.getItem().getLightFuel());
		} else {
			item.setRemainingTime(item.getItem().getMaxUseTime());
		}
		
		int bless = item.getBless();
		item.setBless(bless);

		_items.add(item);
		try {
			insertItem(item);
		} catch (RuntimeException e) {
			_items.remove(item);
			throw e;
		}
		return item;
	}
	
	public synchronized L1ItemInstance storeItem(L1ItemInstance item) {
        if (rejectPendingItemTransfer())
            throw new IllegalStateException("Item mutation refused while CacheD transfer pending");
		if (item.getCount() <= 0) {
			return null;
		}
		int itemId = item.getItem().getItemId();
		if (item.isStackable()) {
			L1ItemInstance findItem = findItemId(itemId);
			
			if (findItem != null) {
				addItemCountCache(itemId, item.getCount());
				findItem.setCount(findItem.getCount() + item.getCount());
				updateItem(findItem);
				return findItem;
			}
		}
		if (item.get_item_level() > 0) {
			item.set_item_level(item.get_item_level());
		}

		
		item.setX(getX());
		item.setY(getY());
		item.setMap(getMapId());
		int chargeCount = item.getItem().getMaxChargeCount();
		switch (itemId) {
		case 40006:
		case 40007:
		case 40008:
		case 140006:
		case 140008:
		case 41401:
		case 810006:
		case 810007:
			L1ItemInstance findItem = findItemId(itemId);
			if (findItem != null) {
				Random random = new Random(System.nanoTime());
				chargeCount -= random.nextInt(5);
				findItem.setChargeCount(findItem.getChargeCount() + chargeCount);
				updateItem(findItem);
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

		if (item.getItem().getType2() == 0 && item.getItem().getType() == 2) { // light
			item.setRemainingTime(item.getItem().getLightFuel());
		} else {
			item.setRemainingTime(item.getItem().getMaxUseTime());
		}
		
		item.setBless(item.getItem().getBless());
		item.setIdentified(true);
		item.set_bless_level(item.get_bless_level());
		item.set_item_level(item.get_item_level());
		_items.add(item);
		try {
			insertItem(item);
		} catch (RuntimeException e) {
			_items.remove(item);
			throw e;
		}
		return item;
	}

	public synchronized L1ItemInstance storeItem(L1ItemInstance item, int bless) {
        if (rejectPendingItemTransfer())
            throw new IllegalStateException("Item mutation refused while CacheD transfer pending");
		if (item.getCount() <= 0) {
			return null;
		}
		int itemId = item.getItem().getItemId();
		if (item.isStackable()) {
			L1ItemInstance findItem = findItemId(itemId);
			
			if (findItem != null) {
				addItemCountCache(itemId, item.getCount());
				findItem.setCount(findItem.getCount() + item.getCount());
				updateItem(findItem);
				return findItem;
			}
		}
		if (item.get_item_level() > 0) {
			item.set_item_level(item.get_item_level());
		}
		
		item.setX(getX());
		item.setY(getY());
		item.setMap(getMapId());
		int chargeCount = item.getItem().getMaxChargeCount();
		switch (itemId) {
		case 40006:
		case 40007:
		case 40008:
		case 140006:
		case 140008:
		case 41401:
		case 810006:
		case 810007:
			L1ItemInstance findItem = findItemId(itemId);
			if (findItem != null) {
				Random random = new Random(System.nanoTime());
				chargeCount -= random.nextInt(5);
				findItem.setChargeCount(findItem.getChargeCount() + chargeCount);
				updateItem(findItem);
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

		if (item.getItem().getType2() == 0 && item.getItem().getType() == 2) { // light
			item.setRemainingTime(item.getItem().getLightFuel());
		} else {
			item.setRemainingTime(item.getItem().getMaxUseTime());
		}
		item.setBless(bless);
		item.setIdentified(true);
		item.set_bless_level(item.get_bless_level());
		item.set_item_level(item.get_item_level());
		_items.add(item);
		try {
			insertItem(item);
		} catch (RuntimeException e) {
			_items.remove(item);
			throw e;
		}
		return item;
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
        if (!(this instanceof L1PcInventory)) return false;
        final L1PcInventory inventory = (L1PcInventory) this;
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
		if (item.isStackable()) {	
			L1ItemInstance findItem = findItemId(item.getItem().getItemId());
			
			int itemId = item.getItem().getItemId();
			if (findItem != null) {
				final int before = findItem.getCount();
				if (before < 0 || (long) before + item.getCount() > MAX_AMOUNT) return null;
				findItem.setCount(before + item.getCount());
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
		L1ItemInstance findItem = findItemId(item.getItem().getItemId());


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
		_items.add(item);
		try {
			insertItem(item);
		} catch (RuntimeException e) {
			_items.remove(item);
			throw e;
		}

		return item;
	}

	public boolean 제작리스트1(int itemid, int count, int en) {
		if (count <= 0) {
			return false;
		}
		if (ItemTable.getInstance().getTemplate(itemid).isStackable()) {
			L1ItemInstance item = findItemId(itemid);
			if (item != null && item.getCount() >= count && item.getEnchantLevel() == en) {
				removeItem(item, count);
				return true;
			}
		} else {
			L1ItemInstance[] itemList = findItemsId(itemid);
			if (itemList.length == count) {
				int j = 0;
				for (int i = 0; i < count; ++i) {
					if (itemList[i].getEnchantLevel() == en) {
						removeItem(itemList[i], 1);
						if (++j == count)
							break;
					}
				}
				return true;
			} else if (itemList.length > count) {
				DataComparator dc = new DataComparator();
				extracted(itemList, dc);
				int j = 0;
				for (int i = 0; i < itemList.length; ++i) {
					if (itemList[i].getEnchantLevel() == en) {
						removeItem(itemList[i], 1);
						if (++j == count)
							break;
					}
				}
				return true;
			}
		}
		return false;
	}
	public L1ItemInstance[] findItemsId_HighEnchant(int id, int enchant) {
		ArrayList<L1ItemInstance> itemList = new ArrayList<L1ItemInstance>();
		for (L1ItemInstance item : _items) {
			if (item.getItemId() == id && item.getEnchantLevel() == enchant) {
				itemList.add(item);
			}
		}
		return itemList.toArray(new L1ItemInstance[] {});
	}
	
	public boolean 제작리스트3(int itemid, int count, int en, int bless, int descid, int attr, int attr_en) {
		L1ItemInstance item = findItemDescId(itemid);
		if (item == null){
			return false;
		}
		if (count <= 0) {
			return false;
		}
		if (attr == 2){
			attr_en += 5;
		} else if (attr == 3){
			attr_en += 10;
		} else if (attr == 4){
			attr_en += 15;
		}
		if (item.isStackable()) {
			if (item != null && item.getCount() >= count && item.getEnchantLevel() == en 
					&& item.getBless() == bless && item.getItem().getItemDescId() == descid
					&& item.getAttrEnchantLevel() == attr_en) {
				removeItem(item, count);
				return true;
			}
		} else {
			L1ItemInstance[] itemList = findItemsId_HighEnchant(item.getItem().getItemId(), en);
			if (itemList.length == count) {
				int j = 0;
				for (int i = 0; i < count; ++i) {
					if (itemList[i].getEnchantLevel() == en && itemList[i].getBless() == bless 
							&& itemList[i].getItem().getItemDescId() ==  descid
							&& itemList[i].getAttrEnchantLevel() == attr_en) {
						removeItem(itemList[i], 1);
						if (++j == count)
							break;
					}
				}
				return true;
			} else if (itemList.length > count) {
				DataComparator dc = new DataComparator();
				extracted(itemList, dc);
				int j = 0;
				for (int i = 0; i < itemList.length; ++i) {
					if (itemList[i].getEnchantLevel() == en && itemList[i].getBless() == bless
							&&  itemList[i].getItem().getItemDescId() ==  descid
							&& itemList[i].getAttrEnchantLevel() == attr_en) {
						removeItem(itemList[i], 1);
						if (++j == count)
							break;
					}
				}
				return true;
			}
		}
		return false;
	}

	public boolean ConsumeItemDescId(int descId, int enchant, int count) {
		L1ItemInstance item = findItemDescId(descId);
		if (item != null) {
			if (count <= 0) return false;
			if (item.isStackable()) {
				if (item.getCount() >= count) {
					removeItem(item, count);
					return true;
				}
			} else {
				L1ItemInstance[] itemList = findItemsId_HighEnchant(item.getItem().getItemId(), enchant);
				if (itemList.length >= count) {
					for (int i = 0; i < count; i++) {
						removeItem(itemList[i], 1);
					}
					return true;
				}
			}
		}
		return false;
	}
	public boolean consumeItem(int itemid, int count) {
		L1Item tem = ItemTable.getInstance().getTemplate(itemid);
		if (tem == null){
			return false;
		}
		if (count <= 0) {
			return false;
		}
		if (tem.isStackable()) {
			L1ItemInstance item = findItemId(itemid);
			if (item != null && item.getCount() >= count) {
				removeItem(item, count);
				return true;
			}
		} else {
			L1ItemInstance[] itemList = findItemsId(itemid);
			if (itemList.length == count) {
				for (int i = 0; i < count; i++) {
					removeItem(itemList[i], 1);
				}
				return true;
			} else if (itemList.length > count) {
				DataComparator dc = new DataComparator();
				extracted(itemList, dc);
				for (int i = 0; i < count; i++) {
					removeItem(itemList[i], 1);
				}
				return true;
			}
		}
		return false;
	}
	
	public boolean consumeItem2(int descid, int count) {
		if (count <= 0) {
			return false;
		}

		L1ItemInstance[] itemList = findItemsId(descid, true);
		if (itemList.length == count) {
			for (int i = 0; i < count; i++) {
				removeItem(itemList[i], 1);
			}
			return true;
		} else if (itemList.length > count) {
			DataComparator dc = new DataComparator();
			extracted(itemList, dc);
			for (int i = 0; i < count; i++) {
				removeItem(itemList[i], 1);
			}
			return true;
		}
		
		return false;
	}
	
	public boolean consumeItem(int itemid, int count, int descid) {
		if (count <= 0) {
			return false;
		}
		if (ItemTable.getInstance().getTemplate(itemid).isStackable()) {
			L1ItemInstance item = findItemId(itemid);
			if (item != null && item.getCount() >= count && item.getItem().getItemDescId() == descid) {
				removeItem(item, count, descid);
				return true;
			}
		} else {
			L1ItemInstance[] itemList = findItemsId(itemid, descid);
			if (itemList.length == count) {
				for (int i = 0; i < count; i++) {
					removeItem(itemList[i], 1, descid);
				}
				return true;
			} else if (itemList.length > count) {
				DataComparator dc = new DataComparator();
				extracted(itemList, dc);
				for (int i = 0; i < count; i++) {
					removeItem(itemList[i], 1, descid );
				}
				return true;
			}
		}
		return false;
	}
	
	public boolean consumeItem(int itemid) {
		L1ItemInstance item = findItemId(itemid);
		if (item != null) {
			removeItem(item, item.getCount());
			return true;
		}
		return false;
	}

	@SuppressWarnings("unchecked")
	private void extracted(L1ItemInstance[] itemList, DataComparator dc) {
		Arrays.sort(itemList, dc);
	}

	@SuppressWarnings("rawtypes")
	public class DataComparator implements java.util.Comparator {
		public int compare(Object item1, Object item2) {
			return ((L1ItemInstance) item1).getEnchantLevel() - ((L1ItemInstance) item2).getEnchantLevel();
		}
	}

	public int removeItem(int objectId) {
		L1ItemInstance item = getItem(objectId);
		if (item == null) return 0;
		return removeItem(item, item.getCount());
	}

	public int removeItem(int objectId, int count) {
		L1ItemInstance item = getItem(objectId);
		return removeItem(item, count);
	}

	public int removeItem(L1ItemInstance item) {
		if (item == null) return 0;
		return removeItem(item, item.getCount());
	}

	public int removeItem(L1ItemInstance item, int count) {
        if (rejectPendingItemTransfer())
            throw new IllegalStateException("Item mutation refused while CacheD transfer pending");
		if (item == null || getItem(item.getId()) != item) {
			return 0;
		}
		if (item.getCount() <= 0 || count <= 0) {
			return 0;
		}
		if (item.getCount() < count) {
			count = item.getCount();
		}
		if (item.getCount() == count) {
			int itemId = item.getItem().getItemId();
			if (itemId == 40314 || itemId == 40316) {
				PetTable.getInstance().deletePet(item.getId());
			} else if (itemId >= 49016 && itemId <= 49025) {
				LetterTable lettertable = new LetterTable();
				lettertable.deleteLetter(item.getId());
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
			deleteItem(item);
			L1World.getInstance().removeObject(item);
		} else {
			
			int itemId = item.getItemId();
			removeItemCountCache(itemId, count);

			// [디버그 로그]
			//System.out.println(String.format("[아이템소모-일부] ID: %d, 소모수량: %d, 맵잔량: %d", itemId, count, _itemCountMap.getOrDefault(itemId, 0)));
			
			item.setCount(item.getCount() - count);
			updateItem(item);
		}
		return count;
	}
	
	public int removeItem(L1ItemInstance item, int count, int descid) {
        if (rejectPendingItemTransfer())
            throw new IllegalStateException("Item mutation refused while CacheD transfer pending");
		if (item == null || getItem(item.getId()) != item) {
			return 0;
		}
		if (item.getCount() <= 0 || count <= 0) {
			return 0;
		}
		if (item.getItem().getItemDescId() != descid){
			return 0;
		}
		if (item.getCount() < count) {
			count = item.getCount();
		}
		if (item.getCount() == count) {
			int itemId = item.getItem().getItemId();
			if (itemId == 40314 || itemId == 40316) {
				PetTable.getInstance().deletePet(item.getId());
			} else if (itemId >= 49016 && itemId <= 49025) {
				LetterTable lettertable = new LetterTable();
				lettertable.deleteLetter(item.getId());
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
			deleteItem(item);
			L1World.getInstance().removeObject(item);
		} else {
			
			int itemId = item.getItemId();
			removeItemCountCache(itemId, count);

			// [디버그 로그]
		//	System.out.println(String.format("[아이템소모-일부] ID: %d, 소모수량: %d, 맵잔량: %d", itemId, count, _itemCountMap.getOrDefault(itemId, 0)));
			
			item.setCount(item.getCount() - count);
			updateItem(item);
		}
		return count;
	}

	public void deleteItem(L1ItemInstance item) {
		_items.remove(item);
	}

    /**
     * Fee currency for a warehouse withdrawal is changed inside the SAME
     * WarehouseMove scope as the item transfer. No independent CacheD job.
     * Only pre-validated currency stacks are accepted here.
     */
    public synchronized boolean debitWarehouseFee(L1ItemInstance fee, int amount) {
        if (rejectPendingItemTransfer())
            throw new IllegalStateException("Item mutation refused while CacheD transfer pending");
        if (fee == null || amount <= 0 || getItem(fee.getId()) != fee
                || fee.getCount() < amount || !fee.isStackable()) return false;
        if (fee.getCount() == amount) {
            deleteItem(fee);
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
        if (getItem(fee.getId()) == fee) {
            final int now = fee.getCount();
            if (now > original) throw new IllegalStateException("Fee changed during rollback");
            fee.setCount(original);
            addItemCountCache(fee.getItemId(), original - now);
            persistTransferCount(fee);
            updateItem(fee);
        } else if (getItem(fee.getId()) == null) {
            fee.setCount(original);
            insertItem(fee);
            _items.add(fee);
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
        if (!(this instanceof L1PcInventory) || ids == null || counts == null
                || ids.length <= 1 || ids.length != counts.length || ids.length > 100
                || dest == null || !(dest instanceof l1j.server.server.model.Warehouse.PrivateWarehouse
                    || dest instanceof l1j.server.server.model.Warehouse.ClanWarehouse
                    || dest instanceof l1j.server.server.model.Warehouse.ElfWarehouse)
                || rejectPendingItemTransfer()) return false;
        final l1j.server.server.model.Instance.L1PcInstance pc =
                ((L1PcInventory) this).getOwner();
        final CIOClient session = pc.getNetConnection();
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
                    final L1ItemInstance item = getItem(ids[i]);
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
                        destinations[i] = item.isStackable() ? dest.findItemId(item.getItemId()) : null;
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
                            synchronized (L1Inventory.this) {
                                synchronized (dest) {
                                    if (!session.isConnected() || pc.getSessionStamp() != stamp
                                            || pc.getNetConnection() != session)
                                        throw new IllegalStateException("Stale warehouse batch session");
                                    for (int i = 0; i < size; i++) {
                                        if (getItem(ids[i]) != sources[i]
                                                || sources[i].getCount() != sourceCounts[i])
                                            throw new IllegalStateException("Warehouse batch source changed after COMMIT");
                                        if (group[i] == i && destinations[i] != null
                                                && (dest.getItem(destinations[i].getId()) != destinations[i]
                                                || destinations[i].getCount() != destinationCounts[i]))
                                            throw new IllegalStateException("Warehouse batch destination changed after COMMIT");
                                    }
                                    CacheD.applyCommittedWarehouseMove(move, () -> {
                                        for (int i = 0; i < size; i++) {
                                            L1ItemInstance result = tradeItem(sources[i], counts[i], dest);
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
                                            java.util.logging.Logger.getLogger(L1Inventory.class.getName())
                                                    .log(java.util.logging.Level.WARNING,
                                                         "Committed warehouse deposit audit dispatch failed", auditFailure);
                                        }
                                    }
                                    try { pc.getLight().turnOnOffLight(); }
                                    catch (RuntimeException lightFailure) {
                                        java.util.logging.Logger.getLogger(L1Inventory.class.getName())
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

    /** One durable receipt for a selected private/clan/elf warehouse withdrawal.
     * The warehouse and player inventory remain unchanged until the SQL commit.
     * Special non-stackable charge-merging items retain their existing single-item path.
     */
    public synchronized boolean tradeWarehouseWithdrawalBatch(int[] ids, int[] counts,
            Warehouse source, int feeItemId, int feeEach, Runnable onCommitted) {
        if (!(this instanceof L1PcInventory) || ids == null || counts == null
                || ids.length <= 1 || ids.length > 100 || ids.length != counts.length
                || source == null || !(source instanceof l1j.server.server.model.Warehouse.PrivateWarehouse
                    || source instanceof l1j.server.server.model.Warehouse.ClanWarehouse
                    || source instanceof l1j.server.server.model.Warehouse.ElfWarehouse)
                || (feeItemId != L1ItemId.ADENA && feeItemId != 40494)
                || feeEach <= 0 || rejectPendingItemTransfer()) return false;
        final l1j.server.server.model.Instance.L1PcInstance pc =
                ((L1PcInventory) this).getOwner();
        final CIOClient session = pc.getNetConnection();
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
            final L1ItemInstance[] carries = new L1ItemInstance[size];
            synchronized (source) {
                final L1ItemInstance fee = findItemId(feeItemId);
                if (fee == null || !fee.isStackable() || fee.getCount() < totalFee) return false;
                final int feeCount = fee.getCount();
                int newSlots = 0;
                long additionalWeight = 0L;
                int feeGroup = -1;
                for (int i = 0; i < size; i++) {
                    final L1ItemInstance item = source.getItem(ids[i]);
                    final int count = counts[i];
                    if (item == null || item.getId() != ids[i] || item.getCount() <= 0
                            || count <= 0 || count > item.getCount() || item.isEquipped()
                            || (!item.isStackable() && count != 1)) return false;
                    // These items merge charge_count rather than stack count; their
                    // existing specialized path must not be flattened into a batch.
                    if (!item.isStackable()) {
                        switch (item.getItemId()) {
                        case 40006: case 40007: case 40008: case 140006:
                        case 140008: case 41401: case 810006: case 810007:
                            return false;
                        default: break;
                        }
                    }
                    for (int j = 0; j < i; j++) if (ids[j] == ids[i]) return false;
                    sources[i] = item;
                    sourceCounts[i] = item.getCount();
                    additionalWeight += (long) item.getItem().getWeight() * count / 1000L + 1L;
                    if (additionalWeight > Integer.MAX_VALUE) return false;
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
                        destinations[i] = item.isStackable() ? findItemId(item.getItemId()) : null;
                        destCounts[i] = destinations[i] == null ? 0 : destinations[i].getCount();
                        if (destinations[i] == null) newSlots++;
                        if (destinations[i] == fee) feeGroup = i;
                    }
                    groupTotals[g] += count;
                    // PC inventory currently caps an existing stack at 1.8 billion.
                    if (groupTotals[g] + destCounts[g] > 1_800_000_000L) return false;
                }
                if (getSize() + newSlots > 180) return false;
                // The inventory's weight is rate-scaled. Conservatively
                // account for the configured rate and per-stack rounding so
                // committed replay cannot hit a later weight rejection.
                final double weightRate = Config.RATE_WEIGHT_LIMIT;
                if (!Double.isFinite(weightRate) || weightRate <= 0.0
                        || pc.getMaxWeight() <= 0) return false;
                final double projectedWeight = getWeight()
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
                        if (dest == fee) finalCount -= (int) totalFee;
                        if (dest != null) {
                            move.expectOriginal(dest);
                            if (finalCount == 0) move.prepareCharacterDelete(dest);
                            else move.prepareCharacterWrite(l1j.server.server.model.Warehouse.Warehouse
                                    .transferCopy(dest, dest.getId(), finalCount, dest.getChargeCount()), false);
                        } else {
                            move.prepareCharacterWrite(l1j.server.server.model.Warehouse.Warehouse
                                    .characterTransferCopy(carries[i], carries[i].getId(),
                                            finalCount, carries[i].getChargeCount()), true);
                        }
                    }
                    if (feeGroup < 0) {
                        move.expectOriginal(fee);
                        if (feeCount == totalFee) move.prepareCharacterDelete(fee);
                        else move.prepareCharacterWrite(l1j.server.server.model.Warehouse.Warehouse
                                .transferCopy(fee, fee.getId(), (int) (feeCount - totalFee),
                                        fee.getChargeCount()), false);
                    }
                    move.commit(() -> CIOThreadCallback.getInstance().completeWarehouseMove(pc,
                            stamp, () -> {
                        synchronized (session) {
                            synchronized (L1Inventory.this) {
                                synchronized (source) {
                                    if (!session.isConnected() || pc.getSessionStamp() != stamp
                                            || pc.getNetConnection() != session)
                                        throw new IllegalStateException("Stale warehouse withdrawal batch session");
                                    if (getItem(fee.getId()) != fee || fee.getCount() != feeCount)
                                        throw new IllegalStateException("Withdrawal batch fee preimage changed");
                                    for (int i = 0; i < size; i++) {
                                        if (source.getItem(ids[i]) != sources[i]
                                                || sources[i].getCount() != sourceCounts[i])
                                            throw new IllegalStateException("Withdrawal source changed after COMMIT");
                                        if (group[i] == i && destinations[i] != null
                                                && (getItem(destinations[i].getId()) != destinations[i]
                                                        || destinations[i].getCount() != destCounts[i]))
                                            throw new IllegalStateException("Withdrawal destination changed after COMMIT");
                                    }
                                    CacheD.applyCommittedWarehouseMove(move, () -> {
                                        for (int i = 0; i < size; i++) {
                                            L1ItemInstance result = source.tradeItem(sources[i], counts[i],
                                                    L1Inventory.this, feeItemId, feeEach);
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
                                            java.util.logging.Logger.getLogger(L1Inventory.class.getName())
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
        if (!(this instanceof L1PcInventory)) return null;
        final L1PcInventory pcInventory = (L1PcInventory) this;
        final l1j.server.server.model.Instance.L1PcInstance pc = pcInventory.getOwner();
        final CIOClient session = pc.getNetConnection();
        if (session == null || !session.isConnected()) return null;
        final int sessionStamp = pc.getSessionStamp();
        if (!pc.tryBeginItemTransfer()) return null;
        boolean submitted = false;
        try {
        final int sourceCount = item.getCount();
        final L1ItemInstance destStack = item.isStackable() ? dest.findItemId(item.getItemId()) : null;
        final int destCount = destStack == null ? 0 : destStack.getCount();
        if (destStack != null && (long) destCount + count > MAX_AMOUNT) return null;

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
                    if (!session.isConnected() || pc.getSessionStamp() != sessionStamp
                            || pc.getNetConnection() != session || getItem(item.getId()) != item
                            || item.getCount() != sourceCount
                            || (destStack != null && (dest.getItem(destStack.getId()) != destStack
                                    || destStack.getCount() != destCount)))
                        throw new IllegalStateException("Warehouse deposit preimage changed after DB COMMIT");
                    CacheD.applyCommittedWarehouseMove(move, () -> {
                        L1ItemInstance applied = tradeItem(item, count, dest);
                        if (applied == null)
                            throw new IllegalStateException("Warehouse deposit replay failed");
                        if (move.replayCarry() == applied)
                            L1World.getInstance().storeObject(applied);
                    });
                    Warehouse.recordCommittedMove(pc, dest, true, item, count);
                    pc.endItemTransfer();
                    try { pc.getLight().turnOnOffLight(); }
                    catch (RuntimeException lightFailure) {
                        java.util.logging.Logger.getLogger(L1Inventory.class.getName())
                                .log(java.util.logging.Level.WARNING, "Warehouse light refresh failed", lightFailure);
                    }
                    // Never execute the next transfer inline: this move still
                    // holds both CacheD FIFO owner lanes until its CIO callback exits.
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

	public synchronized L1ItemInstance tradeItem(int objectId, int count, Warehouse inventory) {
        return tradeItem(objectId, count, inventory, null);
	}

    public synchronized L1ItemInstance tradeItem(int objectId, int count, Warehouse inventory,
            Runnable onCommitted) {
        return tradeItem(getItem(objectId), count, inventory, onCommitted);
    }

	public synchronized L1ItemInstance tradeItem(int objectId, int count, L1Inventory inventory) {
		L1ItemInstance item = getItem(objectId);
		return tradeItem(item, count, inventory);
	}

	public synchronized L1ItemInstance tradeItem(L1ItemInstance item, int count, Warehouse inventory) {
        return tradeItem(item, count, inventory, null);
    }

    public synchronized L1ItemInstance tradeItem(L1ItemInstance item, int count,
            Warehouse inventory, Runnable onCommitted) {
        if (rejectPendingItemTransfer())
            throw new IllegalStateException("Item mutation refused while CacheD transfer pending");
		if (item == null || inventory == null) {
			return null;
		}

		// caller가 들고 온 stale object를 믿지 않는다.
		final L1ItemInstance owned = getItem(item.getId());
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
		if (inventory.checkAddItemToWarehouse(item, count) != L1Inventory.OK) {
			return null;
		}

        if (!CacheD.isWarehouseApplying()) {
            return prepareWarehouseDeposit(item, count, inventory, onCommitted);
        }

        // Warehouse transfers must reserve BOTH DB owner lanes and persist
        // source and destination rows in the same JDBC transaction.
        if (!(this instanceof L1PcInventory)) return null;
        final int characterId = ((L1PcInventory) this).getOwner().getId();
        final CacheD.WarehouseMove reservation;
        try { reservation = CacheD.beginWarehouseMove(characterId, inventory.transferOrderKey()); }
        catch (java.util.concurrent.RejectedExecutionException busy) { return null; }
        try (CacheD.WarehouseMove move = reservation) {
        // If the SQL transaction fails, do not let the game client continue
        // mutating memory that is no longer known to match the authoritative DB.
        final CIOClient session = ((L1PcInventory) this).getOwner().getNetConnection();
        if (session != null) move.onFailure(() ->
                CIOThreadCallback.getInstance().completeSession(session, session::kick));
        move.expectOriginal(item);
        L1ItemInstance destStack = inventory.findItemId(item.getItemId());
        if (destStack != null) move.expectOriginal(destStack);
        final int originalCount = item.getCount();
        final int destinationCount = destStack == null ? 0 : destStack.getCount();
        L1ItemInstance carryItem = null;
        try {
		if (count == item.getCount()) {
			deleteItem(item);
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
            return stored;
        }

		// The warehouse rejected the transfer after the source was changed.
		// Restore the source rather than completing a one-sided move.
		if (carryItem == item) {
			_items.add(item);
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
                if (carryItem != null && inventory.getItem(carryItem.getId()) == carryItem) {
                    inventory.deleteItem(carryItem);
                } else if (destStack != null && destStack.getCount() != destinationCount) {
                    destStack.setCount(destinationCount);
                    inventory.updateItem(destStack);
                }
                if (getItem(item.getId()) != item) {
                    item.setCount(originalCount);
                    _items.add(item);
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

	public synchronized L1ItemInstance tradeItem(L1ItemInstance item, int count, L1Inventory inventory) {
        if (rejectPendingItemTransfer())
            throw new IllegalStateException("Item mutation refused while CacheD transfer pending");
		if (item == null || inventory == null || inventory == this) {
			return null;
		}

        // A destination gate rejection must happen before a source delete,
        // split, cache decrement or independent persistence command.
        if (inventory.rejectPendingItemTransfer()) return null;

		// source inventory가 현재 이 exact object를 소유하는지 다시 확인한다.
		final L1ItemInstance owned = getItem(item.getId());
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
		if (inventory.checkAddItem(item, count) != OK) {
			return null;
		}

		L1ItemInstance carryItem;
		if (count == item.getCount()) {
			deleteItem(item);
			carryItem = item;
		} else {
			carryItem = ItemTable.getInstance().createItem(item.getItem().getItemId());
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
		    if (inventory instanceof L1PcInventory) {
		        stored.setItemOwner(null);
		    }
			return stored;
		}

		// destination 저장 실패 시 source를 복구한다.
		if (carryItem == item) {
			_items.add(item);
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
		L1ItemInstance item = getItem(objectId);
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

		updateItem(item, L1PcInventory.COL_DURABILITY);
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

		updateItem(item, L1PcInventory.COL_DURABILITY);
		return item;
	}

	public L1ItemInstance findEquippedItemId(int id) {
		for (L1ItemInstance item : _items) {
			if (item == null)
				continue;
			if ((item.getItem().getItemId() == id) && item.isEquipped()) {
				return item;
			}
		}
		return null;
	}
	public L1ItemInstance findItemDescId(int descId) {
		for (L1ItemInstance item : _items) {
			if (item.getItem().getItemDescId() == descId) return item;
		}
		return null;
	}
	public L1ItemInstance findItemId(int id) {
		for (L1ItemInstance item : _items) {
			if (item == null)
				continue;
			if (item.getItem().getItemId() == id) {
				return item;
			}
		}
		return null;
	}

	public L1ItemInstance[] findItemsId(int id) {
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
	
	public L1ItemInstance[] findItemsId(int descid, boolean flag) {
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
	
	public L1ItemInstance[] findItemsId(int id, int descid) {
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

	public L1ItemInstance[] findItemsIdNotEquipped(int id) {
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
	
	public L1ItemInstance getItem(int objectId) {
		L1ItemInstance item = null;
		for (Object itemObject : _items) {
			item = (L1ItemInstance) itemObject;
			if (item == null)
				continue;
			if (item.getId() == objectId) {
				return item;
			}
		}
		return null;
	}

	public boolean checkItem(int id) {
		return checkItem(id, 1);
	}
	
	

	public boolean checkItem(int id, int count) {
		if (count < 0) {
			return false;
		}

		if (count == 0) {
			return true;
		}
		L1Item l1item = ItemTable.getInstance().getTemplate(id);
		if (l1item.isStackable()) {
			L1ItemInstance item = findItemId(id);
			if (item != null && item.getCount() >= count) {
				return true;
			}
		} else {
			Object[] itemList = findItemsId(id);
			if (itemList.length >= count) {
				return true;
			}
		}
		return false;
	}

	public boolean checkEnchantItem(int id, int enchant, int count) {
		int num = 0;
		for (L1ItemInstance item : _items) {
			if (item.isEquipped()) {
				continue;
			}
			if (item.getItemId() == id && item.getEnchantLevel() == enchant) {
				num++;
				if (num == count) {
					return true;
				}
			}
		}
		return false;
	}
	
	public boolean checkEnchantItem(int id, int enchant, int count,int descid) {
		int num = 0;
		for (L1ItemInstance item : _items) {
			if (item.isEquipped()) {
				continue;
			}
			if (item.getItemId() == id && item.getEnchantLevel() == enchant && item.getItem().getItemDescId() == descid) {
				num++;
				if (num == count) {
					return true;
				}
			}
		}
		return false;
	}

	public boolean 제작리스트(int id, int enchant, int count) {
		int num = 0;
		for (L1ItemInstance item : _items) {
			if (item.isEquipped()) {
				continue;
			}
			if (item.getItemId() == id && item.getEnchantLevel() == enchant) {
				num += item.getCount();
				if (num >= count) {
					return true;
				}
			}
		}
		return false;
	}

	public boolean consumeEnchantItem(int id, int enchant, int count) {
		for (L1ItemInstance item : _items) {
			if (item.isEquipped()) {
				continue;
			}
			if (item.getItemId() == id && item.getEnchantLevel() == enchant) {
				removeItem(item);
				return true;
			}
		}
		return false;
	}

	public boolean consumeEnchantItem(int id, int enchant, int count, int descid) {
		for (L1ItemInstance item : _items) {
			if (item.isEquipped()) {
				continue;
			}
			if (item.getItemId() == id && item.getEnchantLevel() == enchant && item.getItem().getItemDescId() == descid) {
				removeItem(item);
				return true;
			}
		}
		return false;
	}

	// ////////////////////////////////////

	public boolean checkItemNotEquipped(int id, int count) {
		if (count == 0) {
			return true;
		}
		return count <= countItems(id);
	}

	public boolean checkItem(int[] ids) {
		int len = ids.length;
		int[] counts = new int[len];
		for (int i = 0; i < len; i++) {
			counts[i] = 1;
		}
		return checkItem(ids, counts);
	}

	public boolean checkItem(int[] ids, int[] counts) {
		for (int i = 0; i < ids.length; i++) {
			if (!checkItem(ids[i], counts[i])) {
				return false;
			}
		}
		return true;
	}

	public int countItems(int id) {
		if (ItemTable.getInstance().getTemplate(id).isStackable()) {
			L1ItemInstance item = findItemId(id);
			if (item != null) {
				return item.getCount();
			}
		} else {
			Object[] itemList = findItemsIdNotEquipped(id);
			return itemList.length;
		}
		return 0;
	}

	public void shuffle() {
		if(_items == null || _items.size() <= 1)
			return;
		try{
			Collections.shuffle(_items);
		}catch(Exception e){}
	}

	public void clearItems() {
		L1ItemInstance item = null;
		for (Object itemObject : _items) {
			item = (L1ItemInstance) itemObject;
			if (item == null || item.getItemId() == MJINNHelper.INN_KEYID)
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
	
	public void findAndRemoveItemId(int id){
		for(L1ItemInstance item : _items){
			if(item == null)
				continue;
			
			if(item.getItemId() == id)
				deleteItem(item);
		}
	}
	
	public void findAndRemoveItemId(int[] id){
		for(L1ItemInstance item : _items){
			if(item == null)
				continue;
			
			int item_id = item.getItemId();
			for(int i : id){
				if(item_id == i){
					deleteItem(item);
					break;
				}
			}
		}
	}
	
	private static int _instanceType = -1;
	@Override
	public int getL1Type(){
		return _instanceType == -1 ? _instanceType = super.getL1Type() | MJL1Type.L1TYPE_INVENTORY : _instanceType;		
	}

	public int checkAddItem(int itemId, int count){
		L1Item item = ItemTable.getInstance().getTemplate(itemId);		
		return checkAddItem(item, count);
	}
	
	public int checkAddItem(L1Item item, int count) {
		if (item == null) {
			return -1;
		}

		if (count < 0 || count > MAX_AMOUNT) {
			return AMOUNT_OVER;
		}

		if (getSize() > 180 || (getSize() == 180 && (!item.isStackable() || !checkItem(item.getItemId()))))
			return SIZE_OVER;

		int weight = getWeight() + item.getWeight() * count / 1000 + 1;
		if (weight < 0 || (item.getWeight() * count / 1000) < 0)
			return WEIGHT_OVER;

		L1ItemInstance itemExist = findItemId(item.getItemId());
		if (itemExist != null && ((itemExist.getCount() + count) < 0 || (itemExist.getCount() + count) > MAX_AMOUNT))
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
	
	public int fillAlchemyInputItem(LinkedList<CS_AlchemyMakeReq.Input> inputs){
		int fillCount = 0;
		int size = inputs.size();
		for(L1ItemInstance item : getItems()){
			if(fillCount >= size)
				break;
			
			if(item == null)
				continue;
			
			for(CS_AlchemyMakeReq.Input input : inputs){
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

