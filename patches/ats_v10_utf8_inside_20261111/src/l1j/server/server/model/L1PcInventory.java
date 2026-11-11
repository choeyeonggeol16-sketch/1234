/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
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

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.InvenBonusItem.InvenBonusItemInfo;
import l1j.server.InvenBonusItem.InvenBonusItemLoader;
import l1j.server.Templates.L1Item;
import l1j.server.server.datatables.NoDropItem;
import l1j.server.server.model.Instance.DollInstance;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.PetInstance;
import l1j.server.server.model.item.ItemId;
import l1j.server.server.serverpackets.S_AddItem;
import l1j.server.server.serverpackets.S_CharVisualUpdate;
import l1j.server.server.serverpackets.S_DeleteInventoryItem;
import l1j.server.server.serverpackets.S_ItemColor;
import l1j.server.server.serverpackets.S_ItemName;
import l1j.server.server.serverpackets.S_ItemStatus;
import l1j.server.server.serverpackets.S_OwnCharStatus;
import l1j.server.server.serverpackets.S_PacketBox;
import l1j.server.server.serverpackets.S_ServerMessage;
import l1j.server.server.serverpackets.S_SystemMessage;
import l1j.server.server.storage.CharactersItemStorage;
import l1j.server.server.utils.MessageUtil;

public class L1PcInventory extends L1Inventory {

	private static final long serialVersionUID = 1L;

	private static Logger _log = Logger.getLogger(L1PcInventory.class.getName());

	private static final int MAX_SIZE = 180;

	private final L1PcInstance _owner; // 소유자 플레이어

	private int _arrowId; // 우선해 사용되는 아로의 ItemID

	private int _stingId; // 우선해 사용되는 스팅의 ItemID

	public L1PcInventory(L1PcInstance owner) {
		_owner = owner;
		_arrowId = 0;
		_stingId = 0;
	}

	public L1PcInstance getOwner() {
		return _owner;
	}
	
	public int getWeight100() {
		return calcWeight100(getWeight());
	}

    public int calcWeight100(int weight) {
    	return weight * 100 / _owner.getMaxWeight();
	}
 
	@Override
	public int checkAddItem(L1Item item, int count) {
		int code = super.checkAddItem(item, count);
		if (code == OK) {
			int weight = getWeight() + item.getWeight() * count / 1000 + 1;
			if (calcWeight100(weight) >= 240)
				return WEIGHT_OVER;
		}
		return code;
	}

	@Override
	public int checkAddItem(ItemInstance item, int count) {
		return checkAddItem(item, count, true);
	}

	public int checkAddItem(ItemInstance item, int count, boolean message) {
		if (item == null) {
			return -1;
		}
		if (getSize() > MAX_SIZE || (getSize() == MAX_SIZE && (!item.isStackable() || !checkItem(item.getItem().getItemId())))) { // 용량  확인
			if (message) {
				sendOverMessage(MessageUtil.MESSAGE_NUMBER_28);
			}
			return SIZE_OVER;
		}

		int weight = getWeight() + item.getItem().getWeight() * count / 1000 + 1;
		if (weight < 0 || (item.getItem().getWeight() * count / 1000) < 0) {
			if (message) {
				sendOverMessage(MessageUtil.MESSAGE_NUMBER_26);
			}
			return WEIGHT_OVER;
		}
		if (calcWeight100(weight) >= 100) {
			if (message) {
				sendOverMessage(MessageUtil.MESSAGE_NUMBER_26);
			}
			return WEIGHT_OVER;
		}

		ItemInstance itemExist = findItemId(item.getItemId());
		if (itemExist != null && (itemExist.getCount() + count) > MAX_AMOUNT) {
			
			if (message) { 
				getOwner().sendPackets(new S_SystemMessage("아데나가 20억 초과합니다. 수표로 변환해주세요."));
			}
			return AMOUNT_OVER;
		}
		
		return OK;
	}

	public void sendOverMessage(int message_id) {
		_owner.sendPackets(new S_ServerMessage(message_id), true);
	}

	// DB의 character_items의 독입
	@Override
	public void loadItems() {
		try {
			CharactersItemStorage storage = CharactersItemStorage.create();

			for (ItemInstance item : storage.loadItems(_owner.getId())) {
				if (item.getItemId() == ItemId.아데나) {
					ItemInstance itemExist = findItemId(item.getItemId());

					if (itemExist != null) {
						storage.deleteItem(item); 
						int newCount = itemExist.getCount() + item.getCount(); 
						if (newCount <= MAX_AMOUNT) {
							if (newCount < 0) {
								newCount = 0;
							}
							itemExist.setCount(newCount); 
							storage.updateItemCount(itemExist);
						}
					} else {
						_items.add(item);
						L1World.getInstance().storeObject(item);
					}
				} else {
                	InvenBonusItemInfo info = InvenBonusItemLoader.getInstance().getInvenBonusItemInfo(item.getItemId());
					if (info != null) {
						InvenBonusItemInfo.inven_option(_owner, item.getItemId(), true);
					}
					_items.add(item);
					L1World.getInstance().storeObject(item);
				}

				if (item.isEquipped()) {
					item.setEquipped(false);
					setEquipped(item, true, true, false);
				}
			}
		} catch (Exception e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		}
	}

	// DB의 character_items에 등록
	@Override
	public void insertItem(ItemInstance item) {
		_owner.sendPackets(new S_AddItem(_owner, item));
		if (item.getItem().getWeight() != 0) { 
			int current_weight = (int) (getWeight100() * 2.4);
			_owner.sendPackets(new S_PacketBox(S_PacketBox.WEIGHT, current_weight));
		} 
		
		//인벤 아이템<
		InvenBonusItemInfo info = InvenBonusItemLoader.getInstance().getInvenBonusItemInfo(item.getItemId());
		if (info != null) {
			InvenBonusItemInfo.inven_option(_owner, item.getItemId(), true);
		} 	
		try {
			CharactersItemStorage storage = CharactersItemStorage.create();
			storage.storeItem(_owner.getId(), item);
		} catch (Exception e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		}
		 
	}

	public static final int COL_SAVE_ALL = 2048 * 16;
	
	public static final int COL_BLESS_SCROLL = 2048 * 2;
	 
	public static final int COL_ATTRENCHANTLVL = 1024;

	public static final int COL_BLESS = 512;
	
	public static final int COL_REMAINING_TIME = 256;

	public static final int COL_CHARGE_COUNT = 128;

	public static final int COL_ITEMID = 64;

	public static final int COL_DELAY_EFFECT = 32;

	public static final int COL_COUNT = 16;

	public static final int COL_EQUIPPED = 8;

	public static final int COL_ENCHANTLVL = 4;

	public static final int COL_IS_ID = 2;

	public static final int COL_DURABILITY = 1;

	@Override
	public void updateItem(ItemInstance item) {
		updateItem(item, COL_COUNT);
		if (item.getItem().isToBeSavedAtOnce()) {
			saveItem(item, COL_COUNT);
		}
	}

	/**
	 * 목록내의 아이템 상태를 갱신한다.
	 * 
	 * @param item -
	 *            갱신 대상의 아이템
	 * @param column -
	 *            갱신하는 스테이터스의 종류
	 */
	@Override
	public void updateItem(ItemInstance item, int column) {
		if (column >= COL_ATTRENCHANTLVL) {
			_owner.sendPackets(new S_ItemName(item));
			column -= COL_ATTRENCHANTLVL;
		}
		if (column >= COL_BLESS_SCROLL) {
			_owner.sendPackets(new S_ItemStatus(_owner, item));
			_owner.sendPackets(new S_ItemColor(item));
			column -= COL_BLESS_SCROLL;
		}
		if (column >= COL_BLESS) {
			_owner.sendPackets(new S_ItemColor(item));
			column -= COL_BLESS;
		}
		if (column >= COL_REMAINING_TIME) { 
			_owner.sendPackets(new S_ItemName(item));
			column -= COL_REMAINING_TIME;
		}
		if (column >= COL_CHARGE_COUNT) { // 요금수
			_owner.sendPackets(new S_ItemName(item));
			column -= COL_CHARGE_COUNT;
		}
		if (column >= COL_ITEMID) { // 별의 아이템이 되는 경우(편지지를 개봉했을 때 등)
			_owner.sendPackets(new S_ItemStatus(_owner, item));
			_owner.sendPackets(new S_ItemColor(item));
			int current_weight = (int) (getWeight100() * 2.4);
			_owner.sendPackets(new S_PacketBox(S_PacketBox.WEIGHT, current_weight));		
			column -= COL_ITEMID;
		}
		if (column >= COL_DELAY_EFFECT) { // 효과 지연
			column -= COL_DELAY_EFFECT;
		}
		if (column >= COL_COUNT) { // 카운트
			//_owner.sendPackets(new S_ItemAmount(item));
			_owner.sendPackets(new S_ItemStatus(_owner, item));

			int weight = item.getWeight();
			if (weight != item.getLastWeight()) {
				item.setLastWeight(weight);
				_owner.sendPackets(new S_ItemStatus(_owner, item));
			} else {
				_owner.sendPackets(new S_ItemName(item));
			}
			if (item.getItem().getWeight() != 0) {  
				int current_weight = (int) (getWeight100() * 2.4);
				_owner.sendPackets(new S_PacketBox(S_PacketBox.WEIGHT, current_weight));		
			} 
			column -= COL_COUNT;
		}
		if (column >= COL_EQUIPPED) { // 장비 상태
			_owner.sendPackets(new S_ItemName(item));
			column -= COL_EQUIPPED;
		}
		if (column >= COL_ENCHANTLVL) { // 인챈트
			_owner.sendPackets(new S_ItemStatus(_owner, item));
			column -= COL_ENCHANTLVL;
		}
		if (column >= COL_IS_ID) { // 확인 상태
			_owner.sendPackets(new S_ItemStatus(_owner, item));
			_owner.sendPackets(new S_ItemColor(item));
			column -= COL_IS_ID;
		}
		if (column >= COL_DURABILITY) { // 내구성
			_owner.sendPackets(new S_ItemStatus(_owner, item));
			column -= COL_DURABILITY;
		}
	}

	/**
	 * 목록내의 아이템 상태를 DB에 보존한다.
	 * 
	 * @param item -
	 *            갱신 대상의 아이템
	 * @param column -
	 *            갱신하는 스테이터스의 종류
	 */
	public void saveItem(ItemInstance item, int column) {
		if (column == 0) {
			return;
		}

		try {
			CharactersItemStorage storage = CharactersItemStorage.create();		
			if (column >= COL_SAVE_ALL) {
				storage.updateItemAll(item);
				return;
			}
			if (column >= COL_BLESS_SCROLL) { // 엔챤트 
				storage.updateBlessType(item);
				storage.updateBlessTypeValue(item);
				column -= COL_BLESS_SCROLL;
			}
			if (column >= COL_ATTRENCHANTLVL) {
				storage.updateItemAttrEnchantLevel(item);
				column -= COL_ATTRENCHANTLVL;
			}
			if (column >= COL_BLESS) {
				storage.updateItemBless(item);
				column -= COL_BLESS;
			} 
			if (column >= COL_REMAINING_TIME) {
				storage.updateItemRemainingTime(item);
				column -= COL_REMAINING_TIME;
			}
			if (column >= COL_CHARGE_COUNT) { // 요금수
				storage.updateItemChargeCount(item);
				column -= COL_CHARGE_COUNT;
			}	
			if (column >= COL_ITEMID) { // 별의 아이템이 되는 경우(편지지를 개봉했을 때 등)
				storage.updateItemId(item);
				column -= COL_ITEMID;
			}
			if (column >= COL_DELAY_EFFECT) { // 효과 지연
				storage.updateItemDelayEffect(item);
				column -= COL_DELAY_EFFECT;
			}
			if (column >= COL_COUNT) { // 카운트
				storage.updateItemCount(item);
				column -= COL_COUNT;
			}
			if (column >= COL_EQUIPPED) { // 장비 상태
				storage.updateItemEquipped(item);
				column -= COL_EQUIPPED;
			}
			if (column >= COL_ENCHANTLVL) { // 엔챤트
				storage.updateItemEnchantLevel(item);
				column -= COL_ENCHANTLVL;
			}
			if (column >= COL_IS_ID) { // 확인 상태
				storage.updateItemIdentified(item);
				column -= COL_IS_ID;
			}
			if (column >= COL_DURABILITY) { // 내구성
				storage.updateItemDurability(item);
				column -= COL_DURABILITY;
			}
		} catch (Exception e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		}
	}

	// DB의 character_items로부터 삭제
	@Override
	public void deleteItem(ItemInstance item) {

		try {
			CharactersItemStorage storage = CharactersItemStorage.create();
			storage.deleteItem(item);
		} catch (Exception e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		}
		if (item.isEquipped()) {
			setEquipped(item, false);
		}
		_owner.sendPackets(new S_DeleteInventoryItem(item));
		_items.remove(item);
		if (item.getItem().getWeight() != 0) { 
			int current_weight = (int) (getWeight100() * 2.4);
			_owner.sendPackets(new S_PacketBox(S_PacketBox.WEIGHT, current_weight));
		}
		
		InvenBonusItemInfo info = InvenBonusItemLoader.getInstance().getInvenBonusItemInfo(item.getItemId());
		if (info != null) {
			InvenBonusItemInfo.inven_option(_owner, item.getItemId(), false);
		} 
	}

	// 아이템을 장착 탈착시킨다(L1ItemInstance의 변경, 보정치의 설정, character_items의 갱신, 패킷 송신까지 관리)
	public void setEquipped(ItemInstance item, boolean equipped) {
		setEquipped(item, equipped, false, false);
	}

	public void setEquipped(ItemInstance item, boolean equipped, boolean loaded, boolean changeWeapon) {
		if (item.isEquipped() != equipped) { // 설정치와 다른 경우만 처리
			L1Item temp = item.getItem();
			if (equipped) { // 장착
				item.setEquipped(true);
				_owner.getEquipSlot().set(item);
			} else { // 탈착
				if (!loaded) {
					if (temp.getItemId() == ItemId.투명_망토 || temp.getItemId() == ItemId.발록_핏빛_망토 || temp.getItemId() == ItemId.축복_투명_망토) {
						if (_owner.isInvisble()) {
							_owner.delInvis();
							return;
						}
					}
				}
				item.setEquipped(false);
				_owner.getEquipSlot().remove(item);
			}
			if (!loaded) { // 최초의 독입시는 DB패킷 관련의 처리는 하지 않는다
				// XXX:의미가 없는 셋타
				_owner.setCurrentHp(_owner.getCurrentHp());
				_owner.setCurrentMp(_owner.getCurrentMp());
				updateItem(item, COL_EQUIPPED);
				_owner.sendPackets(new S_OwnCharStatus(_owner));
				if (temp.getType2() == L1Item.CATEGORY_WEAPON && !changeWeapon) { // 무기의 경우는 비주얼 갱신.다만, 무기의 가져 바꾸고로 무기를 탈착할 때는 갱신하지 않는다
					S_CharVisualUpdate ss = S_CharVisualUpdate.get(_owner);
					_owner.sendPackets(ss);
					_owner.broadcastPacket(ss);
				}
			}
		}
	}
	// 특정의 타입의 아이템을 장비 하고 있는 수
	public int getTypeEquipped(int type2, int type) {
		int equipeCount = 0;
		for (Object itemObject : _items) {
			ItemInstance item = (ItemInstance) itemObject;
			if (item.getItem().getType2() == type2 && item.getItem().getType() == type && item.isEquipped()) {
				equipeCount++;
			}
		}
		return equipeCount;
	}

	// 장비 하고 있는 특정의 타입의 아이템
	public ItemInstance getItemEquipped(int type2, int type) {
		ItemInstance equipeitem = null;
		for (Object itemObject : _items) {
			ItemInstance item = (ItemInstance) itemObject;
			if (item.getItem().getType2() == type2
					&& item.getItem().getType() == type && item.isEquipped()) {
				equipeitem = item;
				break;
			}
		}
		return equipeitem;
	}

	// 변신시에 장비 할 수 없는 장비를 제외한다
	public void takeoffEquip(int polyid) {
		takeoffWeapon(polyid);
		takeoffArmor(polyid);
	}

	// 변신시에 장비 할 수 없는 무기를 빗나가게 한다
	private void takeoffWeapon(int polyid) {
		if (_owner.getWeapon() == null) { // 맨손
			return;
		}
		boolean takeoff = false;
		int weapon_type = _owner.getWeapon().getItem().getType();
		takeoff = !L1PolyMorph.isEquipableWeapon(polyid, weapon_type);
		if (takeoff) {
			setEquipped(_owner.getWeapon(), false, false, false);
		}
	}

	// 변신시에 장비 할 수 없는 방어용 기구를 제외한다
	private void takeoffArmor(int polyid) {
		ItemInstance armor = null;
		for (int type = 0; type <= 12; type++) {

			if (getTypeEquipped(L1Item.CATEGORY_ARMOR, type) != 0
					&& !L1PolyMorph.isEquipableArmor(polyid, type)) {
				if (type == L1Item.TYPE_ARMOR_RING) {
					armor = getItemEquipped(L1Item.CATEGORY_ARMOR, type);
					if (armor != null) {
						setEquipped(armor, false, false, false);
					}
					armor = getItemEquipped(L1Item.CATEGORY_ARMOR, type);
					if (armor != null) {
						setEquipped(armor, false, false, false);
					}
				} else {
					armor = getItemEquipped(L1Item.CATEGORY_ARMOR, type);
					if (armor != null) {
						setEquipped(armor, false, false, false);
					}
				}
			}
		}
	}

	// 사용하는 아로의 취득
	public ItemInstance getArrow() {
		return getBullet(0);
	}

	// 사용하는 스팅의 취득
	public ItemInstance getSting() {
		return getBullet(15);
	}

	private ItemInstance getBullet(int type) {
		ItemInstance bullet;
		int priorityId = 0;
		if (type == 0) {
			priorityId = _arrowId; // 아로
		}
		if (type == 15) {
			priorityId = _stingId; // 스팅
		}
		if (priorityId > 0) // 우선하는 총알이 있을까
		{
			bullet = findItemId(priorityId);
			if (bullet != null) {
				return bullet;
			} else // 없어져 있었을 경우는 우선을 지운다
			{
				if (type == 0) {
					_arrowId = 0;
				}
				if (type == 15) {
					_stingId = 0;
				}
			}
		}

		for (Object itemObject : _items) // 탄을 찾는다
		{
			bullet = (ItemInstance) itemObject;
			if (bullet.getItem().getType() == type) {
				if (type == 0) {
					_arrowId = bullet.getItem().getItemId(); // 우선으로 해 둔다
				}
				if (type == 15) {
					_stingId = bullet.getItem().getItemId(); // 우선으로 해 둔다
				}
				return bullet;
			}
		}
		return null;
	}

	// 우선하는 아로의 설정
	public void setArrow(int id) {
		_arrowId = id;
	}

	// 우선하는 스팅의 설정
	public void setSting(int id) {
		_stingId = id;
	}
	
	public ItemInstance CaoPenalty() {
	//	Random random = new Random();
		int rnd = ThreadLocalRandom.current().nextInt(_items.size());
		
		if(rnd <= 0) {
			return null;
		}
		
		ItemInstance penaltyItem = _items.get(rnd);
		
		if(penaltyItem == null) {
			return null;
		}

		if (penaltyItem.getItem().getItemId() == ItemId.아데나
				|| NoDropItem.getInstance().isNoDropItem(penaltyItem.getItem().getItemId()) 
				|| !penaltyItem.getItem().isTradable() || penaltyItem.getEndTime() != null) {
			return null;
		}
		
		Object[] petlist = _owner.getPetList().values().toArray();
		for (Object petObject : petlist) {
			if (petObject instanceof PetInstance) {
				PetInstance pet = (PetInstance) petObject;
				if (penaltyItem.getId() == pet.getItemObjId()) {
					return null;
				}
			}
		}
		DollInstance doll = this._owner.getMagicDoll();
		if ((doll != null) && (penaltyItem.getId() == doll.getItemObjId())) {
			return null;
		}
		setEquipped(penaltyItem, false, false, true);
		return penaltyItem;
	}
	
	/** 조우의 돌골렘 (인챈트 아이템 삭제)
	 *
	 * @param itemid 
	 * 			- 제련시 필요한 무기번호
	 * 
	 * @param enchantLevel 
	 * 			- 제련시 필요한 무기의 인챈트레벨
	 */
	public boolean MakeDeleteEnchant(int itemid, int enchantLevel) {
		ItemInstance item = findItemId(itemid);
		if (item != null && item.getEnchantLevel() == enchantLevel) {
			removeItem(item, 1);
			return true;
		}
		return false;
	}

	/** 조우의 돌골렘 (인챈트 아이템 검사)
	 * 
	 * @param id 
	 * 			- 제련시 필요한 무기번호
	 * 	  
	 * @param enchantLevel 
	 * 			- 제련시 필요한 무기의 인챈트 레벨
	 *	 
	 */
	public boolean MakeCheckEnchant(int id, int enchantLevel) {
		ItemInstance item = findItemId(id);
		if (item != null && item.getEnchantLevel() == enchantLevel && item.getCount() == 1) {
			return true;
		}
		return false;
	}
	
	public ItemInstance findItemObjId(int id) {
		for (ItemInstance item : this._items) {
			if (item == null) {
				continue;
			}
			if (item.getId() == id) {
				return item;
			}
		}
		return null;
	}
	public int checkItemCount(int id) {
		int cnt = 0;
		ItemInstance item = null;
		for (Object itemObject : _items) {
			item = (ItemInstance) itemObject;
			if (item.getItemId() == id) {
				cnt += item.getCount();
			}
		}
		return cnt;
	}
}
