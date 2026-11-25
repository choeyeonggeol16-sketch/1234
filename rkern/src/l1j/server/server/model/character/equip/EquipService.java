package l1j.server.server.model.character.equip;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1EquipmentSlot;
import l1j.server.server.model.CUserInventory;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

public final class EquipService {

    private EquipService() {}

    public static void useArmor(L1PcInstance pc, L1ItemInstance armor) {
        if (pc == null || armor == null || armor.getItem() == null) return;

        final CUserInventory inv = pc.getInventory();
        if (!inv.tryBeginEquipmentChange()) return;
        try {
            if (armor.isEquipped()) {
                inv.setEquipped(armor, false);
                return;
            }

            EquipResult r = EquipRules.canEquipArmor(pc, inv, armor);
            if (!r.ok) { r.applyFail(pc); return; }

            pc.cancelAbsoluteBarrier();
            inv.setEquipped(armor, true);
        } finally {
            inv.endEquipmentChange();
        }
    }

    public static void useWeapon(L1PcInstance pc, L1ItemInstance weapon) {
        if (pc == null || weapon == null || weapon.getItem() == null) return;

        final CUserInventory inv = pc.getInventory();
        if (!inv.tryBeginEquipmentChange()) return;
        try {
            useWeapon(pc, inv, weapon);
        } finally {
            inv.endEquipmentChange();
        }
    }

    private static void useWeapon(L1PcInstance pc, CUserInventory inv, L1ItemInstance weapon) {
        final L1EquipmentSlot slot = pc.getEquipSlot();
        final L1ItemInstance current = slot.getWeapon();

        // "다른 무기 클릭"인 경우에만 기본 체크
        if (current == null || !slot.isWeapon(weapon)) {
            EquipResult r = EquipRules.canEquipWeaponBasic(pc, inv, weapon);
            if (!r.ok) { r.applyFail(pc); return; }
        }

        pc.cancelAbsoluteBarrier();

        if (current == null) {
            equipWeapon(pc, inv, weapon, false);
            return;
        }
        if (isCursed(current)) {
            SC_ServerMessage.send(pc, 150);
            return;
        }
        if (slot.isWeapon(weapon)) {
            if (isCursed(weapon)) {
                SC_ServerMessage.send(pc, 150);
                return;
            }
            inv.setEquipped(weapon, false);
            return;
        }

        final EquipResult secondSlot = EquipRules.canEquipAsSecondWeaponSlayer(pc, inv, current, weapon);
        if (secondSlot.ok) {
            equipWeapon(pc, inv, weapon, true);
            return;
        }
        if (secondSlot.fail != null) {
            secondSlot.applyFail(pc);
            return;
        }

        final L1ItemInstance second = slot.getSecondWeapon();
        if (isCursed(second)) {
            SC_ServerMessage.send(pc, 150);
            return;
        }
        // Reserve time ticking before removing weapons already in use.
        if (!weapon.startEquipmentTimer(pc)) return;
        try {
            inv.setEquipped(current, false, false, true, false);
            if (second != null) inv.setEquipped(second, false, false, true, false);
            equipWeapon(pc, inv, weapon, false);
        } finally {
            if (!weapon.isEquipped()) weapon.stopEquipmentTimer();
        }
    }

    private static void equipWeapon(L1PcInstance pc, CUserInventory inv, L1ItemInstance weapon, boolean secondSlot) {
        if (weapon.getItemId() == 200002) {
            SC_ServerMessage.send(pc, 149, weapon.getLogName());
        }
        inv.setEquipped(weapon, true, false, false, secondSlot);
    }

    private static boolean isCursed(L1ItemInstance weapon) {
        return weapon != null && weapon.getItem().getBless() == 2;
    }
}
