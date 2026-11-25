
package l1j.server.server.model;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import l1j.server.server.datatables.NoDropItem;
import l1j.server.server.model.Instance.L1DollInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1PetInstance;
import l1j.server.server.model.character.companion.instance.CompanionInstanceCache;
import l1j.server.server.model.character.equip.EquipType;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.network.core.sc.packet.SC_Ability;
import l1j.server.server.model.network.core.sc.packet.SC_CharVisualUpdate;
import l1j.server.server.model.network.core.sc.packet.SC_DeleteInventoryItem;
import l1j.server.server.model.network.core.sc.packet.SC_EquipmentWindow;
import l1j.server.server.model.network.core.sc.packet.SC_ItemColor;
import l1j.server.server.model.network.core.sc.packet.SC_ItemName;
import l1j.server.server.model.network.core.sc.packet.SC_ItemStatus;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharAttrDef;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_SPMR;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_Weight;
import l1j.server.server.packets.Protocol.MainServer.Inventory.Proto.SC_ADD_INVENTORY_NOTI;
import l1j.server.server.storage.CharactersItemStorage;
import l1j.server.server.templates.BookmarkSlot;
import l1j.server.server.templates.L1Item;

public class CUserInventory extends CInventory {
    // A durable receipt has already committed when these inventory notices are sent.
    // Never turn an output exception into a partial in-memory replay/quarantine.
    private static final java.util.logging.Logger RECEIPT_NOTICE_LOG =
            java.util.logging.Logger.getLogger(CUserInventory.class.getName());

    private static void receiptNoticeFailed(String notice, RuntimeException failure) {
        RECEIPT_NOTICE_LOG.log(java.util.logging.Level.WARNING,
                "Committed inventory notice failed: " + notice, failure);
    }


	private static final long serialVersionUID = 1L;

	public static final int MAX_SIZE = 180;

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
	private final CItemCountTable _itemCountMap = new CItemCountTable();
	private final CItemCountTable _equippedCountMap = new CItemCountTable();

	// PC 전용 장비 슬롯 추적
	private static final int RING_WINDOW_SLOTS = 4;
	private static final int EARRING_WINDOW_SLOTS = 2;
	private static final int RUNE_WINDOW_SLOTS = 3;
	private int[] displayedAccessories;
	private int displayedPrimaryWeapon;
	private int displayedSecondaryWeapon;

	private int _arrowId;

	private int _stingId;

	private long timeVisible = 0;
	private long timeVisibleDelay = 3000;

	public CUserInventory(L1PcInstance owner) {
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
                original.stopEquipmentTimer();
                _items.Pop(original);
                if (original.getEndTime() != null) {
                    l1j.server.server.model.character.scheduler.PcInventoryExpiryScheduler
                            .getInstance().cancelItem(_owner, original);
                }
                L1World.getInstance().removeObject(original);
                try { SC_DeleteInventoryItem.send(_owner, original); }
                catch (RuntimeException failure) { receiptNoticeFailed("delete", failure); }
                if (original.getItemId() == 900075) {
                    try { SC_Ability.send(_owner, 2, false); }
                    catch (RuntimeException failure) { receiptNoticeFailed("ability-off-2", failure); }
                    try { SC_Ability.send(_owner, 7, false); }
                    catch (RuntimeException failure) { receiptNoticeFailed("ability-off-7", failure); }
                    _owner.setPolyRingMaster(false);
                    if (_owner.hasSkillEffect(L1SkillId.POLY_RING_MASTER)) {
                        // End callback owns both modifier retirement and visual reversal.
                        _owner.removeSkillEffect(L1SkillId.POLY_RING_MASTER);
                    }
                }
                try { SC_Weight.send(_owner); }
                catch (RuntimeException failure) { receiptNoticeFailed("weight", failure); }
                return;
            }
            // Preserve an existing object's identity for timers/equipment refs.
            final int oldTemplate = original.getItemId();
            final int oldEnchant = original.getEnchantLevel();
            final int oldDurability = original.get_durability();
            final boolean newTemplate = oldTemplate != image.getItemId();
            final boolean recalcEquipped = newTemplate && original.isEquipped();
            final L1EquipmentSlot equipment = _owner.getEquipSlot();
            final boolean restorePrimary = recalcEquipped && equipment.getSecondWeapon() != null
                    && equipment.getWeapon() == original;
            if (recalcEquipped) equipment.unequipItem(original);
            if (newTemplate) {
                // A curse conversion changes the item template in place. Keep
                // the equipped-template count index consistent with the item.
                final int newTemplateId = image.getItemId();
                if (original.isEquipped()) {
                    _equippedCountMap.subtract(oldTemplate, 1);
                    _equippedCountMap.add(newTemplateId, 1);
                }
                original.setItem(image.getItem());
                original.set_main_set_armor(false);
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
                equipment.validateSetItemAfterUnequip(oldTemplate);
                if (original.isEquipped()) equipment.equipItem(original);
                if (restorePrimary) equipment.restorePrimaryWeapon(original);
                _owner.setCurrentHp(_owner.getCurrentHp());
                _owner.setCurrentMp(_owner.getCurrentMp());
            }
            addItemCountCache(original.getItemId(), original.getCount());
            original.getLastStatus().updateAll();
            // Publish the worn stats while the receipt still owns the equipment gate.
            if (!newTemplate) {
                if (equipment.applyArmorDurabilityChange(original, oldDurability)) {
                    try { SC_OwnCharAttrDef.send(_owner); }
                    catch (RuntimeException failure) { receiptNoticeFailed("repair-defense", failure); }
                }
                if (equipment.applyEquippedEnchantChange(original, oldEnchant)) {
                    try { SC_SPMR.send(_owner); }
                    catch (RuntimeException failure) { receiptNoticeFailed("enchant-sp-mr", failure); }
                }
            }
            try { SC_ItemStatus.send(_owner, original); }
            catch (RuntimeException failure) { receiptNoticeFailed("item-status", failure); }
            try { SC_ItemName.send(_owner, original); }
            catch (RuntimeException failure) { receiptNoticeFailed("item-name", failure); }
            try { SC_ItemColor.send(_owner, original); }
            catch (RuntimeException failure) { receiptNoticeFailed("item-color", failure); }
        } else {
            if (image == null || getItem(image.getId()) != null)
                throw new IllegalStateException("Economy destination object exists");
            image._cha = _owner;
            image.getLastStatus().updateAll();
            _items.Push(image);
            addItemCountCache(image.getItemId(), image.getCount());
            L1World.getInstance().storeObject(image);
            try { SC_ADD_INVENTORY_NOTI.sendItem(_owner, image, false, _owner.getId()); }
            catch (RuntimeException failure) { receiptNoticeFailed("item-added", failure); }
            if (image.getItemId() == 900075) {
                try { SC_Ability.send(_owner, 2, true); }
                    catch (RuntimeException failure) { receiptNoticeFailed("ability-on-2", failure); }
                try { SC_Ability.send(_owner, 7, true); }
                    catch (RuntimeException failure) { receiptNoticeFailed("ability-on-7", failure); }
                _owner.setPolyRingMaster(true);
            }
            if (!image.getItem().isEndedTimeMessage()
                    && (image.getEnchantLevel() > 0 || image.getAttrEnchantLevel() > 0)) {
                try { SC_ItemStatus.send(_owner, image); }
                catch (RuntimeException failure) { receiptNoticeFailed("new-item-status", failure); }
                try { SC_PacketBox.send(_owner, SC_PacketBox.ITEM_ENCHANT_UPDATE, image); }
                catch (RuntimeException failure) { receiptNoticeFailed("new-item-enchant", failure); }
            }
            if (image.getItemId() == 700024) notifyEconomyBookmark(image);
            if (image.getEndTime() != null && !_owner.isLogoutStarted()) {
                l1j.server.server.model.character.scheduler.PcInventoryExpiryScheduler
                        .getInstance().scheduleItem(_owner, image);
            }
        }
        try { SC_Weight.send(_owner); }
        catch (RuntimeException failure) { receiptNoticeFailed("weight", failure); }
    }

    private void notifyEconomyBookmark(L1ItemInstance image) {
        final var session = _owner.getNetConnection();
        final int stamp = _owner.getSessionStamp(), objectId = image.getId(), templateId = image.getItemId();
        if (session == null || !session.isConnected()) return;
        // Template lookup stays on the existing DB worker; only the prepared
        // packet returns to the original session, after the economy lane clears.
        try {
            l1j.server.server.storage.CacheD.getInstance().submitDbJobOrdered(_owner.getId(), () -> {
                final var bookmarks = BookmarkSlot.LoadItemSlots(null, templateId);
                l1j.server.server.model.network.thread.CIOThreadCallback.getInstance().completeSessionReply(session, () -> {
                    if (_owner.getNetConnection() == session && session.getActiveChar() == _owner
                            && _owner.getSessionStamp() == stamp && getItem(objectId) == image)
                        SC_PacketBox.send(_owner, SC_PacketBox.BOOKMARK, objectId, "$13719", bookmarks);
                });
            });
        } catch (RuntimeException rejected) {
            java.util.logging.Logger.getLogger(CUserInventory.class.getName()).log(
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
		_itemCountMap.add(itemId, amount);
	}

	@Override
	protected void removeItemCountCache(int itemId, int amount) {
		if (amount <= 0) return;

		_itemCountMap.subtract(itemId, amount);
	}

	public void updateEquipmentWindow(L1ItemInstance item) {
		final int type = item.getItem().getType2();
		if (type == 1) {
			syncWeaponWindow(_owner);
		} else if (type == 2) {
			final EquipType equipType = EquipType.get(item.getItem().getType());
			if (equipType == null) return;
			final int index = switch (equipType) {
				case RING1, RING2 -> updateAccessorySlot(item, equipType.windowIndex(), 0, RING_WINDOW_SLOTS);
				case EARRING -> updateAccessorySlot(item, equipType.windowIndex(), RING_WINDOW_SLOTS, EARRING_WINDOW_SLOTS);
				case RUNE -> updateAccessorySlot(item, equipType.windowIndex(),
						RING_WINDOW_SLOTS + EARRING_WINDOW_SLOTS, RUNE_WINDOW_SLOTS);
				default -> equipType.windowIndex();
			};
			if (index != 0) SC_EquipmentWindow.send(_owner, item.getId(), index, item.isEquipped());
		}
	}

	private int updateAccessorySlot(L1ItemInstance item, int baseIndex, int firstSlot, int slotCount) {
		final int itemId = item.getId();
		final boolean equipped = item.isEquipped();
		if (displayedAccessories == null) {
			if (!equipped) return 0;
			displayedAccessories = new int[RING_WINDOW_SLOTS + EARRING_WINDOW_SLOTS + RUNE_WINDOW_SLOTS];
		}

		int empty = -1;
		for (int i = firstSlot; i < firstSlot + slotCount; i++) {
			final int displayedId = displayedAccessories[i];
			if (displayedId == itemId) {
				if (!equipped) displayedAccessories[i] = 0;
				return baseIndex + i - firstSlot;
			}
			if (displayedId == 0 && empty < 0) empty = i;
		}
		if (!equipped || empty < 0) return 0;
		displayedAccessories[empty] = itemId;
		return baseIndex + empty - firstSlot;
	}

	private void syncWeaponWindow(L1PcInstance pc) {
		final L1EquipmentSlot slot = pc.getEquipSlot();
		final L1ItemInstance primary = slot.getWeapon();
		final L1ItemInstance secondary = slot.getSecondWeapon();
		final int primaryId = primary == null ? 0 : primary.getId();
		final int secondaryId = secondary == null ? 0 : secondary.getId();
		final int mainIndex = SC_EquipmentWindow.EQUIPMENT_INDEX_WEAPON;
		final int offIndex = SC_EquipmentWindow.EQUIPMENT_INDEX_SHIELD;

		// Empty both stale slots before putting an axe into its new position.
		if (displayedPrimaryWeapon != 0 && displayedPrimaryWeapon != primaryId) {
			if (!SC_EquipmentWindow.send(pc, displayedPrimaryWeapon, mainIndex, false)) return;
			displayedPrimaryWeapon = 0;
		}
		if (displayedSecondaryWeapon != 0 && displayedSecondaryWeapon != secondaryId) {
			if (!SC_EquipmentWindow.send(pc, displayedSecondaryWeapon, offIndex, false)) return;
			displayedSecondaryWeapon = 0;
		}
		if (displayedPrimaryWeapon != primaryId) {
			if (!SC_EquipmentWindow.send(pc, primaryId, mainIndex, true)) return;
			displayedPrimaryWeapon = primaryId;
		}
		if (displayedSecondaryWeapon != secondaryId && SC_EquipmentWindow.send(pc, secondaryId, offIndex, true)) {
			displayedSecondaryWeapon = secondaryId;
		}
	}

    /**
     * 슬롯 추적 배열 초기화.
     */
    public void resetSlotTracking() {
        displayedPrimaryWeapon = 0;
        displayedSecondaryWeapon = 0;
        if (displayedAccessories != null) Arrays.fill(displayedAccessories, 0);
    }

	public int getWeight100() {
		return calcWeight100(getWeight());
	}

	public int calcWeight100(int weight) {
		return (int) Math.clamp((long) weight * 100L / _owner.getMaxWeight(),
				Integer.MIN_VALUE, Integer.MAX_VALUE);
	}

	@Override
	public int checkAddItem(L1Item item, int count) {
		int code = super.checkAddItem(item, count);
		if (code == OK) {
			final long addedWeight = (long) item.getWeight() * count / 1000L;
			final long weight = getWeight() + addedWeight + 1L;
			final int maxWeight = _owner.getMaxWeight();
			if (weight < 0 || addedWeight < 0 || maxWeight <= 0 || weight * 100L >= 240L * maxWeight)
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

		final long addedWeight = (long) item.getItem().getWeight() * count / 1000L;
		final long weight = getWeight() + addedWeight + 1L;
		final int maxWeight = _owner.getMaxWeight();
		if (weight < 0 || addedWeight < 0 || maxWeight <= 0 || weight * 100L >= 240L * maxWeight) {
			if (message) sendOverMessage(82);
			return WEIGHT_OVER;
		}

		L1ItemInstance itemExist = findItemId(item.getItemId());
		if (itemExist != null && (itemExist.getCount() < 0 || (long) itemExist.getCount() + count > 1_800_000_000L)) {
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
                _items.Push(item);
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
	public synchronized void insertItem(L1ItemInstance item) {
        if (rejectPendingItemTransfer())
            throw new IllegalStateException("Item mutation refused while CacheD transfer pending");
		final L1PcInstance previousOwner = item._cha;
		final int itemId = item.getItemId();
		final int amount = item.getCount();
		if (amount <= 0 || (long) _itemCountMap.getOrDefault(itemId, 0) + amount > MAX_AMOUNT)
			throw new IllegalArgumentException("Invalid inventory insert quantity");
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
			item._cha = previousOwner;
			throw new IllegalStateException("CacheD inventory insert failed: item=" + item.getId(), e);
		}
				
	    _itemCountMap.add(itemId, amount);
	    
	    // [디버그 로그]
	  //  System.out.println(String.format("[아이템추가] ID: %d, 수량: %d, 맵총량: %d", itemId, amount, _itemCountMap.get(itemId)));
	    
	    
	    // Admission succeeded: notification errors must not unlink the item.
	    try {
	        SC_ADD_INVENTORY_NOTI.sendItem(_owner, item, false, _owner.getId());

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
					BookmarkSlot.LoadItemSlots(_owner, item.getItemId()));
		}
	    } catch (RuntimeException notice) {
	        RECEIPT_NOTICE_LOG.log(java.util.logging.Level.WARNING, "Accepted inventory insert notice failed", notice);
	    }
        // Expiry registration is independent of packet delivery.
        if (item.getEndTime() != null && !_owner.isLogoutStarted()
                && getItem(item.getId()) == item) {
            try {
                l1j.server.server.model.character.scheduler.PcInventoryExpiryScheduler
                        .getInstance().scheduleItem(_owner, item);
            } catch (RuntimeException expiry) {
                RECEIPT_NOTICE_LOG.log(java.util.logging.Level.SEVERE, "Accepted inventory expiry registration failed", expiry);
            }
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
        // Persist admission precedes packets; rejection leaves client unchanged.
        saveItem(item, COL_COUNT);
        try { updateItem(item, COL_COUNT); }
        catch (RuntimeException notice) {
            RECEIPT_NOTICE_LOG.log(java.util.logging.Level.WARNING,
                    "Accepted inventory count notice failed", notice);
        }
	}

    @Override
    protected void updateStoredCharge(L1ItemInstance item) {
        saveItem(item, COL_CHARGE_COUNT);
        try { updateItem(item, COL_COUNT | COL_CHARGE_COUNT); }
        catch (RuntimeException notice) {
            RECEIPT_NOTICE_LOG.log(java.util.logging.Level.WARNING,
                    "Accepted inventory charge notice failed", notice);
        }
    }

	private static final int MASK_STATUS  = COL_BLESS_LEVEL | COL_BLESS_LEVEL2 | COL_DURABILITY 
			| COL_ENCHANTLVL | COL_ATTRENCHANTLVL | COL_IS_ID | COL_ITEMID;

	private static final int MASK_NAME    = COL_EQUIPPED | COL_SPECIAL_ENCHANT 
			| COL_REMAINING_TIME | COL_CHARGE_COUNT;

	private static final int MASK_COLOR   = COL_BLESS | COL_BLESS2 | COL_IS_ID | COL_ITEMID;

	private static final int MASK_ENCHANT = COL_ENCHANTLVL | COL_ATTRENCHANTLVL;

	@Override
	public void updateDurability(L1ItemInstance item, int previousDurability) {
		final boolean defenseChanged = _owner.getEquipSlot().applyArmorDurabilityChange(item, previousDurability);
		super.updateDurability(item, previousDurability);
		if (defenseChanged) SC_OwnCharAttrDef.send(_owner);
	}

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
			java.util.logging.Logger.getLogger(CUserInventory.class.getName())
				.log(java.util.logging.Level.SEVERE, "CacheD inventory dirty submission failed: item=" + item.getId(), e);
			throw e;
		}
	}

	@Override
	public synchronized void deleteItem(L1ItemInstance item) {
        if (rejectPendingItemTransfer())
            throw new IllegalStateException("Item mutation refused while CacheD transfer pending");
        // Never submit a persistent deletion for another inventory's item.
        if (item == null || !_items.Have(item)) return;
		
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
		_itemCountMap.subtract(itemId, amount);

		// [디버그 로그]
	//	System.out.println(String.format("[아이템삭제] ID: %d, 삭제수량: %d, 맵잔량: %d", itemId, amount, _itemCountMap.getOrDefault(itemId, 0)));
		
		SC_DeleteInventoryItem.send(_owner, item);
		_items.Pop(item);
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
				// Skill end callback performs polymorph and stat teardown once.
				_owner.removeSkillEffect(L1SkillId.POLY_RING_MASTER);
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
		if (item == null) return;
		// A committed replay already holds the inventory reservation.
		final boolean reserve = !l1j.server.server.storage.CacheD.isItemTransferReplaying();
		if (reserve && !tryBeginEquipmentChange()) return;
		try {
			if (item.isEquipped() == equipped) return;
			final L1Item template = item.getItem();
			if (template == null) return;
			final L1EquipmentSlot slot = _owner.getEquipSlot();
			final int itemId = item.getItemId();
			final int type = template.getType2();
			final boolean weapon = type == 1;
			if (equipped) {
				if (weapon && !slot.hasRoomForWeapon(item)) return;
				if (type == 2 && slot.getSecondWeapon() != null
						&& (template.getType() == 7 || template.getType() == 13)) return;
				if (isInvisibilityGear(itemId) && System.currentTimeMillis() - timeVisible < timeVisibleDelay) return;
				if (type != 0 && !item.startEquipmentTimer(_owner)) return;
				try {
					if (template.getType() != 4) clearArrowSelection();
					item.setEquipped(true);
					_equippedCountMap.add(itemId, 1);
					slot.equipItem(item);
				} finally {
					if (!item.isEquipped()) item.stopEquipmentTimer();
				}
				if (!item.isEquipped()) return; // Polymorph can remove the just-equipped item.
				if (!loaded && weapon && slot.getWeaponCount() == 2) {
					SC_SkillSound.send(_owner, _owner.getId(), 12534);
				}
			} else {
				beforeUnequip(template, itemId, loaded, weapon);
				item.setEquipped(false);
				item.stopEquipmentTimer();
				_equippedCountMap.subtract(itemId, 1);
				slot.unequipItem(item);
			}

			if (!loaded) {
				_owner.setCurrentHp(_owner.getCurrentHp());
				_owner.setCurrentMp(_owner.getCurrentMp());
				updateItem(item, COL_EQUIPPED);
				if (weapon && !changeWeapon) {
					final long visual = SC_CharVisualUpdate.pack(_owner);
					SC_CharVisualUpdate.sendPacked(_owner, visual);
					SC_CharVisualUpdate.broadcastFastPacked(_owner, visual);
				}
			}
			if (weapon) SC_PacketBox.send(_owner, SC_PacketBox.공격가능거리, _owner, item);
			updateEquipmentWindow(item);
		} finally {
			if (reserve) endEquipmentChange();
		}
	}

	private static boolean isInvisibilityGear(int itemId) {
		return itemId == 20077 || itemId == 20062 || itemId == 120077;
	}

	private void clearArrowSelection() {
		final int arrowId = getArrowItemId();
		if (arrowId == 0) return;
		final L1ItemInstance arrow = findItemId(arrowId);
		setArrow(0);
		if (arrow != null) SC_ItemName.send(_owner, arrow);
	}

	private void beforeUnequip(L1Item template, int itemId, boolean loaded, boolean weapon) {
		if (!loaded && isInvisibilityGear(itemId)) {
			if (_owner.isInvisble()) {
				_owner.delInvis();
				_owner.beginInvisTimer();
			}
			timeVisible = System.currentTimeMillis();
		}
		if (weapon && template.getType1() == 4 && _owner.hasSkillEffect(L1SkillId.INFERNO)) {
			_owner.removeSkillEffect(L1SkillId.INFERNO);
		}
		if (template.isTwohandedWeapon() && _owner.hasSkillEffect(L1SkillId.COUNTER_BARRIER)) {
			_owner.removeSkillEffect(L1SkillId.COUNTER_BARRIER);
			SC_PacketBox.send(_owner, SC_PacketBox.UNLIMITED_ICON1, 71, false);
		}
		if (weapon) {
			_owner.remove_elf_second_brave();
			if (template.getType() == 4) clearArrowSelection();
		}
	}

	public boolean checkEquipped(int id) {
		return _items.IsEquippedByClassID(id);
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
		final L1ItemInstance primary = _owner.getWeapon();
		final L1ItemInstance secondary = _owner.getSecondWeapon();
		if (primary != null && !L1PolyMorph.isEquipableWeapon(polyid, primary.getItem().getType())) {
			setEquipped(primary, false, false, false, false);
		}
		if (secondary != null && !L1PolyMorph.isEquipableWeapon(polyid, secondary.getItem().getType())) {
			setEquipped(secondary, false, false, false, false);
		}
	}

	private void takeoffArmor(int polyid) {
		final L1EquipmentSlot equipment = _owner.getEquipSlot();
		for (int type = 0; type <= 12; type++) {
			L1ItemInstance armor = equipment.getAppliedArmor(type);
			if (armor == null || L1PolyMorph.isEquipableArmor(polyid, type)) continue;
			setEquipped(armor, false, false, false, false);
			if (type == 9) {
				armor = equipment.getAppliedArmor(type);
				if (armor != null) setEquipped(armor, false, false, false, false);
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
		if(!CompanionInstanceCache.is_companion_oblivion(penaltyItem.getId()))			
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
		for (L1ItemInstance item : _items) {
			if (item.getItemId() == id && item.getEnchantLevel() == enchant) {
				return removeItem(item, 1) == 1;
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
		return findEquippedItemId(id);
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
		final L1ItemInstance item = getItem(itemobjId);
		if (item == null || item.getItemId() != id || item.getEnchantLevel() != enchant
				|| item.getBless() != bless || item.getAttrEnchantLevel() != attr) return false;
		pc.getInventory().setEquipped(item, true);
		return true;
	}

	public int checkItemCount(int id) {
		return _items.GetItemCountByClassID(id);
	}

	public L1ItemInstance findItemObjId(int id) {
		return getItem(id);
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

	public synchronized boolean consumeItem(L1ItemInstance item, int count) {
        if (item == null || count <= 0 || getItem(item.getId()) != item
                || item.getCount() < count) return false;
        return removeItem(item, count) == count;
	}

	public synchronized boolean consumeItem(L1ItemInstance[] items, int[] counts) {
        if (items == null || counts == null || items.length != counts.length) return false;
        final int size = items.length;
        // Validate a whole request under the same inventory lock as item mutation.
        // Duplicate object IDs are one combined debit, not separate stale reads.
        for (int i = 0; i < size; i++) {
            final L1ItemInstance item = items[i];
            if (item == null || counts[i] <= 0 || getItem(item.getId()) != item) return false;
            boolean first = true;
            for (int j = 0; j < i; j++) {
                if (items[j].getId() == item.getId()) {
                    if (items[j] != item) return false;
                    first = false;
                    break;
                }
            }
            if (!first) continue;
            long required = counts[i];
            for (int j = i + 1; j < size; j++) {
                if (items[j] != null && items[j].getId() == item.getId()) {
                    if (items[j] != item || counts[j] <= 0) return false;
                    required += counts[j];
                }
            }
            if (required > item.getCount()) return false;
        }
        for (int i = size - 1; i >= 0; i--) {
            final L1ItemInstance item = items[i];
            boolean handled = false;
            for (int j = i + 1; j < size; j++)
                if (items[j].getId() == item.getId()) { handled = true; break; }
            if (handled) continue;
            int amount = counts[i];
            for (int j = 0; j < i; j++)
                if (items[j].getId() == item.getId()) amount += counts[j];
            if (removeItem(item, amount) != amount) return false;
        }
        return true;
	}
}
