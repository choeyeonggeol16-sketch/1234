package l1j.server.server.model.inventory;

import static l1j.server.server.model.inventory.EquipmentTraits.Flag.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import l1j.server.server.datatables.SkillsTable;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.BraveSpeedEffects;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.MovementSpeedEffects;
import l1j.server.server.model.character.skill.passive.PassiveID;
import l1j.server.server.model.item.EquipmentBlessing;
import l1j.server.server.model.item.ItemEffectRegistry;
import l1j.server.server.model.item.ItemStat;
import l1j.server.server.model.item.function.ItemBonusHooks;
import l1j.server.server.model.network.core.sc.packet.SC_Ability;
import l1j.server.server.model.network.core.sc.packet.SC_AddSkill;
import l1j.server.server.model.network.core.sc.packet.SC_DelSkill;
import l1j.server.server.model.network.core.sc.packet.SC_InventoryIcon;
import l1j.server.server.model.network.core.sc.packet.SC_ItemStatus;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharAttrDef;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_EquipmentWindow;
import l1j.server.server.model.network.core.sc.packet.SC_SPMR;
import l1j.server.server.model.network.core.sc.packet.SC_SkillHaste;
import l1j.server.server.model.network.core.sc.packet.SC_SkillIconBlessOfEva;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_CharVisualUpdate;
import l1j.server.server.model.network.core.sc.packet.SC_ItemName;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.storage.CacheD;
import l1j.server.server.templates.L1Item;
import l1j.server.server.utils.eKind;
public final class CUserEquipment {
    private static final java.util.logging.Logger NOTICE_LOG =
            java.util.logging.Logger.getLogger(CUserEquipment.class.getName());

    private void equipmentNoticeFailed(RuntimeException notice) {
        NOTICE_LOG.log(java.util.logging.Level.WARNING,
                "Equipment notice failed: " + _owner.getId(), notice);
    }


	private final L1PcInstance _owner;
	// PC 전용 장비 슬롯 추적
	private static final int RING_WINDOW_SLOTS = 4;
	private static final int EARRING_WINDOW_SLOTS = 2;
	private static final int RUNE_WINDOW_SLOTS = 3;
	private int[] accessorySlots;
	private int[] lastSentAccessories;
	private int displayedPrimaryWeapon;
	private int displayedSecondaryWeapon;

	private long invisibleGearRemovedAtMs;

	private static final List<CSetItem> EMPTY_ACTIVE_SETS = List.of();
	private List<CSetItem> activeSets = EMPTY_ACTIVE_SETS;
	private CSetItem polymorphSet;
	private int polymorphSetSprite;
	private final ArrayList<L1ItemInstance> _armors = new ArrayList<>();
	private static final int ARMOR_BONUS_WIDTH = 4;
	private enum ClassArmorBonus { MELEE, MAGIC, BOW }

	private static final ClassArmorBonus[] CLASS_ARMOR_BONUSES = ClassArmorBonus.values();
	// Object ID, applied HP, applied MP and class option for state-dependent armor only.
	private int[] appliedArmorBonuses;
	private int appliedArmorBonusSize;

	public int worldjoin_weapon_idx = 0;

	public CUserEquipment(L1PcInstance owner) {
		_owner = owner;
	}

    public void useItem(L1ItemInstance item) {
        if (item == null) return;
        final L1Item template = item.getItem();
        if (template == null) return;
        final int type = template.getType2();
        if (type != 1 && type != 2) return;

        // Weapon and armor requests have different legacy rejection precedence.
        if (type == 1 && !checkLevel(template)) return;
        if (!EquipRules.canUseByClass(_owner, template)) {
            SC_ServerMessage.send(_owner, 264);
            return;
        }
        if (type == 2 && !checkLevel(template)) return;

        final CUserInventory inventory = _owner.getInventory();
        if (!inventory.tryBeginEquipmentChange()) return;
        try {
            // Reject a stale request before it can retire the currently worn item.
            if (inventory.GetByServerID(item.getId()) != item || item.getItem() != template) return;
            if (type == 1) useWeapon(inventory, item);
            else useArmor(inventory, item);
        } finally {
            inventory.endEquipmentChange();
        }
    }

    private boolean checkLevel(L1Item template) {
        final int min = template.getMinLevel();
        final int max = template.getMaxLevel();
        if (min != 0 && min > _owner.getLevel()) {
            SC_ServerMessage.send(_owner, 318, String.valueOf(min));
            return false;
        }
        if (max != 0 && max < _owner.getLevel()) {
            if (max < 50) SC_PacketBox.send(_owner, SC_PacketBox.MSG_LEVEL_OVER, max);
            else SC_SystemMessage.send(_owner, "이 아이템은" + max + "레벨 이하만 사용할 수 있습니다. ");
            return false;
        }
        return true;
    }

    private void useArmor(CUserInventory inventory, L1ItemInstance armor) {
        if (armor.isEquipped()) {
            applyEquipment(inventory, armor, false, false, false);
            return;
        }
        final EquipResult result = EquipRules.canEquipArmor(_owner, inventory, armor);
        if (!result.ok) {
            result.applyFail(_owner);
            return;
        }
        _owner.cancelAbsoluteBarrier();
        applyEquipment(inventory, armor, true, false, false);
    }

    private void useWeapon(CUserInventory inventory, L1ItemInstance weapon) {
        final L1ItemInstance current = getWeapon();
        final boolean alreadyWorn = isWeapon(weapon);
        if (current == null || !alreadyWorn) {
            final EquipResult result = EquipRules.canEquipWeaponBasic(_owner, inventory, weapon);
            if (!result.ok) {
                result.applyFail(_owner);
                return;
            }
        }

        _owner.cancelAbsoluteBarrier();
        if (current == null) {
            equipSelectedWeapon(inventory, weapon);
            return;
        }
        if (isCursed(current) || (alreadyWorn && isCursed(weapon))) {
            SC_ServerMessage.send(_owner, 150);
            return;
        }
        if (alreadyWorn) {
            applyEquipment(inventory, weapon, false, false, false);
            return;
        }

        final EquipResult secondSlot = EquipRules.canEquipAsSecondWeaponSlayer(_owner, inventory, current, weapon);
        if (secondSlot.ok) {
            equipSelectedWeapon(inventory, weapon);
            return;
        }
        if (secondSlot.fail != null) {
            secondSlot.applyFail(_owner);
            return;
        }

        final L1ItemInstance secondary = getSecondWeapon();
        if (isCursed(secondary)) {
            SC_ServerMessage.send(_owner, 150);
            return;
        }
        if (!weapon.startEquipmentTimer(_owner)) return;
        try {
            applyEquipment(inventory, current, false, false, true);
            if (secondary != null) applyEquipment(inventory, secondary, false, false, true);
            equipSelectedWeapon(inventory, weapon);
        } finally {
            if (!weapon.isEquipped()) weapon.stopEquipmentTimer();
        }
    }

    private void equipSelectedWeapon(CUserInventory inventory, L1ItemInstance weapon) {
        final int equipMessage = EquipmentTraits.forItem(weapon.getItemId()).equipMessage();
        if (equipMessage != 0) {
            try { SC_ServerMessage.send(_owner, equipMessage, weapon.getLogName()); }
            catch (RuntimeException notice) { equipmentNoticeFailed(notice); }
        }
        applyEquipment(inventory, weapon, true, false, false);
    }

    private static boolean isCursed(L1ItemInstance weapon) {
        return weapon != null && weapon.getItem().getBless() == 2;
    }

    public void changeEquipment(L1ItemInstance item,
            boolean equipped, boolean loaded, boolean changingWeapon) {
        if (item == null) return;
        final CUserInventory inventory = _owner.getInventory();
        // Receipt replay already owns the inventory reservation.
        final boolean reserve = !CacheD.isItemTransferReplaying();
        if (reserve && !inventory.tryBeginEquipmentChange()) return;
        try {
            applyEquipment(inventory, item, equipped, loaded, changingWeapon);
        } finally {
            if (reserve) inventory.endEquipmentChange();
        }
    }

    private void applyEquipment(CUserInventory inventory, L1ItemInstance item,
            boolean equipped, boolean loaded, boolean changingWeapon) {
        if (inventory.GetByServerID(item.getId()) != item || item.isEquipped() == equipped) return;
        final L1Item template = item.getItem();
        if (template == null) return;

        final int itemId = item.getItemId();
        final boolean invisibleGear = EquipmentTraits.forItem(itemId).has(EquipmentTraits.Flag.INVISIBILITY);
        final int type = template.getType2();
        final boolean weapon = type == 1;
        if (equipped) {
            if (weapon && !hasRoomForWeapon(item)) return;
            if (type == 2 && getSecondWeapon() != null
                    && (template.getType() == 7 || template.getType() == 13)) return;
            if (invisibleGear && isInvisibilityEquipCoolingDown()) return;
            if (type != 0 && !item.startEquipmentTimer(_owner)) return;
            try {
                if (template.getType() != 4) clearArrowSelection(inventory);
                item.setEquipped(true);
                inventory.SetEquippedCount(itemId, true);
                equipItem(item);
            } finally {
                if (!item.isEquipped()) item.stopEquipmentTimer();
            }
            // A set polymorph may synchronously remove the newly applied item.
            if (!item.isEquipped()) return;
            if (!loaded && weapon && getWeaponCount() == 2) {
                try { SC_SkillSound.send(_owner, _owner.getId(), 12534); }
                catch (RuntimeException notice) { equipmentNoticeFailed(notice); }
            }
        } else {
            beforeUnequip(inventory, template, invisibleGear, loaded, weapon);
            item.setEquipped(false);
            item.stopEquipmentTimer();
            inventory.SetEquippedCount(itemId, false);
            unequipItem(item);
        }
        sendEquipmentChanged(inventory, item, loaded, changingWeapon, weapon);
    }

    private void sendEquipmentChanged(CUserInventory inventory,
            L1ItemInstance item, boolean loaded, boolean changingWeapon, boolean weapon) {
        if (!loaded) {
            _owner.setCurrentHp(_owner.getCurrentHp());
            _owner.setCurrentMp(_owner.getCurrentMp());
            try { inventory.updateItem(item, CUserInventory.COL_EQUIPPED); }
            catch (RuntimeException notice) { equipmentNoticeFailed(notice); }
            if (weapon && !changingWeapon) {
                try { sendWeaponAppearance(); }
                catch (RuntimeException notice) { equipmentNoticeFailed(notice); }
            }
        }
        if (weapon) {
            try { SC_PacketBox.send(_owner, SC_PacketBox.공격가능거리, _owner, item); }
            catch (RuntimeException notice) { equipmentNoticeFailed(notice); }
        }
        try { updateEquipmentWindow(item); }
        catch (RuntimeException notice) { equipmentNoticeFailed(notice); }
    }

    private void sendWeaponAppearance() {
        final long visual = SC_CharVisualUpdate.pack(_owner);
        try { SC_CharVisualUpdate.sendPacked(_owner, visual); }
        catch (RuntimeException notice) { equipmentNoticeFailed(notice); }
        SC_CharVisualUpdate.broadcastFastPacked(_owner, visual);
    }

    private void clearArrowSelection(CUserInventory inventory) {
        final int arrowId = inventory.getArrowItemId();
        if (arrowId == 0) return;
        final L1ItemInstance arrow = inventory.GetFirstItemByClassID(arrowId);
        inventory.setArrow(0);
        if (arrow != null) {
            try { SC_ItemName.send(_owner, arrow); }
            catch (RuntimeException notice) { equipmentNoticeFailed(notice); }
        }
    }

    private void beforeUnequip(CUserInventory inventory,
            L1Item template, boolean invisibleGear, boolean loaded, boolean weapon) {
        if (!loaded && invisibleGear) {
            if (_owner.isInvisble()) {
                _owner.delInvis();
                _owner.beginInvisTimer();
            }
            markInvisibilityGearRemoved();
        }
        if (weapon && template.getType1() == 4 && _owner.hasSkillEffect(L1SkillId.INFERNO)) {
            _owner.removeSkillEffect(L1SkillId.INFERNO);
        }
        if (template.isTwohandedWeapon() && _owner.hasSkillEffect(L1SkillId.COUNTER_BARRIER)) {
            _owner.removeSkillEffect(L1SkillId.COUNTER_BARRIER);
            try { SC_PacketBox.send(_owner, SC_PacketBox.UNLIMITED_ICON1, 71, false); }
            catch (RuntimeException notice) { equipmentNoticeFailed(notice); }
        }
        if (weapon) {
            _owner.remove_elf_second_brave();
            if (template.getType() == 4) clearArrowSelection(inventory);
        }
    }

    public void restoreEquippedItems(List<L1ItemInstance> loadedEquipment) {
        final CUserInventory inventory = _owner.getInventory();
        if (!inventory.tryBeginEquipmentChange())
            throw new IllegalStateException("Equipment restore busy: " + _owner.getId());
        try {
            for (int i = 0, size = loadedEquipment.size(); i < size; i++) {
                final L1ItemInstance item = loadedEquipment.get(i);
                if (!item.isEquipped() || inventory.GetRegisteredItemByServerID(item.getId()) != item) continue;
                item.setEquipped(false);
                final L1Item template = item.getItem();
                if (template == null || (template.getType2() != 1 && template.getType2() != 2)) {
                    NOTICE_LOG.warning("Invalid persisted equipment: " + item.getId());
                    continue;
                }
                applyEquipment(inventory, item, true, true, false);
            }
        } finally {
            inventory.endEquipmentChange();
        }
    }

    public void takeOffItems(int polyId) {
        final L1ItemInstance primary = getWeapon();
        final L1ItemInstance secondary = getSecondWeapon();
        if (primary != null && !L1PolyMorph.isEquipableWeapon(polyId, primary.getItem().getType())) {
            _owner.UnequipItem(primary);
        }
        if (secondary != null && !L1PolyMorph.isEquipableWeapon(polyId, secondary.getItem().getType())) {
            _owner.UnequipItem(secondary);
        }
        for (int type = 0; type <= 12; type++) {
            if (L1PolyMorph.isEquipableArmor(polyId, type)) continue;
            L1ItemInstance armor;
            while ((armor = getAppliedArmor(type)) != null) {
                _owner.UnequipItem(armor);
                if (armor.isEquipped()) break;
            }
        }
    }

    private boolean isInvisibilityEquipCoolingDown() {
        return System.currentTimeMillis() - invisibleGearRemovedAtMs < 3_000L;
    }

    private void markInvisibilityGearRemoved() {
        invisibleGearRemovedAtMs = System.currentTimeMillis();
    }

	void updateEquipmentWindow(L1ItemInstance item) {
		final int type = item.getItem().getType2();
		if (type == 1) {
			syncWeaponWindow();
		} else if (type == 2) {
			final EquipType equipType = EquipType.get(item.getItem().getType());
			if (equipType == null) return;
			switch (equipType) {
				case RING1, RING2 -> publishAccessory(item, 0, RING_WINDOW_SLOTS);
				case EARRING -> publishAccessory(item, RING_WINDOW_SLOTS, EARRING_WINDOW_SLOTS);
				case RUNE -> publishAccessory(item,
						RING_WINDOW_SLOTS + EARRING_WINDOW_SLOTS, RUNE_WINDOW_SLOTS);
				default -> SC_EquipmentWindow.send(_owner, item.getId(), equipType.windowIndex(), item.isEquipped());
			}
		}
	}

    private void publishAccessory(L1ItemInstance item, int firstSlot, int slotCount) {
        final boolean equipped = item.isEquipped();
        if (accessorySlots == null) {
            if (!equipped) return;
            accessorySlots = new int[RING_WINDOW_SLOTS + EARRING_WINDOW_SLOTS + RUNE_WINDOW_SLOTS];
        }
        final int itemId = item.getId();
        int index = -1;
        for (int i = firstSlot; i < firstSlot + slotCount; i++) {
            if (accessorySlots[i] == itemId) {
                index = i;
                break;
            }
            if (equipped && index < 0 && accessorySlots[i] == 0) index = i;
        }
        if (index < 0) return;

        final int previous = accessorySlots[index];
        accessorySlots[index] = equipped ? itemId : 0;
        if (lastSentAccessories != null) {
            syncAccessoryWindow();
            return;
        }
        boolean sent = false;
        try {
            sent = SC_EquipmentWindow.send(_owner, itemId, accessoryWindowIndex(index), equipped);
        } finally {
            // Keep a sent-state copy only when the transport refused a frame.
            if (!sent) {
                lastSentAccessories = accessorySlots.clone();
                lastSentAccessories[index] = previous;
            }
        }
    }

    private void syncAccessoryWindow() {
        for (int i = 0; i < accessorySlots.length; i++) {
            final int previous = lastSentAccessories[i];
            if (previous != 0 && previous != accessorySlots[i]) {
                if (!SC_EquipmentWindow.send(_owner, previous, accessoryWindowIndex(i), false)) return;
                lastSentAccessories[i] = 0;
            }
        }
        for (int i = 0; i < accessorySlots.length; i++) {
            final int current = accessorySlots[i];
            if (current != 0 && current != lastSentAccessories[i]) {
                if (!SC_EquipmentWindow.send(_owner, current, accessoryWindowIndex(i), true)) return;
                lastSentAccessories[i] = current;
            }
        }
        lastSentAccessories = null;
    }

    private static int accessoryWindowIndex(int index) {
        if (index < RING_WINDOW_SLOTS) return SC_EquipmentWindow.EQUIPMENT_INDEX_RING1 + index;
        if (index < RING_WINDOW_SLOTS + EARRING_WINDOW_SLOTS)
            return SC_EquipmentWindow.EQUIPMENT_INDEX_EARRING + index - RING_WINDOW_SLOTS;
        return SC_EquipmentWindow.EQUIPMENT_INDEX_RUNE1 + index - RING_WINDOW_SLOTS - EARRING_WINDOW_SLOTS;
    }

	private void syncWeaponWindow() {
		final L1ItemInstance primary = getWeapon();
		final L1ItemInstance secondary = getSecondWeapon();
		final int primaryId = primary == null ? 0 : primary.getId();
		final int secondaryId = secondary == null ? 0 : secondary.getId();
		final int mainIndex = SC_EquipmentWindow.EQUIPMENT_INDEX_WEAPON;
		final int offIndex = SC_EquipmentWindow.EQUIPMENT_INDEX_SHIELD;

		// Empty both stale slots before putting an axe into its new position.
		if (displayedPrimaryWeapon != 0 && displayedPrimaryWeapon != primaryId) {
			if (!SC_EquipmentWindow.send(_owner, displayedPrimaryWeapon, mainIndex, false)) return;
			displayedPrimaryWeapon = 0;
		}
		if (displayedSecondaryWeapon != 0 && displayedSecondaryWeapon != secondaryId) {
			if (!SC_EquipmentWindow.send(_owner, displayedSecondaryWeapon, offIndex, false)) return;
			displayedSecondaryWeapon = 0;
		}
		if (displayedPrimaryWeapon != primaryId) {
			if (!SC_EquipmentWindow.send(_owner, primaryId, mainIndex, true)) return;
			displayedPrimaryWeapon = primaryId;
		}
		if (displayedSecondaryWeapon != secondaryId && SC_EquipmentWindow.send(_owner, secondaryId, offIndex, true)) {
			displayedSecondaryWeapon = secondaryId;
		}
	}

    /**
     * 슬롯 추적 배열 초기화.
     */
    private void resetSlotTracking() {
        displayedPrimaryWeapon = 0;
        displayedSecondaryWeapon = 0;
        if (accessorySlots != null) Arrays.fill(accessorySlots, 0);
        lastSentAccessories = null;
    }

    void clearEquipmentWindow() {
        accessorySlots = null;
        resetSlotTracking();
    }

    /** Rebuild the login equipment window from applied slots, not inventory rows. */
    void sendEquipmentWindow(CUserInventory inventory) {
        if (!inventory.tryBeginEquipmentChange())
            throw new IllegalStateException("Equipment window restore busy: " + _owner.getId());
        try {
            final List<L1ItemInstance> armors = getArmors();
            final L1ItemInstance[] selected = new L1ItemInstance[armors.size() + getWeaponCount()];
            int count = 0;
            final L1ItemInstance primary = getWeapon();
            final L1ItemInstance secondary = getSecondWeapon();
            if (primary != null) selected[count++] = primary;
            if (secondary != null) selected[count++] = secondary;
            for (int i = 0, n = armors.size(); i < n; i++) selected[count++] = armors.get(i);
            if (count != selected.length || inventory.GetEquippedObjectCount() != count
                    || !inventory.SortSelectedByInventoryOrder(selected, count))
                throw new IllegalStateException("Equipment slot restore mismatch: " + _owner.getId());
            for (int i = 0; i < count; i++) {
                final L1ItemInstance item = selected[i];
                if (!item.isEquipped() || inventory.GetRegisteredItemByServerID(item.getId()) != item
                        || (i > 0 && selected[i - 1] == item))
                    throw new IllegalStateException("Invalid applied equipment: " + item.getId());
            }
            resetSlotTracking();
            for (int i = 0; i < count; i++) {
                try { updateEquipmentWindow(selected[i]); }
                catch (RuntimeException notice) { equipmentNoticeFailed(notice); }
            }
        } finally {
            inventory.endEquipmentChange();
        }
    }

	private void setWeapon(L1ItemInstance weapon) {
        EquipmentEnchantOptions.applyWeaponOptions(_owner, weapon.getItem(), weapon.getEnchantLevel(), 1, null);
        addWeaponToSlots(weapon);
        applyWeaponBlessBonus(weapon, 1);
    }

    private void applyWeaponBlessBonus(L1ItemInstance weapon, int sign) {
        final int sp = EquipmentBlessing.weaponSp(weapon);
        if (sp == 0) return;
        ItemStat.SP.apply(_owner, sp * sign);
        try { SC_SPMR.send(_owner); }
        catch (RuntimeException notice) { equipmentNoticeFailed(notice); }
    }

	public boolean canUseSecondWeaponBySlayer(final L1ItemInstance left, final L1ItemInstance right) {
	    if (_owner == null || left == null || right == null) return false;

	    // 슬레이어 패시브
	    if (!_owner.isPassive(PassiveID.SLAYER.toInt())) return false;

	    // type1 == 11, type == 6 (한손도끼)
	    if (left.getItem() == null || right.getItem() == null) return false;

	    return left.getItem().getType1() == 11
	        && right.getItem().getType1() == 11
	        && left.getItem().getType() == 6
	        && right.getItem().getType() == 6;
	}

	private void publishWeapons(L1ItemInstance primary, L1ItemInstance secondary) {
		_owner.setWeapon(primary);
		_owner.setSecondWeapon(secondary);
		_owner.setCurrentWeapon(secondary != null ? 88 : primary == null ? 0 : primary.getItem().getType1());
	}

	public boolean hasRoomForWeapon(L1ItemInstance weapon) {
		final L1ItemInstance primary = getWeapon();
		if (primary == null) return true;
		return getSecondWeapon() == null && canUseSecondWeaponBySlayer(primary, weapon)
				&& getAppliedArmor(7) == null && getAppliedArmor(13) == null;
	}

	private void addWeaponToSlots(L1ItemInstance weapon) {
		final L1ItemInstance primary = getWeapon();
		if (primary == null) publishWeapons(weapon, null);
		else publishWeapons(primary, weapon);
	}

	private void removeWeaponFromSlots(L1ItemInstance weapon) {
		final L1ItemInstance primary = getWeapon();
		final L1ItemInstance secondary = getSecondWeapon();
		if (weapon == primary) publishWeapons(secondary, null);
		else if (weapon == secondary) publishWeapons(primary, null);
	}

	private int weaponSwapIndex;

	public L1ItemInstance getWeapon() {
		return _owner.getWeapon();
	}

	public L1ItemInstance getSecondWeapon() {
		return _owner.getSecondWeapon();
	}

	void restorePrimaryWeapon(L1ItemInstance weapon) {
		if (getSecondWeapon() == weapon) publishWeapons(weapon, getWeapon());
	}

	boolean hasAppliedItem(int itemId) {
		for (int i = 0; i < _armors.size(); i++) {
			final L1ItemInstance armor = _armors.get(i);
			if (armor.isEquipped() && armor.getItemId() == itemId) return true;
		}
		return isAppliedItem(getWeapon(), itemId) || isAppliedItem(getSecondWeapon(), itemId);
	}

	private static boolean isAppliedItem(L1ItemInstance item, int itemId) {
		return item != null && item.isEquipped() && item.getItemId() == itemId;
	}

	int equippedSetRingCopies(int itemId) {
		int copies = 0;
		for (int i = 0; i < _armors.size(); i++) {
			final L1ItemInstance armor = _armors.get(i);
			if (armor.isEquipped() && armor.getItemId() == itemId && armor.getItem().getType() == 9) copies++;
		}
		return copies;
	}

	public boolean hasAppliedArmor(int type) {
		return getAppliedArmor(type) != null;
	}

	public L1ItemInstance getAppliedArmor(int type) {
		for (int i = 0; i < _armors.size(); i++) {
			final L1ItemInstance armor = _armors.get(i);
			if (armor.isEquipped() && armor.getItem().getType() == type) return armor;
		}
		return null;
	}

	public L1ItemInstance getWeaponSwap() {
		final L1ItemInstance primary = getWeapon();
		final L1ItemInstance secondary = getSecondWeapon();
		return secondary == null || (weaponSwapIndex++ & 1) == 0 ? primary : secondary;
	}

	public boolean isWeapon(L1ItemInstance weapon) {
		return weapon != null && (getWeapon() == weapon || getSecondWeapon() == weapon);
	}

	public int getWeaponCount() {
		if (getSecondWeapon() != null) return 2;
		return getWeapon() == null ? 0 : 1;
	}

	public boolean hasTimedEquipment() {
		if (isTimedEquipment(getWeapon()) || isTimedEquipment(getSecondWeapon())) return true;
		for (int i = 0; i < _armors.size(); i++) {
			if (isTimedEquipment(_armors.get(i))) return true;
		}
		return false;
	}

	private static boolean isTimedEquipment(L1ItemInstance item) {
		return item != null && item.isEquipped() && item.getItem().getMaxUseTime() > 0;
	}


    private void setArmor(L1ItemInstance armor) {
        final L1Item item = armor.getItem();
        final int itemId = item.getItemId();
        final EquipmentTraits traits = EquipmentTraits.forItem(itemId);
        final int enchant = armor.getEnchantLevel();

        if (traits.has(HALPHAS)) {
            final int seconds = _owner.getSkillEffectTimeSec(L1SkillId.할파스의신의);
            try {
                if (seconds >= 60) SC_InventoryIcon.icoNew(_owner, L1SkillId.할파스의신의, 3942, seconds, true);
                else SC_InventoryIcon.iconNewUnLimit(_owner, L1SkillId.할파스의신의1, 3943, true);
            } catch (RuntimeException notice) {
                equipmentNoticeFailed(notice);
            }
        }
        EquipmentBlessing.applyArmor(_owner, armor, 1);
        final int classBonus = applyArmorVitalBonus(armor, item, traits);
        if (traits.equippedSkill() != 0) _owner.setSkillEffect(traits.equippedSkill(), -1);
        applyArmorStats(armor, item, enchant, classBonus, 1);
        _armors.add(armor);
        validateSetItem(itemId, true, item.getType() == 9, false);

        if (traits.has(MAP_BREATH_ICON) && _owner.getMapId() >= 1708 && _owner.getMapId() <= 1712) {
            try { SC_SkillSound.send(_owner, _owner.getId(), 11101); }
            catch (RuntimeException notice) { equipmentNoticeFailed(notice); }
            try { SC_PacketBox.send(_owner, SC_PacketBox.UNLIMITED_ICON1, 484, true); }
            catch (RuntimeException notice) { equipmentNoticeFailed(notice); }
        }
        if (traits.has(INVISIBILITY) && !_owner.hasSkillEffect(L1SkillId.DESPERADO)) {
            _owner.invisibleItem();
        }
        sendArmorAbility(armor, traits.ability(), true);
        if (traits.has(WATER_BREATHING)) {
            try { SC_SkillIconBlessOfEva.send(_owner, _owner.getId(), -1); }
            catch (RuntimeException notice) { equipmentNoticeFailed(notice); }
        }

        applyArmorSlotOptions(item, traits, enchant, 1);
    }

	public ArrayList<L1ItemInstance> getArmors() {
		return _armors;
	}

	private void removeWeapon(L1ItemInstance weapon) {
        final L1Item item = weapon.getItem();
        final int type = item.getType();
        EquipmentEnchantOptions.applyWeaponOptions(_owner, item, weapon.getEnchantLevel(), -1, null);

        if (_owner.hasSkillEffect(L1SkillId.이뮨투함블레이드) && suppliesWeaponImmunity(_owner.getType(), type)) {
            _owner.removeSkillEffect(L1SkillId.이뮨투함블레이드);
        }

		if (_owner.hasSkillEffect(L1SkillId.INFERNO)) {
			_owner.removeSkillEffect(L1SkillId.INFERNO);
		}

		if (_owner.hasSkillEffect(L1SkillId.COUNTER_BARRIER)) {
			_owner.removeSkillEffect(L1SkillId.COUNTER_BARRIER);
		}

		if (_owner.hasSkillEffect(L1SkillId.할파스)) {
			_owner.removeSkillEffect(L1SkillId.할파스);
			try { SC_InventoryIcon.icoEnd(_owner, L1SkillId.할파스); }
			catch (RuntimeException notice) { equipmentNoticeFailed(notice); }
		}


        final int morph = EquipmentTraits.forItem(item.getItemId()).morph();
        if (morph != 0 && _owner.getCurrentSpriteId() == morph && polymorphSet == null) {
            L1PolyMorph.undoPoly(_owner);
        }
        applyWeaponBlessBonus(weapon, -1);
        removeWeaponFromSlots(weapon);
    }

    private static boolean suppliesWeaponImmunity(int playerClass, int weaponType) {
        return switch (playerClass) {
            case 0, 1 -> weaponType == 1 || weaponType == 3;
            case 2 -> weaponType == 4 || weaponType == 13;
            case 3 -> weaponType == 7 || weaponType == 16 || weaponType == 1;
            case 4 -> weaponType == 12 || weaponType == 11;
            case 5 -> weaponType == 1 || weaponType == 3 || weaponType == 18;
            case 6 -> weaponType == 17;
            case 7 -> weaponType == 6 || weaponType == 15;
            default -> false;
        };
    }

    private void removeArmor(L1ItemInstance armor) {
        final L1Item item = armor.getItem();
        final int itemId = item.getItemId();
        final EquipmentTraits traits = EquipmentTraits.forItem(itemId);
        final int enchant = armor.getEnchantLevel();

        if (traits.has(HALPHAS)) {
            try { SC_InventoryIcon.icoEnd(_owner, L1SkillId.할파스의신의1); }
            catch (RuntimeException notice) { equipmentNoticeFailed(notice); }
            try { SC_InventoryIcon.icoEnd(_owner, L1SkillId.할파스의신의); }
            catch (RuntimeException notice) { equipmentNoticeFailed(notice); }
        }
        final int classBonus = removeArmorVitalBonus(armor.getId());
        if (traits.equippedSkill() != 0) _owner.removeSkillEffect(traits.equippedSkill());
        final boolean blessingChangedSpMr = EquipmentBlessing.applyArmor(_owner, armor, -1);
        applyArmorStats(armor, item, enchant, classBonus, -1);
        validateSetItem(itemId, false, false, false);

        if (traits.has(MAP_BREATH_ICON)) {
            try { SC_PacketBox.send(_owner, SC_PacketBox.UNLIMITED_ICON1, 484, false); }
            catch (RuntimeException notice) { equipmentNoticeFailed(notice); }
        }
        if (traits.has(INVISIBILITY)) _owner.delInvis();
        sendArmorAbility(armor, traits.ability(), false);
        if (traits.has(WATER_BREATHING)) refreshEvaAfterRemoval(armor);

        _armors.remove(armor);
        applyArmorSlotOptions(item, traits, enchant, -1);
        if (blessingChangedSpMr) {
            try { SC_SPMR.send(_owner); }
            catch (RuntimeException notice) { equipmentNoticeFailed(notice); }
        }
    }

    private void applyArmorStats(L1ItemInstance armor, L1Item item, int enchant, int classBonus, int sign) {
        EquipmentOptionTable.ARMOR_DODGE.apply(_owner, item, enchant, sign, null);
        applyClassArmorBonus(_owner, classBonus, sign, null);
        EquipmentEnchantOptions.applyArmorPrimaryOptions(_owner, item, enchant, sign, null);
        applyArmorDefense(armor, enchant, sign);
        EquipmentOptionTable.ARMOR_CASTING.apply(_owner, item, enchant, sign, null);
        applyArmorStatBonus(item, sign);
    }

    private void applyArmorSlotOptions(L1Item item, EquipmentTraits traits, int enchant, int sign) {
        applyElixirRuneBonus(_owner, _owner.getType(), traits, sign, null);
        EquipmentEnchantOptions.applyArmorSlotOptions(_owner, item, enchant, sign, null);
        if (EquipmentEnchantOptions.accessoryHeal(item, enchant) != 0) refreshAccessoryHeal();
    }

    private void sendArmorAbility(L1ItemInstance changed, int ability, boolean equipped) {
        if (ability == 0) return;
        if (!equipped) {
            for (int i = 0; i < _armors.size(); i++) {
                final L1ItemInstance other = _armors.get(i);
                if (other != changed && other.isEquipped() && EquipmentTraits.forItem(other.getItemId()).ability() == ability) return;
            }
        }
        try { SC_Ability.send(_owner, ability, equipped); }
        catch (RuntimeException notice) { equipmentNoticeFailed(notice); }
    }

	// Inventory owns the equipped flag and persistence. This stage owns slot and effect changes.
	void equipItem(L1ItemInstance equipment) {
		final L1Item item = equipment.getItem();
		final int type = item.getType2();
		if (type == 0) return;
        final EquipmentTraits traits = EquipmentTraits.forItem(item.getItemId());

		if (type == 1 && !hasRoomForWeapon(equipment)) {
			throw new IllegalStateException("Weapon equip was submitted without a free slot");
		}

		applyStatBonus(equipment, item, 1);
		applyRecoveryStats(item, 1);
		if (item.isHasteItem()) {
			_owner.addHasteItemEquipped(1);
			_owner.removeHasteSkillEffect();
			if (_owner.getMoveSpeed() != 1) {
				_owner.setMoveSpeed(1);
				try { SC_SkillHaste.send(_owner, _owner.getId(), 1, -1); }
				catch (RuntimeException notice) { equipmentNoticeFailed(notice); }
				try { SC_SkillHaste.broadcast(_owner, _owner.getId(), 1, 0); }
				catch (RuntimeException notice) { equipmentNoticeFailed(notice); }
			}
		}
		if (traits.has(CHARGED_POLYMORPH) && _owner.hasSkillEffect(L1SkillId.STATUS_BRAVE)) {
			BraveSpeedEffects.cancelTimer(_owner, L1SkillId.STATUS_BRAVE);
		}
		showMagicHelmSpells(equipment);

		if (type == 1) {
			setWeapon(equipment);
			validateSetItem(item.getItemId(), true, false, false);
		} else if (type == 2) {
			setArmor(equipment);
			try { SC_SPMR.send(_owner); }
			catch (RuntimeException notice) { equipmentNoticeFailed(notice); }
		}
		onEquipmentChanged(equipment, item, true);
        if (type == 1 && traits.morph() != 0 && equipment.isEquipped()) {
            L1PolyMorph.doPoly(_owner, traits.morph(), 0, L1PolyMorph.MORPH_BY_ITEMMAGIC, false);
        }
		// A set polymorph can synchronously unequip gear. Complete this item's
		// own options and callbacks before exposing that transition.
		if (equipment.isEquipped()
				&& validateSetItem(item.getItemId(), true, type == 2 && item.getType() == 9, true)) {
			try { SC_SPMR.send(_owner); }
			catch (RuntimeException notice) { equipmentNoticeFailed(notice); }
		}
		// The last charge can still activate the set's polymorph on this equip.
		if (type == 2 && traits.has(CHARGED_POLYMORPH) && equipment.getChargeCount() != 0) {
			equipment.setChargeCount(equipment.getChargeCount() - 1);
			_owner.getInventory().updateItem(equipment, CUserInventory.COL_CHARGE_COUNT);
		}
	}

	void unequipItem(L1ItemInstance equipment) {
		final L1Item item = equipment.getItem();
		final int type = item.getType2();
		if (type == 0) return;

		if ((item.getType() == 13 || item.getType() == 7)
				&& _owner.hasSkillEffect(L1SkillId.BLOW_ATTACK)) {
			_owner.removeSkillEffect(L1SkillId.BLOW_ATTACK);
		}
		applyStatBonus(equipment, item, -1);
		applyRecoveryStats(item, -1);
		if (item.isHasteItem()) {
			_owner.addHasteItemEquipped(-1);
			if (_owner.getHasteItemEquipped() == 0 && _owner.getMoveSpeed() == 1
					&& MovementSpeedEffects.activeHaste(_owner, 0) == 0) {
				_owner.setMoveSpeed(0);
				try { SC_SkillHaste.send(_owner, _owner.getId(), 0, 0); }
				catch (RuntimeException notice) { equipmentNoticeFailed(notice); }
				try { SC_SkillHaste.broadcast(_owner, _owner.getId(), 0, 0); }
				catch (RuntimeException notice) { equipmentNoticeFailed(notice); }
			}
		}
		retireMagicHelmSpells(equipment);

		if (type == 1) {
			removeWeapon(equipment);
			_owner.remove_elf_second_brave();
			validateSetItem(item.getItemId(), false, false, false);
		} else if (type == 2) {
			removeArmor(equipment);
		}
		onEquipmentChanged(equipment, item, false);
		validateSetItem(item.getItemId(), false, false, true);
		if (!equipment.isEquipped()) updateSetItemDisplay(equipment, false);
	}

    private void applyRecoveryStats(L1Item template, int sign) {
        if (template.get_addhpr() != 0) _owner.addHpr(sign * template.get_addhpr());
        if (template.get_addmpr() != 0) _owner.addMpr(sign * template.get_addmpr());
    }

	private void applyStatBonus(L1ItemInstance equipment, L1Item item, int sign) {
		ItemStat.BLESS_CONSUMO.apply(_owner, sign * item.get_addeinhasadper());
		ItemStat.MAX_HP.apply(_owner, sign * item.get_addhp());
		ItemStat.MAX_MP.apply(_owner, sign * item.get_addmp());
		_owner.add_missile_critical_rate(sign * item.get_missile_critical_probability());
		_owner.add_melee_critical_rate(sign * item.get_melee_critical_probability());

		int magicResistance = equipment.getMr();
		if (_owner.isElf()) magicResistance += EquipmentTraits.forItem(item.getItemId()).elfMr();
		final int spellPower = item.get_addsp();
		if (magicResistance != 0) ItemStat.MR.apply(_owner, sign * magicResistance);
		if (spellPower != 0) ItemStat.SP.apply(_owner, sign * spellPower);
		try {
			_owner.AddStatBonuses(sign * item.get_addstr(), sign * item.get_adddex(), sign * item.get_addcon(),
					sign * item.get_addint(), sign * item.get_addwis(), sign * item.get_addcha(),
					magicResistance != 0 || spellPower != 0);
		} catch (RuntimeException notice) {
			equipmentNoticeFailed(notice);
		}
	}

	private int applyArmorVitalBonus(L1ItemInstance armor, L1Item item, EquipmentTraits traits) {
		final var ability = _owner;
		final int conBonus = item.get_addcon();
		final int wisBonus = item.get_addwis();
		final int hp = armorVital(ability.GetStatCon(), conBonus);
		final int mp = armorVital(ability.GetStatWis(), wisBonus);
		final int classBonus = traits.has(CLASS_DEPENDENT_BONUS) ? selectClassArmorBonus().ordinal() + 1 : 0;
		if (hp == 0 && mp == 0 && classBonus == 0) return 0;

		final int i = appliedArmorBonusSize;
		if (appliedArmorBonuses == null) {
			appliedArmorBonuses = new int[2 * ARMOR_BONUS_WIDTH];
		} else if (i == appliedArmorBonuses.length) {
			appliedArmorBonuses = Arrays.copyOf(appliedArmorBonuses, i * 2);
		}
		appliedArmorBonuses[i] = armor.getId();
		appliedArmorBonuses[i + 1] = hp;
		appliedArmorBonuses[i + 2] = mp;
		appliedArmorBonuses[i + 3] = classBonus;
		appliedArmorBonusSize += ARMOR_BONUS_WIDTH;
		if (hp != 0) _owner.addMaxHp(hp);
		if (mp != 0) _owner.addMaxMp(mp);
		return classBonus;
	}

	private static int findAppliedArmorBonus(int[] bonuses, int size, int objectId) {
		if (bonuses == null) return -1;
		final int end = Math.min(size, bonuses.length);
		for (int i = 0; i < end; i += ARMOR_BONUS_WIDTH) {
			if (bonuses[i] == objectId) return i;
		}
		return -1;
	}

	private int removeArmorVitalBonus(int objectId) {
		final int i = findAppliedArmorBonus(appliedArmorBonuses, appliedArmorBonusSize, objectId);
		if (i < 0) return 0;
		final int hp = appliedArmorBonuses[i + 1];
		final int mp = appliedArmorBonuses[i + 2];
		final int classBonus = appliedArmorBonuses[i + 3];
		appliedArmorBonusSize -= ARMOR_BONUS_WIDTH;
		System.arraycopy(appliedArmorBonuses, appliedArmorBonusSize, appliedArmorBonuses, i, ARMOR_BONUS_WIDTH);
		if (hp != 0) _owner.addMaxHp(-hp);
		if (mp != 0) _owner.addMaxMp(-mp);
		return classBonus;
	}

	private ClassArmorBonus getClassArmorBonus(L1ItemInstance armor) {
		if (armor.isEquipped()) {
			final int[] bonuses = appliedArmorBonuses;
			final int i = findAppliedArmorBonus(bonuses, appliedArmorBonusSize, armor.getId());
			if (i >= 0) {
				final int option = bonuses[i + 3];
				if (option != 0) return CLASS_ARMOR_BONUSES[option - 1];
			}
		}
		return selectClassArmorBonus();
	}

	private ClassArmorBonus selectClassArmorBonus() {
		if (_owner.isBlackwizard() || _owner.isWizard()) return ClassArmorBonus.MAGIC;
		if (_owner.isElf() && _owner.GetOriginalStatDex() >= 30) return ClassArmorBonus.BOW;
		return ClassArmorBonus.MELEE;
	}

    private static void applyClassArmorBonus(L1PcInstance owner, int option, int sign, int[] totals) {
        if (option == 0) return;
        EquipmentEnchantOptions.applyStat(owner, totals, ItemStat.AC, -8 * sign);
        switch (CLASS_ARMOR_BONUSES[option - 1]) {
            case MAGIC -> {
                EquipmentEnchantOptions.applyStat(owner, totals, ItemStat.SP, 2 * sign);
                EquipmentEnchantOptions.applyStat(owner, totals, ItemStat.MAGIC_HIT, 2 * sign);
            }
            case BOW -> {
                EquipmentEnchantOptions.applyStat(owner, totals, ItemStat.BOW_HIT, 2 * sign);
                EquipmentEnchantOptions.applyStat(owner, totals, ItemStat.BOW_DMG, 2 * sign);
            }
            case MELEE -> {
                EquipmentEnchantOptions.applyStat(owner, totals, ItemStat.HIT, 2 * sign);
                EquipmentEnchantOptions.applyStat(owner, totals, ItemStat.DMG_RATE, 2 * sign);
            }
        }
        EquipmentEnchantOptions.applyStat(owner, totals, ItemStat.DMG_RED, 2 * sign);
        EquipmentEnchantOptions.applyStat(owner, totals, ItemStat.FEAR_RESIS, 2 * sign);
    }

    static void collectArmorTooltipModifiers(L1ItemInstance armor, L1PcInstance owner, int[] totals) {
        final EquipmentTraits traits = EquipmentTraits.forItem(armor.getItemId());
        if (traits.has(CLASS_DEPENDENT_BONUS)) {
            final ClassArmorBonus bonus = owner == null ? ClassArmorBonus.MELEE
                    : owner.getEquipment().getClassArmorBonus(armor);
            applyClassArmorBonus(owner, bonus.ordinal() + 1, 1, totals);
        }
        if (owner == null) return;
        final CUserEquipment slots = owner.getEquipment();
        final int[] applied = slots.appliedArmorBonuses;
        final int index = armor.isEquipped()
                ? findAppliedArmorBonus(applied, slots.appliedArmorBonusSize, armor.getId()) : -1;
        final L1Item template = armor.getItem();
        EquipmentEnchantOptions.applyStat(owner, totals, ItemStat.MAX_HP, index >= 0
                ? applied[index + 1] : armorVital(owner.GetStatCon() + template.get_addcon(), template.get_addcon()));
        EquipmentEnchantOptions.applyStat(owner, totals, ItemStat.MAX_MP, index >= 0
                ? applied[index + 2] : armorVital(owner.GetStatWis() + template.get_addwis(), template.get_addwis()));
        applyElixirRuneBonus(owner, owner.getType(), traits, 1, totals);
    }

    private static int armorVital(int equippedStat, int bonus) {
        return bonus > 0 ? equippedStat / 2 * bonus : 0;
    }

	private void applyArmorDefense(L1ItemInstance armor, int enchant, int sign) {
		final L1Item item = armor.getItem();
		if (item.getType() == 30) return;
		ItemStat.AC.apply(_owner, sign * armorDefense(armor, enchant));
		if (!EquipmentTraits.forItem(item.getItemId()).has(FIXED_ARMOR_AC)) {
			try { SC_OwnCharAttrDef.send(_owner); }
			catch (RuntimeException notice) { equipmentNoticeFailed(notice); }
		}
	}

	boolean applyArmorDurabilityChange(L1ItemInstance armor, int previousDurability) {
		final int durability = armor.get_durability();
		final L1Item item = armor.getItem();
		if (previousDurability == durability || !armor.isEquipped() || item.getType2() != 2
				|| item.getType() == 30 || EquipmentTraits.forItem(item.getItemId()).has(FIXED_ARMOR_AC)) return false;
		ItemStat.AC.apply(_owner, durability - previousDurability);
		return true;
	}

	public static int armorDefense(L1ItemInstance armor, int enchant) {
		final L1Item item = armor.getItem();
		final int type = item.getType();
		if (type == 30) return 0;
        final EquipmentTraits traits = EquipmentTraits.forItem(item.getItemId());
		if (traits.has(FIXED_ARMOR_AC)) return item.get_ac();

		int enchantAc = enchant;
		if (type >= 8 && type <= 12) {
			enchantAc = (type == 8 || type == 12) && enchant >= 5
					? (enchant >= 10 ? 6 : Math.min(enchant - 4, 5)) : 0;
		} else if (traits.has(NONNEGATIVE_ENCHANT_AC)) {
			enchantAc = Math.max(enchant, 0);
		}
		return item.get_ac() - enchantAc + armor.get_durability();
	}

	public boolean applyEquippedEnchantChange(L1ItemInstance equipment, int oldEnchant) {
		final int enchant = equipment.getEnchantLevel();
		if (!equipment.isEquipped() || oldEnchant == enchant) return false;
		final L1Item item = equipment.getItem();
		if (item.getType2() == 0) return false;
		final int itemId = item.getItemId();
		final int[] changes = EquipmentEnchantOptions.enchantDelta(_owner, item, oldEnchant, enchant);
		changes[ItemStat.MR.ordinal()] += equipment.getMr() - equipment.getMrAtEnchant(oldEnchant);
		if (item.getType2() == 2) {
			changes[ItemStat.AC.ordinal()] += armorDefense(equipment, enchant) - armorDefense(equipment, oldEnchant);
			if (EquipmentEnchantOptions.accessoryHeal(item, oldEnchant) != EquipmentEnchantOptions.accessoryHeal(item, enchant)) {
				refreshAccessoryHeal();
			}
		}
		final ItemEffectRegistry.Def definition = ItemEffectRegistry.get(itemId);
		final boolean registered = definition != null
				&& definition.addEnchantDelta(_owner.getType(), oldEnchant, enchant, changes);
		final boolean statChanged = EquipmentEnchantOptions.applyDelta(_owner, changes);
		ItemBonusHooks.onEnchantChanged(_owner, equipment);
		return statChanged || registered;
	}

	private void refreshAccessoryHeal() {
		for (int i = _armors.size() - 1; i >= 0; i--) {
			final L1ItemInstance armor = _armors.get(i);
			if (!armor.isEquipped()) continue;
			final int heal = EquipmentEnchantOptions.accessoryHeal(armor.getItem(), armor.getEnchantLevel());
			if (heal == 0) continue;
			_owner.setAccessoryHeal(heal);
			return;
		}
		_owner.setAccessoryHeal(0);
	}

    private static void applyElixirRuneBonus(L1PcInstance owner, int classType,
            EquipmentTraits traits, int sign, int[] totals) {
        final int tier = traits.runeTier();
        if (tier == 0) return;
        final boolean lowTier = tier == 1;
        final int precision = traits.runeAccuracy();
        final int magicDamageReduction = traits.runeMagicDamageReduction();
        if (magicDamageReduction != 0) {
            EquipmentEnchantOptions.applyStat(owner, totals, ItemStat.PVP_MAGIC_DMG_RED, magicDamageReduction * sign);
        }

		switch (classType) {
		case 0 -> {
			EquipmentEnchantOptions.applyStat(owner, totals, ItemStat.DMG_RED, 3 * sign);
			if (precision != 0) {
				EquipmentEnchantOptions.applyStat(owner, totals, ItemStat.HIT, 2 * sign);
				EquipmentEnchantOptions.applyStat(owner, totals, ItemStat.ABILITY_PLERCE, precision * sign);
			} else if (!lowTier) {
				EquipmentEnchantOptions.applyStat(owner, totals, ItemStat.DMG_RATE, 2 * sign);
			}
		}
		case 1 -> {
			EquipmentEnchantOptions.applyStat(owner, totals, ItemStat.MAX_HP, 50 * sign);
			if (!lowTier) EquipmentEnchantOptions.applyStat(owner, totals, ItemStat.DMG_RATE, sign);
			if (precision != 0) EquipmentEnchantOptions.applyStat(owner, totals, ItemStat.ABILITY_PLERCE, precision * sign);
		}
		case 2 -> {
			EquipmentEnchantOptions.applyStat(owner, totals, ItemStat.MAX_MP, 50 * sign);
			if (!lowTier) {
				EquipmentEnchantOptions.applyStat(owner, totals, ItemStat.BOW_DMG, sign);
				EquipmentEnchantOptions.applyStat(owner, totals, ItemStat.DMG_RATE, sign);
			}
			if (precision != 0) EquipmentEnchantOptions.applyStat(owner, totals, ItemStat.SPIRIT_PLERCE, precision * sign);
		}
		case 3 -> {
			EquipmentEnchantOptions.applyStat(owner, totals, ItemStat.MPR, 3 * sign);
			if (traits.runeWizardMp() != 0) EquipmentEnchantOptions.applyStat(owner, totals, ItemStat.MAX_MP, traits.runeWizardMp() * sign);
			if (!lowTier) EquipmentEnchantOptions.applyStat(owner, totals, ItemStat.SP, sign);
			if (precision != 0) EquipmentEnchantOptions.applyStat(owner, totals, ItemStat.MAGIC_HIT, precision * sign);
		}
		case 4 -> {
			EquipmentEnchantOptions.applyStat(owner, totals, ItemStat.AC, -3 * sign);
			if (!lowTier) EquipmentEnchantOptions.applyStat(owner, totals, ItemStat.MAX_MP, 30 * sign);
			if (precision != 0) EquipmentEnchantOptions.applyStat(owner, totals, ItemStat.SPIRIT_PLERCE, precision * sign);
		}
		case 5 -> {
			EquipmentEnchantOptions.applyStat(owner, totals, ItemStat.HIT, 3 * sign);
			if (!lowTier) EquipmentEnchantOptions.applyStat(owner, totals, ItemStat.DMG_RED, sign);
			if (precision != 0) EquipmentEnchantOptions.applyStat(owner, totals, ItemStat.DRAGON_SPELL_PLERCE, precision * sign);
		}
		case 6 -> {
			EquipmentEnchantOptions.applyStat(owner, totals, ItemStat.WEIGHT, 5 * sign);
			if (!lowTier) EquipmentEnchantOptions.applyStat(owner, totals, ItemStat.MAX_HP, 50 * sign);
			if (precision != 0) EquipmentEnchantOptions.applyStat(owner, totals, ItemStat.DRAGON_SPELL_PLERCE, precision * sign);
		}
		case 7 -> {
			if (!lowTier) EquipmentEnchantOptions.applyStat(owner, totals, ItemStat.MR, 5 * sign);
			EquipmentEnchantOptions.applyStat(owner, totals, ItemStat.MAX_HP, 50 * sign);
			if (precision != 0) EquipmentEnchantOptions.applyStat(owner, totals, ItemStat.FEAR_PLERCE, precision * sign);
		}
		}
	}

	private void applyArmorStatBonus(L1Item item, int sign) {
		ItemStat.DMG_RED.apply(_owner, sign * item.get_damage_reduction());
		ItemStat.WEIGHT.apply(_owner, sign * item.getWeightReduction());
		_owner.addBowHitRate(sign * item.getBowHitRate());
		ItemStat.BOW_DMG.apply(_owner, sign * item.getBowDmgRate());
		ItemStat.DMG_RATE.apply(_owner, sign * item.getDmgRate());
		_owner.addHitRate(sign * item.getHitRate());
		final var resistance = _owner;
		resistance.AddEarthResist(sign * item.get_defense_earth());
		resistance.AddWindResist(sign * item.get_defense_wind());
		resistance.AddWaterResist(sign * item.get_defense_water());
		resistance.AddFireResist(sign * item.get_defense_fire());
		item.equipmentItem(_owner, sign > 0);
		ItemStat.PVP_DMG_RED.apply(_owner, sign * item.get_regist_calcPcDefense());
		ItemStat.PVP_DMG.apply(_owner, sign * item.get_regist_PVPweaponTotalDamage());
		ItemStat.MAGIC_HIT.apply(_owner, sign * item.getMagicHitup());
	}

    private void onEquipmentChanged(L1ItemInstance equipment, L1Item item, boolean equipped) {
        applyRegisteredStatBonus(equipment, item, equipped ? 1 : -1);
        ItemBonusHooks.onEquipChanged(_owner, equipment, equipped);
    }

	private void applyRegisteredStatBonus(L1ItemInstance equipment, L1Item item, int sign) {
		final ItemEffectRegistry.Def definition = ItemEffectRegistry.get(item.getItemId());
		if (definition != null) definition.apply(_owner, _owner.getType(), equipment.getEnchantLevel(), sign);
	}

	private static int spellMask(long spells, int level) {
		return (int) (spells >>> (level * Byte.SIZE)) & 0xff;
	}

	private void showMagicHelmSpells(L1ItemInstance item) {
		final long spells = EquipmentTraits.forItem(item.getItemId()).spells();
		if (spells == 0L) return;
		try { SC_AddSkill.send(_owner, spellMask(spells, 0), spellMask(spells, 1), spellMask(spells, 2),
				spellMask(spells, 3), spellMask(spells, 4), spellMask(spells, 5), spellMask(spells, 6),
				0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0); }
		catch (RuntimeException notice) { equipmentNoticeFailed(notice); }
	}

	private void retireMagicHelmSpells(L1ItemInstance removed) {
		long spells = EquipmentTraits.forItem(removed.getItemId()).spells();
		if (spells == 0L) return;
		for (int i = 0; i < _armors.size(); i++) {
			final L1ItemInstance armor = _armors.get(i);
			if (armor != removed && armor.isEquipped()) spells &= ~EquipmentTraits.forItem(armor.getItemId()).spells();
		}

		final SkillsTable learned = SkillsTable.getInstance();
		while (spells != 0L) {
			final long spell = Long.lowestOneBit(spells);
			spells ^= spell;
			if (learned.spellCheck(_owner.getId(), Long.numberOfTrailingZeros(spell) + 1)) continue;
			try { SC_DelSkill.send(_owner, spellMask(spell, 0), spellMask(spell, 1), spellMask(spell, 2),
					spellMask(spell, 3), spellMask(spell, 4), spellMask(spell, 5), spellMask(spell, 6),
					0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0); }
			catch (RuntimeException notice) { equipmentNoticeFailed(notice); }
		}
	}

	void validateSetItemAfterUnequip(int itemId) {
		validateSetItem(itemId, false, false, false);
		validateSetItem(itemId, false, false, true);
	}

	private boolean validateSetItem(int itemId, boolean equipping, boolean ring, boolean polymorph) {
		if (!equipping && activeSets.isEmpty()) return false;
		boolean changed = false;
		final List<CSetItem> definitions = CSetItem.definitions(polymorph);
		for (int i = 0, count = definitions.size(); i < count; i++) {
			final CSetItem set = definitions.get(i);
			if (!set.containsItem(itemId)) {
				if (set.mainItemId == itemId) refreshSetItemDisplay(itemId);
				continue;
			}

			final int activeIndex = activeSets.indexOf(set);
			if (equipping && activeIndex < 0 && set.isValid(this)
					&& (!ring || !set.hasDuplicateEquippedRing(this))) {
				if (activeSets == EMPTY_ACTIVE_SETS) activeSets = new ArrayList<>(4);
				activeSets.add(set);
				set.applyEffects(_owner, 1);
				if (polymorph) applySetPolymorph(set);
				if (activeSets.contains(set) && set.grantsEva) {
					try { SC_SkillIconBlessOfEva.send(_owner, _owner.getId(), -1); }
					catch (RuntimeException notice) { equipmentNoticeFailed(notice); }
				}
				changed = true;
			} else if (!equipping && activeIndex >= 0 && !set.isValid(this)) {
				activeSets.remove(activeIndex);
				set.applyEffects(_owner, -1);
				if (polymorph) removeSetPolymorph(set);
				if (set.grantsEva) refreshEvaAfterRemoval(null);
				changed = true;
			} else if (set.mainItemId != itemId) {
				continue;
			}

			refreshSetItemDisplay(set.mainItemId);
		}
		return changed;
	}

	private void refreshSetItemDisplay(int mainItemId) {
		if (mainItemId == 0) return;
		boolean active = false;
		for (int i = 0; i < activeSets.size(); i++) {
			if (activeSets.get(i).mainItemId == mainItemId) {
				active = true;
				break;
			}
		}
		for (int i = 0; i < _armors.size(); i++) {
			active = refreshSetItemDisplay(_armors.get(i), mainItemId, active);
		}
		final boolean markSecond = refreshSetItemDisplay(getWeapon(), mainItemId, active);
		refreshSetItemDisplay(getSecondWeapon(), mainItemId, markSecond);
	}



	private boolean refreshSetItemDisplay(L1ItemInstance item, int itemId, boolean markNext) {
		if (item == null || item.getItemId() != itemId) return markNext;
		final boolean show = markNext && item.isEquipped();
		updateSetItemDisplay(item, show);
		return markNext && !show;
	}

	private void updateSetItemDisplay(L1ItemInstance item, boolean show) {
		if (item.get_main_set_armor() == show) return;
		item.set_main_set_armor(show);
		try { SC_ItemStatus.send(_owner, item); }
		catch (RuntimeException notice) { equipmentNoticeFailed(notice); }
	}

	private void applySetPolymorph(CSetItem set) {
		if (!set.canApplyPolymorph(_owner)) return;
		L1PolyMorph.doPoly(_owner, set.polymorphGfx(_owner), 0, L1PolyMorph.MORPH_BY_ITEMMAGIC, false, set);
	}

	void recordPolymorphSource(CSetItem set, int sprite) {
		polymorphSet = set;
		polymorphSetSprite = set == null ? 0 : sprite;
	}

	boolean ownsPolymorph(CSetItem set, int sprite) {
		return polymorphSet == set && polymorphSetSprite == sprite;
	}

	private void removeSetPolymorph(CSetItem set) {
		if (polymorphSet != set) return;
		final int gfx = polymorphSetSprite;
		recordPolymorphSource(null, 0);
		if (_owner.getCurrentSpriteId() != gfx) return;
		for (int i = 0; i < activeSets.size(); i++) {
			final CSetItem other = activeSets.get(i);
			if (other.polymorphGfx(_owner) == set.polymorphGfx(_owner) && other.canApplyPolymorph(_owner)) {
				recordPolymorphSource(other, gfx);
				return;
			}
		}
		_owner.killSkillEffectTimer(L1SkillId.SHAPE_CHANGE);
		L1PolyMorph.undoPoly(_owner);
	}

	public int evaIconDuration(int skillSeconds) {
		return hasEvaEquipment(null) ? -1 : skillSeconds;
	}

	private boolean hasEvaEquipment(L1ItemInstance removed) {
		for (int i = 0; i < activeSets.size(); i++) {
			if (activeSets.get(i).grantsEva) return true;
		}
		for (int i = 0; i < _armors.size(); i++) {
			final L1ItemInstance armor = _armors.get(i);
			if (armor != removed && armor.isEquipped() && EquipmentTraits.forItem(armor.getItemId()).has(WATER_BREATHING)) return true;
		}
		return false;
	}

	private void refreshEvaAfterRemoval(L1ItemInstance removed) {
		if (hasEvaEquipment(removed)) return;
		final int skill = L1SkillId.STATUS_UNDERWATER_BREATH;
		final int remaining = _owner.hasSkillEffect(skill) ? _owner.getSkillEffectTimeSec(skill) : 0;
		try { SC_SkillIconBlessOfEva.send(_owner, _owner.getId(), remaining); }
		catch (RuntimeException notice) { equipmentNoticeFailed(notice); }
	}
}
