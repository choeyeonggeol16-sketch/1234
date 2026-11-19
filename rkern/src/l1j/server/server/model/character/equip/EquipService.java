package l1j.server.server.model.character.equip;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import java.util.List;
import l1j.server.server.model.L1PcInventory;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

public final class EquipService {

    private EquipService() {}

    public static void useArmor(L1PcInstance pc, L1ItemInstance armor) {
        if (pc == null || armor == null || armor.getItem() == null) return;

        final L1PcInventory inv = pc.getInventory();

        // 이미 착용중 -> 해제
        if (armor.isEquipped()) {
            inv.setEquipped(armor, false);
            return;
        }

        EquipResult r = EquipRules.canEquipArmor(pc, inv, armor);
        if (!r.ok) { r.applyFail(pc); return; }

        pc.cancelAbsoluteBarrier();
        inv.setEquipped(armor, true);
    }

    public static void useWeapon(L1PcInstance pc, L1ItemInstance weapon) {
        if (pc == null || weapon == null || weapon.getItem() == null) return;

        final L1PcInventory inv = pc.getInventory();
        final L1ItemInstance current = pc.getEquipSlot().getWeapon();

        // "다른 무기 클릭"인 경우에만 기본 체크
        if (current == null || !pc.getEquipSlot().isWeapon(weapon)) {
            EquipResult r = EquipRules.canEquipWeaponBasic(pc, inv, weapon);
            if (!r.ok) { r.applyFail(pc); return; }
        }

        pc.cancelAbsoluteBarrier();

        if (current != null) {
            // 저주 무기는 해제 불가
            if (current.getItem().getBless() == 2) {
                SC_ServerMessage.send(pc, 150);
                return;
            }

            // 같은 무기를 다시 클릭한 경우
            if (pc.getEquipSlot().isWeapon(weapon)) {
                toggleSameWeapon(pc, inv, weapon);
                return;
            }

            // 다른 무기 장착: 슬레이어 쌍도끼 예외 or 기존무기 전부 해제
            boolean shieldWeapon = false;

            EquipResult slayer = EquipRules.canEquipAsSecondWeaponSlayer(pc, inv, current, weapon);
            if (slayer.ok) {
                shieldWeapon = true;
            } else if (slayer.fail != null) {
                // 해당 조건인데 실패 메시지 있을 때만 막는다
                slayer.applyFail(pc);
                return;
            } else {
                // 일반 케이스: 기존 무기들 해제
                for (L1ItemInstance it : pc.getEquipSlot().getWeapons()) {
                    inv.setEquipped(it, false, false, true, false);
                }
            }

            // 특수 메시지
            if (weapon.getItemId() == 200002) {
                SC_ServerMessage.send(pc, 149, weapon.getLogName());
            }

            inv.setEquipped(weapon, true, false, false, shieldWeapon);
            return;
        }

        // 무기 없으면 그냥 착용
        if (weapon.getItemId() == 200002) {
            SC_ServerMessage.send(pc, 149, weapon.getLogName());
        }
        inv.setEquipped(weapon, true, false, false, false);
    }

    private static void toggleSameWeapon(L1PcInstance pc, L1PcInventory inv, L1ItemInstance weapon) {
    	final List<L1ItemInstance> equipped = pc.getEquipSlot().getWeapons();
    	final int count = (equipped != null) ? equipped.size() : 0;

    	// 2자루 이상(슬레이어 쌍도끼)
    	if (count >= 2) {
    		// 슬롯 기준: 0=왼쪽(메인), 1=오른쪽(세컨)
    		final L1ItemInstance left  = equipped.get(0);
    		final L1ItemInstance right = equipped.get(1);

    		// 클릭한 무기가 현재 장착된 두 자루 중 하나가 아니면 그냥 리턴
    		final boolean clickedLeft  = (left  != null && left.getId()  == weapon.getId());
    		final boolean clickedRight = (right != null && right.getId() == weapon.getId());
    		if (!clickedLeft && !clickedRight) return;

    		// 클릭한 반대쪽 무기만 남기기
    		final L1ItemInstance keep = clickedLeft ? right : left;

    		// 두 자루 모두 해제
    		if (left != null)  inv.setEquipped(left,  false, false, false, false);
    		if (right != null) inv.setEquipped(right, false, false, false, false);

    		// 반대 무기를 "단일 무기"로 다시 착용
    		if (keep != null) {
    			inv.setEquipped(keep, true, false, false, false);
    		}
    		return;
    	}

    	// 1자루면 그냥 해제
    	inv.setEquipped(weapon, false, false, false, false);
    }
}
