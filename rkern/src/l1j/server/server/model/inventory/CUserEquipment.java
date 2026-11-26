package l1j.server.server.model.inventory;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_CharVisualUpdate;
import l1j.server.server.model.network.core.sc.packet.SC_ItemName;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.storage.CacheD;
import l1j.server.server.templates.L1Item;

/** Owns player equipment requests and publishes the result of an applied slot change. */
public final class CUserEquipment {
    private static final Logger LOG = Logger.getLogger(CUserEquipment.class.getName());

    private CUserEquipment() {}

    public static void UseItem(L1PcInstance pc, L1ItemInstance item) {
        if (pc == null || item == null) return;
        final L1Item template = item.getItem();
        if (template == null) return;
        final int type = template.getType2();
        if (type != 1 && type != 2) return;

        // Weapon and armor requests have different legacy rejection precedence.
        if (type == 1 && !checkLevel(pc, template)) return;
        if (!EquipRules.canUseByClass(pc, template)) {
            SC_ServerMessage.send(pc, 264);
            return;
        }
        if (type == 2 && !checkLevel(pc, template)) return;

        final CUserInventory inventory = pc.getInventory();
        if (!inventory.tryBeginEquipmentChange()) return;
        try {
            // Reject a stale request before it can retire the currently worn item.
            if (inventory.GetByServerID(item.getId()) != item || item.getItem() != template) return;
            if (type == 1) useWeapon(pc, inventory, item);
            else useArmor(pc, inventory, item);
        } finally {
            inventory.endEquipmentChange();
        }
    }

    private static boolean checkLevel(L1PcInstance pc, L1Item template) {
        final int min = template.getMinLevel();
        final int max = template.getMaxLevel();
        if (min != 0 && min > pc.getLevel()) {
            SC_ServerMessage.send(pc, 318, String.valueOf(min));
            return false;
        }
        if (max != 0 && max < pc.getLevel()) {
            if (max < 50) SC_PacketBox.send(pc, SC_PacketBox.MSG_LEVEL_OVER, max);
            else SC_SystemMessage.send(pc, "이 아이템은" + max + "레벨 이하만 사용할 수 있습니다. ");
            return false;
        }
        return true;
    }

    private static void useArmor(L1PcInstance pc, CUserInventory inventory, L1ItemInstance armor) {
        if (armor.isEquipped()) {
            applyEquipment(pc, inventory, armor, false, false, false);
            return;
        }
        final EquipResult result = EquipRules.canEquipArmor(pc, inventory, armor);
        if (!result.ok) {
            result.applyFail(pc);
            return;
        }
        pc.cancelAbsoluteBarrier();
        applyEquipment(pc, inventory, armor, true, false, false);
    }

    private static void useWeapon(L1PcInstance pc, CUserInventory inventory, L1ItemInstance weapon) {
        final L1EquipmentSlot slots = pc.getEquipSlot();
        final L1ItemInstance current = slots.getWeapon();
        final boolean alreadyWorn = slots.isWeapon(weapon);
        if (current == null || !alreadyWorn) {
            final EquipResult result = EquipRules.canEquipWeaponBasic(pc, inventory, weapon);
            if (!result.ok) {
                result.applyFail(pc);
                return;
            }
        }

        pc.cancelAbsoluteBarrier();
        if (current == null) {
            equipSelectedWeapon(pc, inventory, weapon);
            return;
        }
        if (isCursed(current) || (alreadyWorn && isCursed(weapon))) {
            SC_ServerMessage.send(pc, 150);
            return;
        }
        if (alreadyWorn) {
            applyEquipment(pc, inventory, weapon, false, false, false);
            return;
        }

        final EquipResult secondSlot = EquipRules.canEquipAsSecondWeaponSlayer(pc, inventory, current, weapon);
        if (secondSlot.ok) {
            equipSelectedWeapon(pc, inventory, weapon);
            return;
        }
        if (secondSlot.fail != null) {
            secondSlot.applyFail(pc);
            return;
        }

        final L1ItemInstance secondary = slots.getSecondWeapon();
        if (isCursed(secondary)) {
            SC_ServerMessage.send(pc, 150);
            return;
        }
        if (!weapon.startEquipmentTimer(pc)) return;
        try {
            applyEquipment(pc, inventory, current, false, false, true);
            if (secondary != null) applyEquipment(pc, inventory, secondary, false, false, true);
            equipSelectedWeapon(pc, inventory, weapon);
        } finally {
            if (!weapon.isEquipped()) weapon.stopEquipmentTimer();
        }
    }

    private static void equipSelectedWeapon(L1PcInstance pc, CUserInventory inventory, L1ItemInstance weapon) {
        if (weapon.getItemId() == 200002) {
            try { SC_ServerMessage.send(pc, 149, weapon.getLogName()); }
            catch (RuntimeException notice) { noticeFailed(pc, notice); }
        }
        applyEquipment(pc, inventory, weapon, true, false, false);
    }

    private static boolean isCursed(L1ItemInstance weapon) {
        return weapon != null && weapon.getItem().getBless() == 2;
    }

    public static void ChangeEquipment(L1PcInstance pc, L1ItemInstance item,
            boolean equipped, boolean loaded, boolean changingWeapon) {
        if (item == null) return;
        final CUserInventory inventory = pc.getInventory();
        // Receipt replay already owns the inventory reservation.
        final boolean reserve = !CacheD.isItemTransferReplaying();
        if (reserve && !inventory.tryBeginEquipmentChange()) return;
        try {
            applyEquipment(pc, inventory, item, equipped, loaded, changingWeapon);
        } finally {
            if (reserve) inventory.endEquipmentChange();
        }
    }

    private static void applyEquipment(L1PcInstance pc, CUserInventory inventory, L1ItemInstance item,
            boolean equipped, boolean loaded, boolean changingWeapon) {
        if (inventory.GetByServerID(item.getId()) != item || item.isEquipped() == equipped) return;
        final L1Item template = item.getItem();
        if (template == null) return;

        final L1EquipmentSlot slots = pc.getEquipSlot();
        final int itemId = item.getItemId();
        final int type = template.getType2();
        final boolean weapon = type == 1;
        if (equipped) {
            if (weapon && !slots.hasRoomForWeapon(item)) return;
            if (type == 2 && slots.getSecondWeapon() != null
                    && (template.getType() == 7 || template.getType() == 13)) return;
            if (isInvisibilityGear(itemId) && slots.isInvisibilityEquipCoolingDown()) return;
            if (type != 0 && !item.startEquipmentTimer(pc)) return;
            try {
                if (template.getType() != 4) clearArrowSelection(pc, inventory);
                item.setEquipped(true);
                inventory.SetEquippedCount(itemId, true);
                slots.equipItem(item);
            } finally {
                if (!item.isEquipped()) item.stopEquipmentTimer();
            }
            // A set polymorph may synchronously remove the newly applied item.
            if (!item.isEquipped()) return;
            if (!loaded && weapon && slots.getWeaponCount() == 2) {
                try { SC_SkillSound.send(pc, pc.getId(), 12534); }
                catch (RuntimeException notice) { noticeFailed(pc, notice); }
            }
        } else {
            beforeUnequip(pc, inventory, slots, template, itemId, loaded, weapon);
            item.setEquipped(false);
            item.stopEquipmentTimer();
            inventory.SetEquippedCount(itemId, false);
            slots.unequipItem(item);
        }
        sendEquipmentChanged(pc, inventory, slots, item, loaded, changingWeapon, weapon);
    }

    private static void sendEquipmentChanged(L1PcInstance pc, CUserInventory inventory, L1EquipmentSlot slots,
            L1ItemInstance item, boolean loaded, boolean changingWeapon, boolean weapon) {
        if (!loaded) {
            pc.setCurrentHp(pc.getCurrentHp());
            pc.setCurrentMp(pc.getCurrentMp());
            try { inventory.updateItem(item, CUserInventory.COL_EQUIPPED); }
            catch (RuntimeException notice) { noticeFailed(pc, notice); }
            if (weapon && !changingWeapon) {
                try { sendWeaponAppearance(pc); }
                catch (RuntimeException notice) { noticeFailed(pc, notice); }
            }
        }
        if (weapon) {
            try { SC_PacketBox.send(pc, SC_PacketBox.공격가능거리, pc, item); }
            catch (RuntimeException notice) { noticeFailed(pc, notice); }
        }
        try { slots.updateEquipmentWindow(item); }
        catch (RuntimeException notice) { noticeFailed(pc, notice); }
    }

    private static void sendWeaponAppearance(L1PcInstance pc) {
        final long visual = SC_CharVisualUpdate.pack(pc);
        try { SC_CharVisualUpdate.sendPacked(pc, visual); }
        catch (RuntimeException notice) { noticeFailed(pc, notice); }
        SC_CharVisualUpdate.broadcastFastPacked(pc, visual);
    }

    private static boolean isInvisibilityGear(int itemId) {
        return itemId == 20077 || itemId == 20062 || itemId == 120077;
    }

    private static void clearArrowSelection(L1PcInstance pc, CUserInventory inventory) {
        final int arrowId = inventory.getArrowItemId();
        if (arrowId == 0) return;
        final L1ItemInstance arrow = inventory.GetFirstItemByClassID(arrowId);
        inventory.setArrow(0);
        if (arrow != null) {
            try { SC_ItemName.send(pc, arrow); }
            catch (RuntimeException notice) { noticeFailed(pc, notice); }
        }
    }

    private static void beforeUnequip(L1PcInstance pc, CUserInventory inventory, L1EquipmentSlot slots,
            L1Item template, int itemId, boolean loaded, boolean weapon) {
        if (!loaded && isInvisibilityGear(itemId)) {
            if (pc.isInvisble()) {
                pc.delInvis();
                pc.beginInvisTimer();
            }
            slots.markInvisibilityGearRemoved();
        }
        if (weapon && template.getType1() == 4 && pc.hasSkillEffect(L1SkillId.INFERNO)) {
            pc.removeSkillEffect(L1SkillId.INFERNO);
        }
        if (template.isTwohandedWeapon() && pc.hasSkillEffect(L1SkillId.COUNTER_BARRIER)) {
            pc.removeSkillEffect(L1SkillId.COUNTER_BARRIER);
            try { SC_PacketBox.send(pc, SC_PacketBox.UNLIMITED_ICON1, 71, false); }
            catch (RuntimeException notice) { noticeFailed(pc, notice); }
        }
        if (weapon) {
            pc.remove_elf_second_brave();
            if (template.getType() == 4) clearArrowSelection(pc, inventory);
        }
    }

    public static void RestoreEquippedItems(L1PcInstance pc, List<L1ItemInstance> loadedEquipment) {
        final CUserInventory inventory = pc.getInventory();
        if (!inventory.tryBeginEquipmentChange())
            throw new IllegalStateException("Equipment restore busy: " + pc.getId());
        try {
            for (int i = 0, size = loadedEquipment.size(); i < size; i++) {
                final L1ItemInstance item = loadedEquipment.get(i);
                if (!item.isEquipped() || inventory.GetRegisteredItemByServerID(item.getId()) != item) continue;
                item.setEquipped(false);
                final L1Item template = item.getItem();
                if (template == null || (template.getType2() != 1 && template.getType2() != 2)) {
                    LOG.warning("Invalid persisted equipment: " + item.getId());
                    continue;
                }
                applyEquipment(pc, inventory, item, true, true, false);
            }
        } finally {
            inventory.endEquipmentChange();
        }
    }

    public static void TakeOffItems(L1PcInstance pc, int polyId) {
        final L1EquipmentSlot slots = pc.getEquipSlot();
        final L1ItemInstance primary = slots.getWeapon();
        final L1ItemInstance secondary = slots.getSecondWeapon();
        if (primary != null && !L1PolyMorph.isEquipableWeapon(polyId, primary.getItem().getType())) {
            pc.UnequipItem(primary);
        }
        if (secondary != null && !L1PolyMorph.isEquipableWeapon(polyId, secondary.getItem().getType())) {
            pc.UnequipItem(secondary);
        }
        for (int type = 0; type <= 12; type++) {
            if (L1PolyMorph.isEquipableArmor(polyId, type)) continue;
            L1ItemInstance armor;
            while ((armor = slots.getAppliedArmor(type)) != null) {
                pc.UnequipItem(armor);
                if (armor.isEquipped()) break;
            }
        }
    }

    static void noticeFailed(L1PcInstance pc, RuntimeException notice) {
        LOG.log(Level.WARNING, "Equipment notification failed: " + pc.getId(), notice);
    }
}
