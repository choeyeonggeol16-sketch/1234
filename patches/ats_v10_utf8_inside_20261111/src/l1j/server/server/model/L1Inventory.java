/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 *
 * http://www.gnu.org/copyleft/gpl.html
 */
package l1j.server.server.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

import l1j.server.Templates.L1Item;
import l1j.server.configure.Config;
import l1j.server.server.IdFactory;
import l1j.server.server.datatables.FurnitureSpawnTable;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.datatables.LetterTable;
import l1j.server.server.datatables.PetTable;
import l1j.server.server.model.Instance.FurnitureInstance;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Warehouse.Warehouse;

public class L1Inventory extends L1Object {

	private static final long serialVersionUID = 1L;

	protected List<ItemInstance> _items = new CopyOnWriteArrayList<ItemInstance>();

	public static final int MAX_AMOUNT = 2000000000; // 2G
	
	public static final int MAX_SIZE = 8;
	
	public static final int MAX_WEIGHT = 1500;

	public L1Inventory() {
		//
	}

	// 인벤트리내의 아이템의 총수
	public int getSize() {
		return _items.size();
	}

	// 인벤트리내의 모든 아이템
	public List<ItemInstance> getItems() {
		return _items;
	}

	// 인벤트리내의 총중량
	public int getWeight() {
		int weight = 0;

		for (ItemInstance item : _items) {
			weight += item.getWeight();
		}
		return weight;
	}

	// 인수의 아이템을 추가해도 용량과 중량이 괜찮은가 확인 및 메세지의 송신
	public static final int OK = 0;

	public static final int SIZE_OVER = 1;

	public static final int WEIGHT_OVER = 2;

	public static final int AMOUNT_OVER = 3;
	
 

	public int checkAddItem(ItemInstance item, int count) {
		if (item == null) {
			return -1;
		}
		if (getSize() > MAX_SIZE
				|| (getSize() == MAX_SIZE && (!item.isStackable() || !checkItem(item.getItem().getItemId())))) { // 용량 확인
			return SIZE_OVER;
		}

		int weight = getWeight() + item.getItem().getWeight() * count / 1000 + 1;
		if (weight > (MAX_WEIGHT * Config.RATE_WEIGHT_LIMIT_PET)) { // 그 외의 중량 확인(주로 사몬과 애완동물)
			return WEIGHT_OVER;
		}

		ItemInstance itemExist = findItemId(item.getItemId());
		if (itemExist != null && (itemExist.getCount() + count) > MAX_AMOUNT) {
			return AMOUNT_OVER;
		}
		
		return OK;
	}

	// 새로운 아이템의 격납
	public ItemInstance storeItem(int id, int count) {
		L1Item temp = ItemTable.getInstance().getTemplate(id);
		if (temp == null) {
			return null;
		}

		if (temp.isStackable()) {
			ItemInstance item = new ItemInstance(temp, count); 
			if (findItemId(id) == null) { // 새롭게 생성할 필요가 있는 경우만 ID의 발행과 L1World에의 등록을 실시한다
				item.setId(IdFactory.getInstance().nextId());
				item.setCount(count);
				item.setBless(temp.getBless());
				item.setAttrEnchantLevel(0);
				if (!temp.isStackable() || findItemId(id) == null) {// 새롭게 생성할 필요가 있는 경우만 ID의 발행과 L1World에의 등록을 실시한다
					item.setId(IdFactory.getInstance().nextId());
					L1World.getInstance().storeObject(item);
				}
			}

			return storeItem(item);
		}

		// 스택 할 수 없는 아이템의 경우
		ItemInstance result = null;
		for (int i = 0; i < count; i++) {
			ItemInstance item = new ItemInstance(temp, 1);
			item.setId(IdFactory.getInstance().nextId());
			item.setBless(temp.getBless());
			item.setAttrEnchantLevel(0);
			L1World.getInstance().storeObject(item); 
			storeItem(item);
			result = item;
		}
		// 마지막에 만든 아이템을 돌려준다. 배열을 되돌리도록(듯이) 메소드 정의를 변경하는 편이 좋을지도 모른다.
		return result;
	}

	// DROP, 구입, GM커멘드로 입수한 새로운 아이템의 격납
	public synchronized ItemInstance storeItemBless(ItemInstance item) {
		if (item.getCount() <= 0) {
			return null;
		}
		
		if (item.isStackable()) {
			ItemInstance findItem = findItemId(item.getItem().getItemId());
			if (findItem != null) {
				findItem.setCount(findItem.getCount() + item.getCount());
				updateItem(findItem);
				return findItem;
			}
		}
		item.setX(getX());
		item.setY(getY());
		item.setMap(getMapId());
		int chargeCount = item.getItem().getMaxChargeCount();
		if (item.getItem().getItemId() == 40006 || item.getItem().getItemId() == 40007
				|| item.getItem().getItemId() == 40008 || item.getItem().getItemId() == 140006
				|| item.getItem().getItemId() == 140008 || item.getItem().getItemId() == 41401) {
			Random random = new Random();
			chargeCount -= random.nextInt(5);
		}
		if (item.getItem().getItemId() == 20383) {
			chargeCount = 50;
		}
		item.setChargeCount(chargeCount); 
		if (item.getItem().getType2() == 0 && item.getItem().getType() == 2) { // light
			item.setRemainingTime(item.getItem().getLightFuel());
		} else {
			item.setRemainingTime(item.getItem().getMaxUseTime());
		}
		
		item.setBless(item.getBless());
		_items.add(item);
		insertItem(item);
		return item;
	}
	
	public synchronized ItemInstance storeItem(ItemInstance item) {
		if (item.getCount() <= 0) {
			return null;
		}
		if (item.isStackable()) {
			ItemInstance findItem = findItemId(item.getItem().getItemId());
			if (findItem != null) {
				findItem.setCount(findItem.getCount() + item.getCount());
				updateItem(findItem);
				return findItem;
			}
		}
		item.setX(getX());
		item.setY(getY());
		item.setMap(getMapId());
		int chargeCount = item.getItem().getMaxChargeCount();
		if (item.getItem().getItemId() == 40006 || item.getItem().getItemId() == 40007
				|| item.getItem().getItemId() == 40008 || item.getItem().getItemId() == 140006
				|| item.getItem().getItemId() == 140008 || item.getItem().getItemId() == 41401) {
			Random random = new Random();
			chargeCount -= random.nextInt(5);
		}
		if (item.getItem().getItemId() == 20383) {
			chargeCount = 50;
		}
		item.setChargeCount(chargeCount); 
		if (item.getItem().getType2() == 0 && item.getItem().getType() == 2) { // light
			item.setRemainingTime(item.getItem().getLightFuel());
		} else {
			item.setRemainingTime(item.getItem().getMaxUseTime());
		}
		
		item.setBless(item.getItem().getBless());
		_items.add(item);
		insertItem(item);
		return item;
	}
	
	public synchronized ItemInstance storeItem(int id, int count, int enchant) {
		if (count <= 0) {
			return null;
		}
		
		L1Item temp = ItemTable.getInstance().getTemplate(id);
		if (temp == null) {
			return null;
		}
		if (temp.isStackable()) {
			ItemInstance item = new ItemInstance(temp, count);
			if (findItemId(id) == null) {
				item.setId(IdFactory.getInstance().nextId());
				L1World.getInstance().storeObject(item);
			}
			return storeItem(item);
		}

		ItemInstance result = null;
		ItemInstance item = null;
		for (int i = 0; i < count; i++) {
			item = new ItemInstance(temp, 1);
			item.setId(IdFactory.getInstance().nextId());
			item.setEnchantLevel(enchant);
			item.setIdentified(true);
			L1World.getInstance().storeObject(item);
			storeItem(item);
			result = item;
		}
		return result;
	}
	 

	// /trade, 창고로부터 입수한 아이템의 격납
	public synchronized ItemInstance storeTradeItem(ItemInstance item) {
		 
		if (item.isStackable()) {
			ItemInstance findItem = findItemId(item.getItem().getItemId());
			if (findItem != null) {
				findItem.setCount(findItem.getCount() + item.getCount());
				updateItem(findItem); 
				return findItem;
			}
		}
		item.setX(getX());
		item.setY(getY());
		item.setMap(getMapId());
		_items.add(item);
		insertItem(item);
		return item;
	}

	/**
	 * 목록으로부터 지정된 아이템 ID의 아이템을 삭제한다. L1ItemInstance에의 참조
	 * (이)가 있는 경우는 removeItem의 (분)편을 사용하는 것이 좋다.  (이쪽은 화살이라든지 마석이라든지 특정의 아이템을 소비시킬 때 사용한다)
	 * 
	 * @param itemid -
	 *            삭제하는 아이템의 itemid(objid는 아니다)
	 * @param count -
	 *            삭제하는 개수
	 * @return 실제로 삭제되었을 경우는 true를 돌려준다.
	 */
	
	public boolean consumeItem(int itemid) {
		ItemInstance item = findItemId(itemid);
		if (item != null) {
			removeItem(item, item.getCount());
			return true;
		}
		return false;
	}
	
	public boolean consumeItem(int itemid, int count) {
		L1Item tem = ItemTable.getInstance().getTemplate(itemid);
		if (tem == null) {
			return false;
		}
		if (count <= 0) {
			return false;
		}
		if (tem.isStackable()) {
			ItemInstance item = findItemId(itemid);
			if (item != null && item.getCount() >= count) {
				removeItem(item, count);
				return true;
			}
		} else {
			ItemInstance[] itemList = findItemsId(itemid);
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
	
    public boolean consumeItem(ItemInstance item, int count) {
        ItemInstance find = findItemObjId(item.getId());
        if (find == null || find.getCount() < count) {
            return false;
        }
        removeItem(item, count);
        return true;
    }
	
	public boolean consumeItem(ItemInstance[] items, int[] counts) {
		int size = items.length;
		for (int i = size - 1; i >= 0; --i) {
			ItemInstance item = items[i];
			int count = counts[i];
			ItemInstance find = findItemObjId(item.getId());
			if (find == null || find.getCount() < count)
				return false;
		}

		for (int i = size - 1; i >= 0; --i) {
			removeItem(items[i], counts[i]);
		}
		return true;
	}
	
	public ItemInstance findItemObjId(int id) {
		for (ItemInstance item : this._items) {
			if (item == null)
				continue;
			if (item.getId() == id) {
				return item;
			}
		}
		return null;
	}

	@SuppressWarnings("rawtypes")
	public class DataComparator implements java.util.Comparator {
		public int compare(Object item1, Object item2) {
			return ((ItemInstance) item1).getEnchantLevel() - ((ItemInstance) item2).getEnchantLevel();
		}
	}

	// 지정한 아이템으로부터 지정 개수를 삭제(사용하거나 쓰레기통에 버려졌을 때) 반환값：실제로 삭제한 수
	public int removeItem(int objectId) {
		ItemInstance item = getItem(objectId);
		return removeItem(item, item.getCount());
	}

	public int removeItem(int objectId, int count) {
		ItemInstance item = getItem(objectId);
		return removeItem(item, count);
	}
	public int removeItem(ItemInstance item) {
		return removeItem(item, item.getCount());
	}

	public int removeItem(ItemInstance item, int count) {
		if (item == null) {
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
			if (itemId == 40314 || itemId == 40316) { // 펫 목걸이
				PetTable.getInstance().deletePet(item.getId());
			} else if (itemId >= 49016 && itemId <= 49025) { // 편지지
				LetterTable lettertable = new LetterTable();
				lettertable.deleteLetter(item.getId());
			} else if (itemId >= 41383 && itemId <= 41400) { // 가구
				for (L1Object l1object : L1World.getInstance().getObject()) {
					if (l1object == null)
						continue;
					if (l1object instanceof FurnitureInstance) {
						FurnitureInstance furniture = (FurnitureInstance) l1object;
						if (furniture.getItemObjId() == item.getId()) { // 이미 꺼내고 있는 가구
							FurnitureSpawnTable.getInstance()
									.deleteFurniture(furniture);
						}
					}
				}
			}
			deleteItem(item);
			L1World.getInstance().removeObject(item);
		} else {
			item.setCount(item.getCount() - count);
			updateItem(item);
		}
		return count;
	}

	// _items로부터 지정 오브젝트를 삭제(L1PcInstance, L1DwarfInstance, L1GroundInstance로 이 부분을 오바라이드 한다)
	public void deleteItem(ItemInstance item) {
		_items.remove(item);
	}

	public synchronized ItemInstance tradeItem(int objectId, int count, Warehouse inventory) {
		ItemInstance item = getItem(objectId);
		 
		return tradeItem(item, count, inventory);
	}
	
	// 인수의 인벤트리에 아이템을 이양
	public synchronized ItemInstance tradeItem(int objectId, int count, L1Inventory inventory) {
		ItemInstance item = getItem(objectId);
		return tradeItem(item, count, inventory);
	}
	
	public synchronized ItemInstance tradeItem(ItemInstance item, int count, Warehouse inventory) {
		if (item == null) {
			return null;
		}
		if (item.getCount() <= 0 || count <= 0) {
			return null;
		}
		if (item.isEquipped()) {
			return null;
		}
		if (!checkItem(item.getItem().getItemId(), count)) {
			return null;
		}
		 

		
		ItemInstance carryItem;
		// 엔진관련 버그 방지 추가
		if (item.getCount() <= count || count < 0) {
			deleteItem(item);
			carryItem = item;
		} else {
			
			
			item.setCount(item.getCount() - count);
			updateItem(item);
			carryItem = ItemTable.getInstance().createItem(item.getItem().getItemId());
			carryItem.setCount(count);
			carryItem.setEnchantLevel(item.getEnchantLevel());
			carryItem.setIdentified(item.isIdentified());
			carryItem.setDurability(item.getDurability());
			carryItem.setChargeCount(item.getChargeCount());
			carryItem.setRemainingTime(item.getRemainingTime());
			carryItem.setLastUsed(item.getLastUsed());
			carryItem.setBless(item.getItem().getBless());
			carryItem.setAttrEnchantLevel(item.getAttrEnchantLevel());
			carryItem.setBlessType(item.getBlessType());
			carryItem.setBlessTypeValue(item.getBlessTypeValue());
		}
		return inventory.storeTradeItem(carryItem);
	}

	public ItemInstance tradeItem(ItemInstance item, int count, L1Inventory inventory) {
		if (item == null) {
			return null;
		}
		if (item.getCount() <= 0 || count <= 0) {
			return null;
		}
		if (item.isEquipped()) {
			return null;
		}
		if (!checkItem(item.getItem().getItemId(), count)) {
			return null;
		}
		if (item.getCount() < count) {
			return null;
		}
		int over_count = count + inventory.countItems(40308);
		if (over_count > 1800000000) {
			if (inventory.consumeItem(40308, 1500000000)) {
				inventory.storeItem(46010, 15);
			}
		}
		ItemInstance carryItem;
		if (item.getCount() <= count || count < 0) {
			deleteItem(item);
			carryItem = item;
		} else {
			item.setCount(item.getCount() - count);
			updateItem(item);
			carryItem = ItemTable.getInstance().createItem(item.getItem().getItemId());
			carryItem.setCount(count);
			carryItem.setEnchantLevel(item.getEnchantLevel());
			carryItem.setIdentified(item.isIdentified());
			carryItem.setDurability(item.getDurability());
			carryItem.setChargeCount(item.getChargeCount());
			carryItem.setLastUsed(item.getLastUsed());
			carryItem.setBless(item.getItem().getBless());
			carryItem.setAttrEnchantLevel(item.getAttrEnchantLevel());
			carryItem.setEndTime(item.getEndTime());
			carryItem.setBlessType(item.getBlessType());
			carryItem.setBlessTypeValue(item.getBlessTypeValue());
			
		}
		return inventory.storeTradeItem(carryItem);
	}

	/*
	 * 아이템을 손상·손모시키는(무기·방어용 기구도 포함한다) 아이템의 경우, 손모이므로 마이너스 하지만 무기·방어용 기구는 손상도를 나타내므로 플러스로 한다.
	 */
	public ItemInstance receiveDamage(int objectId) {
		ItemInstance item = getItem(objectId);
		return receiveDamage(item);
	}

	public ItemInstance receiveDamage(ItemInstance item) {
		return receiveDamage(item, 1);
	}

	public ItemInstance receiveDamage(ItemInstance item, int count) {
		if (item == null) {
			return null;
		}
		int itemType = item.getItem().getType2();
		int currentDurability = item.getDurability(); 

		if ((currentDurability == 0 && itemType == 0) || currentDurability < 0) {
			item.setDurability(0);
			return null;
		}

		// 무기·방어용 기구만 손상도를 플러스
		if (itemType == 0) {
			int minDurability = (item.getEnchantLevel() + 5) * -1;
			int durability = currentDurability - count;
			if (durability < minDurability) {
				durability = minDurability;
			}
			if (currentDurability > durability) {
				item.setDurability(durability);
			}
		} else {
			int maxDurability = item.getEnchantLevel() + 5;
			int durability = currentDurability + count;
			if (durability > maxDurability) {
				durability = maxDurability;
			}
			if (currentDurability < durability) {
				item.setDurability(durability);
			}
		}

		updateItem(item, L1PcInventory.COL_DURABILITY);
		return item;
	}

	public ItemInstance recoveryDamage(ItemInstance item) {
		if (item == null) {
			return null;
		}
		int itemType = item.getItem().getType2();
		int durability = item.getDurability(); 

		if ((durability == 0 && itemType != 0) || durability < 0) {
			item.setDurability(0);
			return null;
		}

		if (itemType == 0) {
			// 내구도를 플러스 하고 있다.
			item.setDurability(durability + 1);
		} else {
			// 손상도를 마이너스 하고 있다.
			item.setDurability(durability - 1);
		}

		updateItem(item, L1PcInventory.COL_DURABILITY);
		return item;
	}

	// 아이템 ID로부터 검색
	public ItemInstance findItemId(int id) {
		for (ItemInstance item : _items) { 
			if (item.getItem().getItemId() == id) {
				return item;
			}
		}
		return null;
	}

	public ItemInstance[] findItemsId(int id) {
		ArrayList<ItemInstance> itemList = new ArrayList<ItemInstance>();
		for (ItemInstance item : _items) {
			if (item.getItemId() == id) {
				itemList.add(item);
			}
		}
		return itemList.toArray(new ItemInstance[] {});
	}
	public ItemInstance[] findItemsIdNotEquipped(int id) {
		ArrayList<ItemInstance> itemList = new ArrayList<ItemInstance>();
		for (ItemInstance item : _items) {
			if (item.getItemId() == id) {
				if (!item.isEquipped()) {
					itemList.add(item);
				}
			}
		}
		return itemList.toArray(new ItemInstance[] {});
	}

	// 오브젝트 ID로부터 검색
	public ItemInstance getItem(int objectId) {
		for (Object itemObject : _items) {
			ItemInstance item = (ItemInstance) itemObject;
			if (item.getId() == objectId) {
				return item;
			}
		}
		return null;
	}

	// 특정의 아이템이 지정된 개수 이상 소지하고 있을까 확인(화살이라든지 마석의 확인)
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
		if (ItemTable.getInstance().getTemplate(id).isStackable()) {
			ItemInstance item = findItemId(id);
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

	// 특정의 아이템이 지정된 개수 이상 소지하고 있을까 확인
	// 장비중의 아이템은 소지하고 있지 않으면 판별한다
	public boolean checkItemNotEquipped(int id, int count) {
		if (count == 0) {
			return true;
		}
		return count <= countItems(id);
	}

	// 특정의 아이템을 모두 필요한 개수 소지하고 있을까 확인(이벤트등으로 복수의 아이템을 소지하고 있을까 확인하기 위해(때문에))
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

	/**
	 * 이 인벤트리내에 있는, 지정된 ID의 아이템의 수를 센다.
	 * 
	 * @return
	 */
	public int countItems(int id) {
		if (ItemTable.getInstance().getTemplate(id).isStackable()) {
			ItemInstance item = findItemId(id);
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
		Collections.shuffle(_items);
	}

	// 인벤트리내의 모든 아이템을 지운다(소유자를 지울 때 등)
	public void clearItems() {
		for (Object itemObject : _items) {
			ItemInstance item = (ItemInstance) itemObject;
			L1World.getInstance().removeObject(item);
		}
		_items.clear();
	}

	// 오버라이드(override)용
	public void loadItems() {
	}

	public void insertItem(ItemInstance item) {
	}

	public void updateItem(ItemInstance item) {
	}

	public void updateItem(ItemInstance item, int colmn) {
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

		ItemInstance itemExist = findItemId(item.getItemId());
		if (itemExist != null && ((itemExist.getCount() + count) < 0 || (itemExist.getCount() + count) > MAX_AMOUNT))
			return AMOUNT_OVER;
		
		return OK;
	}
	@SuppressWarnings("unchecked")
	private void extracted(ItemInstance[] itemList, DataComparator dc) {
		Arrays.sort(itemList, dc);
	}
	
	public ItemInstance[] findItemsIdNotEquippedCraftBless(int id, int enchant, int bless) {
		ArrayList<ItemInstance> itemList = new ArrayList<ItemInstance>();
		for (ItemInstance item : _items) {
			if (item == null)
				continue;
			if (item.getItemId() == id) {
				if (!item.isEquipped() && item.getEnchantLevel() == enchant && item.getBless() == bless) {
					itemList.add(item);
				}
			}
		}
		return itemList.toArray(new ItemInstance[] {});
	}
	
	public ItemInstance[] findItemsIdNotEquippedBless(int id, int bless) {
		ArrayList<ItemInstance> itemList = new ArrayList<ItemInstance>();
		for (ItemInstance item : _items) {
			if (item.getItemId() == id && item.getBless() == bless) {
				if (!item.isEquipped()) {
					itemList.add(item);
				}
			}
		}
		return itemList.toArray(new ItemInstance[] {});
	}
	
	public ItemInstance findItemIdBless(int id, int bless) {
		for (ItemInstance item : _items) {
			if (item.getItem().getItemId() == id && item.getBless() == bless) {
				return item;
			}
		}
		return null;
	}
}
