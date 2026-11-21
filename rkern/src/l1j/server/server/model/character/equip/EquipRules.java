package l1j.server.server.model.character.equip;

import java.util.List;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1EquipmentSlot;
import l1j.server.server.model.L1PcInventory;
import l1j.server.server.model.L1PolyMorph;
import l1j.server.server.model.L1Quest;

public final class EquipRules {

    private EquipRules() {}

    // =========================
    // ARMOR RULES
    // =========================
    public static EquipResult canEquipArmor(L1PcInstance pc, L1PcInventory inv, L1ItemInstance armor) {
        if (pc == null || inv == null || armor == null || armor.getItem() == null) return EquipResult.fail(null);

        final int type = armor.getItem().getType();

        // 900111 특수
        if (armor.getItemId() == 900111 && (inv.isEquippedDirect(900111) || inv.isEquippedDirect(20288))) {
            return EquipResult.fail(EquipFail.text("이미 착용중인 아이템입니다."));
        }

        // 변신 장착 제한
        if (!L1PolyMorph.isEquipableArmor(pc.getCurrentSpriteId(), type)) {
            return EquipResult.fail(null);
        }

        final L1EquipmentSlot slot = pc.getEquipSlot();
        final List<L1ItemInstance> equipped = slot.getArmors();
        final String earringName = type == 12 ? armor.getName().replace("축복받은 ", "") : null;
        int sameType = 0;
        int sameId = 0;
        int sameGrade = 0;
        boolean sameEarringName = false;
        for (int i = 0; i < equipped.size(); i++) {
            final L1ItemInstance item = equipped.get(i);
            if (!item.isEquipped() || item.getItem().getType() != type) continue;
            sameType++;
            if (item.getItemId() == armor.getItemId()) sameId++;
            if (type == 9 && item.getItem().getGrade() == armor.getItem().getGrade()) sameGrade++;
            if (earringName != null && earringName.equals(item.getName().replace("축복받은 ", ""))) {
                sameEarringName = true;
            }
        }

        if (type == 9) {
            if (sameType >= 4) return EquipResult.fail(EquipFail.MSG_SLOT_OCCUPIED_144);
            if (!pc.getQuest().isEnd(L1Quest.QUEST_SLOT76) && sameType >= 2) {
                return EquipResult.fail(EquipFail.system("스냅퍼에게 76레벨 슬롯 개방 후 착용 가능"));
            }
            if (!pc.getQuest().isEnd(L1Quest.QUEST_SLOT81) && sameType >= 3) {
                return EquipResult.fail(EquipFail.system("스냅퍼에게 81레벨 슬롯 개방 후 착용 가능"));
            }
            if (sameId >= 2) return EquipResult.fail(EquipFail.MSG_DUP_ITEM_3278);
            if (sameGrade >= 2) return EquipResult.fail(EquipFail.MSG_DUP_GRADE_3279);
        } else if (type == 12) {
            if (sameType >= 2) return EquipResult.fail(EquipFail.MSG_SLOT_OCCUPIED_144);
            if (!pc.getQuest().isEnd(L1Quest.QUEST_SLOT59) && sameType >= 1) {
                return EquipResult.fail(EquipFail.system("스냅퍼에게 59레벨 슬롯 개방 후 착용 가능"));
            }
            if (sameId != 0 || sameEarringName) return EquipResult.fail(EquipFail.MSG_DUP_ITEM_3278);
        } else if (sameType != 0) {
            return EquipResult.fail(EquipFail.MSG_ALREADY_EQUIPPED_124);
        }

        if (type == 29 && !pc.getQuest().isEnd(L1Quest.QUEST_SLOT_SHOULD)) {
            return EquipResult.fail(EquipFail.MSG_SHOULDER_5077);
        }
        if (type == 30 && !pc.getQuest().isEnd(L1Quest.QUEST_SLOT_BADGE)) {
            return EquipResult.fail(EquipFail.MSG_BADGE_5078);
        }
        if ((type == 7 && slot.hasAppliedArmor(13)) || (type == 13 && slot.hasAppliedArmor(7))) {
            return EquipResult.fail(EquipFail.MSG_ALREADY_EQUIPPED_124);
        }

        // 쌍수 상태에서 방패/가더 금지
        if ((type == 7 || type == 13) && slot.getWeaponCount() == 2) {
            return EquipResult.fail(EquipFail.MSG_LEVEL_OVER_129);
        }

        // 방패 착용 시 양손무기 금지(UseType 13 예외 유지)
        if (type == 7 && pc.getWeapon() != null) {
            if (pc.getWeapon().getItem().isTwohandedWeapon() && armor.getItem().getUseType() != 13) {
                return EquipResult.fail(EquipFail.MSG_LEVEL_OVER_129);
            }
        }

        return EquipResult.OK;
    }

    // =========================
    // WEAPON RULES
    // =========================
    public static EquipResult canEquipWeaponBasic(L1PcInstance pc, L1PcInventory inv, L1ItemInstance weapon) {
        if (pc == null || inv == null || weapon == null || weapon.getItem() == null) return EquipResult.fail(null);

        if (!L1PolyMorph.isEquipableWeapon(pc.getCurrentSpriteId(), weapon.getItem().getType())) {
            return EquipResult.fail(null);
        }

        // 양손 + 방패 장착중 불가
        if (weapon.getItem().isTwohandedWeapon() && pc.getEquipSlot().hasAppliedArmor(7)) {
            return EquipResult.fail(EquipFail.MSG_SHIELD_BLOCK_128);
        }

        return EquipResult.OK;
    }

    // 슬레이어 + 한손도끼 조건일 때 “2번째 무기” 가능 여부
    public static EquipResult canEquipAsSecondWeaponSlayer(L1PcInstance pc, L1PcInventory inv, L1ItemInstance current, L1ItemInstance next) {
        if (pc == null || inv == null || current == null || next == null) return EquipResult.fail(null);

        final L1EquipmentSlot slot = pc.getEquipSlot();
        if (!slot.canUseSecondWeaponBySlayer(current, next)) return EquipResult.fail(null);
        if (slot.hasAppliedArmor(7) || slot.hasAppliedArmor(13)) return EquipResult.fail(EquipFail.MSG_SHIELD_BLOCK_128);
        if (slot.getWeaponCount() >= 2) return EquipResult.fail(EquipFail.MSG_ALREADY_EQUIPPED_124);

        return EquipResult.OK; // 두번째 무기로 OK
    }
}
