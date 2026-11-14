
package l1j.server.server.model;
import l1j.server.server.model.network.core.sc.packet.SC_CharVisualUpdate;
import l1j.server.server.model.network.core.sc.packet.SC_ItemName;
import l1j.server.server.model.network.core.sc.packet.SC_Ability;
import l1j.server.server.model.network.core.sc.packet.SC_ItemStatus;



import l1j.server.server.model.network.core.sc.packet.SC_Weight;
import l1j.server.server.model.network.core.sc.packet.SC_DeleteInventoryItem;
import l1j.server.server.model.network.core.sc.packet.SC_ItemColor;
import java.util.Arrays;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import l1j.server.MJCompanion.Instance.MJCompanionInstanceCache;
import l1j.server.server.datatables.NoDropItem;
import l1j.server.server.model.Instance.L1DollInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1PetInstance;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.character.equip.EquipType;
import l1j.server.server.model.character.skill.L1SkillId;
import LIN.SC_ADD_INVENTORY_NOTI;
import LIN.ItemInfo;
import l1j.server.server.model.network.core.sc.packet.SC_EquipmentWindow;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

import l1j.server.server.storage.CharactersItemStorage;
import l1j.server.server.templates.L1BookMark;
import l1j.server.server.templates.L1Item;

public class L1PcInventory extends L1Inventory {

	private static final long serialVersionUID = 1L;

	static final int MAX_SIZE = 180;

	private final L1PcInstance _owner;

    // Reserve the existing transfer gate for a short, synchronous equipment
    // change. Only this thread may nest changes (slot swap -> setEquipped).
    // This is not a CacheD replay scope and does not suppress persistence.
    private volatile Thread equipmentChangeOwner;
    private int equipmentChangeDepth;

    public final boolean tryBeginEquipmentChange() {
        final Thread current = Thread.currentThread();
        if (equipmentChangeOwner == current) {
            equipmentChangeDepth++;
            return true;
        }
        if (!_owner.tryBeginItemTransfer()) return false;
        equipmentChangeDepth = 1;
        equipmentChangeOwner = current;
        return true;
    }

    /** Claim the short synchronous inventory gate before a combat reagent debit.
     * A pending CacheD receipt/equipment change wins instead of allowing a
     * partial debit or a free defensive proc. The owner's scope permits only
     * this thread to perform the ordinary inventory persistence operations.
     */
    public final boolean tryConsumeCombatReagent(int itemId, int count) {
        if (count <= 0 || !tryBeginEquipmentChange()) return false;
        try {
            return consumeItem(itemId, count);
        } finally {
            endEquipmentChange();
        }
    }

    public final void endEquipmentChange() {
        if (!isEquipmentChangeOwner())
            throw new IllegalStateException("Equipment change released by non-owner");
        if (--equipmentChangeDepth == 0) {
            equipmentChangeOwner = null;
            _owner.endItemTransfer();
        }
    }

    final boolean isEquipmentChangeOwner() {
        return equipmentChangeOwner == Thread.currentThread();
    }

	// PC 전용 빠른 조회 캐시
	private final Map<Integer, Integer> _itemCountMap = new ConcurrentHashMap<>();
	private final Map<Integer, Integer> _equippedCountMap = new ConcurrentHashMap<>();

	// PC 전용 장비 슬롯 추적
	public final int[] slot_ring = new int[4];
	public final int[] slot_rune = new int[3];
	public final int[] slot_earring = new int[2];
	public final int[] slot_weapon = new int[2];

	private int _arrowId;

	private int _stingId;

	private long timeVisible = 0;
	private long timeVisibleDelay = 3000;

	public L1PcInventory(L1PcInstance owner) {
		_owner = owner;
		_arrowId = 0;
		_stingId = 0;
	}

	public void setTimeVisible(long l) {
		timeVisible = l;
	}

	public L1PcInstance getOwner() {
		return _owner;
	}

    /** Only the receipt-confirmed owner replay may install prepared images.
     * Do not call insertItem/deleteItem here: they contain independent SQL and
     * initialization side effects which are not part of the prepared image.
     */
    void applyEconomyImage(L1ItemInstance original, L1ItemInstance image) {
        if (!l1j.server.server.storage.CacheD.isWarehouseApplying())
            throw new IllegalStateException("Economy replay scope required");
        if (original != null) {
            if (getItem(original.getId()) != original)
                throw new IllegalStateException("Economy source object changed");
            removeItemCountCache(original.getItemId(), original.getCount());
            if (image == null) {
                _items.remove(original);
                if (original.getEndTime() != null) {
                    l1j.server.server.model.character.scheduler.PcInventoryExpiryScheduler
                            .getInstance().cancelItem(_owner, original);
                }
                L1World.getInstance().removeObject(original);
                SC_DeleteInventoryItem.send(_owner, original);
                if (original.getItemId() == 900075) {
                    SC_Ability.send(_owner, 2, false);
                    SC_Ability.send(_owner, 7, false);
                    _owner.setPolyRingMaster(false);
                    if (_owner.hasSkillEffect(L1SkillId.POLY_RING_MASTER)) {
                        _owner.removeSkillEffect(L1SkillId.POLY_RING_MASTER);
                        L1PolyMorph.undoPoly(_owner);
                        if (_owner.isInParty()) _owner.getParty().refreshPartyMemberStatus(_owner);
                    }
                }
                SC_Weight.send(_owner);
                return;
            }
            // Preserve an existing object's identity for timers/equipment refs.
            final boolean newTemplate = original.getItemId() != image.getItemId();
            final boolean recalcEquipped = newTemplate && original.isEquipped();
            if (recalcEquipped) _owner.getEquipSlot().remove(original);
            if (newTemplate) {
                // A curse conversion changes the item template in place. Keep
                // the equipped-template count index consistent with the item.
                final int oldTemplate = original.getItemId();
                final int newTemplateId = image.getItemId();
                if (original.isEquipped()) {
                    int oldCount = _equippedCountMap.getOrDefault(oldTemplate, 0);
                    if (oldCount > 1) _equippedCountMap.put(oldTemplate, oldCount - 1);
                    else _equippedCountMap.remove(oldTemplate);
                    _equippedCountMap.put(newTemplateId, _equippedCountMap.getOrDefault(newTemplateId, 0) + 1);
                }
                original.setItem(image.getItem());
            }
            original.setCount(image.getCount());
            original.setEnchantLevel(image.getEnchantLevel());
            original.setIdentified(image.isIdentified());
            original.set_durability(image.get_durability());
            original.setChargeCount(image.getChargeCount());
            original.setRemainingTime(image.getRemainingTime());
            original.setLastUsed(image.getLastUsed());
            original.setBless(image.getBless());
            original.setAttrEnchantLevel(image.getAttrEnchantLevel());
            original.setSpecialEnchant(image.getSpecialEnchant());
            original.setPackage(image.isPackage());
            original.set_bless_level(image.get_bless_level());
            original.set_bless_level2(image.get_bless_level2());
            original.set_item_level(image.get_item_level());
            original.setHotel_Town(image.getHotel_Town());
            final boolean expiryChanged = !java.util.Objects.equals(
                    original.getEndTime(), image.getEndTime());
            if (expiryChanged && original.getEndTime() != null) {
                l1j.server.server.model.character.scheduler.PcInventoryExpiryScheduler
                        .getInstance().cancelItem(_owner, original);
            }
            original.setEndTime(image.getEndTime());
            original._cha = _owner;
            if (original.getEndTime() != null && !_owner.isLogoutStarted()) {
                l1j.server.server.model.character.scheduler.PcInventoryExpiryScheduler
                        .getInstance().scheduleItem(_owner, original);
            }
            if (recalcEquipped) {
                // The old template's equipment modifiers were removed above.
                // Reapply the new template without toggling equipped or issuing
                // an independent inventory SQL update inside receipt replay.
                _owner.getEquipSlot().set(original);
                _owner.setCurrentHp(_owner.getCurrentHp());
                _owner.setCurrentMp(_owner.getCurrentMp());
            }
            addItemCountCache(original.getItemId(), original.getCount());
            original.getLastStatus().updateAll();
            SC_ItemStatus.send(_owner, original);
            SC_ItemName.send(_owner, original);
            SC_ItemColor.send(_owner, original);
        } else {
            if (image == null || getItem(image.getId()) != null)
                throw new IllegalStateException("Economy destination object exists");
            image._cha = _owner;
            image.getLastStatus().updateAll();
            _items.add(image);
            addItemCountCache(image.getItemId(), image.getCount());
            L1World.getInstance().storeObject(image);
            LIN.SC_ADD_INVENTORY_NOTI.sendItem(_owner, image, false, _owner.getId());
            if (image.getItemId() == 900075) {
                SC_Ability.send(_owner, 2, true);
                SC_Ability.send(_owner, 7, true);
                _owner.setPolyRingMaster(true);
            }
            if (!image.getItem().isEndedTimeMessage()
                    && (image.getEnchantLevel() > 0 || image.getAttrEnchantLevel() > 0)) {
                SC_ItemStatus.send(_owner, image);
                SC_PacketBox.send(_owner, SC_PacketBox.ITEM_ENCHANT_UPDATE, image);
            }
            if (image.getItemId() == 700024) notifyEconomyBookmark(image);
            if (image.getEndTime() != null && !_owner.isLogoutStarted()) {
                l1j.server.server.model.character.scheduler.PcInventoryExpiryScheduler
                        .getInstance().scheduleItem(_owner, image);
            }
        }
        SC_Weight.send(_owner);
    }

    private void notifyEconomyBookmark(L1ItemInstance image) {
        final var session = _owner.getNetConnection();
        final int stamp = _owner.getSessionStamp(), objectId = image.getId(), templateId = image.getItemId();
        if (session == null || !session.isConnected()) return;
        // Template lookup stays on the existing DB worker; only the prepared
        // packet returns to the original session, after the economy lane clears.
        try {
            l1j.server.server.storage.CacheD.getInstance().submitDbJobOrdered(_owner.getId(), () -> {
                final var bookmarks = L1BookMark.ShowBookmarkitem(null, templateId);
                l1j.server.server.model.network.thread.CIOThreadCallback.getInstance().completeSessionReply(session, () -> {
                    if (_owner.getNetConnection() == session && session.getActiveChar() == _owner
                            && _owner.getSessionStamp() == stamp && getItem(objectId) == image)
                        SC_PacketBox.send(_owner, SC_PacketBox.BOOKMARK, objectId, "$13719", bookmarks);
                });
            });
        } catch (RuntimeException rejected) {
            java.util.logging.Logger.getLogger(L1PcInventory.class.getName()).log(
                    java.util.logging.Level.WARNING, "Committed bookmark display could not be queued", rejected);
        }
    }

	@Override
	public int getItemCountDirect(int itemId) {
		return _itemCountMap.getOrDefault(itemId, 0);
	}

	@Override
	public boolean isEquippedDirect(int itemId) {
		return _equippedCountMap.getOrDefault(itemId, 0) > 0;
	}

	@Override
	protected void addItemCountCache(int itemId, int amount) {
		if (amount <= 0) return;
		_itemCountMap.put(itemId, _itemCountMap.getOrDefault(itemId, 0) + amount);
	}

	@Override
	protected void removeItemCountCache(int itemId, int amount) {
		if (amount <= 0) return;

		int remain = _itemCountMap.getOrDefault(itemId, 0) - amount;
		if (remain > 0) {
			_itemCountMap.put(itemId, remain);
		} else {
			_itemCountMap.remove(itemId);
		}
	}

	public void toSlotPacket(L1PcInstance pc, L1ItemInstance item, boolean worldjoin) {

		final int type2 = item.getItem().getType2();
		int idx = 0;

		if (type2 == 2) { // 방어구 & 악세사리
			EquipType equipType = EquipType.get(item.getItem().getType());
			if (equipType != null) {
				idx = equipType.executeStrategy(this, pc, item, worldjoin);
			}
		} else if (type2 == 1) { 
			idx = updateWeaponSlot(item);
		}
		if (idx != 0) {
			SC_EquipmentWindow.send(pc, item.getId(), idx, item.isEquipped());
		}
	}

	/**
	 * 다중 슬롯 공용 처리 (반지/귀걸이/룬 등)
	 */
	public int updateMultiSlot(int[] slotArray, L1ItemInstance item, int baseIndex, int step) {
		final int itemId = item.getId();
		final boolean isEquipped = item.isEquipped();

		int currentIdx = -1;
		int emptyIdx = -1;

		for (int i = 0; i < slotArray.length; i++) {
			if (slotArray[i] == itemId) {
				currentIdx = i;
			} else if (slotArray[i] == 0 && emptyIdx == -1) {
				emptyIdx = i;
			}
		}
		if (isEquipped) {
			if (currentIdx != -1) {
				return baseIndex + (currentIdx * step);
			}
			if (emptyIdx != -1) {
				slotArray[emptyIdx] = itemId;
				return baseIndex + (emptyIdx * step);
			}
			return 0;
		}
		if (currentIdx != -1) {
			slotArray[currentIdx] = 0;
			return baseIndex + (currentIdx * step);
		}
		return 0;
	}
	/**
	 * 무기 슬롯 처리.
	 */
	public int updateWeaponSlot(L1ItemInstance item) {
		final int itemId = item.getId();
		final boolean isEquipped = item.isEquipped();

		int currentIdx = -1;
		int emptyIdx = -1;

		for (int i = 0; i < slot_weapon.length; i++) {
			if (slot_weapon[i] == itemId) {
				currentIdx = i;
			} else if (slot_weapon[i] == 0 && emptyIdx == -1) {
				emptyIdx = i;
			}
		}
		if (isEquipped) {
			if (currentIdx != -1) {
				return weaponIdx(currentIdx);
			}
			if (emptyIdx != -1) {
				slot_weapon[emptyIdx] = itemId;
				return weaponIdx(emptyIdx);
			}
			return 0; 
		}
		if (currentIdx != -1) {
			slot_weapon[currentIdx] = 0;
			return weaponIdx(currentIdx);
		}
		return 0;
	}

    private static int weaponIdx(int arrIdx) {
        return arrIdx == 0 ? SC_EquipmentWindow.EQUIPMENT_INDEX_WEAPON : SC_EquipmentWindow.EQUIPMENT_INDEX_SHIELD;
    }

    /**
     * 슬롯 추적 배열 초기화.
     */
    public void resetSlotTracking() {
        Arrays.fill(slot_weapon, 0);
        Arrays.fill(slot_ring, 0);
        Arrays.fill(slot_earring, 0);
        Arrays.fill(slot_rune, 0);
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
	public int checkAddItem(L1ItemInstance item, int count) {
		return checkAddItem(item, count, true);
	}

	public int checkAddItem(L1ItemInstance item, int count, boolean message) {
		if (item == null) {
			return -1;
		}

		if (count < 0 || count > MAX_AMOUNT) {
			return AMOUNT_OVER;
		}

		if (getSize() > MAX_SIZE || (getSize() == MAX_SIZE && (!item.isStackable() || !checkItem(item.getItem().getItemId())))) {
			if (message) {
				sendOverMessage(263);
			}
			return SIZE_OVER;
		}

		int weight = getWeight() + item.getItem().getWeight() * count / 1000 + 1;
		if (weight < 0 || (item.getItem().getWeight() * count / 1000) < 0) {
			if (message) {
				sendOverMessage(82); // 아이템이 너무 무거워, 더 이상 가질 수 없습니다.
			}
			return WEIGHT_OVER;
		}
		if (calcWeight100(weight) >= 240) {
			if (message) {
				sendOverMessage(82); // 아이템이 너무 무거워, 더 이상 가질 수 없습니다.
			}
			return WEIGHT_OVER;
		}

		L1ItemInstance itemExist = findItemId(item.getItemId());
		if (itemExist != null && ((itemExist.getCount() + count) < 0 || (itemExist.getCount() + count) > 1800000000)) {
			SC_ServerMessage.send(getOwner(), 166, "소지하고 있는 아데나", "2,000,000,000을 초과하고 있습니다.");
			return AMOUNT_OVER;
		}

		return OK;
	}

	public void sendOverMessage(int message_id) {
		SC_ServerMessage.send(_owner, message_id);
	}

	public void sendOptioon() {
		try {
			for (L1ItemInstance item : _items) {
				if (item.isEquipped()) {
					item.setEquipped(false);
					_owner.getEquipSlot().removeSetItems(item.getItemId());
					setEquipped(item, true, true, false, false);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

    @Override
    public void loadItems() {
        try {
            loadItems(CharactersItemStorage.create().loadItems(_owner.getId()));
        } catch (Exception e) {
            throw new IllegalStateException("CacheD character inventory load failed: " + _owner.getId(), e);
        }
    }

    public void loadItems(List<L1ItemInstance> loadedItems) {
        if (loadedItems == null) throw new IllegalArgumentException("loadedItems");
        try {
            // Restore exactly what was committed to the DB. The previous path
            // deleted duplicate ADENA rows while loading, from inside a CacheD
            // job. That called the synchronous CacheD item-command API on its
            // own worker (and could destroy a row before checking overflow).
            for (L1ItemInstance item : loadedItems) {
                if (item == null) continue;
                final int itemId = item.getItemId();
                final int count = item.getCount();
                if (count <= 0) {
                    throw new IllegalStateException("Invalid stored item amount: " + item.getId());
                }
                final long total = (long) _itemCountMap.getOrDefault(itemId, 0) + count;
                if (total > MAX_AMOUNT) {
                    throw new IllegalStateException("Stored item amount overflow: " + itemId);
                }
                item._cha = _owner;
                _items.add(item);
                if (itemId == 900075) {
                    SC_Ability.send(_owner, 2, true);
                    SC_Ability.send(_owner, 7, true);
                    _owner.setPolyRingMaster(true);
                }
                L1World.getInstance().storeObject(item);
                _itemCountMap.put(itemId, (int) total);
            }
		} catch (Exception e) {
			throw new IllegalStateException("CacheD character inventory restore failed: " + _owner.getId(), e);
		}
	}

	@Override
	public void insertItem(L1ItemInstance item) {
        if (rejectPendingItemTransfer())
            throw new IllegalStateException("Item mutation refused while CacheD transfer pending");
		item._cha = _owner;
		
		if (item.get_bless_level() > 0) {
			item.setBless(0);
		}
		if (item.get_item_level() > 0) {
			item.set_item_level(item.get_item_level());
		}
		try {
			CharactersItemStorage.create().storeItem(_owner.getId(), item);
		} catch (Exception e) {
			throw new IllegalStateException("CacheD inventory insert failed: item=" + item.getId(), e);
		}
				
	    int itemId = item.getItemId();
	    int amount = item.getCount();

	    // 맵에 더하기
	    _itemCountMap.put(itemId, _itemCountMap.getOrDefault(itemId, 0) + amount);
	    
	    // [디버그 로그]
	  //  System.out.println(String.format("[아이템추가] ID: %d, 수량: %d, 맵총량: %d", itemId, amount, _itemCountMap.get(itemId)));
	    
	    
	    if (_owner.isFishing()) {
	        switch (item.getItemId()) {
	        case 41297: case 41296: case 41301: case 41304: case 41303:
	        case 600230: case 820018:
	        case 49092: case 49093: case 49094: case 49095:
                LIN.SC_ADD_INVENTORY_NOTI.sendItem(_owner, item, false, _owner.getId());
	            break;
	        default:
                LIN.SC_ADD_INVENTORY_NOTI.sendItem(_owner, item, false, _owner.getId());
	            break;
	        }
	    } else {
	        LIN.SC_ADD_INVENTORY_NOTI.sendItem(_owner, item, false, _owner.getId());
	    }

		// _owner.sendPackets(new S_AddItem(item));
		if (item.getItem().getWeight() != 0) {
			SC_Weight.send(_owner);
		}

		if (item.getItemId() == 900075) {
			SC_Ability.send(_owner, 2, true);
			SC_Ability.send(_owner, 7, true);
			_owner.setPolyRingMaster(true);
		}
		
		if (!item.getItem().isEndedTimeMessage()) {
			if (item.getEnchantLevel() > 0 || item.getAttrEnchantLevel() > 0) {
				SC_ItemStatus.send(_owner, item);
				SC_PacketBox.send(_owner, SC_PacketBox.ITEM_ENCHANT_UPDATE, item);
			}
		}
		
		if (item.getItemId() == 700024){
			SC_PacketBox.send(_owner, SC_PacketBox.BOOKMARK, item.getId(), "$13719",
					L1BookMark.ShowBookmarkitem(_owner, item.getItemId()));
		}
        // Arm only after the item has been added and all insert notifications ran.
        if (item.getEndTime() != null && !_owner.isLogoutStarted()
                && getItem(item.getId()) == item) {
            l1j.server.server.model.character.scheduler.PcInventoryExpiryScheduler
                    .getInstance().scheduleItem(_owner, item);
        }
    }

	// Explicit masks for formerly untracked persisted fields. Use unused
	// inventory-side bits; CacheD SQL uses its own independent bit layout.
	public static final int COL_END_TIME = 1 << 16;
	public static final int COL_PACKAGE = 1 << 17;
	public static final int COL_ITEM_LEVEL = 1 << 18;
	public static final int COL_HOTEL_TOWN = 1 << 19;
	public static final int COL_SAVE_ALL = 4096;
	public static final int COL_SPECIAL_ENCHANT = 2048;
	public static final int COL_ATTRENCHANTLVL = 1024;
	public static final int COL_BLESS_LEVEL = 8192;
	public static final int COL_BLESS_LEVEL2 = 16384;
	public static final int COL_BLESS = 512;
	public static final int COL_BLESS2 = 32768;
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
	protected void persistTransferCount(L1ItemInstance item) {
		saveItem(item, COL_COUNT);
	}

	@Override
	protected void persistTransferCharge(L1ItemInstance item) {
		saveItem(item, COL_CHARGE_COUNT);
	}

	@Override
	public void updateItem(L1ItemInstance item) {
		updateItem(item, COL_COUNT);
		// Ordinary stack changes must enter CacheD's owner/item FIFO BEFORE a
		// subsequent receipt-guarded use. A five-minute inventory auto-save
		// cannot be the only persistence path for an already consumed stack.
		// CacheD coalesces repeated updates (no synchronous JDBC here).
		saveItem(item, COL_COUNT);
	}

	private static final int MASK_STATUS  = COL_BLESS_LEVEL | COL_BLESS_LEVEL2 | COL_DURABILITY 
			| COL_ENCHANTLVL | COL_ATTRENCHANTLVL | COL_IS_ID | COL_ITEMID;

	private static final int MASK_NAME    = COL_EQUIPPED | COL_SPECIAL_ENCHANT 
			| COL_REMAINING_TIME | COL_CHARGE_COUNT;

	private static final int MASK_COLOR   = COL_BLESS | COL_BLESS2 | COL_IS_ID | COL_ITEMID;

	private static final int MASK_ENCHANT = COL_ENCHANTLVL | COL_ATTRENCHANTLVL;

	@Override
	public void updateItem(L1ItemInstance item, int column) {
        if (rejectPendingItemTransfer())
            throw new IllegalStateException("Item mutation refused while CacheD transfer pending");
		if (column <= 0) return;

		boolean sendStatus  = (column & MASK_STATUS) != 0;
		boolean sendName    = (column & MASK_NAME) != 0;
		boolean sendColor   = (column & MASK_COLOR) != 0;
		boolean sendEnchant = (column & MASK_ENCHANT) != 0;
		boolean sendWeight  = (column & COL_ITEMID) != 0;

		if ((column & COL_IS_ID) != 0) {
			item._cha = _owner;
		}

		if ((column & COL_COUNT) != 0) {
			sendStatus = true; 
			sendWeight = true; 

			int weight = item.getWeight();
			if (weight != item.getLastWeight()) {
				item.setLastWeight(weight);
			} else {
				sendName = true; 
			}
		}

		if (sendStatus)  SC_ItemStatus.send(_owner, item);
		if (sendName)    SC_ItemName.send(_owner, item);
		if (sendColor)   SC_ItemColor.send(_owner, item);
		if (sendEnchant) SC_PacketBox.send(_owner, SC_PacketBox.ITEM_ENCHANT_UPDATE, item);

		if (sendWeight && item.getItem().getWeight() != 0) {
			SC_Weight.send(_owner);
		}
	}

	/**
	 * 목록내의 아이템 상태를 DB에 보존한다.
	 * 
	 * @param item
	 *            - 갱신 대상의 아이템
	 * @param column
	 *            - 갱신하는 스테이터스의 종류
	 */
	public void saveItem(L1ItemInstance item, int column) {
		if (item == null || column == 0) return;

		// Inventory column flags and CacheD SQL column flags have different
		// bit assignments. Translate the complete set once, then capture one
		// coherent item snapshot rather than submitting one snapshot per field.
		long mask = 0L;
		if ((column & COL_SAVE_ALL) != 0) {
			mask = (1L << 18) - 1L;
		} else {
			if ((column & COL_ITEMID) != 0) mask |= 1L;
			if ((column & COL_COUNT) != 0) mask |= 2L;
			if ((column & COL_IS_ID) != 0) mask |= 4L;
			if ((column & COL_EQUIPPED) != 0) mask |= 8L;
			if ((column & COL_ENCHANTLVL) != 0) mask |= 16L;
			if ((column & COL_DURABILITY) != 0) mask |= 32L;
			if ((column & COL_CHARGE_COUNT) != 0) mask |= 64L;
			if ((column & COL_REMAINING_TIME) != 0) mask |= 128L;
			if ((column & COL_DELAY_EFFECT) != 0) mask |= 256L;
			if ((column & COL_BLESS) != 0) mask |= 512L;
			if ((column & COL_ATTRENCHANTLVL) != 0) mask |= 1024L;
			if ((column & COL_SPECIAL_ENCHANT) != 0) mask |= 1L << 17;
			if ((column & COL_END_TIME) != 0) mask |= 1L << 11;
			if ((column & COL_PACKAGE) != 0) mask |= 1L << 12;
			if ((column & COL_ITEM_LEVEL) != 0) mask |= 1L << 15;
			if ((column & COL_HOTEL_TOWN) != 0) mask |= 1L << 16;
			if ((column & COL_BLESS_LEVEL) != 0) mask |= 1L << 13;
			if ((column & COL_BLESS_LEVEL2) != 0) mask |= 1L << 14;
			// Legacy COL_BLESS2 has no dedicated character_items column;
			// retain the old full-save behavior instead of silently dropping it.
			if ((column & COL_BLESS2) != 0) mask = (1L << 18) - 1L;
		}
		if (mask == 0L) return;
		try {
			l1j.server.server.storage.CacheD.getInstance().saveItem(item, mask);
		} catch (RuntimeException e) {
			java.util.logging.Logger.getLogger(L1PcInventory.class.getName())
				.log(java.util.logging.Level.SEVERE, "CacheD inventory dirty submission failed: item=" + item.getId(), e);
			throw e;
		}
	}

	@Override
	public void deleteItem(L1ItemInstance item) {
        if (rejectPendingItemTransfer())
            throw new IllegalStateException("Item mutation refused while CacheD transfer pending");
		
		try {
			CharactersItemStorage.create().deleteItem(item);
		} catch (Exception e) {
			throw new IllegalStateException("CacheD inventory delete failed: item=" + item.getId(), e);
		}
		if (item.isEquipped()) {
			setEquipped(item, false);
		}
		
		int itemId = item.getItemId();
		int amount = item.getCount();

		// 맵에서 빼기
		int currentCount = _itemCountMap.getOrDefault(itemId, 0);
		int newCount = currentCount - amount;

		if (newCount <= 0) {
			_itemCountMap.remove(itemId);
		} else {
			_itemCountMap.put(itemId, newCount);
		}

		// [디버그 로그]
	//	System.out.println(String.format("[아이템삭제] ID: %d, 삭제수량: %d, 맵잔량: %d", itemId, amount, _itemCountMap.getOrDefault(itemId, 0)));
		
		SC_DeleteInventoryItem.send(_owner, item);
		_items.remove(item);
        if (item.getEndTime() != null) {
            l1j.server.server.model.character.scheduler.PcInventoryExpiryScheduler
                    .getInstance().cancelItem(_owner, item);
        }
		
		if (item.getItem().getWeight() != 0) {
			SC_Weight.send(_owner);
		}
		
		if (item.getItemId() == 900075) {
			SC_Ability.send(_owner, 2, false);
			SC_Ability.send(_owner, 7, false);
			_owner.setPolyRingMaster(false);
			if (_owner.hasSkillEffect(L1SkillId.POLY_RING_MASTER)) {
				_owner.removeSkillEffect(L1SkillId.POLY_RING_MASTER);
				L1PolyMorph.undoPoly(_owner);
				if (_owner.isInParty()) {
					_owner.getParty().refreshPartyMemberStatus(_owner);;
				}
			}
		}
	}

	public L1ItemInstance getItemEquippend(int itemId) {// 아이템 착용 상태 확인의 오브젝트 인식
		L1ItemInstance equipeitem = null;
		L1ItemInstance item = null;
		for (Object itemObject : _items) {
			item = (L1ItemInstance) itemObject;
			if (item.getItem().getItemId() == itemId && item.isEquipped()) {
				equipeitem = item;
				break;
			}
		}
		return equipeitem;
	}

	public void setEquipped(L1ItemInstance item, boolean equipped) {
		setEquipped(item, equipped, false, false, false);
	}

	public L1ItemInstance getEquippedItem(int itemId) {
		L1ItemInstance equipeitem = null;
		L1ItemInstance item = null;
		for (Object itemObject : _items) {
			item = (L1ItemInstance) itemObject;
			if (item.getItem().getItemId() == itemId) {
				equipeitem = item;
				break;
			}
		}
		return equipeitem;
	}
	public void setEquipped(L1ItemInstance item, boolean equipped, boolean loaded, boolean changeWeapon, boolean shieldWeapon) {

        // Refuse BEFORE flags, equipment stats, caches or packets change.
        // The committed replay already owns the gate and must retain it.
        final boolean reserve = !l1j.server.server.storage.CacheD.isItemTransferReplaying();
        if (reserve && !tryBeginEquipmentChange()) return;
        try {
		if (item.isEquipped() != equipped) {
			L1Item temp = item.getItem();
			int itemId = item.getItemId(); // 실시간 체크를 위한 아이템 ID

			if (equipped) {
				if (temp.getItemId() == 20077 || temp.getItemId() == 20062 || temp.getItemId() == 120077) {
					if (System.currentTimeMillis() - timeVisible < timeVisibleDelay) {
						return;
					}
				}

				if (item.getItem().getType() != 4 && _owner.getInventory().getArrowItemId() != 0) {
					L1ItemInstance arrow = _owner.getInventory().findItemId(_owner.getInventory().getArrowItemId());
					if (arrow != null) {
						_owner.getInventory().setArrow(0);
						SC_ItemName.send(_owner, arrow);
					}
				}
				
				item.setEquipped(true);
				_owner.getEquipSlot().set(item);
				_equippedCountMap.put(itemId, _equippedCountMap.getOrDefault(itemId, 0) + 1);
   
	            if (!loaded && item.getItem().getType2() == 1 && _owner.getEquipSlot().getWeaponCount() == 2) {
	                SC_SkillSound.send(_owner, _owner.getId(), 12534);
	            }
				
		//		System.out.println(String.format("[장착 완료] %s - %s(%d) | 장착수: %d", 
		//				_owner.getName(), temp.getName(), itemId, _equippedCountMap.get(itemId)));

			} else {
				if (!loaded) {
					if (temp.getItemId() == 20077 || temp.getItemId() == 20062 || temp.getItemId() == 120077) {
						if (_owner.isInvisble()) {
							_owner.delInvis();
							_owner.beginInvisTimer();
							// return;
						}
						timeVisible = System.currentTimeMillis();
					}
				}
				if(item.getItem().getType2() == 1 && item.getItem().getType1() == 4) {
					if (_owner.hasSkillEffect(L1SkillId.INFERNO)) {
						_owner.removeSkillEffect(L1SkillId.INFERNO);
					}
				}
				// 양손검을 착용해제 했을때 카운터배리어 효과해제
				if (item.getItem().isTwohandedWeapon()) {
					if (_owner.hasSkillEffect(L1SkillId.COUNTER_BARRIER)) {
						_owner.removeSkillEffect(L1SkillId.COUNTER_BARRIER);
						SC_PacketBox.send(_owner, SC_PacketBox.UNLIMITED_ICON1, 71, false);
					}
				}
				if (item.getItem().getType2() == 1) {
					_owner.remove_elf_second_brave();
					if (item.getItem().getType() == 4 && _owner.getInventory().getArrowItemId() != 0) {
						L1ItemInstance arrow = _owner.getInventory().findItemId(_owner.getInventory().getArrowItemId());
						_owner.getInventory().setArrow(0);
						SC_ItemName.send(_owner, arrow);
					}
				}
				item.setEquipped(false);
				_owner.getEquipSlot().remove(item);

				int current = _equippedCountMap.getOrDefault(itemId, 0);
				if (current > 1) _equippedCountMap.put(itemId, current - 1);
				else _equippedCountMap.remove(itemId);

		//		System.out.println(String.format("[해제 완료] %s - %s(%d) | 장착수: %d", 
			//			_owner.getName(), temp.getName(), itemId, _equippedCountMap.getOrDefault(itemId, 0)));
			}

			if (!loaded) {
				_owner.setCurrentHp(_owner.getCurrentHp());
				_owner.setCurrentMp(_owner.getCurrentMp());
				updateItem(item, COL_EQUIPPED);
				if (temp.getType2() == 1 && !changeWeapon) {
					final long sc = SC_CharVisualUpdate.pack(_owner);
					SC_CharVisualUpdate.sendPacked(_owner, sc);
					SC_CharVisualUpdate.broadcastFastPacked(_owner, sc);
				}
			}
			if (item.getItem().getType2() == 1) {
				SC_PacketBox.send(_owner, SC_PacketBox.공격가능거리, _owner, item);
			}
			_owner.getInventory().toSlotPacket(_owner, item, false);
		}
        } finally {
            if (reserve) endEquipmentChange();
        }
	}

	public boolean checkEquipped(int id) {
		L1ItemInstance item = null;
		for (Object itemObject : _items) {
			item = (L1ItemInstance) itemObject;
			if (item.getItem().getItemId() == id && item.isEquipped()) {
				return true;
			}
		}
		return false;
	}


	/** '축복받은'이 아이템 접두어로 있다면 이를 제거하고 아이템 이름 비교. **/
	public int getNameEquipped(int type2, int type, String name) {
		int equipeCount = 0;
		L1ItemInstance item = null;
		String tName = null;
		String aName = null;
		if (name.indexOf("축복받은") != -1) {
			aName = name.replace("축복받은 ", "");
		} else {
			aName = name;
		}

		for (Object itemObject : _items) {
			item = (L1ItemInstance) itemObject;
			if (item.getItem().getType2() == type2 && item.getItem().getType() == type && item.isEquipped()) {
				if (item != null) {
					tName = item.getName();
					if (tName.indexOf("축복받은") != -1)
						tName = tName.replace("축복받은 ", "");
					if (tName.equals(aName))
						equipeCount++;
				}
			}
		}
		return equipeCount;
	}

	public int getTypeEquipped(int type2, int type) {
		int equipeCount = 0;
		L1ItemInstance item = null;
		for (Object itemObject : _items) {
			item = (L1ItemInstance) itemObject;
			if (item.getItem().getType2() == type2 && item.getItem().getType() == type && item.isEquipped()) {
				equipeCount++;
			}
		}
		return equipeCount;
	}

	public L1ItemInstance getItemEquipped(int type2, int type) {
		L1ItemInstance equipeitem = null;
		L1ItemInstance item = null;
		for (Object itemObject : _items) {
			item = (L1ItemInstance) itemObject;
			if (item.getItem().getType2() == type2 && item.getItem().getType() == type && item.isEquipped()) {
				equipeitem = item;
				break;
			}
		}
		return equipeitem;
	}

	public L1ItemInstance[] getRingEquipped() {
		L1ItemInstance equipeItem[] = new L1ItemInstance[4];
		int equipeCount = 0;
		L1ItemInstance item = null;
		for (Object itemObject : _items) {
			item = (L1ItemInstance) itemObject;
			if (item.getItem().getType2() == 2 && item.getItem().getType() == 9 && item.isEquipped()) {
				equipeItem[equipeCount] = item;
				equipeCount++;
				if (equipeCount == 4) {
					break;
				}
			}
		}
		return equipeItem;
	}

	public void takeoffEquip(int polyid) {
		takeoffWeapon(polyid);
		takeoffArmor(polyid);
	}

	private void takeoffWeapon(int polyid) {
		if (_owner.getWeapon() == null) {
			return;
		}

		boolean takeoff = false;
		int weapon_type = _owner.getWeapon().getItem().getType();
		takeoff = !L1PolyMorph.isEquipableWeapon(polyid, weapon_type);

		if (takeoff) {
			setEquipped(_owner.getWeapon(), false, false, false, false);
		}

		if (_owner.getSecondWeapon() != null) {// 변신버그추가
			boolean second_takeoff = false;
			int second_weapon_type = _owner.getSecondWeapon().getItem().getType();
			second_takeoff = !L1PolyMorph.isEquipableWeapon(polyid, second_weapon_type);

			if (second_takeoff) {
				setEquipped(_owner.getSecondWeapon(), false, false, false, false);
			}
		} // 변신버그추가

	}

	private void takeoffArmor(int polyid) {
		L1ItemInstance armor = null;

		for (int type = 0; type <= 12; type++) {
			if (getTypeEquipped(2, type) != 0 && !L1PolyMorph.isEquipableArmor(polyid, type)) {
				if (type == 9) {
					armor = getItemEquipped(2, type);
					if (armor != null) {
						setEquipped(armor, false, false, false, false);
					}
					armor = getItemEquipped(2, type);
					if (armor != null) {
						setEquipped(armor, false, false, false, false);
					}
				} else {
					armor = getItemEquipped(2, type);
					if (armor != null) {
						setEquipped(armor, false, false, false, false);
					}
				}
			}
		}
	}

	public L1ItemInstance getArrow() {
		return getBullet(0);
	}

	/** 로봇시스템 **/

	public L1ItemInstance getSting() {
		return getBullet(15);
	}

	// 스팅 타입수정
	private L1ItemInstance getBullet(int type) {
		L1ItemInstance bullet;
		int priorityId = 0;
		if (type == 0) {
			priorityId = _arrowId;
		}
		if (type == 15) {
			priorityId = _stingId;
		}
		if (priorityId > 0) {
			bullet = findItemId(priorityId);
			if (bullet != null) {
				return bullet;
			} else {
				if (type == 0) {
					_arrowId = 0;
				}
				if (type == 15) {
					_stingId = 0;
				}
			}
		}

		for (Object itemObject : _items) {
			bullet = (L1ItemInstance) itemObject;
			if (bullet.getItem().getType() == type) {
				if (type == 0) {
					_arrowId = bullet.getItem().getItemId();
				}
				if (type == 15) {
					_stingId = bullet.getItem().getItemId();
				}
				SC_ItemName.send(_owner, bullet);
				SC_ServerMessage.send(_owner, 452, bullet.getName());
				return bullet;
			}
		}
		return null;
	}

	public int getArrowItemId() {
		return _arrowId;
	}

	public void setArrow(int id) {
		_arrowId = id;
	}

	public int getStingItemId() {
		return _stingId;
	}

	public void setSting(int id) {
		_stingId = id;
	}

	// 해당아이템은 드랍불가
	public L1ItemInstance CaoPenalty() {

		int rnd = ThreadLocalRandom.current().nextInt(_items.size());
		L1ItemInstance penaltyItem = _items.get(rnd);
		
        if (penaltyItem == null || penaltyItem.getItem() == null) return null;
		
		if (penaltyItem.getItem().getItemId() == L1ItemId.ADENA || NoDropItem.getInstance().isNoDropItem(penaltyItem.getItem().getItemId()) ||
				penaltyItem.getItem().getItemId() == 412315 || penaltyItem.getItem().getItemId() == 301066|| penaltyItem.getItem().getItemId() == 70706
				|| penaltyItem.getItem().getItemId() == 3000248
						|| penaltyItem.getItem().getItemId() == 70708|| penaltyItem.getItem().getItemId() == 80500 || !penaltyItem.getItem().isTradable()) {
			return null;
		}
		if(!MJCompanionInstanceCache.is_companion_oblivion(penaltyItem.getId()))			
			return null;
		
        for (Object petObject : _owner.getPetList().values()) {
            if (petObject instanceof L1PetInstance) {
                if (penaltyItem.getId() == ((L1PetInstance) petObject).getItemObjId()) return null;
            }
        }
        for (Object dollObject : _owner.getDollList().values()) {
            if (dollObject instanceof L1DollInstance) {
                if (penaltyItem.getId() == ((L1DollInstance) dollObject).getItemObjId()) return null;
            }
        }

		setEquipped(penaltyItem, false, false, true, false);
		return penaltyItem;
	}

	public boolean checkEnchant(int id, int enchant) {
		L1ItemInstance item = null;
		for (Object itemObject : _items) {
			item = (L1ItemInstance) itemObject;
			if (item.getItem().getItemId() == id && item.getEnchantLevel() == enchant) {
				return true;
			}
		}
		return false;
	}

	public boolean DeleteEnchant(int id, int enchant) {
		L1ItemInstance item = null;
		for (Object itemObject : _items) {
			item = (L1ItemInstance) itemObject;
			if (item.getItem().getItemId() == id && item.getEnchantLevel() == enchant) {
				removeItem(item, 1);
				return true;
			}
		}
		return false;
	}

	public int getEnchantCount(int id) {// 인첸 레벨
		int cnt = 0;
		L1ItemInstance item = null;
		for (Object itemObject : _items) {
			item = (L1ItemInstance) itemObject;
			if (item.getItemId() == id) {
				cnt = item.getEnchantLevel();
			}
		}
		return cnt;
	}
	public L1ItemInstance checkEquippedItem(int id) {
		L1ItemInstance item = null;
		for (Object itemObject : _items) {
			item = (L1ItemInstance) itemObject;
			if (item.getItem().getItemId() == id && item.isEquipped()) {
				return item;
			}
		}
		return null;
	}
	public L1ItemInstance get_etc_itemid(int itemid) {
		Iterator<L1ItemInstance> iter = _items.iterator();
		L1ItemInstance item = null;

		while (iter.hasNext()) {
			item = iter.next();
			if (item == null || item.getItem().getType2() != 0) { // -- 잡화만 검색
				continue;
			}
			if (item.getItemId() == itemid) {
				return item;
			}
		}
		return null;
	}

	public L1ItemInstance get_set_item_eq(int itemid) {
		Iterator<L1ItemInstance> iter = _items.iterator();
		L1ItemInstance item = null;

		while (iter.hasNext()) {
			item = iter.next();
			if (item == null || item.getItem().getType2() == 0)
				continue; // -- 잡화는 검색 조건에서 제외
			if (item.getItemId() == itemid && item.isEquipped()) {
				return item;
			}
		}
		return null;
	}

	public int checkEquippedcount(int id) {
		int equipeCount = 0;
		L1ItemInstance item = null;
		for (Object itemObject : _items) {
			item = (L1ItemInstance) itemObject;
			if (item.getItem().getItemId() == id && item.isEquipped())
				equipeCount++;
		}
		return equipeCount;
	}

	public boolean slotItemFind(L1PcInstance pc, int id, int itemobjId, int enchant, int bless, int attr) {
		for (L1ItemInstance item : _items) {
			if (item.getItemId() == id && item.getId() == itemobjId && item.getEnchantLevel() == enchant && item.getBless() == bless
					&& item.getAttrEnchantLevel() == attr) {
				pc.getInventory().setEquipped(item, true);
				return true;
			}
		}
		return false;
	}

	public int checkItemCount(int id) {
		int cnt = 0;
		L1ItemInstance item = null;
		for (Object itemObject : _items) {
			item = (L1ItemInstance) itemObject;
			if (item.getItemId() == id)
				cnt += item.getCount();
		}
		return cnt;
	}

	public L1ItemInstance findItemObjId(int id) {
		for (L1ItemInstance item : _items) {
			if (item == null)
				continue;
			if (item.getId() == id) {
				return item;
			}
		}

		return null;
	}

	public boolean checkEquipped(int[] ids) {
		for (int id : ids) {
			if (!checkEquipped(id)) {
				return false;
			}
		}
		return true;
	}

	public boolean checkEquippedAtOnce(int[] ids) {
		for (L1ItemInstance item : _items) {
			int itemId = item.getItemId();
			for (int id : ids) {
				if (id == itemId && item.isEquipped())
					return true;
			}
		}
		return false;
	}

	public boolean consumeItem(L1ItemInstance item, int count) {
		L1ItemInstance find = findItemObjId(item.getId());
		if (find == null || find.getCount() < count)
			return false;

		removeItem(item, count);
		return true;
	}

	public boolean consumeItem(L1ItemInstance[] items, int[] counts) {
		int size = items.length;
		for (int i = size - 1; i >= 0; --i) {
			L1ItemInstance item = items[i];
			int count = counts[i];
			L1ItemInstance find = findItemObjId(item.getId());
			if (find == null || find.getCount() < count)
				return false;
		}

		for (int i = size - 1; i >= 0; --i) {
			removeItem(items[i], counts[i]);
		}
		return true;
	}
}
