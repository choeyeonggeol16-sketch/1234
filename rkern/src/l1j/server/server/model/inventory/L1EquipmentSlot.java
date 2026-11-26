package l1j.server.server.model.inventory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Arrays;
import java.util.List;

import l1j.server.server.datatables.SkillsTable;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.BraveSpeedEffects;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.MovementSpeedEffects;
import l1j.server.server.model.character.skill.passive.PassiveID;
import l1j.server.server.model.item.ItemEffectRegistry;
import l1j.server.server.model.item.ItemStat;
import l1j.server.server.model.item.function.ItemBonusHooks;
import l1j.server.server.model.item.assembly.ItemPresentatorFactory;
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
import l1j.server.server.templates.L1Item;
import l1j.server.server.utils.eKind;
public class L1EquipmentSlot {
    private static final java.util.logging.Logger NOTICE_LOG =
            java.util.logging.Logger.getLogger(L1EquipmentSlot.class.getName());

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
	public enum ClassArmorBonus { MELEE, MAGIC, BOW }

	private static final ClassArmorBonus[] CLASS_ARMOR_BONUSES = ClassArmorBonus.values();
	// Object ID, applied HP, applied MP and class option for state-dependent armor only.
	private int[] appliedArmorBonuses;
	private int appliedArmorBonusSize;

	public int worldjoin_weapon_idx = 0;

	public L1EquipmentSlot(L1PcInstance owner) {
		_owner = owner;
	}

    boolean isInvisibilityEquipCoolingDown() {
        return System.currentTimeMillis() - invisibleGearRemovedAtMs < 3_000L;
    }

    void markInvisibilityGearRemoved() {
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
                catch (RuntimeException notice) { CUserEquipment.noticeFailed(_owner, notice); }
            }
        } finally {
            inventory.endEquipmentChange();
        }
    }

	private void setWeapon(L1ItemInstance weapon) {// 무기류
		//L1Item item = weapon.getItem(); //무기테이블
		int itemId = weapon.getItem().getItemId();
		int enchant = weapon.getEnchantLevel();
		
		EquipmentEnchantOptions.applyMeditationStaff(_owner, weapon.getItem(), enchant, 1, null);

		if(itemId == 14061 || itemId == 14012 || itemId == 2202013 || itemId == 2202014 || itemId == 14086) {
			_owner.addDamageReductionByArmor(1);
		}
		if(itemId == 14134 || itemId == 2202012 ) {
			_owner.addDamageReductionByArmor(2);
		}
		EquipmentEnchantOptions.applyNamedWeaponOptions(_owner, weapon.getItem(), enchant, 1, null);
		/** 나이트발드의 양손검 기술적중 **/
		if (itemId == 59 || itemId == 617 || itemId == 11121) {
			_owner.AddSpecialPierce(eKind.ABILITY, 5);
		}
		/** 악몽의 장궁 정령 적중**/
		if (itemId == 622 || itemId == 1136) {
			_owner.AddSpecialPierce(eKind.SPIRIT, 3);
		}
		/** 악마왕 도끼 **/
		EquipmentEnchantOptions.applyDemonAxe(_owner, weapon.getItem(), enchant, 1, null);
		/** 태풍의도끼 공포적중+1 **/
		EquipmentEnchantOptions.applyWeaponAccuracy(_owner, weapon.getItem(), enchant, 1, null);
		/** 썸타는 도끼 공포적중+5 **/
		if (itemId == 7000221) 
			_owner.AddSpecialPierce(eKind.FEAR, 5);

		/** 썸타는 무기류 PVP 효과 부여 **/
		EquipmentEnchantOptions.applyCourtshipWeaponPvp(_owner, weapon.getItem(), enchant, 1, null);

		addWeaponToSlots(weapon);
	
		int type = weapon.getItem().getType();
		if ((type == 7 || type == 16 || type == 17) && (weapon.get_bless_level() != 0)) {
			_owner.AddStatSp(weapon.get_bless_level());
			try { SC_SPMR.send(_owner); }
			catch (RuntimeException notice) { equipmentNoticeFailed(notice); }
		}

		if (itemId == 203003) { // 데스나이트의 불검:진
			L1PolyMorph.doPoly(_owner, 12232, 0, L1PolyMorph.MORPH_BY_ITEMMAGIC, false);
		}
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


	private static boolean isRoomteece(int itemId) {
		return itemId == 222340 || itemId == 222341 || itemId == 22229 || itemId == 22230 || itemId == 22231
				|| itemId == 222337 || itemId == 222338 || itemId == 222339
				|| itemId >= 900124 && itemId <= 900130;
	}

	private void setArmor(L1ItemInstance armor) {	
		L1Item item = armor.getItem();
		int itemlvl = armor.getEnchantLevel();
		int itemtype = armor.getItem().getType();
		int itemId = armor.getItem().getItemId();
		
		
			if (itemId == 123111 || itemId == 123112 || itemId == 123113) {
			
			
			 
				 int n = _owner.getSkillEffectTimeSec(L1SkillId.할파스의신의) / 60;
			 if (n > 0) {
				 try { SC_InventoryIcon.icoNew(_owner, L1SkillId.할파스의신의, 3942, _owner.getSkillEffectTimeSec(L1SkillId.할파스의신의), true); }
				 catch (RuntimeException notice) { equipmentNoticeFailed(notice); }
				 
			 }else {
				 try { SC_InventoryIcon.iconNewUnLimit(_owner, L1SkillId.할파스의신의1, 3943, true); }
				 catch (RuntimeException notice) { equipmentNoticeFailed(notice); }
		 }
			 }
		applyArmorBlessBonus(armor, 1);

		final int classBonus = applyArmorVitalBonus(armor, item);

		if(itemId == 900111){	
			_owner.setSkillEffect(L1SkillId.TELEPORT_RULER, -1);
		}
		
		applyArmorEnchantDodge(armorEnchantDodge(itemId, itemlvl));
		
		
		
		EquipmentEnchantOptions.applyEnhancedWard(_owner, item, itemlvl, 1, null);
		EquipmentEnchantOptions.applyWards(_owner, item, itemlvl, 1, null);
		//착용
		if (itemId == 30184) {
			_owner.addHitup(2);
		}
		if (itemId == 20272 || itemId == 222342) {
			_owner.addBowHitup(3);
		}
		applyClassArmorBonus(classBonus, 1);
		EquipmentEnchantOptions.applyMagicClothing(_owner, item, itemlvl, 1, null);
		
		
		EquipmentEnchantOptions.applyArmorDefenseOptions(_owner, item, itemlvl, 1, null);
		
		applyArmorDefense(armor, itemlvl, 1);
		applyArmorEnchantCasting(itemId, 0, itemlvl, null);

		applyArmorStatBonus(item, 1);
		_armors.add(armor);

		validateSetItem(itemId, true, itemtype == 9, false);

		if (itemId == 900022) {
			if (_owner.getMapId() >= 1708 && _owner.getMapId() <= 1712) {
				try { SC_SkillSound.send(_owner, _owner.getId(), 11101); }
				catch (RuntimeException notice) { equipmentNoticeFailed(notice); }
				try { SC_PacketBox.send(_owner, SC_PacketBox.UNLIMITED_ICON1, 484, true); }
				catch (RuntimeException notice) { equipmentNoticeFailed(notice); }
			}
		}

		if ((itemId == 20077 || itemId == 20062 || itemId == 120077)
				&& !_owner.hasSkillEffect(L1SkillId.DESPERADO)) {
			_owner.invisibleItem();
		}
		if (itemId == 20288 || itemId == 900111) {
		    try { SC_Ability.send(_owner, 1, true); }
		    catch (RuntimeException notice) { equipmentNoticeFailed(notice); }
		}
		if (itemId == 20281) {
			try { SC_Ability.send(_owner, 2, true); }
			catch (RuntimeException notice) { equipmentNoticeFailed(notice); }
		}
		if (itemId == 20036) {
			try { SC_Ability.send(_owner, 3, true); }
			catch (RuntimeException notice) { equipmentNoticeFailed(notice); }
		}
		if (itemId == 20284) {
			try { SC_Ability.send(_owner, 5, true); }
			catch (RuntimeException notice) { equipmentNoticeFailed(notice); }
		}
		if (itemId == 20207) {
			try { SC_SkillIconBlessOfEva.send(_owner, _owner.getId(), -1); }
			catch (RuntimeException notice) { equipmentNoticeFailed(notice); }
		}

		applyElixirRuneBonus(itemId, 1);
		
		/** 화령의 가더 **/
		EquipmentEnchantOptions.applyGuardersAndSanctuaryArmor(_owner, item, itemlvl, 1, null);

		/** 체력의 각반 **/
		EquipmentEnchantOptions.applyVitalityGreaves(_owner, item, itemlvl, 1, null);
		/** 유니콘의 완력 각반 **/
		EquipmentEnchantOptions.applyUnicornGreaves(_owner, item, itemlvl, 1, null);
		/** 맘보 모자 **/
		EquipmentEnchantOptions.applyMamboHat(_owner, item, itemlvl, 1, null);
		/** 예언자의 견갑 **/
		EquipmentEnchantOptions.applyShouldersAndCloak(_owner, item, itemlvl, 1, null);
		/** 커츠의 투사 휘장 **/
		EquipmentEnchantOptions.applyBadges(_owner, item, itemlvl, 1, null);	
		/** 거대여왕 개미의 금빛,은빛 날개 **/
		EquipmentEnchantOptions.applyWingsAndAccessory(_owner, item, itemlvl, 1, null);

		EquipmentEnchantOptions.applyCrimsonArmor(_owner, item, itemlvl, 1, null);
		/** 뱀파이어의 망토 **/
		EquipmentEnchantOptions.applyVampireCloak(_owner, item, itemlvl, 1, null);
		/** 아이리스의 부츠 **/
		EquipmentEnchantOptions.applyIrisBoots(_owner, item, itemlvl, 1, null);
		/** 대마법사의 모자 **/
		EquipmentEnchantOptions.applyMagicAndRareArmor(_owner, item, itemlvl, 1, null);
		/** 흑기사의 면갑 대미지감소 **/
		EquipmentEnchantOptions.applyHelmsAndDragonArmor(_owner, item, itemlvl, 1, null);
		if (itemId == 20279) {

			_owner.AddPvpDamageReduction(2);
			

			}
		if (itemId == 20314) {

			_owner.AddPvpDamage(2);
			

			}
		
		
		/** 머미로드의 왕관 **/
		EquipmentEnchantOptions.applyAccessoryOptions(_owner, item, itemlvl, 1, null);
		if (EquipmentEnchantOptions.accessoryHeal(item, itemlvl) != 0) refreshAccessoryHeal();
	}

	public ArrayList<L1ItemInstance> getArmors() {
		return _armors;
	}

	private void removeWeapon(L1ItemInstance weapon) {
	    if (weapon == null || weapon.getItem() == null) return;
	    
		int type = weapon.getItem().getType();
		int itemId = weapon.getItem().getItemId();
		int enchant = weapon.getEnchantLevel();
		EquipmentEnchantOptions.applyMeditationStaff(_owner, weapon.getItem(), enchant, -1, null);
		if(itemId == 14061 || itemId == 14012 || itemId == 2202013 || itemId == 2202014 || itemId == 14086) {
			_owner.addDamageReductionByArmor(-1);
		}
		if(itemId == 14134 || itemId == 2202012 ) {
			_owner.addDamageReductionByArmor(-2);
		}
		EquipmentEnchantOptions.applyNamedWeaponOptions(_owner, weapon.getItem(), enchant, -1, null);
		/** 악마왕 도끼 **/
		EquipmentEnchantOptions.applyDemonAxe(_owner, weapon.getItem(), enchant, -1, null);
		/** 나이트발드의 양손검 기술적중 **/
		if (itemId == 59 || itemId == 617  || itemId == 11121) {
			_owner.AddSpecialPierce(eKind.ABILITY, -5);
		}
		/** 악몽의 장궁 정령 적중**/
		if (itemId == 622 || itemId == 1136) {
			_owner.AddSpecialPierce(eKind.SPIRIT, -3);
		}
		/** 태풍의도끼 공포적중+1 **/
		EquipmentEnchantOptions.applyWeaponAccuracy(_owner, weapon.getItem(), enchant, -1, null);
		/** 썸타는 도끼 공포적중+5 **/
		if (itemId == 7000221) 
			_owner.AddSpecialPierce(eKind.FEAR, -5);
		
		/** 썸타는 무기류 PVP 효과 부여 **/
		EquipmentEnchantOptions.applyCourtshipWeaponPvp(_owner, weapon.getItem(), enchant, -1, null);
   
		if (_owner.hasSkillEffect(L1SkillId.이뮨투함블레이드)) {
			if (_owner.getType() == 0 || _owner.getType() == 1) {
				if (type == 1 || type == 3)
			_owner.removeSkillEffect(L1SkillId.이뮨투함블레이드);
			}
			if (_owner.getType() == 2 ) {
				if (type == 4 || type == 13)
			_owner.removeSkillEffect(L1SkillId.이뮨투함블레이드);
			}
			if (_owner.getType() == 3 ) {
				if (type == 7 || type == 16 || type == 1)
			_owner.removeSkillEffect(L1SkillId.이뮨투함블레이드);
			}
			if (_owner.getType() == 4 ) {
				if (type == 12 || type == 11 )
			_owner.removeSkillEffect(L1SkillId.이뮨투함블레이드);
			}
			if (_owner.getType() == 5 ) {
				if (type == 1 || type == 3 || type == 18)
			_owner.removeSkillEffect(L1SkillId.이뮨투함블레이드);
			}
			if (_owner.getType() == 6 ) {
				if (type == 17)
			_owner.removeSkillEffect(L1SkillId.이뮨투함블레이드);
			}
			if (_owner.getType() == 7 ) {
				if (type == 6 || type == 15)
			_owner.removeSkillEffect(L1SkillId.이뮨투함블레이드);
			}
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
		
		
		if (getWeaponCount() == 1)
			_owner.setCurrentWeapon(getWeapon().getItem().getType1());
		else
			_owner.setCurrentWeapon(0);

		if (itemId == 203003) { // 데스나이트의 불검:진
			L1PolyMorph.undoPoly(_owner);
		}
		
		if ((type == 7 || type == 16 || type == 17) && (weapon.get_bless_level() != 0)) {
			_owner.AddStatSp(-weapon.get_bless_level());
			try { SC_SPMR.send(_owner); }
			catch (RuntimeException notice) { equipmentNoticeFailed(notice); }
		}
		
	    removeWeaponFromSlots(weapon);	
	}

	private void removeArmor(L1ItemInstance armor) {
		L1Item item = armor.getItem();
		int itemId = armor.getItem().getItemId();
		int itemlvl = armor.getEnchantLevel();
		int itemtype = armor.getItem().getType();
		
		
		if (itemId == 123111 || itemId == 123112 || itemId == 123113) {
			
			
				 try { SC_InventoryIcon.icoEnd(_owner, L1SkillId.할파스의신의1); }
				 catch (RuntimeException notice) { equipmentNoticeFailed(notice); }
			
				 try { SC_InventoryIcon.icoEnd(_owner, L1SkillId.할파스의신의); }
				 catch (RuntimeException notice) { equipmentNoticeFailed(notice); }
			 
		}
		
		final int classBonus = removeArmorVitalBonus(armor.getId());

		if(itemId == 900111){
			_owner.removeSkillEffect(L1SkillId.TELEPORT_RULER); 
		}
		
	
		applyArmorBlessBonus(armor, -1);

		applyArmorEnchantDodge(-armorEnchantDodge(itemId, armor.getEnchantLevel()));

		EquipmentEnchantOptions.applyWards(_owner, item, itemlvl, -1, null);
		//착용 해제
		if (itemId == 30184) {
			_owner.addHitup(-2);
		}
		if (itemId == 20272 || itemId == 222342) {
			_owner.addBowHitup(-3);
		}
		applyClassArmorBonus(classBonus, -1);
		EquipmentEnchantOptions.applyMagicClothing(_owner, item, itemlvl, -1, null);
		EquipmentEnchantOptions.applyEnhancedWard(_owner, item, itemlvl, -1, null);
		
	
		EquipmentEnchantOptions.applyArmorDefenseOptions(_owner, item, itemlvl, -1, null);
			
		
		
		applyArmorDefense(armor, armor.getEnchantLevel(), -1);
		applyArmorEnchantCasting(itemId, itemlvl, 0, null);

		applyArmorStatBonus(item, -1);
		validateSetItem(itemId, false, false, false);

		if (itemId == 900022) {
			try { SC_PacketBox.send(_owner, SC_PacketBox.UNLIMITED_ICON1, 484, false); }
			catch (RuntimeException notice) { equipmentNoticeFailed(notice); }
		}
		
		if (itemId == 20077 || itemId == 20062 || itemId == 120077) {
			_owner.delInvis();
		
		}
		if (itemId == 20288 || itemId == 900111) {
		    try { SC_Ability.send(_owner, 1, false); }
		    catch (RuntimeException notice) { equipmentNoticeFailed(notice); }
		}
		if (itemId == 20281) {
			try { SC_Ability.send(_owner, 2, false); }
			catch (RuntimeException notice) { equipmentNoticeFailed(notice); }
		}
		/*if (itemId == 900075) {
			RAbility.send(_owner, 7, false);
			RAbility.send(_owner, 2, false);
			_owner.setPolyRingMaster(false);
		}*/
		if (itemId == 20036) {
			try { SC_Ability.send(_owner, 3, false); }
			catch (RuntimeException notice) { equipmentNoticeFailed(notice); }
		}
		if (itemId == 20284) {
			try { SC_Ability.send(_owner, 5, false); }
			catch (RuntimeException notice) { equipmentNoticeFailed(notice); }
		}
		if (itemId == 20207) refreshEvaAfterRemoval(armor);
//		if (itemId == 22200 || itemId == 22201 || itemId == 22202 || itemId == 22203) {// 파푸리온 갑옷
//			_owner.stopPapuBlessing();
//		}else if(itemId >= 22208 && itemId <= 22211 || itemId == 922208)
//			_owner.stopValaBlessing();

		applyElixirRuneBonus(itemId, -1);
		
		/** 화령의 가더 **/
		EquipmentEnchantOptions.applyGuardersAndSanctuaryArmor(_owner, item, itemlvl, -1, null);
		/** 유니콘의 완력 각반 **/
		EquipmentEnchantOptions.applyUnicornGreaves(_owner, item, itemlvl, -1, null);
		/** 체력의 각반 **/
		EquipmentEnchantOptions.applyVitalityGreaves(_owner, item, itemlvl, -1, null);
		/** 예언자의 견갑 **/
		EquipmentEnchantOptions.applyShouldersAndCloak(_owner, item, itemlvl, -1, null);
		/** 맘보 모자 **/
		EquipmentEnchantOptions.applyMamboHat(_owner, item, itemlvl, -1, null);
		/** 커츠의 투사 휘장 **/
		EquipmentEnchantOptions.applyBadges(_owner, item, itemlvl, -1, null);	
		/** 대마법사의 모자 **/
		EquipmentEnchantOptions.applyMagicAndRareArmor(_owner, item, itemlvl, -1, null);
		/** 거대여왕 개미의 금빛,은빛 날개 **/
		EquipmentEnchantOptions.applyWingsAndAccessory(_owner, item, itemlvl, -1, null);
		/** 뱀파이어의 망토 **/
		EquipmentEnchantOptions.applyVampireCloak(_owner, item, itemlvl, -1, null);
	
		
		EquipmentEnchantOptions.applyCrimsonArmor(_owner, item, itemlvl, -1, null);
		/** 아이리스의 부츠 **/
		EquipmentEnchantOptions.applyIrisBoots(_owner, item, itemlvl, -1, null);
		/** 흑기사의 면갑 대미지감소 **/
		EquipmentEnchantOptions.applyHelmsAndDragonArmor(_owner, item, itemlvl, -1, null);
		
		if (itemId == 20279) {
			
			_owner.AddPvpDamageReduction(-2);
				
			}
		if (itemId == 20314) {

			_owner.AddPvpDamage(-2);
			

			}
		
		/** 머미로드의 왕관 **/
		EquipmentEnchantOptions.applyAccessoryOptions(_owner, item, itemlvl, -1, null);
		_armors.remove(armor);
		if (EquipmentEnchantOptions.accessoryHeal(item, itemlvl) != 0) refreshAccessoryHeal();
	}

	// Inventory owns the equipped flag and persistence. This stage owns slot and effect changes.
	void equipItem(L1ItemInstance equipment) {
		final L1Item item = equipment.getItem();
		final int type = item.getType2();
		if (type == 0) return;

		if (type == 1 && !hasRoomForWeapon(equipment)) {
			throw new IllegalStateException("Weapon equip was submitted without a free slot");
		}

		applyStatBonus(equipment, item, 1);
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
		if (item.getItemId() == 20383 && _owner.hasSkillEffect(L1SkillId.STATUS_BRAVE)) {
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
		// A set polymorph can synchronously unequip gear. Complete this item's
		// own options and callbacks before exposing that transition.
		if (equipment.isEquipped()
				&& validateSetItem(item.getItemId(), true, type == 2 && item.getType() == 9, true)) {
			try { SC_SPMR.send(_owner); }
			catch (RuntimeException notice) { equipmentNoticeFailed(notice); }
		}
		// The last charge can still activate the set's polymorph on this equip.
		if (type == 2 && item.getItemId() == 20383 && equipment.getChargeCount() != 0) {
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

	private void applyStatBonus(L1ItemInstance equipment, L1Item item, int sign) {
		ItemStat.BLESS_CONSUMO.apply(_owner, sign * item.get_addeinhasadper());
		ItemStat.MAX_HP.apply(_owner, sign * item.get_addhp());
		ItemStat.MAX_MP.apply(_owner, sign * item.get_addmp());
		final var ability = _owner;
		ability.AddStatBonusStr((byte) (sign * item.get_addstr()));
		ability.AddStatBonusCon((byte) (sign * item.get_addcon()));
		ability.AddStatBonusDex((byte) (sign * item.get_adddex()));
		ability.AddStatBonusInt((byte) (sign * item.get_addint()));
		ability.AddStatBonusWis((byte) (sign * item.get_addwis()));
		_owner.add_missile_critical_rate(sign * item.get_missile_critical_probability());
		_owner.add_melee_critical_rate(sign * item.get_melee_critical_probability());
		if (item.get_addwis() != 0) _owner.resetBaseMr();
		ability.AddStatBonusCha((byte) (sign * item.get_addcha()));

		int magicResistance = equipment.getMr();
		if (item.getItemId() == 20236 && _owner.isElf()) magicResistance += 5;
		if (magicResistance != 0) {
			ItemStat.MR.apply(_owner, sign * magicResistance);
			try { SC_SPMR.send(_owner); }
			catch (RuntimeException notice) { equipmentNoticeFailed(notice); }
		}
		if (item.get_addsp() != 0) {
			ItemStat.SP.apply(_owner, sign * item.get_addsp());
			try { SC_SPMR.send(_owner); }
			catch (RuntimeException notice) { equipmentNoticeFailed(notice); }
		}
	}

	private void applyArmorBlessBonus(L1ItemInstance armor, int sign) {
		final int extraHp = armor.get_bless_level2();
		if (extraHp > 0) ItemStat.MAX_HP.apply(_owner, sign * extraHp);

		switch (armor.get_bless_level()) {
		case 1, 4 -> ItemStat.MAX_HP.apply(_owner, sign * 30);
		case 2 -> ItemStat.MAX_HP.apply(_owner, sign * 20);
		case 3 -> ItemStat.MAX_HP.apply(_owner, sign * 10);
		case 5 -> ItemStat.MAX_HP.apply(_owner, sign * 40);
		case 6 -> ItemStat.MAX_HP.apply(_owner, sign * 50);
		case 7, 23 -> ItemStat.DMG_RED.apply(_owner, sign);
		case 8 -> ItemStat.DMG_RED.apply(_owner, sign * 2);
		case 10 -> ItemStat.ABILITY_PLERCE.apply(_owner, sign);
		case 11 -> ItemStat.FEAR_PLERCE.apply(_owner, sign);
		case 12 -> ItemStat.DRAGON_SPELL_PLERCE.apply(_owner, sign);
		case 13 -> ItemStat.SPIRIT_PLERCE.apply(_owner, sign);
		case 14 -> ItemStat.MAGIC_HIT.apply(_owner, sign);
		case 15 -> ItemStat.ABILITY_RESIS.apply(_owner, sign);
		case 16 -> ItemStat.FEAR_RESIS.apply(_owner, sign);
		case 17 -> ItemStat.DRAGON_SPELL_RESIS.apply(_owner, sign);
		case 18 -> ItemStat.SPIRIT_RESIS.apply(_owner, sign);
		case 19 -> ItemStat.DMG_RATE.apply(_owner, sign);
		case 20 -> ItemStat.BOW_DMG.apply(_owner, sign);
		case 21 -> ItemStat.HIT.apply(_owner, sign);
		case 22 -> ItemStat.BOW_HIT.apply(_owner, sign);
		case 24 -> ItemStat.AC.apply(_owner, -sign);
		case 25 -> ItemStat.MR.apply(_owner, sign * 3);
		case 26 -> ItemStat.SP.apply(_owner, sign);
		default -> { }
		}
	}

	private int applyArmorVitalBonus(L1ItemInstance armor, L1Item item) {
		final var ability = _owner;
		final int conBonus = item.get_addcon();
		final int wisBonus = item.get_addwis();
		final int hp = conBonus > 0 ? ability.GetStatCon() / 2 * conBonus : 0;
		final int mp = wisBonus > 0 ? ability.GetStatWis() / 2 * wisBonus : 0;
		final int classBonus = item.getItemId() == 800110 ? selectClassArmorBonus().ordinal() + 1 : 0;
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

	public ClassArmorBonus getClassArmorBonus(L1ItemInstance armor) {
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

	private void applyClassArmorBonus(int option, int sign) {
		if (option == 0) return;
		_owner.addAc(-8 * sign);
		switch (CLASS_ARMOR_BONUSES[option - 1]) {
		case MAGIC -> {
			_owner.AddStatSp(2 * sign);
			_owner.addBaseMagicHitUp(2 * sign);
		}
		case BOW -> {
			_owner.addBowHitup(2 * sign);
			_owner.addBowDmgRate(2 * sign);
		}
		case MELEE -> {
			_owner.addHitup(2 * sign);
			_owner.addDmgRate(2 * sign);
		}
		}
		_owner.addDamageReductionByArmor(2 * sign);
		_owner.AddSpecialResistance(eKind.FEAR, 2 * sign);
	}

	private void applyArmorDefense(L1ItemInstance armor, int enchant, int sign) {
		final L1Item item = armor.getItem();
		if (item.getType() == 30) return;
		ItemStat.AC.apply(_owner, sign * armorDefense(armor, enchant));
		if (!isRoomteece(item.getItemId())) {
			try { SC_OwnCharAttrDef.send(_owner); }
			catch (RuntimeException notice) { equipmentNoticeFailed(notice); }
		}
	}

	boolean applyArmorDurabilityChange(L1ItemInstance armor, int previousDurability) {
		final int durability = armor.get_durability();
		final L1Item item = armor.getItem();
		if (previousDurability == durability || !armor.isEquipped() || item.getType2() != 2
				|| item.getType() == 30 || isRoomteece(item.getItemId())) return false;
		ItemStat.AC.apply(_owner, durability - previousDurability);
		return true;
	}

	private static int armorDefense(L1ItemInstance armor, int enchant) {
		final L1Item item = armor.getItem();
		final int itemId = item.getItemId();
		final int type = item.getType();
		if (type == 30) return 0;
		if (isRoomteece(itemId)) return item.get_ac();

		int enchantAc = enchant;
		if (type >= 8 && type <= 12) {
			enchantAc = (type == 8 || type == 12) && enchant >= 5
					? (enchant >= 10 ? 6 : Math.min(enchant - 4, 5)) : 0;
		} else if (itemId >= 900117 && itemId <= 900120) {
			enchantAc = Math.max(enchant, 0);
		}
		return item.get_ac() - enchantAc + armor.get_durability();
	}

	private void applyArmorEnchantCasting(int itemId, int oldEnchant, int enchant, int[] changes) {
		if (itemId == 202022) {
			final int steps = Math.max(enchant, 0) - Math.max(oldEnchant, 0);
			if (steps != 0) {
				EquipmentEnchantOptions.applyStat(_owner, changes, ItemStat.MAX_MP, steps * 10);
				EquipmentEnchantOptions.applyStat(_owner, changes, ItemStat.MR, steps * 2);
			}
		} else if (itemId == 20107) {
			final int sp = Math.max(enchant - 2, 0) - Math.max(oldEnchant - 2, 0);
			if (sp != 0) {
				EquipmentEnchantOptions.applyStat(_owner, changes, ItemStat.SP, sp);
			}
		}
	}

	private static int armorEnchantDodge(int itemId, int enchant) {
		if (itemId == 22228) {
			return switch (Math.min(enchant, 9)) {
				case 9 -> 7;
				case 8 -> 4;
				case 7 -> 1;
				default -> 0;
			};
		}
		if (itemId == 222334) {
			return switch (Math.min(enchant, 9)) {
				case 9 -> 9;
				case 8 -> 5;
				case 7 -> 3;
				case 6 -> 1;
				default -> 0;
			};
		}
		return 0;
	}

	private void applyArmorEnchantDodge(int amount) {
		if (amount != 0) _owner.addMagicDodgeProbability(amount);
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
			changes[ItemStat.MAGIC_DODGE.ordinal()] += armorEnchantDodge(itemId, enchant) - armorEnchantDodge(itemId, oldEnchant);
			applyArmorEnchantCasting(itemId, oldEnchant, enchant, changes);
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

	public static int elixirRuneAccuracy(int itemId) {
		if (itemId >= 900140 && itemId <= 900144) return 5;
		if (itemId >= 900145 && itemId <= 900149) return 10;
		return 0;
	}

	private void applyElixirRuneBonus(int itemId, int sign) {
		final boolean lowTier = itemId >= 222295 && itemId <= 222299;
		final boolean middleTier = itemId >= 222312 && itemId <= 222316
				|| itemId >= 900135 && itemId <= 900139;
		final int precision = elixirRuneAccuracy(itemId);
		if (!lowTier && !middleTier && precision == 0) return;
		if (precision == 5) _owner.addPVPMagicDamageReduction(2 * sign);

		switch (_owner.getType()) {
		case 0 -> {
			ItemStat.DMG_RED.apply(_owner, 3 * sign);
			if (precision != 0) {
				ItemStat.HIT.apply(_owner, 2 * sign);
				ItemStat.ABILITY_PLERCE.apply(_owner, precision * sign);
			} else if (!lowTier) {
				ItemStat.DMG_RATE.apply(_owner, 2 * sign);
			}
		}
		case 1 -> {
			ItemStat.MAX_HP.apply(_owner, 50 * sign);
			if (!lowTier) ItemStat.DMG_RATE.apply(_owner, sign);
			if (precision != 0) ItemStat.ABILITY_PLERCE.apply(_owner, precision * sign);
		}
		case 2 -> {
			ItemStat.MAX_MP.apply(_owner, 50 * sign);
			if (!lowTier) {
				ItemStat.BOW_DMG.apply(_owner, sign);
				ItemStat.DMG_RATE.apply(_owner, sign);
			}
			if (precision != 0) ItemStat.SPIRIT_PLERCE.apply(_owner, precision * sign);
		}
		case 3 -> {
			ItemStat.MPR.apply(_owner, 3 * sign);
			if (itemId == 222297) ItemStat.MAX_MP.apply(_owner, 30 * sign);
			if (!lowTier) ItemStat.SP.apply(_owner, sign);
			if (precision != 0) ItemStat.MAGIC_HIT.apply(_owner, precision * sign);
		}
		case 4 -> {
			ItemStat.AC.apply(_owner, -3 * sign);
			if (!lowTier) ItemStat.MAX_MP.apply(_owner, 30 * sign);
			if (precision != 0) ItemStat.SPIRIT_PLERCE.apply(_owner, precision * sign);
		}
		case 5 -> {
			ItemStat.HIT.apply(_owner, 3 * sign);
			if (!lowTier) ItemStat.DMG_RED.apply(_owner, sign);
			if (precision != 0) ItemStat.DRAGON_SPELL_PLERCE.apply(_owner, precision * sign);
		}
		case 6 -> {
			ItemStat.WEIGHT.apply(_owner, 5 * sign);
			if (!lowTier) ItemStat.MAX_HP.apply(_owner, 50 * sign);
			if (precision != 0) ItemStat.DRAGON_SPELL_PLERCE.apply(_owner, precision * sign);
		}
		case 7 -> {
			if (!lowTier) ItemStat.MR.apply(_owner, 5 * sign);
			ItemStat.MAX_HP.apply(_owner, 50 * sign);
			if (precision != 0) ItemStat.FEAR_PLERCE.apply(_owner, precision * sign);
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
        ItemPresentatorFactory.applyEquipment(_owner, equipment, equipped);
    }

	private void applyRegisteredStatBonus(L1ItemInstance equipment, L1Item item, int sign) {
		final ItemEffectRegistry.Def definition = ItemEffectRegistry.get(item.getItemId());
		if (definition != null) definition.apply(_owner, _owner.getType(), equipment.getEnchantLevel(), sign);
	}

	private static long magicHelmSpells(int itemId) {
		return switch (itemId) {
		case 20013 -> spellBit(26) | spellBit(43);
		case 20014 -> spellBit(1) | spellBit(19);
		case 20015 -> spellBit(12) | spellBit(13) | spellBit(42);
		case 20008 -> spellBit(43);
		case 20023 -> spellBit(54);
		default -> 0L;
		};
	}

	private static long spellBit(int skillId) {
		return 1L << (skillId - 1);
	}

	private static int spellMask(long spells, int level) {
		return (int) (spells >>> (level * Byte.SIZE)) & 0xff;
	}

	private void showMagicHelmSpells(L1ItemInstance item) {
		final long spells = magicHelmSpells(item.getItemId());
		if (spells == 0L) return;
		try { SC_AddSkill.send(_owner, spellMask(spells, 0), spellMask(spells, 1), spellMask(spells, 2),
				spellMask(spells, 3), spellMask(spells, 4), spellMask(spells, 5), spellMask(spells, 6),
				0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0); }
		catch (RuntimeException notice) { equipmentNoticeFailed(notice); }
	}

	private void retireMagicHelmSpells(L1ItemInstance removed) {
		long spells = magicHelmSpells(removed.getItemId());
		if (spells == 0L) return;
		for (int i = 0; i < _armors.size(); i++) {
			final L1ItemInstance armor = _armors.get(i);
			if (armor != removed && armor.isEquipped()) spells &= ~magicHelmSpells(armor.getItemId());
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

	public static boolean isSetMainItem(int itemId) {
		return CSetItem.isMainItem(itemId);
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
			if (armor != removed && armor.isEquipped() && armor.getItemId() == 20207) return true;
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
