
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
import l1j.server.server.model.network.core.sc.packet.SC_DeleteInventoryItem;
import l1j.server.server.model.network.core.sc.packet.SC_EquipmentWindow;
import l1j.server.server.model.network.core.sc.packet.SC_ItemColor;
import l1j.server.server.model.network.core.sc.packet.SC_ItemName;
import l1j.server.server.model.network.core.sc.packet.SC_ItemStatus;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharAttrDef;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_SPMR;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
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
            return DeleteItemInInventory(itemId, count);
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


	public CUserInventory(L1PcInstance owner) {
		_owner = owner;
		_arrowId = 0;
		_stingId = 0;
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
            if (GetByServerID(original.getId()) != original)
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
            final int oldAttrEnchant = original.getAttrEnchantLevel();
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
            // Expiry remains below, after cancellation of the previous timer.
            original.CopyItemAttributesFrom(image, image.getCount(), image.getChargeCount(), true, false, false);
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
            original.setLastWeight(original.getWeight());
            SetInventoryChanged(original, true, true, true,
                    oldEnchant != original.getEnchantLevel()
                            || oldAttrEnchant != original.getAttrEnchantLevel(), false);
        } else {
            if (image == null || GetByServerID(image.getId()) != null)
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
                            && _owner.getSessionStamp() == stamp && GetByServerID(objectId) == image)
                        SC_PacketBox.send(_owner, SC_PacketBox.BOOKMARK, objectId, "$13719", bookmarks);
                });
            });
        } catch (RuntimeException rejected) {
            java.util.logging.Logger.getLogger(CUserInventory.class.getName()).log(
                    java.util.logging.Level.WARNING, "Committed bookmark display could not be queued", rejected);
        }
    }

	@Override
	public int GetItemAmount(int itemId) {
		return _itemCountMap.getOrDefault(itemId, 0);
	}

	@Override
	public boolean IsEquippedByClassIDCached(int itemId) {
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
		return calcWeight100(GetCarryingWeight());
	}

	public int calcWeight100(int weight) {
		return (int) Math.clamp((long) weight * 100L / _owner.getMaxWeight(),
				Integer.MIN_VALUE, Integer.MAX_VALUE);
	}

	@Override
	public int CheckAddable(L1Item item, int count) {
		int code = super.CheckAddable(item, count);
		if (code == OK) {
			final long addedWeight = (long) item.getWeight() * count / 1000L;
			final long weight = GetCarryingWeight() + addedWeight + 1L;
			final int maxWeight = _owner.getMaxWeight();
			if (weight < 0 || addedWeight < 0 || maxWeight <= 0 || weight * 100L >= 240L * maxWeight)
				return WEIGHT_OVER;
		}
		return code;
	}

	@Override
	public int CheckAddable(L1ItemInstance item, int count) {
		return CheckAddable(item, count, true);
	}

	public int CheckAddable(L1ItemInstance item, int count, boolean message) {
        return CheckAddable(item, count, message, false);
    }

    /** Ordinary transfer uses the exact compatible stack, not a template-only row.
     * The existing warehouse prepare/replay admission policy is unchanged. */
    int CheckAddableTransfer(L1ItemInstance item, int count) {
        return CheckAddable(item, count, true, true);
    }

    private int CheckAddable(L1ItemInstance item, int count, boolean message, boolean transfer) {
		if (item == null) {
			return -1;
		}

		if (count < 0 || count > MAX_AMOUNT) {
			return AMOUNT_OVER;
		}

        final L1ItemInstance itemExist = transfer && item.isStackable()
                ? findMergeItem(item) : GetFirstItemByClassID(item.getItemId());
		if (getSize() > MAX_SIZE || (getSize() == MAX_SIZE && (!item.isStackable()
                || (transfer ? itemExist == null : !HaveItemByClassId(item.getItem().getItemId()))))) {
			if (message) {
				sendOverMessage(263);
			}
			return SIZE_OVER;
		}

		final long addedWeight = (long) item.getItem().getWeight() * count / 1000L;
		final long weight = GetCarryingWeight() + addedWeight + 1L;
		final int maxWeight = _owner.getMaxWeight();
		if (weight < 0 || addedWeight < 0 || maxWeight <= 0 || weight * 100L >= 240L * maxWeight) {
			if (message) sendOverMessage(82);
			return WEIGHT_OVER;
		}

		if (itemExist != null && (itemExist.getCount() < 0 || (long) itemExist.getCount() + count > 1_800_000_000L)) {
			SC_ServerMessage.send(getOwner(), 166, "소지하고 있는 아데나", "2,000,000,000을 초과하고 있습니다.");
			return AMOUNT_OVER;
		}

		return OK;
	}

	public void sendOverMessage(int message_id) {
		SC_ServerMessage.send(_owner, message_id);
	}

    @Override
    public void loadItems() {
        try {
            loadItems(CharactersItemStorage.create().loadItems(_owner.getId()));
        } catch (Exception e) {
            throw new IllegalStateException("CacheD character inventory load failed: " + _owner.getId(), e);
        }
    }

    public synchronized void loadItems(List<L1ItemInstance> loadedItems) {
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
                final int previousCount = _itemCountMap.getOrDefault(itemId, 0);
                final long total = (long) previousCount + count;
                if (total > MAX_AMOUNT) {
                    throw new IllegalStateException("Stored item amount overflow: " + itemId);
                }
                if (GetByServerID(item.getId()) != null)
                    throw new IllegalStateException("Duplicate stored item identity: " + item.getId());
                final L1PcInstance previousOwner = item._cha;
                final L1World world = L1World.getInstance();
                _items.Push(item);
                item._cha = _owner;
                try {
                    _itemCountMap.put(itemId, (int) total);
                    world.storeObject(item);
                } catch (RuntimeException failure) {
                    _items.Pop(item);
                    _itemCountMap.put(itemId, previousCount);
                    item._cha = previousOwner;
                    try { world.removeObject(item); }
                    catch (RuntimeException cleanup) {
                        if (cleanup != failure) failure.addSuppressed(cleanup);
                    }
                    throw failure;
                }
                if (itemId == 900075) {
                    _owner.setPolyRingMaster(true);
                    try { SC_Ability.send(_owner, 2, true); }
                    catch (RuntimeException notice) { receiptNoticeFailed("restore-ability-2", notice); }
                    try { SC_Ability.send(_owner, 7, true); }
                    catch (RuntimeException notice) { receiptNoticeFailed("restore-ability-7", notice); }
                }
            }
		} catch (Exception e) {
			throw new IllegalStateException("CacheD character inventory restore failed: " + _owner.getId(), e);
		}
	}

    /** Logout cleanup: membership and both primitive indexes retire together.
     * Equipment restore still owns initial equipped-state publication.
     */
    @Override
    public synchronized void clearItems() {
        super.clearItems();
        _itemCountMap.clear();
        _equippedCountMap.clear();
        displayedAccessories = null;
        displayedPrimaryWeapon = 0;
        displayedSecondaryWeapon = 0;
    }

	@Override
	public synchronized void insertItem(L1ItemInstance item) {
        if (rejectPendingItemTransfer())
            throw new IllegalStateException("Item mutation refused while CacheD transfer pending");
		final L1PcInstance previousOwner = item._cha;
        final int previousBless = item.getBless();
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
            item.setBless(previousBless);
			throw new IllegalStateException("CacheD inventory insert failed: item=" + item.getId(), e);
		}
				
	    _itemCountMap.add(itemId, amount);
	    
	    // [디버그 로그]
	  //  System.out.println(String.format("[아이템추가] ID: %d, 수량: %d, 맵총량: %d", itemId, amount, _itemCountMap.get(itemId)));
	    
	    
	    if (itemId == 900075) _owner.setPolyRingMaster(true);
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
                && GetByServerID(item.getId()) == item) {
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

        // A template conversion can reduce the item's weight to zero.
        // The resulting total must still be published in that case.
        SetInventoryChanged(item, sendStatus, sendName, sendColor, sendEnchant,
                sendWeight && ((column & COL_ITEMID) != 0 || item.getItem().getWeight() != 0));
    }

    /**
     * Single output path for ordinary and receipt-confirmed item changes.
     * OFF SetInventoryChanged records the change kind; L1 sends its existing
     * protocol-specific frames immediately at the established owner boundary.
     * No delayed item reference, collection, or second execution lane.
     */
    private void SetInventoryChanged(L1ItemInstance item, boolean status, boolean name,
            boolean color, boolean enchant, boolean weight) {
        if (status) {
            try { SC_ItemStatus.send(_owner, item); }
            catch (RuntimeException failure) { receiptNoticeFailed("item-status", failure); }
        }
        if (name) {
            try { SC_ItemName.send(_owner, item); }
            catch (RuntimeException failure) { receiptNoticeFailed("item-name", failure); }
        }
        if (color) {
            try { SC_ItemColor.send(_owner, item); }
            catch (RuntimeException failure) { receiptNoticeFailed("item-color", failure); }
        }
        if (enchant) {
            try { SC_PacketBox.send(_owner, SC_PacketBox.ITEM_ENCHANT_UPDATE, item); }
            catch (RuntimeException failure) { receiptNoticeFailed("item-enchant", failure); }
        }
        if (weight) {
            try { SC_Weight.send(_owner); }
            catch (RuntimeException failure) { receiptNoticeFailed("weight", failure); }
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
	public synchronized void Pop(L1ItemInstance item) {
        if (rejectPendingItemTransfer())
            throw new IllegalStateException("Item mutation refused while CacheD transfer pending");
        if (item == null || !_items.Have(item)) return;

        // Acquire the same short reservation used by setEquipped before
        // accepting a deletion that also needs to retire equipped state.
        final boolean reserve = item.isEquipped()
                && !l1j.server.server.storage.CacheD.isItemTransferReplaying();
        if (reserve && !tryBeginEquipmentChange())
            throw new IllegalStateException("Equipment change busy during inventory delete");
        try {
            deleteOwnedItem(item);
        } finally {
            if (reserve) endEquipmentChange();
        }
    }

    private void deleteOwnedItem(L1ItemInstance item) {
        persistOwnedItemDelete(item);
        finishOwnedItemDeletion(item);
    }

    /** The ordinary drop path stages ground membership before admitting this command. */
    void persistOwnedItemDelete(L1ItemInstance item) {
        assert Thread.holdsLock(this);
        try {
            CharactersItemStorage.create().deleteItem(item);
        } catch (Exception e) {
            throw new IllegalStateException("CacheD inventory delete failed: item=" + item.getId(), e);
        }
    }

    void finishOwnedItemDeletion(L1ItemInstance item) {
        assert Thread.holdsLock(this);
        if (item.isEquipped()) {
            _owner.UnequipItem(item);
        }

        final int itemId = item.getItemId();
        _itemCountMap.subtract(itemId, item.getCount());
        _items.Pop(item);

        if (item.getEndTime() != null) {
            try {
                l1j.server.server.model.character.scheduler.PcInventoryExpiryScheduler
                        .getInstance().cancelItem(_owner, item);
            } catch (RuntimeException failure) {
                RECEIPT_NOTICE_LOG.log(java.util.logging.Level.SEVERE,
                        "Accepted inventory expiry cancellation failed", failure);
            }
        }

        if (itemId == 900075) {
            _owner.setPolyRingMaster(false);
            if (_owner.hasSkillEffect(L1SkillId.POLY_RING_MASTER)) {
                // The skill end callback owns modifier and visual retirement.
                try { _owner.removeSkillEffect(L1SkillId.POLY_RING_MASTER); }
                catch (RuntimeException failure) { receiptNoticeFailed("poly-ring retirement", failure); }
            }
        }

        // Notifications cannot undo an accepted deletion or prevent the
        // caller from unregistering or transferring the detached object.
        try { SC_DeleteInventoryItem.send(_owner, item); }
        catch (RuntimeException failure) { receiptNoticeFailed("delete", failure); }
        if (item.getItem().getWeight() != 0) {
            try { SC_Weight.send(_owner); }
            catch (RuntimeException failure) { receiptNoticeFailed("weight", failure); }
        }
        if (itemId == 900075) {
            try { SC_Ability.send(_owner, 2, false); }
            catch (RuntimeException failure) { receiptNoticeFailed("ability-off-2", failure); }
            try { SC_Ability.send(_owner, 7, false); }
            catch (RuntimeException failure) { receiptNoticeFailed("ability-off-7", failure); }
        }
    }


	/** Update only the inventory's equipped-template index, under the existing item gate. */
	public void SetEquippedCount(int itemId, boolean equipped) {
		if (equipped) _equippedCountMap.add(itemId, 1);
		else _equippedCountMap.subtract(itemId, 1);
	}

	public boolean IsEquippedByClassID(int id) {
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

	@Deprecated
	public void takeoffEquip(int polyid) {
		_owner.TakeOffItems(polyid);
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
			bullet = GetFirstItemByClassID(priorityId);
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

		_owner.UnequipItem(penaltyItem, false, true);
		return penaltyItem;
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



	public boolean IsEquippedByClassID(int[] ids) {
		for (int id : ids) {
			if (!IsEquippedByClassID(id)) {
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

	public synchronized boolean DeleteItemInInventory(L1ItemInstance item, int count) {
        if (item == null || count <= 0 || GetByServerID(item.getId()) != item
                || item.getCount() < count) return false;
        return DeleteItemAmount(item, count) == count;
	}

	public synchronized boolean DeleteItemInInventory(L1ItemInstance[] items, int[] counts) {
        if (items == null || counts == null || items.length != counts.length) return false;
        final int size = items.length;
        // Validate a whole request under the same inventory lock as item mutation.
        // Duplicate object IDs are one combined debit, not separate stale reads.
        for (int i = 0; i < size; i++) {
            final L1ItemInstance item = items[i];
            if (item == null || counts[i] <= 0 || GetByServerID(item.getId()) != item) return false;
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
            if (DeleteItemAmount(item, amount) != amount) return false;
        }
        return true;
	}
}
